package com.hbm.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.ServerConfig;
import com.hbm.entity.effect.EntityQuasar;
import com.hbm.lib.ModDamageSource;
import com.hbm.main.MainRegistry;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.PacketDigammaApocalypse;
import com.hbm.util.ContaminationUtil;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
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

	public static int SUCK_TICKS = 20 * 12;
	public static int CHARGE_TICKS = 20 * 10;
	public static int SINGULARITY_LIFESPAN_TICKS = 20 * 60 * 5;

	public static float AOE_KILL_RADIUS = 500F;
	public static float AOE_DAMAGE_PER_HIT = 20F;
	public static int AOE_DAMAGE_INTERVAL_TICKS = 20;

	public static int KICK_DELAY_AFTER_BOOM_TICKS = 25;

	/** make blocks just move them upwards */
	public static int BLOCK_RAISE_INTERVAL_TICKS = 10;
	public static int BLOCK_RAISE_COUNT_PER_INTERVAL = 4;
	public static int BLOCK_RAISE_RADIUS = 8;

	/** for some reason not working */
	public static long RADIATION_DURATION_TICKS = 100L * 24000L;
	/** digamma damage (50 drx/s) */
	public static float RADIATION_PER_TICK = (50F / 1000F) / 20F;

	/** (Not working) sometimes and randomly near the player digamma fire is placed on surface every 1 second */
	public static int FIRE_PLACEMENT_INTERVAL_TICKS = 20;
	public static float FIRE_PLACEMENT_CHANCE = 0.5F;
	public static int FIRE_PLACEMENT_RADIUS = 12;

	public static final String KICK_MESSAGE = "A fatal error has occurred and this connection has been terminated";

	private static final Random rand = new Random();

	private static class CutsceneSession {
		NetHandlerPlayServer netHandler;
		EntityQuasar quasar;
		Ticket chunkTicket;
		MinecraftServer server;
		int ticksLeft;
		int damageTickCounter;
		int blockRaiseCounter;
	}

	private static class SingularityWatch {
		EntityQuasar singularity;
		MinecraftServer server;
		World world;
		float size;
		double spawnX, spawnY, spawnZ;
		int ticksLeft = SINGULARITY_LIFESPAN_TICKS;
	}

	private static class RadiationZone {
		MinecraftServer server;
		int dimensionId;
		long ticksLeft = RADIATION_DURATION_TICKS;
		int fireTickCounter;
	}

	private static final List<CutsceneSession> sessions = new ArrayList<CutsceneSession>();
	private static final List<SingularityWatch> singularities = new ArrayList<SingularityWatch>();
	private static final List<RadiationZone> radiationZones = new ArrayList<RadiationZone>();

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

	public static void beginCutscene(EntityPlayerMP player, EntityQuasar quasar) {

		player.closeScreen();

		CutsceneSession s = new CutsceneSession();
		s.netHandler = player.playerNetServerHandler;
		s.quasar = quasar;
		s.server = MinecraftServer.getServer();
		s.ticksLeft = SUCK_TICKS + CHARGE_TICKS + KICK_DELAY_AFTER_BOOM_TICKS;

		World world = quasar.worldObj;
		Ticket ticket = ForgeChunkManager.requestTicket(MainRegistry.instance, world, Type.ENTITY);
		if(ticket != null) {
			ticket.bindEntity(quasar);
			ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(MathHelper.floor_double(quasar.posX) >> 4, MathHelper.floor_double(quasar.posZ) >> 4));
		}
		s.chunkTicket = ticket;

		sessions.add(s);

		if(ServerConfig.DIGAMMA_APOCALYPSE_MODE.get()) {
			RadiationZone z = new RadiationZone();
			z.server = s.server;
			z.dimensionId = world.provider.dimensionId;
			radiationZones.add(z);
		}

		PacketDispatcher.wrapper.sendTo(new PacketDigammaApocalypse(SUCK_TICKS, CHARGE_TICKS, quasar.posX, quasar.posY, quasar.posZ), player);
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {

		if(event.phase != Phase.END)
			return;

		MinecraftServer currentServer = MinecraftServer.getServer();

		tickCutsceneSessions(currentServer);
		tickSingularities(currentServer);
		tickRadiationZones(currentServer);
	}

	private void tickCutsceneSessions(MinecraftServer currentServer) {

		Iterator<CutsceneSession> it = sessions.iterator();
		while(it.hasNext()) {

			CutsceneSession s = it.next();

			if(s.server != currentServer || s.quasar == null || s.quasar.worldObj == null) {
				if(s.chunkTicket != null) {
					try { ForgeChunkManager.releaseTicket(s.chunkTicket); } catch(Exception ignored) { }
				}
				it.remove();
				continue;
			}

			boolean inMainPhase = s.ticksLeft > KICK_DELAY_AFTER_BOOM_TICKS;

			if(inMainPhase) {

				EntityPlayerMP live = s.netHandler.playerEntity;
				if(live != null && !live.isDead) {

					live.motionY = 0.02D;
					live.motionX *= 0.5D;
					live.motionZ *= 0.5D;
					live.fallDistance = 0F;

					s.blockRaiseCounter++;
					if(s.blockRaiseCounter >= BLOCK_RAISE_INTERVAL_TICKS) {
						s.blockRaiseCounter = 0;
						raiseNearbyBlocks(live.worldObj, live.posX, live.posY, live.posZ);
					}
				}

				s.damageTickCounter++;
				if(s.damageTickCounter >= AOE_DAMAGE_INTERVAL_TICKS) {
					s.damageTickCounter = 0;

					double r = AOE_KILL_RADIUS;
					AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
							s.quasar.posX - r, s.quasar.posY - r, s.quasar.posZ - r,
							s.quasar.posX + r, s.quasar.posY + r, s.quasar.posZ + r);

					List<EntityPlayer> victims = s.quasar.worldObj.getEntitiesWithinAABB(EntityPlayer.class, box);
					for(EntityPlayer victim : victims) {
						victim.attackEntityFrom(ModDamageSource.digamma, AOE_DAMAGE_PER_HIT);
					}
				}
			}

			s.ticksLeft--;

			if(s.ticksLeft <= 0) {
				if(s.chunkTicket != null) {
					ForgeChunkManager.releaseTicket(s.chunkTicket);
				}
				it.remove();
				triggerTheEnd();
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
					EntityQuasar replacement = new EntityQuasar(w.world, w.size);
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

	private void tickRadiationZones(MinecraftServer currentServer) {

		Iterator<RadiationZone> it = radiationZones.iterator();
		while(it.hasNext()) {

			RadiationZone z = it.next();

			if(z.server != currentServer) {
				it.remove();
				continue;
			}

			WorldServer world = findWorldServer(currentServer, z.dimensionId);
			if(world == null) {
				it.remove();
				continue;
			}

			for(Object o : world.playerEntities) {
				EntityPlayer p = (EntityPlayer) o;
				ContaminationUtil.applyDigammaData(p, RADIATION_PER_TICK);
			}

			z.fireTickCounter++;
			if(z.fireTickCounter >= FIRE_PLACEMENT_INTERVAL_TICKS) {
				z.fireTickCounter = 0;

				for(Object o : world.playerEntities) {
					EntityPlayer p = (EntityPlayer) o;
					if(rand.nextFloat() > FIRE_PLACEMENT_CHANCE)
						continue;
					placeDigammaFireNear(world, p.posX, p.posY, p.posZ);
				}
			}

			z.ticksLeft--;
			if(z.ticksLeft <= 0) {
				it.remove();
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

	private static WorldServer findWorldServer(MinecraftServer server, int dimensionId) {
		for(WorldServer w : server.worldServers) {
			if(w.provider.dimensionId == dimensionId)
				return w;
		}
		return null;
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
