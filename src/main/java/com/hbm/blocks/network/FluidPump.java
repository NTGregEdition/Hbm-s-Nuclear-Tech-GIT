package com.hbm.blocks.network;

import java.util.ArrayList;
import java.util.List;

import com.hbm.handler.CompatHandler;
import cpw.mods.fml.common.Optional;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIPump;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.uninos.GenNode;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.BobMathUtil;
import com.hbm.util.EnumUtil;
import com.hbm.util.i18n.I18nUtil;
import com.hbm.world.gen.nbt.INBTBlockTransformable;

import api.hbm.block.IToolable;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import api.hbm.energymk2.VoltageTier;
import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.IFluidPumpMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

public class FluidPump extends BlockContainer implements INBTBlockTransformable, ILookOverlay, IGUIProvider, IToolable, ITooltipProvider {

	public FluidPump(Material mat) {
		super(mat);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityFluidPump();
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

		if(!player.isSneaking()) {
			ItemStack held = player.getHeldItem();

			if(held != null && held.getItem() instanceof IItemFluidIdentifier) {
				IItemFluidIdentifier id = (IItemFluidIdentifier) player.getHeldItem().getItem();
				FluidType type = id.getType(world, x, y, z, player.getHeldItem());
				TileEntity tile = world.getTileEntity(x, y, z);
				if(tile instanceof TileEntityFluidPump) {
					if(!world.isRemote) {
						TileEntityFluidPump pump = (TileEntityFluidPump) tile;
						pump.tank[0].setTankType(type);
						pump.markDirty();
						player.addChatComponentMessage(new ChatComponentText("Changed type to ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)).appendSibling(new ChatComponentTranslation(type.getConditionalName())).appendSibling(new ChatComponentText("!")));
					}
					return true;
				}
			}

			if(PumpUpgrades.isPumpItem(held, true)) {
				TileEntity tile = world.getTileEntity(x, y, z);
				if(tile instanceof TileEntityFluidPump && ((TileEntityFluidPump) tile).regulator == PumpUpgrades.NONE) {
					if(!world.isRemote) ((TileEntityFluidPump) tile).installRegulator(player, held);
					return true;
				}
			}

			if(world.isRemote) FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, x, y, z);
			return true;
		}

		return false;
	}

	@Override
	public boolean onScrew(World world, EntityPlayer player, int x, int y, int z, int side, float fX, float fY, float fZ, ToolType tool) {

		if(!player.isSneaking() || tool != ToolType.WRENCH) return false;

		TileEntity tile = world.getTileEntity(x, y, z);
		if(!(tile instanceof TileEntityFluidPump)) return false;
		TileEntityFluidPump pump = (TileEntityFluidPump) tile;
		if(pump.regulator == PumpUpgrades.NONE) return false;

		if(!world.isRemote) {
			PumpUpgrades.giveBack(player, ModItems.voltage_fluid_regulator, pump.regulator);
			pump.regulator = PumpUpgrades.NONE;
			pump.running = false;
			pump.markDirty();
		}
		return true;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

		TileEntity tile = world.getTileEntity(x, y, z);

		if(tile instanceof TileEntityFluidPump) {
			TileEntityFluidPump pump = (TileEntityFluidPump) tile;
			if(pump.regulator != PumpUpgrades.NONE && !world.isRemote) {
				this.dropBlockAsItem(world, x, y, z, new ItemStack(ModItems.voltage_fluid_regulator, 1, pump.regulator));
			}
		}

		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	@SuppressWarnings("rawtypes")
	public void addInformation(ItemStack stack, EntityPlayer player, java.util.List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}

	@Override @SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) { return new GUIPump((TileEntityFluidPump) world.getTileEntity(x, y, z)); }
	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) { return null; }

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {
		TileEntity tile = world.getTileEntity(x, y, z);
		if(!(tile instanceof TileEntityFluidPump)) return;
		TileEntityFluidPump pump = (TileEntityFluidPump) tile;

		List<String> text = new ArrayList();
		text.add(EnumChatFormatting.GREEN + "-> " + EnumChatFormatting.RESET + pump.tank[0].getTankType().getLocalizedName() + " (" + pump.tank[0].getPressure() + " PU): " + BobMathUtil.format(pump.bufferSize) + "mB/t" + EnumChatFormatting.RED + " ->" + EnumChatFormatting.RESET);
		text.add("Priority: " + EnumChatFormatting.YELLOW + pump.priority.name());

		if(pump.regulator == PumpUpgrades.NONE) {
			text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("hbm.pump.noRegulator") + EnumChatFormatting.RESET);
			text.add(I18nUtil.resolveKey("hbm.pump.hintInstallRegulator"));
		} else {
			text.add(I18nUtil.resolveKey("hbm.pump.regulator", PumpUpgrades.tierName(pump.regulator), BobMathUtil.format(pump.getMaxFlow())));
			text.add(I18nUtil.resolveKey("hbm.pump.voltage", VoltageTier.format(PumpUpgrades.voltageOf(pump.regulator))));

			boolean withinLimit = pump.isWithinPipeLimit();
			text.add((withinLimit ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + I18nUtil.resolveKey("hbm.pump.pipes", BobMathUtil.format(pump.getPipes()), BobMathUtil.format(pump.getPipeLimit())) + EnumChatFormatting.RESET);
			if(!withinLimit) text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("hbm.pump.overLimit") + EnumChatFormatting.RESET);

			text.add((pump.running ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + I18nUtil.resolveKey("hbm.pump.buffer", BobMathUtil.format(pump.power), BobMathUtil.format(pump.getMaxPower())) + EnumChatFormatting.RESET);
			text.add(I18nUtil.resolveKey("hbm.pump.draw", BobMathUtil.format(pump.getEnergyDemand())));
			if(!pump.running) text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("hbm.pump.noPower") + EnumChatFormatting.RESET);

			text.add(I18nUtil.resolveKey("hbm.pump.hintRemove"));
		}

