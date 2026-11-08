package api.hbm.fluidmk2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import java.util.Map.Entry;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.uninos.NodeNet;
import com.hbm.util.Tuple.Pair;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;

public class FluidNetMK2 extends NodeNet<IFluidReceiverMK2, IFluidProviderMK2, FluidNode> {

	public long fluidTracker = 0L;

	protected static int timeout = 3_000;
	protected static final int PUSH_MEMORY = 40;
	protected int pushCooldown;
	protected FluidType type;

	public FluidNetMK2(FluidType type) {
		this.type = type;
		for(int i = 0; i < IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1; i++) providers[i] = new ArrayList();
		for(int i = 0; i < IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1; i++) for(int j = 0; j < ConnectionPriority.values().length; j++) receivers[i][j] = new ArrayList();
	}

	protected long throughput = Long.MAX_VALUE;
	protected long capacity;
	protected boolean throughputDirty = true;
	protected long passBudget = Long.MAX_VALUE;
	protected long pumpBoost;
	protected final long[] buffer = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
	protected long totalBuffer;

	@Override
	public void resetTrackers() {
		this.fluidTracker = 0;
		if(this.pushCooldown > 0) this.pushCooldown--;
		this.pumpBoost = this.countPumpFlow();
		this.passBudget = this.getThroughput();
	}

	@Override
	public NodeNet forceJoinLink(FluidNode node) {
		this.throughputDirty = true;
		NodeNet net = super.forceJoinLink(node);
		if(node.stored != null) {
			this.fillBuffer(node.stored);
			node.stored = null;
		}
		return net;
	}

	@Override
	public void leaveLink(FluidNode node) {
		this.releaseShare(node, true);
		this.throughputDirty = true;
		super.leaveLink(node);
	}

	@Override
	public void joinNetworks(NodeNet network) {
		if(network == this) return;

		long[] carried = null;
		if(network instanceof FluidNetMK2 && ((FluidNetMK2) network).totalBuffer > 0) {
			FluidNetMK2 other = (FluidNetMK2) network;
			carried = other.buffer.clone();
			Arrays.fill(other.buffer, 0L);
			other.totalBuffer = 0;
		}

		this.throughputDirty = true;
		super.joinNetworks(network);
		if(carried != null) this.fillBuffer(carried);
	}

	@Override
	public void destroy() {
		if(totalBuffer > 0) {
			for(FluidNode node : links) this.releaseShare(node, false);
			Arrays.fill(buffer, 0L);
			totalBuffer = 0;
		}
		super.destroy();
	}

	private void refreshStats() {
		if(throughputDirty) {
			long min = Long.MAX_VALUE;
			long total = 0;
			for(FluidNode node : links) {
				min = Math.min(min, node.throughput);
				total += node.capacity;
			}
			throughput = min;
			capacity = total;
			throughputDirty = false;
		}
	}

	public long getThroughput() {
		refreshStats();
		return Math.max(throughput, pumpBoost);
	}

	public long getBufferCapacity() {
		refreshStats();
		return capacity;
	}

	public long getBuffered() {
		return totalBuffer;
	}

	public long getShare(FluidNode node, int pressure) {
		long total = getBufferCapacity();
		if(total <= 0 || node.capacity <= 0 || buffer[pressure] <= 0) return 0L;
		long scaled = buffer[pressure] * node.capacity;
		long share = scaled / total;
		if((long) (rand.nextDouble() * total) < scaled % total) share++;
		return Math.min(share, buffer[pressure]);
	}

	private void releaseShare(FluidNode node, boolean takeFromBuffer) {
		if(totalBuffer <= 0) return;
		for(int p = 0; p < buffer.length; p++) {
			long share = getShare(node, p);
			if(share <= 0) continue;
			if(node.stored == null) node.stored = new long[buffer.length];
			node.stored[p] += share;
			if(takeFromBuffer) {
				buffer[p] -= share;
				totalBuffer -= share;
			}
		}
	}

	private void fillBuffer(long[] amounts) {
		long room = getBufferCapacity() - totalBuffer;
		for(int p = 0; p < buffer.length && room > 0; p++) {
			long in = Math.min(amounts[p], room);
			if(in <= 0) continue;
			buffer[p] += in;
			totalBuffer += in;
			room -= in;
		}
	}

	public long pushBufferTo(FluidNetMK2 target, int pressure, long budget) {
		long amount = Math.min(Math.min(buffer[pressure], budget), target.getBufferCapacity() - target.totalBuffer);
		if(target == this || amount <= 0) return 0L;
		buffer[pressure] -= amount;
		totalBuffer -= amount;
		target.buffer[pressure] += amount;
		target.totalBuffer += amount;
		target.fluidTracker += amount;
		target.pushCooldown = PUSH_MEMORY;
		return amount;
	}

	public boolean isActive() {
		return totalBuffer > 0 || fluidTracker > 0 || pushCooldown > 0;
	}

