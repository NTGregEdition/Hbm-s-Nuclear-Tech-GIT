package com.hbm.items.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import api.hbm.block.IToolable;
import api.hbm.block.IToolable.ToolType;

import com.hbm.items.ModItems;
import com.hbm.main.MainRegistry;

import com.sun.org.apache.xpath.internal.operations.Mod;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import net.minecraft.util.StatCollector;
import net.minecraft.util.ChatComponentTranslation;


public class ItemMultitool extends ItemCraftingDegradation {

	public static class Mode {
		public final ToolType type;
		public final String name; // NBT value + texture suffix (texture_layer.png)

		public final Item emulatedItem;

		public Mode(ToolType type, String name) {
			this(type, name, null);
		}

		public Mode(ToolType type, String name, Item emulatedItem) {
			this.type = type;
			this.name = name;
			this.emulatedItem = emulatedItem;
		}
	}

	private static final List<Mode> MODES = new ArrayList<Mode>();
	static {
		MODES.add(new Mode(ToolType.SCREWDRIVER, "screwdriver", ModItems.screwdriver));
		MODES.add(new Mode(ToolType.HAND_DRILL,      "hand_drill", ModItems.hand_drill));
		MODES.add(new Mode(ToolType.MALLET, "mallet"));
		MODES.add(new Mode(ToolType.SAW,      "saw"));
		MODES.add(new Mode(ToolType.HAMMER, "hammer"));
		MODES.add(new Mode(ToolType.FILE,      "file"));
		MODES.add(new Mode(ToolType.WIRE_CUTTER, "wire_cutter", ModItems.wire_cutter));
		MODES.add(new Mode(ToolType.WRENCH,      "wrench", ModItems.wrench));
	}

	private static final String NBT_MODE = "multitoolMode";

	private static final String BASE_ICON_KEY = "__base__";

	private final String iconPrefix;


	private Map<String, IIcon> icons;

	public ItemMultitool(String iconPrefix, int durability) {
		super(durability);
		this.iconPrefix = iconPrefix;
		this.setFull3D();
		this.setCreativeTab(MainRegistry.controlTab);

		for (Mode m : MODES) {
			m.type.register(new ItemStack(this));
		}
	}

	// ---------------- mode helpers ----------------

	private Mode getMode(ItemStack stack) {
		if (MODES.isEmpty()) return null;
		String name = stack.hasTagCompound() ? stack.getTagCompound().getString(NBT_MODE) : null;
		if (name == null || name.isEmpty()) return null;
		for (Mode m : MODES) {
			if (m.name.equals(name)) return m;
		}
		return null;
	}

	private void setMode(ItemStack stack, Mode mode) {
		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		stack.getTagCompound().setString(NBT_MODE, mode.name);
	}

	// ---------------- persisted state for emulated items ----------------

	private static final String NBT_EMU_STATE = "multitoolEmuState";

	private NBTTagCompound getEmulatedState(ItemStack realStack, Mode mode) {
		if (!realStack.hasTagCompound()) return null;
		NBTTagCompound root = realStack.getTagCompound();
		if (!root.hasKey(NBT_EMU_STATE)) return null;
		NBTTagCompound all = root.getCompoundTag(NBT_EMU_STATE);
		return all.hasKey(mode.name) ? all.getCompoundTag(mode.name) : null;
	}

	private void setEmulatedState(ItemStack realStack, Mode mode, NBTTagCompound state) {
		if (!realStack.hasTagCompound()) realStack.setTagCompound(new NBTTagCompound());
		NBTTagCompound root = realStack.getTagCompound();
		NBTTagCompound all = root.hasKey(NBT_EMU_STATE) ? root.getCompoundTag(NBT_EMU_STATE) : new NBTTagCompound();
		if (state != null && !state.hasNoTags()) {
			all.setTag(mode.name, state);
		} else {
			all.removeTag(mode.name);
		}
		root.setTag(NBT_EMU_STATE, all);
	}

	private ItemStack swapInEmulatedItem(ItemStack realStack, Mode mode, EntityPlayer player) {
		ItemStack fake = new ItemStack(mode.emulatedItem, 1, 0);
		NBTTagCompound savedState = getEmulatedState(realStack, mode);
		if (savedState != null) fake.setTagCompound((NBTTagCompound) savedState.copy());
		player.inventory.setInventorySlotContents(player.inventory.currentItem, fake);
		return fake;
	}

