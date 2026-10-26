package com.hbm.physics;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import com.hbm.physics.GJK.GJKInfo;
import com.hbm.physics.GJK.Result;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class RigidBody {

	public static final Vec3d[] cardinals = new Vec3d[] {
			new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1),
			new Vec3d(-1, 0, 0), new Vec3d(0, -1, 0), new Vec3d(0, 0, -1)};

	//Stands in for the immovable world in contacts
	public static final RigidBody DUMMY = new RigidBody(null) {
		public void solveContacts(float dt) { }

		public void updateOrientation() { }
		public void updateGlobalCentroidFromPosition() { }
		public void updatePositionFromGlobalCentroid() { }
		public void doTimeStep(float dt) { }
		public void addColliders(Collider... collide) { }

		public Vec3d globalToLocalPos(Vec3d pos) { return pos; }
		public Vec3d localToGlobalPos(Vec3d pos) { return pos; }
		public Vec3d globalToLocalVec(Vec3d vec) { return vec; }
		public Vec3d localToGlobalVec(Vec3d vec) { return vec; }

		public void addLinearVelocity(Vec3d v) { }
		public void addAngularVelocity(Vec3d v) { }

		public void addContact(Contact c) { }
	};

	static {
		DUMMY.inv_rotation = DUMMY.rotation.copy();
		DUMMY.localInertiaTensor = new Matrix3f();
		DUMMY.inv_localInertiaTensor = new Matrix3f();
		DUMMY.inv_globalInertiaTensor = new Matrix3f();
		DUMMY.localCentroid = Vec3d.ZERO;
		DUMMY.globalCentroid = Vec3d.ZERO;
	}

	//Only touched from the client thread
	private static final List<AxisAlignedBB> collisionBoxes = new ArrayList<>();

	public final World world;
	public AxisAlignedBB boundingBox;

	public final List<Collider> colliders = new ArrayList<>();
	public final List<AxisAlignedBB> colliderBoundingBoxes = new ArrayList<>();

	public Vec3d position = Vec3d.ZERO;
	public Vec3d globalCentroid;
	public Matrix3f rotation = new Matrix3f();
	public Matrix3f inv_rotation;

	public Vec3d prevPosition = Vec3d.ZERO;
	public final Quat4f prevRotation = new Quat4f();

	public Vec3d linearVelocity = Vec3d.ZERO;
	public Vec3d angularVelocity = Vec3d.ZERO;

	public float mass;
	public float inv_mass;
	public Matrix3f localInertiaTensor;
	public Matrix3f inv_localInertiaTensor;
	public Matrix3f inv_globalInertiaTensor;
	public float friction = 0.5F;
	public float restitution = 0;

	public Vec3d localCentroid;

	public final ContactManifold contacts = new ContactManifold();

	public RigidBody(World w) {
		this.world = w;
		rotation.setIdentity();
	}

	public RigidBody(World w, double x, double y, double z) {
		this(w);
		this.position = new Vec3d(x, y, z);
	}

	public void minecraftTimestep() {
		this.setPrevData();
		int timeStepSubDiv = 8;
		float step = 0.05F / (float) timeStepSubDiv;
		for(int i = 0; i < timeStepSubDiv; i++) {
			doTimeStep(step);
		}
	}

	public void doTimeStep(float dt) {
		contacts.update();
		//Do collision detection
		List<AxisAlignedBB> l = getWorldCollisionBoxes();
		for(int j = 0; j < l.size(); j++) {
			AxisAlignedBB box = l.get(j);
			for(int i = 0; i < colliders.size(); i++) {
				Collider c = colliders.get(i);
				if(!colliderBoundingBoxes.get(i).intersectsWith(box))
					continue;
				Collider b = new AABBCollider(box);
				GJKInfo info = GJK.colliding(this, null, c, b);
				if(info.result == Result.COLLIDING) {
					//No need to find whatever is deepest. We can just add every contact and let the manifold sort itself out correctly.
					contacts.addContact(new Contact(this, null, c, b, info));
				}
			}
		}

		solveContacts(dt);
		integrateVelocityAndPosition(dt);
	}

	private List<AxisAlignedBB> getWorldCollisionBoxes() {
		collisionBoxes.clear();
		int minX = MathHelper.floor_double(boundingBox.minX);
		int maxX = MathHelper.floor_double(boundingBox.maxX + 1D);
		int minY = MathHelper.floor_double(boundingBox.minY - 1D);
		int maxY = MathHelper.floor_double(boundingBox.maxY + 1D);
		int minZ = MathHelper.floor_double(boundingBox.minZ);
		int maxZ = MathHelper.floor_double(boundingBox.maxZ + 1D);
		for(int x = minX; x < maxX; x++) {
			for(int z = minZ; z < maxZ; z++) {
				for(int y = minY; y < maxY; y++) {
					Block block = world.getBlock(x, y, z);
					block.addCollisionBoxesToList(world, x, y, z, boundingBox, collisionBoxes, null);
				}
			}
		}
		return collisionBoxes;
	}

	public void integrateVelocityAndPosition(float dt) {
		//Integrate position
		globalCentroid = globalCentroid.add(linearVelocity.scale(dt));
		if(angularVelocity.lengthSquared() > 0) {
			Vec3d axis = angularVelocity.normalize();
			double angle = angularVelocity.length() * dt;
			Matrix3f turn = new Matrix3f();
			turn.setAxisAngle((float) axis.x, (float) axis.y, (float) axis.z, (float) angle);
			turn.mul(rotation);
			rotation = turn;
			updateOrientation();
		}
		updatePositionFromGlobalCentroid();
		updateAABBs();
		addLinearVelocity(new Vec3d(0, -9.81 * dt, 0));
	}

	public void setPrevData() {
		this.prevPosition = position;
		setFromMat(prevRotation, rotation);
	}

	public void addContact(Contact c) {
		contacts.addContact(c);
	}

	public void solveContacts(float dt) {
		for(int j = 0; j < contacts.contactCount; j++) {
			contacts.contacts[j].init(dt);
		}
		int velocityIterations = 4;
		for(int i = 0; i < velocityIterations; i++) {
			for(int j = 0; j < contacts.contactCount; j++) {
				contacts.contacts[j].solve(dt);
			}
		}
	}

	public Vec3d localToGlobalPos(Vec3d pos) {
		return pos.matTransform(rotation).add(position);
	}

	public Vec3d globalToLocalPos(Vec3d pos) {
		return pos.subtract(position).matTransform(inv_rotation);
	}

	public Vec3d localToGlobalVec(Vec3d vec) {
		return vec.matTransform(rotation);
	}

	public Vec3d globalToLocalVec(Vec3d vec) {
		return vec.matTransform(inv_rotation);
	}

	public void addLinearVelocity(Vec3d v) {
		linearVelocity = linearVelocity.add(v);
	}

	public void addAngularVelocity(Vec3d v) {
		angularVelocity = angularVelocity.add(v);
	}

	public void impulseVelocityDirect(Vec3d force, Vec3d position) {
		linearVelocity = linearVelocity.add(force);
		angularVelocity = angularVelocity.add(position.subtract(globalCentroid).crossProduct(force));
	}

	public void updateOrientation() {
		Quat4f quat = new Quat4f();
		setFromMat(quat, rotation);
		quat.normalize();
		rotation.setFromQuat(quat);
		inv_rotation = rotation.copy();
		inv_rotation.transpose();

		inv_globalInertiaTensor.set(inv_rotation);
		inv_globalInertiaTensor.mul(inv_localInertiaTensor);
		inv_globalInertiaTensor.mul(rotation);
	}

	public void updatePositionFromGlobalCentroid() {
		position = globalCentroid.add(localCentroid.scale(-1).matTransform(rotation));
	}

	public void updateGlobalCentroidFromPosition() {
		globalCentroid = localCentroid.matTransform(rotation).add(position);
	}

	public void updateAABBs() {
		colliderBoundingBoxes.clear();
		double tMaxX, tMaxY, tMaxZ, tMinX, tMinY, tMinZ;
		tMaxX = tMaxY = tMaxZ = -Double.MAX_VALUE;
		tMinX = tMinY = tMinZ = Double.MAX_VALUE;
		for(int i = 0; i < colliders.size(); i++) {
			Collider c = colliders.get(i);
			double maxX = GJK.localSupport(this, c, cardinals[0]).x;
			double maxY = GJK.localSupport(this, c, cardinals[1]).y;
			double maxZ = GJK.localSupport(this, c, cardinals[2]).z;
			double minX = GJK.localSupport(this, c, cardinals[3]).x;
			double minY = GJK.localSupport(this, c, cardinals[4]).y;
			double minZ = GJK.localSupport(this, c, cardinals[5]).z;
			colliderBoundingBoxes.add(AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
			tMaxX = Math.max(tMaxX, maxX);
			tMaxY = Math.max(tMaxY, maxY);
			tMaxZ = Math.max(tMaxZ, maxZ);
			tMinX = Math.min(tMinX, minX);
			tMinY = Math.min(tMinY, minY);
			tMinZ = Math.min(tMinZ, minZ);
		}
		boundingBox = AxisAlignedBB.getBoundingBox(tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ);
	}

	public void addColliders(Collider... collide) {
		for(Collider c : collide) {
			colliders.add(c);
		}
		localCentroid = Vec3d.ZERO;
		mass = 0;
		for(Collider c : colliders) {
			mass += c.mass;
			localCentroid = localCentroid.add(c.localCentroid.scale(c.mass));
		}
		inv_mass = 1F / mass;
		localCentroid = localCentroid.scale(inv_mass);

		localInertiaTensor = new Matrix3f();
		for(Collider c : colliders) {
			//https://en.wikipedia.org/wiki/Parallel_axis_theorem
			Vec3d colliderToLocal = localCentroid.subtract(c.localCentroid);
			double lenSquared = colliderToLocal.dotProduct(colliderToLocal);
			Matrix3f outerProduct = colliderToLocal.outerProduct(colliderToLocal);

			Matrix3f colliderToLocalMat = new Matrix3f();
			colliderToLocalMat.setIdentity();
			colliderToLocalMat.mul((float) lenSquared);
			colliderToLocalMat.sub(outerProduct);
			colliderToLocalMat.mul(c.mass);
			Matrix3f cLocalIT = c.localInertiaTensor.copy();
			cLocalIT.add(colliderToLocalMat);
			localInertiaTensor.add(cLocalIT);
		}
		inv_localInertiaTensor = new Matrix3f();
		inv_localInertiaTensor.set(localInertiaTensor);

		inv_localInertiaTensor.invert();
		inv_globalInertiaTensor = new Matrix3f();
		updateOrientation();
		updateGlobalCentroidFromPosition();
		this.prevPosition = position;
		updateAABBs();
	}

	/** Writes the interpolated model matrix, relative to the camera, into a 16 float column major buffer for glMultMatrix. */
	public void writeGlMatrix(FloatBuffer buf, Vec3d cameraPos, float partialTicks) {
		Quat4f quat = new Quat4f();
		setFromMat(quat, rotation);
		quat.interpolate(prevRotation, 1 - partialTicks);
		quat.normalize();
		Matrix3f rot = new Matrix3f();
		rot.setFromQuat(quat);

		buf.put(0, rot.m00);
		buf.put(1, rot.m10);
		buf.put(2, rot.m20);
		buf.put(3, 0);
		buf.put(4, rot.m01);
		buf.put(5, rot.m11);
		buf.put(6, rot.m21);
		buf.put(7, 0);
		buf.put(8, rot.m02);
		buf.put(9, rot.m12);
		buf.put(10, rot.m22);
		buf.put(11, 0);

		Vec3d pos = this.prevPosition.add(this.position.subtract(this.prevPosition).scale(partialTicks)).subtract(cameraPos);

		buf.put(12, (float) pos.x);
		buf.put(13, (float) pos.y);
		buf.put(14, (float) pos.z);
		buf.put(15, 1);
	}

	//LWJGL style matrix to quaternion conversion, stays correct for rotations aligned along an axis
	public static void setFromMat(Quat4f q, Matrix3f mat) {
		setFromMat(q, mat.m00, mat.m01, mat.m02, mat.m10, mat.m11, mat.m12, mat.m20, mat.m21, mat.m22);
	}

	public static void setFromMat(Quat4f q, float m00, float m01, float m02, float m10,
			float m11, float m12, float m20, float m21, float m22) {

		float s;
		float tr = m00 + m11 + m22;
		if(tr >= 0.0) {
			s = (float) Math.sqrt(tr + 1.0);
			q.w = s * 0.5f;
			s = 0.5f / s;
			q.x = (m21 - m12) * s;
			q.y = (m02 - m20) * s;
			q.z = (m10 - m01) * s;
		} else {
			float max = Math.max(Math.max(m00, m11), m22);
			if(max == m00) {
				s = (float) Math.sqrt(m00 - (m11 + m22) + 1.0);
				q.x = s * 0.5f;
				s = 0.5f / s;
				q.y = (m01 + m10) * s;
				q.z = (m20 + m02) * s;
				q.w = (m21 - m12) * s;
			} else if(max == m11) {
				s = (float) Math.sqrt(m11 - (m22 + m00) + 1.0);
				q.y = s * 0.5f;
				s = 0.5f / s;
				q.z = (m12 + m21) * s;
				q.x = (m01 + m10) * s;
				q.w = (m02 - m20) * s;
			} else {
				s = (float) Math.sqrt(m22 - (m00 + m11) + 1.0);
				q.z = s * 0.5f;
				s = 0.5f / s;
				q.x = (m20 + m02) * s;
				q.y = (m12 + m21) * s;
				q.w = (m10 - m01) * s;
			}
		}
	}
}
