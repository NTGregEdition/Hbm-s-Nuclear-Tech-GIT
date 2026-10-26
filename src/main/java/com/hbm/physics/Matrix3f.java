package com.hbm.physics;

//row-major 3x3 float matrix, only the operations the rigid body code needs
public class Matrix3f {

	public float m00, m01, m02;
	public float m10, m11, m12;
	public float m20, m21, m22;

	public Matrix3f() { }

	public Matrix3f(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {
		this.m00 = m00; this.m01 = m01; this.m02 = m02;
		this.m10 = m10; this.m11 = m11; this.m12 = m12;
		this.m20 = m20; this.m21 = m21; this.m22 = m22;
	}

	public Matrix3f copy() {
		return new Matrix3f(m00, m01, m02, m10, m11, m12, m20, m21, m22);
	}

	public void set(Matrix3f m) {
		m00 = m.m00; m01 = m.m01; m02 = m.m02;
		m10 = m.m10; m11 = m.m11; m12 = m.m12;
		m20 = m.m20; m21 = m.m21; m22 = m.m22;
	}

	public void setIdentity() {
		m00 = 1; m01 = 0; m02 = 0;
		m10 = 0; m11 = 1; m12 = 0;
		m20 = 0; m21 = 0; m22 = 1;
	}

	public float getElement(int row, int col) {
		switch(row) {
		case 0: return col == 0 ? m00 : col == 1 ? m01 : m02;
		case 1: return col == 0 ? m10 : col == 1 ? m11 : m12;
		default: return col == 0 ? m20 : col == 1 ? m21 : m22;
		}
	}

	/** this = this * m */
	public void mul(Matrix3f m) {
		set(new Matrix3f(
				m00 * m.m00 + m01 * m.m10 + m02 * m.m20, m00 * m.m01 + m01 * m.m11 + m02 * m.m21, m00 * m.m02 + m01 * m.m12 + m02 * m.m22,
				m10 * m.m00 + m11 * m.m10 + m12 * m.m20, m10 * m.m01 + m11 * m.m11 + m12 * m.m21, m10 * m.m02 + m11 * m.m12 + m12 * m.m22,
				m20 * m.m00 + m21 * m.m10 + m22 * m.m20, m20 * m.m01 + m21 * m.m11 + m22 * m.m21, m20 * m.m02 + m21 * m.m12 + m22 * m.m22));
	}

	public void mul(float s) {
		m00 *= s; m01 *= s; m02 *= s;
		m10 *= s; m11 *= s; m12 *= s;
		m20 *= s; m21 *= s; m22 *= s;
	}

	public void add(Matrix3f m) {
		m00 += m.m00; m01 += m.m01; m02 += m.m02;
		m10 += m.m10; m11 += m.m11; m12 += m.m12;
		m20 += m.m20; m21 += m.m21; m22 += m.m22;
	}

	public void sub(Matrix3f m) {
		m00 -= m.m00; m01 -= m.m01; m02 -= m.m02;
		m10 -= m.m10; m11 -= m.m11; m12 -= m.m12;
		m20 -= m.m20; m21 -= m.m21; m22 -= m.m22;
	}

	public void transpose() {
		float t = m01; m01 = m10; m10 = t;
		t = m02; m02 = m20; m20 = t;
		t = m12; m12 = m21; m21 = t;
	}

	public float determinant() {
		return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
	}

	/** a singular matrix (zero volume sliver) becomes the zero matrix, i.e. a body that cannot rotate */
	public void invert() {
		float det = determinant();
		if(det == 0) {
			set(new Matrix3f());
			return;
		}
		float inv = 1F / det;
		set(new Matrix3f(
				(m11 * m22 - m12 * m21) * inv, (m02 * m21 - m01 * m22) * inv, (m01 * m12 - m02 * m11) * inv,
				(m12 * m20 - m10 * m22) * inv, (m00 * m22 - m02 * m20) * inv, (m02 * m10 - m00 * m12) * inv,
				(m10 * m21 - m11 * m20) * inv, (m01 * m20 - m00 * m21) * inv, (m00 * m11 - m01 * m10) * inv));
	}

	public void rotX(float angle) {
		float s = (float) Math.sin(angle);
		float c = (float) Math.cos(angle);
		m00 = 1; m01 = 0; m02 = 0;
		m10 = 0; m11 = c; m12 = -s;
		m20 = 0; m21 = s; m22 = c;
	}

	public void rotY(float angle) {
		float s = (float) Math.sin(angle);
		float c = (float) Math.cos(angle);
		m00 = c; m01 = 0; m02 = s;
		m10 = 0; m11 = 1; m12 = 0;
		m20 = -s; m21 = 0; m22 = c;
	}

	public void rotZ(float angle) {
		float s = (float) Math.sin(angle);
		float c = (float) Math.cos(angle);
		m00 = c; m01 = -s; m02 = 0;
		m10 = s; m11 = c; m12 = 0;
		m20 = 0; m21 = 0; m22 = 1;
	}

	/** rotation of angle radians around the axis, which is normalised here */
	public void setAxisAngle(float x, float y, float z, float angle) {
		float len = (float) Math.sqrt(x * x + y * y + z * z);
		if(len < 1.0E-8F) {
			setIdentity();
			return;
		}
		x /= len;
		y /= len;
		z /= len;
		float s = (float) Math.sin(angle);
		float c = (float) Math.cos(angle);
		float t = 1 - c;
		m00 = t * x * x + c; m01 = t * x * y - s * z; m02 = t * x * z + s * y;
		m10 = t * x * y + s * z; m11 = t * y * y + c; m12 = t * y * z - s * x;
		m20 = t * x * z - s * y; m21 = t * y * z + s * x; m22 = t * z * z + c;
	}

	public void setFromQuat(Quat4f q) {
		m00 = 1 - 2 * q.y * q.y - 2 * q.z * q.z;
		m01 = 2 * q.x * q.y - 2 * q.z * q.w;
		m02 = 2 * q.x * q.z + 2 * q.y * q.w;

		m10 = 2 * q.x * q.y + 2 * q.z * q.w;
		m11 = 1 - 2 * q.x * q.x - 2 * q.z * q.z;
		m12 = 2 * q.y * q.z - 2 * q.x * q.w;

		m20 = 2 * q.x * q.z - 2 * q.y * q.w;
		m21 = 2 * q.y * q.z + 2 * q.x * q.w;
		m22 = 1 - 2 * q.x * q.x - 2 * q.y * q.y;
	}

	/** orientation for projecting onto a plane with this normal, spun around it by roll radians */
	public static Matrix3f normalToMat(Vec3d normal, float roll) {
		float yaw = (float) Math.atan2(normal.x, normal.z);
		float pitch = (float) Math.atan2(normal.y, Math.sqrt(normal.x * normal.x + normal.z * normal.z));
		return eulerToMat(yaw, pitch, roll);
	}

	public static Matrix3f eulerToMat(float yaw, float pitch, float roll) {
		Matrix3f mY = new Matrix3f();
		mY.rotY(-yaw);
		Matrix3f mP = new Matrix3f();
		mP.rotX(pitch);
		Matrix3f mR = new Matrix3f();
		mR.rotZ(roll);
		mR.mul(mP);
		mR.mul(mY);
		return mR;
	}
}
