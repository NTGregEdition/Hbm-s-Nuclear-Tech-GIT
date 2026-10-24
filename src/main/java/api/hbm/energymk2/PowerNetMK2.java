package api.hbm.energymk2;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.uninos.NodeNet;
import com.hbm.util.Tuple.Pair;

import java.util.Map.Entry;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import api.hbm.energymk2.Nodespace.PowerNode;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Technically MK3 since it's now UNINOS compatible, although UNINOS was build out of 95% nodespace code
 *
 * @author hbm
 */
public class PowerNetMK2 extends NodeNet<IEnergyReceiverMK2, IEnergyProviderMK2, PowerNode> {


	private void punishLegacyVoltageMismatches(List<Pair<IEnergyProviderMK2, Long>> providers, List<Pair<IEnergyReceiverMK2, Long>>[] receivers, long[] demand) {

		Set<Long> providerVoltages = new HashSet<Long>();
		for(Pair<IEnergyProviderMK2, Long> p : providers) {
			long v = p.getKey().getProviderVoltage();
			if(VoltageTier.isConfigured(v)) providerVoltages.add(v);
		}
		if(providerVoltages.isEmpty()) return; // nothing configured providing here, nothing to compare against

		for(int i = 0; i < receivers.length; i++) {
			Iterator<Pair<IEnergyReceiverMK2, Long>> it = receivers[i].iterator();
			while(it.hasNext()) {
				Pair<IEnergyReceiverMK2, Long> entry = it.next();
				long receiverVoltage = entry.getKey().getReceiverVoltage();
				if(!VoltageTier.isConfigured(receiverVoltage)) continue;

				for(Long providerVoltage : providerVoltages) {
					if(providerVoltage.longValue() > receiverVoltage) {
						entry.getKey().onOvervoltage(providerVoltage.longValue());
						demand[i] -= entry.getValue();
						it.remove();
						break;
					}
				}
			}
		}
	}
	public long energyTracker = 0L;

	private final Map<IEnergyProviderMK2, List<DirPos>> providerCablePorts = new HashMap<IEnergyProviderMK2, List<DirPos>>();
	private final Map<IEnergyReceiverMK2, List<DirPos>> receiverCablePorts = new HashMap<IEnergyReceiverMK2, List<DirPos>>();
	private final Map<IEnergyProviderMK2, Long> providerPowerTransferredThisTick = new HashMap<IEnergyProviderMK2, Long>();
	private final Map<IEnergyProviderMK2, Long> providerAmperageTransferredThisTick = new HashMap<IEnergyProviderMK2, Long>();
	private final Map<IEnergyProviderMK2, Map<IEnergyReceiverMK2, VoltageRoute>> voltageRoutes = new IdentityHashMap<IEnergyProviderMK2, Map<IEnergyReceiverMK2, VoltageRoute>>();
	private static final VoltageRoute NO_VOLTAGE_ROUTE = new VoltageRoute();
	private boolean voltageCableStateKnown;
	private boolean containsVoltageCable;

	@Override public void resetTrackers() { this.energyTracker = 0; }

	@Override
	public void addProvider(IEnergyProviderMK2 provider) {
		if(isProvider(provider)) return;
		super.addProvider(provider);
		invalidateVoltageCaches();
	}

	public void addProviderAt(IEnergyProviderMK2 provider, int x, int y, int z, ForgeDirection cableFace) {
		addProvider(provider);
		if(rememberCablePort(providerCablePorts, provider, x, y, z, cableFace)) invalidateVoltageCaches();
	}

	@Override
	public void addReceiver(IEnergyReceiverMK2 receiver) {
		if(isSubscribed(receiver)) return;
		super.addReceiver(receiver);
		invalidateVoltageCaches();
	}

	public void addReceiverAt(IEnergyReceiverMK2 receiver, int x, int y, int z, ForgeDirection cableFace) {
		addReceiver(receiver);
		if(rememberCablePort(receiverCablePorts, receiver, x, y, z, cableFace)) invalidateVoltageCaches();
	}

	private <T> boolean rememberCablePort(Map<T, List<DirPos>> ports, T endpoint, int x, int y, int z, ForgeDirection cableFace) {
		if(cableFace == null || cableFace == ForgeDirection.UNKNOWN) return false;
		List<DirPos> list = ports.get(endpoint);
		if(list == null) {
			list = new ArrayList<DirPos>();
			ports.put(endpoint, list);
		}
		for(DirPos port : list) {
			if(port.getX() == x && port.getY() == y && port.getZ() == z && port.getDir() == cableFace) return false;
		}
		list.add(new DirPos(x, y, z, cableFace));
		return true;
	}

