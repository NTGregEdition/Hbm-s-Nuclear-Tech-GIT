package com.hbm.blocks.network;

import com.hbm.items.ModItems;

import api.hbm.energymk2.VoltageTier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class PumpUpgrades {

	public static final int NONE = -1;

	public static final int BUFFER_SECONDS = 30;

	public static final class Stats {

		public final int flow;
		public final int pipes;
		public final long idle;
		public final long perBucket;

		public Stats(int flow, int pipes, long idle, long perBucket) {
			this.flow = flow;
			this.pipes = pipes;
			this.idle = idle;
			this.perBucket = perBucket;
		}
	}

	/** Voltage Electric Pump, installed in the Pipe Pump: flow, pipes, idle draw, HE per 1000mB */
	public static final Stats[] ELECTRIC_PUMP = {
		new Stats(50, 8, 20, 4_096),                    //  0  LV   32 V
		new Stats(100, 16, 40, 2_048),                  //  1  MV   128 V
		new Stats(250, 32, 80, 1_024),                  //  2  HV   512 V
		new Stats(500, 64, 160, 512),                   //  3  EV   2,048 V
		new Stats(1_000, 128, 320, 256),                //  4  IV   8,192 V
		new Stats(2_500, 256, 640, 128),                //  5  LUV  32,768 V
		new Stats(5_000, 512, 1_280, 64),               //  6  ZPM  131,072 V
		new Stats(10_000, 1_024, 2_560, 32),            //  7  UV   524,288 V
		new Stats(25_000, 2_048, 5_120, 16),            //  8  UHV  2,097,152 V
		new Stats(50_000, 4_096, 10_240, 8),            //  9  UEV  8,388,608 V
		new Stats(100_000, 8_192, 20_480, 4),           // 10  UIV  33,554,432 V
		new Stats(250_000, 16_384, 40_960, 2),          // 11  UMV  134,217,728 V
		new Stats(500_000, 32_768, 81_920, 1),          // 12  UXV  536,870,912 V
		new Stats(1_000_000, 65_536, 163_840, 1),       // 13  MAX  2,147,483,648 V
	};

	/** Voltage Fluid Regulator, installed in the Flow Control Pump: max flow setting, pipes, idle draw, HE per 1000mB */
	public static final Stats[] FLUID_REGULATOR = {
		new Stats(50, 8, 20, 4_096),                    //  0  LV   32 V
		new Stats(100, 16, 40, 2_048),                  //  1  MV   128 V
		new Stats(250, 32, 80, 1_024),                  //  2  HV   512 V
		new Stats(500, 64, 160, 512),                   //  3  EV   2,048 V
		new Stats(1_000, 128, 320, 256),                //  4  IV   8,192 V
		new Stats(2_500, 256, 640, 128),                //  5  LUV  32,768 V
		new Stats(5_000, 512, 1_280, 64),               //  6  ZPM  131,072 V
		new Stats(10_000, 1_024, 2_560, 32),            //  7  UV   524,288 V
		new Stats(25_000, 2_048, 5_120, 16),            //  8  UHV  2,097,152 V
		new Stats(50_000, 4_096, 10_240, 8),            //  9  UEV  8,388,608 V
		new Stats(100_000, 8_192, 20_480, 4),           // 10  UIV  33,554,432 V
		new Stats(250_000, 16_384, 40_960, 2),          // 11  UMV  134,217,728 V
		new Stats(500_000, 32_768, 81_920, 1),          // 12  UXV  536,870,912 V
		new Stats(1_000_000, 65_536, 163_840, 1),       // 13  MAX  2,147,483,648 V
	};

	private PumpUpgrades() { }

	public static int tierOf(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return NONE;
		return VoltageTier.wrapIndex(stack.getItemDamage());
	}

	public static boolean isValidTier(int tier) {
		return tier >= 0 && tier < VoltageTier.TIERS.length;
	}

	public static String tierName(int tier) {
		return isValidTier(tier) ? VoltageTier.getTierName(VoltageTier.TIERS[tier]) : "none";
	}

	public static long voltageOf(int tier) {
		return isValidTier(tier) ? VoltageTier.TIERS[tier] : VoltageTier.DEFAULT;
	}

	public static int flow(Stats[] table, int tier) {
		return isValidTier(tier) ? table[tier].flow : 0;
	}

	public static int pipes(Stats[] table, int tier) {
		return isValidTier(tier) ? table[tier].pipes : 0;
	}

	public static long idle(Stats[] table, int tier) {
		return isValidTier(tier) ? table[tier].idle : 0L;
	}

	public static long energyForMoved(Stats[] table, int tier, long mB) {
		if(!isValidTier(tier) || mB <= 0L) return 0L;
		return mB * table[tier].perBucket / 1_000L;
	}

	public static long maxDrain(Stats[] table, int tier) {
		if(!isValidTier(tier)) return 0L;
		Stats stats = table[tier];
		return stats.idle + (long) stats.flow * stats.perBucket / 1_000L;
	}

	public static long capacity(Stats[] table, int tier) {
		return (long) BUFFER_SECONDS * maxDrain(table, isValidTier(tier) ? tier : 0);
	}

	public static boolean isPumpItem(ItemStack stack, boolean regulator) {
		if(stack == null || stack.getItem() == null) return false;
		return stack.getItem() == (regulator ? ModItems.voltage_fluid_regulator : ModItems.voltage_electric_pump);
	}

	public static void giveBack(EntityPlayer player, Item item, int tier) {
		ItemStack stack = new ItemStack(item, 1, Math.max(0, tier));
		if(!player.inventory.addItemStackToInventory(stack)) player.dropPlayerItemWithRandomChoice(stack, false);
	}
}
