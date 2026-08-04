package com.hbm.blocks.machine;

import com.hbm.main.MainRegistry;
import com.hbm.tileentity.machine.TileEntityCraftingStation;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class CraftingStation extends BlockContainer {

	public CraftingStation() {
		super(Material.wood);
		this.setStepSound(Block.soundTypeWood);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityCraftingStation();
	}

	@Override
	public net.minecraft.util.IIcon getIcon(int side, int metadata) {
		return net.minecraft.init.Blocks.crafting_table.getIcon(side, metadata);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) return true;
		if(player.isSneaking()) return false;
		FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, x, y, z);
		return true;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
		TileEntity tile = world.getTileEntity(x, y, z);
		if(tile instanceof TileEntityCraftingStation) {
			TileEntityCraftingStation station = (TileEntityCraftingStation) tile;
			for(int slot = 0; slot < station.getSizeInventory(); slot++) {
				ItemStack stack = station.getStackInSlot(slot);
				if(stack != null) world.spawnEntityInWorld(new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, stack.copy()));
			}
		}
		super.breakBlock(world, x, y, z, block, metadata);
	}
}
