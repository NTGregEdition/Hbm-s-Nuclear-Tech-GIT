package com.hbm.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.ServerConfig;
import com.hbm.entity.effect.EntityQuasar;
import com.hbm.lib.ModDamageSource;
import com.hbm.main.MainRegistry;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.PacketDigammaApocalypse;
import com.hbm.saveddata.DigammaZoneSavedData;
import com.hbm.util.ContaminationUtil;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

public class DigammaApocalypseHandler {

	public static int SUCK_TICKS = 20 * 20;
	public static int CHARGE_TICKS = 20 * 15;
	public static int SINGULARITY_LIFESPAN_TICKS = 20 * 60 * 5;

	public static float AOE_KILL_RADIUS = 500F;
	public static float AOE_DAMAGE_PER_HIT = 20F;
	public static int AOE_DAMAGE_INTERVAL_TICKS = 20;

	public static int KICK_DELAY_AFTER_BOOM_TICKS = 20 * 7;

	private static final long TICK_NANOS = 50_000_000L;
	/** A gap between server ticks longer than this (a paused integrated server) only counts this much, so a pause can't skip the show. */
	private static final long MAX_TICK_GAP_NANOS = 1_000_000_000L;

	/** make blocks just move them upwards */
	public static int BLOCK_RAISE_INTERVAL_TICKS = 10;
	public static int BLOCK_RAISE_COUNT_PER_INTERVAL = 4;
	public static int BLOCK_RAISE_RADIUS = 8;

	public static int ZONE_DURATION_DAYS = 100;
	private static final long TICKS_PER_DAY = 24000L;
	/** digamma damage (50 drx/s) */
	public static float RADIATION_PER_TICK = (50F / 1000F) / 20F;

	public static int FIRE_PLACEMENT_INTERVAL_TICKS = 20;
	public static float FIRE_PLACEMENT_CHANCE = 0.5F;
	public static int FIRE_PLACEMENT_RADIUS = 12;

	public static final String KICK_MESSAGE = "A fatal error has occurred and this connection has been terminated";

	private static final Random rand = new Random();

	private static class CutsceneSession {
		EntityQuasar quasar;
		Ticket chunkTicket;
		MinecraftServer server;
		/** Real time, not server ticks, so a lagging server still ends the event when the clients' timelines do. */
		long elapsedNanos;
		long lastTickNanos;
		volatile long deadlineNanos;
		volatile boolean finished;
		int damageTickCounter;
		int blockRaiseCounter;

		long remainingNanos() {
			return totalTicks() * TICK_NANOS - elapsedNanos;
		}
	}

	private static class SingularityWatch {
		EntityQuasar singularity;
		MinecraftServer server;
		World world;
		float size;
		double spawnX, spawnY, spawnZ;
		int ticksLeft = SINGULARITY_LIFESPAN_TICKS;
	}

	private static final List<CutsceneSession> sessions = new ArrayList<CutsceneSession>();
	private static final List<SingularityWatch> singularities = new ArrayList<SingularityWatch>();

	private static final CopyOnWriteArrayList<NetHandlerPlayServer> kickTargets = new CopyOnWriteArrayList<NetHandlerPlayServer>();

	private static boolean pendingWorldDeletion = false;
	private static File pendingSaveDirectory = null;

	public static void watchSingularity(EntityQuasar singularity) {
		if(singularity == null)
			return;
		SingularityWatch w = new SingularityWatch();
		w.singularity = singularity;
		w.server = MinecraftServer.getServer();
		w.world = singularity.worldObj;
		w.size = singularity.getDataWatcher().getWatchableObjectFloat(16);
		w.spawnX = singularity.posX;
		w.spawnY = singularity.posY;
		w.spawnZ = singularity.posZ;
		singularities.add(w);
	}

	public static void beginCutscene(EntityQuasar quasar) {

		CutsceneSession s = new CutsceneSession();
		s.quasar = quasar;
		s.server = MinecraftServer.getServer();
		s.lastTickNanos = System.nanoTime();
		s.deadlineNanos = s.lastTickNanos + totalTicks() * TICK_NANOS;

		World world = quasar.worldObj;
		Ticket ticket = ForgeChunkManager.requestTicket(MainRegistry.instance, world, Type.ENTITY);
		if(ticket != null) {
			ticket.bindEntity(quasar);
			ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(MathHelper.floor_double(quasar.posX) >> 4, MathHelper.floor_double(quasar.posZ) >> 4));
		}
		s.chunkTicket = ticket;

