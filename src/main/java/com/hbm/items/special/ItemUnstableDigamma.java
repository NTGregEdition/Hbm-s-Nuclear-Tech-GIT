package com.hbm.items.special;

import java.util.List;

import com.hbm.config.ServerConfig;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.effect.EntityQuasar;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.handler.DigammaApocalypseHandler;
import com.hbm.lib.ModDamageSource;
import com.hbm.util.TrackerUtil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class ItemUnstableDigamma extends Item {

	/** Size passed to EntityQuasar. */
	public static float SINGULARITY_SIZE = 30F;

	/** How far away the mushroom cloud / quasar stay loaded & visible from. */
	public static int TRACKING_RANGE = 1500;

	private final int radius;
	private final int timer;

	public ItemUnstableDigamma(int radius, int timer) {
		this.radius = radius;
		this.timer = timer;
		this.setMaxStackSize(1);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {
		list.add("Decay: " + Math.min(100, getTimer(stack) * 100 / timer) + "%");
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {

		if(this.hasDetonated(stack))
			return;

		this.setTimer(stack, this.getTimer(stack) + 1);

		if(this.getTimer(stack) >= timer) {
			this.setDetonated(stack);

			if(!world.isRemote) {
				detonate(world, entity);
			}

			stack.stackSize = 0;
		}
	}

	private void detonate(World world, Entity holder) {

		double x = holder.posX;
		double y = holder.posY;
		double z = holder.posZ;

		world.playSoundEffect(x, y, z, "hbm:entity.oldExplosion", 1.0F, 1.0F);
		world.spawnEntityInWorld(EntityNukeExplosionMK5.statFac(world, radius, x, y, z));

		EntityNukeTorex torex = new EntityNukeTorex(world);
		torex.setPositionAndRotation(x, y + 1, z, 0, 0);
		torex.getDataWatcher().updateObject(10, 3.0F);
		world.spawnEntityInWorld(torex);
		TrackerUtil.setTrackingRange(world, torex, TRACKING_RANGE);

		holder.attackEntityFrom(ModDamageSource.nuclearBlast, 10000);

		EntityQuasar quasar = new EntityQuasar(world, SINGULARITY_SIZE).anchor();
		quasar.posX = x;
		quasar.posY = y + 2;
		quasar.posZ = z;
		world.spawnEntityInWorld(quasar);
		TrackerUtil.setTrackingRange(world, quasar, TRACKING_RANGE);

		DigammaApocalypseHandler.watchSingularity(quasar);

		if(ServerConfig.DIGAMMA_APOCALYPSE_MODE.get()) {
			DigammaApocalypseHandler.beginCutscene(quasar);
		}
	}

	private void setTimer(ItemStack stack, int time) {
		if(!stack.hasTagCompound())
			stack.stackTagCompound = new NBTTagCompound();

		stack.stackTagCompound.setInteger("timer", time);
	}

	private int getTimer(ItemStack stack) {
		if(!stack.hasTagCompound())
			stack.stackTagCompound = new NBTTagCompound();

		return stack.stackTagCompound.getInteger("timer");
	}

	private boolean hasDetonated(ItemStack stack) {
		return stack.hasTagCompound() && stack.stackTagCompound.getBoolean("detonated");
	}

	private void setDetonated(ItemStack stack) {
		if(!stack.hasTagCompound())
			stack.stackTagCompound = new NBTTagCompound();

		stack.stackTagCompound.setBoolean("detonated", true);
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		return ("" + StatCollector.translateToLocal(this.getUnlocalizedName() + ".name")).trim();
	}
}
