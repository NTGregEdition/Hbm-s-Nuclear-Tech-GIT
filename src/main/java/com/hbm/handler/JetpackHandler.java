package com.hbm.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.hbm.items.armor.JetpackGlider;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.JetpackSyncPacket;
import com.hbm.particle.JetpackParticles;
import com.hbm.render.util.ColorGradient;
import com.hbm.sound.MovingSoundJetpack;
import com.hbm.util.BobMathUtil;
import com.hbm.util.TrackerUtil;

import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.MovementInput;

/** Logic of the glider jetpack armor mod. Rendering lives in the client-only JetpackRenderer. */
public class JetpackHandler {

	public static final String JETPACK_NBT = "hbmJetpackAdvanced";

	private static final int CHEST = 2;
	private static final String GLIDER_SLOT = ArmorModHandler.MOD_SLOT_KEY + ArmorModHandler.plate_only;

	private static boolean jet_key_down = false;
	private static boolean hover_key_down = false;
	private static boolean hud_key_down = false;

	/** Client and server players are different objects, so one identity map can serve both sides. */
	private static final Map<EntityPlayer, JetpackInfo> perPlayerInfo = Collections.synchronizedMap(new IdentityHashMap<EntityPlayer, JetpackInfo>());

	public static JetpackInfo get(EntityPlayer p) {
		return perPlayerInfo.get(p);
	}

	public static void put(EntityPlayer p, JetpackInfo j) {
		perPlayerInfo.put(p, j);
	}

	private static JetpackInfo getOrCreate(EntityPlayer p) {
		JetpackInfo j = get(p);
		if(j == null) {
			j = new JetpackInfo(p.worldObj.isRemote);
			put(p, j);
		}
		return j;
	}

	public static boolean hasJetpack(EntityPlayer p) {
		ItemStack chest = p.inventory.armorInventory[CHEST];
		if(chest == null || !ArmorModHandler.hasMods(chest)) return false;
		NBTTagCompound slot = chest.getTagCompound().getCompoundTag(ArmorModHandler.MOD_COMPOUND_KEY).getCompoundTag(GLIDER_SLOT);
		return slot.getShort("id") == Item.getIdFromItem(ModItems.jetpack_glider);
	}
	
	private static void refreshFuel(EntityPlayer p, JetpackInfo j) {
		j.fuelType = Fluids.NONE;
		j.fuel = 0;
		j.fuelMax = 0;
		if(!hasJetpack(p)) return;

		ItemStack glider = ArmorModHandler.pryMod(p.inventory.armorInventory[CHEST], ArmorModHandler.plate_only);
		if(glider == null || !(glider.getItem() instanceof JetpackGlider)) return;

		FluidTank tank = ((JetpackGlider) glider.getItem()).getTank(glider);
		j.fuelType = tank.getTankType();
		j.fuel = tank.getFill();
		j.fuelMax = tank.getMaxFill();
	}

	private static void drainFuel(EntityPlayer p, int amount) {
		if(amount <= 0) return;

		ItemStack chest = p.inventory.armorInventory[CHEST];
		ItemStack glider = ArmorModHandler.pryMod(chest, ArmorModHandler.plate_only);
		if(glider == null || !(glider.getItem() instanceof JetpackGlider)) return;

		JetpackGlider item = (JetpackGlider) glider.getItem();
		FluidTank tank = item.getTank(glider);
		tank.setFill(Math.max(0, tank.getFill() - amount));
		if(tank.getFill() == 0) tank.setTankType(Fluids.NONE);
		item.setTank(glider, tank);
		ArmorModHandler.applyMod(chest, glider);
	}

	public static float getSpeed(FluidType type) {
		if(type == Fluids.KEROSENE) return 0.3F;
		if(type == Fluids.KEROSENE_REFORM) return 0.4F;
		if(type == Fluids.NITAN) return 0.5F;
		if(type == Fluids.BALEFIRE) return 1.5F;
		return 0;
	}
	
	public static int getDrain(FluidType type) {
		if(type == Fluids.KEROSENE || type == Fluids.NITAN || type == Fluids.BALEFIRE || type == Fluids.KEROSENE_REFORM) return 1;
		return 0;
	}
	
	private static final float[] keroseneColor = new float[] {1, 0.6F, 0.5F};
	private static final float[] nitanColor = new float[] {1F, 0.5F, 1F};
	private static final float[] bfColor = new float[] {0.4F, 1, 0.7F};
	private static final float[] keroseneReformColor = new float[] {0.55F, 0.75F, 0.95F};

