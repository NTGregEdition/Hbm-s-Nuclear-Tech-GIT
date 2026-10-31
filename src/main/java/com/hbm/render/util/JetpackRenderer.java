package com.hbm.render.util;

import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import com.hbm.animloader.AnimationWrapper;
import com.hbm.animloader.AnimationWrapper.EndResult;
import com.hbm.animloader.AnimationWrapper.EndType;
import com.hbm.handler.JetpackHandler;
import com.hbm.handler.JetpackHandler.JetpackInfo;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.main.ResourceManager;
import com.hbm.particle.JetpackParticles;
import com.hbm.util.BobMathUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

@SideOnly(Side.CLIENT)
public final class JetpackRenderer {

	private static final EndResult STAY = new EndResult(EndType.STAY, null);

	private static final FloatBuffer MATRIX_BUF = GLAllocation.createDirectFloatBuffer(16);
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f VIEW_INV = new Matrix4f();
	private static final Matrix4f CURRENT = new Matrix4f();
	private static final Matrix4f TO_WORLD = new Matrix4f();
	private static final Vector4f IN = new Vector4f();
	private static final Vector4f OUT = new Vector4f();

	/** Whether the camera matrix was captured for the player that is currently being rendered. */
	private static boolean viewCaptured = false;
	/** Pre and post render have to agree on whether a matrix was pushed, the active state can change in between. */
	private static boolean pushed = false;

	private JetpackRenderer() { }

	/** Fired before the player is drawn; tips the whole player over around the feet. Must be paired with {@link #postRender}. */
	public static void preRender(EntityPlayer player, float partialTicks) {
		if(!JetpackHandler.hasJetpack(player) || !JetpackHandler.jetpackActive(player)) return;

		readMatrix(VIEW);
		Matrix4f.invert(VIEW, VIEW_INV);
		viewCaptured = true;

		GL11.glPushMatrix();
		pushed = true;

		Entity cam = Minecraft.getMinecraft().renderViewEntity;
		double offsetX = lerp(player.lastTickPosX, player.posX, partialTicks) - lerp(cam.lastTickPosX, cam.posX, partialTicks);
		double offsetY = lerp(player.lastTickPosY, player.posY, partialTicks) - player.yOffset - lerp(cam.lastTickPosY, cam.posY, partialTicks);
		double offsetZ = lerp(player.lastTickPosZ, player.posZ, partialTicks) - lerp(cam.lastTickPosZ, cam.posZ, partialTicks);
		GL11.glTranslated(offsetX, offsetY, offsetZ);

		JetpackInfo j = JetpackHandler.get(player);
		if(JetpackHandler.isHovering(player)) {
			if(j != null) {
				float prevMX = (float) (player.prevPosX - j.prevPrevPosX);
				float prevMZ = (float) (player.prevPosZ - j.prevPrevPosZ);
				float mX = (float) (player.posX - player.prevPosX);
				float mZ = (float) (player.posZ - player.prevPosZ);
				float motionX = prevMX + (mX - prevMX) * partialTicks;
				float motionZ = prevMZ + (mZ - prevMZ) * partialTicks;
				float angle = (float) (Math.atan2(motionX, motionZ) + Math.PI * 0.5F);
				float amount = MathHelper.clamp_float(MathHelper.sqrt_float(motionX * motionX + motionZ * motionZ), 0, 2);
				GL11.glRotatef(amount * 22.5F, (float) Math.toDegrees(MathHelper.sin(angle)), 0, (float) Math.toDegrees(MathHelper.cos(angle)));
			}
		} else if(!player.onGround && j != null && j.failureTicks <= 0 && j.fuel > 0) {
			Vec3 look = player.getLook(partialTicks);
			float renderYaw = interpolateRotation(player.prevRenderYawOffset, player.renderYawOffset, partialTicks);
			GL11.glRotatef(180.0F - renderYaw, 0.0F, 1.0F, 0.0F);
			float time = j.jetpackFlyTime + partialTicks;
			float mult = BobMathUtil.remap01_clamp(time * time * 0.5F, 0, 100);
			GL11.glRotatef((-player.rotationPitch - 90) * mult, 1, 0, 0);

			float lookX = (float) look.xCoord;
			float lookZ = (float) look.zCoord;
			float rotX = MathHelper.cos((float) Math.toRadians(renderYaw + 90));
			float rotZ = MathHelper.sin((float) Math.toRadians(renderYaw + 90));
			float lookLenSq = lookX * lookX + lookZ * lookZ;
			float rotLenSq = rotX * rotX + rotZ * rotZ;
			if(lookLenSq != 0 && rotLenSq != 0) {
				float lookLen = MathHelper.sqrt_float(lookLenSq);
				float rotLen = MathHelper.sqrt_float(rotLenSq);
				lookX /= lookLen;
				lookZ /= lookLen;
				rotX /= rotLen;
				rotZ /= rotLen;
				float angle = (float) Math.acos(Math.max(lookX * rotX + lookZ * rotZ, 0));
				if(!Float.isNaN(angle)) {
					float cross = lookZ * rotX - rotZ * lookX;
					GL11.glRotatef((float) (Math.toDegrees(angle) * Math.signum(cross) * mult), 0, 1, 0);
				}
			}
			GL11.glRotatef(-(180.0F - renderYaw), 0.0F, 1.0F, 0.0F);
		}
		GL11.glTranslated(-offsetX, -offsetY, -offsetZ);
	}

