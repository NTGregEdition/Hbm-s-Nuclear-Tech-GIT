package com.hbm.items.tool;

import java.util.List;

import com.google.common.collect.Multimap;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;

import api.hbm.block.IToolable.ToolType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

public class ItemCraftingDegradation extends Item {

	private static final String NBT_WEAR = "toolWear";

	protected final String tool;

	public ItemCraftingDegradation(int durability) {
		this(null, durability);
	}

	protected ItemCraftingDegradation(String tool) {
		this(tool, 0);
		this.setHasSubtypes(true);
		this.setUnlocalizedName(tool);
	}

	private ItemCraftingDegradation(String tool, int durability) {
		this.tool = tool;
		this.setMaxStackSize(1);
		this.setMaxDamage(durability);
		this.setNoRepair();
		this.setCreativeTab(MainRegistry.controlTab);
	}

	public int getCraftWear() {
		return 1;
	}

	public static int maxWear(ItemStack stack) {
		String tool = toolOf(stack);
		if(tool != null) return ToolMat.of(tool, stack.getItemDamage()).maxDamage(tool);
		return stack.isItemStackDamageable() ? stack.getMaxDamage() : 0;
	}

	public static int wear(ItemStack stack) {
		if(toolOf(stack) == null) return stack.getItemDamage();
		return stack.hasTagCompound() ? stack.stackTagCompound.getInteger(NBT_WEAR) : 0;
	}

	public static void addWear(ItemStack stack, int amount, EntityLivingBase holder) {

		if(toolOf(stack) == null) {
			stack.damageItem(amount, holder);
			return;
		}

		int max = maxWear(stack);
		if(max <= 0 || holder instanceof EntityPlayer && ((EntityPlayer) holder).capabilities.isCreativeMode) return;

		int worn = wear(stack) + amount;

		if(worn > max) {
			holder.renderBrokenItemStack(stack);
			stack.stackSize--;
			worn = 0;
		}

		setWear(stack, worn);
	}

	private static void setWear(ItemStack stack, int worn) {
		if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		stack.stackTagCompound.setInteger(NBT_WEAR, worn);
	}

	private static String toolOf(ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof ItemCraftingDegradation ? ((ItemCraftingDegradation) item).tool : null;
	}

	public static boolean isListed(ItemStack stack) {
		return toolOf(stack) != null && stack.getItemDamage() == ToolMat.LISTED.id;
	}

	public static ItemStack toListed(ItemStack stack) {
		return toolOf(stack) == null || isListed(stack) ? stack : new ItemStack(stack.getItem(), 1, ToolMat.LISTED.id);
	}

	protected void registerStacks(ToolType type) {
		for(ToolMat mat : ToolMat.ALL) if(mat.exists(tool)) type.register(new ItemStack(this, 1, mat.id));
	}

	@Override
	public boolean doesContainerItemLeaveCraftingGrid(ItemStack stack) {
		return false;
	}

	@Override
	public boolean hasContainerItem(ItemStack stack) {
		return true;
	}

	@Override
	public ItemStack getContainerItem(ItemStack stack) {

		if(maxWear(stack) <= 0) return stack;

		int worn = wear(stack) + getCraftWear();
		ItemStack damaged = stack.copy();

		if(tool == null) {
			damaged.setItemDamage(worn);
			return damaged;
		}

		if(worn > maxWear(stack)) return null;
		setWear(damaged, worn);
		return damaged;
	}

	@Override
	public boolean showDurabilityBar(ItemStack stack) {
		return tool != null ? wear(stack) > 0 && maxWear(stack) > 0 : super.showDurabilityBar(stack);
	}

	@Override
	public double getDurabilityForDisplay(ItemStack stack) {
		return tool != null ? (double) wear(stack) / (double) maxWear(stack) : super.getDurabilityForDisplay(stack);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs tab, List list) {
		if(tool == null) {
			super.getSubItems(item, tab, list);
			return;
		}
		list.add(new ItemStack(item, 1, ToolMat.LISTED.id));
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		if(tool == null) return super.getItemStackDisplayName(stack);
		return StatCollector.translateToLocalFormatted("toolbase." + tool, StatCollector.translateToLocal("toolmat." + ToolMat.of(tool, stack.getItemDamage()).name)).trim();
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
		if(tool != null) ToolMat.of(tool, stack.getItemDamage()).addTooltip(stack, list, tool);
	}

	@Override
	public Multimap getAttributeModifiers(ItemStack stack) {
		Multimap multimap = super.getAttributeModifiers(stack);
		if(tool != null) multimap.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Weapon modifier", ToolMat.of(tool, stack.getItemDamage()).damage(tool), 0));
		return multimap;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public int getColorFromItemStack(ItemStack stack, int pass) {
		return tool != null && pass == 0 ? ToolMat.of(tool, stack.getItemDamage()).color : 0xFFFFFF;
	}

	@Override
	public Item setUnlocalizedName(String unlocalizedName) {
		super.setUnlocalizedName(unlocalizedName);
		this.setTextureName(RefStrings.MODID + ":"+ unlocalizedName);
		return this;
	}
}
