package com.hbm.particle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

//EntityFX.interpPos is only refreshed after the lit particle pass, so it lags a frame behind for particles drawn in it
@SideOnly(Side.CLIENT)
public final class ParticleCamera {

	private ParticleCamera() { }

	public static double x(float partialTicks) {
		Entity cam = Minecraft.getMinecraft().renderViewEntity;
		return cam.lastTickPosX + (cam.posX - cam.lastTickPosX) * partialTicks;
	}

	public static double y(float partialTicks) {
		Entity cam = Minecraft.getMinecraft().renderViewEntity;
		return cam.lastTickPosY + (cam.posY - cam.lastTickPosY) * partialTicks;
	}

	public static double z(float partialTicks) {
		Entity cam = Minecraft.getMinecraft().renderViewEntity;
		return cam.lastTickPosZ + (cam.posZ - cam.lastTickPosZ) * partialTicks;
	}
}