	/** Fired after the player was drawn; undoes {@link #preRender} and draws the wing trails in world space. */
	public static void postRender(EntityPlayer player, float partialTicks) {
		if(!pushed) return;
		pushed = false;
		viewCaptured = false;

		GL11.glPopMatrix();
		JetpackInfo info = JetpackHandler.get(player);
		if(info == null || !JetpackHandler.jetpackActive(player)) return;

		if(!JetpackHandler.isHovering(player) && info.fuel > 0) {
			double speedSq = player.motionX * player.motionX + player.motionY * player.motionY + player.motionZ * player.motionZ;
			if(!player.onGround && info.failureTicks <= 0 && info.spawnPositionsValid && speedSq > 1.5 * 1.5) {
				if(info.trailLeft == null) {
					info.trailLeft = new JetpackParticles.Trail();
					info.trailRight = new JetpackParticles.Trail();
					info.trails.add(info.trailLeft);
					info.trails.add(info.trailRight);
				}
				info.trailLeft.tryAddNewPos(info.spawnPositions[0], info.spawnPositions[1], info.spawnPositions[2]);
				info.trailRight.tryAddNewPos(info.spawnPositions[3], info.spawnPositions[4], info.spawnPositions[5]);
			} else {
				info.trailLeft = null;
				info.trailRight = null;
			}
		}

		if(!info.trails.isEmpty()) {
			Entity cam = Minecraft.getMinecraft().renderViewEntity;
			GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glDepthMask(false);
			JetpackParticles.renderTrails(info.trails,
					lerp(cam.lastTickPosX, cam.posX, partialTicks),
					lerp(cam.lastTickPosY, cam.posY, partialTicks),
					lerp(cam.lastTickPosZ, cam.posZ, partialTicks));
			GL11.glPopAttrib();
		}
	}

	/** Draws the animated glider on the player's back plus its flames, glow and heat haze. Called in the player's model space. */
	public static void renderLayer(EntityPlayer player, float partialTicks) {
		if(!JetpackHandler.hasJetpack(player)) return;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
		//the held item is drawn right before this and leaves its tint behind
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glTranslated(0, 0.9, 1.25);
		GL11.glRotatef(180, 0, 1, 0);
		GL11.glRotatef(180, 0, 0, 1);
		GL11.glScaled(0.25, 0.25, 0.25);
		if(player.isSneaking()) {
			GL11.glTranslated(0, 5.4, 1);
			GL11.glRotatef((float) Math.toDegrees(0.5), 1, 0, 0);
			GL11.glTranslated(0, -4, 0);
		}
		GL11.glShadeModel(GL11.GL_SMOOTH);
		Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.jetpack_tex);

		AnimationWrapper w = new AnimationWrapper(JetpackHandler.getAnimTime(player), ResourceManager.jetpack_activate);
		if(!JetpackHandler.isOpening(player)) w.reverse();
		w.onEnd(STAY);
		ResourceManager.jetpack.controller.setAnim(w);
		ResourceManager.jetpack.renderAnimated(System.currentTimeMillis());
		GL11.glShadeModel(GL11.GL_FLAT);

		JetpackInfo j = JetpackHandler.get(player);
		if(j != null) renderEffects(player, j, partialTicks);

		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private static void renderEffects(EntityPlayer player, JetpackInfo j, float partialTicks) {
		if(viewCaptured) updateSpawnPositions(player, j, partialTicks);

		JetpackParticles.captureMatrix();
		JetpackParticles.bindMask();
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDepthMask(false);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
		GL11.glColor4f(1F, 1F, 1F, 1F);

		// the particles are projected through the captured matrix, so they are drawn in plain view space
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		JetpackParticles.renderHaze(j.haze, partialTicks);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		JetpackParticles.renderPlasma(j.plasma, partialTicks);
		JetpackParticles.renderGlow(j.glow, partialTicks);
		GL11.glPopMatrix();
	}

