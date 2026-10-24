package com.hbm.blocks.network;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.VoltageTier;
import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.IFluidPumpMK2;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.tileentity.TileEntityLoadedBase;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

abstract class TileEntityVoltagePumpBase extends TileEntityLoadedBase implements IEnergyReceiverMK2, IFluidPumpMK2 {

	public long power;
	protected long moved;
	public int pullSize;
	public int pushSize;
	public boolean running;
	protected boolean passing;

	protected abstract PumpUpgrades.Stats[] getUpgradeType();
	protected abstract int getInstalledUpgrade();
	protected abstract void destroyInstalledUpgrade();

	public int getPipeLimit() {
		return PumpUpgrades.pipes(getUpgradeType(), getInstalledUpgrade());
	}

	public int getPipes() {
		return Math.max(pullSize, pushSize);
	}

	public boolean isWithinPipeLimit() {
		return getInstalledUpgrade() != PumpUpgrades.NONE
				&& pullSize <= getPipeLimit()
				&& pushSize <= getPipeLimit();
	}

	public long getEnergyDemand() {
		return (PumpUpgrades.idle(getUpgradeType(), getInstalledUpgrade())
				+ PumpUpgrades.energyForMoved(getUpgradeType(), getInstalledUpgrade(), moved)) / 2L;
	}

	protected void updateRunningState() {
		long demand = getEnergyDemand();
		running = getInstalledUpgrade() != PumpUpgrades.NONE && power >= demand;
		if(running) power -= demand;
		moved = 0L;
	}

	protected void updatePipeCounts(FluidNetMK2 pullNet, FluidNetMK2 pushNet) {
		pullSize = pullNet == null ? 0 : pullNet.links.size();
		pushSize = pushNet == null || pushNet == pullNet ? 0 : pushNet.links.size();
	}

	protected long transferToPushNet(FluidNetMK2 pullNet, FluidNetMK2 pushNet, FluidType type, int pressure, long amount, long demand) {
		long toMove = Math.min(amount, demand);
		if(passing || toMove <= 0L || pushNet == null || pushNet == pullNet) return amount;

		passing = true;
		long sent = toMove - pushNet.passThrough(type, pressure, toMove);
		passing = false;
		if(sent > 0L) {
			moved += sent;
			onFluidTransferred(sent);
		}
		return amount - sent;
	}

	protected void onFluidTransferred(long amount) { }

	protected void writePumpPower(NBTTagCompound nbt) {
		nbt.setLong("power", power);
	}

	protected void readPumpPower(NBTTagCompound nbt) {
		power = nbt.getLong("power");
	}

	protected void serializePumpState(ByteBuf buf) {
		buf.writeInt(pullSize);
		buf.writeInt(pushSize);
		buf.writeLong(power);
		buf.writeBoolean(running);
	}

	protected void deserializePumpState(ByteBuf buf) {
		pullSize = buf.readInt();
		pushSize = buf.readInt();
		power = buf.readLong();
		running = buf.readBoolean();
	}

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = power; }
	@Override public long getMaxPower() { return Math.max(PumpUpgrades.capacity(getUpgradeType(), getInstalledUpgrade()), power); }
	@Override public long getVoltage() { return getInstalledUpgrade() == PumpUpgrades.NONE ? VoltageTier.DEFAULT : PumpUpgrades.voltageOf(getInstalledUpgrade()); }
	@Override public int getPipeBudget() { return getPipeLimit(); }
	@Override public boolean isPumpRunning() { return running && getInstalledUpgrade() != PumpUpgrades.NONE; }
	@Override public int getPumpStrength() { return isPumpRunning() && isWithinPipeLimit() ? getPipeLimit() : 0; }

	@Override
	public void onOvervoltage(long voltage) {
		destroyInstalledUpgrade();
		IEnergyReceiverMK2.super.onOvervoltage(voltage);
	}
}
