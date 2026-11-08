package com.hbm.blocks.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.ICustomBlockHighlight;
import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.gui.GUIFluidPipeMulti;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.items.tool.ItemMultitool;
import com.hbm.items.tool.ItemTooling;
import com.hbm.items.tool.ItemWrench;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;
import com.hbm.render.icon.RGBMutatorInterpolatedComponentRemap;
import com.hbm.render.icon.TextureAtlasSpriteMutatable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.network.TileEntityFluidPipe;

import api.hbm.block.IToolable;
import api.hbm.block.IToolable.ToolType;
import api.hbm.fluidmk2.FluidPipeProperties;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class BlockFluidPipe extends FluidDuctBox implements ITooltipProvider, IToolable, ICustomBlockHighlight, IGUIProvider {

	public static final int META_STEP = 3;
	public static final int NONUPLE_META = 1;
	public static final int QUADRUPLE_META = 4;

	private static final double OUTLINE_GROW = 0.002D;
	private static final double GRID_OFFSET = 0.002D;
	private static final float GRID_WIDTH = 2.5F;
	private static final float GRID_REFERENCE_HEIGHT = 1080F;
	private static final float GRID_ALPHA = 127F / 255F;
	private static final int[] U_AXIS = { 1, 0, 0 };

	private int placedSide = -1;

	@SideOnly(Side.CLIENT) public IIcon iconRestrictor;

	private final FluidPipeProperties properties;

	public BlockFluidPipe(Material mat, FluidPipeProperties properties) {
		super(mat);
		this.properties = properties;
	}

	public FluidPipeProperties getProperties() {
		return properties;
	}

	public static int sizeOf(int meta) {
		return Math.min(meta / META_STEP, FluidPipeProperties.SIZES - 1);
	}

	public static int channelsOf(int meta) {
		return meta == NONUPLE_META ? 9 : meta == QUADRUPLE_META ? 4 : 1;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityFluidPipe();
	}

	@Override
	protected String[] getIconMaterials() {
		String icon = "pipe_" + properties.id;
		return properties.allSizes ? new String[] { icon, icon + "_multi" } : new String[] { icon };
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister iconRegister) {
		super.registerBlockIcons(iconRegister);
		iconRestrictor = iconRegister.registerIcon(RefStrings.MODID + ":pipe_restrictor");
	}

	/** A boxduct_pipe_<id>[_multi]_<part> texture wins, multi pipes fall back to the single one, otherwise the white duct is recolored with the material colors */
	@Override
	@SideOnly(Side.CLIENT)
	protected IIcon registerDuctIcon(IIconRegister iconRegister, String material, String part) {
		String custom = "boxduct_" + material + "_" + part;
		if(textureExists(custom)) return iconRegister.registerIcon(RefStrings.MODID + ":" + custom);

		String single = "boxduct_pipe_" + properties.id + "_" + part;
		if(!material.equals("pipe_" + properties.id) && textureExists(single)) return iconRegister.registerIcon(RefStrings.MODID + ":" + single);

		String base = RefStrings.MODID + ":boxduct_white_" + part;
		NTMMaterial mat = tintMaterial(properties.id);
		if(mat == null || !(iconRegister instanceof TextureMap)) return iconRegister.registerIcon(base);

		String placeholder = base + "-" + material;
		TextureAtlasSpriteMutatable icon = new TextureAtlasSpriteMutatable(placeholder, new RGBMutatorInterpolatedComponentRemap(0xFFFFFF, 0x505050, mat.solidColorLight, mat.solidColorDark)).setBlockAtlas();
		((TextureMap) iconRegister).setTextureEntry(placeholder, icon);
		return icon;
	}

	@SideOnly(Side.CLIENT)
	private static NTMMaterial tintMaterial(String id) {
		switch(id) {
		case "copper": return Mats.MAT_COPPER;
		case "bronze": return Mats.MAT_BRONZE;
		case "steel": return Mats.MAT_STEEL;
		case "stainless": return Mats.MAT_STAINLESS;
		case "titanium": return Mats.MAT_TITANIUM;
		case "tungsten": return Mats.MAT_TUNGSTEN;
		default: return null;
		}
	}

	@SideOnly(Side.CLIENT)
	private static boolean textureExists(String name) {
		try {
			Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(RefStrings.MODID, "textures/blocks/" + name + ".png")).getInputStream().close();
			return true;
		} catch(IOException e) { //a missing file is the expected answer here
			return false;
		}
	}

	@Override
	public boolean canConnectTo(IBlockAccess world, int x, int y, int z, ForgeDirection dir, TileEntity tile) {
		return tile instanceof TileEntityFluidPipe && ((TileEntityFluidPipe) tile).isConnected(dir);
	}

	@Override
	public boolean drawsStubs() {
		return true;
	}

	@Override
	public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
		if(!world.isRemote) placedSide = side;
		return meta;
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
		int side = placedSide;
		placedSide = -1;
		if(world.isRemote || side < 0) return;

		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof TileEntityFluidPipe) ((TileEntityFluidPipe) te).connectBack(ForgeDirection.getOrientation(side).getOpposite());
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof TileEntityFluidPipe) ((TileEntityFluidPipe) te).checkConnections();
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
		if(holdsWrench(MainRegistry.proxy.me())) setBlockBounds(0F, 0F, 0F, 1F, 1F, 1F);
		else super.setBlockBoundsBasedOnState(world, x, y, z);
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		super.setBlockBoundsBasedOnState(world, x, y, z);
		return AxisAlignedBB.getBoundingBox(x + this.minX, y + this.minY, z + this.minZ, x + this.maxX, y + this.maxY, z + this.maxZ);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubBlocks(Item item, CreativeTabs tab, List list) {
		for(int size = 0; size < FluidPipeProperties.SIZES; size++) {
			if(properties.hasSize(size)) list.add(new ItemStack(item, 1, size * META_STEP));
		}
		if(properties.allSizes) {
			list.add(new ItemStack(item, 1, NONUPLE_META));
			list.add(new ItemStack(item, 1, QUADRUPLE_META));
		}
	}

	private String getDisplayName(int meta) {
		String size = meta == NONUPLE_META ? "nonuple" : meta == QUADRUPLE_META ? "quadruple" : String.valueOf(sizeOf(meta));
		return StatCollector.translateToLocalFormatted("hbm.fluidpipe.name",
				StatCollector.translateToLocal("hbm.fluidpipe.size." + size),
				StatCollector.translateToLocal("hbm.fluidpipe.material." + properties.id));
	}

	private int getThroughput(int meta) {
		return properties.getThroughput(sizeOf(meta), channelsOf(meta));
	}

	@Override
	public String getOverrideDisplayName(ItemStack stack) {
		return getDisplayName(stack.getItemDamage());
	}

	@Override
	public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
		if(world.isRemote || !(entity instanceof EntityLivingBase)) return;
		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof TileEntityFluidPipe) ((TileEntityFluidPipe) te).onContact((EntityLivingBase) entity);
	}

	@Override
	public boolean onScrew(World world, EntityPlayer player, int x, int y, int z, int side, float hitX, float hitY, float hitZ, ToolType tool) {
		if(tool != ToolType.WRENCH) return false;
		if(world.isRemote) return true;

		TileEntity te = world.getTileEntity(x, y, z);
		if(!(te instanceof TileEntityFluidPipe)) return false;

		TileEntityFluidPipe pipe = (TileEntityFluidPipe) te;
		ForgeDirection dir = getWrenchedSide(ForgeDirection.getOrientation(side), hitX, hitY, hitZ);
		return player.isSneaking() ? pipe.toggleInput(player, dir) : pipe.toggleSide(player, dir);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(channelsOf(world.getBlockMetadata(x, y, z)) == 1 || holdsWrench(player)) return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
		if(world.isRemote) FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, x, y, z);
		return true;
	}

	@Override
	public void changeTypeRecursively(World world, int x, int y, int z, FluidType prevType, FluidType type, int loopsRemaining) {
		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof TileEntityFluidPipe && ((TileEntityFluidPipe) te).getChannelCount() > 1) return;
		super.changeTypeRecursively(world, x, y, z, prevType, type, loopsRemaining);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return null;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		TileEntity te = world.getTileEntity(x, y, z);
		return te instanceof TileEntityFluidPipe ? new GUIFluidPipeMulti((TileEntityFluidPipe) te) : null;
	}

	private static boolean holdsWrench(EntityPlayer player) {
		if(player == null) return false;
		ItemStack held = player.getHeldItem();
		if(held == null) return false;

		Item item = held.getItem();
		if(item instanceof ItemWrench) return true;
		if(item instanceof ItemMultitool) return ((ItemMultitool) item).getModeType(held) == ToolType.WRENCH;
		return item instanceof ItemTooling && ((ItemTooling) item).getToolType() == ToolType.WRENCH;
	}

	private static ForgeDirection getWrenchedSide(ForgeDirection face, float hitX, float hitY, float hitZ) {
		float x = (hitX % 1F + 1F) % 1F;
		float y = (hitY % 1F + 1F) % 1F;
		float z = (hitZ % 1F + 1F) % 1F;
		ForgeDirection back = face.getOpposite();

		switch(face) {
		case DOWN:
		case UP:
			if(x < 0.25F) return z < 0.25F || z >= 0.75F ? back : ForgeDirection.WEST;
			if(x >= 0.75F) return z < 0.25F || z >= 0.75F ? back : ForgeDirection.EAST;
			if(z < 0.25F) return ForgeDirection.NORTH;
			if(z >= 0.75F) return ForgeDirection.SOUTH;
			return face;
		case NORTH:
		case SOUTH:
			if(x < 0.25F) return y < 0.25F || y >= 0.75F ? back : ForgeDirection.WEST;
			if(x >= 0.75F) return y < 0.25F || y >= 0.75F ? back : ForgeDirection.EAST;
			if(y < 0.25F) return ForgeDirection.DOWN;
			if(y >= 0.75F) return ForgeDirection.UP;
			return face;
		case WEST:
		case EAST:
			if(z < 0.25F) return y < 0.25F || y >= 0.75F ? back : ForgeDirection.NORTH;
			if(z >= 0.75F) return y < 0.25F || y >= 0.75F ? back : ForgeDirection.SOUTH;
			if(y < 0.25F) return ForgeDirection.DOWN;
			if(y >= 0.75F) return ForgeDirection.UP;
			return face;
		default:
			return ForgeDirection.UNKNOWN;
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean shouldDrawHighlight(World world, int x, int y, int z) {
		return holdsWrench(MainRegistry.proxy.me()) && world.getTileEntity(x, y, z) instanceof TileEntityFluidPipe;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void drawHighlight(DrawBlockHighlightEvent event, World world, int x, int y, int z) {
		TileEntity te = world.getTileEntity(x, y, z);
		MovingObjectPosition mop = event.target;
		if(!(te instanceof TileEntityFluidPipe)) return;

		EntityPlayer player = event.player;
		double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
		double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
		double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL20.glUseProgram(0);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glLineWidth(GRID_WIDTH * Minecraft.getMinecraft().displayHeight / GRID_REFERENCE_HEIGHT);

		Tessellator t = Tessellator.instance;
		AxisAlignedBB box = getSelectedBoundingBoxFromPool(world, x, y, z).expand(OUTLINE_GROW, OUTLINE_GROW, OUTLINE_GROW).getOffsetBoundingBox(-camX, -camY, -camZ);

		t.startDrawing(GL11.GL_LINE_STRIP);
		t.setColorRGBA_F(0F, 0F, 0F, GRID_ALPHA);
		t.addVertex(box.minX, box.minY, box.minZ);
		t.addVertex(box.maxX, box.minY, box.minZ);
		t.addVertex(box.maxX, box.minY, box.maxZ);
		t.addVertex(box.minX, box.minY, box.maxZ);
		t.addVertex(box.minX, box.minY, box.minZ);
		t.draw();

		t.startDrawing(GL11.GL_LINE_STRIP);
		t.setColorRGBA_F(0F, 0F, 0F, GRID_ALPHA);
		t.addVertex(box.minX, box.maxY, box.minZ);
		t.addVertex(box.maxX, box.maxY, box.minZ);
		t.addVertex(box.maxX, box.maxY, box.maxZ);
		t.addVertex(box.minX, box.maxY, box.maxZ);
		t.addVertex(box.minX, box.maxY, box.minZ);
		t.draw();

		t.startDrawing(GL11.GL_LINES);
		t.setColorRGBA_F(0F, 0F, 0F, GRID_ALPHA);
		t.addVertex(box.minX, box.minY, box.minZ);
		t.addVertex(box.minX, box.maxY, box.minZ);
		t.addVertex(box.maxX, box.minY, box.minZ);
		t.addVertex(box.maxX, box.maxY, box.minZ);
		t.addVertex(box.maxX, box.minY, box.maxZ);
		t.addVertex(box.maxX, box.maxY, box.maxZ);
		t.addVertex(box.minX, box.minY, box.maxZ);
		t.addVertex(box.minX, box.maxY, box.maxZ);
		t.draw();

		ForgeDirection face = ForgeDirection.getOrientation(mop.sideHit);
		int axis = face.offsetX != 0 ? 0 : face.offsetY != 0 ? 1 : 2;
		double plane = face.offsetX + face.offsetY + face.offsetZ > 0 ? 1D + GRID_OFFSET : -GRID_OFFSET;
		double ox = x - camX;
		double oy = y - camY;
		double oz = z - camZ;

		t.startDrawing(GL11.GL_LINES);
		t.setColorRGBA_F(0F, 0F, 0F, GRID_ALPHA);
		line(t, axis, plane, 0.25D, 0D, plane, 0.25D, 1D, ox, oy, oz);
		line(t, axis, plane, 0.75D, 0D, plane, 0.75D, 1D, ox, oy, oz);
		line(t, axis, plane, 0D, 0.25D, plane, 1D, 0.25D, ox, oy, oz);
		line(t, axis, plane, 0D, 0.75D, plane, 1D, 0.75D, ox, oy, oz);

		TileEntityFluidPipe pipe = (TileEntityFluidPipe) te;
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			if(pipe.isConnected(dir)) drawMark(t, axis, plane, face, dir, ox, oy, oz);
		}
		t.draw();

		GL20.glUseProgram(program);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
	}

	@SideOnly(Side.CLIENT)
	private static void drawMark(Tessellator t, int axis, double plane, ForgeDirection face, ForgeDirection dir, double ox, double oy, double oz) {
		if(dir == face) {
			cross(t, axis, plane, 0.25D, 0.25D, 0.75D, 0.75D, ox, oy, oz);
		} else if(dir == face.getOpposite()) {
			cross(t, axis, plane, 0D, 0D, 0.25D, 0.25D, ox, oy, oz);
			cross(t, axis, plane, 0.75D, 0D, 1D, 0.25D, ox, oy, oz);
			cross(t, axis, plane, 0D, 0.75D, 0.25D, 1D, ox, oy, oz);
			cross(t, axis, plane, 0.75D, 0.75D, 1D, 1D, ox, oy, oz);
		} else {
			int dirAxis = dir.offsetX != 0 ? 0 : dir.offsetY != 0 ? 1 : 2;
			boolean positive = dir.offsetX + dir.offsetY + dir.offsetZ > 0;
			double near = positive ? 0.75D : 0D;
			double far = positive ? 1D : 0.25D;
			if(dirAxis == U_AXIS[axis]) cross(t, axis, plane, near, 0.25D, far, 0.75D, ox, oy, oz);
			else cross(t, axis, plane, 0.25D, near, 0.75D, far, ox, oy, oz);
		}
	}

	@SideOnly(Side.CLIENT)
	private static void cross(Tessellator t, int axis, double plane, double u1, double v1, double u2, double v2, double ox, double oy, double oz) {
		line(t, axis, plane, u1, v1, plane, u2, v2, ox, oy, oz);
		line(t, axis, plane, u1, v2, plane, u2, v1, ox, oy, oz);
	}

	@SideOnly(Side.CLIENT)
	private static void line(Tessellator t, int axis, double p1, double u1, double v1, double p2, double u2, double v2, double ox, double oy, double oz) {
		vertex(t, axis, p1, u1, v1, ox, oy, oz);
		vertex(t, axis, p2, u2, v2, ox, oy, oz);
	}

	@SideOnly(Side.CLIENT)
	private static void vertex(Tessellator t, int axis, double p, double u, double v, double ox, double oy, double oz) {
		if(axis == 0) t.addVertex(ox + p, oy + u, oz + v);
		else if(axis == 1) t.addVertex(ox + u, oy + p, oz + v);
		else t.addVertex(ox + u, oy + v, oz + p);
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {

		TileEntity te = world.getTileEntity(x, y, z);

		if(!(te instanceof TileEntityFluidPipe))
			return;

		TileEntityFluidPipe pipe = (TileEntityFluidPipe) te;
		int meta = world.getBlockMetadata(x, y, z);

		List<String> text = new ArrayList();
		for(int c = 0; c < pipe.getChannelCount(); c++) {
			FluidType type = pipe.getChannelCount() == 1 ? pipe.getType() : pipe.getChannelType(c);
			if(type != Fluids.NONE) text.add("&[" + type.getColor() + "&]" + type.getLocalizedName());
			else if(c == 0) text.add("&[" + type.getColor() + "&]" + StatCollector.translateToLocal(pipe.getChannelCount() == 1 ? "hbm.fluidpipe.any" : "hbm.fluidpipe.empty"));
		}
		int throughput = getThroughput(meta);
		text.add(StatCollector.translateToLocalFormatted("hbm.fluidpipe.throughput", throughput, throughput * 20));
		ILookOverlay.printGeneric(event, getDisplayName(meta), 0xffff00, 0x404000, text);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		int meta = stack.getItemDamage();
		int throughput = getThroughput(meta);
		int channels = channelsOf(meta);
		if(channels > 1) list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("hbm.fluidpipe.channels", channels));
		list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("hbm.fluidpipe.throughput", throughput, throughput * 20));
		list.add(EnumChatFormatting.GREEN + StatCollector.translateToLocalFormatted("hbm.fluidpipe.capacity", properties.getCapacity(sizeOf(meta), channels)));
		list.add(EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("hbm.fluidpipe.heat", properties.heatResistance));
		if(!properties.gasProof) list.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("hbm.fluidpipe.leaks"));
		list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(channels > 1 ? "hbm.fluidpipe.screenHint" : "hbm.fluidpipe.anyHint"));
		list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("hbm.fluidpipe.wrenchHint"));
		list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("hbm.fluidpipe.arrowHint"));
	}
}
