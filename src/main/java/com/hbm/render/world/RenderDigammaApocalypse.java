package com.hbm.render.world;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;

import com.hbm.lib.RefStrings;
import com.hbm.render.shader.Shader;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

@SideOnly(Side.CLIENT)
public class RenderDigammaApocalypse {

	public static final int FLASH_WHITE = 0;
	public static final int FLASH_INVERT = 1;
	public static final int FLASH_RED = 2;
	public static final int FLASH_VIOLET = 3;
	public static final int FLASH_BLACK = 4;

	private static final double SKY_DISTANCE = 20D;

	private static final int RAY_COUNT = 28;
	private static final int RAY_SEGMENTS = 3;
	private static final int GLITCH_STRIPS = 24;
	private static final int VIGNETTE_SEGMENTS = 32;
	private static final float TWO_PI = (float) (Math.PI * 2D);
	private static final int BOLT_SEGMENTS = 10;
	private static final int SHELL_SLICES = 32;
	private static final int SHELL_STACKS = 16;

	private static final float[] SHELL_COS_THETA = new float[SHELL_SLICES + 1];
	private static final float[] SHELL_SIN_THETA = new float[SHELL_SLICES + 1];
	private static final float[] SHELL_COS_PHI = new float[SHELL_STACKS + 1];
	private static final float[] SHELL_SIN_PHI = new float[SHELL_STACKS + 1];

	static {
		for(int i = 0; i <= SHELL_SLICES; i++) {
			double theta = i * Math.PI * 2D / SHELL_SLICES;
			SHELL_COS_THETA[i] = (float) Math.cos(theta);
			SHELL_SIN_THETA[i] = (float) Math.sin(theta);
		}
		for(int j = 0; j <= SHELL_STACKS; j++) {
			double phi = j * Math.PI / SHELL_STACKS;
			SHELL_COS_PHI[j] = (float) Math.cos(phi);
			SHELL_SIN_PHI[j] = (float) Math.sin(phi);
		}
	}

	private static Shader singularityShader;
	private static Shader inkShader;

	private static int sceneTexture = 0;
	private static int sceneTexWidth;
	private static int sceneTexHeight;
	private static int depthTexture = 0;
	private static int depthTexWidth;
	private static int depthTexHeight;

	private static final IntBuffer viewport = BufferUtils.createIntBuffer(16);
	private static final FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
	private static final FloatBuffer viewBuf = BufferUtils.createFloatBuffer(16);
	private static final FloatBuffer invBuf = BufferUtils.createFloatBuffer(16);
	private static final Matrix4f projMatrix = new Matrix4f();
	private static final Matrix4f viewMatrix = new Matrix4f();
	private static final Matrix4f viewProjMatrix = new Matrix4f();
	private static final Matrix4f invViewProjMatrix = new Matrix4f();

	// viewport of the screen pass that is currently running
	private static int passX, passY, passWidth, passHeight, passPreviousTexture;

	// camera-facing basis around the singularity direction, reused every frame
	private static double dirX, dirY, dirZ;
	private static double rightX, rightY, rightZ;
	private static double upX, upY, upZ;

	private static boolean wasTexture, wasLighting, wasFog, wasCull, wasBlend, wasAlpha, wasDepth, wasDepthMask, wasLightmap;
	private static int wasBlendSrcRgb, wasBlendDstRgb, wasBlendSrcAlpha, wasBlendDstAlpha, wasDepthFunc, wasActiveTexture;

	// ================= 3D =================

	/** Draws the light rays and, if asked, the raymarched singularity, on a small sphere around the camera so they work at any distance. */
	public static void drawSky(double dx, double dy, double dz, float halfAngle, float rayStrength, float time, boolean singularity) {

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if(length < 0.0001D)
			return;

		setBasis(dx / length, dy / length, dz / length);
		saveState();

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glDepthRange(1D, 1D); // only passes where nothing has been drawn, i.e. open sky

		if(rayStrength > 0F) {
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			drawRays(halfAngle, rayStrength, time);
		}

		if(singularity) {
			drawSingularity(halfAngle, time);
		}

		GL11.glDepthRange(0D, 1D);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		restoreState();
	}