	private static final ColorGradient keroseneGradient = new ColorGradient()
			.addColor(1, 0.918F, 0.882F, 1, 0)
			.addColor(0.887F, 1, 0, 1, 0.177F)
			.addColor(1, 0.19F, 0, 1, 0.336F)
			.addColor(1, 0.14F, 0, 1, 0.85F)
			.addColor(1, 0.14F, 0, 0, 1);
	private static final ColorGradient nitanGradient = new ColorGradient()
			.addColor(0.845F, 0.779F, 1F, 1, 0)
			.addColor(1F, 0.3F, 1F, 1, 0.122F)
			.addColor(0.7F, 0.4F, 1F, 1, 0.389F)
			.addColor(0.35F, 0.2F, 1F, 1, 0.891F)
			.addColor(0.1F, 0.1F, 1F, 0, 1);
	private static final ColorGradient bfGradient = new ColorGradient()
			.addColor(1F, 0.95F, 0.279F, 1, 0)
			.addColor(1F, 0.9F, 0.1F, 1, 0.122F)
			.addColor(0.013F, 1F, 0.068F, 1, 0.389F)
			.addColor(0.2F, 1F, 0.3F, 1, 0.891F)
			.addColor(0, 1F, 0.4F, 0, 1);
	private static final ColorGradient keroseneReformGradient = new ColorGradient()
			.addColor(1F, 0.95F, 0.85F, 1, 0)
			.addColor(0.55F, 0.75F, 1F, 1, 0.15F)
			.addColor(0.25F, 0.55F, 0.9F, 1, 0.4F)
			.addColor(0.15F, 0.3F, 0.7F, 1, 0.8F)
			.addColor(0.1F, 0.05F, 0.2F, 0, 1);

	public static ColorGradient getGradientFromFuel(FluidType fuel) {
		if(fuel == Fluids.BALEFIRE) return bfGradient;
		if(fuel == Fluids.NITAN) return nitanGradient;
		if(fuel == Fluids.KEROSENE_REFORM) return keroseneReformGradient;
		return keroseneGradient;
	}

	public static float[] getBrightnessColorFromFuel(FluidType fuel) {
		if(fuel == Fluids.BALEFIRE) return bfColor;
		if(fuel == Fluids.NITAN) return nitanColor;
		if(fuel == Fluids.KEROSENE_REFORM) return keroseneReformColor;
		return keroseneColor;
	}

