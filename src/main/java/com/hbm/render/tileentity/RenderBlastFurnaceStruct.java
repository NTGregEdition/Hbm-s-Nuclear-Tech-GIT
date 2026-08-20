package com.hbm.render.tileentity;

import org.lwjgl.opengl.GL11;

import com.hbm.render.util.GhostBlockRenderer;
import com.hbm.tileentity.machine.TileEntityBlastFurnaceStruct;
import com.hbm.tileentity.machine.TileEntityBlastFurnaceStruct.Component;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class RenderBlastFurnaceStruct extends TileEntitySpecialRenderer {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		TileEntityBlastFurnaceStruct assembly = (TileEntityBlastFurnaceStruct) tile;

		if(assembly.structureOK) return;

		ForgeDirection dir = ForgeDirection.getOrientation(tile.getBlockMetadata());
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		GL11.glPushMatrix();
		GL11.glTranslated(x - tile.xCoord, y - tile.yCoord, z - tile.zCoord);

		bindTexture(TextureMap.locationBlocksTexture);
		GhostBlockRenderer.begin(0.75F);

		for(Component comp : TileEntityBlastFurnaceStruct.getComponents()) {
			if(comp.block == Blocks.air) continue;

			int rx = getRotatedX(comp, dir, rot);
			int ry = comp.y;
			int rz = getRotatedZ(comp, dir, rot);

			GhostBlockRenderer.renderGhostBlock(tile.getWorldObj(), comp.block, comp.getDisplayMeta(dir),
				tile.xCoord + rx, tile.yCoord + ry, tile.zCoord + rz);
		}

		GhostBlockRenderer.end();

		if(assembly.highlightTimer > 0) {
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glLineWidth(3.0F);
			GL11.glColor4f(1.0F, 0.0F, 0.0F, 1.0F);

			for(Component comp : TileEntityBlastFurnaceStruct.getComponents()) {
				int rx = getRotatedX(comp, dir, rot);
				int ry = comp.y;
				int rz = getRotatedZ(comp, dir, rot);

				int absX = tile.xCoord + rx;
				int absY = tile.yCoord + ry;
				int absZ = tile.zCoord + rz;

				if(!assembly.isComponentValid(comp, absX, absY, absZ)) {
					drawRedBox(absX, absY, absZ);
				}
			}

			GL11.glEnable(GL11.GL_DEPTH_TEST);
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_BLEND);
		}

		GL11.glPopMatrix();
	}

	private int getRotatedX(Component comp, ForgeDirection dir, ForgeDirection rot) {
		if(dir == ForgeDirection.EAST || dir == ForgeDirection.WEST) {
			return dir.offsetZ * comp.z - rot.offsetZ * comp.z;
		}
		return -dir.offsetX * comp.x + rot.offsetX * comp.x;
	}

	private int getRotatedZ(Component comp, ForgeDirection dir, ForgeDirection rot) {
		if(dir == ForgeDirection.EAST || dir == ForgeDirection.WEST) {
			return dir.offsetX * comp.x - rot.offsetX * comp.x;
		}
		return -dir.offsetZ * comp.z + rot.offsetZ * comp.z;
	}

	private void drawRedBox(double x, double y, double z) {
		double minX = x - 0.002D;
		double minY = y - 0.002D;
		double minZ = z - 0.002D;
		double maxX = x + 1.002D;
		double maxY = y + 1.002D;
		double maxZ = z + 1.002D;

		GL11.glBegin(GL11.GL_LINES);

		// Bottom face
		GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(maxX, minY, minZ);
		GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, minY, maxZ);
		GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(minX, minY, maxZ);
		GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, minY, minZ);

		// Top face
		GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
		GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(maxX, maxY, maxZ);
		GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
		GL11.glVertex3d(minX, maxY, maxZ); GL11.glVertex3d(minX, maxY, minZ);

		// Pillars
		GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, maxY, minZ);
		GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
		GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(maxX, maxY, maxZ);
		GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);

		GL11.glEnd();
	}
}
