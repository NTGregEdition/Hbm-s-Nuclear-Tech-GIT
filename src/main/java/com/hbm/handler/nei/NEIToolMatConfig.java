package com.hbm.handler.nei;

import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

public class NEIToolMatConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		ToolRecipeHandler handler = new ToolRecipeHandler();
		// API.registerRecipeHandler would drop it, the vanilla shaped handler already owns this id
		GuiCraftingRecipe.craftinghandlers.add(handler);
		GuiUsageRecipe.usagehandlers.add(0, handler);
	}

	@Override
	public String getName() {
		return "NTM tool materials";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}
}
