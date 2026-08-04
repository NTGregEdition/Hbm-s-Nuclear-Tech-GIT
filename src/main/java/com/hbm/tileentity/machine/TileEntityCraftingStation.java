package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.container.ContainerCraftingStation;
import com.hbm.inventory.gui.GUICraftingStation;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityCraftingStation extends TileEntityMachineBase implements IGUIProvider {

	public TileEntityCraftingStation() {
		super(9);
	}

	@Override
	public String getName() {
		return "container.craftingStation";
	}

	@Override
	public void updateEntity() {

	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return slot >= 0 && slot < slots.length;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		super.setInventorySlotContents(slot, stack);
		this.markDirty();
		if(worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		ItemStack stack = super.decrStackSize(slot, amount);
		if(stack != null) {
			this.markDirty();
			if(worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
		return stack;
	}

	@Override
	public Container provideContainer(int id, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerCraftingStation(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int id, EntityPlayer player, World world, int x, int y, int z) {
		return new GUICraftingStation(player.inventory, this);
	}

	public List<ConnectedInventory> getConnectedInventories() {
		List<ConnectedInventory> inventories = new ArrayList<ConnectedInventory>();
		if(worldObj == null) return inventories;

		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			int x = xCoord + direction.offsetX;
			int y = yCoord + direction.offsetY;
			int z = zCoord + direction.offsetZ;
			TileEntity tile = worldObj.getTileEntity(x, y, z);
			IInventory inventory = null;
			Block block = worldObj.getBlock(x, y, z);

			if(block instanceof BlockChest) {
				inventory = ((BlockChest) block).func_149951_m(worldObj, x, y, z);
			} else if(tile instanceof IInventory && tile != this) {
				inventory = (IInventory) tile;
			}

			if(inventory == null || inventory.getSizeInventory() <= 0 || contains(inventories, inventory)) continue;
			inventories.add(new ConnectedInventory(inventory, direction));
		}

		return inventories;
	}

	private boolean contains(List<ConnectedInventory> inventories, IInventory inventory) {
		for(ConnectedInventory entry : inventories) if(entry.inventory == inventory) return true;
		return false;
	}

	public static class ConnectedInventory {
		public final IInventory inventory;
		public final ForgeDirection direction;

		public ConnectedInventory(IInventory inventory, ForgeDirection direction) {
			this.inventory = inventory;
			this.direction = direction;
		}
	}
}
