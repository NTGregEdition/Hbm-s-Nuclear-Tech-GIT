package com.hbm.render.shader;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import com.hbm.lib.RefStrings;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.util.ResourceLocation;

@SideOnly(Side.CLIENT)
public class HeatDistortion {

	private static final ResourceLocation NOISE = new ResourceLocation(RefStrings.MODID, "textures/misc/noise_2.png");
	private static final IntBuffer VIEWPORT = GLAllocation.createDirectIntBuffer(16);

	private static Shader shader;
	private static int uMask, uNoise, uScene, uAmount, uTime, uSize;
	private static int sceneTexture = 0;
	private static int sceneWidth;
	private static int sceneHeight;

	public static boolean begin(float amount) {

		if(shader == null) {
			shader = new Shader(new ResourceLocation(RefStrings.MODID, "shaders/heat_distortion.vert"), new ResourceLocation(RefStrings.MODID, "shaders/heat_distortion.frag"));
			if(shader.isLoaded()) {
				uMask = shader.getUniformLocation("tex_mask");
				uNoise = shader.getUniformLocation("noise");
				uScene = shader.getUniformLocation("fbo_tex");
				uAmount = shader.getUniformLocation("amount");
				uTime = shader.getUniformLocation("time");
				uSize = shader.getUniformLocation("windowSize");
			}
		}

		if(!shader.isLoaded())
			return false;

		VIEWPORT.clear();
		GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
		int width = VIEWPORT.get(2);
		int height = VIEWPORT.get(3);
		if(width <= 0 || height <= 0)
			return false;

		// units 2 and 3, the lightmap sits on 1
		GL13.glActiveTexture(GL13.GL_TEXTURE3);
		if(sceneTexture == 0) sceneTexture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);

		if(width != sceneWidth || height != sceneHeight) {
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
			sceneWidth = width;
			sceneHeight = height;
		}

		GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, VIEWPORT.get(0), VIEWPORT.get(1), width, height);

		GL13.glActiveTexture(GL13.GL_TEXTURE2);
		Minecraft.getMinecraft().renderEngine.bindTexture(NOISE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);

		shader.use();
		shader.setUniform1i(uMask, 0);
		shader.setUniform1i(uNoise, 2);
		shader.setUniform1i(uScene, 3);
		shader.setUniform1f(uAmount, amount);
		// 100s is a whole number of noise repeats at both scroll speeds, so the wrap is seamless
		shader.setUniform1f(uTime, (System.currentTimeMillis() % 100000L) / 1000F);
		GL20.glUniform2f(uSize, width, height);
		return true;
	}

	public static void setAmount(float amount) {
		shader.setUniform1f(uAmount, amount);
	}

	public static void end() {
		shader.stop();
	}
}