	public long leak(long amount) {
		long left = amount;
		for(int p = 0; p < buffer.length && left > 0; p++) {
			long lost = Math.min(left, buffer[p]);
			buffer[p] -= lost;
			totalBuffer -= lost;
			left -= lost;
		}
		return amount - left;
	}

	private long countPumpFlow() {
		long flow = 0;
		for(IFluidReceiverMK2 receiver : receiverEntries) flow += runningPumpFlow(receiver);
		for(IFluidProviderMK2 provider : providerEntries) if(!receiverEntries.contains(provider)) flow += runningPumpFlow(provider);
		return flow;
	}

	private static long runningPumpFlow(Object entry) {
		if(!(entry instanceof IFluidPumpMK2) || isBadLink(entry)) return 0L;
		return ((IFluidPumpMK2) entry).getPumpFlow();
	}

	protected static final ConnectionPriority[] PRIORITIES = ConnectionPriority.values();

	/** Pumps move fluid between nets with this: pipes with a buffer take what fits, others hand it straight to their receivers (highest priority first, split by demand). Returns what nobody took */
	public long passThrough(FluidType type, int pressure, long amount) {

		long refused = amount - Math.min(amount, passBudget);
		amount -= refused;
		long offered = amount;

		amount = getBufferCapacity() > 0 ? deposit(pressure, amount) : handOver(type, pressure, amount);

		passBudget -= offered - amount;
		return amount + refused;
	}

	private long deposit(int pressure, long amount) {
		long taken = Math.min(amount, getBufferCapacity() - totalBuffer);
		if(taken <= 0) return amount;
		buffer[pressure] += taken;
		totalBuffer += taken;
		fluidTracker += taken;
		return amount - taken;
	}

	private long handOver(FluidType type, int pressure, long amount) {

		for(int i = PRIORITIES.length - 1; i >= 0 && amount > 0; i--) {

			long demand = 0;
			for(IFluidReceiverMK2 receiver : receiverEntries) demand += passDemand(receiver, type, pressure, i);
			if(demand <= 0) continue;

			long left = Math.min(amount, demand);
			long total = left;

			for(IFluidReceiverMK2 receiver : receiverEntries) {
				long wanted = passDemand(receiver, type, pressure, i);
				if(wanted <= 0) continue;
				long toSend = Math.min(wanted, (long) (total * ((double) wanted / (double) demand)));
				if(toSend > 0) left -= toSend - receiver.transferFluid(type, pressure, toSend);
			}

			// rounding leftovers, otherwise tiny flows would never get anywhere
			for(IFluidReceiverMK2 receiver : receiverEntries) {
				if(left <= 0) break;
				long toSend = Math.min(left, passDemand(receiver, type, pressure, i));
				if(toSend > 0) left -= toSend - receiver.transferFluid(type, pressure, toSend);
			}

			amount -= total - left;
		}

		return amount;
	}

	private static long passDemand(IFluidReceiverMK2 receiver, FluidType type, int pressure, int priority) {
		if(receiver.getFluidPriority().ordinal() != priority || isBadLink(receiver)) return 0L;
		return Math.max(0L, Math.min(receiver.getDemand(type, pressure), receiver.getReceiverSpeed(type, pressure)));
	}

	@Override
	public void update() {

		boolean buffered = this.getBufferCapacity() > 0;

		if(providerEntries.isEmpty() && totalBuffer <= 0) return;
		if(receiverEntries.isEmpty() && !buffered) return;

		setupFluidProviders();
		if(!buffered) setupFluidReceivers();
		if(buffered) transferBuffered();
		else transferFluid();

		cleanUp();
	}

