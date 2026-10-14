package com.hbm.blocks.network;

import com.hbm.tileentity.TilePort.PortDef;
import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.uninos.GenNode;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.hbm.world.gen.nbt.INBTBlockTransformable;

import api.hbm.block.IToolable;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.VoltageTier;
import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.IFluidPumpMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

public class VoltagePump extends BlockContainer implements INBTBlockTransformable, ILookOverlay, IToolable, ITooltipProvider {

	public VoltagePump(Material mat) {
		super(mat);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityVoltagePump();
	}

	@Override public int getRenderType() { return -1; }
	@Override public boolean isOpaqueCube() { return false; }
	@Override public boolean renderAsNormalBlock() { return false; }

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemStack) {

		int i = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;

		if(i == 0) world.setBlockMetadataWithNotify(x, y, z, 2, 2);
		if(i == 1) world.setBlockMetadataWithNotify(x, y, z, 5, 2);
		if(i == 2) world.setBlockMetadataWithNotify(x, y, z, 3, 2);
		if(i == 3) world.setBlockMetadataWithNotify(x, y, z, 4, 2);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float fX, float fY, float fZ) {

		if(player.isSneaking()) return false;

		ItemStack held = player.getHeldItem();
		if(held == null) return false;

		TileEntity tile = world.getTileEntity(x, y, z);
		if(!(tile instanceof TileEntityVoltagePump)) return false;
		TileEntityVoltagePump pump = (TileEntityVoltagePump) tile;

		if(held.getItem() instanceof IItemFluidIdentifier) {
			if(!world.isRemote) {
				FluidType type = ((IItemFluidIdentifier) held.getItem()).getType(world, x, y, z, held);
				pump.tank[0].setTankType(type);
				pump.markDirty();
			}
			return true;
		}

		if(PumpUpgrades.isPumpItem(held, false) && pump.pump == PumpUpgrades.NONE) {
			if(!world.isRemote) pump.installPump(player, held);
			return true;
		}

		return false;
	}

	@Override
	public boolean onScrew(World world, EntityPlayer player, int x, int y, int z, int side, float fX, float fY, float fZ, ToolType tool) {

		if(!player.isSneaking() || tool != ToolType.WRENCH) return false;

		TileEntity tile = world.getTileEntity(x, y, z);
		if(!(tile instanceof TileEntityVoltagePump)) return false;
		TileEntityVoltagePump pump = (TileEntityVoltagePump) tile;
		if(pump.pump == PumpUpgrades.NONE) return false;

		if(!world.isRemote) {
			PumpUpgrades.giveBack(player, ModItems.voltage_electric_pump, pump.pump);
			pump.pump = PumpUpgrades.NONE;
			pump.running = false;
			pump.markDirty();
		}
		return true;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

		TileEntity tile = world.getTileEntity(x, y, z);

		if(tile instanceof TileEntityVoltagePump) {
			TileEntityVoltagePump pump = (TileEntityVoltagePump) tile;
			if(pump.pump != PumpUpgrades.NONE && !world.isRemote) {
				this.dropBlockAsItem(world, x, y, z, new ItemStack(ModItems.voltage_electric_pump, 1, pump.pump));
			}
		}

		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {
		TileEntity tile = world.getTileEntity(x, y, z);
		if(!(tile instanceof TileEntityVoltagePump)) return;
		TileEntityVoltagePump pump = (TileEntityVoltagePump) tile;

		List<String> text = new ArrayList();
		text.add(EnumChatFormatting.GREEN + "-> " + EnumChatFormatting.RESET + pump.tank[0].getTankType().getLocalizedName() + " (" + pump.tank[0].getPressure() + " PU): " + BobMathUtil.format(pump.getFlow()) + "mB/t" + EnumChatFormatting.RED + " ->" + EnumChatFormatting.RESET);

		if(pump.pump == PumpUpgrades.NONE) {
			text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("hbm.pump.noPump") + EnumChatFormatting.RESET);
			text.add(I18nUtil.resolveKey("hbm.pump.hintInstall"));
		} else {
			text.add(I18nUtil.resolveKey("hbm.pump.installed", PumpUpgrades.tierName(pump.pump)));
			text.add(I18nUtil.resolveKey("hbm.pump.voltage", VoltageTier.format(PumpUpgrades.voltageOf(pump.pump))));

			boolean withinLimit = pump.isWithinPipeLimit();
			text.add((withinLimit ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + I18nUtil.resolveKey("hbm.pump.pipes", BobMathUtil.format(pump.getPipes()), BobMathUtil.format(pump.getPipeLimit())) + EnumChatFormatting.RESET);
			if(!withinLimit) text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("hbm.pump.overLimit") + EnumChatFormatting.RESET);

			text.add((pump.running ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + I18nUtil.resolveKey("hbm.pump.buffer", BobMathUtil.format(pump.power), BobMathUtil.format(pump.getMaxPower())) + EnumChatFormatting.RESET);
			text.add(I18nUtil.resolveKey("hbm.pump.draw", BobMathUtil.format(pump.getEnergyDemand())));
			if(!pump.running) text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("hbm.pump.noPower") + EnumChatFormatting.RESET);

			text.add(I18nUtil.resolveKey("hbm.pump.hintRemove"));
		}

		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	@Override
	public int transformMeta(int meta, int coordBaseMode) {
		return INBTBlockTransformable.transformMetaDeco(meta, coordBaseMode);
	}

	@Override
	@SuppressWarnings("rawtypes")
	public void addInformation(ItemStack stack, EntityPlayer player, java.util.List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}

	public static class TileEntityVoltagePump extends TileEntityLoadedBase implements IFluidStandardTransceiverMK2, IEnergyReceiverMK2, IFluidPumpMK2 {

		public final FluidTank[] tank = {new FluidTank(Fluids.NONE, 0)};
		public int pump = PumpUpgrades.NONE;
		public long power;
		private long moved;

		public int pullSize;
		public int pushSize;

		public boolean running;

		protected PortDef inPort, outPort, powerPort;

		public int getFlow() {
			return PumpUpgrades.flow(PumpUpgrades.ELECTRIC_PUMP, this.pump);
		}

		public int getPipeLimit() {
			return PumpUpgrades.pipes(PumpUpgrades.ELECTRIC_PUMP, this.pump);
		}

		public int getPipes() {
			return this.pullSize + this.pushSize;
		}

		public boolean isWithinPipeLimit() {
			return this.pump != PumpUpgrades.NONE && this.getPipes() <= this.getPipeLimit();
		}

		public long getEnergyDemand() {
			return PumpUpgrades.idle(PumpUpgrades.ELECTRIC_PUMP, this.pump) + PumpUpgrades.energyForMoved(PumpUpgrades.ELECTRIC_PUMP, this.pump, this.moved);
		}

		public long getMaxDemand() {
			return PumpUpgrades.maxDrain(PumpUpgrades.ELECTRIC_PUMP, this.pump);
		}

		public void installPump(EntityPlayer player, ItemStack stack) {
			this.pump = PumpUpgrades.tierOf(stack);
			if(!player.capabilities.isCreativeMode) player.inventory.decrStackSize(player.inventory.currentItem, 1);
			this.markDirty();
		}

		@SuppressWarnings("unchecked")
		private FluidNetMK2 getNetwork(ForgeDirection dir) {
			FluidType type = this.tank[0].getTankType();
			if(type == null) return null;
			GenNode<FluidNetMK2> node = UniNodespace.getNode(this.worldObj, this.xCoord + dir.offsetX, this.yCoord + dir.offsetY, this.zCoord + dir.offsetZ, type.getNetworkProvider());
			return node != null && node.hasValidNet() ? node.net : null;
		}

		@Override
		public void updateEntity() {

			if(this.worldObj.isRemote) return;

			/* the tank doubles as throughput limit and as buffer, resizing it directly would destroy
			 * whatever fluid is currently buffered, so the fill is kept as the new size if need be */
			int flow = this.getFlow();
			if(flow != this.tank[0].getMaxFill()) this.tank[0].changeTankSize(Math.max(this.tank[0].getFill(), flow));

			/* pay the standby drain plus the cost of the fluid moved through this pump during the last tick */
			long demand = this.getEnergyDemand();
			this.running = this.pump != PumpUpgrades.NONE && this.power >= demand;
			if(this.running) this.power -= demand;
			this.moved = 0L;

			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
			ForgeDirection in = dir.getRotation(ForgeDirection.UP);
			ForgeDirection out = in.getOpposite();

			/* ports are built from the full tank array, the sending/receiving getters gate them. an uninstalled
			 * or unpowered pump therefore never joins a fluid network and must not count as a pump */
			if(this.inPort == null) {
				this.inPort = PortDef.make(this.xCoord, this.yCoord, this.zCoord, in);
				this.outPort = PortDef.make(this.xCoord, this.yCoord, this.zCoord, out);
				List<ForgeDirection> powerDirs = new ArrayList<ForgeDirection>();
				for(ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) if(d != in && d != out) powerDirs.add(d);
				this.powerPort = PortDef.make(this.xCoord, this.yCoord, this.zCoord, powerDirs.toArray(new ForgeDirection[0]));
			}
			this.setupFluidInPorts(this.tank, this.inPort);
			this.setupFluidOutPorts(this.tank, this.outPort);
			this.setupPowerPorts(new PortDef[] {this.powerPort});

			FluidNetMK2 pullNet = this.getNetwork(in);
			FluidNetMK2 pushNet = this.getNetwork(out);
			this.pullSize = pullNet == null ? 0 : pullNet.links.size();
			this.pushSize = pushNet == null || pushNet == pullNet ? 0 : pushNet.links.size();

			this.updatePortPIFIFO();

			this.networkPackNT(20);
		}

		/* this is how the pump knows how much fluid passed through it, the network calls it when
		 * it fills the pump's tank up. the default useUpFluid then drains it again on the output side */

		@Override
		public long transferFluid(FluidType type, int pressure, long amount) {

			if(this.tank[0].getTankType() != type || this.tank[0].getPressure() != pressure) return amount;

			int toAdd = (int) Math.min(amount, this.tank[0].getMaxFill() - this.tank[0].getFill());

			if(toAdd > 0) {
				this.tank[0].setFill(this.tank[0].getFill() + toAdd);
				this.moved += toAdd;
				amount -= toAdd;
			}

			return amount;
		}

		@Override
		public void writeToNBT(NBTTagCompound nbt) {
			super.writeToNBT(nbt);
			this.tank[0].writeToNBT(nbt, "t");
			nbt.setLong("power", this.power);
			if(this.pump != PumpUpgrades.NONE) nbt.setByte("pump", (byte) this.pump);
		}

		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			super.readFromNBT(nbt);
			this.tank[0].readFromNBT(nbt, "t");
			this.power = nbt.getLong("power");
			this.pump = nbt.hasKey("pump") ? nbt.getByte("pump") : PumpUpgrades.NONE;
		}

		@Override
		public void serialize(ByteBuf buf) {
			super.serialize(buf);
			this.tank[0].serialize(buf);
			buf.writeByte(this.pump);
			buf.writeInt(this.pullSize);
			buf.writeInt(this.pushSize);
			buf.writeLong(this.power);
			buf.writeBoolean(this.running);
		}

		@Override
		public void deserialize(ByteBuf buf) {
			super.deserialize(buf);
			this.tank[0].deserialize(buf);
			this.pump = buf.readByte();
			this.pullSize = buf.readInt();
			this.pushSize = buf.readInt();
			this.power = buf.readLong();
			this.running = buf.readBoolean();
		}

		@Override public FluidTank[] getSendingTanks() { return this.running ? this.tank : FluidTank.EMPTY_ARRAY; }
		@Override public FluidTank[] getReceivingTanks() { return this.running && this.tank[0].getFill() < this.tank[0].getMaxFill() ? this.tank : FluidTank.EMPTY_ARRAY; }
		@Override public FluidTank[] getAllTanks() { return this.tank; }

		@Override public long getPower() { return this.power; }
		@Override public void setPower(long power) { this.power = power; }
		/** the buffer never shrinks below whatever is stored, so no energy is destroyed when the item changes */
		@Override public long getMaxPower() { return Math.max(PumpUpgrades.capacity(PumpUpgrades.ELECTRIC_PUMP, this.pump), this.power); }
		@Override public long getVoltage() { return this.pump == PumpUpgrades.NONE ? VoltageTier.DEFAULT : PumpUpgrades.voltageOf(this.pump); }

		@Override
		public void onOvervoltage(long voltage) {
			this.dropInstalledItem();
			IEnergyReceiverMK2.super.onOvervoltage(voltage);
		}

		/** Drops the installed pump item, used when the machine is destroyed rather than broken */
		public void dropInstalledItem() {
			if(this.pump == PumpUpgrades.NONE || this.worldObj == null || this.worldObj.isRemote) return;
			this.worldObj.spawnEntityInWorld(new EntityItem(this.worldObj, this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D, new ItemStack(ModItems.voltage_electric_pump, 1, this.pump)));
			this.pump = PumpUpgrades.NONE;
		}

		@Override public int getPipeBudget() { return this.getPipeLimit(); }
		@Override public boolean isPumpRunning() { return this.running && this.pump != PumpUpgrades.NONE; }
		@Override public int getPumpStrength() { return this.isPumpRunning() && this.isWithinPipeLimit() ? this.getPipeLimit() : 0; }
	}
}