		sessions.add(s);

		if(ServerConfig.DIGAMMA_APOCALYPSE_MODE.get()) {
			DigammaZoneSavedData.forWorld(world).extendTo(world.getTotalWorldTime() + ZONE_DURATION_DAYS * TICKS_PER_DAY);
			// the event ends by kicking everyone and possibly stopping the server, so don't wait for an autosave
			world.perWorldStorage.saveAllData();
		}

		for(Object o : s.server.getConfigurationManager().playerEntityList) {
			EntityPlayerMP p = (EntityPlayerMP) o;
			p.closeScreen();
			kickTargets.addIfAbsent(p.playerNetServerHandler);
		}

		if(s.server.isDedicatedServer()) {
			startKickWatchdog(s);
		}

		PacketDispatcher.wrapper.sendToAll(createPacket(s));
	}

	/** If the main thread stalls, still disconnect everyone on time. Kicking is only a netty write, so it is safe off-thread. */
	private static void startKickWatchdog(final CutsceneSession s) {

		Thread t = new Thread("DigammaApocalypse-Kick") {
			@Override
			public void run() {
				try {
					while(!s.finished) {
						long wait = s.deadlineNanos - System.nanoTime();
						if(wait <= 0L)
							break;
						Thread.sleep(Math.min(wait / 1_000_000L + 1L, 20L));
					}
				} catch(InterruptedException ex) {
					return;
				}

				if(!s.finished) {
					for(NetHandlerPlayServer handler : kickTargets) {
						handler.kickPlayerFromServer(KICK_MESSAGE);
					}
				}
			}
		};
		t.setPriority(Thread.MAX_PRIORITY);
		t.setDaemon(true);
		t.start();
	}

	private static int totalTicks() {
		return SUCK_TICKS + CHARGE_TICKS + KICK_DELAY_AFTER_BOOM_TICKS;
	}

	private static PacketDigammaApocalypse createPacket(CutsceneSession s) {
		EntityQuasar q = s.quasar;
		return new PacketDigammaApocalypse(SUCK_TICKS, CHARGE_TICKS, KICK_DELAY_AFTER_BOOM_TICKS, (int) (s.elapsedNanos / TICK_NANOS), q.worldObj.provider.dimensionId, q.posX, q.posY, q.posZ);
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {

		if(!(event.player instanceof EntityPlayerMP))
			return;

		EntityPlayerMP player = (EntityPlayerMP) event.player;
		MinecraftServer server = MinecraftServer.getServer();
		for(CutsceneSession s : sessions) {
			if(s.server == server && s.quasar != null && s.quasar.worldObj != null) {
				kickTargets.addIfAbsent(player.playerNetServerHandler);
				PacketDispatcher.wrapper.sendTo(createPacket(s), player);
				return;
			}
		}
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if(event.player instanceof EntityPlayerMP) {
			kickTargets.remove(((EntityPlayerMP) event.player).playerNetServerHandler);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onServerTick(TickEvent.ServerTickEvent event) {

		if(event.phase != Phase.END)
			return;

		MinecraftServer currentServer = MinecraftServer.getServer();

		tickCutsceneSessions(currentServer);
		tickSingularities(currentServer);
		tickDigammaZones(currentServer);
	}

	private void tickCutsceneSessions(MinecraftServer currentServer) {

		long now = System.nanoTime();

		Iterator<CutsceneSession> it = sessions.iterator();
		while(it.hasNext()) {

			CutsceneSession s = it.next();

			if(s.server != currentServer || s.quasar == null || s.quasar.worldObj == null) {
				if(s.chunkTicket != null) {
					try { ForgeChunkManager.releaseTicket(s.chunkTicket); } catch(Exception ignored) { }
				}
				s.finished = true;
				it.remove();
				continue;
			}

			s.elapsedNanos += Math.min(now - s.lastTickNanos, MAX_TICK_GAP_NANOS);
			s.lastTickNanos = now;

			long remaining = s.remainingNanos();
			s.deadlineNanos = now + remaining;

			// first thing, so nothing below can delay the disconnect
			if(remaining <= 0L) {
				if(s.chunkTicket != null) {
					ForgeChunkManager.releaseTicket(s.chunkTicket);
				}
				s.finished = true;
				it.remove();
				triggerTheEnd();
				continue;
			}

			World world = s.quasar.worldObj;
			boolean inMainPhase = remaining > KICK_DELAY_AFTER_BOOM_TICKS * TICK_NANOS;

			boolean raiseBlocks = false;
			if(inMainPhase) {
				raiseBlocks = ++s.blockRaiseCounter >= BLOCK_RAISE_INTERVAL_TICKS;
				if(raiseBlocks)
					s.blockRaiseCounter = 0;
			}

			for(Object o : world.playerEntities) {
				EntityPlayer live = (EntityPlayer) o;

				live.fallDistance = 0F;

				if(raiseBlocks)
					raiseNearbyBlocks(world, live.posX, live.posY, live.posZ);
			}

			if(inMainPhase) {

				s.damageTickCounter++;
				if(s.damageTickCounter >= AOE_DAMAGE_INTERVAL_TICKS) {
					s.damageTickCounter = 0;

					double r = AOE_KILL_RADIUS;
					AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
							s.quasar.posX - r, s.quasar.posY - r, s.quasar.posZ - r,
							s.quasar.posX + r, s.quasar.posY + r, s.quasar.posZ + r);

					List<EntityPlayer> victims = world.getEntitiesWithinAABB(EntityPlayer.class, box);
					for(EntityPlayer victim : victims) {
						victim.attackEntityFrom(ModDamageSource.digamma, AOE_DAMAGE_PER_HIT);
					}
				}
			}
		}
	}

	private static void raiseNearbyBlocks(World world, double px, double py, double pz) {

		int cx = MathHelper.floor_double(px);
		int cy = MathHelper.floor_double(py);
		int cz = MathHelper.floor_double(pz);

		for(int i = 0; i < BLOCK_RAISE_COUNT_PER_INTERVAL; i++) {

			int x = cx + rand.nextInt(BLOCK_RAISE_RADIUS * 2 + 1) - BLOCK_RAISE_RADIUS;
			int y = cy + rand.nextInt(BLOCK_RAISE_RADIUS) - BLOCK_RAISE_RADIUS / 2;
			int z = cz + rand.nextInt(BLOCK_RAISE_RADIUS * 2 + 1) - BLOCK_RAISE_RADIUS;

			Block block = world.getBlock(x, y, z);
			if(block == Blocks.air || block == Blocks.bedrock || block.getMaterial().isLiquid())
				continue;

			if(world.getBlock(x, y + 1, z) != Blocks.air)
				continue; // no room above to move into

			int meta = world.getBlockMetadata(x, y, z);

			world.setBlock(x, y, z, Blocks.air, 0, 3);
			world.setBlock(x, y + 1, z, block, meta, 3);
		}
	}

	private void tickSingularities(MinecraftServer currentServer) {

		Iterator<SingularityWatch> it2 = singularities.iterator();
		while(it2.hasNext()) {

			SingularityWatch w = it2.next();

			if(w.server != currentServer || w.world == null) {
				it2.remove();
				continue;
			}

			if(w.singularity == null || w.singularity.isDead) {

				if(w.ticksLeft > 0) {
					EntityQuasar replacement = new EntityQuasar(w.world, w.size).anchor();
					replacement.posX = w.spawnX;
					replacement.posY = w.spawnY;
					replacement.posZ = w.spawnZ;
					w.world.spawnEntityInWorld(replacement);
					w.singularity = replacement;

					// keep any cutscene session pointed at the new instance too
					for(CutsceneSession s : sessions) {
						if(s.server == w.server && s.quasar != null
								&& s.quasar.posX == w.spawnX && s.quasar.posY == w.spawnY && s.quasar.posZ == w.spawnZ) {
							s.quasar = replacement;
						}
					}
				} else {
					it2.remove();
					continue;
				}
			}

			// pin it in place - "singularity should be inmoveable"
			w.singularity.posX = w.spawnX;
			w.singularity.posY = w.spawnY;
			w.singularity.posZ = w.spawnZ;
			w.singularity.motionX = 0;
			w.singularity.motionY = 0;
			w.singularity.motionZ = 0;

			w.ticksLeft--;
			if(w.ticksLeft <= 0) {
				w.singularity.setDead();
				it2.remove();
			}
		}
	}

	private void tickDigammaZones(MinecraftServer currentServer) {

		for(WorldServer world : currentServer.worldServers) {

			long now = world.getTotalWorldTime();
			DigammaZoneSavedData zone = DigammaZoneSavedData.forWorld(world);

			if(!zone.isActive(now)) {
				zone.clear();
				continue;
			}

			for(Object o : world.playerEntities) {
				ContaminationUtil.applyDigammaData((EntityPlayer) o, RADIATION_PER_TICK);
			}

			if(now % FIRE_PLACEMENT_INTERVAL_TICKS != 0)
				continue;

			for(Object o : world.playerEntities) {
				EntityPlayer p = (EntityPlayer) o;
				if(rand.nextFloat() > FIRE_PLACEMENT_CHANCE)
					continue;
				placeDigammaFireNear(world, p.posX, p.posY, p.posZ);
			}
		}
	}

	private static void placeDigammaFireNear(World world, double px, double py, double pz) {

		int x = MathHelper.floor_double(px) + rand.nextInt(FIRE_PLACEMENT_RADIUS * 2 + 1) - FIRE_PLACEMENT_RADIUS;
		int z = MathHelper.floor_double(pz) + rand.nextInt(FIRE_PLACEMENT_RADIUS * 2 + 1) - FIRE_PLACEMENT_RADIUS;
		int y = world.getTopSolidOrLiquidBlock(x, z);

		if(ModBlocks.fire_digamma.canPlaceBlockAt(world, x, y, z)) {
			world.setBlock(x, y, z, ModBlocks.fire_digamma);
		}
	}

	@SuppressWarnings("unchecked")
	private static void triggerTheEnd() {

		MinecraftServer server = MinecraftServer.getServer();

		List<EntityPlayerMP> all = new ArrayList<EntityPlayerMP>((List<EntityPlayerMP>) server.getConfigurationManager().playerEntityList);
		for(EntityPlayerMP p : all) {
			p.playerNetServerHandler.kickPlayerFromServer(KICK_MESSAGE);
		}

		armDeletion(server);
	}

	private static void armDeletion(MinecraftServer server) {

		if(!ServerConfig.DIGAMMA_DELETE_WORLD.get() || pendingWorldDeletion)
			return;

		try {
			pendingSaveDirectory = server.worldServers[0].getSaveHandler().getWorldDirectory();
		} catch(Exception ex) {
			pendingSaveDirectory = null;
		}

		pendingWorldDeletion = true;
		server.initiateShutdown();
	}

	@SuppressWarnings("unchecked")
	public static void onServerStopping() {

		if(sessions.isEmpty())
			return;

		MinecraftServer server = MinecraftServer.getServer();
		if(server == null)
			return;

		for(CutsceneSession s : sessions) {
			s.finished = true;
		}

		try {
			List<EntityPlayerMP> all = new ArrayList<EntityPlayerMP>((List<EntityPlayerMP>) server.getConfigurationManager().playerEntityList);
			for(EntityPlayerMP p : all) {
				p.playerNetServerHandler.kickPlayerFromServer(KICK_MESSAGE);
			}
		} catch(Exception ignored) { }

		armDeletion(server);
		sessions.clear();
	}

	public static void onServerStopped() {

		kickTargets.clear();

		if(!pendingWorldDeletion)
			return;

		pendingWorldDeletion = false;

		final File dir = pendingSaveDirectory;

		Thread t = new Thread("DigammaApocalypse-Cleanup") {
			@Override
			public void run() {
				try {
					Thread.sleep(500L);
				} catch(InterruptedException ignored) { }

				if(dir != null) {
					deleteRecursively(dir);
				}

				System.exit(0);
			}
		};
		t.setDaemon(true);
		t.start();
	}

	private static void deleteRecursively(File file) {
		if(file == null || !file.exists())
			return;
		if(file.isDirectory()) {
			File[] children = file.listFiles();
			if(children != null) {
				for(File child : children) {
					deleteRecursively(child);
				}
			}
		}
		file.delete();
	}
}
