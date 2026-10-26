package com.hbm.packet.toclient;

import java.util.List;
import java.util.Random;

import com.hbm.main.MainRegistry;
import com.hbm.main.ResourceManager;
import com.hbm.particle.ParticleBlood;
import com.hbm.particle.ParticleCutSpark;
import com.hbm.particle.ParticleSlicedMob;
import com.hbm.physics.Vec3d;
import com.hbm.render.util.ModelRendererUtil;
import com.hbm.render.util.Triangle;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Tells clients that a mob was cut along the plane (normal xyz, offset w, relative to the mob's feet) so it falls apart into pieces. */
public class PacketSpecialDeath implements IMessage {

	private int entityId;
	private float[] plane;

	public PacketSpecialDeath() { }

	public PacketSpecialDeath(Entity entity, float[] plane) {
		this.entityId = entity.getEntityId();
		this.plane = plane;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		entityId = buf.readInt();
		plane = new float[4];
		for(int i = 0; i < plane.length; i++) plane[i] = buf.readFloat();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(entityId);
		for(float f : plane) buf.writeFloat(f);
	}

	public static class Handler implements IMessageHandler<PacketSpecialDeath, IMessage> {

		private static final int BLOOD_COUNT = 5;
		private static final int SPARK_BURSTS = 8;

		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(PacketSpecialDeath m, MessageContext ctx) {

			World world = Minecraft.getMinecraft().theWorld;
			if(world == null) return null;

			Entity entity = world.getEntityByID(m.entityId);

			//a failing handler would disconnect the player, the linkage error is a reflection lookup failing when ModelRendererUtil is first loaded
			try {
				if(entity instanceof EntityLivingBase) slice((EntityLivingBase) entity, m.plane);
			} catch(Exception | LinkageError ex) {
				MainRegistry.logger.error("Failed to slice entity " + m.entityId, ex);
			}

			return null;
		}

		@SideOnly(Side.CLIENT)
		private static void slice(EntityLivingBase entity, float[] plane) {

			Minecraft mc = Minecraft.getMinecraft();
			entity.setDead();

			ParticleSlicedMob[] pieces = ModelRendererUtil.generateCutParticles(entity, plane, ResourceManager.crucible_cap, true, caps -> spawnEffects(entity, plane, caps));

			for(ParticleSlicedMob piece : pieces) mc.effectRenderer.addEffect(piece);
		}

		/** Blood sprays sideways from random spots of the cut face, sparks fly off it in both directions */
		@SideOnly(Side.CLIENT)
		private static void spawnEffects(EntityLivingBase entity, float[] plane, List<Triangle> caps) {

			EntityPlayer viewer = Minecraft.getMinecraft().thePlayer;
			if(caps.isEmpty() || viewer == null) return;

			World world = entity.worldObj;
			Random rand = world.rand;
			Vec3d normal = new Vec3d(plane[0], plane[1], plane[2]);
			Vec3 look = viewer.getLook(1F);
			Vec3d bloodDirection = new Vec3d(look.xCoord, look.yCoord, look.zCoord).crossProduct(normal).normalize().scale(-0.6D);

			for(int i = 0; i < BLOOD_COUNT + SPARK_BURSTS; i++) {

				Triangle tri = caps.get(rand.nextInt(caps.size()));
				float rand1 = rand.nextFloat();
				float rand2 = rand.nextFloat();
				if(rand2 < rand1) {
					float swap = rand2;
					rand2 = rand1;
					rand1 = swap;
				}
				Vec3d pos = tri.p1.pos.scale(rand1).add(tri.p2.pos.scale(rand2 - rand1)).add(tri.p3.pos.scale(1 - rand2)).add(entity.posX, entity.boundingBox.minY, entity.posZ);

				if(i < BLOOD_COUNT) {
					Vec3d motion = bloodDirection.add(new Vec3d(rand.nextDouble() * 2 - 1, rand.nextDouble() * 2 - 1, rand.nextDouble() * 2 - 1).scale(0.2D));
					ParticleBlood blood = new ParticleBlood(world, pos.x, pos.y, pos.z, 1, 0.4F + rand.nextFloat() * 0.4F, 18 + rand.nextInt(10), 0.05F)
							.motion((float) motion.x, (float) motion.y, (float) motion.z).color(0.5F, 0.1F, 0.1F, 1F);
					blood.onUpdate();
					Minecraft.getMinecraft().effectRenderer.addEffect(blood);
				} else {
					sparkBurst(world, pos, normal.scale(i % 2 == 0 ? 0.4D : -0.4D));
				}
			}
		}

		/** 12 sparks inside a 60° cone around the direction, the direction's length is the minimum speed */
		@SideOnly(Side.CLIENT)
		private static void sparkBurst(World world, Vec3d pos, Vec3d direction) {

			Random rand = world.rand;
			Vec3d axis = direction.normalize();
			Vec3d helper = Math.abs(axis.y) < 0.9D ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
			Vec3d u = axis.crossProduct(helper).normalize();
			Vec3d v = axis.crossProduct(u);

			for(int i = 0; i < 12; i++) {
				double polar = Math.toRadians(rand.nextFloat() * 60F);
				double azimuth = rand.nextFloat() * 2D * Math.PI;
				Vec3d motion = axis.scale(Math.cos(polar)).add(u.scale(Math.cos(azimuth) * Math.sin(polar))).add(v.scale(Math.sin(azimuth) * Math.sin(polar)))
						.scale(direction.length() + rand.nextFloat() * 0.3F);

				ParticleCutSpark spark = new ParticleCutSpark(world, pos.x, pos.y, pos.z, 0.8F + rand.nextFloat() * 0.5F, 0.02F, 20 + rand.nextInt(20), 0.1F)
						.color(0.8F, 0.6F, 0.5F, 1.5F).motion((float) motion.x, (float) motion.y, (float) motion.z);
				Minecraft.getMinecraft().effectRenderer.addEffect(spark);
			}
		}
	}
}