	@Override
	public void joinNetworks(NodeNet network) {
		invalidateVoltageCaches();
		if(network != this && network instanceof PowerNetMK2) {
			PowerNetMK2 other = (PowerNetMK2) network;
			mergeCablePorts(providerCablePorts, other.providerCablePorts);
			mergeCablePorts(receiverCablePorts, other.receiverCablePorts);
		}
		super.joinNetworks(network);
	}

	@Override
	public NodeNet joinLink(PowerNode node) {
		invalidateVoltageCaches();
		return super.joinLink(node);
	}

	@Override
	public NodeNet forceJoinLink(PowerNode node) {
		invalidateVoltageCaches();
		return super.forceJoinLink(node);
	}

	@Override
	public void leaveLink(PowerNode node) {
		invalidateVoltageCaches();
		super.leaveLink(node);
	}

	private <T> void mergeCablePorts(Map<T, List<DirPos>> destination, Map<T, List<DirPos>> source) {
		for(Entry<T, List<DirPos>> entry : source.entrySet()) {
			for(DirPos port : entry.getValue()) {
				rememberCablePort(destination, entry.getKey(), port.getX(), port.getY(), port.getZ(), port.getDir());
			}
		}
	}

	@Override
	public void removeProvider(IEnergyProviderMK2 provider) {
		boolean changed = isProvider(provider) || providerCablePorts.containsKey(provider);
		super.removeProvider(provider);
		providerCablePorts.remove(provider);
		if(changed) invalidateVoltageCaches();
	}

	@Override
	public void removeReceiver(IEnergyReceiverMK2 receiver) {
		boolean changed = isSubscribed(receiver) || receiverCablePorts.containsKey(receiver);
		super.removeReceiver(receiver);
		receiverCablePorts.remove(receiver);
		if(changed) invalidateVoltageCaches();
	}

	@Override
	public void destroy() {
		super.destroy();
		providerCablePorts.clear();
		receiverCablePorts.clear();
		providerPowerTransferredThisTick.clear();
		providerAmperageTransferredThisTick.clear();
		invalidateVoltageCaches();
	}

	private void invalidateVoltageCaches() {
		voltageCableStateKnown = false;
		voltageRoutes.clear();
	}

	@Override
	public void update() {

		providerPowerTransferredThisTick.clear();
		providerAmperageTransferredThisTick.clear();
		if(providerEntries.isEmpty()) return;

		World world = getNetworkWorld();
		if(world != null && hasVoltageCable(world)) {
			updateVoltageCableNetwork(world);
			return;
		}
		if(receiverEntries.isEmpty()) return;

		List<Pair<IEnergyProviderMK2, Long>> providers = new ArrayList();
		long powerAvailable = 0;
		long totalPowerInNetwork = 0;

		// sum up available power
		Iterator<IEnergyProviderMK2> provIt = providerEntries.iterator();
		while(provIt.hasNext()) {
			IEnergyProviderMK2 entry = provIt.next();
			if(isBadLink(entry)) { providerCablePorts.remove(entry); provIt.remove(); continue; }
			long src = Math.min(entry.getProviderPower(), entry.getProviderSpeed());
			if(src > 0) {
				providers.add(new Pair(entry, src));
				powerAvailable += src;
				totalPowerInNetwork += entry.getProviderPower();
			}
		}

		// sum up total demand, categorized by priority
		List<Pair<IEnergyReceiverMK2, Long>>[] receivers = new ArrayList[ConnectionPriority.values().length];
		for(int i = 0; i < receivers.length; i++) receivers[i] = new ArrayList();
		long[] demand = new long[ConnectionPriority.values().length];
		long totalDemand = 0;

		Iterator<IEnergyReceiverMK2> recIt = receiverEntries.iterator();

		while(recIt.hasNext()) {
			IEnergyReceiverMK2 entry = recIt.next();
			if(isBadLink(entry)) { receiverCablePorts.remove(entry); recIt.remove(); continue; }
			long rec = Math.min(entry.getReceiverMaxPower() - entry.getReceiverPower(), entry.getReceiverSpeed());
			if(rec > 0) {
				int p = entry.getPriority().ordinal();
				receivers[p].add(new Pair(entry, rec));
				demand[p] += rec;
				totalDemand += rec;
			}
		}

		punishLegacyVoltageMismatches(providers, receivers, demand);
		totalDemand = 0;
		for(long d : demand) totalDemand += d;

		long toTransfer = Math.min(powerAvailable, totalDemand);
		long energyUsed = 0;

		// add power to receivers, ordered by priority
		for(int i = ConnectionPriority.values().length - 1; i >= 0; i--) {
			List<Pair<IEnergyReceiverMK2, Long>> list = receivers[i];
			long priorityDemand = demand[i];

			for(Pair<IEnergyReceiverMK2, Long> entry : list) {
				double weight = (double) entry.getValue() / (double) (priorityDemand);
				long toSend = (long) Math.min(Math.max(toTransfer * weight, 0D), entry.getValue());
				energyUsed += (toSend - entry.getKey().transferPower(toSend)); //leftovers are subtracted from the intended amount to use up
			}

			toTransfer -= energyUsed;
		}

		long leftover = energyUsed;

		// remove power from providers
		for(Pair<IEnergyProviderMK2, Long> entry : providers) {
			double weight = (double) entry.getValue() / (double) powerAvailable;
			long toUse = (long) Math.max(energyUsed * weight, 0D);
			entry.getKey().usePower(toUse);
			leftover -= toUse;
		}

		// rounding error compensation, detects surplus that hasn't been used and removes it from random providers
		int iterationsLeft = 100; // whiles without emergency brakes are a bad idea
		while(iterationsLeft > 0 && leftover > 0 && providers.size() > 0) {
			iterationsLeft--;

			Pair<IEnergyProviderMK2, Long> selected = providers.get(rand.nextInt(providers.size()));
			IEnergyProviderMK2 scapegoat = selected.getKey();

			long toUse = Math.min(leftover, scapegoat.getProviderPower());
			scapegoat.usePower(toUse);
			leftover -= toUse;
		}

		long powerAvailableAfter = 0;
		for(IEnergyProviderMK2 entry : providerEntries) powerAvailableAfter += Math.max(entry.getProviderPower(), 0);

		this.energyTracker += totalPowerInNetwork - powerAvailableAfter;
	}

