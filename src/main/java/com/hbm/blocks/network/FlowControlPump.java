package com.hbm.blocks.network;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
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
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.uninos.GenNode;
import com.hbm.uninos.NodeNet;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.fauxpointtwelve.DirPos;
import com.hbm.util.BobMathUtil;
import com.hbm.util.EnumUtil;
import com.hbm.util.i18n.I18nUtil;

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
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

public class FlowControlPump extends BlockDummyable implements ILookOverlay, IGUIProvider, IToolable, ITooltipProvider {

	public FlowControlPump() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityFlowControlPump();
		return new TileEntityProxyCombo().fluid();
	}

	@Override 	public int[] getDimensions() { return new int[] {0, 0, 0, 0, 1, 1}; }
	@Override public int getOffset() { return 0; }
	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		return rotatePorts(new int[][] {
			{1, 0, 0, ForgeDirection.EAST.ordinal()},
			{-1, 0, 0, ForgeDirection.WEST.ordinal()}
		}, dir);
	}
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float fX, float fY, float fZ) {
		
		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;
		
		TileEntity tile = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(tile instanceof TileEntityFlowControlPump)) return false;
		
		TileEntityFlowControlPump pump = (TileEntityFlowControlPump) tile;
		
		if(player.getHeldItem() != null && player.getHeldItem().getItem() instanceof IItemFluidIdentifier) {
			
			IItemFluidIdentifier id = (IItemFluidIdentifier) player.getHeldItem().getItem();
			FluidType type = id.getType(world, x, y, z, player.getHeldItem());
			
			if(!world.isRemote) {
				pump.tank[0].setTankType(type);
				pump.markDirty();
				player.addChatComponentMessage(new ChatComponentText("Changed type to ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)).appendSibling(new ChatComponentTranslation(type.getConditionalName())).appendSibling(new ChatComponentText("!")));
			}
			return true;
		}

		if(!player.isSneaking()) {
			ItemStack held = player.getHeldItem();
			if(PumpUpgrades.isPumpItem(held, true) && pump.regulator == PumpUpgrades.NONE) {
				if(!world.isRemote) pump.installRegulator(player, held);
				return true;
			}
			if(world.isRemote) FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, pos[0], pos[1], pos[2]);
			return true;
		}

		return false;
	}

	@Override
	public boolean onScrew(World world, EntityPlayer player, int x, int y, int z, int side, float fX, float fY, float fZ, ToolType tool) {

		if(!player.isSneaking() || tool != ToolType.WRENCH) return false;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;
		TileEntity tile = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(tile instanceof TileEntityFlowControlPump)) return false;
		TileEntityFlowControlPump pump = (TileEntityFlowControlPump) tile;
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

		if(meta >= 12 && !world.isRemote) {
			TileEntity tile = world.getTileEntity(x, y, z);
			if(tile instanceof TileEntityFlowControlPump) {
				TileEntityFlowControlPump pump = (TileEntityFlowControlPump) tile;
				if(pump.regulator != PumpUpgrades.NONE) this.dropBlockAsItem(world, x, y, z, new ItemStack(ModItems.voltage_fluid_regulator, 1, pump.regulator));
			}
		}

		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	@SuppressWarnings("rawtypes")
	public void addInformation(ItemStack stack, EntityPlayer player, java.util.List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {
		
		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return;
		TileEntity tile = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(tile instanceof TileEntityFlowControlPump)) return;
		
		TileEntityFlowControlPump pump = (TileEntityFlowControlPump) tile;

		List<String> text = new ArrayList();
		text.add(EnumChatFormatting.GREEN + "-> " + EnumChatFormatting.RESET + pump.tank[0].getTankType().getLocalizedName() + " (" + pump.tank[0].getPressure() + " PU): " + BobMathUtil.format(pump.bufferSize) + "mB/t" + EnumChatFormatting.RED + " ->");
		text.add("Priority: " + EnumChatFormatting.YELLOW + pump.priority.name());
		if(pump.redstone) text.add(EnumChatFormatting.RED + "Disabled by redstone");

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
		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	@Override public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) { return new GUIPump((TileEntityFlowControlPump) world.getTileEntity(x, y, z)); }
	@Override public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) { return null; }

	public static class TileEntityFlowControlPump extends TileEntityVoltagePumpBase implements IFluidStandardTransceiverMK2, IControlReceiver {

		public int bufferSize = 0;
		public FluidTank[] tank;
		public ConnectionPriority priority = ConnectionPriority.NORMAL;
		public boolean redstone = false;

		public boolean topRail = false;
		public boolean bottomRail = true;
		public boolean isWorking = false;

		// regulator / energy / pipe limit logic
		public int regulator = PumpUpgrades.NONE;

		public float rotor;
		public float prevRotor;
		public float rotorSpeed;
		public static final float ROTOR_ACCELERATION = 1F;

		public TileEntityFlowControlPump() {
			this.tank = new FluidTank[1];
			this.tank[0] = new FluidTank(Fluids.NONE, bufferSize);
		}

		protected PortDef cachedOutPort;
		protected PortDef cachedInPort;
		
		public PortDef getOutPort() {
			if(cachedOutPort == null) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.DOWN);
				cachedOutPort = PortDef.make(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir);
			}
			return cachedOutPort;
		}
		public PortDef getInPort() {
			if(cachedInPort == null) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.UP);
				cachedInPort = PortDef.make(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir);
			}
			return cachedInPort;
		}
		
		protected PortDef cachedPowerPort;
		public PortDef getPowerPort() {
			if(cachedPowerPort == null) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
				// everything except the two fluid ends
				cachedPowerPort = PortDef.make(xCoord, yCoord, zCoord, dir, dir.getOpposite(), ForgeDirection.UP, ForgeDirection.DOWN);
			}
			return cachedPowerPort;
		}

		@Override
		public boolean canConnect(ForgeDirection dir) {
			ForgeDirection rot = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.DOWN);
			return dir != rot && dir != rot.getOpposite();
		}

		@Override protected PumpUpgrades.Stats[] getUpgradeType() { return PumpUpgrades.FLUID_REGULATOR; }
		@Override protected int getInstalledUpgrade() { return regulator; }
		@Override protected void destroyInstalledUpgrade() { regulator = PumpUpgrades.NONE; }

		public int getMaxFlow() { return PumpUpgrades.flow(PumpUpgrades.FLUID_REGULATOR, this.regulator); }

		public void installRegulator(EntityPlayer player, ItemStack stack) {
			this.regulator = PumpUpgrades.tierOf(stack);
			this.bufferSize = Math.min(this.getMaxFlow(), Math.max(this.bufferSize, 100));
			if(!player.capabilities.isCreativeMode) player.inventory.decrStackSize(player.inventory.currentItem, 1);
			this.markDirty();
		}

		/** Network the given fluid port is attached to, used to count the pipes this pump has to push */
		@SuppressWarnings("unchecked")
		private FluidNetMK2 getNetwork(PortDef port) {
			FluidType type = this.tank[0].getTankType();
			if(type == null || port == null || port.portConnections.length == 0) return null;
			DirPos con = port.portConnections[0];
			GenNode<FluidNetMK2> node = UniNodespace.getNode(this.worldObj, con.getX(), con.getY(), con.getZ(), type.getNetworkProvider());
			return node != null && node.hasValidNet() ? node.net : null;
		}

		@Override
		public boolean canConnect(FluidType type, ForgeDirection dir) {
			ForgeDirection rot = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.DOWN);
			return dir == rot || dir == rot.getOpposite();
		}

		@Override
		public void updateEntity() {

			if(!worldObj.isRemote) {

				// ports are always built from the full tank array, the sending/receiving getters are what gate the pump
				this.setupFluidOutPorts(this.tank, getOutPort());
				this.setupFluidInPorts(this.tank, getInPort());
				this.setupPowerPorts(new PortDef[] {getPowerPort()});

				if(this.bufferSize > this.getMaxFlow()) this.bufferSize = this.getMaxFlow();

				this.updateRunningState();

				FluidNetMK2 pullNet = this.getNetwork(getInPort());
				FluidNetMK2 pushNet = this.getNetwork(getOutPort());
				this.updatePipeCounts(pullNet, pushNet);

				this.updatePortPIFIFO();

				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.DOWN);
				this.redstone = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord) ||
						worldObj.isBlockIndirectlyGettingPowered(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ) ||
						worldObj.isBlockIndirectlyGettingPowered(xCoord - dir.offsetX, yCoord, zCoord - dir.offsetZ);
				
				this.networkPackNT(50);
				this.isWorking = false;
			} else {
				
				if(worldObj.getTotalWorldTime() % 20 == 0) {
					topRail = !worldObj.getBlock(xCoord, yCoord + 1, zCoord).isAir(worldObj, xCoord, yCoord + 1, zCoord);
					bottomRail = !worldObj.getBlock(xCoord, yCoord - 1, zCoord).isAir(worldObj, xCoord, yCoord - 1, zCoord);
				}

				if(isWorking) this.rotorSpeed += ROTOR_ACCELERATION;
				else this.rotorSpeed -= ROTOR_ACCELERATION;

				this.rotorSpeed = MathHelper.clamp_float(this.rotorSpeed, 0F, 30);

				this.prevRotor = this.rotor;
				this.rotor += this.rotorSpeed;

				if(this.rotor >= 360F) {
					this.rotor -= 360F;
					this.prevRotor -= 360F;
				}
			}
		}

		/** The pump holds no fluid, it hands whatever the pull side offers straight to the push side, up to the configured throughput */
		@Override
		public long getDemand(FluidType type, int pressure) {
			if(!this.running || this.redstone || !this.isWithinPipeLimit() || this.tank[0].getTankType() != type || this.tank[0].getPressure() != pressure) return 0L;
			return Math.max(0L, this.bufferSize - this.moved);
		}

		@Override
		public long transferFluid(FluidType type, int pressure, long amount) {

			if(this.fluidInPorts == null || this.fluidOutPorts == null) return amount;
			NodeNet pushNet = this.fluidOutPorts[0].getNetwork();
			NodeNet pullNet = this.fluidInPorts[0].getNetwork();
			return pushNet instanceof FluidNetMK2
					? transferToPushNet(pullNet instanceof FluidNetMK2 ? (FluidNetMK2) pullNet : null, (FluidNetMK2) pushNet, type, pressure, amount, getDemand(type, pressure))
					: amount;
		}

		@Override protected void onFluidTransferred(long amount) { isWorking = true; }

		@Override
		public void writeToNBT(NBTTagCompound nbt) {
			super.writeToNBT(nbt);
			tank[0].writeToNBT(nbt, "t");
			nbt.setByte("p", (byte) priority.ordinal());
			nbt.setInteger("buffer", bufferSize);
			writePumpPower(nbt);
			if(regulator != PumpUpgrades.NONE) nbt.setByte("regulator", (byte) regulator);
		}

		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			super.readFromNBT(nbt);
			tank[0].readFromNBT(nbt, "t");
			priority = EnumUtil.grabEnumSafely(ConnectionPriority.class, nbt.getByte("p"));
			bufferSize = nbt.getInteger("buffer");
			readPumpPower(nbt);
			regulator = nbt.hasKey("regulator") ? nbt.getByte("regulator") : PumpUpgrades.NONE;
		}

		@Override
		public void serialize(ByteBuf buf) {
			super.serialize(buf);
			tank[0].serialize(buf);
			buf.writeByte((byte) priority.ordinal());
			buf.writeInt(bufferSize);
			buf.writeBoolean(redstone);
			buf.writeBoolean(isWorking);
			buf.writeByte(regulator);
			serializePumpState(buf);
		}

		@Override
		public void deserialize(ByteBuf buf) {
			super.deserialize(buf);
			tank[0].deserialize(buf);
			priority = EnumUtil.grabEnumSafely(ConnectionPriority.class, buf.readByte());
			bufferSize = buf.readInt();
			redstone = buf.readBoolean();
			isWorking = buf.readBoolean();
			regulator = buf.readByte();
			deserializePumpState(buf);
		}

		@Override public ConnectionPriority getFluidPriority() { return priority; }
		@Override public FluidTank[] getSendingTanks() { return tank; }
		@Override public FluidTank[] getReceivingTanks() { return tank; }
		@Override public FluidTank[] getAllTanks() { return tank; }

		@Override public long getProviderSpeed(FluidType type, int pressure) { return redstone ?  0 : 1_000_000_000; }

		@Override
		public boolean hasPermission(EntityPlayer player) {
			return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 128;
		}

		@Override
		public void receiveControl(EntityPlayer player, NBTTagCompound data) {
			if(data.hasKey("capacity")) this.bufferSize = MathHelper.clamp_int(data.getInteger("capacity"), 0, this.getMaxFlow());
			if(data.hasKey("pressure")) this.tank[0].withPressure(MathHelper.clamp_int(data.getByte("pressure"), 0, 5));
			if(data.hasKey("priority")) priority = EnumUtil.grabEnumSafely(ConnectionPriority.class, data.getByte("priority"));

			this.markDirty();
		}
		
		AxisAlignedBB bb = null;
		
		@Override
		public AxisAlignedBB getRenderBoundingBox() {
			if(bb == null) bb = AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 1, zCoord + 2);
			return bb;
		}
		
		@Override
		@SideOnly(Side.CLIENT)
		public double getMaxRenderDistanceSquared() {
			return 65536.0D;
		}
	}
}
