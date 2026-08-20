package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.MachineBlastFurnace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityBlastFurnaceStruct extends TileEntity {

	public static class Component {

		public final Block block;
		public final int x;
		public final int y;
		public final int z;
		public final int[] metas;

		public Component(Block block, int x, int y, int z, int... metas) {
			this.block = block;
			this.x = x;
			this.y = y;
			this.z = z;
			this.metas = metas;
		}

		public boolean matchesMeta(int meta, ForgeDirection dir) {
			for(int m : metas) if(rotateMeta(block, m, dir) == meta) return true;
			return false;
		}

		public int getDisplayMeta(ForgeDirection dir) {
			if(metas.length == 0) return 0;
			int index = (int) ((System.currentTimeMillis() / 1000) % metas.length);
			return rotateMeta(block, metas[index], dir);
		}
	}

	private static List<Component> components;

	public static List<Component> getComponents() {
		if(components == null) {
			components = buildComponents();
		}
		return components;
	}

	private static List<Component> buildComponents() {

		List<Component> list = new ArrayList();

		Block STONEBRICK = (Block) Block.blockRegistry.getObject("minecraft:stonebrick");
		Block BRICK_FIRE = (Block) Block.blockRegistry.getObject("hbm:tile.brick_fire");
		Block BRICK_FIRE_STAIRS = (Block) Block.blockRegistry.getObject("hbm:tile.brick_fire_stairs");

		list.add(new Component(STONEBRICK, 1, -1, 0, 0));
		list.add(new Component(STONEBRICK, 0, -1, 0, 0));
		list.add(new Component(STONEBRICK, -1, -1, 0, 0));
		list.add(new Component(BRICK_FIRE, 1, 0, 0, 0));
		list.add(new Component(BRICK_FIRE, -1, 0, 0, 0));
		list.add(new Component(BRICK_FIRE, 1, 1, 0, 0));
		list.add(new Component(BRICK_FIRE, 0, 1, 0, 0));
		list.add(new Component(BRICK_FIRE, -1, 1, 0, 0));
		list.add(new Component(BRICK_FIRE, 1, 2, 0, 0));
		list.add(new Component(STONEBRICK, 0, 2, 0, 0));
		list.add(new Component(BRICK_FIRE, -1, 2, 0, 0));
		list.add(new Component(BRICK_FIRE_STAIRS, 1, 3, 0, 1));
		list.add(new Component(STONEBRICK, 0, 3, 0, 0));
		list.add(new Component(BRICK_FIRE_STAIRS, -1, 3, 0, 0));
		list.add(new Component(STONEBRICK, 0, 4, 0, 0));
		list.add(new Component(STONEBRICK, 0, 5, 0, 0));

		list.add(new Component(Blocks.air, -1, 4, 0, 0));
		list.add(new Component(Blocks.air, 1, 4, 0, 0));
		list.add(new Component(Blocks.air, -1, 5, 0, 0));
		list.add(new Component(Blocks.air, 1, 5, 0, 0));


		list.add(new Component(STONEBRICK, 1, -1, 1, 0));
		list.add(new Component(STONEBRICK, 0, -1, 1, 0));
		list.add(new Component(STONEBRICK, -1, -1, 1, 0));
		list.add(new Component(STONEBRICK, 1, 0, 1, 0));
		list.add(new Component(STONEBRICK, 0, 0, 1, 0));
		list.add(new Component(STONEBRICK, -1, 0, 1, 0));
		list.add(new Component(BRICK_FIRE, 1, 1, 1, 0));
		list.add(new Component(BRICK_FIRE, 0, 1, 1, 0));
		list.add(new Component(BRICK_FIRE, -1, 1, 1, 0));
		list.add(new Component(BRICK_FIRE, 1, 2, 1, 0));
		list.add(new Component(BRICK_FIRE, 0, 2, 1, 0));
		list.add(new Component(BRICK_FIRE, -1, 2, 1, 0));
		list.add(new Component(BRICK_FIRE, 1, 3, 1, 0));
		list.add(new Component(BRICK_FIRE, 0, 3, 1, 0));
		list.add(new Component(BRICK_FIRE, -1, 3, 1, 0));
		list.add(new Component(BRICK_FIRE, 1, 4, 1, 0));
		list.add(new Component(BRICK_FIRE, 0, 4, 1, 0));
		list.add(new Component(BRICK_FIRE, -1, 4, 1, 0));
		list.add(new Component(STONEBRICK, 1, 5, 1, 0));
		list.add(new Component(STONEBRICK, -1, 5, 1, 0));

		list.add(new Component(Blocks.air, 0, 5, 1, 0));


		list.add(new Component(STONEBRICK, 1, -1, 2, 0));
		list.add(new Component(STONEBRICK, 0, -1, 2, 0));
		list.add(new Component(STONEBRICK, -1, -1, 2, 0));
		list.add(new Component(BRICK_FIRE, 1, 0, 2, 0));
		list.add(new Component(STONEBRICK, 0, 0, 2, 0));
		list.add(new Component(BRICK_FIRE, -1, 0, 2, 0));
		list.add(new Component(BRICK_FIRE, 1, 1, 2, 0));
		list.add(new Component(BRICK_FIRE, 0, 1, 2, 0));
		list.add(new Component(BRICK_FIRE, -1, 1, 2, 0));
		list.add(new Component(BRICK_FIRE, 1, 2, 2, 0));
		list.add(new Component(BRICK_FIRE, 0, 2, 2, 0));
		list.add(new Component(BRICK_FIRE, -1, 2, 2, 0));
		list.add(new Component(BRICK_FIRE_STAIRS, 1, 3, 2, 3));
		list.add(new Component(BRICK_FIRE, 0, 3, 2, 0));
		list.add(new Component(BRICK_FIRE_STAIRS, -1, 3, 2, 3));
		list.add(new Component(BRICK_FIRE, 0, 4, 2, 0));
		list.add(new Component(STONEBRICK, 0, 5, 2, 0));

		list.add(new Component(Blocks.air, -1, 4, 2, 0));
		list.add(new Component(Blocks.air, 1, 4, 2, 0));
		list.add(new Component(Blocks.air, -1, 5, 2, 0));
		list.add(new Component(Blocks.air, 1, 5, 2, 0));

		return list;
	}

	private static int rotateMeta(Block block, int meta, ForgeDirection dir) {

		if(!(block instanceof BlockStairs) || dir == ForgeDirection.NORTH) return meta;

		int topBit = meta & 4;
		ForgeDirection facing = stairMetaToFacing(meta & 3);

		int fx = facing.offsetX;
		int fz = facing.offsetZ;
		int rx, rz;

		switch(dir) {
			case SOUTH: rx = -fx; rz = -fz; break;
			case EAST:  rx = -fz; rz =  fx; break;
			case WEST:  rx =  fz; rz = -fx; break;
			default:    rx =  fx; rz =  fz; break;
		}

		return facingToStairMeta(rx, rz) | topBit;
	}

	private static ForgeDirection stairMetaToFacing(int facingBits) {
		switch(facingBits) {
			case 0: return ForgeDirection.EAST;
			case 1: return ForgeDirection.WEST;
			case 2: return ForgeDirection.SOUTH;
			default: return ForgeDirection.NORTH;
		}
	}

	private static int facingToStairMeta(int offsetX, int offsetZ) {
		if(offsetX == 1) return 0;  // east
		if(offsetX == -1) return 1; // west
		if(offsetZ == 1) return 2;  // south
		return 3;                   // north
	}


	public boolean structureOK = false;

	public int highlightTimer = 0;

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			if(highlightTimer > 0) highlightTimer--;
			return;
		}

		if(worldObj.getTotalWorldTime() % 20 != 0) return;

		if(checkStructure()) {
			assemble();
		}
	}

	public boolean isComponentValid(Component comp, int x, int y, int z) {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
		Block b = worldObj.getBlock(x, y, z);
		if(b != comp.block) return false;

		int meta = worldObj.getBlockMetadata(x, y, z);
		return comp.matchesMeta(meta, dir);
	}

	public boolean checkStructure() {

		this.structureOK = false;

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		for(Component comp : getComponents()) {

			int x = xCoord - dir.offsetX * comp.x + rot.offsetX * comp.x;
			int y = yCoord + comp.y;
			int z = zCoord - dir.offsetZ * comp.z + rot.offsetZ * comp.z;

			if(dir == ForgeDirection.EAST || dir == ForgeDirection.WEST) {
				x = xCoord + dir.offsetZ * comp.z - rot.offsetZ * comp.z;
				z = zCoord + dir.offsetX * comp.x - rot.offsetX * comp.x;
			}

			Block b = worldObj.getBlock(x, y, z);
			if(b != comp.block) return false;

			int meta = worldObj.getBlockMetadata(x, y, z);
			if(!comp.matchesMeta(meta, dir)) return false;
		}

		this.structureOK = true;
		return true;
	}

	public void buildStructure() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		for(Component comp : getComponents()) {

			int x = xCoord - dir.offsetX * comp.x + rot.offsetX * comp.x;
			int y = yCoord + comp.y;
			int z = zCoord - dir.offsetZ * comp.z + rot.offsetZ * comp.z;

			if(dir == ForgeDirection.EAST || dir == ForgeDirection.WEST) {
				x = xCoord + dir.offsetZ * comp.z - rot.offsetZ * comp.z;
				z = zCoord + dir.offsetX * comp.x - rot.offsetX * comp.x;
			}

			worldObj.setBlock(x, y, z, comp.block, rotateMeta(comp.block, comp.metas[0], dir), 3);
		}
	}

	private void assemble() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
		MachineBlastFurnace furnace = (MachineBlastFurnace) ModBlocks.machine_blast_furnace;
		int o = -furnace.getOffset();
		int spawnY = yCoord - 1;

		BlockDummyable.safeRem = true;
		worldObj.setBlock(xCoord + dir.offsetX * o, spawnY, zCoord + dir.offsetZ * o, ModBlocks.machine_blast_furnace, dir.ordinal() + BlockDummyable.offset, 3);
		furnace.fillSpace(worldObj, xCoord, spawnY, zCoord, dir, o);
		BlockDummyable.safeRem = false;
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 2,
				yCoord - 2,
				zCoord - 2,
				xCoord + 2,
				yCoord + 6,
				zCoord + 2
			);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