	public long sendPowerDiode(long power) {

		if(receiverEntries.isEmpty()) return power;

		List<Pair<IEnergyReceiverMK2, Long>>[] receivers = new ArrayList[ConnectionPriority.values().length];
		for(int i = 0; i < receivers.length; i++) receivers[i] = new ArrayList();
		long[] demand = new long[ConnectionPriority.values().length];
		long totalDemand = 0;

		Iterator<IEnergyReceiverMK2> recIt = receiverEntries.iterator();

		while(recIt.hasNext()) {
			IEnergyReceiverMK2 entry = recIt.next();
			if(isBadLink(entry)) { receiverCablePorts.remove(entry); recIt.remove(); continue; }
			long rec = Math.min(entry.getReceiverMaxPower() - entry.getReceiverPower(), entry.getReceiverSpeed());
			int p = entry.getPriority().ordinal();
			receivers[p].add(new Pair(entry, rec));
			demand[p] += rec;
			totalDemand += rec;
		}

		long toTransfer = Math.min(power, totalDemand);
		long energyUsed = 0;

		for(int i = ConnectionPriority.values().length - 1; i >= 0; i--) {
			List<Pair<IEnergyReceiverMK2, Long>> list = receivers[i];
			long priorityDemand = demand[i];

			for(Pair<IEnergyReceiverMK2, Long> entry : list) {
				double weight = (double) entry.getValue() / (double) (priorityDemand);
				long toSend = (long) Math.max(toTransfer * weight, 0D);
				energyUsed += (toSend - entry.getKey().transferPower(toSend)); //leftovers are subtracted from the intended amount to use up
			}

			toTransfer -= energyUsed;
		}

		this.energyTracker += energyUsed;

		return power - energyUsed;
	}

