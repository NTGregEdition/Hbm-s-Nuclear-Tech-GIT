package com.hbm.packet.toclient;

import com.hbm.main.DigammaApocalypseClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class PacketDigammaApocalypse implements IMessage {

	private int suckTicks;
	private int chargeTicks;
	private int aftermathTicks;
	private int elapsedTicks;
	private int dimensionId;
	private double quasarX;
	private double quasarY;
	private double quasarZ;

	public PacketDigammaApocalypse() { }

	public PacketDigammaApocalypse(int suckTicks, int chargeTicks, int aftermathTicks, int elapsedTicks, int dimensionId, double quasarX, double quasarY, double quasarZ) {
		this.suckTicks = suckTicks;
		this.chargeTicks = chargeTicks;
		this.aftermathTicks = aftermathTicks;
		this.elapsedTicks = elapsedTicks;
		this.dimensionId = dimensionId;
		this.quasarX = quasarX;
		this.quasarY = quasarY;
		this.quasarZ = quasarZ;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		suckTicks = buf.readInt();
		chargeTicks = buf.readInt();
		aftermathTicks = buf.readInt();
		elapsedTicks = buf.readInt();
		dimensionId = buf.readInt();
		quasarX = buf.readDouble();
		quasarY = buf.readDouble();
		quasarZ = buf.readDouble();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(suckTicks);
		buf.writeInt(chargeTicks);
		buf.writeInt(aftermathTicks);
		buf.writeInt(elapsedTicks);
		buf.writeInt(dimensionId);
		buf.writeDouble(quasarX);
		buf.writeDouble(quasarY);
		buf.writeDouble(quasarZ);
	}

	public static class Handler implements IMessageHandler<PacketDigammaApocalypse, IMessage> {

		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(PacketDigammaApocalypse m, MessageContext ctx) {
			DigammaApocalypseClient.begin(m.suckTicks, m.chargeTicks, m.aftermathTicks, m.elapsedTicks, m.dimensionId, m.quasarX, m.quasarY, m.quasarZ);
			return null;
		}
	}
}
