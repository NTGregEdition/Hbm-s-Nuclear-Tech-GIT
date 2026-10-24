package api.hbm.energymk2;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public interface IVoltageCableMK2 extends IEnergyConductorMK2 {

	public CableProperties getCableProperties();
	public void beginPowerTick();
	public long getRemainingTransfer();
	public long useTransferCapacity(long amount);
	public default void recordAmperageFlow(long voltage, long amperage, int delayTicks, TileEntity source) {
		recordAmperageFlow(voltage, amperage, getCableProperties().maxAmperage, delayTicks, source);
	}
	public default void recordAmperageFlow(long voltage, long amperage, long pathMaxAmperage, int delayTicks, TileEntity source) {
		useAmperageCapacity(amperage);
		burnForElectricalOverload(voltage, amperage, delayTicks, source);
	}
	public void explodeForWrongVoltage(long suppliedVoltage);
	public default long getRemainingAmperage() { return getRemainingTransfer(); }
	public default long useAmperageCapacity(long amperage) { return useTransferCapacity(amperage); }

	/** Queues a fire when either the cable voltage or amperage rating is exceeded. */
	public default void burnForElectricalOverload(long suppliedVoltage, long suppliedAmperage, int delayTicks) {
		CableProperties properties = getCableProperties();
		if(suppliedVoltage > properties.voltage || suppliedAmperage > properties.maxAmperage) {
			explodeForWrongVoltage(suppliedVoltage, delayTicks);
		}
	}

	/** Optional source context for implementations that need it while scheduling a cable burn. */
	public default void burnForElectricalOverload(long suppliedVoltage, long suppliedAmperage, int delayTicks, TileEntity source) {
		burnForElectricalOverload(suppliedVoltage, suppliedAmperage, delayTicks);
	}

	public default void explodeForWrongVoltage(long suppliedVoltage, int delayTicks) {
		this.explodeForWrongVoltage(suppliedVoltage);
	}

	public default boolean isFaceConnected(ForgeDirection direction) {
		return direction != null && direction != ForgeDirection.UNKNOWN;
	}
}
