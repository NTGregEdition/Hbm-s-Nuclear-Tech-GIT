package com.hbm.tileentity.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import com.hbm.blocks.network.BlockFluidPipe;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Gaseous;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous_ART;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.FluidNode;
import api.hbm.fluidmk2.FluidPipeProperties;
import api.hbm.fluidmk2.IFluidConnectorBlockMK2;
import api.hbm.fluidmk2.IFluidConnectorMK2;
import api.hbm.fluidmk2.IFluidUserMK2;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityFluidPipe extends TileEntityPipeBaseNT implements IControlReceiver {

	private static final int ENVIRONMENT_INTERVAL = 5;
	private static final int LEAK_PER_CHECK = 5;
	private static final int KELVIN = 273;
	private static final int MAX_CHANNELS = 9;
	private static final int PRESSURES = IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1;
	private static final int AUTO_INTERVAL = 10;
	private static final int IDLE_CHECKS = 2;
	private static final int VALVE_INTERVAL = 40;
	private static final int FLOOD_LIMIT = 4096;

	private byte connected;
	private byte inputOff;
	private byte valves;
	private boolean legacy;
	private boolean locked;
	
	private int idleChecks;
	private boolean channelsKnown;
	
	private FluidType[] extraTypes;
	private FluidNode[] extraNodes;
	private long[][] pending;

	private BlockFluidPipe getPipe() {
		Block block = getBlockType();
		return block instanceof BlockFluidPipe ? (BlockFluidPipe) block : null;
	}

	private void allocateExtras(int extras) {
		extraTypes = new FluidType[extras];
		Arrays.fill(extraTypes, Fluids.NONE);
		extraNodes = new FluidNode[extras];
	}

	public int getChannelCount() {
		if(!channelsKnown && worldObj != null) {
			channelsKnown = true;
			int count = BlockFluidPipe.channelsOf(getBlockMetadata());
			if(count > 1 && extraTypes == null) allocateExtras(count - 1);
		}
		return extraTypes == null ? 1 : extraTypes.length + 1;
	}

	public FluidType getChannelType(int channel) {
		return channel == 0 ? type : extraTypes[channel - 1];
	}

	private FluidNode getChannelNode(int channel) {
		return channel == 0 ? node : extraNodes[channel - 1];
	}

	public int channelOf(FluidType fluid) {
		int count = getChannelCount();
		for(int c = 0; c < count; c++) if(getChannelType(c) == fluid) return c;
		return -1;
	}

	public boolean isConnected(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && (connected & (1 << dir.ordinal())) != 0;
	}

	public boolean isInputDisabled(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && (inputOff & (1 << dir.ordinal())) != 0;
	}

	public boolean allowsInput(ForgeDirection dir) {
		return isConnected(dir) && !isInputDisabled(dir);
	}

	public boolean allowsOutput(ForgeDirection dir) {
		return isConnected(dir);
	}

	private boolean sharesNet(ForgeDirection dir) {
		TileEntityFluidPipe other = pipeAt(dir);
		return other == null || !(isInputDisabled(dir) || other.isInputDisabled(dir.getOpposite()));
	}

	private boolean isWild() {
		return type == Fluids.NONE && getChannelCount() == 1;
	}

	private static TileEntityFluidPipe pipeAt(World world, int x, int y, int z) {
		if(!world.blockExists(x, y, z)) return null;
		TileEntity te = world.getTileEntity(x, y, z);
		return te instanceof TileEntityFluidPipe ? (TileEntityFluidPipe) te : null;
	}

	private TileEntityFluidPipe pipeAt(ForgeDirection dir) {
		return pipeAt(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
	}

	@Override
	public FluidNode createNode(FluidType type) {
		DirPos[] connections = new DirPos[ForgeDirection.VALID_DIRECTIONS.length];
		int count = 0;
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			if(isConnected(dir) && sharesNet(dir)) connections[count++] = new DirPos(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
		}

		FluidNode node = new FluidNode(type.getNetworkProvider(), new BlockPos(xCoord, yCoord, zCoord)).setConnections(Arrays.copyOf(connections, count));

		BlockFluidPipe pipe = getPipe();
		if(pipe != null) {
			int meta = getBlockMetadata();
			int size = BlockFluidPipe.sizeOf(meta);
			int channels = BlockFluidPipe.channelsOf(meta);
			node.throughput = pipe.getProperties().getThroughput(size, channels);
			node.capacity = pipe.getProperties().getCapacity(size, channels);
		}

		int channel = Math.max(0, channelOf(type));
		if(pending != null && pending[channel] != null) {
			node.stored = pending[channel];
			pending[channel] = null;
		}
		return node;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		if(!isConnected(dir)) return false;
		if(type == this.type) return true;
		int count = getChannelCount();
		for(int c = 1; c < count; c++) if(extraTypes[c - 1] == type && type != Fluids.NONE) return true;
		return false;
	}

	@Override
	public FluidType getType() {
		return locked ? type : Fluids.NONE;
	}

	@Override
	public void setType(FluidType fluid) {
		if(getChannelCount() > 1) return;
		locked = fluid != Fluids.NONE;
		super.setType(fluid);
	}

	private void setAuto(FluidType fluid) {
		locked = false;
		idleChecks = 0;
		super.setType(fluid);
	}

	private void setChannelType(int channel, FluidType fluid) {
		if(channel == 0) {
			super.setType(fluid);
			return;
		}

		FluidType prev = extraTypes[channel - 1];
		if(prev == fluid) return;
		extraTypes[channel - 1] = fluid;

		if(extraNodes[channel - 1] != null) {
			UniNodespace.destroyNode(worldObj, xCoord, yCoord, zCoord, prev.getNetworkProvider());
			extraNodes[channel - 1] = null;
		}

		markDirty();
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	public boolean toggleSide(EntityPlayer player, ForgeDirection dir) {
		if(dir == ForgeDirection.UNKNOWN || worldObj.isRemote) return false;

		boolean connect = !isConnected(dir);
		if(connect && !canLink(dir)) return false;

		link(dir, connect);
		player.addChatMessage(new ChatComponentTranslation(connect ? "hbm.fluidpipe.connected" : "hbm.fluidpipe.disconnected"));
		return true;
	}

	public boolean toggleInput(EntityPlayer player, ForgeDirection dir) {
		if(dir == ForgeDirection.UNKNOWN || worldObj.isRemote || !isConnected(dir)) return false;

		inputOff = (byte) (inputOff ^ (1 << dir.ordinal()));
		rebuildNodes();

		TileEntityFluidPipe other = pipeAt(dir);
		if(other != null) other.rebuildNodes();

		player.addChatMessage(new ChatComponentTranslation(isInputDisabled(dir) ? "hbm.fluidpipe.input.disable" : "hbm.fluidpipe.input.enable"));
		return true;
	}

	public void connectBack(ForgeDirection dir) {
		if(!worldObj.isRemote && canLink(dir)) link(dir, true);
	}

	public void checkConnections() {
		if(worldObj.isRemote) return;

		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			if(!isConnected(dir) || !worldObj.blockExists(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ)) continue;

			if(!canLink(dir)) {
				setSide(dir, false);
				continue;
			}

			TileEntityFluidPipe other = pipeAt(dir);
			if(other != null && !other.isConnected(dir.getOpposite())) other.setSide(dir.getOpposite(), true);
		}
	}

	private boolean canLink(ForgeDirection dir) {
		int x = xCoord + dir.offsetX;
		int y = yCoord + dir.offsetY;
		int z = zCoord + dir.offsetZ;
		if(!worldObj.blockExists(x, y, z)) return false;
		if(worldObj.isAirBlock(x, y, z)) return true;
		return worldObj.getBlock(x, y, z) instanceof IFluidConnectorBlockMK2 || worldObj.getTileEntity(x, y, z) instanceof IFluidConnectorMK2;
	}

	private void link(ForgeDirection dir, boolean on) {
		setSide(dir, on);
		TileEntityFluidPipe other = pipeAt(dir);
		if(other != null) other.setSide(dir.getOpposite(), on);
	}

	private void setSide(ForgeDirection dir, boolean on) {
		int bit = 1 << dir.ordinal();
		connected = (byte) (on ? connected | bit : connected & ~bit);
		rebuildNodes();
	}

	private void migrate() {
		int sides = 0;
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			int x = xCoord + dir.offsetX;
			int y = yCoord + dir.offsetY;
			int z = zCoord + dir.offsetZ;
			if(!worldObj.blockExists(x, y, z)) return;
			if(!worldObj.isAirBlock(x, y, z) && canLink(dir)) sides |= 1 << dir.ordinal();
		}
		connected = (byte) sides;
		legacy = false;
		rebuildNodes();
	}

	private void rebuildNodes() {
		int count = getChannelCount();
		for(int c = 0; c < count; c++) {
			if(getChannelNode(c) == null) continue;
			UniNodespace.destroyNode(worldObj, xCoord, yCoord, zCoord, getChannelType(c).getNetworkProvider());
			if(c == 0) node = null;
			else extraNodes[c - 1] = null;
		}

		refreshValves();
		markDirty();
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	private void refreshValves() {
		int mask = 0;
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			if(!allowsInput(dir)) continue;
			TileEntityFluidPipe other = pipeAt(dir);
			if(other != null && other.isConnected(dir.getOpposite()) && other.isInputDisabled(dir.getOpposite())) mask |= 1 << dir.ordinal();
		}
		valves = (byte) mask;
	}

	private void runValves() {
		int count = getChannelCount();
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			if((valves & (1 << dir.ordinal())) == 0) continue;
			TileEntityFluidPipe other = pipeAt(dir);
			if(other == null) continue;

			for(int c = 0; c < count; c++) {
				FluidType fluid = getChannelType(c);
				int otherChannel = fluid == Fluids.NONE ? -1 : other.channelOf(fluid);
				if(otherChannel < 0) continue;

				FluidNode to = getChannelNode(c);
				FluidNode from = other.getChannelNode(otherChannel);
				if(to == null || from == null || !to.hasValidNet() || !from.hasValidNet() || to.net == from.net) continue;

				long budget = Math.min(to.throughput, from.throughput);
				for(int p = 0; p < PRESSURES && budget > 0; p++) budget -= from.net.pushBufferTo(to.net, p, budget);
			}
		}
	}

	private void ensureExtraNode(int channel) {
		FluidType fluid = extraTypes[channel - 1];
		FluidNode extra = extraNodes[channel - 1];
		if(fluid == Fluids.NONE || (extra != null && !extra.expired)) return;

		extra = (FluidNode) UniNodespace.getNode(worldObj, xCoord, yCoord, zCoord, fluid.getNetworkProvider());
		if(extra == null || extra.expired) {
			extra = createNode(fluid);
			UniNodespace.createNode(worldObj, extra);
		}
		extraNodes[channel - 1] = extra;
	}

	@Override
	public void updateEntity() {
		if(legacy && !worldObj.isRemote) migrate();

		super.updateEntity();

		if(worldObj.isRemote) return;

		int count = getChannelCount();
		for(int c = 1; c < count; c++) ensureExtraNode(c);

		if((worldObj.getTotalWorldTime() + xCoord + yCoord + zCoord) % VALVE_INTERVAL == 0) refreshValves();
		if(valves != 0) runValves();

		if(count == 1 && !locked && (worldObj.getTotalWorldTime() + xCoord + yCoord + zCoord) % AUTO_INTERVAL == 0) manageAuto();

		if((worldObj.getTotalWorldTime() + xCoord + yCoord + zCoord) % ENVIRONMENT_INTERVAL != 0) return;

		BlockFluidPipe pipe = getPipe();
		if(pipe == null) return;
		FluidPipeProperties props = pipe.getProperties();

		for(int c = 0; c < count; c++) {
			FluidType fluid = getChannelType(c);
			FluidNode channelNode = getChannelNode(c);
			if(!isCarrying(fluid, channelNode)) continue;

			int kelvin = fluid.temperature + KELVIN;

			if(kelvin > props.heatResistance) {
				if(worldObj.rand.nextInt(100) == 0) {
					worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.fire);
					return;
				}
				igniteNeighbor();
			}

			if(!props.gasProof && isGas(fluid)) {
				channelNode.net.leak(LEAK_PER_CHECK);
				if(kelvin > 320 || kelvin < 260) {
					AxisAlignedBB area = AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord - 2, zCoord - 2, xCoord + 3, yCoord + 3, zCoord + 3);
					for(Object o : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) hurt((EntityLivingBase) o, kelvin, 25F, 12.5F);
				}
			}
		}
	}

	private void manageAuto() {
		if(type != Fluids.NONE) {
			releaseIfIdle();
			return;
		}

		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			TileEntityFluidPipe other = allowsInput(dir) ? pipeAt(dir) : null;
			if(other == null || !other.hasFluidToGive() || !other.isConnected(dir.getOpposite())) continue;
			adoptFluid(other.type);
			return;
		}
	}

	private boolean hasFluidToGive() {
		if(type == Fluids.NONE || getChannelCount() > 1) return false;
		return locked || (node != null && node.hasValidNet() && node.net.isActive());
	}

	public void offerFluid(FluidType fluid, ForgeDirection side) {
		if(fluid == Fluids.NONE || !isWild() || !allowsInput(side)) return;
		adoptFluid(fluid);
	}

	private void adoptFluid(FluidType fluid) {
		Deque<TileEntityFluidPipe> queue = new ArrayDeque<>();
		setAuto(fluid);
		queue.add(this);

		for(int visited = 0; !queue.isEmpty() && visited < FLOOD_LIMIT; visited++) {
			TileEntityFluidPipe pipe = queue.poll();
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				if(!pipe.isConnected(dir)) continue;
				TileEntityFluidPipe other = pipeAt(pipe.worldObj, pipe.xCoord + dir.offsetX, pipe.yCoord + dir.offsetY, pipe.zCoord + dir.offsetZ);
				if(other == null || !other.isWild() || !other.allowsInput(dir.getOpposite())) continue;
				other.setAuto(fluid);
				queue.add(other);
			}
		}
	}

	private void releaseIfIdle() {
		if(node == null || !node.hasValidNet()) return;
		FluidNetMK2 net = node.net;

		if(net.isActive()) {
			idleChecks = 0;
			return;
		}
		if(++idleChecks < IDLE_CHECKS) return;
		idleChecks = 0;

		List<TileEntityFluidPipe> pipes = new ArrayList<>(net.links.size());
		for(FluidNode link : net.links) {
			BlockPos pos = link.positions[0];
			TileEntityFluidPipe pipe = pipeAt(worldObj, pos.getX(), pos.getY(), pos.getZ());
			if(pipe == null) continue;
			if(pipe.locked || pipe.getChannelCount() > 1) return;
			pipes.add(pipe);
		}

		for(TileEntityFluidPipe pipe : pipes) pipe.setAuto(Fluids.NONE);
	}

	private static boolean isCarrying(FluidType fluid, FluidNode channelNode) {
		return fluid != Fluids.NONE && channelNode != null && channelNode.hasValidNet() && channelNode.net.getBuffered() > 0;
	}

	private static boolean isGas(FluidType fluid) {
		return fluid.hasTrait(FT_Gaseous.class) || fluid.hasTrait(FT_Gaseous_ART.class);
	}

	private void igniteNeighbor() {
		ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[worldObj.rand.nextInt(ForgeDirection.VALID_DIRECTIONS.length)];
		int x = xCoord + dir.offsetX;
		int y = yCoord + dir.offsetY;
		int z = zCoord + dir.offsetZ;
		if(worldObj.isAirBlock(x, y, z) && Blocks.fire.canPlaceBlockAt(worldObj, x, y, z)) worldObj.setBlock(x, y, z, Blocks.fire);
	}

	public void onContact(EntityLivingBase entity) {
		int count = getChannelCount();
		for(int c = 0; c < count; c++) {
			FluidType fluid = getChannelType(c);
			if(isCarrying(fluid, getChannelNode(c))) hurt(entity, fluid.temperature + KELVIN, 50F, 25F);
		}
	}

	private static void hurt(EntityLivingBase entity, int kelvin, float heatDivisor, float frostDivisor) {
		if(kelvin > 320) entity.attackEntityFrom(DamageSource.onFire, (kelvin - 300) / heatDivisor);
		else if(kelvin < 260) entity.attackEntityFrom(DamageSource.generic, (270 - kelvin) / frostDivisor);
	}

	@Override
	public void invalidate() {
		super.invalidate();

		if(worldObj.isRemote || extraNodes == null) return;
		for(int c = 0; c < extraNodes.length; c++) {
			if(extraNodes[c] != null) UniNodespace.destroyNode(worldObj, xCoord, yCoord, zCoord, extraTypes[c].getNetworkProvider());
		}
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 256D;
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		int channel = data.getInteger("channel");
		if(channel < 0 || channel >= getChannelCount() || getChannelCount() == 1) return;

		FluidType fluid = Fluids.fromID(data.getInteger("fluid"));
		int existing = channelOf(fluid);
		if(fluid != Fluids.NONE && existing >= 0 && existing != channel) return;

		setChannelType(channel, fluid);
	}

	@Override
	public int[] getFluidIDToCopy() {
		int count = getChannelCount();
		int[] ids = new int[count];
		for(int c = 0; c < count; c++) ids[c] = getChannelType(c).getID();
		return ids;
	}

	private static String shareKey(int channel, int pressure) {
		return channel == 0 ? "fb" + pressure : "fb" + channel + "_" + pressure;
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		super.onDataPacket(net, pkt);
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		legacy = !nbt.hasKey("con");
		connected = nbt.getByte("con");
		inputOff = nbt.getByte("ino");
		locked = nbt.hasKey("lock") ? nbt.getBoolean("lock") : type != Fluids.NONE;

		int count = Math.max(1, nbt.getByte("ch"));
		if(count > 1) {
			if(extraTypes == null || extraTypes.length != count - 1) allocateExtras(count - 1);
			channelsKnown = true;
			for(int c = 1; c < count; c++) extraTypes[c - 1] = Fluids.fromID(nbt.getInteger("type" + c));
		}

		pending = null;
		for(int c = 0; c < count; c++) {
			for(int p = 0; p < PRESSURES; p++) {
				long amount = nbt.getLong(shareKey(c, p));
				if(amount <= 0) continue;
				if(pending == null) pending = new long[MAX_CHANNELS][];
				if(pending[c] == null) pending[c] = new long[PRESSURES];
				pending[c][p] = amount;
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setByte("con", connected);
		nbt.setByte("ino", inputOff);
		nbt.setBoolean("lock", locked);

		int count = getChannelCount();
		if(count > 1) {
			nbt.setByte("ch", (byte) count);
			for(int c = 1; c < count; c++) nbt.setInteger("type" + c, extraTypes[c - 1].getID());
		}

		for(int c = 0; c < count; c++) {
			FluidNode channelNode = getChannelNode(c);
			boolean inNet = channelNode != null && channelNode.hasValidNet();
			long[] carried = channelNode != null ? channelNode.stored : pending != null ? pending[c] : null;
			for(int p = 0; p < PRESSURES; p++) {
				long amount = inNet ? channelNode.net.getShare(channelNode, p) : carried != null ? carried[p] : 0L;
				if(amount > 0) nbt.setLong(shareKey(c, p), amount);
			}
		}
	}
}