		if(pump.tank[0].getFill() > 0) text.add(BobMathUtil.format(pump.tank[0].getFill()) + "mB buffered");
		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	@Override
	public int transformMeta(int meta, int coordBaseMode) {
		return INBTBlockTransformable.transformMetaDeco(meta, coordBaseMode);
	}

	@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
	public static class TileEntityFluidPump extends TileEntityLoadedBase implements IFluidStandardTransceiverMK2, IControlReceiver, SimpleComponent, CompatHandler.OCComponent, IEnergyReceiverMK2, IFluidPumpMK2 {

		public int bufferSize = 0;
		public FluidTank[] tank;
		public ConnectionPriority priority = ConnectionPriority.NORMAL;
		public boolean redstone = false;
		public int regulator = PumpUpgrades.NONE;
		public long power;
		private long moved;

		public int pullSize;
		public int pushSize;

		public boolean running;

		public TileEntityFluidPump() {
			this.tank = new FluidTank[1];
			this.tank[0] = new FluidTank(Fluids.NONE, bufferSize);
		}

		public int getMaxFlow() {
			return PumpUpgrades.flow(PumpUpgrades.FLUID_REGULATOR, this.regulator);
		}

		public int getPipeLimit() {
			return PumpUpgrades.pipes(PumpUpgrades.FLUID_REGULATOR, this.regulator);
		}

		public int getPipes() {
			return this.pullSize + this.pushSize;
		}

		public boolean isWithinPipeLimit() {
			return this.regulator != PumpUpgrades.NONE && this.getPipes() <= this.getPipeLimit();
		}

		public long getEnergyDemand() {
			return PumpUpgrades.idle(PumpUpgrades.FLUID_REGULATOR, this.regulator) + PumpUpgrades.energyForMoved(PumpUpgrades.FLUID_REGULATOR, this.regulator, this.moved);
		}

		public long getMaxDemand() {
			if(this.regulator == PumpUpgrades.NONE) return 0L;
			return PumpUpgrades.idle(PumpUpgrades.FLUID_REGULATOR, this.regulator) + PumpUpgrades.energyForMoved(PumpUpgrades.FLUID_REGULATOR, this.regulator, this.bufferSize);
		}

