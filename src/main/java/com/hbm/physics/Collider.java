package com.hbm.physics;

public abstract class Collider {

	public float mass;
	public Matrix3f localInertiaTensor;
	public Vec3d localCentroid;

	public abstract Vec3d support(Vec3d direction);
}
