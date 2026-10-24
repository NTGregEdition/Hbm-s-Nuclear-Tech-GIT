package api.hbm.energymk2;

public final class CableProperties {

	public final String id;
	public final long voltage;
	public final long maxAmperage;
	/** Maximum energy throughput in HE/t, derived from voltage and amperage. */
	@Deprecated
	public final long maxEnergyPerTick;
	/** HE/t lost for each amp passing through this cable block. */
	public final long lossPerBlock;

	public CableProperties(String id, long voltage, long maxAmperage, long lossPerBlock) {
		if(id == null || id.isEmpty()) throw new IllegalArgumentException("Cable id cannot be empty");
		if(voltage <= 0 || maxAmperage <= 0 || lossPerBlock < 0) {
			throw new IllegalArgumentException("Cable properties must be positive; loss may be zero");
		}
		this.id = id;
		this.voltage = voltage;
		this.maxAmperage = maxAmperage;
		this.maxEnergyPerTick = voltage > Long.MAX_VALUE / maxAmperage ? Long.MAX_VALUE : voltage * maxAmperage;
		this.lossPerBlock = lossPerBlock;
	}
}
