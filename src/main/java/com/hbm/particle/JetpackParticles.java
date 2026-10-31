package com.hbm.particle;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.hbm.main.ResourceManager;
import com.hbm.render.shader.HeatDistortion;
import com.hbm.render.util.ColorGradient;
import com.hbm.util.BobMathUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;

@SideOnly(Side.CLIENT)
public final class JetpackParticles {

	private static final FloatBuffer MV_BUF = GLAllocation.createDirectFloatBuffer(16);
	private static final float[] MV = new float[16];
	private static final float[] COLOR = new float[4];
	private static final double[] CROSS_A = new double[3];
	private static final double[] CROSS_B = new double[3];
	private static final float TRAIL_END = 0.15F;

	private JetpackParticles() { }

	public static abstract class Base {

		public double posX, posY, posZ;
		public double prevPosX, prevPosY, prevPosZ;
		public double motionX, motionY, motionZ;
		public int age;
		public final int maxAge;
		public final float scale;

		protected Base(double x, double y, double z, float scale, int maxAge) {
			this.posX = this.prevPosX = x;
			this.posY = this.prevPosY = y;
			this.posZ = this.prevPosZ = z;
			this.scale = scale;
			this.maxAge = maxAge;
		}

		public Base motion(float x, float y, float z) {
			this.motionX = x;
			this.motionY = y;
			this.motionZ = z;
			return this;
		}

		/** @return false once the particle has expired */
		public boolean update() {
			if(++age >= maxAge) return false;
			prevPosX = posX;
			prevPosY = posY;
			prevPosZ = posZ;
			posX += motionX;
			posY += motionY;
			posZ += motionZ;
			return true;
		}

		protected float age(float partialTicks) {
			return (age + partialTicks) / (float) maxAge;
		}
	}

	public static class Plasma extends Base {

		private final ColorGradient gradient;

		public Plasma(double x, double y, double z, float scale, ColorGradient gradient) {
			super(x, y, z, scale, 5);
			this.gradient = gradient;
		}
	}

	public static class Glow extends Base {

		private static final float FADE_IN = 2F;
		private float r, g, b, a;

		public Glow(double x, double y, double z, float scale, int maxAge) {
			super(x, y, z, scale, maxAge);
		}

		public Glow color(float r, float g, float b, float a) {
			this.r = r;
			this.g = g;
			this.b = b;
			this.a = a;
			return this;
		}
	}

	public static class Haze extends Base {

		private final float heatAmount;

		public Haze(double x, double y, double z, float scale, float heatAmount, int maxAge) {
			super(x, y, z, scale, maxAge);
			this.heatAmount = heatAmount;
		}
	}

	public static class Trail {

		private final List<double[]> points = new ArrayList<double[]>();

		public void tryAddNewPos(double x, double y, double z) {
			int size = points.size();
			if(size > 0) {
				double[] last = points.get(size - 1);
				double dx = last[0] - x, dy = last[1] - y, dz = last[2] - z;
				if(dx * dx + dy * dy + dz * dz <= 0.01) {
					last[0] = x;
					last[1] = y;
					last[2] = z;
					last[3] = 0;
					return;
				}
			}
			points.add(new double[] {x, y, z, 0});
		}

		/** @return false once every point has faded out */
		public boolean update() {
			Iterator<double[]> it = points.iterator();
			while(it.hasNext()) {
				if(++it.next()[3] > 20) it.remove();
			}
			return !points.isEmpty();
		}
	}

