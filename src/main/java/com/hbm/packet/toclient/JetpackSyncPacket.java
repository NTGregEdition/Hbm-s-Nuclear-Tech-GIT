package com.hbm.packet.toclient;

import com.hbm.handler.JetpackHandler;
import com.hbm.handler.JetpackHandler.JetpackInfo;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Client -> server with the own state, server -> client with the state of a tracked player. */
public class JetpackSyncPacket implements IMessage {

	private int playerId;
	private JetpackInfo info;

	public JetpackSyncPacket() { }

	public JetpackSyncPacket(EntityPlayer player) {
		this.playerId = player.getEntityId();
		this.info = JetpackHandler.get(player);
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		playerId = buf.readInt();
		info = new JetpackInfo(false);
		info.read(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(playerId);
		info.write(buf);
	}

	public static class Handler implements IMessageHandler<JetpackSyncPacket, IMessage> {

		@Override
		public IMessage onMessage(JetpackSyncPacket m, MessageContext ctx) {
			if(ctx.side == Side.SERVER) {
				handleServer(m, ctx.getServerHandler().playerEntity);
			} else {
				handleClient(m);
			}
			return null;
		}

		private static void handleServer(JetpackSyncPacket m, EntityPlayer player) {
			JetpackInfo info = m.info;
			if(!player.isEntityAlive() || !JetpackHandler.hasJetpack(player)
					|| m.playerId != player.getEntityId()
					|| Float.isNaN(info.thrust) || Float.isInfinite(info.thrust) || info.thrust < 0F || info.thrust > 2F
					|| info.jetpackFlyTime < -1 || info.failureTicks < 0 || info.failureTicks > 80) return;

			JetpackHandler.put(player, info);
		}

		@SideOnly(Side.CLIENT)
		private static void handleClient(JetpackSyncPacket m) {
			EntityPlayer me = MainRegistry.proxy.me();
			if(me == null) return;

			World world = me.worldObj;
			Entity ent = world.getEntityByID(m.playerId);
			if(!(ent instanceof EntityPlayer)) return;

			EntityPlayer player = (EntityPlayer) ent;
			JetpackInfo info = JetpackHandler.get(player);
			if(info == null) {
				info = new JetpackInfo(true);
				JetpackHandler.put(player, info);
			}
			info.setFromServer(m.info);
		}
	}
}