	/** Runs after vanilla has read the keys, replaces the work of the 1.12.2 InputUpdateEvent. Not cheat protected on purpose. */
	@SideOnly(Side.CLIENT)
	public static void inputUpdate(EntityPlayer player, MovementInput m) {
		if(!hasJetpack(player)) return;

		JetpackInfo info = get(player);
		if(info == null) {
			info = new JetpackInfo(true);
			put(player, info);
			info.dirty = true;
			refreshFuel(player, info);
		}

		boolean jKey = HbmKeybinds.gliderActivateKey.getIsKeyPressed();
		if(jKey && !jet_key_down && System.currentTimeMillis() - getAnimTime(player) > 1000) {
			toggleOpenState(player);
		}
		jet_key_down = jKey;

		boolean hKey = HbmKeybinds.gliderHoverKey.getIsKeyPressed();
		if(hKey && !hover_key_down) {
			toggleHoverState(player);
		}
		hover_key_down = hKey;

		boolean hudKey = HbmKeybinds.gliderHudKey.getIsKeyPressed();
		if(hudKey && !hud_key_down) {
			toggleHUDState(player);
		}
		hud_key_down = hudKey;

		float thrust = info.thrust;
		if(jetpackActive(player) && player.isInWater()) {
			info.failureTicks = 80;
		}

		if(jetpackActive(player) && !player.onGround && info.failureTicks <= 0 && info.fuel > 0) {
			float speed = getSpeed(info.fuelType);
			player.capabilities.isFlying = false;
			boolean sprint = Minecraft.getMinecraft().gameSettings.keyBindSprint.getIsKeyPressed();
			if(player.isSprinting()) player.setSprinting(sprint);

			if(isHovering(player)) {
				m.moveForward *= (player.isSprinting() ? 0.17 : 0.1) * speed;
				m.moveStrafe *= 0.1F * speed;
				player.motionX -= MathHelper.sin((float) Math.toRadians(player.rotationYawHead)) * m.moveForward;
				player.motionZ += MathHelper.cos((float) Math.toRadians(player.rotationYawHead)) * m.moveForward;
				player.motionX -= MathHelper.sin((float) Math.toRadians(player.rotationYawHead - 90)) * m.moveStrafe;
				player.motionZ += MathHelper.cos((float) Math.toRadians(player.rotationYawHead - 90)) * m.moveStrafe;
				player.motionY *= 0.75;
				player.motionY += 0.05;
				float extraMY = 0;
				if(m.jump) {
					m.jump = false;
					extraMY += 0.3 * speed;
				}
				if(m.sneak) {
					m.sneak = false;
					extraMY -= Math.min(0.3, 0.3 * speed);
				}
				player.motionY += extraMY;
				float diff = (Math.abs(m.moveForward) + Math.abs(m.moveStrafe) + extraMY + 0.4F) - thrust;
				setThrust(player, thrust + diff * 0.3F);
				m.moveForward = sprint ? 0.81F : 0;
				m.moveStrafe = 0;
			} else {
				float boost = m.jump ? speed : 0;
				float diff = MathHelper.clamp_float(BobMathUtil.remap(boost, 0, 2, 0F, 4), 0, 2) - thrust;
				setThrust(player, thrust + diff * 0.2F);
				Vec3 look = player.getLookVec();
				if(m.jump) {
					player.motionX += look.xCoord * boost * 0.5F;
					player.motionY += look.yCoord * boost * 0.5F;
					if(look.yCoord > 0) {
						player.motionY += look.yCoord * 0.08F;
					}
					player.motionZ += look.zCoord * boost * 0.5F;
				}
				m.moveForward = 0;
				m.moveStrafe = 0;

				// same behavior as the lunar glider, kept consistent on purpose
				if(player.motionY < -0.08) {
					double mo = player.motionY * -0.4;
					player.motionY += mo * (1 - Math.abs(look.yCoord));
					player.motionX += look.xCoord * mo;
					player.motionY += look.yCoord * mo * (1 - Math.abs(look.yCoord));
					player.motionZ += look.zCoord * mo;
				}
			}
			info.prevThrust = thrust;
		} else {
			float diff = -thrust;
			setThrust(player, thrust + diff * 0.3F);
			info.prevThrust = thrust;
		}
	}

	public static void serverTick() {
		synchronized(perPlayerInfo) {
			Iterator<Map.Entry<EntityPlayer, JetpackInfo>> itr = perPlayerInfo.entrySet().iterator();
			while(itr.hasNext()) {
				Map.Entry<EntityPlayer, JetpackInfo> e = itr.next();
				EntityPlayer player = e.getKey();
				if(player.isDead) {
					itr.remove();
					continue;
				}
				if(player.worldObj.isRemote) continue;

				JetpackInfo info = e.getValue();
				refreshFuel(player, info);
				if(jetpackActive(player)) {
					int drain = (int) Math.ceil(getDrain(info.fuelType) * info.thrust);
					if(info.thrust < 0.0001) drain = 0;
					drainFuel(player, drain);
				}
				if(jetpackActive(player) && player.motionY > -0.5) player.fallDistance = 0;
				if(hasJetpack(player)) sendToTracking(player);
			}
		}
	}

	private static void sendToTracking(EntityPlayer player) {
		EntityTrackerEntry entry = TrackerUtil.getTrackerEntry((WorldServer) player.worldObj, player.getEntityId());
		if(entry == null || entry.trackingPlayers.isEmpty()) return;

		JetpackSyncPacket packet = new JetpackSyncPacket(player);
		for(Object tracking : entry.trackingPlayers) PacketDispatcher.wrapper.sendTo(packet, (EntityPlayerMP) tracking);
	}

	public static void playerLoggedIn(PlayerLoggedInEvent e) {
		EntityPlayer player = e.player;
		if(loadNBT(player)) {
			PacketDispatcher.wrapper.sendTo(new JetpackSyncPacket(player), (EntityPlayerMP) player);
		}
	}

	/** @return whether a saved state was found */
	public static boolean loadNBT(EntityPlayer player) {
		ItemStack stack = player.inventory.armorInventory[CHEST];
		if(stack == null || !stack.hasTagCompound() || !stack.getTagCompound().hasKey(JETPACK_NBT)) return false;

		JetpackInfo j = new JetpackInfo(player.worldObj.isRemote);
		j.readFromNBT(stack.getTagCompound().getCompoundTag(JETPACK_NBT));
		put(player, j);
		return true;
	}