	/** Converts the wing tip nozzles from the glider's local space into world space. */
	private static void updateSpawnPositions(EntityPlayer player, JetpackInfo j, float partialTicks) {
		readMatrix(CURRENT);
		Matrix4f.mul(VIEW_INV, CURRENT, TO_WORLD);

		Entity cam = Minecraft.getMinecraft().renderViewEntity;
		double camX = lerp(cam.lastTickPosX, cam.posX, partialTicks);
		double camY = lerp(cam.lastTickPosY, cam.posY, partialTicks);
		double camZ = lerp(cam.lastTickPosZ, cam.posZ, partialTicks);

		toWorld(5.25F, 0.2F, 2.75F, camX, camY, camZ, j.spawnPositions, 0);
		toWorld(-5.25F, 0.2F, 2.75F, camX, camY, camZ, j.spawnPositions, 3);
		j.spawnPositionsValid = true;
	}

	private static void toWorld(float x, float y, float z, double camX, double camY, double camZ, double[] out, int offset) {
		IN.set(x, y, z, 1F);
		Matrix4f.transform(TO_WORLD, IN, OUT);
		out[offset] = OUT.x + camX;
		out[offset + 1] = OUT.y + camY;
		out[offset + 2] = OUT.z + camZ;
	}

	private static void readMatrix(Matrix4f target) {
		MATRIX_BUF.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUF);
		target.load(MATRIX_BUF);
	}

	private static double lerp(double prev, double cur, float partialTicks) {
		return prev + (cur - prev) * partialTicks;
	}

	private static float interpolateRotation(float prevYawOffset, float yawOffset, float partialTicks) {
		float f = yawOffset - prevYawOffset;
		while(f < -180.0F) f += 360.0F;
		while(f >= 180.0F) f -= 360.0F;
		return prevYawOffset + partialTicks * f;
	}

	public static void renderHUD(EntityPlayer p, ScaledResolution res, float partialTicks) {
		if(!JetpackHandler.hasJetpack(p)) return;
		JetpackInfo info = JetpackHandler.get(p);
		if(info == null) return;

		Minecraft mc = Minecraft.getMinecraft();
		int hudYOffset = 45;
		int screenH = res.getScaledHeight();
		float fuelFill = info.fuelMax > 0 ? (float) info.fuel / info.fuelMax : 0;
		float thrust = info.prevThrust + (info.thrust - info.prevThrust) * partialTicks;
		float thrustDegrees = MathHelper.clamp_float(thrust * 100 - 27, -27, 200);
		float fuelDegrees = fuelFill * 227 - 27;

		GL11.glColor4f(1F, 1F, 1F, 1F);
		if(info.useCompactHUD) {
			float maxHeight = 0.78125F;
			float maxHeightPixels = 50 * maxHeight;
			bindHudTexture(mc, ResourceManager.jetpack_hud_small);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
			drawGuiRect(0, screenH - maxHeightPixels - hudYOffset, 0, 1 - maxHeight, 50, maxHeightPixels, 1, 1);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			float oX = 31 / 256F;
			float width = 88 / 256F - oX;
			float oY = 8 / 256F;
			float height = 31 / 256F - oY;
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			//Thrust
			GL11.glPushMatrix();
			float rX = oX * 50 + 40.5F * 50 / 256F;
			float rY = screenH - (maxHeightPixels - maxHeightPixels * oY) - hudYOffset + 14.5F * maxHeightPixels / 256F;
			GL11.glTranslatef(rX + (117 / 256F) * 50, rY + (76 / 256F) * maxHeightPixels, 0);
			GL11.glRotatef(thrustDegrees, 0, 0, 1);
			GL11.glTranslatef(-rX, -rY, 0);
			drawGuiRect(oX * 50, screenH - (maxHeightPixels - maxHeightPixels * oY) - hudYOffset, oX, oY, width * 50, height * 50, oX + width, oY + height);
			GL11.glPopMatrix();

			//Fuel
			GL11.glPushMatrix();
			GL11.glTranslatef(rX, rY + (76 / 256F) * maxHeightPixels, 0);
			GL11.glRotatef(fuelDegrees, 0, 0, 1);
			GL11.glTranslatef(-rX, -rY, 0);
			drawGuiRect(oX * 50, screenH - (maxHeightPixels - maxHeightPixels * oY) - hudYOffset, oX, oY, width * 50, height * 50, oX + width, oY + height);
			GL11.glPopMatrix();

			bindHudTexture(mc, ResourceManager.jetpack_hud_small_text);
			float yOffset;
			float yPosition;
			float yHeight;
			if(info.failureTicks > 0) {
				yOffset = 76 / 128F;
				yHeight = 104 / 128F - yOffset;
				yPosition = -0.5F;
			} else if(JetpackHandler.jetpackActive(p)) {
				if(info.hover) {
					yOffset = 25 / 128F;
					yHeight = 46 / 128F - yOffset;
					yPosition = -10.25F;
				} else {
					yOffset = 1 / 128F;
					yHeight = 23 / 128F - yOffset;
					yPosition = -14.75F;
				}
			} else {
				yOffset = 47 / 128F;
				yHeight = 75 / 128F - yOffset;
				yPosition = -6;
			}
			drawGuiRect(0, screenH - 25 - (yOffset - yOffset * 25) - yPosition - hudYOffset, 0, yOffset, 50, 25 * yHeight, 1, yOffset + yHeight);
		} else {
			bindHudTexture(mc, ResourceManager.jetpack_hud_large);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
			drawGuiRect(0, screenH - 80 - hudYOffset, 0, 0, 80, 80, 0.5F, 1);
			GL11.glDisable(GL11.GL_ALPHA_TEST);

			boolean active = JetpackHandler.jetpackActive(p);
			boolean hover = JetpackHandler.isHovering(p);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			float oX = 40 / 512F;
			float width = 172 / 512F - oX;
			if(active) {
				drawGuiRect(oX * 80, screenH - 40 - hudYOffset, 0.5F + oX * 0.5F, 0.5F, width * 80, 40, 0.5F + (width + oX) * 0.5F, 1);
			}
			oX = 172 / 512F;
			width = 282 / 512F - oX;
			if(hover) {
				drawGuiRect(oX * 80, screenH - 40 - hudYOffset, 0.5F + oX * 0.5F, 0.5F, width * 80, 40, 0.5F + (width + oX) * 0.5F, 1);
			}
			oX = 282 / 512F;
			width = 383 / 512F - oX;
			if(!hover) {
				drawGuiRect(oX * 80, screenH - 40 - hudYOffset, 0.5F + oX * 0.5F, 0.5F, width * 80, 40, 0.5F + (width + oX) * 0.5F, 1);
			}
			oX = 383 / 512F;
			width = 512 / 512F - oX;
			if(info.failureTicks > 0) {
				drawGuiRect(oX * 80, screenH - 40 - hudYOffset, 0.5F + oX * 0.5F, 0.5F, width * 80, 40, 0.5F + (width + oX) * 0.5F, 1);
			}
			oX = 49 / 512F;
			width = 134 / 512F - oX;
			float oY = 148 / 512F;
			float height = 171 / 512F - oY;
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			//Thrust
			GL11.glPushMatrix();
			float rX = oX * 80 + 61.5F * 80 / 512F;
			float rY = screenH - (80 - oY * 80) - hudYOffset + 11.5F * 80 / 512F;
			GL11.glTranslatef(rX, rY, 0);
			GL11.glRotatef(thrustDegrees, 0, 0, 1);
			GL11.glTranslatef(-rX, -rY, 0);
			drawGuiRect(oX * 80, screenH - (80 - oY * 80) - hudYOffset, 0.5F + oX * 0.5F, oY, width * 80, height * 80, 0.5F + (width + oX) * 0.5F, oY + height);
			GL11.glPopMatrix();

			//Fuel
			GL11.glPushMatrix();
			GL11.glTranslatef(rX + 80 * 179 / 512F, rY, 0);
			GL11.glRotatef(fuelDegrees, 0, 0, 1);
			GL11.glTranslatef(-rX, -rY, 0);
			drawGuiRect(oX * 80, screenH - (80 - oY * 80) - hudYOffset, 0.5F + oX * 0.5F, oY, width * 80, height * 80, 0.5F + (width + oX) * 0.5F, oY + height);
			GL11.glPopMatrix();

			if(info.fuel > 0 && info.fuelType != Fluids.NONE) {
				int color = info.fuelType.getTint();
				GL11.glColor3f(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F);
				mc.renderEngine.bindTexture(info.fuelType.getTexture());
				drawGuiRect(66.8F, screenH - 71.3F - hudYOffset, 0, 0, 6.5F, 6.8F, 6.5F / 16F, 6.8F / 16F);
			}
		}
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
	}

	private static void bindHudTexture(Minecraft mc, ResourceLocation tex) {
		mc.getTextureManager().bindTexture(tex);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
	}

	private static void drawGuiRect(float x, float y, float u, float v, float width, float height, float uMax, float vMax) {
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertexWithUV(x, y + height, 0, u, vMax);
		t.addVertexWithUV(x + width, y + height, 0, uMax, vMax);
		t.addVertexWithUV(x + width, y, 0, uMax, v);
		t.addVertexWithUV(x, y, 0, u, v);
		t.draw();
	}
}
