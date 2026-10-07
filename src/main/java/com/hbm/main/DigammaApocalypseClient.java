package com.hbm.main;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import com.hbm.config.ClientConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.render.world.RenderDigammaApocalypse;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

@SideOnly(Side.CLIENT)
public class DigammaApocalypseClient {

	private static final String SOUND_RUMBLE = "hbm:misc.rumble";
	private static final String SOUND_CHARGE = "hbm:entity.chopperCharge";
	private static final String SOUND_BOOM = "hbm:weapon.nuclearExplosion";
	private static final String SOUND_THUNDER = "hbm:weapon.fire.loudestNoiseOnEarth";

	private static final int RUMBLE_REPEAT_TICKS = 280;
	private static final int CHARGE_SOUND_REPEAT_TICKS = 30;
	private static final int FLASH_RAMP_TICKS = 20;
	private static final int BLACKOUT_TICKS = 25;
	private static final int SAFETY_TIMEOUT_TICKS = 20 * 30;

	/** Base blocks per tick the shockwave travels; it also decides when the boom reaches each player. */
	private static final float SHOCK_SPEED = 25F;
	private static final int SHOCK_MIN_TRAVEL_TICKS = 3;
	/** The shockwave has to land at least this long before the server kicks everyone. */
	private static final int SHOCK_MARGIN_TICKS = 30;
	private static final int KICK_LENGTH_TICKS = 30;

	private static final double SPRITE_MIN_DISTANCE = 150D;
	private static final double RAYS_MIN_DISTANCE = 20D;

	private static final float MOB_DARKEN_LEVEL = 0.06F;

	private static final int BOLT_SLOTS = 3;
	private static final int BOLT_LIFE_TICKS = 4;

	private static volatile boolean active = false;
	private static boolean hasSeenWorld = false;
	private static long lastWorldTime = -1;

	private static int ticksElapsed = 0;
	private static int suckTicks = 1;
	private static int chargeTicks = 1;
	private static int aftermathTicks = 1;
	private static int dimensionId;
	private static CelestialBody eventBody;
	private static double quasarX, quasarY, quasarZ;

	private static int shockHitTick = -1;
	private static float shockSpeed = SHOCK_SPEED;
	private static int nextRumbleTick;
	private static int nextChargeSoundTick;
	private static int nextStrobeTick;
	private static int strobeCount;

	private static int flashStyle;
	private static int flashStartTick = -1000;
	private static int flashLength = 1;
	private static float flashPeak;

	private static int kickStartTick = -1000;
	private static float kickPower;

	private static EntityPlayer shakenPlayer;
	private static float shakeYaw, shakePitch;
	private static float appliedYaw, appliedPitch;

	public static void begin(int suckTicksIn, int chargeTicksIn, int aftermathTicksIn, int elapsedTicks, int dimensionIdIn, double qx, double qy, double qz) {
		suckTicks = Math.max(1, suckTicksIn);
		chargeTicks = Math.max(1, chargeTicksIn);
		aftermathTicks = Math.max(1, aftermathTicksIn);
		dimensionId = dimensionIdIn;
		eventBody = CelestialBody.getBodyOrNull(dimensionIdIn);
		quasarX = qx;
		quasarY = qy;
		quasarZ = qz;

		ticksElapsed = Math.max(0, elapsedTicks);
		shockHitTick = -1;
		shockSpeed = SHOCK_SPEED;
		nextRumbleTick = ticksElapsed + 1;
		nextChargeSoundTick = suckTicks;
		nextStrobeTick = Math.max(ticksElapsed + 1, suckTicks / 4);
		strobeCount = 0;
		flashStartTick = -1000;
		kickStartTick = -1000;
		hasSeenWorld = false;
		active = true;
	}

	private static void reset() {
		active = false;
		ticksElapsed = 0;
		lastWorldTime = -1;
		RenderDigammaApocalypse.release();
	}

	// ================= timeline =================

