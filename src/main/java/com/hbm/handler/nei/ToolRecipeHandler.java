package com.hbm.handler.nei;

import com.hbm.items.tool.ItemCraftingDegradation;
import com.hbm.items.tool.ToolMat;

import codechicken.nei.recipe.ShapedRecipeHandler;
import net.minecraft.item.ItemStack;

/** The item list only has one material of a tool, so its recipe page lists the recipes of every material like in GTNH */
public class ToolRecipeHandler extends ShapedRecipeHandler {

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		if(!ItemCraftingDegradation.isListed(result)) return;
		for(ToolMat mat : ToolMat.ALL) super.loadCraftingRecipes(new ItemStack(result.getItem(), 1, mat.id));
	}
}