	private void updateVoltageCableNetwork(World world) {
		List<IEnergyProviderMK2> providers = new ArrayList<IEnergyProviderMK2>();
		Iterator<IEnergyProviderMK2> provIt = providerEntries.iterator();
		while(provIt.hasNext()) {
			IEnergyProviderMK2 entry = provIt.next();
			if(isBadLink(entry)) { providerCablePorts.remove(entry); provIt.remove(); continue; }
			providers.add(entry);
		}

		for(PowerNode node : this.links) {
			for(BlockPos pos : node.positions) {
				TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
				if(tile instanceof IVoltageCableMK2) ((IVoltageCableMK2) tile).beginPowerTick();
			}
		}
		updateEnergizedVoltageCables(world, providers);
		if(receiverEntries.isEmpty()) return;

		@SuppressWarnings("unchecked")
		List<IEnergyReceiverMK2>[] receivers = new ArrayList[ConnectionPriority.values().length];
		for(int i = 0; i < receivers.length; i++) receivers[i] = new ArrayList<IEnergyReceiverMK2>();
		Iterator<IEnergyReceiverMK2> recIt = receiverEntries.iterator();
		while(recIt.hasNext()) {
			IEnergyReceiverMK2 entry = recIt.next();
			if(isBadLink(entry)) { receiverCablePorts.remove(entry); recIt.remove(); continue; }
			receivers[entry.getPriority().ordinal()].add(entry);
		}

		for(int priority = receivers.length - 1; priority >= 0; priority--) {
			for(IEnergyReceiverMK2 receiver : receivers[priority]) {
				for(IEnergyProviderMK2 provider : providers) {
					if((Object) provider == (Object) receiver) continue;
					if(isBadLink(provider) || isBadLink(receiver)) continue;
					VoltageRoute route = getVoltageRoute(world, provider, receiver);
					if(route == null || route.cables.isEmpty()) {
						if(isDirectlyAdjacent(provider, receiver)) moveDirectly(provider, receiver);
						if(getReceiverDemand(receiver) <= 0L) break;
						continue;
					}
					moveAlongRoute(provider, receiver, route);
					if(getReceiverDemand(receiver) <= 0) break;
				}
			}
		}
	}

	private void updateEnergizedVoltageCables(World world, List<IEnergyProviderMK2> providers) {
		for(IEnergyProviderMK2 provider : providers) {
			long voltage = provider.getProviderVoltage();
			long sourceAvailable = getProviderPowerAvailableThisTick(provider);
			if(sourceAvailable <= 0L || !VoltageTier.isConfigured(voltage)) continue;
			long amperage = Math.min(getProviderAmperageAvailableThisTick(provider), sourceAvailable / voltage);
			if(amperage <= 0L) continue;

			for(ReachableCable reachable : findReachableVoltageCables(world, provider)) {
				IVoltageCableMK2 cable = reachable.cable;
				if(reachable.voltage > cable.getCableProperties().voltage) {
					cable.burnForElectricalOverload(reachable.voltage, 0L, 1 + reachable.distance * 2,
							provider instanceof TileEntity ? (TileEntity) provider : null);
				}
			}
		}
	}

	private long getReceiverDemand(IEnergyReceiverMK2 receiver) {
		return Math.max(0L, Math.min(receiver.getReceiverMaxPower() - receiver.getReceiverPower(), receiver.getReceiverSpeed()));
	}

	private boolean isDirectlyAdjacent(IEnergyProviderMK2 provider, IEnergyReceiverMK2 receiver) {
		if(!(provider instanceof TileEntity) || !(receiver instanceof TileEntity)) return false;
		TileEntity source = (TileEntity) provider;
		TileEntity target = (TileEntity) receiver;
		if(source.getWorldObj() == null || source.getWorldObj() != target.getWorldObj()) return false;
		BlockPos sourcePos = new BlockPos(source.xCoord, source.yCoord, source.zCoord);
		BlockPos targetPos = new BlockPos(target.xCoord, target.yCoord, target.zCoord);
		if(!isAdjacent(sourcePos, targetPos)) return false;
		return hasPortAt(providerCablePorts.get(provider), targetPos)
				|| hasPortAt(receiverCablePorts.get(receiver), sourcePos)
				|| hasBatterySocketConnection(provider, targetPos)
				|| hasBatterySocketConnection(receiver, sourcePos);
	}

	private boolean hasPortAt(List<DirPos> ports, BlockPos position) {
		if(ports == null) return false;
		for(DirPos port : ports) {
			if(port.getX() == position.getX() && port.getY() == position.getY() && port.getZ() == position.getZ()) return true;
		}
		return false;
	}

