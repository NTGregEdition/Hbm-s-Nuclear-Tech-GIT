package com.hbm.render.item.weapon;

import java.nio.DoubleBuffer;

import org.lwjgl.opengl.GL11;

import com.hbm.animloader.AnimatedModel.IAnimatedModelCallback;
import com.hbm.animloader.AnimationWrapper;
import com.hbm.animloader.AnimationWrapper.EndResult;
import com.hbm.animloader.AnimationWrapper.EndType;
import com.hbm.items.weapon.ItemCrucible;
import com.hbm.main.ResourceManager;
import com.hbm.render.anim.BusAnimation;
import com.hbm.render.anim.HbmAnimations;
import com.hbm.render.anim.HbmAnimations.Animation;
import com.hbm.render.anim.HbmAnimations.BlenderAnimation;
import com.hbm.render.shader.HeatDistortion;
import com.hbm.util.BobMathUtil;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

public class ItemRenderCrucible implements IItemRenderer {

	/** length of the rig's equip animation in ms */
	public static final int EQUIP_TIME = 1060;
	/** how far into the equip animation a crucible without charge is held, the guards are still folded and the blade hasn't come out */
	private static final int DRAINED_TIME = 400;
	/** how long the item may go undrawn while the server's equip animation is on its way before the rig is started on the client's own */
	private static final long RIG_GRACE = 400L;
	/** the rig lives in the first animation rail of the held slot */
	private static final int RIG_RAIL = 0;

	private static final DoubleBuffer CLIP_PLANE = GLAllocation.createDirectByteBuffer(4 * 8).asDoubleBuffer();
	private static final RigCallback CALLBACK = new RigCallback();
	private static final int HALO_LAYERS = 8;

	private static long missingSince = -1L;
	private static long lastMissingFrame = -1L;

