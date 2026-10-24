package api.hbm.energymk2;

import com.hbm.items.ModItems;
import com.hbm.lib.Library;

import net.minecraft.item.ItemStack;

public final class VoltageCheckedCharging {

	private VoltageCheckedCharging() { }

	public static long chargeTEFromItems(IEnergyReceiverMK2 machine, ItemStack[] slots, int index, long power, long maxPower) {

		ItemStack stack = slots[index];

		if(stack != null && stack.getItem() instanceof IBatteryItem && stack.getItem() != ModItems.battery_creative) {

			long machineVoltage = machine.getReceiverVoltage();

			if(VoltageTier.isConfigured(machineVoltage)) {
				long batteryVoltage = BatteryVoltageRegistry.getVoltage(stack, machineVoltage);

				if(batteryVoltage > machineVoltage) {
					machine.onOvervoltage(batteryVoltage);
					return power;
				}
			}
		}

		return Library.chargeTEFromItems(slots, index, power, maxPower);
	}

	public static long chargeItemsFromTE(IEnergyProviderMK2 machine, ItemStack[] slots, int index, long power, long maxPower) {

		ItemStack stack = slots[index];

		if(stack != null && stack.getItem() instanceof IBatteryItem && stack.getItem() != ModItems.battery_creative) {

			long machineVoltage = machine.getProviderVoltage();
			long itemVoltage = BatteryVoltageRegistry.getVoltage(stack, machineVoltage);
			if(VoltageTier.isConfigured(machineVoltage) && machineVoltage > itemVoltage) {
				destroyItem(slots, index);
				return power;
			}
		}

		return Library.chargeItemsFromTE(slots, index, power, maxPower);
	}

	private static void destroyItem(ItemStack[] slots, int index) {
		ItemStack stack = slots[index];
		if(stack == null) return;
		if(--stack.stackSize <= 0) slots[index] = null;
	}
}
