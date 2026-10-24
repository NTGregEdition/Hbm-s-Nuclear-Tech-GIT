package api.hbm.energymk2;

public final class TransformerProperties {

	public final String id;
	public final long inputVoltage;
	public final long outputVoltage;
	public final long maxInputEnergyPerTick;

	public TransformerProperties(String id, long inputVoltage, long outputVoltage, long maxInputEnergyPerTick) {
		if(id == null || id.isEmpty()) throw new IllegalArgumentException("Transformer id cannot be empty");
		if(inputVoltage <= 0 || outputVoltage <= 0 || maxInputEnergyPerTick <= 0) {
			throw new IllegalArgumentException("Transformer properties must be positive");
		}
		this.id = id;
		this.inputVoltage = inputVoltage;
		this.outputVoltage = outputVoltage;
		this.maxInputEnergyPerTick = maxInputEnergyPerTick;
	}

	/** Transformers change packet voltage and amperage, while conserving HE/t. */
	public long convert(long inputEnergy) {
		return Math.max(0L, Math.min(inputEnergy, maxInputEnergyPerTick));
	}

	public long getMaxOutputEnergyPerTick() {
		return maxInputEnergyPerTick;
	}

	public long getMaxInputAmperage() {
		return maxInputEnergyPerTick / inputVoltage;
	}

	public long getMaxOutputAmperage() {
		return getMaxOutputEnergyPerTick() / outputVoltage;
	}
}
