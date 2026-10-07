package com.hbm.entity.effect;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class EntityQuasar extends EntityBlackHole {

	private static final int ANCHORED = 17;

	public EntityQuasar(World world) {
		super(world);
		this.ignoreFrustumCheck = true;
		this.isImmuneToFire = true;
	}

	public EntityQuasar(World world, float size) {
		super(world);
		this.dataWatcher.updateObject(16, size);
	}

	public EntityQuasar anchor() {
		this.dataWatcher.updateObject(ANCHORED, Byte.valueOf((byte) 1));
		return this;
	}

	public boolean isAnchored() {
		return this.dataWatcher.getWatchableObjectByte(ANCHORED) != 0;
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(ANCHORED, Byte.valueOf((byte) 0));
	}

	@Override
	public void onUpdate() {
		if(isAnchored()) {
			this.motionX = 0D;
			this.motionY = 0D;
			this.motionZ = 0D;
		}
		super.onUpdate();
	}

	@Override
	protected boolean canBeDestroyedByAntimatter() {
		return !isAnchored();
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		if(nbt.getBoolean("anchored")) {
			anchor();
		}
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		nbt.setBoolean("anchored", isAnchored());
	}
}
