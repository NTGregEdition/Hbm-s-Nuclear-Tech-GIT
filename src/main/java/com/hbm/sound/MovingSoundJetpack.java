package com.hbm.sound;

import com.hbm.handler.JetpackHandler;
import com.hbm.handler.JetpackHandler.JetpackInfo;
import com.hbm.lib.RefStrings;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

@SideOnly(Side.CLIENT)
public class MovingSoundJetpack extends MovingSound {

	public EntityPlayer player;

	public MovingSoundJetpack(EntityPlayer player) {
		super(new ResourceLocation(RefStrings.MODID, "weapon.jetpack"));
		this.player = player;
		this.repeat = true;
	}

	@Override
	public void update() {
		this.xPosF = (float) player.posX;
		this.yPosF = (float) player.posY;
		this.zPosF = (float) player.posZ;
		JetpackInfo j = JetpackHandler.get(player);
		if(j != null) {
			this.volume = (float) Math.log(j.thrust * 30 + 1) * 0.25F;
		} else {
			this.donePlaying = true;
		}
	}

	public void end() {
		this.donePlaying = true;
	}
}
