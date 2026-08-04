package com.hbm.main;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.hbm.lib.RefStrings;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
public class DigammaApocalypseClient {

	// TODO: JUST DELETE EVERYTHING (OR MAKE THIS BETTER)
	private static final String SOUND_CHARGE = "hbm:entity.chopperCharge";
	private static final String SOUND_BOOM = "hbm:weapon.nuclearExplosion";

	private static final int CHARGE_SOUND_REPEAT_TICKS = 60;
	private static final int FADE_IN_TICKS = 10;

	/** How opaque the background gets at its darkest, 0-1. */
	private static final float WORLD_VISIBILITY_ALPHA = 0.75F;

	private static final int FLASH_RAMP_TICKS = 20;
	private static final int SAFETY_TIMEOUT_TICKS = 20 * 30;

	/** Inside this range, the real EntityQuasar/RenderQuasar should already be visible in-world - the stylized sky marker only draws beyond it. */
	private static final double MARKER_MIN_DISTANCE = 200D;
	private static final double MARKER_RENDER_DISTANCE = 60D;
	private static final float MARKER_BASE_SIZE = 32F;   // was 4 - "make it smaller"
	private static final float MARKER_GROWTH = 64F;      // was 14

	/** How dark rendered mobs get tinted, 0 (black) - 1 (untouched). */
	private static final float MOB_DARKEN_LEVEL = 0.06F;

	private static boolean active = false;
	private static int ticksElapsed = 0;
	private static int suckTicks = 1;
	private static int chargeTicks = 1;
	private static boolean playedBoom = false;

	private static double quasarX, quasarY, quasarZ;

	private static int bgShader = 0;
	private static boolean bgShaderLoadAttempted = false;

	private static int markerShader = 0;
	private static boolean markerShaderLoadAttempted = false;

	public static void begin(int suckTicksIn, int chargeTicksIn, double qx, double qy, double qz) {
		suckTicks = Math.max(1, suckTicksIn);
		chargeTicks = Math.max(1, chargeTicksIn);
		quasarX = qx;
		quasarY = qy;
		quasarZ = qz;
		ticksElapsed = 0;
		playedBoom = false;
		active = true;
	}

	private static void reset() {
		active = false;
		ticksElapsed = 0;
		playedBoom = false;
		lastWorldTime = -1;
	}

	private static long lastWorldTime = -1;

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {

		if(event.phase != Phase.END)
			return;

		World world = Minecraft.getMinecraft().theWorld;

		if(world == null) {
			if(active)
				reset();
			return;
		}

		long currentWorldTime = world.getTotalWorldTime();
		boolean worldIsTicking = currentWorldTime != lastWorldTime;
		lastWorldTime = currentWorldTime;

		if(!active || !worldIsTicking)
			return;

		ticksElapsed++;
		int total = suckTicks + chargeTicks;

		if(Minecraft.getMinecraft().thePlayer != null) {

			if(ticksElapsed >= suckTicks && ticksElapsed < total
					&& (ticksElapsed - suckTicks) % CHARGE_SOUND_REPEAT_TICKS == 0) {
				Minecraft.getMinecraft().thePlayer.playSound(SOUND_CHARGE, 1.0F, 1.0F);
			}

			if(ticksElapsed >= total && !playedBoom) {
				playedBoom = true;
				Minecraft.getMinecraft().thePlayer.playSound(SOUND_BOOM, 1.0F, 1.0F);
			}
		}

		if(ticksElapsed >= total + SAFETY_TIMEOUT_TICKS) {
			reset();
		}
	}