	private static void setBasis(double x, double y, double z) {

		dirX = x;
		dirY = y;
		dirZ = z;

		// right = normalize(dir x worldUp)
		double flat = Math.sqrt(x * x + z * z);
		if(flat < 0.0001D) {
			rightX = 1D;
			rightY = 0D;
			rightZ = 0D;
		} else {
			rightX = -z / flat;
			rightY = 0D;
			rightZ = x / flat;
		}

		// up = right x dir
		upX = rightY * z - rightZ * y;
		upY = rightZ * x - rightX * z;
		upZ = rightX * y - rightY * x;
	}

	private static void drawSingularity(float halfAngle, float time) {

		Shader shader = getSingularityShader();
		if(!shader.isLoaded())
			return;

		double half = Math.tan(halfAngle);

		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1F, 1F, 1F, 1F);

		shader.use();
		shader.setUniform1f("iTime", time);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		spriteVertex(tess, -half, -half, 0D, 0D);
		spriteVertex(tess, half, -half, 1D, 0D);
		spriteVertex(tess, half, half, 1D, 1D);
		spriteVertex(tess, -half, half, 0D, 1D);
		tess.draw();

		shader.stop();
	}

	private static Shader getSingularityShader() {
		if(singularityShader == null) {
			singularityShader = new Shader(
					new ResourceLocation(RefStrings.MODID, "shaders/blackhole_digamma.vert"),
					new ResourceLocation(RefStrings.MODID, "shaders/blackhole_digamma.frag"));
		}
		return singularityShader;
	}

	private static void spriteVertex(Tessellator tess, double right, double up, double u, double v) {
		tess.addVertexWithUV(
				(dirX + rightX * right + upX * up) * SKY_DISTANCE,
				(dirY + rightY * right + upY * up) * SKY_DISTANCE,
				(dirZ + rightZ * right + upZ * up) * SKY_DISTANCE, u, v);
	}

	private static void drawRays(float halfAngle, float strength, float time) {

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();

		for(int i = 0; i < RAY_COUNT; i++) {

			float h1 = hash(i * 3 + 1);
			float h2 = hash(i * 3 + 2);
			float h3 = hash(i * 3 + 3);

			double angle = (i + h1 * 0.6D) * (Math.PI * 2D / RAY_COUNT) + time * (h2 - 0.5D) * 0.15D;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);

			double reach = Math.min(1.45D, halfAngle * (1.2D + 3.5D * h2) * (0.4D + 0.6D * strength));
			double width = 0.012D + 0.03D * h3;
			float flicker = 0.75F + 0.25F * (float) Math.sin(time * (3F + h1 * 6F) + i);

			for(int k = 0; k < RAY_SEGMENTS; k++) {

				float f0 = k / (float) RAY_SEGMENTS;
				float f1 = (k + 1) / (float) RAY_SEGMENTS;

				double t0 = Math.tan(reach * f0);
				double t1 = Math.tan(reach * f1);
				double w0 = width * (1D - f0 * 0.7D);
				double w1 = width * (1D - f1 * 0.7D);

				float a0 = strength * flicker * (float) Math.pow(1F - f0, 1.5D);
				float a1 = strength * flicker * (float) Math.pow(1F - f1, 1.5D);

				rayVertex(tess, t0 * cos - sin * w0, t0 * sin + cos * w0, f0, a0);
				rayVertex(tess, t0 * cos + sin * w0, t0 * sin - cos * w0, f0, a0);
				rayVertex(tess, t1 * cos + sin * w1, t1 * sin - cos * w1, f1, a1);
				rayVertex(tess, t1 * cos - sin * w1, t1 * sin + cos * w1, f1, a1);
			}
		}

		tess.draw();
	}

	/** Hot white at the source, red at the tip. */
	private static void rayVertex(Tessellator tess, double right, double up, float fade, float alpha) {

		double x = dirX + rightX * right + upX * up;
		double y = dirY + rightY * right + upY * up;
		double z = dirZ + rightZ * right + upZ * up;
		double scale = SKY_DISTANCE / Math.sqrt(x * x + y * y + z * z);

		tess.setColorRGBA_F(1F, 0.85F - 0.7F * fade, 0.7F - 0.65F * fade, alpha);
		tess.addVertex(x * scale, y * scale, z * scale);
	}

	/** Expanding shell of light centered on (cx, cy, cz), which is relative to the camera. */
	public static void drawShell(double cx, double cy, double cz, double radius, float alpha) {

		if(radius < 1D || alpha <= 0F)
			return;

		saveState();

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();

		for(int j = 0; j < SHELL_STACKS; j++) {

			float sin0 = SHELL_SIN_PHI[j];
			float sin1 = SHELL_SIN_PHI[j + 1];
			float cos0 = SHELL_COS_PHI[j];
			float cos1 = SHELL_COS_PHI[j + 1];
			float a0 = alpha * band(sin0);
			float a1 = alpha * band(sin1);

			for(int i = 0; i < SHELL_SLICES; i++) {
				shellVertex(tess, cx, cy, cz, radius, i, sin0, cos0, a0);
				shellVertex(tess, cx, cy, cz, radius, i + 1, sin0, cos0, a0);
				shellVertex(tess, cx, cy, cz, radius, i + 1, sin1, cos1, a1);
				shellVertex(tess, cx, cy, cz, radius, i, sin1, cos1, a1);
			}
		}

		tess.draw();

		GL11.glColor4f(1F, 1F, 1F, 1F);
		restoreState();
	}

	/** Brightest around the equator so the shell reads as a ring sweeping across the land. */
	private static float band(float sinPhi) {
		float s2 = sinPhi * sinPhi;
		return 0.12F + 0.88F * s2 * s2;
	}

	private static void shellVertex(Tessellator tess, double cx, double cy, double cz, double radius, int slice, float sinPhi, float cosPhi, float alpha) {
		tess.setColorRGBA_F(1F, 0.7F, 0.55F, alpha);
		tess.addVertex(
				cx + radius * sinPhi * SHELL_COS_THETA[slice],
				cy + radius * cosPhi,
				cz + radius * sinPhi * SHELL_SIN_THETA[slice]);
	}

	// ================= 2D overlay =================

	private static boolean beginScenePass() {

		viewport.clear();
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
		passX = viewport.get(0);
		passY = viewport.get(1);
		passWidth = viewport.get(2);
		passHeight = viewport.get(3);
		if(passWidth <= 0 || passHeight <= 0)
			return false;

		saveState();

		passPreviousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

		if(sceneTexture == 0) {
			sceneTexture = GL11.glGenTextures();
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);

		if(passWidth != sceneTexWidth || passHeight != sceneTexHeight) {
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, passWidth, passHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
			sceneTexWidth = passWidth;
			sceneTexHeight = passHeight;
		}

		GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, passX, passY, passWidth, passHeight);

		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glOrtho(0D, passWidth, passHeight, 0D, -1D, 1D);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		return true;
	}

	private static void endScenePass() {
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, passPreviousTexture);
		restoreState();
	}


	public static void drawGlitch(float intensity, int step, boolean slices) {

		if(intensity <= 0F || !beginScenePass())
			return;

		// the slices shift sideways past the screen edge and should wrap around
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

		float split = 0.006F * intensity;
		Tessellator tess = Tessellator.instance;

		// one pass per color channel, each shifted a little, so only that channel is overwritten
		for(int channel = 0; channel < 3; channel++) {

			GL11.glColorMask(channel == 0, channel == 1, channel == 2, false);
			tess.startDrawingQuads();

			for(int strip = 0; strip < GLITCH_STRIPS; strip++) {

				float shift = (channel - 1) * split;
				if(slices && hash(step * 97 + strip) < intensity * 0.45F) {
					shift += (hash(step * 193 + strip * 7 + 5) - 0.5F) * 0.3F * intensity;
				}

				float y0 = passHeight * strip / (float) GLITCH_STRIPS;
				float y1 = passHeight * (strip + 1) / (float) GLITCH_STRIPS;
				float v0 = 1F - strip / (float) GLITCH_STRIPS;
				float v1 = 1F - (strip + 1) / (float) GLITCH_STRIPS;

				tess.addVertexWithUV(0, y1, 0, shift, v1);
				tess.addVertexWithUV(passWidth, y1, 0, shift + 1F, v1);
				tess.addVertexWithUV(passWidth, y0, 0, shift + 1F, v0);
				tess.addVertexWithUV(0, y0, 0, shift, v0);
			}

			tess.draw();
		}

		GL11.glColorMask(true, true, true, true);
		endScenePass();
	}

	public static void drawInk(double camRelX, double camRelY, double camRelZ, float time, float ink, float wave, float shock, boolean rumble) {

		if(ink <= 0F && wave <= 0F && shock < 0F)
			return;

		Shader shader = getInkShader();
		if(!shader.isLoaded())
			return;

		projBuf.clear();
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projBuf);
		viewBuf.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, viewBuf);
		projMatrix.load(projBuf);
		viewMatrix.load(viewBuf);
		Matrix4f.mul(projMatrix, viewMatrix, viewProjMatrix);
		if(Matrix4f.invert(viewProjMatrix, invViewProjMatrix) == null)
			return;
		invBuf.clear();
		invViewProjMatrix.store(invBuf);
		invBuf.flip();

		if(!beginScenePass())
			return;

		// the depth copy goes on unit 2 so the lightmap unit is left alone
		GL13.glActiveTexture(GL13.GL_TEXTURE2);
		int previousDepthBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

		if(depthTexture == 0) {
			depthTexture = GL11.glGenTextures();
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);

		if(passWidth != depthTexWidth || passHeight != depthTexHeight) {
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, passWidth, passHeight, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
			depthTexWidth = passWidth;
			depthTexHeight = passHeight;
		}

		GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, passX, passY, passWidth, passHeight);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);

		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

		shader.use();
		shader.setUniform1i("uScene", 0);
		shader.setUniform1i("uDepth", 2);
		GL20.glUniformMatrix4(shader.getUniformLocation("uInvViewProj"), false, invBuf);
		GL20.glUniform3f(shader.getUniformLocation("uCamRel"), (float) camRelX, (float) camRelY, (float) camRelZ);
		shader.setUniform1f("uTime", time);
		shader.setUniform1f("uInk", ink);
		shader.setUniform1f("uWave", wave);
		shader.setUniform1f("uShock", shock);
		shader.setUniform1f("uRumble", rumble ? 1F : 0F);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.addVertexWithUV(0, passHeight, 0, 0, 0);
		tess.addVertexWithUV(passWidth, passHeight, 0, 1, 0);
		tess.addVertexWithUV(passWidth, 0, 0, 1, 1);
		tess.addVertexWithUV(0, 0, 0, 0, 1);
		tess.draw();

		shader.stop();

		GL13.glActiveTexture(GL13.GL_TEXTURE2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousDepthBinding);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);

		endScenePass();
	}

	private static Shader getInkShader() {
		if(inkShader == null) {
			inkShader = new Shader(new ResourceLocation(RefStrings.MODID, "shaders/digamma_ink.frag"));
		}
		return inkShader;
	}

	/** Jagged red lightning; both ends are relative to the camera. */
	public static void drawBolt(double sx, double sy, double sz, double ex, double ey, double ez, int seed, float alpha) {

		if(alpha <= 0F)
			return;

		saveState();

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();

		// wide red glow first, then a thin hot core on top
		for(int layer = 0; layer < 2; layer++) {

			float width = layer == 0 ? 1F : 0.35F;
			float green = layer == 0 ? 0.08F : 0.75F;
			float blue = layer == 0 ? 0.04F : 0.65F;
			float a = alpha * (layer == 0 ? 0.55F : 1F);

			double px = sx, py = sy, pz = sz;

			for(int i = 1; i <= BOLT_SEGMENTS; i++) {

				double f = i / (double) BOLT_SEGMENTS;
				double jitter = i == BOLT_SEGMENTS ? 0D : 14D * Math.sin(Math.PI * f);
				double cx = sx + (ex - sx) * f + (hash(seed * 31 + i * 2) - 0.5D) * jitter;
				double cy = sy + (ey - sy) * f;
				double cz = sz + (ez - sz) * f + (hash(seed * 31 + i * 2 + 1) - 0.5D) * jitter;

				boltQuad(tess, px, py, pz, cx, cy, cz, width, green, blue, a);

				px = cx;
				py = cy;
				pz = cz;
			}
		}

		tess.draw();

		GL11.glColor4f(1F, 1F, 1F, 1F);
		restoreState();
	}

	/** A segment widened sideways relative to the camera, and wider the further away it is so far bolts stay visible. */
	private static void boltQuad(Tessellator tess, double px, double py, double pz, double cx, double cy, double cz, float width, float green, float blue, float alpha) {

		double dx = cx - px, dy = cy - py, dz = cz - pz;
		double mx = (px + cx) * 0.5D, my = (py + cy) * 0.5D, mz = (pz + cz) * 0.5D;

		double nx = dy * mz - dz * my;
		double ny = dz * mx - dx * mz;
		double nz = dx * my - dy * mx;
		double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if(length < 0.000001D)
			return;

		double half = (0.2D + 0.005D * Math.sqrt(mx * mx + my * my + mz * mz)) * width / length;
		nx *= half;
		ny *= half;
		nz *= half;

		tess.setColorRGBA_F(1F, green, blue, alpha);
		tess.addVertex(px + nx, py + ny, pz + nz);
		tess.addVertex(px - nx, py - ny, pz - nz);
		tess.addVertex(cx - nx, cy - ny, cz - nz);
		tess.addVertex(cx + nx, cy + ny, cz + nz);
	}

	/** Black darkening, crimson wash and a vignette. */
	public static void drawTint(int w, int h, float dark, float red, float vignette) {

		if(dark <= 0F && red <= 0F && vignette <= 0F)
			return;

		saveState();
		setup2D();
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		if(dark > 0F) {
			GL11.glColor4f(0F, 0F, 0F, dark);
			fullscreenQuad(w, h);
		}

		if(red > 0F) {
			GL11.glColor4f(0.55F, 0.02F, 0.04F, red);
			fullscreenQuad(w, h);
		}

		if(vignette > 0F) {
			GL11.glColor4f(1F, 1F, 1F, 1F);
			drawVignette(w, h, vignette);
		}

		restoreState();
	}

	/** One continuous ring: transparent inside, opaque past the corners. Separate edge strips would overlap and double up in the corners. */
	private static void drawVignette(int w, int h, float alpha) {

		float cx = w * 0.5F;
		float cy = h * 0.5F;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();

		for(int i = 0; i < VIGNETTE_SEGMENTS; i++) {

			float a0 = i * (TWO_PI / VIGNETTE_SEGMENTS);
			float a1 = (i + 1) * (TWO_PI / VIGNETTE_SEGMENTS);
			float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
			float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

			vignetteVertex(tess, cx + cos0 * cx * 0.6F, cy + sin0 * cy * 0.6F, 0F);
			vignetteVertex(tess, cx + cos0 * cx * 1.5F, cy + sin0 * cy * 1.5F, alpha);
			vignetteVertex(tess, cx + cos1 * cx * 1.5F, cy + sin1 * cy * 1.5F, alpha);
			vignetteVertex(tess, cx + cos1 * cx * 0.6F, cy + sin1 * cy * 0.6F, 0F);
		}

		tess.draw();
	}

	private static void vignetteVertex(Tessellator tess, float x, float y, float alpha) {
		tess.setColorRGBA_F(0.12F, 0F, 0.02F, alpha);
		tess.addVertex(x, y, 0D);
	}

	/** Fullscreen flash; FLASH_INVERT flips the colors of whatever is already on screen and ignores alpha. */
	public static void drawFlash(int w, int h, int style, float alpha) {

		if(alpha <= 0F)
			return;

		saveState();
		setup2D();

		switch(style) {
		case FLASH_INVERT:
			GL11.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ZERO);
			GL11.glColor4f(1F, 1F, 1F, 1F);
			break;
		case FLASH_RED:
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glColor4f(1F, 0.08F, 0.04F, alpha);
			break;
		case FLASH_VIOLET:
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glColor4f(0.55F, 0.1F, 1F, alpha);
			break;
		case FLASH_BLACK:
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glColor4f(0F, 0F, 0F, alpha);
			break;
		default:
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glColor4f(1F, 1F, 1F, alpha);
			break;
		}

		fullscreenQuad(w, h);

		GL11.glColor4f(1F, 1F, 1F, 1F);
		restoreState();
	}

	private static void setup2D() {
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_FOG);
	}

	private static void fullscreenQuad(int w, int h) {
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.addVertex(0, h, 0);
		tess.addVertex(w, h, 0);
		tess.addVertex(w, 0, 0);
		tess.addVertex(0, 0, 0);
		tess.draw();
	}

	/** Frees the screen-copy textures once the event is over. */
	public static void release() {
		if(sceneTexture != 0) {
			GL11.glDeleteTextures(sceneTexture);
			sceneTexture = 0;
			sceneTexWidth = 0;
			sceneTexHeight = 0;
		}
		if(depthTexture != 0) {
			GL11.glDeleteTextures(depthTexture);
			depthTexture = 0;
			depthTexWidth = 0;
			depthTexHeight = 0;
		}
	}

	// ================= helpers =================

	private static void saveState() {
		wasTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
		wasLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
		wasFog = GL11.glIsEnabled(GL11.GL_FOG);
		wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
		wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
		wasAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
		wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		wasDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		wasDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		wasBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
		wasBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		wasBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
		wasBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

		// a lit lightmap unit would multiply everything drawn here by the light texture
		wasActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		wasLightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
	}

	private static void restoreState() {
		toggle(GL11.GL_TEXTURE_2D, wasTexture);
		toggle(GL11.GL_LIGHTING, wasLighting);
		toggle(GL11.GL_FOG, wasFog);
		toggle(GL11.GL_CULL_FACE, wasCull);
		toggle(GL11.GL_BLEND, wasBlend);
		toggle(GL11.GL_ALPHA_TEST, wasAlpha);
		toggle(GL11.GL_DEPTH_TEST, wasDepth);
		GL11.glDepthMask(wasDepthMask);
		GL11.glDepthFunc(wasDepthFunc);
		GL14.glBlendFuncSeparate(wasBlendSrcRgb, wasBlendDstRgb, wasBlendSrcAlpha, wasBlendDstAlpha);

		OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		toggle(GL11.GL_TEXTURE_2D, wasLightmap);
		GL13.glActiveTexture(wasActiveTexture);
	}

	private static void toggle(int capability, boolean enabled) {
		if(enabled) {
			GL11.glEnable(capability);
		} else {
			GL11.glDisable(capability);
		}
	}

	/** Deterministic noise in [0, 1) so glitches and rays don't need per-frame random objects. */
	public static float hash(int n) {
		n = (n ^ 61) ^ (n >>> 16);
		n *= 9;
		n = n ^ (n >>> 4);
		n *= 0x27d4eb2d;
		n = n ^ (n >>> 15);
		return (n & 0xFFFFFF) / (float) 0x1000000;
	}
}
