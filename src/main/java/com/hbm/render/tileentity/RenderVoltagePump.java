package com.hbm.render.tileentity;

import com.hbm.blocks.ModBlocks;

import net.minecraft.item.Item;

public class RenderVoltagePump extends RenderFluidPump {

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.voltage_pump);
	}
}
