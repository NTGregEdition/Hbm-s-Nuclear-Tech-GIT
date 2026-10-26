package com.hbm.render.util;

import com.hbm.physics.Vec3d;

public class Triangle {

	public final TexVertex p1, p2, p3;

	public Triangle(Vec3d p1, Vec3d p2, Vec3d p3) {
		this.p1 = new TexVertex(p1, 0, 0);
		this.p2 = new TexVertex(p2, 0, 0);
		this.p3 = new TexVertex(p3, 0, 0);
	}

	public Triangle(Vec3d p1, Vec3d p2, Vec3d p3, float[] texCoords) {
		this.p1 = new TexVertex(p1, texCoords[0], texCoords[1]);
		this.p2 = new TexVertex(p2, texCoords[2], texCoords[3]);
		this.p3 = new TexVertex(p3, texCoords[4], texCoords[5]);
	}

	public static class TexVertex {

		public final Vec3d pos;
		public final float texX, texY;

		public TexVertex(Vec3d pos, float texX, float texY) {
			this.pos = pos;
			this.texX = texX;
			this.texY = texY;
		}
	}
}
