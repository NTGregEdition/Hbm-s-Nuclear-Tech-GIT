package com.hbm.tileentity.network;

import java.util.ArrayList;
import java.util.List;

import api.hbm.energymk2.CableProperties;
import api.hbm.energymk2.IVoltageCableMK2;
import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import com.hbm.blocks.network.BlockVoltageCable;
import com.hbm.lib.Library;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.Blocks;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityVoltageCable extends TileEntityCableBaseNT implements IVoltageCableMK2 {

	private static final CableProperties FALLBACK_PROPERTIES = new CableProperties("unconfigured", Long.MAX_VALUE, Long.MAX_VALUE, 0L);

	private long amperageTransferredThisTick;
	private long amperageOverloadBuffer;
	private long lastPowerTick = Long.MIN_VALUE;
	private boolean burnQueued;
	private int pendingBurnTicks;
	private long pendingBurnVoltage;
	private long pendingBurnAmperage;
	private byte disconnectedSides;
	private byte forcedOpenSides;
	private byte pendingAirCutResets;

	@Override
	public CableProperties getCableProperties() {
		if(this.getBlockType() instanceof BlockVoltageCable) {
			return ((BlockVoltageCable) this.getBlockType()).getProperties();
		}
		return FALLBACK_PROPERTIES;
	}

	@Override
	public boolean canConnect(ForgeDirection direction) {
		if(!isConnectionEnabled(direction) || worldObj == null) return false;
		TileEntity neighbour = worldObj.getTileEntity(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ);
		if(neighbour instanceof TileEntityVoltageCable) {
			TileEntityVoltageCable cable = (TileEntityVoltageCable) neighbour;
			return cable.isConnectionEnabled(direction.getOpposite());
		}
		if(neighbour instanceof TileEntityVoltageTransformer) {
			return ((TileEntityVoltageTransformer) neighbour).canConnectCable(direction.getOpposite(), getCableProperties().voltage);
		}
		if(neighbour instanceof TileEntityCableBaseNT) return false;
		return true;
	}

	public boolean isConnectionEnabled(ForgeDirection direction) {
		if(direction == null || direction == ForgeDirection.UNKNOWN) return false;
		return (disconnectedSides & (1 << direction.ordinal())) == 0;
	}

	@Override
	public boolean isFaceConnected(ForgeDirection direction) {
		return isConnectionEnabled(direction);
	}

	public boolean isConnectionForced(ForgeDirection direction) {
		if(direction == null || direction == ForgeDirection.UNKNOWN || worldObj == null) return false;
		int bit = 1 << direction.ordinal();
		return (forcedOpenSides & bit) != 0;
	}

	public boolean isConnected(ForgeDirection direction) {
		if(!isConnectionEnabled(direction)) return false;
		if(canConnect(direction)
				&& Library.canConnect(worldObj, xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ, direction)) return true;
		return isConnectionForced(direction);
	}

	public boolean toggleConnection(ForgeDirection direction) {
		if(direction == null || direction == ForgeDirection.UNKNOWN || worldObj == null || worldObj.isRemote) return false;
		int bit = 1 << direction.ordinal();
		boolean mirrorCut = false;
		boolean mirrorReconnect = false;
		if((forcedOpenSides & bit) != 0) {
			forcedOpenSides = (byte) (forcedOpenSides & ~bit);
			updateMatchingCableOpenEnd(direction, false);
			if(hasAutomaticConnection(direction)) {
				addCut(direction);
				mirrorCut = true;
			}
		} else if((disconnectedSides & bit) != 0) {
			removeCut(direction);
			mirrorReconnect = true;
		} else if(hasAutomaticConnection(direction)) {
			addCut(direction);
			mirrorCut = true;
		} else {
			forcedOpenSides |= (byte) bit;
			updateMatchingCableOpenEnd(direction, true);
		}
		if(mirrorCut) updateMatchingCableCut(direction, true);
		if(mirrorReconnect) updateMatchingCableCut(direction, false);
		rebuildPowerNode();
		markDirty();
		refreshCableRender();
		return true;
	}

	private void updateMatchingCableOpenEnd(ForgeDirection direction, boolean open) {
		TileEntity neighbour = worldObj.getTileEntity(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ);
		if(!(neighbour instanceof TileEntityVoltageCable)) return;
		TileEntityVoltageCable cable = (TileEntityVoltageCable) neighbour;
		int bit = 1 << direction.getOpposite().ordinal();
		if(open) cable.forcedOpenSides |= (byte) bit;
		else cable.forcedOpenSides = (byte) (cable.forcedOpenSides & ~bit);
		cable.markDirty();
		cable.refreshCableRender();
	}

	private void updateMatchingCableCut(ForgeDirection direction, boolean cut) {
		TileEntity neighbour = worldObj.getTileEntity(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ);
		if(!(neighbour instanceof TileEntityVoltageCable)) return;
		TileEntityVoltageCable cable = (TileEntityVoltageCable) neighbour;

		ForgeDirection opposite = direction.getOpposite();
		if(cut) {
			cable.forcedOpenSides = (byte) (cable.forcedOpenSides & ~(1 << opposite.ordinal()));
			cable.addCut(opposite);
		} else {
			cable.removeCut(opposite);
		}
		cable.rebuildPowerNode();
		cable.markDirty();
		cable.refreshCableRender();
	}

	private void addCut(ForgeDirection direction) {
		disconnectedSides |= (byte) (1 << direction.ordinal());
	}

	private void removeCut(ForgeDirection direction) {
		disconnectedSides = (byte) (disconnectedSides & ~(1 << direction.ordinal()));
	}

	private boolean hasAutomaticConnection(ForgeDirection direction) {
		return canConnect(direction)
				&& Library.canConnect(worldObj, xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ, direction);
	}

	public void clearCutsForChangedNeighbours() {
		if(worldObj == null || worldObj.isRemote || disconnectedSides == 0) return;
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			int bit = 1 << direction.ordinal();
			if((disconnectedSides & bit) == 0) continue;
			int x = xCoord + direction.offsetX;
			int y = yCoord + direction.offsetY;
			int z = zCoord + direction.offsetZ;
			if(worldObj.blockExists(x, y, z) && worldObj.isAirBlock(x, y, z)) {
				pendingAirCutResets |= (byte) bit;
			} else {
				pendingAirCutResets = (byte) (pendingAirCutResets & ~bit);
			}
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();
		if(worldObj == null || worldObj.isRemote) return;
		if(pendingBurnTicks > 0 && --pendingBurnTicks == 0) burnForWrongVoltage();
		if(pendingAirCutResets == 0) return;

		byte pending = pendingAirCutResets;
		pendingAirCutResets = 0;
		boolean changed = false;
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			int bit = 1 << direction.ordinal();
			if((pending & bit) == 0 || (disconnectedSides & bit) == 0) continue;
			int x = xCoord + direction.offsetX;
			int y = yCoord + direction.offsetY;
			int z = zCoord + direction.offsetZ;
			if(worldObj.blockExists(x, y, z) && worldObj.isAirBlock(x, y, z)) {
				removeCut(direction);
				changed = true;
			}
		}
		if(changed) {
			rebuildPowerNode();
			markDirty();
			refreshCableRender();
		}
	}

	@Override
	public PowerNode createNode() {
		List<DirPos> connections = new ArrayList<DirPos>();
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if(isConnectionEnabled(direction)) {
				connections.add(new DirPos(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ, direction));
			}
		}
		return new PowerNode(new BlockPos(xCoord, yCoord, zCoord)).setConnections(connections.toArray(new DirPos[connections.size()]));
	}

	private void rebuildPowerNode() {
		if(node != null) {
			Nodespace.destroyNode(worldObj, xCoord, yCoord, zCoord);
			node = null;
		}
	}

	private void refreshCableRender() {
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			worldObj.markBlockForUpdate(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ);
		}
	}

	@Override
	public void beginPowerTick() {
		amperageTransferredThisTick = 0L;
	}

	@Override
	public long getRemainingTransfer() {
		return Math.max(0L, getCableProperties().maxAmperage - amperageTransferredThisTick);
	}

	@Override
	public long useTransferCapacity(long amount) {
		long used = Math.min(Math.max(0L, amount), getRemainingTransfer());
		amperageTransferredThisTick += used;
		return used;
	}

	@Override
	public void recordAmperageFlow(long voltage, long amperage, long pathMaxAmperage, int delayTicks, TileEntity source) {
		if(amperage <= 0L || burnQueued || worldObj == null || worldObj.isRemote) return;
		long currentTick = worldObj.getTotalWorldTime();
		long elapsedTicks = lastPowerTick == Long.MIN_VALUE ? 1L : Math.max(0L, currentTick - lastPowerTick);
		if(elapsedTicks > 100L) {
			amperageOverloadBuffer = 0L;
		} else if(elapsedTicks > 0L) {
			long decayAmperage = Math.max(0L, pathMaxAmperage);
			long decay = decayAmperage > Long.MAX_VALUE / elapsedTicks ? Long.MAX_VALUE : decayAmperage * elapsedTicks;
			amperageOverloadBuffer = Math.max(0L, amperageOverloadBuffer - decay);
		}
		lastPowerTick = currentTick;
		amperageTransferredThisTick = addClamped(amperageTransferredThisTick, amperage);
		amperageOverloadBuffer = addClamped(amperageOverloadBuffer, amperage);
		long maxAmperage = getCableProperties().maxAmperage;
		// GT tolerates a 40-tick current surplus before a cable burns.
		long overloadLimit = maxAmperage > Long.MAX_VALUE / 40L ? Long.MAX_VALUE : maxAmperage * 40L;
		if(amperageOverloadBuffer > overloadLimit) {
			queueBurn(voltage, amperageTransferredThisTick, delayTicks);
		}
	}

	@Override
	public void explodeForWrongVoltage(long suppliedVoltage) {
		explodeForWrongVoltage(suppliedVoltage, 1);
	}

	@Override
	public void explodeForWrongVoltage(long suppliedVoltage, int delayTicks) {
		burnForElectricalOverload(suppliedVoltage, 0L, delayTicks);
	}

	@Override
	public void burnForElectricalOverload(long suppliedVoltage, long suppliedAmperage, int delayTicks) {
		CableProperties properties = getCableProperties();
		if(burnQueued || worldObj == null || worldObj.isRemote
				|| (suppliedVoltage <= properties.voltage && suppliedAmperage <= properties.maxAmperage)) return;
		queueBurn(suppliedVoltage, suppliedAmperage, delayTicks);
	}

	@Override
	public void burnForElectricalOverload(long suppliedVoltage, long suppliedAmperage, int delayTicks, TileEntity source) {
		burnForElectricalOverload(suppliedVoltage, suppliedAmperage, delayTicks);
	}

	private void queueBurn(long suppliedVoltage, long attemptedAmperage, int delayTicks) {
		burnQueued = true;
		pendingBurnVoltage = suppliedVoltage;
		pendingBurnAmperage = attemptedAmperage;
		pendingBurnTicks = Math.max(1, delayTicks);
		spawnTopFire();
		markDirty();
	}

	public boolean isElectricalBurnQueued() {
		return burnQueued;
	}

	private void spawnTopFire() {
		int fireY = yCoord + 1;
		if(worldObj.isAirBlock(xCoord, fireY, zCoord)) {
			worldObj.setBlock(xCoord, fireY, zCoord, Blocks.fire, 0, 3);
		}
	}

	private void burnForWrongVoltage() {
		long voltage = pendingBurnVoltage;
		long amperage = pendingBurnAmperage;
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if(!isConnectionEnabled(direction)) continue;
			TileEntity neighbour = worldObj.getTileEntity(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ);
			if(!(neighbour instanceof TileEntityVoltageCable)) continue;
			TileEntityVoltageCable cable = (TileEntityVoltageCable) neighbour;
			ForgeDirection opposite = direction.getOpposite();
			if(!cable.isConnectionEnabled(opposite) || !canConnect(direction) || !cable.canConnect(opposite)) continue;
			cable.burnForElectricalOverload(voltage, amperage, 2);
		}

		worldObj.setBlockToAir(xCoord, yCoord, zCoord);
		worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.fire, 0, 3);
	}

	private long addClamped(long first, long second) {
		return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		disconnectedSides = nbt.getByte("voltageCableDisconnectedSides");
		forcedOpenSides = nbt.getByte("voltageCableForcedOpenSides");
		burnQueued = nbt.getBoolean("voltageCableBurnQueued");
		pendingBurnTicks = Math.max(0, nbt.getInteger("voltageCableBurnTicks"));
		pendingBurnVoltage = nbt.getLong("voltageCableBurnVoltage");
		pendingBurnAmperage = nbt.getLong("voltageCableBurnAmperage");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setByte("voltageCableDisconnectedSides", disconnectedSides);
		nbt.setByte("voltageCableForcedOpenSides", forcedOpenSides);
		nbt.setBoolean("voltageCableBurnQueued", burnQueued);
		nbt.setInteger("voltageCableBurnTicks", pendingBurnTicks);
		nbt.setLong("voltageCableBurnVoltage", pendingBurnVoltage);
		nbt.setLong("voltageCableBurnAmperage", pendingBurnAmperage);
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
		readFromNBT(packet.func_148857_g());
		if(worldObj != null) refreshCableRender();
	}

}
