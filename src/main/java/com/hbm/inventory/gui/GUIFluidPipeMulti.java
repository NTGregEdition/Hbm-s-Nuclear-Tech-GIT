package com.hbm.inventory.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.lib.RefStrings;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.tileentity.network.TileEntityFluidPipe;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class GUIFluidPipeMulti extends GuiScreen {

	private static final ResourceLocation texture = new ResourceLocation(RefStrings.MODID + ":textures/gui/machine/gui_fluid_pipe.png");

	private static final int CELL = 18;
	private static final int COLUMNS = 9;
	private static final int ROWS = 4;
	private static final int GRID_X = 7;
	private static final int CHANNEL_Y = 18;
	private static final int SEARCH_Y = 40;
	private static final int GRID_Y = 62;
	private static final int BUTTON_Y = 140;

	private final int xSize = 176;
	private final int ySize = 166;
	private int guiLeft;
	private int guiTop;

	private final TileEntityFluidPipe pipe;
	private final List<FluidType> shown = new ArrayList<>();
	private final List<String> tooltip = new ArrayList<>();
	private GuiTextField search;
	private int selected;
	private int scroll;

	public GUIFluidPipeMulti(TileEntityFluidPipe pipe) {
		this.pipe = pipe;
	}

	@Override
	public void initGui() {
		super.initGui();
		this.guiLeft = (this.width - this.xSize) / 2;
		this.guiTop = (this.height - this.ySize) / 2;

		Keyboard.enableRepeatEvents(true);
		this.search = new GuiTextField(this.fontRendererObj, guiLeft + 46, guiTop + SEARCH_Y + 4, 86, 12);
		this.search.setTextColor(-1);
		this.search.setDisabledTextColour(-1);
		this.search.setEnableBackgroundDrawing(false);
		this.search.setFocused(true);

		this.buttonList.clear();
		this.buttonList.add(new GuiButton(0, guiLeft + 7, guiTop + BUTTON_Y, 80, 20, I18n.format("hbm.fluidpipe.gui.clearChannel")));
		this.buttonList.add(new GuiButton(1, guiLeft + 89, guiTop + BUTTON_Y, 80, 20, I18n.format("hbm.fluidpipe.gui.clearAll")));
		this.updateSearch();
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		this.search.updateCursorCounter();
		if(pipe.isInvalid() || mc.theWorld.getTileEntity(pipe.xCoord, pipe.yCoord, pipe.zCoord) != pipe) mc.thePlayer.closeScreen();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		this.drawDefaultBackground();
		this.drawBackgroundLayer();
		super.drawScreen(mouseX, mouseY, f);
		GL11.glDisable(GL11.GL_LIGHTING);
		this.drawForegroundLayer(mouseX, mouseY);
		GL11.glEnable(GL11.GL_LIGHTING);
	}

	private void drawBackgroundLayer() {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		mc.getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		if(search.isFocused()) drawTexturedModalRect(guiLeft + 43, guiTop + SEARCH_Y, 0, 170, 90, 18);

		int count = pipe.getChannelCount();
		for(int c = 0; c < count; c++) {
			int x = channelX(c);
			int y = guiTop + CHANNEL_Y;
			drawTexturedModalRect(x, y, 176, 36, CELL, CELL);
			drawDrop(pipe.getChannelType(c), x, y);
			if(c == selected) drawTexturedModalRect(x, y, 176, 0, CELL, CELL);
		}

		for(int i = 0; i < COLUMNS * ROWS; i++) {
			int index = scroll * COLUMNS + i;
			if(index >= shown.size()) break;

			FluidType type = shown.get(index);
			int x = guiLeft + GRID_X + (i % COLUMNS) * CELL;
			int y = guiTop + GRID_Y + (i / COLUMNS) * CELL;
			drawDrop(type, x, y);

			int holder = pipe.channelOf(type);
			if(holder >= 0) drawTexturedModalRect(x, y, 176, holder == selected ? 0 : 18, CELL, CELL);
		}
	}

	private void drawDrop(FluidType type, int cellX, int cellY) {
		if(type == Fluids.NONE) return;
		int color = type.getColor();
		GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
		drawTexturedModalRect(cellX + 5, cellY + 2, 176, 54, 8, 14);
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	private void drawForegroundLayer(int mouseX, int mouseY) {
		String title = I18n.format("hbm.fluidpipe.gui.title");
		fontRendererObj.drawString(title, guiLeft + (xSize - fontRendererObj.getStringWidth(title)) / 2, guiTop + 6, 4210752);
		search.drawTextBox();

		tooltip.clear();
		int channel = channelAt(mouseX, mouseY);
		int index = gridIndexAt(mouseX, mouseY);

		if(channel >= 0) {
			FluidType type = pipe.getChannelType(channel);
			tooltip.add(I18n.format("hbm.fluidpipe.gui.channel", channel + 1));
			tooltip.add(type == Fluids.NONE ? I18n.format("hbm.fluidpipe.empty") : type.getLocalizedName());
			tooltip.add(I18n.format("hbm.fluidpipe.gui.channelHint"));
		} else if(index >= 0) {
			FluidType type = shown.get(index);
			tooltip.add(type.getLocalizedName());
			type.addInfo(tooltip);
		}

		if(!tooltip.isEmpty()) func_146283_a(tooltip, mouseX, mouseY);
	}

	private int channelX(int channel) {
		return guiLeft + GRID_X + (COLUMNS - pipe.getChannelCount()) * CELL / 2 + channel * CELL;
	}

	private int channelAt(int x, int y) {
		if(y < guiTop + CHANNEL_Y || y >= guiTop + CHANNEL_Y + CELL) return -1;
		int relative = x - channelX(0);
		return relative >= 0 && relative < pipe.getChannelCount() * CELL ? relative / CELL : -1;
	}

	private int gridIndexAt(int x, int y) {
		int relX = x - (guiLeft + GRID_X);
		int relY = y - (guiTop + GRID_Y);
		if(relX < 0 || relY < 0 || relX >= COLUMNS * CELL || relY >= ROWS * CELL) return -1;
		int index = scroll * COLUMNS + relY / CELL * COLUMNS + relX / CELL;
		return index < shown.size() ? index : -1;
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);
		search.mouseClicked(x, y, button);

		int channel = channelAt(x, y);
		if(channel >= 0) {
			if(button == 1) setChannel(channel, Fluids.NONE);
			else selected = channel;
			click();
			return;
		}

		int index = gridIndexAt(x, y);
		if(index < 0 || button != 0) return;

		FluidType type = shown.get(index);
		int holder = pipe.channelOf(type);
		if(holder >= 0) selected = holder;
		else setChannel(selected, type);
		click();
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if(button.id == 0) {
			setChannel(selected, Fluids.NONE);
			return;
		}
		for(int c = 0; c < pipe.getChannelCount(); c++) setChannel(c, Fluids.NONE);
	}

	private void setChannel(int channel, FluidType fluid) {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("channel", channel);
		data.setInteger("fluid", fluid.getID());
		PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, pipe.xCoord, pipe.yCoord, pipe.zCoord));
	}

	private void click() {
		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if(wheel == 0) return;
		int maxScroll = Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
		scroll = Math.max(0, Math.min(maxScroll, scroll + (wheel > 0 ? -1 : 1)));
	}

	@Override
	protected void keyTyped(char c, int key) {
		if(search.textboxKeyTyped(c, key)) updateSearch();
		else super.keyTyped(c, key);

		if(key == 1) mc.thePlayer.closeScreen();
	}

	private void updateSearch() {
		shown.clear();
		scroll = 0;
		String subs = search.getText().toLowerCase(Locale.US);

		for(FluidType type : Fluids.getInNiceOrder()) {
			if(type == Fluids.NONE || type.hasNoID()) continue;
			if(type.getLocalizedName().toLowerCase(Locale.US).contains(subs)) shown.add(type);
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}
}
