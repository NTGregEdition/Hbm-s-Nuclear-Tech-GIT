package com.hbm.inventory.gui;

import org.lwjgl.input.Keyboard;

import com.hbm.blocks.network.FlowControlPump.TileEntityFlowControlPump;
import com.hbm.blocks.network.FluidPump.TileEntityFluidPump;
import com.hbm.blocks.network.PumpUpgrades;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.util.BobMathUtil;
import com.hbm.util.EnumUtil;
import com.hbm.util.i18n.I18nUtil;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

@SideOnly(Side.CLIENT)
public class GUIPump extends GuiScreen {

	protected final TileEntityFlowControlPump pump;

	private GuiTextField textPlacementPriority;
	private GuiButton buttonPressure;
	private GuiButton buttonPriority;
	private int pressure;
	private int priority;

	public GUIPump(TileEntityFlowControlPump pump) {
		this.pump = pump;
		this.pressure = pump.tank[0].getPressure();
		this.priority = pump.priority.ordinal();
	}

	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);

		textPlacementPriority = new GuiTextField(fontRendererObj, this.width / 2 - 150, 100, 90, 20);
		textPlacementPriority.setText("" + pump.bufferSize);
		textPlacementPriority.setMaxStringLength(7);

		buttonPressure = new GuiButton(0, this.width / 2 - 50, 100, 90, 20, pressure + " PU");

		buttonPriority = new GuiButton(1, this.width / 2 + 50, 100, 90, 20, pump.priority.name());
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();

		drawString(fontRendererObj, "Throughput:", this.width / 2 - 150, 80, 0xA0A0A0);
		drawString(fontRendererObj, "(max. " + BobMathUtil.format(pump.getMaxFlow()) + "mB/t)", this.width / 2 - 150, 90, 0xA0A0A0);
		textPlacementPriority.drawTextBox();

		drawString(fontRendererObj, "Pressure:", this.width / 2 - 50, 80, 0xA0A0A0);
		buttonPressure.drawButton(mc, mouseX, mouseY);

		drawString(fontRendererObj, "Priority:", this.width / 2 + 50, 80, 0xA0A0A0);
		buttonPriority.drawButton(mc, mouseX, mouseY);

		int colour = pump.regulator == PumpUpgrades.NONE ? 0xFF5555 : 0xA0A0A0;
		String regulator = pump.regulator == PumpUpgrades.NONE ? I18nUtil.resolveKey("hbm.pump.noRegulator") : I18nUtil.resolveKey("hbm.pump.regulator", PumpUpgrades.tierName(pump.regulator), BobMathUtil.format(pump.getMaxFlow()));
		drawString(fontRendererObj, regulator, this.width / 2 - 150, 140, colour);

		if(pump.regulator != PumpUpgrades.NONE) {
			boolean withinLimit = pump.isWithinPipeLimit();
			drawString(fontRendererObj, I18nUtil.resolveKey("hbm.pump.pipes", BobMathUtil.format(pump.getPipes()), BobMathUtil.format(pump.getPipeLimit())), this.width / 2 - 150, 150, withinLimit ? 0x55FF55 : 0xFF5555);
			drawString(fontRendererObj, I18nUtil.resolveKey("hbm.pump.buffer", BobMathUtil.format(pump.power), BobMathUtil.format(pump.getMaxPower())), this.width / 2 - 150, 160, pump.running ? 0x55FF55 : 0xFF5555);
			drawString(fontRendererObj, I18nUtil.resolveKey("hbm.pump.draw", BobMathUtil.format(pump.getEnergyDemand())), this.width / 2 - 150, 170, 0xA0A0A0);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);

		NBTTagCompound data = new NBTTagCompound();

		data.setByte("pressure", (byte) pressure);
		data.setByte("priority", (byte) priority);

		try { data.setInteger("capacity", Integer.parseInt(textPlacementPriority.getText())); } catch(Exception ex) {}

		PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, pump.xCoord, pump.yCoord, pump.zCoord));
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		super.keyTyped(typedChar, keyCode);
		if(textPlacementPriority.textboxKeyTyped(typedChar, keyCode)) return;

		if(keyCode == 1 || keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
			this.mc.thePlayer.closeScreen();
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		textPlacementPriority.mouseClicked(mouseX, mouseY, mouseButton);

		if(buttonPressure.mousePressed(mc, mouseX, mouseY)) {
			this.pressure++;
			if(pressure > 5) pressure = 0;
			buttonPressure.displayString = pressure + " PU";
			mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
		}

		if(buttonPriority.mousePressed(mc, mouseX, mouseY)) {
			this.priority++;
			if(priority >= ConnectionPriority.values().length) priority = 0;
			buttonPriority.displayString = EnumUtil.grabEnumSafely(ConnectionPriority.class, priority).name();
			mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
		}
	}

	@Override public boolean doesGuiPauseGame() { return false; }
}