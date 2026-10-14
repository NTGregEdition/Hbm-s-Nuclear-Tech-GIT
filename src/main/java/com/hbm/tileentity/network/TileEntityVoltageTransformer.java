package com.hbm.tileentity.network;

import api.hbm.energymk2.IEnergyConductorMK2;
import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import api.hbm.energymk2.PowerNetMK2;
import api.hbm.energymk2.TransformerProperties;
import com.hbm.blocks.network.BlockVoltageTransformer;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityVoltageTransformer extends TileEntityLoadedBase implements IEnergyReceiverMK2, IEnergyProviderMK2, IEnergyConductorMK2 {

	private PowerNode outputNode;
	private PowerNetMK2 inputNet;
	private PowerNetMK2 outputNet;
	private long pendingInput;
	private long pendingOutput;
	private long conversionRemainder;

	private TransformerProperties getProperties() {
		if(getBlockType() instanceof BlockVoltageTransformer) {
			return ((BlockVoltageTransformer) getBlockType()).getProperties();
		}
		throw new IllegalStateException("Voltage transformer tile placed without a transformer block");
	}

	public ForgeDirection getInputDirection() {
		ForgeDirection direction = ForgeDirection.getOrientation(getBlockMetadata());
		return direction == ForgeDirection.UNKNOWN ? ForgeDirection.NORTH : direction;
	}

	public ForgeDirection getOutputDirection() {
		return getInputDirection().getOpposite();
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) return;
		pendingOutput = 0L;

		if(outputNode == null || outputNode.expired) {
			outputNode = Nodespace.getNode(worldObj, xCoord, yCoord, zCoord);
			if(outputNode == null || outputNode.expired) {
				outputNode = createNode();
				Nodespace.createNode(worldObj, outputNode);
			}
		}

		if(worldObj.getTotalWorldTime() % 20L == 0L) subscribeInput();
		convertPendingInput();

		provideOutput();
	}

	/** Registers this transformer as a provider on the net of the cable attached to its output side, dropping out of the previous net if that changed */
	private void provideOutput() {
		ForgeDirection output = getOutputDirection();
		int x = xCoord + output.offsetX, y = yCoord + output.offsetY, z = zCoord + output.offsetZ;
		PowerNetMK2 target = null;

		TileEntity outputTile = worldObj.getTileEntity(x, y, z);
		if(outputTile instanceof IEnergyConductorMK2 && ((IEnergyConductorMK2) outputTile).canConnect(output.getOpposite())) {
			PowerNode node = Nodespace.getNode(worldObj, x, y, z);
			if(node != null && node.net != null) target = node.net;
		}

		if(outputNet != null && outputNet != target) outputNet.removeProvider(this);
		outputNet = target;
		if(target != null) target.addProviderAt(this, x, y, z, output.getOpposite());
	}

	private void subscribeInput() {
		ForgeDirection input = getInputDirection();
		int x = xCoord + input.offsetX, y = yCoord + input.offsetY, z = zCoord + input.offsetZ;
		PowerNetMK2 target = null;

		TileEntity inputTile = worldObj.getTileEntity(x, y, z);
		boolean wrongVoltage = inputTile instanceof TileEntityVoltageCable
				&& ((TileEntityVoltageCable) inputTile).getCableProperties().voltage != getProperties().inputVoltage;
		if(!wrongVoltage) {
			PowerNode node = Nodespace.getNode(worldObj, x, y, z);
			if(node != null && node.net != null) target = node.net;
		}

		if(inputNet != null && inputNet != target) inputNet.removeReceiver(this);
		inputNet = target;
		if(target != null) target.addReceiverAt(this, x, y, z, input.getOpposite());
	}

	private void convertPendingInput() {
		TransformerProperties properties = getProperties();
		long input = Math.min(pendingInput, properties.maxInputEnergyPerTick);
		pendingInput = 0L;
		if(input <= 0) return;

		long numerator = input * properties.inputVoltage + conversionRemainder;
		long converted = numerator / properties.outputVoltage;
		pendingOutput = converted;
		conversionRemainder = numerator % properties.outputVoltage;
		markDirty();
	}

	@Override
	public PowerNode createNode() {
		ForgeDirection output = getOutputDirection();
		return new PowerNode(new BlockPos(xCoord, yCoord, zCoord)).setConnections(
				new DirPos(xCoord + output.offsetX, yCoord + output.offsetY, zCoord + output.offsetZ, output));
	}

	@Override
	public boolean canConnect(ForgeDirection direction) {
		return direction == getInputDirection() || direction == getOutputDirection();
	}

	public boolean canConnectCable(ForgeDirection transformerSide, long cableVoltage) {
		if(transformerSide == getInputDirection()) return cableVoltage == getProperties().inputVoltage;
		if(transformerSide == getOutputDirection()) return cableVoltage == getProperties().outputVoltage;
		return false;
	}

	@Override
	public long getPower() { return pendingOutput; }
	@Override
	public void setPower(long power) { pendingOutput = Math.max(0L, Math.min(power, getProviderMaxPower())); }
	@Override
	public long getMaxPower() { return getProviderMaxPower(); }

	@Override
	public long getReceiverPower() { return pendingInput; }
	@Override
	public void setReceiverPower(long power) { pendingInput = Math.max(0L, Math.min(power, getReceiverMaxPower())); }
	@Override
	public long getReceiverMaxPower() { return getProperties().maxInputEnergyPerTick; }
	@Override
	public long getReceiverVoltage() { return getProperties().inputVoltage; }
	@Override
	public long getReceiverSpeed() { return getProperties().maxInputEnergyPerTick; }

	@Override
	public long getProviderPower() { return pendingOutput; }
	@Override
	public void setProviderPower(long power) { pendingOutput = Math.max(0L, Math.min(power, getProviderMaxPower())); }
	@Override
	public long getProviderMaxPower() { return getProperties().getMaxOutputEnergyPerTick(); }
	@Override
	public long getProviderVoltage() { return getProperties().outputVoltage; }
	@Override
	public long getProviderSpeed() { return getProperties().getMaxOutputEnergyPerTick(); }

	@Override
	public void invalidate() {
		super.invalidate();
		if(inputNet != null) inputNet.removeReceiver(this);
		if(outputNet != null) outputNet.removeProvider(this);
		if(worldObj != null && !worldObj.isRemote && outputNode != null) {
			Nodespace.destroyNode(worldObj, xCoord, yCoord, zCoord);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		conversionRemainder = Math.max(0L, nbt.getLong("transformerRemainder"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("transformerRemainder", conversionRemainder);
	}
}