	public static void saveNBT(EntityPlayer player) {
		if(!hasJetpack(player)) return;
		JetpackInfo info = get(player);
		if(info == null) return;

		ItemStack stack = player.inventory.armorInventory[CHEST];
		if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		stack.getTagCompound().setTag(JETPACK_NBT, info.writeToNBT(new NBTTagCompound()));
	}

	public static void worldSave(WorldEvent.Save e) {
		for(Object o : e.world.playerEntities) {
			saveNBT((EntityPlayer) o);
		}
	}

	public static void postPlayerTick(EntityPlayer player) {
		JetpackInfo j = get(player);
		if(j == null) return;

		boolean remote = player.worldObj.isRemote;
		if(jetpackActive(player) && !player.onGround && j.failureTicks <= 0 && j.fuel > 0) {
			if(isHovering(player)) {
				if(j.jetpackFlyTime >= 0 && remote) j.jetpackFlyTime = -1;
			} else if(remote) {
				j.jetpackFlyTime++;
			}
		} else if(j.jetpackFlyTime >= 0 && remote) {
			j.jetpackFlyTime = -1;
		}
	}

	@SideOnly(Side.CLIENT)
	public static void clientTick(ClientTickEvent e) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityClientPlayerMP me = mc.thePlayer;
		if(me == null) return;

		if(e.phase == Phase.END) {
			for(Object o : me.worldObj.playerEntities) {
				EntityPlayer player = (EntityPlayer) o;
				if(jetpackActive(player) && !player.onGround) {
					player.limbSwingAmount = 0;
					player.limbSwing = 0;
					player.prevLimbSwingAmount = 0;
				}
			}
			return;
		}
		if(e.phase != Phase.START) return;

		if(me.movementInput.getClass() == MovementInputFromOptions.class) {
			me.movementInput = new JetpackMovementInput(mc.gameSettings);
		}