		public void installRegulator(EntityPlayer player, ItemStack stack) {
			this.regulator = PumpUpgrades.tierOf(stack);
			this.bufferSize = Math.min(this.getMaxFlow(), Math.max(this.bufferSize, 100));
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

			if(!worldObj.isRemote) {

				if(this.bufferSize > this.getMaxFlow()) this.bufferSize = this.getMaxFlow();

				// if the capacity were changed directly, any excess buffered fluid would be destroyed
				// when running a closed loop or handling hard to get fluids, that's quite bad
				if(this.bufferSize != this.tank[0].getMaxFill()) {
					int nextBuffer = Math.max(this.tank[0].getFill(), this.bufferSize);
					this.tank[0].changeTankSize(nextBuffer);
				}

				long demand = this.getEnergyDemand();
				this.running = this.regulator != PumpUpgrades.NONE && this.power >= demand;
				if(this.running) this.power -= demand;
				this.moved = 0L;

				for(ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) this.trySubscribe(worldObj, xCoord + side.offsetX, yCoord + side.offsetY, zCoord + side.offsetZ, side);

				this.redstone = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);

				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
				ForgeDirection in = dir.getRotation(ForgeDirection.UP);
				ForgeDirection out = in.getOpposite();

				FluidNetMK2 pullNet = this.getNetwork(in);
				FluidNetMK2 pushNet = this.getNetwork(out);
				this.pullSize = pullNet == null ? 0 : pullNet.links.size();
				this.pushSize = pushNet == null || pushNet == pullNet ? 0 : pushNet.links.size();

				if(this.running) {
					this.trySubscribe(tank[0].getTankType(), worldObj, xCoord + in.offsetX, yCoord, zCoord + in.offsetZ, in);
					if(!redstone) this.tryProvide(tank[0], worldObj, xCoord + out.offsetX, yCoord, zCoord + out.offsetZ, out);
				}

				this.networkPackNT(15);
			}
		}

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
			tank[0].writeToNBT(nbt, "t");
			nbt.setByte("p", (byte) priority.ordinal());
			nbt.setInteger("buffer", bufferSize);
			nbt.setLong("power", power);
			if(regulator != PumpUpgrades.NONE) nbt.setByte("regulator", (byte) regulator);
		}

		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			super.readFromNBT(nbt);
			tank[0].readFromNBT(nbt, "t");
			priority = EnumUtil.grabEnumSafely(ConnectionPriority.class, nbt.getByte("p"));
			bufferSize = nbt.getInteger("buffer");
			power = nbt.getLong("power");
			regulator = nbt.hasKey("regulator") ? nbt.getByte("regulator") : PumpUpgrades.NONE;
		}

		@Override
		public void serialize(ByteBuf buf) {
			super.serialize(buf);
			tank[0].serialize(buf);
			buf.writeByte((byte) priority.ordinal());
			buf.writeInt(bufferSize);
			buf.writeByte(regulator);
			buf.writeLong(power);
			buf.writeInt(pullSize);
			buf.writeInt(pushSize);
			buf.writeBoolean(running);
		}

		@Override
		public void deserialize(ByteBuf buf) {
			super.deserialize(buf);
			tank[0].deserialize(buf);
			priority = EnumUtil.grabEnumSafely(ConnectionPriority.class, buf.readByte());
			bufferSize = buf.readInt();
			regulator = buf.readByte();
			power = buf.readLong();
			pullSize = buf.readInt();
			pushSize = buf.readInt();
			running = buf.readBoolean();
		}

		@Override public ConnectionPriority getFluidPriority() { return priority; }
		@Override public FluidTank[] getSendingTanks() { return redstone || !running ? FluidTank.EMPTY_ARRAY : tank; }
		@Override public FluidTank[] getReceivingTanks() { return !running || this.bufferSize < this.tank[0].getFill() ? FluidTank.EMPTY_ARRAY : tank; }
		@Override public FluidTank[] getAllTanks() { return tank; }

		@Override public long getPower() { return power; }
		@Override public void setPower(long power) { this.power = power; }

		@Override public long getMaxPower() { return Math.max(PumpUpgrades.capacity(PumpUpgrades.FLUID_REGULATOR, this.regulator), this.power); }
		@Override public long getVoltage() { return this.regulator == PumpUpgrades.NONE ? VoltageTier.DEFAULT : PumpUpgrades.voltageOf(this.regulator); }

		@Override
		public void onOvervoltage(long voltage) {
			this.dropInstalledItem();
			IEnergyReceiverMK2.super.onOvervoltage(voltage);
		}

		public void dropInstalledItem() {
			if(this.regulator == PumpUpgrades.NONE || this.worldObj == null || this.worldObj.isRemote) return;
			this.worldObj.spawnEntityInWorld(new EntityItem(this.worldObj, this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D, new ItemStack(ModItems.voltage_fluid_regulator, 1, this.regulator)));
			this.regulator = PumpUpgrades.NONE;
		}

		@Override public int getPipeBudget() { return this.getPipeLimit(); }
		@Override public boolean isPumpRunning() { return this.running && this.regulator != PumpUpgrades.NONE; }
		@Override public int getPumpStrength() { return this.isPumpRunning() && this.isWithinPipeLimit() ? this.getPipeLimit() : 0; }

		@Override
		public boolean hasPermission(EntityPlayer player) {
			return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 128;
		}

		@Override
		public void receiveControl(NBTTagCompound data) {
			if(data.hasKey("capacity")) {
				this.bufferSize = MathHelper.clamp_int(data.getInteger("capacity"), 0, this.getMaxFlow());
			}
			if(data.hasKey("pressure")) {
				this.tank[0].withPressure(MathHelper.clamp_int(data.getByte("pressure"), 0, 5));
			}
			if(data.hasKey("priority")) {
				priority = EnumUtil.grabEnumSafely(ConnectionPriority.class, data.getByte("priority"));
			}

			this.markDirty();
		}

		@Override
		@Optional.Method(modid = "OpenComputers")
		public String getComponentName() {
			return "ntm_fluid_pump";
		}

		@Callback(direct = true, limit = 4)
		@Optional.Method(modid = "OpenComputers")
		public Object[] getFluid(Context context, Arguments args) {
			return new Object[] {
				tank[0].getTankType().getUnlocalizedName()
			};
		}

		@Callback(direct = true, limit = 4)
		@Optional.Method(modid = "OpenComputers")
		public Object[] getPressure(Context context, Arguments args) {
			return new Object[] {
				tank[0].getPressure()
			};
		}

		@Callback(direct = true, limit = 4)
		@Optional.Method(modid = "OpenComputers")
		public Object[] getFlow(Context context, Arguments args) {
			return new Object[] {
				bufferSize
			};
		}

		@Callback(direct = true, limit = 4)
		@Optional.Method(modid = "OpenComputers")
		public Object[] getPriority(Context context, Arguments args) {
			return new Object[] {
				getFluidPriority()
			};
		}

		@Callback(direct = true, limit = 4)
		@Optional.Method(modid = "OpenComputers")
		public Object[] getInfo(Context context, Arguments args) {
			return new Object[] {
				tank[0].getTankType().getUnlocalizedName(),
				tank[0].getPressure(),
				bufferSize,
				getFluidPriority()
			};
		}

		@Callback(direct = true, limit = 4)
		@Optional.Method(modid = "OpenComputers")
		public Object[] setPriority(Context context, Arguments args) {
			int num = args.checkInteger(0);
			switch (num) {
				case 0:
					priority = ConnectionPriority.LOWEST;
					break;
				case 1:
					priority = ConnectionPriority.LOW;
					break;
				case 2:
					priority = ConnectionPriority.NORMAL;
					break;
				case 3:
					priority = ConnectionPriority.HIGH;
					break;
				case 4:
					priority = ConnectionPriority.HIGHEST;
					break;
				default:
					return new Object[] {null, "Not a valid Priority."};
			}
			return new Object[] {true};
		}

		@Callback(direct = true, limit = 4)
		@Optional.Method(modid = "OpenComputers")
		public Object[] setFlow(Context context, Arguments args) {
			int input = args.checkInteger(0);
			if (input > getMaxFlow() || input < 0)
				return new Object[] {null, "Number outside of bounds."};
			bufferSize = input;
			return new Object[] {true};
		}

		@Override
		@Optional.Method(modid = "OpenComputers")
		public String[] methods() {
			return new String[] {
				"getPriority",
				"getPressure",
				"getFluid",
				"getFlow",
				"getInfo",
				"setPriority",
				"setFlow"
			};
		}

		@Override
		@Optional.Method(modid = "OpenComputers")
		public Object[] invoke(String method, Context context, Arguments args) throws Exception {
			switch (method) {
				case ("getPriority"):
					return getPriority(context, args);
				case ("getPressure"):
					return getPressure(context, args);
				case ("getFluid"):
					return getFluid(context, args);
				case ("getFlow"):
					return getFlow(context, args);
				case ("getInfo"):
					return getInfo(context, args);
				case ("setPriority"):
					return setPriority(context, args);
				case ("setFlow"):
					return setFlow(context, args);
			}
			throw new NoSuchMethodException();
		}
	}
}
