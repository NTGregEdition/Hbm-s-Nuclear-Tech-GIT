package com.hbm.items.armor;

import java.util.List;

import com.hbm.handler.ArmorModHandler;
import com.hbm.handler.JetpackHandler;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.i18n.I18nUtil;

import api.hbm.fluidmk2.IFillableItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

public class JetpackGlider extends ItemArmorMod implements IFillableItem {

	private static final String TANK_KEY = "fuelTank";

	public final int capacity;

	public JetpackGlider(int capacity) {
		super(ArmorModHandler.plate_only, false, true, false, false);
		this.capacity = capacity;
	}

	public FluidTank getTank(ItemStack stack) {
		FluidTank tank = new FluidTank(Fluids.NONE, capacity);
		if(stack.hasTagCompound() && stack.getTagCompound().hasKey(TANK_KEY)) {
			tank.readFromNBT(stack.getTagCompound().getCompoundTag(TANK_KEY), "0");
		}
		return tank;
	}

	public void setTank(ItemStack stack, FluidTank tank) {
		if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		NBTTagCompound nbt = new NBTTagCompound();
		tank.writeToNBT(nbt, "0");
		stack.getTagCompound().setTag(TANK_KEY, nbt);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {
		FluidTank tank = getTank(stack);
		boolean hasFuel = tank.getFill() > 0 && tank.getTankType() != Fluids.NONE;
		FluidType type = hasFuel ? tank.getTankType() : null;
		String typeName = hasFuel ? type.getLocalizedName() : I18nUtil.resolveKey("armor.jetpackGlider.none");
		int percent = (int) (((float) tank.getFill() / tank.getMaxFill()) * 100);

		list.add(EnumChatFormatting.RED + "    " + I18nUtil.resolveKey("armor.jetpackGlider.fuelType", typeName));
		list.add(EnumChatFormatting.RED + "    " + I18nUtil.resolveKey("armor.jetpackGlider.fuelSpeed", JetpackHandler.getSpeed(type)));
		list.add(EnumChatFormatting.RED + "    " + I18nUtil.resolveKey("armor.jetpackGlider.fuelAmount", tank.getFill(), tank.getMaxFill(), percent));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addDesc(List list, ItemStack stack, ItemStack armor) {
		super.addDesc(list, stack, armor);
		addInformation(stack, null, list, false);
	}

	@Override
	public boolean acceptsFluid(FluidType type, ItemStack stack) {
		FluidTank tank = getTank(stack);
		if(tank.getTankType() == Fluids.NONE || tank.getFill() == 0) {
			return type == Fluids.KEROSENE || type == Fluids.KEROSENE_REFORM || type == Fluids.BALEFIRE || type == Fluids.NITAN;
		}
		return type == tank.getTankType();
	}

	@Override
	public int tryFill(FluidType type, int amount, ItemStack stack) {
		if(stack.stackSize > 1 || !acceptsFluid(type, stack)) return amount;

		FluidTank contained = getTank(stack);
		int filled = Math.min(capacity - contained.getFill(), amount);
		if(filled <= 0) return amount;

		if(contained.getFill() == 0) contained.setTankType(type);
		contained.setFill(contained.getFill() + filled);
		setTank(stack, contained);
		return amount - filled;
	}

	@Override
	public boolean providesFluid(FluidType type, ItemStack stack) {
		return getTank(stack).getTankType() == type;
	}

	@Override
	public int tryEmpty(FluidType type, int amount, ItemStack stack) {
		if(stack.stackSize > 1) return 0;

		FluidTank contained = getTank(stack);
		if(contained.getTankType() != type) return 0;

		int drained = Math.min(contained.getFill(), amount);
		if(drained <= 0) return 0;

		contained.setFill(contained.getFill() - drained);
		if(contained.getFill() == 0) contained.setTankType(Fluids.NONE);
		setTank(stack, contained);
		return drained;
	}

	@Override
	public FluidType getFirstFluidType(ItemStack stack) {
		FluidType type = getTank(stack).getTankType();
		return type == Fluids.NONE ? null : type;
	}

	@Override
	public int getFill(ItemStack stack) {
		return getTank(stack).getFill();
	}
}
