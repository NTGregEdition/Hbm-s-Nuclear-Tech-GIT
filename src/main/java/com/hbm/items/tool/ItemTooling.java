package com.hbm.items.tool;

import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;

import api.hbm.block.IToolable;
import api.hbm.block.IToolable.ToolType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class ItemTooling extends ItemCraftingDegradation {
	
	protected ToolType type;
	protected boolean layered;
	@SideOnly(Side.CLIENT) private IIcon layerIcon;
	
	public ItemTooling(ToolType type, int durability) {
		super(durability);
		this.type = type;
		this.setFull3D();
		this.setCreativeTab(MainRegistry.controlTab);
		
		type.register(new ItemStack(this));
	}

	/** One item for all materials, tinted with the material color, tool + "_layer" is drawn on top without tint. Without a layer the whole icon is tinted */
	public ItemTooling(ToolType type, String tool, boolean layered) {
		super(tool);
		this.type = type;
		this.layered = layered;
		this.setFull3D();

		registerStacks(type);
	}

	public ToolType getToolType() {
		return type;
	}

	@Override
	public int getCraftWear() {
		return tool != null ? ToolMat.craftWear(type) : super.getCraftWear();
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float fX, float fY, float fZ) {
		
		Block b = world.getBlock(x, y, z);
		
		if(b instanceof IToolable) {
			if(((IToolable)b).onScrew(world, player, x, y, z, side, fX, fY, fZ, this.type)) {
				
				useTool(stack, player);
				
				return true;
			}
		}
		
		return false;
	}

	protected void useTool(ItemStack stack, EntityPlayer player) {
		addWear(stack, tool != null ? ToolMat.useCost(type) : 1, player);
	}

	@Override
	public boolean requiresMultipleRenderPasses() {
		return layered;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister reg) {
		if(tool == null) {
			super.registerIcons(reg);
			return;
		}
		this.itemIcon = reg.registerIcon(RefStrings.MODID + ":" + tool);
		if(layered) this.layerIcon = reg.registerIcon(RefStrings.MODID + ":" + tool + "_layer");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamageForRenderPass(int damage, int pass) {
		return pass == 1 && layerIcon != null ? layerIcon : this.itemIcon;
	}
}