	private static float clamp01(float f) {
		return MathHelper.clamp_float(f, 0F, 1F);
	}

	private static float ease(float f) {
		return f * f * (3F - 2F * f);
	}

	private static float suckProgress(float t) {
		return clamp01(t / suckTicks);
	}

	private static float chargeProgress(float t) {
		return clamp01((t - suckTicks) / chargeTicks);
	}

	private static float aftermathProgress(float t) {
		return clamp01((t - suckTicks - chargeTicks) / aftermathTicks);
	}

	private static float buildup(float t) {
		return clamp01(t / (suckTicks + chargeTicks));
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {

		if(event.phase != Phase.END)
			return;

		Minecraft mc = Minecraft.getMinecraft();
		World world = mc.theWorld;

		if(world == null) {
			if(active && hasSeenWorld)
				reset();
			return;
		}

		hasSeenWorld = true;

		long currentWorldTime = world.getTotalWorldTime();
		boolean worldIsTicking = currentWorldTime != lastWorldTime;
		lastWorldTime = currentWorldTime;

		if(!active || !worldIsTicking)
			return;

		ticksElapsed++;
		int total = suckTicks + chargeTicks;

		EntityPlayer player = mc.thePlayer;
		if(player != null) {
			tickAudio(player, total);
			tickFlashes(player, total);
		}

		if(ticksElapsed >= total + aftermathTicks + SAFETY_TIMEOUT_TICKS) {
			reset();
		}
	}

	private static void tickAudio(EntityPlayer player, int total) {

		int t = ticksElapsed;

		if(t < total && t >= nextRumbleTick) {
			nextRumbleTick = t + RUMBLE_REPEAT_TICKS;
			player.playSound(SOUND_RUMBLE, 2F, 0.6F);
		}

		if(t >= suckTicks && t < total && t >= nextChargeSoundTick) {
			nextChargeSoundTick = t + CHARGE_SOUND_REPEAT_TICKS;
			player.playSound(SOUND_CHARGE, 1.5F, 0.5F + 1.5F * chargeProgress(t));
		}

		// light travels faster than sound: the flash is silent and the boom comes with the shockwave
		if(t == shockHitTick) {
			player.playSound(SOUND_BOOM, 4F, 1F);
			player.playSound(SOUND_THUNDER, 4F, 0.8F);
		}
	}

	private static void tickFlashes(EntityPlayer player, int total) {

		int t = ticksElapsed;
		boolean reduced = ClientConfig.DIGAMMA_REDUCED_EFFECTS.get();

		if(t >= total && shockHitTick < 0) {
			shockHitTick = computeShockHitTick(player, total);
		}

		if(t == total) {
			flash(RenderDigammaApocalypse.FLASH_WHITE, reduced ? 40 : 18, 1F);
			kick(1F);
		}

		if(t == shockHitTick) {
			if(reduced) {
				flash(RenderDigammaApocalypse.FLASH_WHITE, 30, 0.8F);
			} else {
				flash(RenderDigammaApocalypse.FLASH_INVERT, 3, 1F);
			}
			kick(1.5F);
		}

		if(t >= nextStrobeTick && t < total) {

			if(t < suckTicks) {
				flash(RenderDigammaApocalypse.FLASH_RED, reduced ? 16 : 8, 0.3F);
				nextStrobeTick = t + 70 - (int) (40F * suckProgress(t));
			} else {
				float cp = chargeProgress(t);
				int interval = reduced ? 24 : 4 + (int) (26F * (1F - cp) * (1F - cp));
				nextStrobeTick = t + interval;

				if(reduced) {
					flash(strobeCount % 2 == 0 ? RenderDigammaApocalypse.FLASH_RED : RenderDigammaApocalypse.FLASH_VIOLET, 14, 0.35F);
				} else {
					switch(strobeCount % 4) {
					case 0: flash(RenderDigammaApocalypse.FLASH_WHITE, 3, 1F); break;
					case 1: flash(RenderDigammaApocalypse.FLASH_INVERT, 2, 1F); break;
					case 2: flash(RenderDigammaApocalypse.FLASH_RED, 4, 0.6F); break;
					default: flash(RenderDigammaApocalypse.FLASH_VIOLET, 4, 0.6F); break;
					}
				}
				strobeCount++;
			}
		}
	}

	private static int computeShockHitTick(EntityPlayer player, int total) {

		int travel = 20;
		double distance = 0D;

		if(player.worldObj.provider.dimensionId == dimensionId) {
			double dx = player.posX - quasarX;
			double dy = player.posY - quasarY;
			double dz = player.posZ - quasarZ;
			distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			travel = (int) (distance / SHOCK_SPEED);
		}

		travel = MathHelper.clamp_int(travel, SHOCK_MIN_TRAVEL_TICKS, Math.max(SHOCK_MIN_TRAVEL_TICKS, aftermathTicks - SHOCK_MARGIN_TICKS));

		// past the clamp the base speed would never reach the player before the kick, so the wave speeds up to land with the boom
		shockSpeed = Math.max(SHOCK_SPEED, (float) (distance / travel));

		return total + travel;
	}

	/** Radius of the shockwave in blocks, negative before the detonation. */
	private static float shockRadius(float t) {
		int total = suckTicks + chargeTicks;
		return t >= total ? shockSpeed * (t - total) : -1F;
	}

	private static void flash(int style, int length, float peak) {
		flashStyle = style;
		flashStartTick = ticksElapsed;
		flashLength = length;
		flashPeak = peak;
	}

	private static float flashAlpha(float t) {
		float age = t - flashStartTick;
		if(age < 0F || age >= flashLength)
			return 0F;
		float left = 1F - age / flashLength;
		return flashPeak * left * left;
	}

	private static void kick(float power) {
		kickStartTick = ticksElapsed;
		kickPower = power;
	}

	// ================= camera shake =================

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {

		if(event.phase == Phase.START) {

			EntityPlayer player = Minecraft.getMinecraft().thePlayer;
			if(!active || player == null)
				return;

			computeShake(ticksElapsed + event.renderTickTime);

			appliedYaw = shakeYaw;
			appliedPitch = MathHelper.clamp_float(player.rotationPitch + shakePitch, -90F, 90F) - player.rotationPitch;

			player.rotationYaw += appliedYaw;
			player.prevRotationYaw += appliedYaw;
			player.rotationPitch += appliedPitch;
			player.prevRotationPitch += appliedPitch;
			shakenPlayer = player;

		} else if(shakenPlayer != null) {

			shakenPlayer.rotationYaw -= appliedYaw;
			shakenPlayer.prevRotationYaw -= appliedYaw;
			shakenPlayer.rotationPitch -= appliedPitch;
			shakenPlayer.prevRotationPitch -= appliedPitch;
			shakenPlayer = null;
		}
	}

	private static void computeShake(float t) {

		int total = suckTicks + chargeTicks;

		float amplitude;
		if(t < total) {
			float p = buildup(t);
			amplitude = 0.12F + 2.4F * p * p;
		} else {
			amplitude = 2.2F * (1F - 0.3F * aftermathProgress(t));
		}

		float kickAge = t - kickStartTick;
		if(kickAge >= 0F && kickAge < KICK_LENGTH_TICKS) {
			float left = 1F - kickAge / KICK_LENGTH_TICKS;
			amplitude += kickPower * 6F * left * left;
		}

		if(ClientConfig.DIGAMMA_REDUCED_EFFECTS.get()) {
			amplitude *= 0.35F;
		}

		float s = t * 0.9F;
		shakeYaw = amplitude * (MathHelper.sin(s * 1.7F) * 0.6F + MathHelper.sin(s * 4.1F + 1.3F) * 0.4F);
		shakePitch = amplitude * 0.7F * (MathHelper.sin(s * 1.3F + 2.1F) * 0.6F + MathHelper.sin(s * 3.7F + 0.4F) * 0.4F);
	}

	// ================= fog =================

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onFogDensity(EntityViewRenderEvent.FogDensity event) {

		if(!active)
			return;

		float p = buildup(ticksElapsed + (float) event.renderPartialTicks);

		if(GLContext.getCapabilities().GL_NV_fog_distance) {
			GL11.glFogi(34138, 34139);
		}
		GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);

		event.density = 0.003F + 0.027F * p * p;
		event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onFogColors(EntityViewRenderEvent.FogColors event) {

		if(!active)
			return;

		float t = ticksElapsed + (float) event.renderPartialTicks;
		int total = suckTicks + chargeTicks;

		float crimson = t < total ? 0.9F * ease(suckProgress(t)) : 1F;
		float glare = t < total ? chargeProgress(t) * chargeProgress(t) * chargeProgress(t) : 0F;

		event.red += (0.3F - event.red) * crimson;
		event.green += (0.02F - event.green) * crimson;
		event.blue += (0.03F - event.blue) * crimson;

		event.red += (1F - event.red) * glare;
		event.green += (0.9F - event.green) * glare;
		event.blue += (0.85F - event.blue) * glare;
	}

	private static float glitchIntensity(float t) {

		int total = suckTicks + chargeTicks;
		float suckP = suckProgress(t);
		float chargeP = chargeProgress(t);

		float glitch = t >= total ? 0.85F : 0.6F * ease(clamp01((suckP - 0.35F) / 0.65F)) + 0.4F * chargeP;
		glitch = Math.max(glitch, blackoutProgress(t));

		return ClientConfig.DIGAMMA_REDUCED_EFFECTS.get() ? glitch * 0.5F : glitch;
	}

	private static float inkProgress(float t) {
		return ease(clamp01((t - suckTicks * 0.08F) / (suckTicks * 0.9F)));
	}

	private static float waveStrength(float t) {
		if(t >= suckTicks + chargeTicks)
			return 1F;
		return Math.min(1F, 0.55F * clamp01((t - suckTicks * 0.3F) / (suckTicks * 0.7F)) + 0.45F * chargeProgress(t));
	}

	private static float heartbeat(float t) {
		float seconds = Math.min(t / 20F, (suckTicks + chargeTicks) / 20F);
		float buildupSeconds = (suckTicks + chargeTicks) / 20F;
		float afterSeconds = Math.max(t / 20F - buildupSeconds, 0F);

		float phase = (float) (Math.PI * 2D * (seconds + 0.75F * seconds * seconds / buildupSeconds + 2.5F * afterSeconds));
		float lub = (float) Math.pow(Math.max(Math.sin(phase), 0D), 6D);
		float dub = 0.6F * (float) Math.pow(Math.max(Math.sin(phase - 1.2F), 0D), 6D);
		return Math.min(1F, lub + dub);
	}

	private static float blackoutProgress(float t) {
		return clamp01((t - (suckTicks + chargeTicks + aftermathTicks - BLACKOUT_TICKS)) / BLACKOUT_TICKS);
	}

	// ================= celestial bodies =================

	private static float bodyInk(float t) {
		return clamp01(t / ((suckTicks + chargeTicks) * 0.95F));
	}

	/** How far the black has spread over a body in the sky; 0 for every body except the one the event is on. */
	public static float getBodyInk(CelestialBody body, float partialTicks) {
		if(!active || body != eventBody)
			return 0F;
		return bodyInk(ticksElapsed + partialTicks);
	}

	/** Spreads the black over a body's sky quad, starting from a spot picked from where the singularity is. */
	public static void drawBodyCorruption(CelestialBody body, double size, double uvOffset, float visibility, float partialTicks) {

		float ink = getBodyInk(body, partialTicks);
		if(ink <= 0F)
			return;

		float t = ticksElapsed + partialTicks;
		int total = suckTicks + chargeTicks;
		float soften = ClientConfig.DIGAMMA_REDUCED_EFFECTS.get() ? 0.5F : 1F;

		int seed = (int) quasarX * 31 + (int) quasarZ;
		float originU = 0.2F + 0.6F * RenderDigammaApocalypse.hash(seed);
		float originV = 0.25F + 0.5F * RenderDigammaApocalypse.hash(seed + 1);

		RenderDigammaApocalypse.drawBody(size, (float) uvOffset, t / 20F, ink, waveStrength(t), heartbeat(t) * soften, t >= total ? (t - total) / 20F : -1F, soften, originU, originV, visibility);
	}

	// ================= 2D overlay =================

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {

		if(!active || event.type != ElementType.ALL)
			return;

		int w = event.resolution.getScaledWidth();
		int h = event.resolution.getScaledHeight();

		float t = ticksElapsed + event.partialTicks;
		int total = suckTicks + chargeTicks;

		float suckP = suckProgress(t);
		float chargeP = chargeProgress(t);
		float afterP = aftermathProgress(t);
		float suck = ease(suckP);
		boolean boomed = t >= total;

		// the ink pass blackens the world itself, so the screen-wide darkening only needs to support it
		float dark = boomed ? 0.25F * (1F - 0.5F * afterP) : 0.3F * suck * (1F - chargeP);
		float red = boomed ? 0.2F : 0.18F * suck;

		float beatAmount = ClientConfig.DIGAMMA_REDUCED_EFFECTS.get() ? 0.12F : 0.3F;
		float beat = heartbeat(t);
		float vignette = 0.9F * (boomed ? 1F : suck) * (1F - beatAmount + beatAmount * beat);
		red += 0.05F * beat * (boomed ? 1F : suck);

		float white = boomed ? 0F : 0.9F * chargeP * chargeP * chargeP;
		if(!boomed) {
			white = Math.max(white, clamp01((t - (total - FLASH_RAMP_TICKS)) / FLASH_RAMP_TICKS));
		}

		float black = blackoutProgress(t);

		RenderDigammaApocalypse.drawTint(w, h, dark, red, vignette);
		RenderDigammaApocalypse.drawFlash(w, h, RenderDigammaApocalypse.FLASH_WHITE, white);
		RenderDigammaApocalypse.drawFlash(w, h, flashStyle, flashAlpha(t));
		RenderDigammaApocalypse.drawFlash(w, h, RenderDigammaApocalypse.FLASH_BLACK, black);
	}

	// ================= mob darkening =================

	@SubscribeEvent
	public void onRenderLivingPre(RenderLivingEvent.Pre event) {
		if(active) {
			float level = 1F - (1F - MOB_DARKEN_LEVEL) * ease(suckProgress(ticksElapsed));
			GL11.glColor4f(level, level * 0.6F, level * 0.6F, 1F);
		}
	}

	@SubscribeEvent
	public void onRenderLivingPost(RenderLivingEvent.Post event) {
		if(active) {
			GL11.glColor4f(1F, 1F, 1F, 1F);
		}
	}

	// ================= 3D =================

	@SubscribeEvent
	public void onRenderWorldLast(RenderWorldLastEvent event) {

		if(!active)
			return;

		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;
		World world = mc.theWorld;
		if(player == null || world == null)
			return;

		float pt = event.partialTicks;
		float t = ticksElapsed + pt;

		if(world.provider.dimensionId == dimensionId) {
			drawInk(player, pt, t);
			drawSkyObjects(player, pt, t);
			drawBolts(player, world, pt, t);
		}

		// last, so everything above gets distorted with the rest of the scene; the HUD is drawn afterwards and stays clean
		RenderDigammaApocalypse.drawGlitch(glitchIntensity(t), ticksElapsed / 2, !ClientConfig.DIGAMMA_REDUCED_EFFECTS.get());
	}

	/** First, so the light effects drawn after it sit on top of the dark instead of being inked over. */
	private static void drawInk(EntityPlayer player, float pt, float t) {

		double camX = player.prevPosX + (player.posX - player.prevPosX) * pt;
		double camY = player.prevPosY + (player.posY - player.prevPosY) * pt;
		double camZ = player.prevPosZ + (player.posZ - player.prevPosZ) * pt;

		RenderDigammaApocalypse.drawInk(camX - quasarX, camY - quasarY, camZ - quasarZ, t / 20F, inkProgress(t), waveStrength(t), shockRadius(t), !ClientConfig.DIGAMMA_REDUCED_EFFECTS.get());
	}

	/** Red lightning striking the ground around the player, faster as the event builds. Skipped in reduced mode since it flashes. */
	private static void drawBolts(EntityPlayer player, World world, float pt, float t) {

		float p = buildup(t);
		if(p < 0.35F || ClientConfig.DIGAMMA_REDUCED_EFFECTS.get())
			return;

		double camX = player.prevPosX + (player.posX - player.prevPosX) * pt;
		double camY = player.prevPosY + (player.posY - player.prevPosY) * pt;
		double camZ = player.prevPosZ + (player.posZ - player.prevPosZ) * pt;

		int period = 34 - (int) (24F * clamp01((p - 0.35F) / 0.65F));

		for(int slot = 0; slot < BOLT_SLOTS; slot++) {

			int clock = ticksElapsed + slot * 11;
			int phase = clock % period;
			if(phase >= BOLT_LIFE_TICKS)
				continue;

			int seed = (clock / period) * 7919 + slot * 104729;
			double angle = RenderDigammaApocalypse.hash(seed) * Math.PI * 2D;
			double radius = 25D + RenderDigammaApocalypse.hash(seed + 1) * 110D;

			double tx = player.posX + Math.cos(angle) * radius;
			double tz = player.posZ + Math.sin(angle) * radius;
			double ty = world.getHeightValue(MathHelper.floor_double(tx), MathHelper.floor_double(tz));
			if(ty < 1D)
				continue;

			double sx = tx + (RenderDigammaApocalypse.hash(seed + 2) - 0.5D) * 40D;
			double sz = tz + (RenderDigammaApocalypse.hash(seed + 3) - 0.5D) * 40D;
			double sy = ty + 140D + RenderDigammaApocalypse.hash(seed + 4) * 60D;

			RenderDigammaApocalypse.drawBolt(sx - camX, sy - camY, sz - camZ, tx - camX, ty - camY, tz - camZ, seed, 1F - phase / (float) BOLT_LIFE_TICKS);
		}
	}

	private static void drawSkyObjects(EntityPlayer player, float pt, float t) {

		double dx = quasarX - (player.prevPosX + (player.posX - player.prevPosX) * pt);
		double dy = quasarY - (player.prevPosY + (player.posY - player.prevPosY) * pt);
		double dz = quasarZ - (player.prevPosZ + (player.posZ - player.prevPosZ) * pt);
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

		int total = suckTicks + chargeTicks;
		float p = buildup(t);

		if(distance > RAYS_MIN_DISTANCE) {
			// far away the sprite is the only thing that shows the singularity, so keep it a readable size
			float halfAngle = Math.min(0.5F, (0.2F + 0.22F * ease(p)) * (float) Math.min(1D, 700D / distance) * (1F + 0.2F * aftermathProgress(t)));
			float rayStrength = clamp01((p - 0.25F) / 0.75F);
			RenderDigammaApocalypse.drawSky(dx, dy, dz, Math.max(0.1F, halfAngle), rayStrength, t / 20F, distance > SPRITE_MIN_DISTANCE);
		}

		if(t >= total) {
			RenderDigammaApocalypse.drawShell(dx, dy, dz, shockRadius(t), 0.85F * (1F - 0.6F * aftermathProgress(t)));
		}
	}
}
