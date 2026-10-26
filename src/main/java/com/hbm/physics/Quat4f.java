package com.hbm.physics;

public class Quat4f {

	public float x, y, z, w;

	public void normalize() {
		float norm = x * x + y * y + z * z + w * w;
		if(norm > 0) {
			float inv = 1F / (float) Math.sqrt(norm);
			x *= inv;
			y *= inv;
			z *= inv;
			w *= inv;
		} else {
			x = y = z = w = 0;
		}
	}

	/** this = slerp(this, q, alpha), taking the short way round */
	public void interpolate(Quat4f q, float alpha) {
		float qx = q.x, qy = q.y, qz = q.z, qw = q.w;
		float dot = x * qx + y * qy + z * qz + w * qw;
		if(dot < 0) {
			qx = -qx; qy = -qy; qz = -qz; qw = -qw;
			dot = -dot;
		}
		float s1;
		float s2;
		if(1 - dot > 1.0E-6F) {
			float om = (float) Math.acos(dot);
			float sinom = (float) Math.sin(om);
			s1 = (float) Math.sin((1 - alpha) * om) / sinom;
			s2 = (float) Math.sin(alpha * om) / sinom;
		} else {
			s1 = 1 - alpha;
			s2 = alpha;
		}
		x = s1 * x + s2 * qx;
		y = s1 * y + s2 * qy;
		z = s1 * z + s2 * qz;
		w = s1 * w + s2 * qw;
	}
}
