package api.hbm.fluidmk2;

public final class FluidPipeProperties {

	public static final int HUGE = 0;
	public static final int LARGE = 1;
	public static final int NORMAL = 2;
	public static final int SMALL = 3;
	public static final int TINY = 4;
	public static final int SIZES = 5;

	public final String id;
	/** mB/t a normal sized pipe of this material lets through */
	public final int baseThroughput;
	/** Kelvin */
	public final int heatResistance;
	public final boolean gasProof;
	/** The full set of sizes and the multi fluid pipes, wooden style pipes only come as small, normal and large */
	public final boolean allSizes;

	public FluidPipeProperties(String id, int baseThroughput, int heatResistance, boolean gasProof, boolean allSizes) {
		if(id == null || id.isEmpty()) throw new IllegalArgumentException("Pipe id cannot be empty");
		if(baseThroughput < 6 || heatResistance <= 0) throw new IllegalArgumentException("Pipe properties must be positive");
		this.id = id;
		this.baseThroughput = baseThroughput;
		this.heatResistance = heatResistance;
		this.gasProof = gasProof;
		this.allSizes = allSizes;
	}

	public boolean hasSize(int size) {
		return size >= 0 && size < SIZES && (allSizes || (size >= LARGE && size <= SMALL));
	}
	
	public int getThroughput(int size, int channels) {
		if(channels == 4) return baseThroughput;
		if(channels == 9) return baseThroughput / 3;
		return getThroughput(size);
	}
	
	public long getCapacity(int size, int channels) {
		return getThroughput(size, channels) * 20L;
	}

	public int getThroughput(int size) {
		switch(size) {
		case HUGE: return baseThroughput * 4;
		case LARGE: return baseThroughput * 2;
		case SMALL: return baseThroughput / 3;
		case TINY: return baseThroughput / 6;
		default: return baseThroughput;
		}
	}
}
