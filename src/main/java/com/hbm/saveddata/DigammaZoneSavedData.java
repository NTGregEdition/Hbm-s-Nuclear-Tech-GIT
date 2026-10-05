package com.hbm.saveddata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class DigammaZoneSavedData extends WorldSavedData {

	public final static String key = "digammaZone";

	private long endTime;

	public DigammaZoneSavedData(String tagName) {
		super(tagName);
	}

	public static DigammaZoneSavedData forWorld(World world) {
		DigammaZoneSavedData result = (DigammaZoneSavedData) world.perWorldStorage.loadData(DigammaZoneSavedData.class, key);

		if(result == null) {
			result = new DigammaZoneSavedData(key);
			world.perWorldStorage.setData(key, result);
		}
		return result;
	}

	public boolean isActive(long worldTime) {
		return endTime > 0 && worldTime < endTime;
	}

	public void extendTo(long newEndTime) {
		if(newEndTime > endTime) {
			endTime = newEndTime;
			markDirty();
		}
	}

	public void clear() {
		if(endTime != 0) {
			endTime = 0;
			markDirty();
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		endTime = nbt.getLong("endTime");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setLong("endTime", endTime);
	}
}
