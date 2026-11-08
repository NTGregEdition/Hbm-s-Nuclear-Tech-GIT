package com.hbm.handler.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.recipe.GuiUsageRecipe;

public class NEIToolMatConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		ToolRecipeHandler handler = new ToolRecipeHandler();
		API.registerRecipeHandler(handler);
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
