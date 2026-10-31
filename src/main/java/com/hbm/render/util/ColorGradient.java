package com.hbm.render.util;

import java.util.ArrayList;
import java.util.List;

import com.hbm.util.BobMathUtil;

public class ColorGradient {

	private final List<float[]> colors = new ArrayList<float[]>();

	public ColorGradient addColor(float r, float g, float b, float a, float time) {
		colors.add(new float[] {r, g, b, a, time});
		return this;
	}

	/** Writes the RGBA at the given time into out and returns it. */
	public float[] getColor(float time, float[] out) {
		if(colors.isEmpty()) {
			out[0] = out[1] = out[2] = out[3] = 1F;
			return out;
		}
		if(time <= colors.get(0)[4]) return copy(colors.get(0), out);
		if(time >= colors.get(colors.size() - 1)[4]) return copy(colors.get(colors.size() - 1), out);

		for(int i = 0; i < colors.size() - 1; i++) {
			float[] c0 = colors.get(i);
			float[] c1 = colors.get(i + 1);
			if(time < c0[4] || time >= c1[4]) continue;
			// same interpolation maths as the 1.12.2 gradient so the plume looks identical
			float interp = BobMathUtil.remap(time, 0, 1, c0[4], c1[4]);
			for(int j = 0; j < 4; j++) out[j] = c0[j] + (c1[j] - c0[j]) * interp;
			return out;
		}
		return copy(colors.get(colors.size() - 1), out);
	}

	private static float[] copy(float[] c, float[] out) {
		out[0] = c[0];
		out[1] = c[1];
		out[2] = c[2];
		out[3] = c[3];
		return out;
	}
}
