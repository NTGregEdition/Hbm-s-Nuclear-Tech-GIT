package com.hbm.render.util;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.hbm.physics.Matrix3f;
import com.hbm.physics.Vec3d;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

@SideOnly(Side.CLIENT)
public final class DecalUtil {

	private DecalUtil() { }

	public static int generateDecalMesh(World world, Vec3d normal, float scale, float offsetX, float offsetY, float offsetZ) {
		float roll = world.rand.nextFloat() * 2F * (float) Math.PI;
		Matrix3f rot = Matrix3f.normalToMat(normal, roll);
		Vec3d c1 = new Vec3d(rot.m00, rot.m01, rot.m02);
		Vec3d c2 = new Vec3d(rot.m10, rot.m11, rot.m12);
		Vec3d c3 = new Vec3d(rot.m20, rot.m21, rot.m22);

		float[][] planes = new float[6][];
		planes[0] = new float[] {(float) c1.x, (float) c1.y, (float) c1.z, scale};
		planes[1] = new float[] {(float) -c1.x, (float) -c1.y, (float) -c1.z, scale};
		planes[2] = new float[] {(float) c2.x, (float) c2.y, (float) c2.z, scale};
		planes[3] = new float[] {(float) -c2.x, (float) -c2.y, (float) -c2.z, scale};
		planes[4] = new float[] {(float) c3.x, (float) c3.y, (float) c3.z, scale * 3};
		planes[5] = new float[] {(float) -c3.x, (float) -c3.y, (float) -c3.z, scale * 3};

		double hx = Math.max(Math.max(Math.abs(c1.x * scale), Math.abs(c2.x * scale)), Math.abs(c3.x * scale * 3));
		double hy = Math.max(Math.max(Math.abs(c1.y * scale), Math.abs(c2.y * scale)), Math.abs(c3.y * scale * 3));
		double hz = Math.max(Math.max(Math.abs(c1.z * scale), Math.abs(c2.z * scale)), Math.abs(c3.z * scale * 3));
		AxisAlignedBB box = AxisAlignedBB.getBoundingBox(-hx, -hy, -hz, hx, hy, hz).offset(offsetX, offsetY, offsetZ);

		List<Vec3d[]> tris = new ArrayList<Vec3d[]>();
		List<AxisAlignedBB> shapes = new ArrayList<AxisAlignedBB>();
		for(int i = (int) Math.floor(box.minX); i <= Math.ceil(box.maxX); i++) {
			for(int j = (int) Math.floor(box.minY); j <= Math.ceil(box.maxY); j++) {
				for(int k = (int) Math.floor(box.minZ); k <= Math.ceil(box.maxZ); k++) {
					Block block = world.getBlock(i, j, k);
					shapes.clear();
					block.addCollisionBoxesToList(world, i, j, k, AxisAlignedBB.getBoundingBox(i, j, k, i + 1, j + 1, k + 1), shapes, null);
					List<Vec3d[]> blockTris = new ArrayList<Vec3d[]>();
					for(AxisAlignedBB shape : shapes) {
						addBoxFaces(world, blockTris, shape, i, j, k, normal, -offsetX, -offsetY, -offsetZ);
					}
					for(float[] plane : planes) {
						blockTris = clipToPositiveSide(blockTris, plane);
					}
					tris.addAll(blockTris);
				}
			}
		}

		int list = GL11.glGenLists(1);
		GL11.glNewList(list, GL11.GL_COMPILE);
		Tessellator tes = Tessellator.instance;
		tes.startDrawing(GL11.GL_TRIANGLES);
		float invScale = 1F / scale;
		for(Vec3d[] t : tris) {
			Vec3d norm = t[1].subtract(t[0]).crossProduct(t[2].subtract(t[0])).normalize();
			tes.setNormal((float) norm.x, (float) norm.y, (float) norm.z);
			for(Vec3d v : t) {
				Vec3d uv = v.matTransform(rot);
				tes.addVertexWithUV(v.x, v.y, v.z, uv.x * 0.5 * invScale + 0.5, uv.y * 0.5 * invScale + 0.5);
			}
		}
		tes.draw();
		GL11.glEndList();
		return list;
	}

