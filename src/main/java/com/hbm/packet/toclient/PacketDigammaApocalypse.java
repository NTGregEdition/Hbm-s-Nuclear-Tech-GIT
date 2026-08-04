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
	private double quasarX;
	private double quasarY;
	private double quasarZ;

	public PacketDigammaApocalypse() { }

	public PacketDigammaApocalypse(int suckTicks, int chargeTicks, double quasarX, double quasarY, double quasarZ) {
		this.suckTicks = suckTicks;
		this.chargeTicks = chargeTicks;
		this.quasarX = quasarX;
		this.quasarY = quasarY;
		this.quasarZ = quasarZ;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		suckTicks = buf.readInt();
		chargeTicks = buf.readInt();
		quasarX = buf.readDouble();
		quasarY = buf.readDouble();
		quasarZ = buf.readDouble();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(suckTicks);
		buf.writeInt(chargeTicks);
		buf.writeDouble(quasarX);
		buf.writeDouble(quasarY);
		buf.writeDouble(quasarZ);
	}

	public static class Handler implements IMessageHandler<PacketDigammaApocalypse, IMessage> {

		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(PacketDigammaApocalypse m, MessageContext ctx) {
			DigammaApocalypseClient.begin(m.suckTicks, m.chargeTicks, m.quasarX, m.quasarY, m.quasarZ);
			return null;
		}
	}
}
