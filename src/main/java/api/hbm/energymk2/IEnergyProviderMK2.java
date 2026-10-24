package api.hbm.energymk2;

/** If it sends energy, use this */
public interface IEnergyProviderMK2 extends IEnergyHandlerMK2 {

	public default long getProviderPower() { return this.getPower(); }
	public default void setProviderPower(long power) { this.setPower(power); }
	public default long getProviderMaxPower() { return this.getMaxPower(); }
	public default long getProviderVoltage() { return this.getVoltage(); }

	/** Maximum whole-voltage packets this provider can emit per tick. */
	public default long getProviderAmperage() {
		long voltage = getProviderVoltage();
		return voltage > 0L ? Math.max(0L, getProviderSpeed()) / voltage : 0L;
	}

	/** Uses up available power, default implementation has no sanity checking, make sure that the requested power is lequal to the current power */
	public default void usePower(long power) {
		this.setProviderPower(this.getProviderPower() - power);
	}

	public default long getProviderSpeed() {
		return this.getProviderMaxPower();
	}
}
