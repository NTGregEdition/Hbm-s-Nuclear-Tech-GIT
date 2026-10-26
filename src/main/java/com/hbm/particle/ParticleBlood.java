package com.hbm.particle;

import org.lwjgl.opengl.GL11;

import com.hbm.main.ResourceManager;
import com.hbm.physics.Vec3d;
import com.hbm.render.util.DecalUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A stretched blood drop that leaves a decal on the first block it hits. */
@SideOnly(Side.CLIENT)
public class ParticleBlood extends EntityFX {

	private final float stretch;
	private final float gravity;
	private Vec3d originalAxis;

	public ParticleBlood(World world, double x, double y, double z, float stretch, float scale, int lifetime, float gravity) {
		super(world, x, y, z);
		this.stretch = stretch;
		this.particleScale = scale;
		this.particleMaxAge = lifetime;
		this.gravity = gravity;
	}

	public ParticleBlood motion(float mX, float mY, float mZ) {
		this.motionX = mX;
		this.motionY = mY - gravity;
		this.motionZ = mZ;
		this.posX += mX;
		this.posY += mY - gravity;
		this.posZ += mZ;
		originalAxis = new Vec3d(mX, mY, mZ).normalize();
		return this;
	}

	public ParticleBlood color(float colR, float colG, float colB, float colA) {
		this.particleRed = colR;
		this.particleGreen = colG;
		this.particleBlue = colB;
		this.particleAlpha = colA;
		return this;
	}

	@Override
	public void onUpdate() {
		this.particleAge++;
		if(this.particleAge > this.particleMaxAge) {
			this.setDead();
			return;
		}

		MovingObjectPosition r = worldObj.rayTraceBlocks(Vec3.createVectorHelper(posX, posY, posZ), Vec3.createVectorHelper(posX + motionX, posY + motionY, posZ + motionZ));
		if(r != null && r.typeOfHit == MovingObjectType.BLOCK) {
			Vec3d direction = new Vec3d(motionX, motionY, motionZ).normalize();
			int dl = DecalUtil.generateDecalMesh(worldObj, direction, 1, (float) r.hitVec.xCoord, (float) r.hitVec.yCoord, (float) r.hitVec.zCoord);
			direction = direction.scale(0.001F);
			Minecraft.getMinecraft().effectRenderer.addEffect(new ParticleDecal(worldObj, dl, ResourceManager.blood_dec1, 120, (float) r.hitVec.xCoord - direction.x, (float) r.hitVec.yCoord - direction.y, (float) r.hitVec.zCoord - direction.z));
			setDead();
			return;
		}

		this.prevPosX = this.posX;
		this.prevPosY = this.posY;
		this.prevPosZ = this.posZ;
		this.moveEntity(motionX, motionY, motionZ);
		final float airResistance = 0.9F;
		this.motionX *= airResistance;
		this.motionY = motionY * airResistance - gravity;
		this.motionZ *= airResistance;
	}

	@Override
	public int getFXLayer() {
		return 3;
	}

	@Override
	public void renderParticle(Tessellator tess, float partialTicks, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(ResourceManager.blood0);
		float fade = ((float) this.particleAge + partialTicks) / (float) particleMaxAge;
		boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
		GL11.glAlphaFunc(GL11.GL_GREATER, 0);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		float f5 = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) partialTicks - ParticleCamera.x(partialTicks));
		float f6 = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) partialTicks - ParticleCamera.y(partialTicks));
		float f7 = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) partialTicks - ParticleCamera.z(partialTicks));

		Vec3d particleAxis = originalAxis;
		//Is it right to subtract the eye height here? No idea!
		Vec3d toPlayer = new Vec3d(f5, f6 - Minecraft.getMinecraft().thePlayer.getEyeHeight(), f7);
		Vec3d point1 = particleAxis.crossProduct(toPlayer).normalize().scale(0.5 * particleScale * (fade * fade * 1.5F + 1));
		Vec3d point2 = point1.scale(-1);
		point1 = point1.add(f5, f6, f7);
		point2 = point2.add(f5, f6, f7);
		particleAxis = particleAxis.scale(stretch * (fade * fade * 1.5F + 1));

		float a = MathHelper.clamp_float(this.particleAlpha * (1 - fade * fade), 0, 1);
		tess.startDrawingQuads();
		tess.setBrightness(this.getBrightnessForRender(partialTicks));
		tess.setColorRGBA_F(particleRed, particleGreen, particleBlue, a);
		tess.addVertexWithUV(point2.x, point2.y, point2.z, 1, 0);
		tess.addVertexWithUV(point1.x, point1.y, point1.z, 1, 1);
		tess.addVertexWithUV(point1.x + particleAxis.x, point1.y + particleAxis.y, point1.z + particleAxis.z, 0, 1);
		tess.addVertexWithUV(point2.x + particleAxis.x, point2.y + particleAxis.y, point2.z + particleAxis.z, 0, 0);
		tess.draw();

		GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
		if(lighting) GL11.glEnable(GL11.GL_LIGHTING);
	}
}
