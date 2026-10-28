package com.hbm.items.weapon;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hbm.handler.threading.PacketThreading;
import com.hbm.items.IAnimatedItem;
import com.hbm.items.IEquipReceiver;
import com.hbm.items.tool.ItemSwordAbility;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.packet.toserver.PacketMobSlicer;
import com.hbm.physics.Vec3d;
import com.hbm.render.anim.AnimationEnums.ToolAnimation;
import com.hbm.render.anim.BusAnimation;
import com.hbm.render.anim.BusAnimationSequence;
import com.hbm.render.anim.HbmAnimations;
import com.hbm.render.item.weapon.ItemRenderCrucible;
import com.hbm.util.CrucibleSlice;
import com.hbm.util.EntityDamageUtil;
import com.hbm.util.ShadyUtil;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class ItemCrucible extends ItemSwordAbility implements IEquipReceiver, IAnimatedItem<ToolAnimation> {

	//client only, the cut that is currently being drawn with the right mouse button
	public static Vec3d startPos;
	public static boolean clicked;
	public static boolean canClick = true;
	public static Vec3d planeNormal;
	public static float yaw, pitch;
	//the angle the blade was rolled to while the line was drawn, held for the swing after it
	public static double prevAngle;
	public static final float MAX_DYAW = 120;
	public static final float MAX_DPITCH = 120;

	//server only, the world time of each player's latest cut so that its swing does not use up a second charge
	private static final Map<EntityPlayer, Long> cutSwings = new WeakHashMap<EntityPlayer, Long>();

	public ItemCrucible(float damage, double movement, ToolMaterial material) {
		super(damage, movement, material);
	}

	@Override
	public void onEquip(EntityPlayer player, ItemStack stack) {

		if(!(player instanceof EntityPlayerMP))
			return;

		playAnimation(player, ToolAnimation.EQUIP);

		if(player.getHeldItem() != null && player.getHeldItem().getItemDamage() < player.getHeldItem().getMaxDamage()) {

			World world = player.worldObj;
			world.playSoundEffect(player.posX, player.posY, player.posZ, "hbm:weapon.cDeploy", 1.0F, 1.0F);
		}
	}

	public static boolean isCharged(ItemStack stack) {
		return stack.getItemDamage() < stack.getMaxDamage();
	}

	public static void playSliceSound(Entity at) {
		at.worldObj.playSoundEffect(at.posX, at.posY, at.posZ, "hbm:weapon.crucible_swing", 1.0F, 1.0F);
	}

	public static void markCutSwing(EntityPlayer player) {
		cutSwings.put(player, player.worldObj.getTotalWorldTime());
	}

	private static boolean isCutSwing(EntityLivingBase entity) {
		Long tick = cutSwings.get(entity);
		return tick != null && entity.worldObj.getTotalWorldTime() - tick <= 2;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if(world.isRemote) rightClickClient(player);
		return stack;
	}

	@SideOnly(Side.CLIENT)
	private void rightClickClient(EntityPlayer player) {

		if(player != Minecraft.getMinecraft().thePlayer || clicked || !canClick)
			return;

		Vec3 look = player.getLookVec();
		startPos = new Vec3d(look.xCoord, look.yCoord, look.zCoord);
		planeNormal = null;
		clicked = true;
		canClick = false;
		yaw = player.rotationYaw;
		pitch = player.rotationPitch;
	}

	@SideOnly(Side.CLIENT)
	private void swingClient(EntityLivingBase entity, ItemStack stack) {

		if(entity != Minecraft.getMinecraft().thePlayer || !clicked || planeNormal == null)
			return;

		Vec3 eye = EntityDamageUtil.getPosition((EntityPlayer) entity);
		PacketDispatcher.wrapper.sendToServer(new PacketMobSlicer(startPos.add(eye.xCoord, eye.yCoord, eye.zCoord), planeNormal));
		planeNormal = null;
		clicked = false;

		playSwing(0.8F + itemRand.nextFloat() * 0.2F);
		ItemRenderCrucible.layerAnimation((EntityPlayer) entity, stack, new BusAnimation().addBus("CUT_SWING", new BusAnimationSequence().addPos(120, 0, 0, 150).addPos(0, 0, 0, 500)));
	}

	@Override
	public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack) {

		if(entityLiving.worldObj.isRemote)
			swingClient(entityLiving, stack);

		if(!(entityLiving instanceof EntityPlayerMP))
			return false;

		if(entityLiving instanceof EntityPlayer && ((EntityPlayer)entityLiving).getUniqueID().toString().equals(ShadyUtil.Tankish)) {
			stack.setItemDamage(0);
		}

		if(stack.getItemDamage() >= stack.getMaxDamage())
			return false;

		//the client plays the cut's own swing, the regular one would replace it
		if(isCutSwing(entityLiving))
			return false;

		playAnimation((EntityPlayerMP)entityLiving, ToolAnimation.SWING);

		return false;
	}
	
	@Override
	public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {

		if(!isCharged(stack) || isCutSwing(player) || !(entity instanceof EntityLivingBase) || !entity.isEntityAlive() || entity instanceof EntityDragon || !CrucibleSlice.canSlice((EntityLivingBase) entity, player))
			return super.onLeftClickEntity(stack, player, entity);

		if(!player.worldObj.isRemote) {
			EntityLivingBase victim = (EntityLivingBase) entity;

			float yaw = player.rotationYaw * (float) Math.PI / 180F;
			Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0D, -MathHelper.sin(yaw));
			Vec3 lookVec = player.getLookVec();
			Vec3d up = right.crossProduct(new Vec3d(lookVec.xCoord, lookVec.yCoord, lookVec.zCoord));
			//right and up are perpendicular to the view and to each other, so the plane through the view and their diagonal has this normal
			Vec3d normal = right.subtract(up).scale(Math.sqrt(0.5D));

			stack.damageItem(1, player);
			CrucibleSlice.slice(victim, new Vec3d(victim.posX, victim.posY + victim.height * 0.5D, victim.posZ), normal);
			playSliceSound(victim);
		}

		return true;
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase victim, EntityLivingBase attacker) {

		boolean active = stack.getItemDamage() < stack.getMaxDamage();

		if(active) {

			attacker.worldObj.playSoundEffect(victim.posX, victim.posY, victim.posZ, "mob.zombie.woodbreak", 1.0F, 0.75F + victim.getRNG().nextFloat() * 0.2F);

			if(!attacker.worldObj.isRemote && !victim.isEntityAlive()) {
				int count = Math.min((int)Math.ceil(victim.getMaxHealth() / 3D), 250);

				NBTTagCompound data = new NBTTagCompound();
				data.setString("type", "vanillaburst");
				data.setInteger("count", count * 4);
				data.setDouble("motion", 0.1D);
				data.setString("mode", "blockdust");
				data.setInteger("block", Block.getIdFromBlock(Blocks.redstone_block));
				PacketThreading.createAllAroundThreadedPacket(new AuxParticlePacketNT(data, victim.posX, victim.posY + victim.height * 0.5, victim.posZ), new TargetPoint(victim.dimension, victim.posX, victim.posY + victim.height * 0.5, victim.posZ, 50));
			}

			if(attacker instanceof EntityPlayer && (((EntityPlayer)attacker).getDisplayName().equals("Tankish") || ((EntityPlayer)attacker).getDisplayName().equals("Tankish020")))
				return true;

			if(isCutSwing(attacker))
				return true;

			return super.hitEntity(stack, victim, attacker);
		} else {

			if(!attacker.worldObj.isRemote && attacker instanceof EntityPlayer)
				((EntityPlayer)attacker).addChatComponentMessage(new ChatComponentText("Not enough energy.").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
			return false;
		}
	}

	public Multimap getAttributeModifiers(ItemStack stack) {

		Multimap multimap = HashMultimap.create();

		if(stack.getItemDamage() < stack.getMaxDamage()) {
			multimap.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Tool modifier", (double) this.damage, 0));
			multimap.put(SharedMonsterAttributes.movementSpeed.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Tool modifier", movement, 1));
		}

		return multimap;
	}

	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {

		String charge = EnumChatFormatting.RED + "Charge [";

		for(int i = 9; i >= 0; i--)
			if(stack.getItemDamage() <= i)
				charge += "||||||";
			else
				charge += "   ";

		charge += "]";

		list.add(charge);
	}

	//both animations are layered onto the Blender rig of the renderer instead of being handed back, handing one back would replace the rig
	@Override
	public BusAnimation getAnimation(ToolAnimation type, ItemStack stack) {
		animateClient(type, stack);
		return null;
	}

	//has to stay out of getAnimation itself, that one exists on the server too and must not mention anything client side
	@SideOnly(Side.CLIENT)
	private void animateClient(ToolAnimation type, ItemStack stack) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if(player == null || stack == null) return;

		/* crucible deploy */
		if(type == ToolAnimation.EQUIP) {
			ItemRenderCrucible.startEquip(player, stack);
		}

		/* crucible swing */
		if(type == ToolAnimation.SWING) {

			if(HbmAnimations.getRelevantTransformation("SWING_ROT")[0] == 0) {

				int offset = itemRand.nextInt(80) - 20;

				playSwing(0.8F + itemRand.nextFloat() * 0.2F);

				ItemRenderCrucible.layerAnimation(player, stack, new BusAnimation()
						.addBus("SWING_ROT", new BusAnimationSequence()
								.addPos(60 - offset, 60 - offset, -55, 75)
								.addPos(60 + offset, 60 - offset, -45, 150)
								.addPos(0, 0, 0, 500))
						.addBus("SWING_TRANS", new BusAnimationSequence()
								.addPos(0, -10, 0, 75)
								.addPos(0, -10, 0, 150)
								.addPos(0, 0, 0, 500)));
			}
		}
	}

	// could do this better, but this preserves existing behaviour the closest with the least amount
	// of effort, without crashing servers (I'm learning my lesson :o_ )
	@SideOnly(Side.CLIENT)
	private void playSwing(float pitchProbablyIDontFuckingCare) {
		Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("hbm:weapon.cSwing"), pitchProbablyIDontFuckingCare));
	}

	@Override
	public Class<ToolAnimation> getEnum() {
		return ToolAnimation.class;
	}

	@Override
	public boolean shouldPlayerModelAim(ItemStack stack) {
		return false;
	}

}
