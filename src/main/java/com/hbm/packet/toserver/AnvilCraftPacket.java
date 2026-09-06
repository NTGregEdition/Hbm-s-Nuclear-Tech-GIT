package com.hbm.packet.toserver;

import com.hbm.inventory.container.ContainerAnvil;
import com.hbm.inventory.recipes.anvil.AnvilRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes.AnvilConstructionRecipe;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.util.AchievementHandler;
import com.hbm.util.InventoryUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

public class AnvilCraftPacket implements IMessage {

	int recipeIndex;
	int mode;

	public AnvilCraftPacket() { }

	public AnvilCraftPacket(AnvilConstructionRecipe recipe, int mode) {
		this.recipeIndex = AnvilRecipes.getConstruction().indexOf(recipe);
		this.mode = mode;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.recipeIndex = buf.readInt();
		this.mode = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(this.recipeIndex);
		buf.writeInt(this.mode);
	}

	public static class Handler implements IMessageHandler<AnvilCraftPacket, IMessage> {

		@Override
		public IMessage onMessage(AnvilCraftPacket m, MessageContext ctx) {

			if(m.recipeIndex < 0 || m.recipeIndex >= AnvilRecipes.getConstruction().size()) //recipe is out of range -> bad
				return null;

			EntityPlayer p = ctx.getServerHandler().playerEntity;

			if(!(p.openContainer instanceof ContainerAnvil)) //player isn't even using an anvil -> bad
				return null;

			ContainerAnvil anvil = (ContainerAnvil) p.openContainer;
			AnvilConstructionRecipe recipe = AnvilRecipes.getConstruction().get(m.recipeIndex);

			if(!recipe.isTierValid(anvil.tier)) //player is using the wrong type of anvil -> bad
				return null;

			int count = m.mode == 1 ? (recipe.output.size() > 1 ? 64 : (recipe.output.get(0).stack.getMaxStackSize() / recipe.output.get(0).stack.stackSize)) : 1;

			for(int i = 0; i < count; i++) {

				List<AStack> consumed = recipe.getConsumedInputs();
				boolean hasConsumed = InventoryUtil.doesPlayerHaveAStacks(p, consumed, false);

				boolean hasTools = true;
				for(int idx : recipe.keptInputs) {
					if(!InventoryUtil.doesPlayerHaveAStacks(p, Collections.singletonList(recipe.input.get(idx)), false)) {
						hasTools = false;
						break;
					}
				}

				if(hasConsumed && hasTools) {
					InventoryUtil.doesPlayerHaveAStacks(p, consumed, true);

					for(Map.Entry<Integer, Integer> entry : recipe.wear.entrySet()) {
						InventoryUtil.damageMatchingStack(p, recipe.input.get(entry.getKey()), entry.getValue());
					}

					InventoryUtil.giveChanceStacksToPlayer(p, recipe.output);
					AchievementHandler.fire(p, recipe.output.get(0).stack);

				} else {
					break;
				}
			}

			p.inventoryContainer.detectAndSendChanges();

			return null;
		}
	}
}