	//this sucks ass, but it makes the code just a smidge more structured
	public long totalFluidInNetwork;
	public long[] fluidAvailable = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
	public List<Pair<IFluidProviderMK2, Long>>[] providers = new ArrayList[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
	public long[][] fluidDemand = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1][ConnectionPriority.values().length];
	public List<Pair<IFluidReceiverMK2, Long>>[][] receivers = new ArrayList[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1][ConnectionPriority.values().length];
	public long[] transfered = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];

	public void setupFluidProviders() {
		
		totalFluidInNetwork = 0;
		
		Iterator<IFluidProviderMK2> iterator = providerEntries.iterator();

		while(iterator.hasNext()) {
			IFluidProviderMK2 provider = iterator.next();
			if(isBadLink(provider)) { iterator.remove(); continue; }
			int[] pressureRange = provider.getProvidingPressureRange(type);
			for(int p = pressureRange[0]; p <= pressureRange[1]; p++) {
				long available = Math.min(provider.getFluidAvailable(type, p), provider.getProviderSpeed(type, p));
				providers[p].add(new Pair(provider, available));
				fluidAvailable[p] += available;
				totalFluidInNetwork += provider.getFluidAvailable(type, p);
			}
		}
	}

	public void setupFluidReceivers() {
		Iterator<IFluidReceiverMK2> iterator = receiverEntries.iterator();

		while(iterator.hasNext()) {
			IFluidReceiverMK2 receiver = iterator.next();
			if(isBadLink(receiver)) { iterator.remove(); continue; }
			int[] pressureRange = receiver.getReceivingPressureRange(type);
			for(int p = pressureRange[0]; p <= pressureRange[1]; p++) {
				long required = Math.min(receiver.getDemand(type, p), receiver.getReceiverSpeed(type, p));
				int priority = receiver.getFluidPriority().ordinal();
				receivers[p][priority].add(new Pair(receiver, required));
				fluidDemand[p][priority] += required;
			}
		}
	}

	public void transferFluid() {

		long[] received = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
		long[] notAccountedFor = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
		long budget = this.getThroughput();

		for(int p = 0; p <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; p++) { // if the pressure range were ever to increase, we might have to rethink this

			long totalAvailable = Math.min(fluidAvailable[p], budget);

			for(int i = ConnectionPriority.values().length - 1; i >= 0; i--) {

				long toTransfer = Math.min(fluidDemand[p][i], totalAvailable);
				if(toTransfer <= 0) continue;

				long priorityDemand = fluidDemand[p][i];

				for(Pair<IFluidReceiverMK2, Long> entry : receivers[p][i]) {
					double weight = (double) entry.getValue() / (double) (priorityDemand);
					long toSend = (long) Math.max(toTransfer * weight, 0D);
					toSend -= entry.getKey().transferFluid(type, p, toSend);
					received[p] += toSend;
				}

				totalAvailable -= received[p];
			}

			notAccountedFor[p] = received[p];
			budget = Math.max(0L, budget - received[p]);
		}

		for(int p = 0; p <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; p++) {

			for(Pair<IFluidProviderMK2, Long> entry : providers[p]) {
				double weight = (double) entry.getValue() / (double) fluidAvailable[p];
				long toUse = (long) Math.max(received[p] * weight, 0D);
				entry.getKey().useUpFluid(type, p, toUse);
				notAccountedFor[p] -= toUse;
			}
		}

		for(int p = 0; p <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; p++) {

			int iterationsLeft = 100;
			while(iterationsLeft > 0 && notAccountedFor[p] > 0 && providers[p].size() > 0) {
				iterationsLeft--;

				Pair<IFluidProviderMK2, Long> selected = providers[p].get(rand.nextInt(providers[p].size()));
				IFluidProviderMK2 scapegoat = selected.getKey();

				long toUse = Math.min(notAccountedFor[p], scapegoat.getFluidAvailable(type, p));
				scapegoat.useUpFluid(type, p, toUse);
				notAccountedFor[p] -= toUse;
			}
		}
		
		Iterator<IFluidProviderMK2> iterator = providerEntries.iterator();
		long totalAfterTransfer = 0;
		
		while(iterator.hasNext()) {
			IFluidProviderMK2 provider = iterator.next();
			for(int p = 0; p <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; p++) {
				totalAfterTransfer += provider.getFluidAvailable(type, p);
			}
		}
		
		this.fluidTracker += this.totalFluidInNetwork - totalAfterTransfer;
	}

	/** Providers fill the pipes, the pipes pass their content on to the receivers, each step limited by the throughput */
	public void transferBuffered() {

		long budget = this.getThroughput();
		long room = this.getBufferCapacity() - totalBuffer;

		for(int p = 0; p <= IFluidUserMK2.HIGHEST_VALID_PRESSURE && budget > 0 && room > 0; p++) {

			long take = Math.min(Math.min(fluidAvailable[p], budget), room);
			if(take <= 0) continue;

			long used = 0;
			for(Pair<IFluidProviderMK2, Long> entry : providers[p]) {
				long toUse = (long) Math.max(take * ((double) entry.getValue() / (double) fluidAvailable[p]), 0D);
				if(toUse <= 0) continue;
				entry.getKey().useUpFluid(type, p, toUse);
				used += toUse;
			}

			// rounding leftovers
			for(Pair<IFluidProviderMK2, Long> entry : providers[p]) {
				if(used >= take) break;
				long toUse = Math.min(take - used, entry.getKey().getFluidAvailable(type, p));
				if(toUse <= 0) continue;
				entry.getKey().useUpFluid(type, p, toUse);
				used += toUse;
			}

			buffer[p] += used;
			totalBuffer += used;
			fluidTracker += used;
			budget -= used;
			room -= used;
		}

		budget = this.getThroughput();

		for(int p = 0; p <= IFluidUserMK2.HIGHEST_VALID_PRESSURE && budget > 0; p++) {

			long out = Math.min(buffer[p], budget);
			if(out <= 0) continue;

			long delivered = out - handOver(type, p, out);
			buffer[p] -= delivered;
			totalBuffer -= delivered;
			budget -= delivered;
		}
	}

	public void cleanUp() {
		for(int i = 0; i < IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1; i++) {
			fluidAvailable[i] = 0;
			providers[i].clear();
			transfered[i] = 0;

			for(int j = 0; j < ConnectionPriority.values().length; j++) {
				fluidDemand[i][j] = 0;
				receivers[i][j].clear();
			}
		}
	}
}