	private boolean hasBatterySocketConnection(IEnergyHandlerMK2 endpoint, BlockPos target) {
		if(!(endpoint instanceof TileEntity)) return false;
		TileEntity tile = (TileEntity) endpoint;
		BlockPos endpointPos = new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord);
		for(PowerNode node : this.links) {
			if(!node.batterySocket || !containsPosition(node, endpointPos)) continue;
			for(DirPos connection : node.connections) {
				if(connection.getX() == target.getX() && connection.getY() == target.getY() && connection.getZ() == target.getZ()) return true;
			}
		}
		return false;
	}

	private void moveDirectly(IEnergyProviderMK2 provider, IEnergyReceiverMK2 receiver) {
		long voltage = provider.getProviderVoltage();
		if(!VoltageTier.isConfigured(voltage)) return;
		long available = getProviderPowerAvailableThisTick(provider);
		long sourceAmperage = Math.min(getProviderAmperageAvailableThisTick(provider), available / voltage);
		long demand = getReceiverDemand(receiver);
		if(sourceAmperage <= 0L || demand <= 0L) return;

		long receiverVoltage = receiver.getReceiverVoltage();
		if(VoltageTier.isConfigured(receiverVoltage) && voltage > receiverVoltage) {
			receiver.onOvervoltage(voltage);
			return;
		}

		long amperage = Math.min(sourceAmperage,
				Math.min(receiver.getReceiverAmperage(), ampsNeededForEnergy(demand, voltage)));
		if(amperage <= 0L) return;
		long deliveredPower = multiplyClamped(amperage, voltage);
		long remainder = receiver.transferPowerAtVoltage(deliveredPower, voltage);
		long acceptedPower = deliveredPower - Math.min(deliveredPower, Math.max(0L, remainder));
		if(acceptedPower <= 0L) return;

		long acceptedAmperage = Math.min(amperage, ampsNeededForEnergy(acceptedPower, voltage));
		long sourcePower = multiplyClamped(acceptedAmperage, voltage);
		provider.usePower(sourcePower);
		Long priorUsed = providerPowerTransferredThisTick.get(provider);
		providerPowerTransferredThisTick.put(provider, Long.valueOf(addClamped(priorUsed == null ? 0L : priorUsed.longValue(), sourcePower)));
		Long priorAmperage = providerAmperageTransferredThisTick.get(provider);
		providerAmperageTransferredThisTick.put(provider, Long.valueOf(addClamped(priorAmperage == null ? 0L : priorAmperage.longValue(), acceptedAmperage)));
		energyTracker += acceptedPower;
	}

	private long ampsNeededForEnergy(long energy, long voltage) {
		if(energy <= 0L || voltage <= 0L) return 0L;
		return 1L + (energy - 1L) / voltage;
	}

	private long getProviderPowerAvailableThisTick(IEnergyProviderMK2 provider) {
		Long used = providerPowerTransferredThisTick.get(provider);
		long usedPower = used == null ? 0L : used.longValue();
		long speedRemaining = Math.max(0L, provider.getProviderSpeed() - usedPower);
		return Math.max(0L, Math.min(provider.getProviderPower(), speedRemaining));
	}

	private long getProviderAmperageAvailableThisTick(IEnergyProviderMK2 provider) {
		Long used = providerAmperageTransferredThisTick.get(provider);
		long usedAmperage = used == null ? 0L : used.longValue();
		return Math.max(0L, provider.getProviderAmperage() - usedAmperage);
	}

	private void moveAlongRoute(IEnergyProviderMK2 provider, IEnergyReceiverMK2 receiver, VoltageRoute route) {
		long voltage = provider.getProviderVoltage();
		if(!VoltageTier.isConfigured(voltage)) return;
		long sourceAvailable = getProviderPowerAvailableThisTick(provider);
		if(sourceAvailable <= 0L) return;
		long sourceAmperage = Math.min(getProviderAmperageAvailableThisTick(provider), sourceAvailable / voltage);
		if(sourceAmperage <= 0L) return;
		long demand = getReceiverDemand(receiver);
		long totalLossPerAmp = 0L;
		for(IVoltageCableMK2 cable : route.cables) {
			totalLossPerAmp = addClamped(totalLossPerAmp, Math.max(0L, cable.getCableProperties().lossPerBlock));
		}
		long deliveredVoltage = Math.max(0L, voltage - totalLossPerAmp);
		long flowingAmperage = demand > 0L && deliveredVoltage > 0L
				? Math.min(sourceAmperage, Math.min(receiver.getReceiverAmperage(), ampsNeededForEnergy(demand, deliveredVoltage)))
				: 0L;

		int firstOverloadIndex = -1;
		long cableVoltage = voltage;
		for(int i = 0; i < route.cables.size(); i++) {
			IVoltageCableMK2 cable = route.cables.get(i);
			boolean overvoltage = cableVoltage > cable.getCableProperties().voltage;
			if(overvoltage) {
				if(firstOverloadIndex < 0) firstOverloadIndex = i;
				cable.burnForElectricalOverload(cableVoltage, 0L, 1 + (i - firstOverloadIndex) * 2,
						provider instanceof TileEntity ? (TileEntity) provider : null);
			}
			cableVoltage = Math.max(0L, cableVoltage - Math.max(0L, cable.getCableProperties().lossPerBlock));
		}

		if(demand <= 0L || deliveredVoltage <= 0L) return;
		long amperage = flowingAmperage;
		if(amperage <= 0L) return;
		long receiverVoltage = receiver.getReceiverVoltage();
		if(VoltageTier.isConfigured(receiverVoltage) && deliveredVoltage > receiverVoltage) {
			receiver.onOvervoltage(deliveredVoltage);
			return;
		}

		long deliveredPower = multiplyClamped(amperage, deliveredVoltage);
		if(deliveredPower <= 0L) return;
		long remainder = receiver.transferPowerAtVoltage(deliveredPower, deliveredVoltage);
		long acceptedPower = deliveredPower - Math.min(deliveredPower, Math.max(0L, remainder));
		if(acceptedPower <= 0L) return;

		long acceptedAmperage = Math.min(amperage, ampsNeededForEnergy(acceptedPower, deliveredVoltage));
		long pathMaxAmperage = Long.MAX_VALUE;
		for(IVoltageCableMK2 cable : route.cables) {
			pathMaxAmperage = Math.min(pathMaxAmperage, cable.getCableProperties().maxAmperage);
		}
		cableVoltage = voltage;
		for(int i = 0; i < route.cables.size(); i++) {
			IVoltageCableMK2 cable = route.cables.get(i);
			cable.recordAmperageFlow(cableVoltage, acceptedAmperage, pathMaxAmperage, 1 + i * 2,
					provider instanceof TileEntity ? (TileEntity) provider : null);
			cableVoltage = Math.max(0L, cableVoltage - Math.max(0L, cable.getCableProperties().lossPerBlock));
		}
		long sourcePower = multiplyClamped(acceptedAmperage, voltage);
		provider.usePower(sourcePower);
		Long priorUsed = providerPowerTransferredThisTick.get(provider);
		providerPowerTransferredThisTick.put(provider, Long.valueOf(addClamped(priorUsed == null ? 0L : priorUsed.longValue(), sourcePower)));
		Long priorAmperage = providerAmperageTransferredThisTick.get(provider);
		providerAmperageTransferredThisTick.put(provider, Long.valueOf(addClamped(priorAmperage == null ? 0L : priorAmperage.longValue(), acceptedAmperage)));
		energyTracker += acceptedPower;
	}

	private long addClamped(long first, long second) {
		return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
	}

	private long multiplyClamped(long first, long second) {
		if(first <= 0L || second <= 0L) return 0L;
		return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
	}

	private World getNetworkWorld() {
		for(IEnergyProviderMK2 provider : providerEntries) {
			if(provider instanceof TileEntity && ((TileEntity) provider).getWorldObj() != null) return ((TileEntity) provider).getWorldObj();
		}
		for(IEnergyReceiverMK2 receiver : receiverEntries) {
			if(receiver instanceof TileEntity && ((TileEntity) receiver).getWorldObj() != null) return ((TileEntity) receiver).getWorldObj();
		}
		return null;
	}

	private boolean hasVoltageCable(World world) {
		if(voltageCableStateKnown) return containsVoltageCable;
		containsVoltageCable = false;
		for(PowerNode node : this.links) {
			for(BlockPos pos : node.positions) {
				if(world.getTileEntity(pos.getX(), pos.getY(), pos.getZ()) instanceof IVoltageCableMK2) {
					containsVoltageCable = true;
					voltageCableStateKnown = true;
					return true;
				}
			}
		}
		voltageCableStateKnown = true;
		return containsVoltageCable;
	}

	private VoltageRoute getVoltageRoute(World world, IEnergyProviderMK2 provider, IEnergyReceiverMK2 receiver) {
		Map<IEnergyReceiverMK2, VoltageRoute> routes = voltageRoutes.get(provider);
		if(routes != null && routes.containsKey(receiver)) return routes.get(receiver);

		VoltageRoute route = findVoltageRoute(world, provider, receiver);
		if(route == null) route = NO_VOLTAGE_ROUTE;
		if(routes == null) {
			routes = new IdentityHashMap<IEnergyReceiverMK2, VoltageRoute>();
			voltageRoutes.put(provider, routes);
		}
		routes.put(receiver, route);
		return route;
	}

	private VoltageRoute findVoltageRoute(World world, IEnergyProviderMK2 provider, IEnergyReceiverMK2 receiver) {
		Map<BlockPos, PowerNode> nodes = new HashMap<BlockPos, PowerNode>();
		Set<BlockPos> targets = new HashSet<BlockPos>();
		Set<PowerNode> receiverBatterySockets = new HashSet<PowerNode>();
		ArrayDeque<PowerNode> queue = new ArrayDeque<PowerNode>();
		Map<PowerNode, PowerNode> previous = new HashMap<PowerNode, PowerNode>();

		for(PowerNode node : this.links) {
			for(BlockPos pos : node.positions) {
				BlockPos key = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
				nodes.put(key, node);
			}
		}

		List<DirPos> receiverPorts = receiverCablePorts.get(receiver);
		if(receiverPorts != null) {
			for(PowerNode node : this.links) {
				if(!node.batterySocket) continue;
				for(DirPos port : receiverPorts) {
					if(containsPosition(node, port)) {
						receiverBatterySockets.add(node);
						break;
					}
				}
			}
		}

		for(PowerNode node : this.links) {
			for(BlockPos pos : node.positions) {
				BlockPos key = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
				TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
				if(tile instanceof IVoltageCableMK2 && touchesEnabledCableFace(world, pos, providerCablePorts.get(provider), provider) && !previous.containsKey(node)) {
					queue.add(node);
					previous.put(node, null);
				}
				if(tile instanceof IVoltageCableMK2
						&& (touchesEnabledCableFace(world, pos, receiverPorts, receiver)
								|| touchesBatterySocket(world, pos, receiverBatterySockets))) targets.add(key);
			}
		}

		PowerNode end = null;
		while(!queue.isEmpty()) {
			PowerNode node = queue.remove();
			if(containsPosition(node, targets)) { end = node; break; }
			for(DirPos connection : node.connections) {
				PowerNode next = nodes.get(new BlockPos(connection.getX(), connection.getY(), connection.getZ()));
				if(next != null && !previous.containsKey(next) && isVoltageCableStep(world, node, next)) {
					previous.put(next, node);
					queue.add(next);
				}
			}
		}

		if(end == null) return null;
		List<PowerNode> nodePath = new ArrayList<PowerNode>();
		for(PowerNode node = end; node != null; node = previous.get(node)) nodePath.add(0, node);
		VoltageRoute route = new VoltageRoute();
		for(PowerNode node : nodePath) {
			for(BlockPos pos : node.positions) {
				TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
				if(tile instanceof IVoltageCableMK2) route.cables.add((IVoltageCableMK2) tile);
			}
		}
		return route;
	}

	private boolean touchesBatterySocket(World world, BlockPos cablePosition, Set<PowerNode> batterySockets) {
		TileEntity tile = world.getTileEntity(cablePosition.getX(), cablePosition.getY(), cablePosition.getZ());
		if(!(tile instanceof IVoltageCableMK2)) return false;
		IVoltageCableMK2 cable = (IVoltageCableMK2) tile;
		for(PowerNode socket : batterySockets) {
			for(DirPos connection : socket.connections) {
				if(connection.getX() == cablePosition.getX()
						&& connection.getY() == cablePosition.getY()
						&& connection.getZ() == cablePosition.getZ()
						&& cable.isFaceConnected(connection.getDir().getOpposite())) return true;
			}
		}
		return false;
	}

	private boolean isVoltageCableStep(World world, PowerNode from, PowerNode to) {
		IVoltageCableMK2 first = getVoltageCable(world, from);
		IVoltageCableMK2 second = getVoltageCable(world, to);
		if(first == null || second == null) return false;
		for(BlockPos firstPos : from.positions) {
			TileEntity firstTile = world.getTileEntity(firstPos.getX(), firstPos.getY(), firstPos.getZ());
			if(!(firstTile instanceof IVoltageCableMK2)) continue;
			for(BlockPos secondPos : to.positions) {
				TileEntity secondTile = world.getTileEntity(secondPos.getX(), secondPos.getY(), secondPos.getZ());
				if(!(secondTile instanceof IVoltageCableMK2)) continue;
				for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
					if(firstPos.getX() + direction.offsetX == secondPos.getX()
							&& firstPos.getY() + direction.offsetY == secondPos.getY()
							&& firstPos.getZ() + direction.offsetZ == secondPos.getZ()
							&& ((IVoltageCableMK2) firstTile).isFaceConnected(direction)
							&& ((IVoltageCableMK2) secondTile).isFaceConnected(direction.getOpposite())) return true;
				}
			}
		}
		return false;
	}

	private List<ReachableCable> findReachableVoltageCables(World world, IEnergyProviderMK2 provider) {
		Map<BlockPos, PowerNode> nodes = new HashMap<BlockPos, PowerNode>();
		ArrayDeque<PowerNode> queue = new ArrayDeque<PowerNode>();
		Map<PowerNode, Long> voltages = new HashMap<PowerNode, Long>();
		Map<PowerNode, Integer> distances = new HashMap<PowerNode, Integer>();
		long providerVoltage = provider.getProviderVoltage();
		for(PowerNode node : this.links) {
			for(BlockPos pos : node.positions) {
				nodes.put(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), node);
				TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
				if(tile instanceof IVoltageCableMK2
						&& touchesEnabledCableFace(world, pos, providerCablePorts.get(provider), provider)
						&& !voltages.containsKey(node)) {
					voltages.put(node, Long.valueOf(providerVoltage));
					distances.put(node, Integer.valueOf(0));
					queue.add(node);
				}
			}
		}

		while(!queue.isEmpty()) {
			PowerNode node = queue.remove();
			long voltage = voltages.get(node).longValue();
			int distance = distances.get(node).intValue();
			long voltageAfterNode = voltage;
			for(BlockPos pos : node.positions) {
				TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
				if(tile instanceof IVoltageCableMK2) {
					voltageAfterNode = Math.max(0L, voltageAfterNode - Math.max(0L, ((IVoltageCableMK2) tile).getCableProperties().lossPerBlock));
				}
			}
			for(DirPos connection : node.connections) {
				PowerNode next = nodes.get(new BlockPos(connection.getX(), connection.getY(), connection.getZ()));
				if(next == null || voltageAfterNode <= 0L || !isVoltageCableStep(world, node, next)) continue;
				Long previousVoltage = voltages.get(next);
				if(previousVoltage == null || voltageAfterNode > previousVoltage.longValue()) {
					voltages.put(next, Long.valueOf(voltageAfterNode));
					if(!distances.containsKey(next) || distance + 1 < distances.get(next).intValue()) {
						distances.put(next, Integer.valueOf(distance + 1));
					}
					queue.add(next);
				}
			}
		}

		List<ReachableCable> cables = new ArrayList<ReachableCable>();
		for(Map.Entry<PowerNode, Long> entry : voltages.entrySet()) {
			PowerNode node = entry.getKey();
			int distance = distances.get(node).intValue();
			for(BlockPos pos : node.positions) {
				TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
				if(tile instanceof IVoltageCableMK2) {
					cables.add(new ReachableCable((IVoltageCableMK2) tile, distance, entry.getValue().longValue()));
				}
			}
		}
		return cables;
	}

	private IVoltageCableMK2 getVoltageCable(World world, PowerNode node) {
		for(BlockPos pos : node.positions) {
			TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
			if(tile instanceof IVoltageCableMK2) return (IVoltageCableMK2) tile;
		}
		return null;
	}

	private boolean containsPosition(PowerNode node, Set<BlockPos> positions) {
		for(BlockPos pos : node.positions) if(positions.contains(pos)) return true;
		return false;
	}

	private boolean containsPosition(PowerNode node, BlockPos position) {
		for(BlockPos pos : node.positions) {
			if(pos.getX() == position.getX() && pos.getY() == position.getY() && pos.getZ() == position.getZ()) return true;
		}
		return false;
	}

	private boolean isAdjacent(BlockPos first, BlockPos second) {
		return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ()) == 1;
	}

	private boolean touchesEnabledCableFace(World world, BlockPos cablePosition, List<DirPos> subscribedPorts, IEnergyHandlerMK2 endpoint) {
		TileEntity tile = world.getTileEntity(cablePosition.getX(), cablePosition.getY(), cablePosition.getZ());
		if(!(tile instanceof IVoltageCableMK2)) return false;
		IVoltageCableMK2 cable = (IVoltageCableMK2) tile;

		if(subscribedPorts != null && !subscribedPorts.isEmpty()) {
			for(DirPos port : subscribedPorts) {
				if(port.getX() == cablePosition.getX() && port.getY() == cablePosition.getY() && port.getZ() == cablePosition.getZ()) {
					return cable.isFaceConnected(port.getDir());
				}
			}
			return false;
		}

		BlockPos[] connectionPoints = endpoint.getVoltageConnectionPoints();
		if(connectionPoints == null) return false;
		for(BlockPos endpointPosition : connectionPoints) {
			if(endpointPosition == null || !isAdjacent(cablePosition, endpointPosition)) continue;
			for(net.minecraftforge.common.util.ForgeDirection direction : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
				if(cablePosition.getX() + direction.offsetX == endpointPosition.getX()
						&& cablePosition.getY() + direction.offsetY == endpointPosition.getY()
						&& cablePosition.getZ() + direction.offsetZ == endpointPosition.getZ()) {
					return cable.isFaceConnected(direction);
				}
			}
		}
		return false;
	}

	private static class VoltageRoute {
		final List<IVoltageCableMK2> cables = new ArrayList<IVoltageCableMK2>();
	}

	private static class ReachableCable {
		final IVoltageCableMK2 cable;
		final int distance;
		final long voltage;

		ReachableCable(IVoltageCableMK2 cable, int distance, long voltage) {
			this.cable = cable;
			this.distance = distance;
			this.voltage = voltage;
		}
	}
}
