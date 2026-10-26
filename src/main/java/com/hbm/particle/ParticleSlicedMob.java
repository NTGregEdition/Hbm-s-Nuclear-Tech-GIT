package com.hbm.particle;

import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.hbm.physics.RigidBody;
import com.hbm.physics.Vec3d;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/** One half of a sliced mob, a rigid body drawn from two display lists. */
@SideOnly(Side.CLIENT)
public class ParticleSlicedMob extends EntityFX {

	private static final FloatBuffer MATRIX_BUFFER = GLAllocation.createDirectFloatBuffer(16);

	private final ResourceLocation capTex;
	private final ResourceLocation texture;
	private final boolean glowingCap;
	private final int cutMob;
	private final int cap;
	private final RigidBody body;

	public ParticleSlicedMob(World world, RigidBody body, int cutMob, int cap, ResourceLocation tex, ResourceLocation capTex, boolean glowingCap) {
		super(world, body.globalCentroid.x, body.globalCentroid.y, body.globalCentroid.z);
		this.body = body;
		this.cutMob = cutMob;
		this.cap = cap;
		this.capTex = capTex;
		this.glowingCap = glowingCap;
		this.texture = tex;
		this.particleMaxAge = 80 + world.rand.nextInt(20);
	}

	@Override
	public void onUpdate() {
		body.minecraftTimestep();
		this.posX = body.globalCentroid.x;
		this.posY = body.globalCentroid.y;
		this.posZ = body.globalCentroid.z;
		this.particleAge++;
		if(particleAge >= particleMaxAge) {
			setDead();
			GL11.glDeleteLists(cutMob, 1);
			GL11.glDeleteLists(cap, 1);
		}
	}

	@Override
	public int getFXLayer() {
		return 3;
	}

	@Override
	public void renderParticle(Tessellator tess, float partialTicks, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_COLOR_MATERIAL);
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		RenderHelper.enableStandardItemLighting();
		int i = this.getBrightnessForRender(partialTicks);
		int j = i >> 16 & 65535;
		int k = i & 65535;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, k, j);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		body.writeGlMatrix(MATRIX_BUFFER, new Vec3d(ParticleCamera.x(partialTicks), ParticleCamera.y(partialTicks), ParticleCamera.z(partialTicks)), partialTicks);
		GL11.glMultMatrix(MATRIX_BUFFER);
		GL11.glCallList(cutMob);
		Minecraft.getMinecraft().getTextureManager().bindTexture(capTex);
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-1, -1);
		float lx = OpenGlHelper.lastBrightnessX;
		float ly = OpenGlHelper.lastBrightnessY;
		if(glowingCap) {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
		}
		GL11.glCallList(cap);

		if(glowingCap) {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
		}
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		RenderHelper.disableStandardItemLighting();
		GL11.glPopMatrix();
	}
}
