package com.hbm.inventory.container;

import java.util.ArrayList;
import java.util.List;

import com.hbm.tileentity.machine.TileEntityCraftingStation;
import com.hbm.tileentity.machine.TileEntityCraftingStation.ConnectedInventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.common.util.ForgeDirection;

public class ContainerCraftingStation extends Container {

	private static final int OUTPUT_SLOT = 0;
	private static final int GRID_START = 1;
	private static final int GRID_END = 10;
	public static final int GUI_WIDTH = 376;
	public static final int GUI_HEIGHT = 218;
	public static final int STORAGE_X = 8;
	public static final int STORAGE_VIEW_Y = 28;
	public static final int STORAGE_VIEW_HEIGHT = 176;
	public static final int STORAGE_WIDTH = 162;
	public static final int SCROLLBAR_X = STORAGE_X + STORAGE_WIDTH + 4;
	public static final int CRAFTING_X = 210;
	public static final int PLAYER_INVENTORY_X = 204;
	public static final int PLAYER_INVENTORY_Y = 128;

	public final TileEntityCraftingStation station;
	public final InventoryCrafting craftMatrix;
	public final IInventory craftResult = new InventoryCraftResult();
	public final List<StorageSection> storageSections = new ArrayList<StorageSection>();
	public final int playerSlotStart;
	public final int playerInventoryY;
	public final int guiHeight;
	public final int storageContentHeight;
	public final int maxStorageScroll;
	private int storageScroll;

	public ContainerCraftingStation(InventoryPlayer playerInventory, TileEntityCraftingStation station) {
		this.station = station;
		this.craftMatrix = new StationInventoryCrafting(this, station);

		this.addSlotToContainer(new SlotCrafting(playerInventory.player, craftMatrix, craftResult, 0, 326, 64));
		for(int row = 0; row < 3; row++) for(int column = 0; column < 3; column++) {
			this.addSlotToContainer(new Slot(craftMatrix, column + row * 3, 216 + column * 18, 46 + row * 18));
		}

		int y = 0;
		for(ConnectedInventory connected : station.getConnectedInventories()) {
			IInventory inventory = connected.inventory;
			inventory.openInventory();

			int labelY = y;
			y += 12;
			int slotY = y;
			int size = inventory.getSizeInventory();
			int rows = (size + 8) / 9;
			int start = this.inventorySlots.size();
			ForgeDirection accessSide = connected.direction.getOpposite();

			for(int slot = 0; slot < size; slot++) {
				this.addSlotToContainer(new SlotConnectedInventory(inventory, slot, STORAGE_X + slot % 9 * 18, -1000, accessSide));
			}

			storageSections.add(new StorageSection(inventory, connected.direction, start, this.inventorySlots.size(), labelY, slotY));
			y += rows * 18 + 10;
		}

		this.storageContentHeight = y;
		this.maxStorageScroll = Math.max(0, storageContentHeight - STORAGE_VIEW_HEIGHT);
		this.playerInventoryY = PLAYER_INVENTORY_Y;
		this.playerSlotStart = this.inventorySlots.size();
		addPlayerInventory(playerInventory, playerInventoryY);
		this.guiHeight = GUI_HEIGHT;
		this.setStorageScroll(0);

		this.onCraftMatrixChanged(craftMatrix);
	}

	@Override
	public void onCraftMatrixChanged(IInventory inventory) {
		this.craftResult.setInventorySlotContents(0, CraftingManager.getInstance().findMatchingRecipe(this.craftMatrix, station.getWorldObj()));
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return station.isUseableByPlayer(player);
	}

	@Override
	public void onContainerClosed(EntityPlayer player) {
		super.onContainerClosed(player);
		for(StorageSection section : storageSections) section.inventory.closeInventory();
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		Slot slot = (Slot) this.inventorySlots.get(index);
		if(slot == null || !slot.getHasStack()) return null;

		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();

		if(index == OUTPUT_SLOT) {
			if(!this.mergeItemStack(stack, playerSlotStart, this.inventorySlots.size(), true)) return null;
			slot.onSlotChange(stack, original);
		} else if(index >= GRID_START && index < GRID_END) {
			if(!this.mergeItemStack(stack, playerSlotStart, this.inventorySlots.size(), false)) return null;
		} else if(index < playerSlotStart) {
			if(!this.mergeItemStack(stack, playerSlotStart, this.inventorySlots.size(), true)) return null;
		} else if(!this.mergeItemStack(stack, GRID_END, playerSlotStart, false)) {
			int playerHotbarStart = this.inventorySlots.size() - 9;
			if(index < playerHotbarStart) {
				if(!this.mergeItemStack(stack, playerHotbarStart, this.inventorySlots.size(), false)) return null;
			} else if(!this.mergeItemStack(stack, playerSlotStart, playerHotbarStart, false)) {
				return null;
			}
		}

		if(stack.stackSize == 0) slot.putStack(null);
		else slot.onSlotChanged();
		if(stack.stackSize == original.stackSize) return null;

		slot.onPickupFromSlot(player, stack);
		return original;
	}

