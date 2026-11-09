package com.hbm.handler.nei;

import com.hbm.items.tool.ItemCraftingDegradation;
import com.hbm.items.tool.ToolMat;

import codechicken.nei.recipe.ShapedRecipeHandler;
import net.minecraft.item.ItemStack;

/** The item list only has one material of a tool, so its recipe page lists the recipes of every material like in GTNH */
public class ToolRecipeHandler extends ShapedRecipeHandler {

	/** Same id as the vanilla handler so NEI draws this as the real crafting table tab; no @Override as the compile-time NEI lacks it */
	public String getHandlerId() {
		return ShapedRecipeHandler.class.getName();
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		if(!ItemCraftingDegradation.isListed(result)) return;
		for(ToolMat mat : ToolMat.ALL) super.loadCraftingRecipes(new ItemStack(result.getItem(), 1, mat.id));
	}

	/** Recipes only show the listed material, so a usage lookup of any material is redirected to it. The array is shared with the handlers after this one, so this has to be the first usage handler */
	@Override
	public void loadUsageRecipes(String inputId, Object... ingredients) {
		if("item".equals(inputId) && ingredients.length > 0 && ingredients[0] instanceof ItemStack) ingredients[0] = ItemCraftingDegradation.toListed((ItemStack) ingredients[0]);
	}
}