		synchronized(perPlayerInfo) {
			Iterator<Map.Entry<EntityPlayer, JetpackInfo>> itr = perPlayerInfo.entrySet().iterator();
			while(itr.hasNext()) {
				Map.Entry<EntityPlayer, JetpackInfo> entry = itr.next();
				EntityPlayer player = entry.getKey();
				JetpackInfo j = entry.getValue();
				if(player.isDead) {
					if(j.sound != null) j.sound.end();
					itr.remove();
					continue;
				}
				if(!player.worldObj.isRemote) continue;

				refreshFuel(player, j);
				updateSound(player, j, mc);

				if(player == me) {
					if(j.failureTicks > 0) j.dirty = true;
					j.failureTicks = Math.max(0, j.failureTicks - 1);
				}
				j.prevPrevPosX = player.prevPosX;
				j.prevPrevPosZ = player.prevPosZ;

				updateParticles(j);
				spawnParticles(player, j);
				player.ignoreFrustumCheck = true;

				if(player == me && j.dirty) {
					PacketDispatcher.wrapper.sendToServer(new JetpackSyncPacket(player));
					j.dirty = false;
				}
			}
		}
	}

	@SideOnly(Side.CLIENT)
	private static void updateSound(EntityPlayer player, JetpackInfo j, Minecraft mc) {
		if(j.thrust > 0.001 && hasJetpack(player)) {
			if(j.sound == null) {
				j.sound = new MovingSoundJetpack(player);
				mc.getSoundHandler().playSound(j.sound);
			}
		} else if(j.sound != null) {
			j.sound.end();
			j.sound = null;
		}
	}

	@SideOnly(Side.CLIENT)
	private static void updateParticles(JetpackInfo j) {
		for(int i = j.plasma.size() - 1; i >= 0; i--) if(!j.plasma.get(i).update()) j.plasma.remove(i);
		for(int i = j.haze.size() - 1; i >= 0; i--) if(!j.haze.get(i).update()) j.haze.remove(i);
		for(int i = j.glow.size() - 1; i >= 0; i--) if(!j.glow.get(i).update()) j.glow.remove(i);
		for(int i = j.trails.size() - 1; i >= 0; i--) {
			JetpackParticles.Trail trail = j.trails.get(i);
			if(trail.update()) continue;
			j.trails.remove(i);
			if(j.trailLeft == trail) j.trailLeft = null;
			if(j.trailRight == trail) j.trailRight = null;
		}
	}

	@SideOnly(Side.CLIENT)
	private static void spawnParticles(EntityPlayer player, JetpackInfo j) {
		if(!jetpackActive(player) || player.onGround || j.fuel <= 0 || j.fuelType == Fluids.NONE) return;
		if(j.thrust <= 0.05F) return;

		ColorGradient grad = getGradientFromFuel(j.fuelType);
		float[] color = getBrightnessColorFromFuel(j.fuelType);
		Random rand = player.worldObj.rand;
		float thrust = j.thrust - 0.4F;
		float speed = -1 - 2 * thrust;
		float scale = 4 + 2 * thrust;
		int numParticles = 3;

		for(int i = 0; i < numParticles; i++) {
			float iN = (float) i / (float) numParticles;
			float randX = (float) (rand.nextGaussian() * 0.05F);
			float randZ = (float) (rand.nextGaussian() * 0.05F);
			j.plasma.add(withMotion(new JetpackParticles.Plasma(-1.8, iN * speed, 4, scale, grad), randX - 0.1F, speed, randZ));
			randX = (float) (rand.nextGaussian() * 0.05F);
			randZ = (float) (rand.nextGaussian() * 0.05F);
			j.plasma.add(withMotion(new JetpackParticles.Plasma(1.8, iN * speed, 4, scale, grad), randX + 0.1F, speed, randZ));
		}
		if(player.worldObj.getTotalWorldTime() % (2 - rand.nextInt(2)) == 0) {
			float alpha = MathHelper.clamp_float(0.05F + thrust * 0.1F, 0, 1);
			j.glow.add(new JetpackParticles.Glow(1.8, -1, 4, 20 + thrust * 10, 6 + rand.nextInt(2)).color(color[0], color[1], color[2], alpha));
			j.glow.add(new JetpackParticles.Glow(-1.8, -1, 4, 20 + thrust * 10, 6 + rand.nextInt(2)).color(color[0], color[1], color[2], alpha));
		}
		if(rand.nextInt(2) == 0) {
			j.haze.add(withMotion(new JetpackParticles.Haze(1.8, -1, 4, 5 + thrust, 1.5F + thrust * 3, 5), 0.1F, speed * 0.5F, 0));
		}
		if(rand.nextInt(2) == 0) {
			j.haze.add(withMotion(new JetpackParticles.Haze(-1.8, -1, 4, 5 + thrust, 1.5F + thrust * 3, 5), -0.1F, speed * 0.5F, 0));
		}
	}

	@SideOnly(Side.CLIENT)
	private static <T extends JetpackParticles.Base> T withMotion(T particle, float x, float y, float z) {
		particle.motion(x, y, z);
		return particle;
	}

	public static void toggleOpenState(EntityPlayer p) {
		JetpackInfo j = getOrCreate(p);
		j.dirty = true;
		j.animTime = System.currentTimeMillis();
		j.opening = !j.opening;
	}

	public static void toggleHoverState(EntityPlayer p) {
		JetpackInfo j = getOrCreate(p);
		j.dirty = true;
		j.hover = !j.hover;
	}

	public static void toggleHUDState(EntityPlayer p) {
		JetpackInfo j = getOrCreate(p);
		j.dirty = true;
		j.useCompactHUD = !j.useCompactHUD;
	}

	public static void setThrust(EntityPlayer p, float thrust) {
		JetpackInfo j = getOrCreate(p);
		if(j.thrust != thrust) {
			j.dirty = true;
			j.thrust = thrust;
		}
	}

	public static boolean isHovering(EntityPlayer p) {
		JetpackInfo j = get(p);
		return j != null && j.hover;
	}

	public static boolean isOpening(EntityPlayer p) {
		JetpackInfo j = get(p);
		return j == null || j.opening;
	}

	public static boolean useCompactHUD(EntityPlayer p) {
		JetpackInfo j = get(p);
		return j != null && j.useCompactHUD;
	}

	public static long getAnimTime(EntityPlayer p) {
		JetpackInfo j = get(p);
		return j == null ? 0 : j.animTime;
	}

	public static boolean jetpackActive(EntityPlayer p) {
		JetpackInfo j = get(p);
		if(j == null || !hasJetpack(p)) return false;
		return j.opening && System.currentTimeMillis() - j.animTime > 1000;
	}

	@SideOnly(Side.CLIENT)
	public static class JetpackMovementInput extends MovementInputFromOptions {

		public JetpackMovementInput(GameSettings settings) {
			super(settings);
		}

		@Override
		public void updatePlayerMoveState() {
			super.updatePlayerMoveState();
			EntityPlayer player = Minecraft.getMinecraft().thePlayer;
			if(player != null) JetpackHandler.inputUpdate(player, this);
		}
	}

	public static class JetpackInfo {

		//Values that must be sync'd.
		public boolean opening;
		public boolean hover = false;
		/** Used to determine how much rocket flame and smoke there should be. */
		public float thrust;
		/** How long the jetpack has been active in regular mode. Used for tipping the player over for a smooth transition. */
		public int jetpackFlyTime;
		/** How many ticks the jetpack remains in a fail state */
		public int failureTicks = 0;
		public boolean dirty = true;
		public boolean useCompactHUD = false;

		//Values that are kept only on this side.
		public long animTime;
		public FluidType fuelType = Fluids.NONE;
		public int fuel;
		public int fuelMax;
		/** Wing tip positions in world space {x1, y1, z1, x2, y2, z2} for the trails. */
		public final double[] spawnPositions = new double[6];
		public boolean spawnPositionsValid = false;
		/** Used for interpolating motion for smooth leaning in hover mode. */
		public double prevPrevPosX;
		public double prevPrevPosZ;
		public float prevThrust;

		@SideOnly(Side.CLIENT)
		public MovingSoundJetpack sound;
		//Particles in separate lists so the render order stays distortion, flames, brightness.
		@SideOnly(Side.CLIENT)
		public List<JetpackParticles.Plasma> plasma;
		@SideOnly(Side.CLIENT)
		public List<JetpackParticles.Glow> glow;
		@SideOnly(Side.CLIENT)
		public List<JetpackParticles.Haze> haze;
		@SideOnly(Side.CLIENT)
		public List<JetpackParticles.Trail> trails;
		@SideOnly(Side.CLIENT)
		public JetpackParticles.Trail trailLeft;
		@SideOnly(Side.CLIENT)
		public JetpackParticles.Trail trailRight;

		public JetpackInfo(boolean client) {
			if(client) initClient();
		}

		@SideOnly(Side.CLIENT)
		public void initClient() {
			sound = null;
			plasma = new ArrayList<JetpackParticles.Plasma>();
			glow = new ArrayList<JetpackParticles.Glow>();
			haze = new ArrayList<JetpackParticles.Haze>();
			trails = new ArrayList<JetpackParticles.Trail>();
		}

		public NBTTagCompound writeToNBT(NBTTagCompound tag) {
			tag.setBoolean("opening", opening);
			tag.setBoolean("hover", hover);
			tag.setFloat("thrust", thrust);
			tag.setInteger("flyTime", jetpackFlyTime);
			tag.setBoolean("compact", useCompactHUD);
			tag.setInteger("failureTicks", failureTicks);
			return tag;
		}

		public void readFromNBT(NBTTagCompound tag) {
			opening = tag.getBoolean("opening");
			hover = tag.getBoolean("hover");
			thrust = tag.getFloat("thrust");
			jetpackFlyTime = tag.getInteger("flyTime");
			useCompactHUD = tag.getBoolean("compact");
			failureTicks = tag.getInteger("failureTicks");
			dirty = true;
		}

		public void write(ByteBuf buf) {
			buf.writeBoolean(opening);
			buf.writeBoolean(hover);
			buf.writeFloat(thrust);
			buf.writeInt(jetpackFlyTime);
			buf.writeBoolean(useCompactHUD);
			buf.writeInt(failureTicks);
		}

		public void read(ByteBuf buf) {
			opening = buf.readBoolean();
			hover = buf.readBoolean();
			thrust = buf.readFloat();
			jetpackFlyTime = buf.readInt();
			useCompactHUD = buf.readBoolean();
			failureTicks = buf.readInt();
		}

		public void setFromServer(JetpackInfo other) {
			if(other.opening != this.opening) {
				this.animTime = System.currentTimeMillis();
			}
			this.opening = other.opening;
			this.hover = other.hover;
			this.thrust = other.thrust;
			this.jetpackFlyTime = other.jetpackFlyTime;
			this.useCompactHUD = other.useCompactHUD;
			this.failureTicks = other.failureTicks;
		}
	}
}
