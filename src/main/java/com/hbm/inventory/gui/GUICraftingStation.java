package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerCraftingStation;
import com.hbm.inventory.container.ContainerCraftingStation.SlotConnectedInventory;
import com.hbm.inventory.container.ContainerCraftingStation.StorageSection;
import com.hbm.lib.RefStrings;
import com.hbm.tileentity.machine.TileEntityCraftingStation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GUICraftingStation extends GuiContainer {

	private static final ResourceLocation LEFT_TEXTURE = new ResourceLocation(RefStrings.MODID + ":textures/gui/processing/gui_crafting_station_left.png");
	private static final ResourceLocation RIGHT_TEXTURE = new ResourceLocation(RefStrings.MODID + ":textures/gui/processing/gui_crafting_station_right.png");
	private static final int SLOT_TEXTURE_X = 192;
	private static final int OUTPUT_SLOT_TEXTURE_X = 212;
	private static final int LEFT_PANEL_WIDTH = 192;
	private final ContainerCraftingStation container;
	private boolean draggingScrollbar;
	private int storageScrollTarget;

	public GUICraftingStation(InventoryPlayer playerInventory, TileEntityCraftingStation station) {
		super(new ContainerCraftingStation(playerInventory, station));
		this.container = (ContainerCraftingStation) this.inventorySlots;
		this.xSize = ContainerCraftingStation.GUI_WIDTH;
		this.ySize = container.guiHeight;
		this.storageScrollTarget = container.getStorageScroll();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		int currentScroll = container.getStorageScroll();
		if(currentScroll != storageScrollTarget) {
			int difference = storageScrollTarget - currentScroll;
			container.setStorageScroll(currentScroll + (difference > 0 ? Math.min(4, difference) : Math.max(-4, difference)));
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(LEFT_TEXTURE);
		drawTexturedModalRect(0, 0, 0, 0, LEFT_PANEL_WIDTH, ContainerCraftingStation.STORAGE_VIEW_Y);
		drawTexturedModalRect(0, ContainerCraftingStation.STORAGE_VIEW_Y + ContainerCraftingStation.STORAGE_VIEW_HEIGHT, 0, ContainerCraftingStation.STORAGE_VIEW_Y + ContainerCraftingStation.STORAGE_VIEW_HEIGHT, LEFT_PANEL_WIDTH, ySize - ContainerCraftingStation.STORAGE_VIEW_Y - ContainerCraftingStation.STORAGE_VIEW_HEIGHT);

		String title = I18n.format("container.craftingStation");
		this.fontRendererObj.drawString(title, this.xSize / 2 - this.fontRendererObj.getStringWidth(title) / 2, 6, 0xFFFFFF);
		this.fontRendererObj.drawString(I18n.format("container.craftingStation.storages"), 8, 10, 0xFFFFFF);
		this.fontRendererObj.drawString(I18n.format("container.craftingStation.crafting"), ContainerCraftingStation.CRAFTING_X, 28, 0xFFFFFF);

		for(StorageSection section : container.storageSections) {
			if(!container.isSectionLabelVisible(section)) continue;
			String name = section.inventory.hasCustomInventoryName() ? section.inventory.getInventoryName() : I18n.format(section.inventory.getInventoryName());
			this.fontRendererObj.drawString(I18n.format("container.craftingStation.connected", name) + EnumChatFormatting.DARK_GRAY + " (" + section.direction.name().toLowerCase() + ")", 8, container.getSectionLabelY(section), 0xFFFFFF);
		}

		this.fontRendererObj.drawString(I18n.format("container.inventory"), ContainerCraftingStation.PLAYER_INVENTORY_X, container.playerInventoryY - 12, 0xFFFFFF);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(LEFT_TEXTURE);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, LEFT_PANEL_WIDTH, ySize);
		Minecraft.getMinecraft().getTextureManager().bindTexture(RIGHT_TEXTURE);
		drawTexturedModalRect(guiLeft + LEFT_PANEL_WIDTH, guiTop, 0, 0, xSize - LEFT_PANEL_WIDTH, ySize);
		drawScrollbar();
		drawSlotFrames();
	}

	private void drawSlotFrames() {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(LEFT_TEXTURE);
		for(Object object : this.inventorySlots.inventorySlots) {
			Slot slot = (Slot) object;
			if(!(slot instanceof SlotConnectedInventory) || slot.yDisplayPosition < -500) continue;
			drawTexturedModalRect(guiLeft + slot.xDisplayPosition - 1, guiTop + slot.yDisplayPosition - 1, SLOT_TEXTURE_X, 0, 18, 18);
		}

		Minecraft.getMinecraft().getTextureManager().bindTexture(RIGHT_TEXTURE);
		for(int index = 0; index < this.inventorySlots.inventorySlots.size(); index++) {
			Slot slot = (Slot) this.inventorySlots.inventorySlots.get(index);
			if(slot instanceof SlotConnectedInventory) continue;
			drawTexturedModalRect(guiLeft + slot.xDisplayPosition - 1, guiTop + slot.yDisplayPosition - 1, index == 0 ? OUTPUT_SLOT_TEXTURE_X : SLOT_TEXTURE_X, 0, 18, 18);
		}
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		if(mc == null || Mouse.getEventButton() != -1) return;

		int wheel = Mouse.getEventDWheel();
		if(wheel == 0) return;
		int mouseX = Mouse.getEventX() * width / mc.displayWidth;
		int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
		if(isOverStoragePanel(mouseX, mouseY)) adjustStorageScroll(wheel > 0 ? -18 : 18);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if(button == 0 && isOverScrollbar(mouseX, mouseY)) {
			draggingScrollbar = true;
			setScrollFromMouse(mouseY);
			return;
		}
		if(isOverStorageColumn(mouseX) && !isOverStoragePanel(mouseX, mouseY)) return;
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int button, long heldTime) {
		super.mouseClickMove(mouseX, mouseY, button, heldTime);
		if(button == 0 && draggingScrollbar) setScrollFromMouse(mouseY);
	}

	@Override
	protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
		super.mouseMovedOrUp(mouseX, mouseY, button);
		if(button == 0) draggingScrollbar = false;
	}

	private boolean isOverStoragePanel(int mouseX, int mouseY) {
		return mouseX >= guiLeft + 4 && mouseX < guiLeft + 180 && mouseY >= guiTop + ContainerCraftingStation.STORAGE_VIEW_Y && mouseY < guiTop + ContainerCraftingStation.STORAGE_VIEW_Y + ContainerCraftingStation.STORAGE_VIEW_HEIGHT;
	}

	private boolean isOverStorageColumn(int mouseX) {
		return mouseX >= guiLeft + 4 && mouseX < guiLeft + LEFT_PANEL_WIDTH;
	}

	private boolean isOverScrollbar(int mouseX, int mouseY) {
		return mouseX >= guiLeft + ContainerCraftingStation.SCROLLBAR_X && mouseX < guiLeft + ContainerCraftingStation.SCROLLBAR_X + 7 && mouseY >= guiTop + ContainerCraftingStation.STORAGE_VIEW_Y && mouseY < guiTop + ContainerCraftingStation.STORAGE_VIEW_Y + ContainerCraftingStation.STORAGE_VIEW_HEIGHT;
	}

	private void setScrollFromMouse(int mouseY) {
		if(container.maxStorageScroll == 0) return;
		int track = ContainerCraftingStation.STORAGE_VIEW_HEIGHT - getScrollbarHeight();
		int position = MathHelper.clamp_int(mouseY - guiTop - ContainerCraftingStation.STORAGE_VIEW_Y - getScrollbarHeight() / 2, 0, track);
		storageScrollTarget = Math.round(position * (float) container.maxStorageScroll / track);
		container.setStorageScroll(storageScrollTarget);
	}

	private void adjustStorageScroll(int amount) {
		storageScrollTarget = MathHelper.clamp_int(storageScrollTarget + amount, 0, container.maxStorageScroll);
	}

	private int getScrollbarHeight() {
		if(container.maxStorageScroll == 0) return ContainerCraftingStation.STORAGE_VIEW_HEIGHT;
		return Math.max(20, ContainerCraftingStation.STORAGE_VIEW_HEIGHT * ContainerCraftingStation.STORAGE_VIEW_HEIGHT / Math.max(ContainerCraftingStation.STORAGE_VIEW_HEIGHT, container.storageContentHeight));
	}

	private void drawScrollbar() {
		int x = guiLeft + ContainerCraftingStation.SCROLLBAR_X;
		int y = guiTop + ContainerCraftingStation.STORAGE_VIEW_Y;
		int height = getScrollbarHeight();
		drawRect(x, y, x + 7, y + ContainerCraftingStation.STORAGE_VIEW_HEIGHT, 0x55000000);
		int travel = ContainerCraftingStation.STORAGE_VIEW_HEIGHT - height;
		int position = container.maxStorageScroll == 0 ? 0 : Math.round(container.getStorageScroll() * (float) travel / container.maxStorageScroll);
		drawRect(x + 1, y + position, x + 6, y + position + height, 0xFF9C7B3E);
		drawRect(x + 2, y + position + 1, x + 5, y + position + height - 1, 0xFF59421F);
	}
}