	private void restoreRealItem(ItemStack realStack, Mode mode, EntityPlayer player, ItemStack fake, ItemStack backup) {
		setEmulatedState(realStack, mode, fake.getTagCompound());
		player.inventory.setInventorySlotContents(player.inventory.currentItem, backup);
	}

// ---------------- rendering (icon only - durability bar comes from the base class, untouched) ----------------

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister reg) {
		icons = new HashMap<String, IIcon>();
		icons.put(BASE_ICON_KEY, reg.registerIcon("hbm:" + iconPrefix)); // neutral "no mode yet" look
		for (Mode m : MODES) {
			icons.put(m.name, reg.registerIcon("hbm:" + iconPrefix + "_" + m.name));
		}
		this.itemIcon = icons.get(BASE_ICON_KEY);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(ItemStack stack, int pass) {
		Mode mode = getMode(stack);
		if (icons == null) return super.getIcon(stack, pass);
		if (mode != null && icons.containsKey(mode.name)) {
			return icons.get(mode.name);
		}
		return icons.containsKey(BASE_ICON_KEY) ? icons.get(BASE_ICON_KEY) : super.getIcon(stack, pass);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconIndex(ItemStack stack) {
		Mode mode = getMode(stack);
		if (icons == null) return super.getIconIndex(stack);
		if (mode != null && icons.containsKey(mode.name)) {
			return icons.get(mode.name);
		}
		return icons.containsKey(BASE_ICON_KEY) ? icons.get(BASE_ICON_KEY) : super.getIconIndex(stack);
	}

	// ---------------- tooltip ----------------

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
		Mode mode = getMode(stack);
		if (mode != null) {
			String localizedModeName = StatCollector.translateToLocal("multitool.mode." + mode.name);

			list.add(StatCollector.translateToLocalFormatted("tooltip.multitool.mode", localizedModeName));
			list.add(StatCollector.translateToLocal("tooltip.multitool.switch"));
		} else {
			list.add(StatCollector.translateToLocal("tooltip.multitool.mode.none"));
			list.add(StatCollector.translateToLocal("tooltip.multitool.pick"));
		}
	}

	// ---------------- cycling ----------------

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (!MODES.isEmpty() && player.isSneaking()) {
			Mode current = getMode(stack);
			int idx = MODES.indexOf(current);
			Mode next = MODES.get((idx + 1) % MODES.size());
			setMode(stack, next);

			if (!world.isRemote) {
				ChatComponentTranslation modeNameComponent = new ChatComponentTranslation("multitool.mode." + next.name);

				player.addChatMessage(new ChatComponentTranslation("chat.multitool.switch", modeNameComponent));
			}
			world.playSoundAtEntity(player, "random.click", 0.4F, 1.6F);
		}
		return stack;
	}

	// ---------------- pre-empting Block#onBlockActivated (dud bombs, landmines, ...) ----------------

	@Override
	public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float fX, float fY, float fZ) {

		if (world.isRemote) return false;

		Mode mode = getMode(stack);
		if (mode == null || mode.emulatedItem == null) return false;

		Block b = world.getBlock(x, y, z);

		if (b.onBlockActivated(world, x, y, z, player, side, fX, fY, fZ)) {
			return true;
		}

		ItemStack backup = player.inventory.getStackInSlot(player.inventory.currentItem);
		ItemStack fake = swapInEmulatedItem(stack, mode, player);

		boolean handled;
		try {
			handled = mode.emulatedItem.onItemUseFirst(fake, player, world, x, y, z, side, fX, fY, fZ);
			if (!handled) {
				handled = b.onBlockActivated(world, x, y, z, player, side, fX, fY, fZ);
			}
		} finally {
			restoreRealItem(stack, mode, player, fake, backup);
		}

		if (handled && this.getMaxDamage() > 0) {
			stack.damageItem(1, player);
		}

		return handled;
	}

	// ---------------- exactly ItemTooling.onItemUse, just using the CURRENT mode instead of a fixed type ----------------

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float fX, float fY, float fZ) {

		Mode mode = getMode(stack);
		if (mode == null) return false;

		boolean handled = (mode.emulatedItem == null)
			? tryScrew(mode, player, world, x, y, z, side, fX, fY, fZ)
			: useEmulated(stack, mode, player, world, x, y, z, side, fX, fY, fZ);

		if (handled && this.getMaxDamage() > 0) {
			stack.damageItem(1, player);
		}

		return handled;
	}

	private boolean tryScrew(Mode mode, EntityPlayer player, World world, int x, int y, int z, int side, float fX, float fY, float fZ) {
		Block b = world.getBlock(x, y, z);
		if (b instanceof IToolable) {
			return ((IToolable) b).onScrew(world, player, x, y, z, side, fX, fY, fZ, mode.type);
		}
		return false;
	}

	private boolean useEmulated(ItemStack realStack, Mode mode, EntityPlayer player, World world, int x, int y, int z, int side, float fX, float fY, float fZ) {

		ItemStack backup = player.inventory.getStackInSlot(player.inventory.currentItem);
		ItemStack fake = swapInEmulatedItem(realStack, mode, player);

		boolean handled;
		try {
			handled = tryScrew(mode, player, world, x, y, z, side, fX, fY, fZ);
			if (!handled) {
				handled = mode.emulatedItem.onItemUse(fake, player, world, x, y, z, side, fX, fY, fZ);
			}
		} finally {
			restoreRealItem(realStack, mode, player, fake, backup);
		}

		return handled;
	}

	// ---------------- entities: defusing primed creepers, glyphid with nukes, etc. ----------------
	@Override
	public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target) {

		if (player.worldObj.isRemote) return false;

		Mode mode = getMode(stack);
		if (mode == null || mode.emulatedItem == null) return false;

		ItemStack backup = player.inventory.getStackInSlot(player.inventory.currentItem);
		ItemStack fake = swapInEmulatedItem(stack, mode, player);

		boolean handled;
		try {
			handled = mode.emulatedItem.itemInteractionForEntity(fake, player, target);
		} finally {
			restoreRealItem(stack, mode, player, fake, backup);
		}

		if (handled && this.getMaxDamage() > 0) {
			stack.damageItem(1, player);
		}

		return handled;
	}
}
