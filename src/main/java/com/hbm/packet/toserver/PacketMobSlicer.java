package com.hbm.packet.toserver;

import java.util.ArrayList;
import java.util.List;

import com.hbm.items.weapon.ItemCrucible;
import com.hbm.physics.Vec3d;
import com.hbm.util.CrucibleSlice;
import com.hbm.util.EntityDamageUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Sent when a cut is swung with the crucible: the plane normal and a point on the plane (the line's start, in world space). */
public class PacketMobSlicer implements IMessage {

	private Vec3d pos;
	private Vec3d norm;

	public PacketMobSlicer() { }

	public PacketMobSlicer(Vec3d position, Vec3d normal) {
		this.pos = position;
		this.norm = normal;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
		norm = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeDouble(pos.x);
		buf.writeDouble(pos.y);
		buf.writeDouble(pos.z);
		buf.writeDouble(norm.x);
		buf.writeDouble(norm.y);
		buf.writeDouble(norm.z);
	}

	public static class Handler implements IMessageHandler<PacketMobSlicer, IMessage> {

		@Override
		public IMessage onMessage(PacketMobSlicer m, MessageContext ctx) {

			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			ItemStack held = player.getHeldItem();

			if(held == null || !(held.getItem() instanceof ItemCrucible) || !ItemCrucible.isCharged(held))
				return null;

			//a client can send anything, NaN fails both comparisons
			double normLength = m.norm.length();
			if(!(normLength > 0.9D && normLength < 1.1D) || !(m.pos.length() < 1.0E6D))
				return null;

			ItemCrucible.markCutSwing(player);

			Vec3 eyePos = EntityDamageUtil.getPosition(player);
			Vec3d eye = new Vec3d(eyePos.xCoord, eyePos.yCoord, eyePos.zCoord);
			Vec3 lookVec = player.getLookVec();
			Vec3d look = new Vec3d(lookVec.xCoord, lookVec.yCoord, lookVec.zCoord);
			Vec3d toStart = m.pos.subtract(eye);

			double reach = player.capabilities.isCreativeMode ? 6D : 3D;

			//fans out from the view direction to the start of the line
			List<EntityLivingBase> victims = new ArrayList<EntityLivingBase>();
			for(float i = 0; i <= 1; i += 0.1F) {
				Vec3d dir = new Vec3d(look.x + (toStart.x - look.x) * i, look.y + (toStart.y - look.y) * i, look.z + (toStart.z - look.z) * i).normalize();
				EntityLivingBase hit = trace(player.worldObj, eye, eye.add(dir.scale(reach)), player);
				if(hit != null && !victims.contains(hit)) victims.add(hit);
			}

			if(victims.isEmpty())
				return null;

			held.damageItem(1, player);

			//once per cut, not once per victim, the slices of a single swing are one sound
			ItemCrucible.playSliceSound(player);

			for(EntityLivingBase victim : victims)
				CrucibleSlice.slice(victim, m.pos, m.norm);

			return null;
		}

		/** Closest living entity on the segment, with the segment ending at the first block hit */
		private static EntityLivingBase trace(World world, Vec3d from, Vec3d to, EntityPlayerMP exclude) {

			Vec3 start = Vec3.createVectorHelper(from.x, from.y, from.z);
			Vec3 end = Vec3.createVectorHelper(to.x, to.y, to.z);

			//the block trace drags the vector it is given along the ray, the entity test below needs start where it began
			MovingObjectPosition block = world.func_147447_a(Vec3.createVectorHelper(from.x, from.y, from.z), Vec3.createVectorHelper(to.x, to.y, to.z), false, true, false);
			if(block != null) end = block.hitVec;

			AxisAlignedBB sweep = AxisAlignedBB.getBoundingBox(
					Math.min(start.xCoord, end.xCoord), Math.min(start.yCoord, end.yCoord), Math.min(start.zCoord, end.zCoord),
					Math.max(start.xCoord, end.xCoord), Math.max(start.yCoord, end.yCoord), Math.max(start.zCoord, end.zCoord)).expand(1D, 1D, 1D);

			EntityLivingBase closest = null;
			double closestDist = Double.MAX_VALUE;

			for(Object o : world.getEntitiesWithinAABB(EntityLivingBase.class, sweep)) {
				EntityLivingBase living = (EntityLivingBase) o;
				if(living == exclude || !living.isEntityAlive() || !living.canBeCollidedWith() || !CrucibleSlice.canSlice(living, exclude)) continue;

				AxisAlignedBB box = living.boundingBox.expand(0.3D, 0.3D, 0.3D);
				double dist = 0D;

				if(!box.isVecInside(start)) {
					MovingObjectPosition intercept = box.calculateIntercept(start, end);
					if(intercept == null) continue;
					dist = start.squareDistanceTo(intercept.hitVec);
				}

				if(dist < closestDist) {
					closestDist = dist;
					closest = living;
				}
			}

			return closest;
		}
	}
}