	/** Adds the faces of the shape that point against the direction, skipping the ones sealed by an opaque neighbour. */
	private static void addBoxFaces(World world, List<Vec3d[]> out, AxisAlignedBB s, int bx, int by, int bz, Vec3d direction, double ox, double oy, double oz) {
		double x0 = s.minX + ox, x1 = s.maxX + ox;
		double y0 = s.minY + oy, y1 = s.maxY + oy;
		double z0 = s.minZ + oz, z1 = s.maxZ + oz;

		if(direction.x > 0.2 && !sealed(world, s.minX == bx, bx - 1, by, bz))
			addQuad(out, new Vec3d(x0, y0, z0), new Vec3d(x0, y0, z1), new Vec3d(x0, y1, z1), new Vec3d(x0, y1, z0));
		if(direction.x < -0.2 && !sealed(world, s.maxX == bx + 1, bx + 1, by, bz))
			addQuad(out, new Vec3d(x1, y0, z0), new Vec3d(x1, y1, z0), new Vec3d(x1, y1, z1), new Vec3d(x1, y0, z1));
		if(direction.y > 0.2 && !sealed(world, s.minY == by, bx, by - 1, bz))
			addQuad(out, new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), new Vec3d(x0, y0, z1));
		if(direction.y < -0.2 && !sealed(world, s.maxY == by + 1, bx, by + 1, bz))
			addQuad(out, new Vec3d(x0, y1, z0), new Vec3d(x0, y1, z1), new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z0));
		if(direction.z > 0.2 && !sealed(world, s.minZ == bz, bx, by, bz - 1))
			addQuad(out, new Vec3d(x0, y0, z0), new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0), new Vec3d(x1, y0, z0));
		if(direction.z < -0.2 && !sealed(world, s.maxZ == bz + 1, bx, by, bz + 1))
			addQuad(out, new Vec3d(x0, y0, z1), new Vec3d(x1, y0, z1), new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1));
	}

	private static boolean sealed(World world, boolean onBlockEdge, int nx, int ny, int nz) {
		return onBlockEdge && world.getBlock(nx, ny, nz).isOpaqueCube();
	}

	private static void addQuad(List<Vec3d[]> out, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
		out.add(new Vec3d[] {a, b, c});
		out.add(new Vec3d[] {c, d, a});
	}

	private static List<Vec3d[]> clipToPositiveSide(List<Vec3d[]> tris, float[] plane) {
		List<Vec3d[]> out = new ArrayList<Vec3d[]>();
		for(Vec3d[] t : tris) {
			boolean p1 = side(t[0], plane);
			boolean p2 = side(t[1], plane);
			boolean p3 = side(t[2], plane);
			if(p1 && p2 && p3) {
				out.add(t);
			} else if(p1 ^ p2 ^ p3) {
				//only one corner is kept
				Vec3d a = p1 ? t[0] : p2 ? t[1] : t[2];
				Vec3d b = p1 ? t[1] : p2 ? t[2] : t[0];
				Vec3d c = p1 ? t[2] : p2 ? t[0] : t[1];
				Vec3d d = a.add(b.subtract(a).scale((float) ModelRendererUtil.rayPlaneIntercept(a, b.subtract(a), plane)));
				Vec3d e = a.add(c.subtract(a).scale((float) ModelRendererUtil.rayPlaneIntercept(a, c.subtract(a), plane)));
				out.add(new Vec3d[] {a, d, e});
			} else if(p1 || p2 || p3) {
				//only one corner is cut off
				Vec3d a = !p1 ? t[0] : !p2 ? t[1] : t[2];
				Vec3d b = !p1 ? t[1] : !p2 ? t[2] : t[0];
				Vec3d c = !p1 ? t[2] : !p2 ? t[0] : t[1];
				Vec3d d = a.add(b.subtract(a).scale((float) ModelRendererUtil.rayPlaneIntercept(a, b.subtract(a), plane)));
				Vec3d e = a.add(c.subtract(a).scale((float) ModelRendererUtil.rayPlaneIntercept(a, c.subtract(a), plane)));
				out.add(new Vec3d[] {d, b, e});
				out.add(new Vec3d[] {b, c, e});
			}
		}
		return out;
	}

	private static boolean side(Vec3d p, float[] plane) {
		return p.x * plane[0] + p.y * plane[1] + p.z * plane[2] + plane[3] > 0;
	}
}
