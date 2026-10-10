package com.hbm.blocks;

import com.hbm.render.block.RenderBlockMultipass;

import api.hbm.energymk2.VoltageTier;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

public class BlockVoltage extends BlockMulti {

	private IIcon[] icons;

	public BlockVoltage(Material mat) {
		super(mat);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		this.icons = new IIcon[VoltageTier.TIERS.length];

		for(int i = 0; i < icons.length; i++) {
			icons[i] = reg.registerIcon(this.getTextureName() + "_" + VoltageTier.getTierKey(VoltageTier.TIERS[i]));
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		return icons[VoltageTier.wrapIndex(meta)];
	}

	@Override
	public String getOverrideDisplayName(ItemStack stack) {
		String tier = VoltageTier.getTierName(VoltageTier.TIERS[VoltageTier.wrapIndex(stack.getItemDamage())]);
		return StatCollector.translateToLocalFormatted(this.getUnlocalizedName() + ".name", tier);
	}

	@Override
	public int getSubCount() {
		return VoltageTier.TIERS.length;
	}

	@Override
	public int getPasses() {
		return 2;
	}

	@Override
	public boolean shouldRenderItemMulti() {
		return true;
	}

	@Override
	public int getRenderType() {
		return IBlockMultiPass.getRenderType();
	}
}
