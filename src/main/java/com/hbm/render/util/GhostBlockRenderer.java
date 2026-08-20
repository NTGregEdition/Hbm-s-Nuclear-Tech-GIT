package com.hbm.render.util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.ARBImaging;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

public class GhostBlockRenderer {

	private static final RenderBlocks RENDER_BLOCKS = new RenderBlocks();
	private static final SinglePositionOverride ACCESS = new SinglePositionOverride();

	public static void begin(float alpha) {
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendColor(1F, 1F, 1F, alpha);
		GL11.glBlendFunc(ARBImaging.GL_CONSTANT_ALPHA, ARBImaging.GL_ONE_MINUS_CONSTANT_ALPHA);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);

		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(true);

		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

		RENDER_BLOCKS.renderAllFaces = true;
	}

	public static void end() {
		RENDER_BLOCKS.renderAllFaces = false;
		GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LIGHTING);
	}

	public static void renderGhostBlock(World world, Block block, int meta, int x, int y, int z) {

		if (block == null || block == Blocks.air) return;

		if (world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == meta) return;

		ACCESS.setup(world, block, meta, x, y, z);
		RENDER_BLOCKS.blockAccess = ACCESS;

		GL11.glPushMatrix();
		GL11.glTranslatef(x + 0.5F, y + 0.5F, z + 0.5F);
		GL11.glScalef(0.32F, 0.32F, 0.32F);
		GL11.glTranslatef(-(x + 0.5F), -(y + 0.5F), -(z + 0.5F));

		Tessellator.instance.startDrawingQuads();
		RENDER_BLOCKS.renderBlockByRenderType(block, x, y, z);
		Tessellator.instance.draw();

		GL11.glPopMatrix();
	}

	private static class SinglePositionOverride implements IBlockAccess {

		World real;
		Block fakeBlock;
		int fakeMeta, fx, fy, fz;

		void setup(World real, Block fakeBlock, int fakeMeta, int fx, int fy, int fz) {
			this.real = real;
			this.fakeBlock = fakeBlock;
			this.fakeMeta = fakeMeta;
			this.fx = fx;
			this.fy = fy;
			this.fz = fz;
		}

		private boolean isTarget(int x, int y, int z) {
			return x == fx && y == fy && z == fz;
		}

		@Override
		public Block getBlock(int x, int y, int z) {
			return isTarget(x, y, z) ? fakeBlock : real.getBlock(x, y, z);
		}

		@Override
		public int getBlockMetadata(int x, int y, int z) {
			return isTarget(x, y, z) ? fakeMeta : real.getBlockMetadata(x, y, z);
		}

		@Override
		public TileEntity getTileEntity(int x, int y, int z) {
			return isTarget(x, y, z) ? null : real.getTileEntity(x, y, z);
		}

		@Override
		public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) {
			return 0xF000F0;
		}

		@Override
		public boolean isAirBlock(int x, int y, int z) {
			return isTarget(x, y, z) ? fakeBlock.isAir(this, x, y, z) : real.isAirBlock(x, y, z);
		}

		@Override
		public BiomeGenBase getBiomeGenForCoords(int x, int z) {
			return real.getBiomeGenForCoords(x, z);
		}

		@Override
		public int getHeight() {
			return real.getHeight();
		}

		@Override
		public boolean extendedLevelsInChunkCache() {
			return real.extendedLevelsInChunkCache();
		}

		@Override
		public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
			return real.isBlockProvidingPowerTo(x, y, z, side);
		}

		@Override
		public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
			return isTarget(x, y, z) ? fakeBlock.isSideSolid(this, x, y, z, side) : real.isSideSolid(x, y, z, side, _default);
		}
	}
}
