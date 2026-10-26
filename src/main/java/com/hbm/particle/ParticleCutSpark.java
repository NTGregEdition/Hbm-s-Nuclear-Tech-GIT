package com.hbm.particle;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.hbm.main.ResourceManager;
import com.hbm.physics.Vec3d;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

@SideOnly(Side.CLIENT)
public class ParticleCutSpark extends EntityFX {

	private static final int FULL_BRIGHT = 240 << 16 | 240;

	private final float stretch;
	private final float gravity;
	private boolean canCollide = true;

	public ParticleCutSpark(World world, double x, double y, double z, float stretch, float scale, int lifetime, float gravity) {
		super(world, x, y, z);
		this.stretch = stretch;
		this.particleScale = scale;
		this.particleMaxAge = lifetime;
		this.gravity = gravity;
	}

	public ParticleCutSpark color(float colR, float colG, float colB, float colA) {
		this.particleRed = colR;
		this.particleGreen = colG;
		this.particleBlue = colB;
		this.particleAlpha = colA;
		return this;
	}

	public ParticleCutSpark motion(float mX, float mY, float mZ) {
		this.motionX = mX;
		this.motionY = mY - gravity;
		this.motionZ = mZ;
		this.posX += mX;
		this.posY += mY - gravity;
		this.posZ += mZ;
		return this;
	}

	@Override
	public void onUpdate() {
		this.particleAge++;
		if(this.particleAge >= this.particleMaxAge || new Vec3d(motionX, motionY, motionZ).lengthSquared() < 0.001) {
			setDead();
			return;
		}
		this.canCollide = this.particleAge >= 4;
		this.prevPosX = this.posX;
		this.prevPosY = this.posY;
		this.prevPosZ = this.posZ;
		this.moveEntity(motionX, motionY, motionZ);
		final float airResistance = 0.95F;
		this.motionX *= airResistance;
		this.motionY = motionY * airResistance - gravity;
		this.motionZ *= airResistance;
	}

	@Override
	public void moveEntity(double x, double y, double z) {
		double d0 = y;
		double origX = x;
		double origZ = z;

		if(this.canCollide) {
			@SuppressWarnings("unchecked")
			List<AxisAlignedBB> list = this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(x, y, z));

			for(AxisAlignedBB box : list) {
				y = box.calculateYOffset(this.boundingBox, y);
			}
			this.boundingBox.offset(0.0D, y, 0.0D);

			for(AxisAlignedBB box : list) {
				x = box.calculateXOffset(this.boundingBox, x);
			}
			this.boundingBox.offset(x, 0.0D, 0.0D);

			for(AxisAlignedBB box : list) {
				z = box.calculateZOffset(this.boundingBox, z);
			}
			this.boundingBox.offset(0.0D, 0.0D, z);
		} else {
			this.boundingBox.offset(x, y, z);
		}

		this.posX = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0D;
		this.posY = this.boundingBox.minY + (double) this.yOffset;
		this.posZ = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0D;
		this.onGround = d0 != y && d0 < 0.0D;

		if(d0 != y) {
			this.motionY = -motionY * 0.75 * (rand.nextFloat() * 0.8 + 0.25);
		}

		if(origX != x) {
			this.motionX = -motionX * 0.75 * (rand.nextFloat() * 1.12 + 0.25);
		}

		if(origZ != z) {
			this.motionZ = -motionY * 0.75 * (rand.nextFloat() * 1.12 + 0.25);
		}
	}

	@Override
	public int getFXLayer() {
		return 3;
	}

	@Override
	public void renderParticle(Tessellator tess, float partialTicks, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(ResourceManager.bfg_particle);
		boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDisable(GL11.GL_LIGHTING);

		double camX = ParticleCamera.x(partialTicks);
		double camY = ParticleCamera.y(partialTicks);
		double camZ = ParticleCamera.z(partialTicks);
		float f5 = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) partialTicks - camX);
		float f6 = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) partialTicks - camY);
		float f7 = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) partialTicks - camZ);
		float mX = (float) (this.posX + (this.posX + this.motionX - this.posX) * (double) partialTicks - camX);
		float mY = (float) (this.posY + (this.posY + this.motionY - this.posY) * (double) partialTicks - camY);
		float mZ = (float) (this.posZ + (this.posZ + this.motionZ - this.posZ) * (double) partialTicks - camZ);

		Vec3d particleAxis = new Vec3d(mX - f5, mY - f6, mZ - f7);
		Vec3d toPlayer = new Vec3d(mX, mY, mZ);
		Vec3d point1 = particleAxis.crossProduct(toPlayer).normalize().scale(0.5 * particleScale);
		Vec3d point2 = point1.scale(-1);
		point1 = point1.add(f5, f6, f7);
		point2 = point2.add(f5, f6, f7);
		particleAxis = particleAxis.scale(stretch);

		tess.startDrawingQuads();
		tess.setBrightness(FULL_BRIGHT);
		//alpha above 1 stacks extra quads so the streak glows brighter
		float alpha = this.particleAlpha;
		while(alpha > 0) {
			tess.setColorRGBA_F(particleRed, particleGreen, particleBlue, MathHelper.clamp_float(alpha, 0, 1));
			tess.addVertexWithUV(point2.x, point2.y, point2.z, 1, 0);
			tess.addVertexWithUV(point1.x, point1.y, point1.z, 1, 1);
			tess.addVertexWithUV(point1.x + particleAxis.x, point1.y + particleAxis.y, point1.z + particleAxis.z, 0, 1);
			tess.addVertexWithUV(point2.x + particleAxis.x, point2.y + particleAxis.y, point2.z + particleAxis.z, 0, 0);
			alpha -= 1;
		}
		tess.draw();

		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		if(lighting) GL11.glEnable(GL11.GL_LIGHTING);
	}
}
