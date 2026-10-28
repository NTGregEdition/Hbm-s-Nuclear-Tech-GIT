package com.hbm.util;

import com.hbm.lib.ModDamageSource;
import com.hbm.main.MainRegistry;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.PacketSpecialDeath;
import com.hbm.physics.Vec3d;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

public class CrucibleSlice {

	public static boolean canSlice(EntityLivingBase victim, EntityPlayer attacker) {
		if(!(victim instanceof EntityPlayer)) return true;
		EntityPlayer target = (EntityPlayer) victim;
		return !target.capabilities.disableDamage && target.canAttackPlayer(attacker);
	}

	public static void slice(EntityLivingBase victim, Vec3d point, Vec3d normal) {
		Vec3d rel = point.subtract(new Vec3d(victim.posX, victim.posY, victim.posZ));
		float[] plane = new float[] {(float) normal.x, (float) normal.y, (float) normal.z, -(float) normal.dotProduct(rel)};
		
		victim.getEntityData().setBoolean("killedByMobSlicer", true);
		try {
			victim.func_110142_aN().func_94547_a(ModDamageSource.crucible, victim.getHealth(), victim.getHealth());
			if(victim instanceof EntityPlayer) victim.setDead();
			victim.setHealth(0);
			victim.onDeath(ModDamageSource.crucible);
		} finally {
			victim.getEntityData().removeTag("killedByMobSlicer");
		}

		EntityTrackerEntry tracker = TrackerUtil.getTrackerEntry((WorldServer) victim.worldObj, victim.getEntityId());
		if(tracker != null) {
			PacketSpecialDeath packet = new PacketSpecialDeath(victim, plane);
			for(Object tracking : tracker.trackingPlayers) PacketDispatcher.wrapper.sendTo(packet, (EntityPlayerMP) tracking);
			if(victim instanceof EntityPlayerMP) PacketDispatcher.wrapper.sendTo(packet, (EntityPlayerMP) victim);
		} else {
			MainRegistry.logger.warn("Sliced entity " + victim + " is not tracked, clients will not see the cut");
		}
	}
}