	/** Reads the current modelview matrix; every local-space effect drawn afterwards is projected through it. */
	public static void captureMatrix() {
		MV_BUF.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MV_BUF);
		MV_BUF.get(MV);
	}

	public static void renderPlasma(List<Plasma> list, float partialTicks) {
		if(list.isEmpty()) return;
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		for(int i = 0; i < list.size(); i++) {
			Plasma p = list.get(i);
			float time = p.age(partialTicks);
			float[] c = p.gradient.getColor(time, COLOR);
			float half = (float) (0.1F * p.scale * (1 - time * time));
			t.setColorRGBA_F(c[0], c[1], c[2], c[3]);
			quad(t, p, partialTicks, half);
		}
		t.draw();
	}

	public static void renderGlow(List<Glow> list, float partialTicks) {
		if(list.isEmpty()) return;
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		for(int i = 0; i < list.size(); i++) {
			Glow p = list.get(i);
			float age = p.age(partialTicks);
			float fade = MathHelper.clamp_float(age * Glow.FADE_IN, 0, 1) * MathHelper.clamp_float(2 - age * Glow.FADE_IN + 0.1F, 0, 1);
			t.setColorRGBA_F(p.r, p.g, p.b, p.a);
			quad(t, p, partialTicks, 0.1F * p.scale * fade);
		}
		t.draw();
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	public static void renderHaze(List<Haze> list, float partialTicks) {
		if(list.isEmpty()) return;
		if(!HeatDistortion.begin(0F)) return;
		Tessellator t = Tessellator.instance;
		for(int i = 0; i < list.size(); i++) {
			Haze p = list.get(i);
			float time = p.age(partialTicks);
			float fade = MathHelper.clamp_float(1 - BobMathUtil.remap(MathHelper.clamp_float(time, 0.8F, 1F), 0.8F, 1F, 0F, 1.1F), 0, 1);
			fade *= MathHelper.clamp_float(BobMathUtil.remap(MathHelper.clamp_float(time, 0F, 0.2F), 0F, 0.2F, 0F, 1.1F), 0, 1);
			HeatDistortion.setAmount(p.heatAmount * fade * 0.15F);
			t.startDrawingQuads();
			t.setColorRGBA_F(1F, 1F, 1F, 1F);
			quad(t, p, partialTicks, 0.1F * p.scale);
			t.draw();
		}
		HeatDistortion.end();
	}

	/** Draws the trails in world space; the matrix must be the plain camera view. */
	public static void renderTrails(List<Trail> trails, double camX, double camY, double camZ) {
		if(trails.isEmpty()) return;
		Tessellator t = Tessellator.instance;
		GL11.glPushMatrix();
		GL11.glTranslated(-camX, -camY, -camZ);
		for(int i = 0; i < trails.size(); i++) {
			drawTrail(t, trails.get(i).points, camX, camY, camZ);
		}
		GL11.glPopMatrix();
	}

	private static void drawTrail(Tessellator t, List<double[]> points, double camX, double camY, double camZ) {
		int size = points.size();
		if(size < 2) return;

		t.startDrawingQuads();
		double[] prev = points.get(0);
		double[] prevCross = CROSS_A;
		double[] cross = CROSS_B;
		cross(prev, points.get(1), camX, camY, camZ, prevCross, null);
		for(int i = 1; i < size; i++) {
			double[] cur = points.get(i);
			double[] next = i < size - 1 ? points.get(i + 1) : cur;
			cross(prev, next, camX, camY, camZ, cross, prevCross);
			float a0 = trailAlpha((float) (i - 1) / (size - 1));
			float a1 = trailAlpha((float) i / (size - 1));

			t.setColorRGBA_F(0.7F, 0.7F, 0.7F, a0);
			t.addVertex(prev[0] + prevCross[0], prev[1] + prevCross[1], prev[2] + prevCross[2]);
			t.addVertex(prev[0] - prevCross[0], prev[1] - prevCross[1], prev[2] - prevCross[2]);
			t.setColorRGBA_F(0.7F, 0.7F, 0.7F, a1);
			t.addVertex(cur[0] - cross[0], cur[1] - cross[1], cur[2] - cross[2]);
			t.addVertex(cur[0] + cross[0], cur[1] + cross[1], cur[2] + cross[2]);

			prev = cur;
			double[] swap = prevCross;
			prevCross = cross;
			cross = swap;
		}
		t.draw();
	}

	private static float trailAlpha(float t) {
		return BobMathUtil.remap01_clamp(t, 0, TRAIL_END) * BobMathUtil.remap01_clamp(t, 1, 1 - TRAIL_END) * 0.7F;
	}

	/** Ribbon half-width vector: tangent x direction to the camera, scaled to the trail width. */
	private static void cross(double[] from, double[] to, double camX, double camY, double camZ, double[] out, double[] fallback) {
		double tx = to[0] - from[0], ty = to[1] - from[1], tz = to[2] - from[2];
		double vx = camX - from[0], vy = camY - from[1], vz = camZ - from[2];
		double cx = ty * vz - tz * vy;
		double cy = tz * vx - tx * vz;
		double cz = tx * vy - ty * vx;
		double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
		if(len < 1.0E-6) {
			out[0] = fallback != null ? fallback[0] : 0;
			out[1] = fallback != null ? fallback[1] : 0;
			out[2] = fallback != null ? fallback[2] : 0;
			return;
		}
		double s = 0.025 / len;
		out[0] = cx * s;
		out[1] = cy * s;
		out[2] = cz * s;
	}

	/** Camera facing quad centred on the particle; the position goes through the captured matrix, the size is in view space. */
	private static void quad(Tessellator t, Base p, float partialTicks, float half) {
		double lx = p.prevPosX + (p.posX - p.prevPosX) * partialTicks;
		double ly = p.prevPosY + (p.posY - p.prevPosY) * partialTicks;
		double lz = p.prevPosZ + (p.posZ - p.prevPosZ) * partialTicks;
		double vx = MV[0] * lx + MV[4] * ly + MV[8] * lz + MV[12];
		double vy = MV[1] * lx + MV[5] * ly + MV[9] * lz + MV[13];
		double vz = MV[2] * lx + MV[6] * ly + MV[10] * lz + MV[14];
		t.addVertexWithUV(vx + half, vy - half, vz, 0, 1);
		t.addVertexWithUV(vx + half, vy + half, vz, 0, 0);
		t.addVertexWithUV(vx - half, vy + half, vz, 1, 0);
		t.addVertexWithUV(vx - half, vy - half, vz, 1, 1);
	}

	public static void bindMask() {
		Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.fresnel_ms);
	}
}
