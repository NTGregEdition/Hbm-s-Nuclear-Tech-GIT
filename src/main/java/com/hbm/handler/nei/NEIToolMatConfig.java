package com.hbm.handler.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

public class NEIToolMatConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		API.registerRecipeHandler(new ToolRecipeHandler());
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
