package com.hbm.blocks.machine;

import com.hbm.items.ModItems;
import com.hbm.tileentity.machine.TileEntityBlastFurnaceStruct;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class BlockBlastFurnaceStruct extends BlockContainer {

	@SideOnly(Side.CLIENT)
	private IIcon iconFront;

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister iconRegister) {
		this.iconFront = iconRegister.registerIcon(com.hbm.lib.RefStrings.MODID + ":struct_blast_furnace_front");
		this.blockIcon = iconRegister.registerIcon(com.hbm.lib.RefStrings.MODID + ":brick_fire");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int metadata) {
		return metadata == 0 && side == 3 ? this.iconFront : (side == metadata ? this.iconFront : this.blockIcon);
	}

	public BlockBlastFurnaceStruct(Material mat) {
		super(mat);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityBlastFurnaceStruct();
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
		int i = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;

		if(i == 0) world.setBlockMetadataWithNotify(x, y, z, 2, 2);
		if(i == 1) world.setBlockMetadataWithNotify(x, y, z, 5, 2);
		if(i == 2) world.setBlockMetadataWithNotify(x, y, z, 3, 2);
		if(i == 3) world.setBlockMetadataWithNotify(x, y, z, 4, 2);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		TileEntityBlastFurnaceStruct tile = (TileEntityBlastFurnaceStruct) world.getTileEntity(x, y, z);

		if(tile != null) {
			if(!world.isRemote && player.getHeldItem() != null && player.getHeldItem().getItem() == ModItems.wand_s) {
				tile.buildStructure();
				return true;
			}

			if(world.isRemote) {
				tile.highlightTimer = 100;
			}
			return true;
		}

		return false;
	}
}
