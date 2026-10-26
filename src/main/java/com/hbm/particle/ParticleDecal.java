package com.hbm.particle;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/** A mesh projected onto nearby blocks that fades out over its last 30 ticks. */
@SideOnly(Side.CLIENT)
public class ParticleDecal extends EntityFX {

	private final ResourceLocation tex;
	private final int displayList;

	public ParticleDecal(World world, int displayList, ResourceLocation texture, int maxAge, double x, double y, double z) {
		super(world, x, y, z);
		this.displayList = displayList;
		this.tex = texture;
		this.particleMaxAge = maxAge;
	}

	@Override
	public void onUpdate() {
		this.particleAge++;
		if(this.particleAge > this.particleMaxAge) {
			setDead();
			GL11.glDeleteLists(displayList, 1);
		}
	}

	@Override
	public int getFXLayer() {
		return 3;
	}

	@Override
	public void renderParticle(Tessellator tess, float partialTicks, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
		int i = this.getBrightnessForRender(partialTicks);
		int j = i >> 16 & 65535;
		int k = i & 65535;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, k, j);
		boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
		boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
		GL11.glPushMatrix();
		GL11.glTranslated(posX - ParticleCamera.x(partialTicks), posY - ParticleCamera.y(partialTicks), posZ - ParticleCamera.z(partialTicks));
		Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glAlphaFunc(GL11.GL_GREATER, 0);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_COLOR_MATERIAL);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		float fade = (this.particleAge - particleMaxAge + 30 + partialTicks) / 30F;
		GL11.glColor4f(0.5F, 0.1F, 0.1F, 1 - fade);
		GL11.glPolygonOffset(-5, -5);
		GL11.glCallList(displayList);
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDepthMask(true);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		if(!lighting) GL11.glDisable(GL11.GL_LIGHTING);
		if(!colorMaterial) GL11.glDisable(GL11.GL_COLOR_MATERIAL);
		GL11.glPopMatrix();
	}
}
