package com.hbm.render.item;

import org.lwjgl.opengl.GL11;

import com.hbm.animloader.AnimationWrapper;
import com.hbm.main.ResourceManager;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

public class ItemRenderJetpackGlider extends ItemRenderBase {
	
	private static final double CENTER_X = -0.115;
	private static final double CENTER_Y = 2.075;
	private static final double CENTER_Z = 3.532;
	
	private static final double SCALE_INVENTORY = 1.4;
	private static final double SCALE_FIRST_PERSON = 0.5;
	private static final double SCALE_THIRD_PERSON = 0.4;
	private static final double SCALE_GROUND = 0.33;

	private double nonInvScale = 1;

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		switch(type) {
		case EQUIPPED_FIRST_PERSON: nonInvScale = SCALE_FIRST_PERSON; break;
		case EQUIPPED: nonInvScale = SCALE_THIRD_PERSON; break;
		default: nonInvScale = SCALE_GROUND; break;
		}
		super.renderItem(type, item, data);
	}

	@Override
	public void renderInventory() {
		GL11.glScaled(SCALE_INVENTORY, SCALE_INVENTORY, SCALE_INVENTORY);
		GL11.glTranslated(-CENTER_X, -CENTER_Y, -CENTER_Z);
	}

	@Override
	public void renderNonInv() {
		GL11.glScaled(nonInvScale, nonInvScale, nonInvScale);
		GL11.glTranslated(-CENTER_X, -CENTER_Y, -CENTER_Z);
	}

	@Override
	public void renderCommon() {
		Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.jetpack_tex);
		GL11.glShadeModel(GL11.GL_SMOOTH);
		ResourceManager.jetpack.controller.setAnim(new AnimationWrapper(0, ResourceManager.jetpack_activate));
		ResourceManager.jetpack.renderAnimated(ResourceManager.jetpack_activate.length);
		GL11.glShadeModel(GL11.GL_FLAT);
	}
}
