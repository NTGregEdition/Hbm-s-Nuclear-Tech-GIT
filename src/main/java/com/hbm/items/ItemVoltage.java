package com.hbm.items;

import java.util.List;

import api.hbm.energymk2.VoltageTier;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

public class ItemVoltage extends Item {

	private IIcon[] icons;

	public ItemVoltage() {
		this.setHasSubtypes(true);
		this.setMaxDamage(0);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs tab, List list) {
		for(int i = 0; i < VoltageTier.TIERS.length; i++) {
			list.add(new ItemStack(item, 1, i));
		}
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		String tier = VoltageTier.getTierName(VoltageTier.TIERS[VoltageTier.wrapIndex(stack.getItemDamage())]);
		return StatCollector.translateToLocalFormatted(this.getUnlocalizedName() + ".name", tier);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister reg) {
		this.icons = new IIcon[VoltageTier.TIERS.length];

		for(int i = 0; i < icons.length; i++) {
			icons[i] = reg.registerIcon(this.getIconString() + "_" + VoltageTier.getTierKey(VoltageTier.TIERS[i]));
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean requiresMultipleRenderPasses() {
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int meta) {
		return icons[VoltageTier.wrapIndex(meta)];
	}
}