	// ================= 2D background tint =================

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {

		if(!active || event.type != ElementType.ALL)
			return;

		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
		int w = res.getScaledWidth();
		int h = res.getScaledHeight();

		float whiteAmount = getWhiteAmount();
		float alpha = getAlpha();

		int program = getBgShader();

		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		if(program > 0) {
			GL20.glUseProgram(program);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "u_whiteAmount"), whiteAmount);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "u_alpha"), alpha);
			drawFullscreenQuad(w, h);
			GL20.glUseProgram(0);
		} else {
			int c = (int) (whiteAmount * 255);
			int a = (int) (alpha * 255);
			Gui.drawRect(0, 0, w, h, (a << 24) | (c << 16) | (c << 8) | c);
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();
	}

	private static void drawFullscreenQuad(int w, int h) {
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.addVertex(0, h, 0);
		tess.addVertex(w, h, 0);
		tess.addVertex(w, 0, 0);
		tess.addVertex(0, 0, 0);
		tess.draw();
	}

	// ================= mob darkening =================

	@SubscribeEvent
	public void onRenderLivingPre(RenderLivingEvent.Pre event) {
		if(active) {
			GL11.glColor4f(MOB_DARKEN_LEVEL, MOB_DARKEN_LEVEL * 0.6F, MOB_DARKEN_LEVEL * 0.6F, 1.0F);
		}
	}

	@SubscribeEvent
	public void onRenderLivingPost(RenderLivingEvent.Post event) {
		if(active) {
			GL11.glColor4f(1F, 1F, 1F, 1F);
		}
	}

	// ================= 3D: sky marker + shockwave + floating blocks =================

	@SubscribeEvent
	public void onRenderWorldLast(RenderWorldLastEvent event) {

		if(!active)
			return;

		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;
		World world = mc.theWorld;
		if(player == null || world == null)
			return;

		double camX = player.prevPosX + (player.posX - player.prevPosX) * event.partialTicks;
		double camY = player.prevPosY + (player.posY - player.prevPosY) * event.partialTicks;
		double camZ = player.prevPosZ + (player.posZ - player.prevPosZ) * event.partialTicks;
		double eyeY = camY + player.getEyeHeight();

		renderSkyMarker(player, world, camX, eyeY, camZ);
	}

	private static void renderSkyMarker(EntityPlayer player, World world, double eyeX, double eyeY, double eyeZ) {

		double dx = quasarX - eyeX;
		double dy = quasarY - eyeY;
		double dz = quasarZ - eyeZ;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if(distance <= MARKER_MIN_DISTANCE)
			return;

		double invDist = 1.0 / Math.max(distance, 0.0001);
		double dirX = dx * invDist;
		double dirY = dy * invDist;
		double dirZ = dz * invDist;

		Vec3 look = player.getLookVec();

		double forward = dirX * look.xCoord + dirY * look.yCoord + dirZ * look.zCoord;
		if(forward <= 0.05D)
			return;

		Vec3 dirVec = Vec3.createVectorHelper(dirX, dirY, dirZ);
		Vec3 worldUp = Vec3.createVectorHelper(0, 1, 0);
		Vec3 right = dirVec.crossProduct(worldUp);
		double rightLen = right.lengthVector();
		if(rightLen < 0.0001D)
			return;
		right = Vec3.createVectorHelper(right.xCoord / rightLen, right.yCoord / rightLen, right.zCoord / rightLen);
		Vec3 up = right.crossProduct(dirVec);

		int program = getMarkerShader();
		if(program <= 0)
			return;

		float progress = getProgress();
		float size = MARKER_BASE_SIZE + MARKER_GROWTH * progress;

		double cx = dirX * MARKER_RENDER_DISTANCE;
		double cy = dirY * MARKER_RENDER_DISTANCE;
		double cz = dirZ * MARKER_RENDER_DISTANCE;

		GL11.glPushMatrix();
		GL11.glTranslated(cx, cy, cz);

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDepthMask(false);

		GL20.glUseProgram(program);
		GL20.glUniform1f(GL20.glGetUniformLocation(program, "iTime"), ticksElapsed / 20F);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		addBillboardVertex(tess, right, up, -size, -size, 0, 0);
		addBillboardVertex(tess, right, up, size, -size, 1, 0);
		addBillboardVertex(tess, right, up, size, size, 1, 1);
		addBillboardVertex(tess, right, up, -size, size, 0, 1);
		tess.draw();

		GL20.glUseProgram(0);

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();
	}

	private static void addBillboardVertex(Tessellator tess, Vec3 right, Vec3 up, float rightAmount, float upAmount, double u, double v) {
		double x = right.xCoord * rightAmount + up.xCoord * upAmount;
		double y = right.yCoord * rightAmount + up.yCoord * upAmount;
		double z = right.zCoord * rightAmount + up.zCoord * upAmount;
		tess.addVertexWithUV(x, y, z, u, v);
	}

	// ================= shader loading =================

	private static int getBgShader() {
		if(!bgShaderLoadAttempted) {
			bgShaderLoadAttempted = true;
			bgShader = compileProgram("digamma");
		}
		return bgShader;
	}

	private static int getMarkerShader() {
		if(!markerShaderLoadAttempted) {
			markerShaderLoadAttempted = true;
			markerShader = compileProgram("blackhole_digamma");
		}
		return markerShader;
	}

	private static int compileProgram(String name) {

		int vert = 0;
		int frag = 0;

		try {
			String vertSrc = readShaderSource(new ResourceLocation(RefStrings.MODID, "shaders/" + name + ".vert"));
			String fragSrc = readShaderSource(new ResourceLocation(RefStrings.MODID, "shaders/" + name + ".frag"));

			vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
			GL20.glShaderSource(vert, vertSrc);
			GL20.glCompileShader(vert);
			if(GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
				System.err.println("[DigammaApocalypse] " + name + ".vert failed to compile:\n" + GL20.glGetShaderInfoLog(vert, 8192));
				return 0;
			}

			frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
			GL20.glShaderSource(frag, fragSrc);
			GL20.glCompileShader(frag);
			if(GL20.glGetShaderi(frag, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
				System.err.println("[DigammaApocalypse] " + name + ".frag failed to compile:\n" + GL20.glGetShaderInfoLog(frag, 8192));
				return 0;
			}

			int program = GL20.glCreateProgram();
			GL20.glAttachShader(program, vert);
			GL20.glAttachShader(program, frag);
			GL20.glLinkProgram(program);
			if(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
				System.err.println("[DigammaApocalypse] " + name + " failed to link:\n" + GL20.glGetProgramInfoLog(program, 8192));
				return 0;
			}

			GL20.glDeleteShader(vert);
			GL20.glDeleteShader(frag);

			return program;

		} catch(Exception ex) {
			System.err.println("[DigammaApocalypse] couldn't load " + name + " (check assets/" + RefStrings.MODID + "/shaders/" + name + ".vert/.frag):");
			ex.printStackTrace();
			return 0;
		}
	}

	private static String readShaderSource(ResourceLocation loc) throws Exception {
		InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream();
		try {
			return IOUtils.toString(in, "UTF-8");
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	// ================= timing =================

	private static float getProgress() {
		int total = suckTicks + chargeTicks;
		return Math.min(1F, ticksElapsed / (float) Math.max(1, total));
	}

	private static float getWhiteAmount() {

		int total = suckTicks + chargeTicks;

		float whiteAmount;
		if(ticksElapsed <= suckTicks) {
			whiteAmount = 0F;
		} else {
			whiteAmount = Math.min(1F, (ticksElapsed - suckTicks) / (float) Math.max(1, chargeTicks));
		}

		if(ticksElapsed >= total - FLASH_RAMP_TICKS) {
			float flash = Math.min(1F, (ticksElapsed - (total - FLASH_RAMP_TICKS)) / (float) FLASH_RAMP_TICKS);
			whiteAmount = Math.max(whiteAmount, flash);
		}

		return whiteAmount;
	}

	private static float getAlpha() {

		int total = suckTicks + chargeTicks;

		float alpha = WORLD_VISIBILITY_ALPHA;
		if(ticksElapsed < FADE_IN_TICKS) {
			alpha = WORLD_VISIBILITY_ALPHA * (ticksElapsed / (float) FADE_IN_TICKS);
		}

		if(ticksElapsed >= total - FLASH_RAMP_TICKS) {
			float flash = Math.min(1F, (ticksElapsed - (total - FLASH_RAMP_TICKS)) / (float) FLASH_RAMP_TICKS);
			alpha = WORLD_VISIBILITY_ALPHA + (1F - WORLD_VISIBILITY_ALPHA) * flash;
		}

		return alpha;
	}
}