	private void addPlayerInventory(InventoryPlayer inventory, int y) {
		for(int row = 0; row < 3; row++) for(int column = 0; column < 9; column++) {
			this.addSlotToContainer(new Slot(inventory, column + row * 9 + 9, PLAYER_INVENTORY_X + column * 18, y + row * 18));
		}
		for(int column = 0; column < 9; column++) this.addSlotToContainer(new Slot(inventory, column, PLAYER_INVENTORY_X + column * 18, y + 58));
	}

	public void setStorageScroll(int value) {
		storageScroll = Math.max(0, Math.min(maxStorageScroll, value));

		for(StorageSection section : storageSections) {
			for(int index = section.start; index < section.end; index++) {
				Slot slot = (Slot) this.inventorySlots.get(index);
				int relative = index - section.start;
				int slotY = STORAGE_VIEW_Y + section.slotY + relative / 9 * 18 - storageScroll;
				slot.xDisplayPosition = STORAGE_X + relative % 9 * 18;
				slot.yDisplayPosition = slotY + 18 > STORAGE_VIEW_Y && slotY < STORAGE_VIEW_Y + STORAGE_VIEW_HEIGHT ? slotY : -1000;
			}
		}
	}

	public void scrollStorage(int amount) {
		setStorageScroll(storageScroll + amount);
	}

	public int getStorageScroll() {
		return storageScroll;
	}

	public int getSectionLabelY(StorageSection section) {
		return STORAGE_VIEW_Y + section.labelY - storageScroll;
	}

	public boolean isSectionLabelVisible(StorageSection section) {
		int y = getSectionLabelY(section);
		return y >= STORAGE_VIEW_Y && y + 10 <= STORAGE_VIEW_Y + STORAGE_VIEW_HEIGHT;
	}

	public static class StorageSection {
		public final IInventory inventory;
		public final ForgeDirection direction;
		public final int start;
		public final int end;
		public final int labelY;
		public final int slotY;

		public StorageSection(IInventory inventory, ForgeDirection direction, int start, int end, int labelY, int slotY) {
			this.inventory = inventory;
			this.direction = direction;
			this.start = start;
			this.end = end;
			this.labelY = labelY;
			this.slotY = slotY;
		}
	}

	public static class SlotConnectedInventory extends Slot {
		private final ForgeDirection accessSide;

		public SlotConnectedInventory(IInventory inventory, int slot, int x, int y, ForgeDirection accessSide) {
			super(inventory, slot, x, y);
			this.accessSide = accessSide;
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			if(!inventory.isItemValidForSlot(getSlotIndex(), stack)) return false;
			if(!(inventory instanceof ISidedInventory)) return true;
			ISidedInventory sided = (ISidedInventory) inventory;
			return contains(sided.getAccessibleSlotsFromSide(accessSide.ordinal()), getSlotIndex()) && sided.canInsertItem(getSlotIndex(), stack, accessSide.ordinal());
		}

		@Override
		public boolean canTakeStack(EntityPlayer player) {
			if(!(inventory instanceof ISidedInventory)) return true;
			ISidedInventory sided = (ISidedInventory) inventory;
			ItemStack stack = getStack();
			return stack != null && contains(sided.getAccessibleSlotsFromSide(accessSide.ordinal()), getSlotIndex()) && sided.canExtractItem(getSlotIndex(), stack, accessSide.ordinal());
		}

		private boolean contains(int[] slots, int target) {
			for(int slot : slots) if(slot == target) return true;
			return false;
		}
	}

	private static class StationInventoryCrafting extends InventoryCrafting {
		private final ContainerCraftingStation container;
		private final TileEntityCraftingStation station;

		private StationInventoryCrafting(ContainerCraftingStation container, TileEntityCraftingStation station) {
			super(container, 3, 3);
			this.container = container;
			this.station = station;
		}

		@Override public ItemStack getStackInSlot(int slot) { return station.getStackInSlot(slot); }
		@Override public ItemStack getStackInSlotOnClosing(int slot) { return station.getStackInSlot(slot); }

		@Override
		public ItemStack decrStackSize(int slot, int amount) {
			ItemStack stack = station.decrStackSize(slot, amount);
			if(stack != null) container.onCraftMatrixChanged(this);
			return stack;
		}

		@Override
		public void setInventorySlotContents(int slot, ItemStack stack) {
			station.setInventorySlotContents(slot, stack);
			container.onCraftMatrixChanged(this);
		}

	}
}
