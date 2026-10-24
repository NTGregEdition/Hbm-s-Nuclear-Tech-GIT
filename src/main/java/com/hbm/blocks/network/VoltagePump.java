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

	public static class TileEntityVoltagePump extends TileEntityVoltagePumpBase implements IFluidStandardTransceiverMK2 {

		public final FluidTank[] tank = {new FluidTank(Fluids.NONE, 0)};
		public int pump = PumpUpgrades.NONE;

		private FluidNetMK2 pullNet, pushNet;
		protected PortDef powerPort;

		public int getFlow() {
			return PumpUpgrades.flow(PumpUpgrades.ELECTRIC_PUMP, this.pump);
		}

		@Override protected PumpUpgrades.Stats[] getUpgradeType() { return PumpUpgrades.ELECTRIC_PUMP; }
		@Override protected int getInstalledUpgrade() { return pump; }
		@Override protected void destroyInstalledUpgrade() { pump = PumpUpgrades.NONE; }

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

			/* pay the standby drain plus the cost of the fluid moved through this pump during the last tick */
			this.updateRunningState();

			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
			ForgeDirection in = dir.getRotation(ForgeDirection.UP);
			ForgeDirection out = in.getOpposite();

			if(this.powerPort == null) {
				List<ForgeDirection> powerDirs = new ArrayList<ForgeDirection>();
				for(ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) if(d != in && d != out) powerDirs.add(d);
				this.powerPort = PortDef.make(this.xCoord, this.yCoord, this.zCoord, powerDirs.toArray(new ForgeDirection[0]));
			}
			this.setupPowerPorts(new PortDef[] {this.powerPort});

			// no node of its own, that would merge both sides into one network, so it subscribes to the two neighbouring networks instead
			this.pullNet = this.getNetwork(in);
			this.pushNet = this.getNetwork(out);
			if(this.pullNet != null) this.pullNet.addReceiver(this);
			if(this.pushNet != null) this.pushNet.addProvider(this);
			this.updatePipeCounts(this.pullNet, this.pushNet);

			this.updatePortPIFIFO();

			this.networkPackNT(20);
		}

		/** The pump holds no fluid, it hands whatever the pull side offers straight to the push side, up to its flow */
		@Override
		public long getDemand(FluidType type, int pressure) {
			if(!this.isPumpRunning() || !this.isWithinPipeLimit() || this.tank[0].getTankType() != type || this.tank[0].getPressure() != pressure) return 0L;
			return Math.max(0L, this.getFlow() - this.moved);
		}

		@Override
		public long transferFluid(FluidType type, int pressure, long amount) {

			return transferToPushNet(this.pullNet, this.pushNet, type, pressure, amount, getDemand(type, pressure));
		}

		@Override
		public void writeToNBT(NBTTagCompound nbt) {
			super.writeToNBT(nbt);
			this.tank[0].writeToNBT(nbt, "t");
			writePumpPower(nbt);
			if(this.pump != PumpUpgrades.NONE) nbt.setByte("pump", (byte) this.pump);
		}

		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			super.readFromNBT(nbt);
			this.tank[0].readFromNBT(nbt, "t");
			readPumpPower(nbt);
			this.pump = nbt.hasKey("pump") ? nbt.getByte("pump") : PumpUpgrades.NONE;
		}

		@Override
		public void serialize(ByteBuf buf) {
			super.serialize(buf);
			this.tank[0].serialize(buf);
			buf.writeByte(this.pump);
			serializePumpState(buf);
		}

		@Override
		public void deserialize(ByteBuf buf) {
			super.deserialize(buf);
			this.tank[0].deserialize(buf);
			this.pump = buf.readByte();
			deserializePumpState(buf);
		}

		@Override public FluidTank[] getSendingTanks() { return this.tank; }
		@Override public FluidTank[] getReceivingTanks() { return this.tank; }
		@Override public FluidTank[] getAllTanks() { return this.tank; }

	}
}
