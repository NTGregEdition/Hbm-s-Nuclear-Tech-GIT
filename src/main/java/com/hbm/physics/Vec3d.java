package com.hbm.physics;

//immutable, the physics code shares vectors freely between bodies and contacts
public final class Vec3d {

	public static final Vec3d ZERO = new Vec3d(0, 0, 0);

	public final double x;
	public final double y;
	public final double z;

	public Vec3d(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3d add(Vec3d v) {
		return new Vec3d(x + v.x, y + v.y, z + v.z);
	}

	public Vec3d add(double dx, double dy, double dz) {
		return new Vec3d(x + dx, y + dy, z + dz);
	}

	public Vec3d subtract(Vec3d v) {
		return new Vec3d(x - v.x, y - v.y, z - v.z);
	}

	public Vec3d negate() {
		return new Vec3d(-x, -y, -z);
	}

	public Vec3d scale(double s) {
		return new Vec3d(x * s, y * s, z * s);
	}

	public double dotProduct(Vec3d v) {
		return x * v.x + y * v.y + z * v.z;
	}

	public Vec3d crossProduct(Vec3d v) {
		return new Vec3d(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
	}

	public double lengthSquared() {
		return x * x + y * y + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSquared());
	}

	public Vec3d normalize() {
		double len = length();
		return len < 1.0E-4D ? ZERO : new Vec3d(x / len, y / len, z / len);
	}

	public Vec3d matTransform(Matrix3f m) {
		return new Vec3d(
				m.m00 * x + m.m01 * y + m.m02 * z,
				m.m10 * x + m.m11 * y + m.m12 * z,
				m.m20 * x + m.m21 * y + m.m22 * z);
	}

	public Matrix3f outerProduct(Vec3d v) {
		return new Matrix3f(
				(float) (x * v.x), (float) (x * v.y), (float) (x * v.z),
				(float) (y * v.x), (float) (y * v.y), (float) (y * v.z),
				(float) (z * v.x), (float) (z * v.y), (float) (z * v.z));
	}
}
