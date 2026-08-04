package com.hbm.items.tool;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.network.TileEntityPipelineBase;

import api.hbm.block.IToolable.ToolType;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class ItemToolingWrench extends ItemTooling {

	public ItemToolingWrench(ToolType type, int durability) {
		super(type, durability);
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float fX, float fY, float fZ) {

		if(!player.isSneaking()) {

			Block b = world.getBlock(x, y, z);
			int px = x, py = y, pz = z;

			if(b instanceof BlockDummyable) {
				int[] core = ((BlockDummyable) b).findCore(world, x, y, z);
				if(core != null) {
					px = core[0]; py = core[1]; pz = core[2];
				}
			}

			TileEntity te = world.getTileEntity(px, py, pz);

			if(te instanceof TileEntityPipelineBase) {

				if(stack.stackTagCompound == null) {
					stack.stackTagCompound = new NBTTagCompound();
					stack.stackTagCompound.setInteger("x", px);
					stack.stackTagCompound.setInteger("y", py);
					stack.stackTagCompound.setInteger("z", pz);

					if(!world.isRemote) player.addChatMessage(new ChatComponentText("Pipe start"));

				} else if(!world.isRemote) {

					int x1 = stack.stackTagCompound.getInteger("x");
					int y1 = stack.stackTagCompound.getInteger("y");
					int z1 = stack.stackTagCompound.getInteger("z");

					if(world.getTileEntity(x1, y1, z1) instanceof TileEntityPipelineBase) {

						TileEntityPipelineBase first = (TileEntityPipelineBase) world.getTileEntity(x1, y1, z1);
						TileEntityPipelineBase second = (TileEntityPipelineBase) te;

						switch(TileEntityPipelineBase.canConnect(first, second)) {
							case 0:
								first.addConnection(px, py, pz);
								second.addConnection(x1, y1, z1);
								player.addChatMessage(new ChatComponentText("Pipe end"));
								break;
							case 1: player.addChatMessage(new ChatComponentText("Pipe error - Pipes are not the same type")); break;
							case 2: player.addChatMessage(new ChatComponentText("Pipe error - Cannot connect to the same pipe anchor")); break;
							case 3: player.addChatMessage(new ChatComponentText("Pipe error - Pipe anchor is too far away")); break;
							case 4: player.addChatMessage(new ChatComponentText("Pipe error - Pipe anchor fluid types do not match")); break;
						}
					} else {
						player.addChatMessage(new ChatComponentText("Pipe error"));
					}

					stack.stackTagCompound = null;
				}

				player.swingItem();
				return true;
			}
		}

		// not a pipe anchor -> fall back to the normal IToolable wrench behavior
		return super.onItemUse(stack, player, world, x, y, z, side, fX, fY, fZ);
	}

	@Override
	public void addInformation(ItemStack itemstack, EntityPlayer player, List list, boolean bool) {
		if(itemstack.stackTagCompound != null) {
			list.add("Pipe start x: " + itemstack.stackTagCompound.getInteger("x"));
			list.add("Pipe start y: " + itemstack.stackTagCompound.getInteger("y"));
			list.add("Pipe start z: " + itemstack.stackTagCompound.getInteger("z"));
		} else {
			list.add("Right-click anchor to connect");
		}
	}
}
