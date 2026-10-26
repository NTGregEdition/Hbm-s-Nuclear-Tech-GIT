package com.hbm.physics;

import net.minecraft.util.AxisAlignedBB;

//static world geometry only, it has no mass
public class AABBCollider extends Collider {

	public final AxisAlignedBB box;

	public AABBCollider(AxisAlignedBB box) {
		this.box = box;
		this.localCentroid = new Vec3d((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
	}

	@Override
	public Vec3d support(Vec3d direction) {
		return new Vec3d(
				direction.x > 0 ? box.maxX : box.minX,
				direction.y > 0 ? box.maxY : box.minY,
				direction.z > 0 ? box.maxZ : box.minZ);
	}
}
