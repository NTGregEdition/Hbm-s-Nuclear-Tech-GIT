package com.hbm.physics;

import com.hbm.render.util.Triangle;

import net.minecraft.util.AxisAlignedBB;

public class ConvexMeshCollider extends Collider {

	public final Triangle[] triangles;
	public final float[] vertices;
	public final AxisAlignedBB localBox;

	public ConvexMeshCollider(int[] indices, float[] vertices, float density) {
		this.vertices = vertices;
		triangles = new Triangle[indices.length / 3];
		for(int i = 0; i < indices.length; i += 3) {
			Vec3d p1 = new Vec3d(vertices[indices[i + 0] * 3 + 0], vertices[indices[i + 0] * 3 + 1], vertices[indices[i + 0] * 3 + 2]);
			Vec3d p2 = new Vec3d(vertices[indices[i + 1] * 3 + 0], vertices[indices[i + 1] * 3 + 1], vertices[indices[i + 1] * 3 + 2]);
			Vec3d p3 = new Vec3d(vertices[indices[i + 2] * 3 + 0], vertices[indices[i + 2] * 3 + 1], vertices[indices[i + 2] * 3 + 2]);
			triangles[i / 3] = new Triangle(p1, p2, p3);
		}
		double maxX = support(RigidBody.cardinals[0]).x;
		double maxY = support(RigidBody.cardinals[1]).y;
		double maxZ = support(RigidBody.cardinals[2]).z;
		double minX = support(RigidBody.cardinals[3]).x;
		double minY = support(RigidBody.cardinals[4]).y;
		double minZ = support(RigidBody.cardinals[5]).z;
		this.localBox = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

		this.localCentroid = computeCenterOfMass();
		this.mass = computeVolume() * density;
		this.localInertiaTensor = computeInertia(localCentroid, mass);
	}

	private Matrix3f vertexMatrix(Triangle t, Vec3d offset) {
		return new Matrix3f(
				(float) (t.p1.pos.x - offset.x), (float) (t.p1.pos.y - offset.y), (float) (t.p1.pos.z - offset.z),
				(float) (t.p2.pos.x - offset.x), (float) (t.p2.pos.y - offset.y), (float) (t.p2.pos.z - offset.z),
				(float) (t.p3.pos.x - offset.x), (float) (t.p3.pos.y - offset.y), (float) (t.p3.pos.z - offset.z));
	}

	private float computeVolume() {
		float vol = 0;
		for(Triangle t : triangles) {
			vol += vertexMatrix(t, Vec3d.ZERO).determinant();
		}
		return vol / 6F;
	}

	private Vec3d computeCenterOfMass() {
		double cx = 0, cy = 0, cz = 0;
		float volume = 0;
		for(Triangle t : triangles) {
			Matrix3f mat = vertexMatrix(t, Vec3d.ZERO);
			float vol = mat.determinant();
			cx += vol * (mat.m00 + mat.m10 + mat.m20);
			cy += vol * (mat.m01 + mat.m11 + mat.m21);
			cz += vol * (mat.m02 + mat.m12 + mat.m22);
			volume += vol;
		}
		return new Vec3d(cx / (volume * 4F), cy / (volume * 4F), cz / (volume * 4F));
	}

	private Matrix3f computeInertia(Vec3d com, float mass) {
		float volume = 0;
		double[] diag = new double[3];
		double[] offd = new double[3];
		for(Triangle t : triangles) {
			Matrix3f mat = vertexMatrix(t, com);
			float d = mat.determinant();
			volume += d;

			for(int j = 0; j < 3; j++) {
				int j1 = (j + 1) % 3;
				int j2 = (j + 2) % 3;
				diag[j] += (mat.getElement(0, j) * mat.getElement(1, j) + mat.getElement(1, j) * mat.getElement(2, j) + mat.getElement(2, j) * mat.getElement(0, j) +
						mat.getElement(0, j) * mat.getElement(0, j) + mat.getElement(1, j) * mat.getElement(1, j) + mat.getElement(2, j) * mat.getElement(2, j)) * d;
				offd[j] += (mat.getElement(0, j1) * mat.getElement(1, j2) + mat.getElement(1, j1) * mat.getElement(2, j2) + mat.getElement(2, j1) * mat.getElement(0, j2) +
						mat.getElement(0, j1) * mat.getElement(2, j2) + mat.getElement(1, j1) * mat.getElement(0, j2) + mat.getElement(2, j1) * mat.getElement(1, j2) +
						mat.getElement(0, j1) * mat.getElement(0, j2) + mat.getElement(1, j1) * mat.getElement(1, j2) + mat.getElement(2, j1) * mat.getElement(2, j2)) * d;
			}
		}
		float diagDiv = volume * (60F / 6F);
		float offdDiv = volume * (120F / 6F);
		for(int j = 0; j < 3; j++) {
			diag[j] = diag[j] / diagDiv * mass;
			offd[j] = offd[j] / offdDiv * mass;
		}
		return new Matrix3f(
				(float) (diag[1] + diag[2]), (float) -offd[2], (float) -offd[1],
				(float) -offd[2], (float) (diag[0] + diag[2]), (float) -offd[0],
				(float) -offd[1], (float) -offd[0], (float) (diag[0] + diag[1]));
	}

	@Override
	public Vec3d support(Vec3d dir) {
		double dot = -Float.MAX_VALUE;
		int index = 0;
		for(int i = 0; i < vertices.length; i += 3) {
			double newDot = dir.x * vertices[i] + dir.y * vertices[i + 1] + dir.z * vertices[i + 2];
			if(newDot > dot) {
				dot = newDot;
				index = i;
			}
		}
		return new Vec3d(vertices[index], vertices[index + 1], vertices[index + 2]);
	}
}