	public ItemRenderCrucible() { }

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type) {
		switch(type) {
		case EQUIPPED:
		case EQUIPPED_FIRST_PERSON:
		case ENTITY:
		case INVENTORY:
			return true;
		default: return false;
		}
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
		return type == ItemRenderType.ENTITY && (helper == ItemRendererHelper.ENTITY_BOBBING || helper == ItemRendererHelper.ENTITY_ROTATION);
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		GL11.glPushMatrix();
		
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		GL11.glShadeModel(GL11.GL_SMOOTH);
		
		boolean isOn = item.getItemDamage() < item.getMaxDamage();
		
		switch(type) {
		case EQUIPPED_FIRST_PERSON:
			renderFirstPerson(player, item, isOn);
			break;

		case ENTITY:
			GL11.glTranslated(-0.75, 0.6, 0);
			GL11.glRotated(-45, 0, 0, 1);
		case EQUIPPED:

			GL11.glRotated(45, 0, 0, 1);
			GL11.glTranslated(0.75, -0.4, 0);
			GL11.glRotated(90, 0, 1, 0);
			GL11.glScaled(0.15, 0.15, 0.15);

			Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.crucible_hilt);
			ResourceManager.crucible.renderPart("Hilt");
			Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.crucible_guard);
			GL11.glPushMatrix();
			GL11.glTranslated(0, 3, 0.5);
			GL11.glRotated(isOn ? 0 : 90, -1, 0, 0);
			GL11.glTranslated(0, -3, -0.5);
			ResourceManager.crucible.renderPart("GuardLeft");
			GL11.glPopMatrix();

			GL11.glPushMatrix();
			GL11.glTranslated(0, 3, -0.5);
			GL11.glRotated(isOn ? 0 : 90, 1, 0, 0);
			GL11.glTranslated(0, -3, 0.5);
			ResourceManager.crucible.renderPart("GuardRight");
			GL11.glPopMatrix();
			
			if(isOn) {
				GL11.glPushMatrix();
				GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
	
				GL11.glDisable(GL11.GL_LIGHTING);
				GL11.glDisable(GL11.GL_CULL_FACE);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
				GL11.glTranslated(0.005, 0, 0);
				Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.crucible_blade);
				ResourceManager.crucible.renderPart("Blade");
				renderHalo(-1, 7.22D, 0.06D, 4.16D, 1.88D, 0.6D);
				if(type == ItemRenderType.EQUIPPED) renderHeat(-1, 7.22D, 1.15D, 1.05D, 1.15D);
				GL11.glEnable(GL11.GL_LIGHTING);
	
				GL11.glPopAttrib();
				GL11.glPopMatrix();
			}
			
			break;
		case INVENTORY:

			GL11.glTranslated(2, 14, 0);
			GL11.glRotated(-135, 0, 0, 1);
			GL11.glRotated(90, 0, 1, 0);
			double scale = 1.5D;
			GL11.glScaled(scale, scale, scale);

			Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.crucible_hilt);
			ResourceManager.crucible.renderPart("Hilt");
			Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.crucible_guard);
			GL11.glPushMatrix();
			GL11.glTranslated(0, 3, 0.5);
			GL11.glRotated(isOn ? 0 : 90, -1, 0, 0);
			GL11.glTranslated(0, -3, -0.5);
			ResourceManager.crucible.renderPart("GuardLeft");
			GL11.glPopMatrix();

			GL11.glPushMatrix();
			GL11.glTranslated(0, 3, -0.5);
			GL11.glRotated(isOn ? 0 : 90, 1, 0, 0);
			GL11.glTranslated(0, -3, 0.5);
			ResourceManager.crucible.renderPart("GuardRight");
			GL11.glPopMatrix();
			GL11.glTranslated(0.005, 0, 0);
			if(isOn) {
				GL11.glDisable(GL11.GL_LIGHTING);
				Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.crucible_blade);
				ResourceManager.crucible.renderPart("Blade");
				renderHalo(-1, 7.22D, 0.06D, 4.16D, 1.88D, 0.6D);
				GL11.glEnable(GL11.GL_LIGHTING);
			}
			break;

		default: break;
		}
		
		GL11.glShadeModel(GL11.GL_FLAT);
		
		GL11.glPopMatrix();
	}

	/**
	 * Soft glow around the blade, shells of the blade that get bigger and fainter, white-hot near the blade and red further out.
	 * list is the blade's display list or -1 for the static model, cy the center of the blade, hx/hy/hz its half extents and reach how far the glow goes.
	 */
	private static void renderHalo(int list, double cy, double hx, double hy, double hz, double reach) {
		float pulse = 0.9F + 0.1F * (float) Math.sin(System.currentTimeMillis() * 0.012D);

		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);

		for(int i = 1; i <= HALO_LAYERS; i++) {
			float t = (i - 0.5F) / HALO_LAYERS;
			double d = reach * i / HALO_LAYERS;
			GL11.glColor4f(1F, 0.75F - 0.55F * t, 0.6F - 0.5F * t, 0.4F * 2F / HALO_LAYERS * (1F - t) * pulse);
			GL11.glPushMatrix();
			GL11.glTranslated(0, cy, 0);
			GL11.glScaled(1 + d / hx, 1 + d / hy, 1 + d / hz);
			GL11.glTranslated(0, -cy, 0);
			drawBlade(list);
			GL11.glPopMatrix();
		}

		GL11.glPopAttrib();
	}

	/** The blade, from its display list or -1 for the static model. */
	private static void drawBlade(int list) {
		if(list < 0) ResourceManager.crucible.renderPart("Blade");
		else GL11.glCallList(list);
	}

	/** A slightly bigger blade that warps the picture behind it, which is what makes the glow boil. */
	private static void renderHeat(int list, double cy, double sx, double sy, double sz) {
		Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.crucible_blade);
		if(!HeatDistortion.begin(0.5F)) return;

		GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glDepthMask(false);
		GL11.glPushMatrix();
		GL11.glTranslated(0, cy, 0);
		GL11.glScaled(sx, sy, sz);
		GL11.glTranslated(0, -cy, 0);
		drawBlade(list);
		GL11.glPopMatrix();
		GL11.glPopAttrib();

		HeatDistortion.end();
	}

	private static void renderFirstPerson(EntityPlayer player, ItemStack item, boolean isOn) {
		Minecraft mc = Minecraft.getMinecraft();
		ItemRenderer itemRenderer = mc.entityRenderer.itemRenderer;

		// stops the vanilla swing, and the vanilla lowering and raising of the held item, the rig animates all of that itself
		player.isSwingInProgress = false;

		float prevEq = ReflectionHelper.getPrivateValue(ItemRenderer.class, itemRenderer, "prevEquippedProgress", "field_78451_d");
		float eq = ReflectionHelper.getPrivateValue(ItemRenderer.class, itemRenderer, "equippedProgress", "field_78454_c");

		if(eq < prevEq) {
			ReflectionHelper.setPrivateValue(ItemRenderer.class, itemRenderer, 0.0F, "prevEquippedProgress", "field_78451_d");
			ReflectionHelper.setPrivateValue(ItemRenderer.class, itemRenderer, 0.0F, "equippedProgress", "field_78454_c");
		} else if(eq > prevEq) {
			ReflectionHelper.setPrivateValue(ItemRenderer.class, itemRenderer, 1.0F, "prevEquippedProgress", "field_78451_d");
			ReflectionHelper.setPrivateValue(ItemRenderer.class, itemRenderer, 1.0F, "equippedProgress", "field_78454_c");
		}

		long now = System.currentTimeMillis();
		BlenderAnimation rig = getRig(player, item);

		if(rig == null) {
			// the equip animation comes from the server, a few frames of nothing are better than the finished sword flashing up before it starts over
			if(now - lastMissingFrame > 250L) missingSince = now;
			lastMissingFrame = now;
			if(now - missingSince < RIG_GRACE) return;
			rig = startRig(player, item, false);
		}

		AnimationWrapper wrapper = rig.wrapper;
		
		GL11.glTranslated(0.9375, 0.0625, 0);
		GL11.glRotated(-335, 0, 0, 1);
		GL11.glRotated(-50, 0, 1, 0);
		GL11.glScaled(1D / 1.5D, 1D / 1.5D, 1D / 1.5D);
		GL11.glTranslated(0, 0.3, 0);
		GL11.glScaled(2.5, 2.5, 2.5);
		GL11.glRotated(-45, 0, 1, 0);
		// display transform of item/generated for the right hand in first person
		GL11.glTranslated(1.13D / 16D, 3.2D / 16D, 1.13D / 16D);
		GL11.glRotated(-90, 0, 1, 0);
		GL11.glRotated(25, 0, 0, 1);
		GL11.glScaled(0.68, 0.68, 0.68);
		// the item renderer centers the model
		GL11.glTranslated(-0.5, -0.5, -0.5);
		// the rig itself
		GL11.glScaled(5, 5, 5);
		GL11.glTranslated(0.2, -1.5, 0.5);
		GL11.glRotated(-90, 0, 1, 0);
		GL11.glRotated(-20, 1, 0, 0);
		GL11.glRotated(5, 0, 0, 1);

		// a cut line is being drawn or a cut is being swung: roll everything around the hand until the blade follows the line
		Animation anim = HbmAnimations.getRelevantAnim(RIG_RAIL);
		boolean cutting = ItemCrucible.startPos != null && (ItemCrucible.clicked || (anim != null && anim.animation != null && anim.animation.getBus("CUT_SWING") != null));

		if(cutting) {
			double[] swing = HbmAnimations.getRelevantTransformation("CUT_SWING");
			// where the start of the line is relative to the crosshair, in view space
			double yawRad = Math.toRadians(player.rotationYaw + 180D);
			double pitchRad = Math.toRadians(-player.rotationPitch);
			double sx = ItemCrucible.startPos.x, sy = ItemCrucible.startPos.y, sz = ItemCrucible.startPos.z;
			double x1 = sx * Math.cos(yawRad) + sz * Math.sin(yawRad);
			double z1 = sz * Math.cos(yawRad) - sx * Math.sin(yawRad);
			double y1 = sy * Math.cos(pitchRad) + z1 * Math.sin(pitchRad);
			double angle = Math.toDegrees(Math.atan2(y1, x1)) - 80D;
			boolean released = false;

			if(anim != null && anim.animation != null) {
				// the line is done, hold the last angle and let go of it again shortly into the swing
				angle = ItemCrucible.prevAngle;
				long time = now - anim.startMillis;
				if(anim.animation.getDuration() - time < 400) released = true;
			} else {
				ItemCrucible.prevAngle = angle;
			}

			if(!released) {
				// the wrist, which the roll turns around
				double pivotX = 0.4D, pivotY = -1.55D, pivotZ = 0D;
				GL11.glTranslated(0.3, -0.1, 0);
				GL11.glTranslated(-pivotX, -pivotY, -pivotZ);
				GL11.glRotated(-angle, 0, 0, 1);
				GL11.glTranslated(pivotX, pivotY, pivotZ);
				GL11.glTranslated(0, -0.2, 0);
				GL11.glRotated(10, 0, 1, 0);
			}

			GL11.glRotated(swing[0], 1, 0, 0);
		}

		if(!isOn) {
			// out of charge: held where the rig is still folded, and a bit lower
			GL11.glTranslated(-0.1, -0.25, 0.1);
			wrapper.startTime = now - DRAINED_TIME;
		}

		double[] sRot = HbmAnimations.getRelevantTransformation("SWING_ROT");
		double[] sTrans = HbmAnimations.getRelevantTransformation("SWING_TRANS");
		GL11.glTranslated(sTrans[0], sTrans[1], sTrans[2]);
		GL11.glRotated(sRot[0], 1, 0, 0);
		GL11.glRotated(sRot[2], 0, 0, 1);
		GL11.glRotated(sRot[1], 0, 1, 0);

		ResourceManager.crucible_anim.controller.setAnim(wrapper);
		ResourceManager.crucible_anim.renderAnimated(now, CALLBACK);
	}

	/** The rig animation of the held crucible, null if the equip animation hasn't arrived yet. */
	public static BlenderAnimation getRig(EntityPlayer player, ItemStack stack) {
		Animation anim = HbmAnimations.hotbar[hotbarSlot(player)][RIG_RAIL];
		if(anim instanceof BlenderAnimation && anim.key.equals(stack.getItem().getUnlocalizedName())) return (BlenderAnimation) anim;
		return null;
	}

	/** Puts a rig into the held slot, either at the start of its equip animation or already finished. */
	private static BlenderAnimation startRig(EntityPlayer player, ItemStack stack, boolean playEquip) {
		long start = System.currentTimeMillis();
		if(!playEquip) start -= EQUIP_TIME + 100;
		BlenderAnimation rig = new BlenderAnimation(stack.getItem().getUnlocalizedName(), start, 1F, ResourceManager.crucible_equip, new EndResult(EndType.STAY));
		HbmAnimations.hotbar[hotbarSlot(player)][RIG_RAIL] = rig;
		return rig;
	}

	/** Starts the deploy animation of the rig. */
	public static void startEquip(EntityPlayer player, ItemStack stack) {
		startRig(player, stack, true);
	}

	/** Plays a bus animation on top of the rig, the rig's own animation keeps going underneath. */
	public static void layerAnimation(EntityPlayer player, ItemStack stack, BusAnimation bus) {
		BlenderAnimation rig = getRig(player, stack);
		if(rig == null) rig = startRig(player, stack, false);
		rig.animation = bus;
		rig.startMillis = System.currentTimeMillis();
	}

	private static int hotbarSlot(EntityPlayer player) {
		int slot = player.inventory.currentItem;
		if(slot < 0 || slot > 8) slot = Math.abs(slot) % 9;
		return slot;
	}

	/** Textures the parts of the rig and lets the blade grow out of the hilt while the equip animation plays. */
	private static class RigCallback implements IAnimatedModelCallback {

		private float lastBrightnessX;
		private float lastBrightnessY;

		@Override
		public boolean onRender(int prevFrame, int currentFrame, int model, float diffN, String name) {
			Minecraft mc = Minecraft.getMinecraft();

			if(name.startsWith("Guard")) {
				mc.renderEngine.bindTexture(ResourceManager.crucible_guard);

			} else if(name.equals("Hilt")) {
				mc.renderEngine.bindTexture(ResourceManager.crucible_hilt);

			} else if(name.equals("Blade")) {
				GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TRANSFORM_BIT);
				lastBrightnessX = OpenGlHelper.lastBrightnessX;
				lastBrightnessY = OpenGlHelper.lastBrightnessY;

				// the plane moves along the blade with the animation, everything behind it is cut off
				CLIP_PLANE.clear();
				CLIP_PLANE.put(0D).put(0D).put(1D).put(BobMathUtil.remap(diffN, 0.6F, 0.7F, -0.7F, 0.7F));
				CLIP_PLANE.rewind();
				GL11.glClipPlane(GL11.GL_CLIP_PLANE0, CLIP_PLANE);
				GL11.glEnable(GL11.GL_CLIP_PLANE0);

				GL11.glEnable(GL11.GL_BLEND);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GL11.glDisable(GL11.GL_LIGHTING);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
				GL11.glColor4f(1F, 1F, 1F, 0.7F);
				mc.renderEngine.bindTexture(ResourceManager.crucible_blade);

			} else {
				// everything else is an arm
				mc.renderEngine.bindTexture(mc.thePlayer.getLocationSkin());
			}

			return false;
		}

		@Override
		public void postRender(int prevFrame, int currentFrame, int model, float diffN, String name) {
			if(name.equals("Blade")) {
				renderHalo(model, 0D, 0.01D, 0.293D, 0.648D, 0.094D);
				if(diffN > 0.6F) renderHeat(model, 0D, 1.15D, 1.15D, 1.05D);
				GL11.glDisable(GL11.GL_CLIP_PLANE0);
				GL11.glPopAttrib();
				GL11.glColor4f(1F, 1F, 1F, 1F);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY);
			}
		}
	}
}
