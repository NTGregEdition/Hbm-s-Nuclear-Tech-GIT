package com.hbm.render.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import com.hbm.main.MainRegistry;
import com.hbm.particle.ParticleSlicedMob;
import com.hbm.physics.Collider;
import com.hbm.physics.ConvexMeshCollider;
import com.hbm.physics.GJK;
import com.hbm.physics.Matrix3f;
import com.hbm.physics.RigidBody;
import com.hbm.physics.Vec3d;
import com.hbm.render.util.Triangle.TexVertex;

import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Timer;
import net.minecraft.util.Vec3;

/** Slices a living entity's model along a plane and turns the halves into physics particles. */
@SideOnly(Side.CLIENT)
public class ModelRendererUtil {

	//The protected render methods are called so models that scale or tilt themselves (bats, slimes, ...) get cut the way they are drawn
	private static final Method rGetEntityTexture = ReflectionHelper.findMethod(Render.class, null, new String[] {"getEntityTexture", "func_110775_a"}, Entity.class);
	private static final Method rHandleRotationFloat = ReflectionHelper.findMethod(RendererLivingEntity.class, null, new String[] {"handleRotationFloat", "func_77044_a"}, EntityLivingBase.class, float.class);
	private static final Method rRotateCorpse = ReflectionHelper.findMethod(RendererLivingEntity.class, null, new String[] {"rotateCorpse", "func_77043_a"}, EntityLivingBase.class, float.class, float.class, float.class);
	private static final Method rPreRenderCallback = ReflectionHelper.findMethod(RendererLivingEntity.class, null, new String[] {"preRenderCallback", "func_77041_b"}, EntityLivingBase.class, float.class);
	private static final Field rMainModel = ReflectionHelper.findField(RendererLivingEntity.class, "mainModel", "field_77045_g");
	private static final Field rCompiled = ReflectionHelper.findField(ModelRenderer.class, "compiled", "field_78812_q");
	private static final Field rQuadList = ReflectionHelper.findField(ModelBox.class, "quadList", "field_78254_i");
	private static final Field rTimer = ReflectionHelper.findField(Minecraft.class, "timer", "field_71428_T");

	private static final ResourceLocation FALLBACK_TEXTURE = new ResourceLocation("textures/entity/steve.png");
	private static final FloatBuffer MATRIX_BUFFER = GLAllocation.createDirectFloatBuffer(16);

	private static final FloatBuffer FEEDBACK_BUFFER = GLAllocation.createDirectFloatBuffer(1 << 20);
	private static final IntBuffer VIEWPORT = GLAllocation.createDirectIntBuffer(16);
	private static final int FEEDBACK_VERTEX = 11;
	private static final float FEEDBACK_SCALE = 1F / 32F;
	private static final int[] BOUNDS_INDICES = {0, 2, 3, 0, 3, 1, 4, 5, 7, 4, 7, 6, 0, 4, 6, 0, 6, 2, 1, 3, 7, 1, 7, 5, 0, 1, 5, 0, 5, 4, 2, 6, 7, 2, 7, 3};

	@SuppressWarnings("unchecked")
	private static <T> T get(Field field, Object owner) {
		try {
			return (T) field.get(owner);
		} catch(IllegalAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static ResourceLocation getEntityTexture(Entity e) {
		ResourceLocation r = null;
		try {
			r = (ResourceLocation) rGetEntityTexture.invoke(RenderManager.instance.getEntityRenderObject(e), e);
		} catch(Exception ex) {
			MainRegistry.logger.error("Could not get the texture of a sliced entity", ex);
		}
		return r == null ? FALLBACK_TEXTURE : r;
	}

	/** every cube of the model, with the transform that places it relative to the entity's feet in world scale */
	public static List<ModelPart> getBoxesFromMob(EntityLivingBase e, float partialTicks) {
		Render render = RenderManager.instance.getEntityRenderObject(e);
		if(e.isChild() || !(render instanceof RendererLivingEntity)) return Collections.emptyList();

		ModelBase model = get(rMainModel, render);
		GL11.glPushMatrix();
		try {
			GL11.glLoadIdentity();
			return collectParts(e, (RendererLivingEntity) render, model, partialTicks, false);
		} finally {
			GL11.glPopMatrix();
		}
	}

	private static List<ModelPart> collectParts(EntityLivingBase e, RendererLivingEntity render, ModelBase model, float partialTicks, boolean draw) {
		//This copies RendererLivingEntity.doRender up to the model call, without the translation to the entity
		model.onGround = e.getSwingProgress(partialTicks);
		model.isRiding = e.isRiding();
		model.isChild = e.isChild();
		float bodyYaw = interpolateRotation(e.prevRenderYawOffset, e.renderYawOffset, partialTicks);
		float headYawAbs = interpolateRotation(e.prevRotationYawHead, e.rotationYawHead, partialTicks);
		if(e.isRiding() && e.ridingEntity instanceof EntityLivingBase) {
			EntityLivingBase mount = (EntityLivingBase) e.ridingEntity;
			bodyYaw = interpolateRotation(mount.prevRenderYawOffset, mount.renderYawOffset, partialTicks);
			float diff = MathHelper.wrapAngleTo180_float(headYawAbs - bodyYaw);

			if(diff < -85.0F) diff = -85.0F;
			if(diff >= 85.0F) diff = 85.0F;

			bodyYaw = headYawAbs - diff;

			if(diff * diff > 2500.0F) bodyYaw += diff * 0.2F;
		}

		float pitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * partialTicks;
		float scale = 0.0625F;
		float age = 0;
		try {
			age = (Float) rHandleRotationFloat.invoke(render, e, partialTicks);
			rRotateCorpse.invoke(render, e, age, bodyYaw, partialTicks);
			GL11.glScalef(-1.0F, -1.0F, 1.0F);
			rPreRenderCallback.invoke(render, e, partialTicks);
		} catch(Exception ex) {
			MainRegistry.logger.error("Could not apply the render transforms of a sliced entity", ex);
		}
		GL11.glTranslatef(0.0F, -24.0F * scale - 0.0078125F, 0.0F);

		float limbSwingAmount = e.prevLimbSwingAmount + (e.limbSwingAmount - e.prevLimbSwingAmount) * partialTicks;
		float limbSwing = e.limbSwing - e.limbSwingAmount * (1.0F - partialTicks);
		if(e.isChild()) limbSwing *= 3.0F;
		if(limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;

		model.setLivingAnimations(e, limbSwing, limbSwingAmount, partialTicks);
		model.setRotationAngles(limbSwing, limbSwingAmount, age, headYawAbs - bodyYaw, pitch, scale, e);

		if(draw) {
			model.render(e, limbSwing, limbSwingAmount, age, headYawAbs - bodyYaw, pitch, scale);
			return Collections.emptyList();
		}

		List<ModelPart> list = new ArrayList<ModelPart>();
		for(Object o : model.boxList) {
			ModelRenderer renderer = (ModelRenderer) o;
			if(!isChild(renderer, model.boxList)) generateList(scale, list, renderer);
		}
		return list;
	}

	private static boolean isChild(ModelRenderer r, List<?> list) {
		for(Object o : list) {
			ModelRenderer r2 = (ModelRenderer) o;
			if(r2.childModels != null && r2.childModels.contains(r)) return true;
		}
		return false;
	}

	private static void generateList(float scale, List<ModelPart> list, ModelRenderer render) {
		//A lot of mobs replace model renderers and leave extra ones in the list that never get compiled, those are not drawn so they are skipped
		if(render.isHidden || !render.showModel || !(Boolean) get(rCompiled, render)) return;
		GL11.glPushMatrix();
		doTransforms(render, scale);
		if(render.childModels != null) {
			for(Object child : render.childModels) {
				generateList(scale, list, (ModelRenderer) child);
			}
		}
		GL11.glScalef(scale, scale, scale);
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUFFER);
		Matrix4f mat = new Matrix4f();
		mat.load(MATRIX_BUFFER);
		MATRIX_BUFFER.rewind();
		list.add(new ModelPart(mat, render));
		GL11.glPopMatrix();
	}

	private static void doTransforms(ModelRenderer m, float scale) {
		GL11.glTranslatef(m.offsetX, m.offsetY, m.offsetZ);
		if(m.rotateAngleX == 0.0F && m.rotateAngleY == 0.0F && m.rotateAngleZ == 0.0F) {
			if(m.rotationPointX != 0.0F || m.rotationPointY != 0.0F || m.rotationPointZ != 0.0F) {
				GL11.glTranslatef(m.rotationPointX * scale, m.rotationPointY * scale, m.rotationPointZ * scale);
			}
		} else {
			GL11.glTranslatef(m.rotationPointX * scale, m.rotationPointY * scale, m.rotationPointZ * scale);
			if(m.rotateAngleZ != 0.0F) {
				GL11.glRotatef(m.rotateAngleZ * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
			}
			if(m.rotateAngleY != 0.0F) {
				GL11.glRotatef(m.rotateAngleY * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
			}
			if(m.rotateAngleX != 0.0F) {
				GL11.glRotatef(m.rotateAngleX * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
			}
		}
	}

	private static float interpolateRotation(float prevYawOffset, float yawOffset, float partialTicks) {
		float f = yawOffset - prevYawOffset;
		while(f < -180.0F) f += 360.0F;
		while(f >= 180.0F) f -= 360.0F;
		return prevYawOffset + partialTicks * f;
	}

	public static Triangle[] decompress(VertexData vertices) {
		Triangle[] tris = new Triangle[vertices.positionIndices.length / 3];
		for(int i = 0; i < vertices.positionIndices.length; i += 3) {
			int i0 = vertices.positionIndices[i];
			int i1 = vertices.positionIndices[i + 1];
			int i2 = vertices.positionIndices[i + 2];
			float[] tex = new float[6];
			tex[0] = vertices.texCoords[(i + 0) * 2];
			tex[1] = vertices.texCoords[(i + 0) * 2 + 1];
			tex[2] = vertices.texCoords[(i + 1) * 2];
			tex[3] = vertices.texCoords[(i + 1) * 2 + 1];
			tex[4] = vertices.texCoords[(i + 2) * 2];
			tex[5] = vertices.texCoords[(i + 2) * 2 + 1];
			tris[i / 3] = new Triangle(vertices.positions[i0], vertices.positions[i1], vertices.positions[i2], tex);
		}
		return tris;
	}

	public static VertexData compress(Triangle[] tris) {
		List<Vec3d> vertices = new ArrayList<Vec3d>(tris.length * 3);
		int[] indices = new int[tris.length * 3];
		float[] texCoords = new float[tris.length * 6];
		for(int i = 0; i < tris.length; i++) {
			Triangle tri = tris[i];
			double eps = 0.00001D;
			int idx = epsIndexOf(vertices, tri.p1.pos, eps);
			if(idx != -1) {
				indices[i * 3] = idx;
			} else {
				indices[i * 3] = vertices.size();
				vertices.add(tri.p1.pos);
			}

			idx = epsIndexOf(vertices, tri.p2.pos, eps);
			if(idx != -1) {
				indices[i * 3 + 1] = idx;
			} else {
				indices[i * 3 + 1] = vertices.size();
				vertices.add(tri.p2.pos);
			}

			idx = epsIndexOf(vertices, tri.p3.pos, eps);
			if(idx != -1) {
				indices[i * 3 + 2] = idx;
			} else {
				indices[i * 3 + 2] = vertices.size();
				vertices.add(tri.p3.pos);
			}

			texCoords[i * 6 + 0] = tri.p1.texX;
			texCoords[i * 6 + 1] = tri.p1.texY;
			texCoords[i * 6 + 2] = tri.p2.texX;
			texCoords[i * 6 + 3] = tri.p2.texY;
			texCoords[i * 6 + 4] = tri.p3.texX;
			texCoords[i * 6 + 5] = tri.p3.texY;
		}
		VertexData data = new VertexData();
		data.positions = vertices.toArray(new Vec3d[0]);
		data.positionIndices = indices;
		data.texCoords = texCoords;
		return data;
	}

	private static boolean epsEquals(Vec3d a, Vec3d b, double eps) {
		return Math.abs(a.x - b.x) < eps && Math.abs(a.y - b.y) < eps && Math.abs(a.z - b.z) < eps;
	}

	private static int epsIndexOf(List<Vec3d> l, Vec3d vec, double eps) {
		for(int i = 0; i < l.size(); i++) {
			if(epsEquals(vec, l.get(i), eps)) {
				return i;
			}
		}
		return -1;
	}

	public static VertexData[] cutAndCapModelBox(ModelBox b, float[] plane, Matrix4f transform) {
		return cutAndCapConvex(triangulate(b, transform), plane);
	}

	/** Returns the triangles on the positive side of the plane, on the negative side, and the cap that closes the cut. */
	public static VertexData[] cutAndCapConvex(Triangle[] tris, float[] plane) {
		return cutAndCap(tris, plane, false);
	}

	private static VertexData[] cutAndCap(Triangle[] tris, float[] plane, boolean mesh) {
		VertexData[] returnData = new VertexData[] {null, null, new VertexData()};
		List<Triangle> side1 = new ArrayList<Triangle>();
		List<Triangle> side2 = new ArrayList<Triangle>();
		List<Vec3d[]> clippedEdges = new ArrayList<Vec3d[]>();
		for(Triangle t : tris) {
			//Clip each triangle to the plane.
			boolean p1 = t.p1.pos.x * plane[0] + t.p1.pos.y * plane[1] + t.p1.pos.z * plane[2] + plane[3] > 0;
			boolean p2 = t.p2.pos.x * plane[0] + t.p2.pos.y * plane[1] + t.p2.pos.z * plane[2] + plane[3] > 0;
			boolean p3 = t.p3.pos.x * plane[0] + t.p3.pos.y * plane[1] + t.p3.pos.z * plane[2] + plane[3] > 0;
			//If all points on positive side, add to side 1
			if(p1 && p2 && p3) {
				side1.add(t);
			//else if all on negative side, add to size 2
			} else if(!p1 && !p2 && !p3) {
				side2.add(t);
			//else if only one is positive, clip and add 1 triangle to side 1, 2 to side 2
			} else if(p1 ^ p2 ^ p3) {
				TexVertex a, b, c;
				if(p1) {
					a = t.p1;
					b = t.p2;
					c = t.p3;
				} else if(p2) {
					a = t.p2;
					b = t.p3;
					c = t.p1;
				} else {
					a = t.p3;
					b = t.p1;
					c = t.p2;
				}
				Vec3d rAB = b.pos.subtract(a.pos);
				Vec3d rAC = c.pos.subtract(a.pos);
				float interceptAB = (float) rayPlaneIntercept(a.pos, rAB, plane);
				float interceptAC = (float) rayPlaneIntercept(a.pos, rAC, plane);
				Vec3d d = a.pos.add(rAB.scale(interceptAB));
				Vec3d e = a.pos.add(rAC.scale(interceptAC));
				float[] deTex = new float[4];
				deTex[0] = a.texX + (b.texX - a.texX) * interceptAB;
				deTex[1] = a.texY + (b.texY - a.texY) * interceptAB;
				deTex[2] = a.texX + (c.texX - a.texX) * interceptAC;
				deTex[3] = a.texY + (c.texY - a.texY) * interceptAC;

				side2.add(new Triangle(d, b.pos, e, new float[] {deTex[0], deTex[1], b.texX, b.texY, deTex[2], deTex[3]}));
				side2.add(new Triangle(b.pos, c.pos, e, new float[] {b.texX, b.texY, c.texX, c.texY, deTex[2], deTex[3]}));
				side1.add(new Triangle(a.pos, d, e, new float[] {a.texX, a.texY, deTex[0], deTex[1], deTex[2], deTex[3]}));
				clippedEdges.add(new Vec3d[] {d, e});

			//else one is negative, clip and add 2 triangles to side 1, 1 to side 2.
			} else {
				TexVertex a, b, c;
				if(!p1) {
					a = t.p1;
					b = t.p2;
					c = t.p3;
				} else if(!p2) {
					a = t.p2;
					b = t.p3;
					c = t.p1;
				} else {
					a = t.p3;
					b = t.p1;
					c = t.p2;
				}
				Vec3d rAB = b.pos.subtract(a.pos);
				Vec3d rAC = c.pos.subtract(a.pos);
				float interceptAB = (float) rayPlaneIntercept(a.pos, rAB, plane);
				float interceptAC = (float) rayPlaneIntercept(a.pos, rAC, plane);
				Vec3d d = a.pos.add(rAB.scale(interceptAB));
				Vec3d e = a.pos.add(rAC.scale(interceptAC));
				float[] deTex = new float[4];
				deTex[0] = a.texX + (b.texX - a.texX) * interceptAB;
				deTex[1] = a.texY + (b.texY - a.texY) * interceptAB;
				deTex[2] = a.texX + (c.texX - a.texX) * interceptAC;
				deTex[3] = a.texY + (c.texY - a.texY) * interceptAC;

				side1.add(new Triangle(d, b.pos, e, new float[] {deTex[0], deTex[1], b.texX, b.texY, deTex[2], deTex[3]}));
				side1.add(new Triangle(b.pos, c.pos, e, new float[] {b.texX, b.texY, c.texX, c.texY, deTex[2], deTex[3]}));
				side2.add(new Triangle(a.pos, d, e, new float[] {a.texX, a.texY, deTex[0], deTex[1], deTex[2], deTex[3]}));
				clippedEdges.add(new Vec3d[] {e, d});
			}
		}
		if(mesh && !clippedEdges.isEmpty()) {
			Triangle[] cap = capLoops(clippedEdges, plane, side1, side2);
			if(cap.length > 0) returnData[2] = compress(cap);
		//Since this is a convex mesh, generating one edge list is fine.
		} else if(!clippedEdges.isEmpty()) {
			Matrix3f mat = Matrix3f.normalToMat(new Vec3d(plane[0], plane[1], plane[2]), 0);
			List<Vec3d> orderedClipVertices = new ArrayList<Vec3d>();
			orderedClipVertices.add(clippedEdges.get(0)[0]);
			while(!clippedEdges.isEmpty()) {
				orderedClipVertices.add(getNext(clippedEdges, orderedClipVertices.get(orderedClipVertices.size() - 1)));
			}
			Vec3d uv1 = orderedClipVertices.get(0).matTransform(mat);
			Triangle[] cap = new Triangle[orderedClipVertices.size() - 2];
			for(int i = 0; i < cap.length; i++) {
				Vec3d uv2 = orderedClipVertices.get(i + 2).matTransform(mat);
				Vec3d uv3 = orderedClipVertices.get(i + 1).matTransform(mat);
				cap[i] = new Triangle(orderedClipVertices.get(0), orderedClipVertices.get(i + 2), orderedClipVertices.get(i + 1), new float[] {(float) uv1.x, (float) uv1.y, (float) uv2.x, (float) uv2.y, (float) uv3.x, (float) uv3.y});

				side1.add(new Triangle(orderedClipVertices.get(0), orderedClipVertices.get(i + 2), orderedClipVertices.get(i + 1), new float[] {0, 0, 0, 0, 0, 0}));
				side2.add(new Triangle(orderedClipVertices.get(0), orderedClipVertices.get(i + 1), orderedClipVertices.get(i + 2), new float[] {0, 0, 0, 0, 0, 0}));
			}
			returnData[2] = compress(cap);
		}
		returnData[0] = compress(side1.toArray(new Triangle[side1.size()]));
		returnData[1] = compress(side2.toArray(new Triangle[side2.size()]));
		return returnData;
	}

	private static Vec3d getNext(List<Vec3d[]> edges, Vec3d first) {
		Vec3d next = findNext(edges, first);
		if(next == null) throw new IllegalStateException("Didn't find next in loop!");
		return next;
	}

	private static Vec3d findNext(List<Vec3d[]> edges, Vec3d first) {
		Iterator<Vec3d[]> itr = edges.iterator();
		while(itr.hasNext()) {
			Vec3d[] v = itr.next();
			double eps = 0.00001D;
			if(epsEquals(v[0], first, eps)) {
				itr.remove();
				return v[1];
			} else if(epsEquals(v[1], first, eps)) {
				itr.remove();
				return v[0];
			}
		}
		return null;
	}

	public static double rayPlaneIntercept(Vec3d start, Vec3d ray, float[] plane) {
		double num = -(plane[0] * start.x + plane[1] * start.y + plane[2] * start.z + plane[3]);
		double denom = plane[0] * ray.x + plane[1] * ray.y + plane[2] * ray.z;
		return num / denom;
	}

	private static Vec3d transform(Vec3 vec, Matrix4f mat) {
		double x = mat.m00 * vec.xCoord + mat.m10 * vec.yCoord + mat.m20 * vec.zCoord + mat.m30;
		double y = mat.m01 * vec.xCoord + mat.m11 * vec.yCoord + mat.m21 * vec.zCoord + mat.m31;
		double z = mat.m02 * vec.xCoord + mat.m12 * vec.yCoord + mat.m22 * vec.zCoord + mat.m32;
		return new Vec3d(x, y, z);
	}

	public static Triangle[] triangulate(ModelBox b, Matrix4f transform) {
		TexturedQuad[] quads = get(rQuadList, b);
		Triangle[] tris = new Triangle[quads.length * 2];
		int i = 0;
		for(TexturedQuad t : quads) {
			PositionTextureVertex[] v = t.vertexPositions;
			Vec3d v0 = transform(v[0].vector3D, transform);
			Vec3d v1 = transform(v[1].vector3D, transform);
			Vec3d v2 = transform(v[2].vector3D, transform);
			Vec3d v3 = transform(v[3].vector3D, transform);
			tris[i++] = new Triangle(v0, v1, v2, new float[] {v[0].texturePositionX, v[0].texturePositionY, v[1].texturePositionX, v[1].texturePositionY, v[2].texturePositionX, v[2].texturePositionY});
			tris[i++] = new Triangle(v2, v3, v0, new float[] {v[2].texturePositionX, v[2].texturePositionY, v[3].texturePositionX, v[3].texturePositionY, v[0].texturePositionX, v[0].texturePositionY});
		}
		return tris;
	}

	/**
	 * Cuts the entity along the plane (normal xyz, offset w, relative to the entity's feet) into physics driven pieces.
	 * @param capConsumer receives the triangles of the cut faces, relative to the entity's feet
	 */
	public static ParticleSlicedMob[] generateCutParticles(EntityLivingBase ent, float[] plane, ResourceLocation capTex, boolean glowingCap, Consumer<List<Triangle>> capConsumer) {

		// Cut all mob boxes and store them in separate lists //

		float partialTicks = ((Timer) get(rTimer, Minecraft.getMinecraft())).renderPartialTicks;
		List<ModelPart> boxes = getBoxesFromMob(ent, partialTicks);
		List<CutModelData> top = new ArrayList<CutModelData>();
		List<CutModelData> bottom = new ArrayList<CutModelData>();
		for(ModelPart r : boxes) {
			for(Object o : r.renderer.cubeList) {
				VertexData[] dat = cutAndCapModelBox((ModelBox) o, plane, r.matrix);
				CutModelData tp = null;
				CutModelData bt = null;
				if(dat[0].positionIndices != null && dat[0].positionIndices.length > 0) {
					tp = new CutModelData(dat[0], null, false, new ConvexMeshCollider(dat[0].positionIndices, dat[0].vertexArray(), 1));
					top.add(tp);
				}
				if(dat[1].positionIndices != null && dat[1].positionIndices.length > 0) {
					bt = new CutModelData(dat[1], null, true, new ConvexMeshCollider(dat[1].positionIndices, dat[1].vertexArray(), 1));
					bottom.add(bt);
				}
				if(tp != null && bt != null && dat[2].positionIndices != null && dat[2].positionIndices.length > 0) {
					tp.cap = dat[2];
					bt.cap = dat[2];
				}
			}
		}

		if(boxes.isEmpty()) cutDrawnModel(ent, plane, partialTicks, top, bottom);

		if(capConsumer != null) {
			List<Triangle> tris = new ArrayList<Triangle>();
			for(CutModelData d : top) {
				if(d.cap != null) {
					for(Triangle t : decompress(d.cap)) {
						tris.add(t);
					}
				}
			}
			capConsumer.accept(tris);
		}

		List<List<CutModelData>> particleChunks = new ArrayList<List<CutModelData>>();
		generateChunks(particleChunks, top);
		generateChunks(particleChunks, bottom);

		List<ParticleSlicedMob> particles = new ArrayList<ParticleSlicedMob>(2);

		Tessellator tes = Tessellator.instance;

		ResourceLocation tex = getEntityTexture(ent);

		for(List<CutModelData> l : particleChunks) {
			float scale = 3.5F;
			if(l.get(0).flip) {
				scale = -scale;
			}
			//Generate the physics body
			RigidBody body = new RigidBody(ent.worldObj, ent.posX, ent.boundingBox.minY, ent.posZ);
			Collider[] colliders = new Collider[l.size()];
			int i = 0;
			for(CutModelData dat : l) {
				colliders[i++] = dat.collider;
			}
			body.addColliders(colliders);
			body.impulseVelocityDirect(new Vec3d(plane[0] * scale, plane[1] * scale, plane[2] * scale), body.globalCentroid);

			//Create rendering display lists
			int bodyDL = GL11.glGenLists(1);
			int capDL = GL11.glGenLists(1);

			GL11.glNewList(bodyDL, GL11.GL_COMPILE);
			tes.startDrawing(GL11.GL_TRIANGLES);
			for(CutModelData dat : l) {
				dat.data.tessellate(tes, false);
			}
			tes.draw();
			GL11.glEndList();

			GL11.glNewList(capDL, GL11.GL_COMPILE);
			tes.startDrawing(GL11.GL_TRIANGLES);
			for(CutModelData dat : l) {
				if(dat.cap != null) dat.cap.tessellate(tes, dat.flip);
			}
			tes.draw();
			GL11.glEndList();

			particles.add(new ParticleSlicedMob(ent.worldObj, body, bodyDL, capDL, tex, capTex, glowingCap));
		}

		return particles.toArray(new ParticleSlicedMob[particles.size()]);
	}

	private static void cutDrawnModel(EntityLivingBase ent, float[] plane, float partialTicks, List<CutModelData> top, List<CutModelData> bottom) {
		Triangle[] tris = captureMobTriangles(ent, partialTicks);
		if(tris.length == 0) return;

		VertexData[] dat = cutAndCap(tris, plane, true);
		CutModelData tp = null;
		CutModelData bt = null;
		if(dat[0].positionIndices != null && dat[0].positionIndices.length > 0) {
			tp = new CutModelData(dat[0], null, false, boundsCollider(dat[0]));
			top.add(tp);
		}
		if(dat[1].positionIndices != null && dat[1].positionIndices.length > 0) {
			bt = new CutModelData(dat[1], null, true, boundsCollider(dat[1]));
			bottom.add(bt);
		}
		if(tp != null && bt != null && dat[2].positionIndices != null && dat[2].positionIndices.length > 0) {
			tp.cap = dat[2];
			bt.cap = dat[2];
		}
	}

	private static ConvexMeshCollider boundsCollider(VertexData d) {
		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
		float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		float[] verts = d.vertexArray();
		for(int i = 0; i < verts.length; i++) {
			min[i % 3] = Math.min(min[i % 3], verts[i]);
			max[i % 3] = Math.max(max[i % 3], verts[i]);
		}

		float pad = 0.02F;
		float[] box = new float[24];
		for(int i = 0; i < 8; i++) {
			box[i * 3] = (i & 1) == 0 ? min[0] - pad : max[0] + pad;
			box[i * 3 + 1] = (i & 2) == 0 ? min[1] - pad : max[1] + pad;
			box[i * 3 + 2] = (i & 4) == 0 ? min[2] - pad : max[2] + pad;
		}
		return new ConvexMeshCollider(BOUNDS_INDICES, box, 1);
	}

	private static Triangle[] captureMobTriangles(EntityLivingBase e, float partialTicks) {
		Render render = RenderManager.instance.getEntityRenderObject(e);
		if(render == null) return new Triangle[0];

		Triangle[] tris = new Triangle[0];
		if(render instanceof RendererLivingEntity) {
			RendererLivingEntity living = (RendererLivingEntity) render;
			ModelBase model = get(rMainModel, render);
			tris = captureDraw(() -> collectParts(e, living, model, partialTicks, true));
		}
		if(tris.length == 0) tris = captureDraw(() -> render.doRender(e, 0D, 0D, 0D, 0F, partialTicks));
		return tris;
	}

	private static Triangle[] captureDraw(Runnable draw) {
		VIEWPORT.clear();
		GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
		int values = 0;

		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TRANSFORM_BIT);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		try {
			GL11.glLoadIdentity();
			GL11.glScalef(FEEDBACK_SCALE, FEEDBACK_SCALE, FEEDBACK_SCALE);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_CLIP_PLANE0);
			GL11.glEnable(GL11.GL_TEXTURE_2D);

			FEEDBACK_BUFFER.clear();
			GL11.glFeedbackBuffer(GL11.GL_3D_COLOR_TEXTURE, FEEDBACK_BUFFER);
			GL11.glRenderMode(GL11.GL_FEEDBACK);
			try {
				draw.run();
			} finally {
				values = GL11.glRenderMode(GL11.GL_RENDER);
			}
		} finally {
			GL11.glPopMatrix();
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}

		if(values < 0) {
			MainRegistry.logger.warn("The model of a sliced entity is too big to be read back, the cut will be incomplete");
			values = FEEDBACK_BUFFER.capacity();
		}

		List<Triangle> tris = new ArrayList<Triangle>();
		int i = 0;
		while(i < values) {
			int token = (int) FEEDBACK_BUFFER.get(i++);
			if(token == GL11.GL_POLYGON_TOKEN) {
				int count = (int) FEEDBACK_BUFFER.get(i++);
				if(i + count * FEEDBACK_VERTEX > values) break;
				for(int v = 1; v < count - 1; v++) {
					int a = i;
					int b = i + v * FEEDBACK_VERTEX;
					int c = b + FEEDBACK_VERTEX;
					tris.add(new Triangle(feedbackPos(a), feedbackPos(b), feedbackPos(c), new float[] {
							FEEDBACK_BUFFER.get(a + 7), FEEDBACK_BUFFER.get(a + 8),
							FEEDBACK_BUFFER.get(b + 7), FEEDBACK_BUFFER.get(b + 8),
							FEEDBACK_BUFFER.get(c + 7), FEEDBACK_BUFFER.get(c + 8)}));
				}
				i += count * FEEDBACK_VERTEX;
			} else if(token == GL11.GL_POINT_TOKEN) {
				i += FEEDBACK_VERTEX;
			} else if(token == GL11.GL_LINE_TOKEN || token == GL11.GL_LINE_RESET_TOKEN) {
				i += FEEDBACK_VERTEX * 2;
			} else if(token == GL11.GL_PASS_THROUGH_TOKEN) {
				i++;
			} else {
				break;
			}
		}
		return tris.toArray(new Triangle[tris.size()]);
	}

	private static Vec3d feedbackPos(int at) {
		double x = (FEEDBACK_BUFFER.get(at) - VIEWPORT.get(0)) * 2D / VIEWPORT.get(2) - 1D;
		double y = (FEEDBACK_BUFFER.get(at + 1) - VIEWPORT.get(1)) * 2D / VIEWPORT.get(3) - 1D;
		double z = FEEDBACK_BUFFER.get(at + 2) * 2D - 1D;
		return new Vec3d(x / FEEDBACK_SCALE, y / FEEDBACK_SCALE, z / FEEDBACK_SCALE);
	}

	private static Triangle[] capLoops(List<Vec3d[]> edges, float[] plane, List<Triangle> side1, List<Triangle> side2) {
		Matrix3f mat = Matrix3f.normalToMat(new Vec3d(plane[0], plane[1], plane[2]), 0);
		List<List<Vec3d>> loops = new ArrayList<List<Vec3d>>();
		while(!edges.isEmpty()) {
			Vec3d[] start = edges.remove(0);
			List<Vec3d> loop = new ArrayList<Vec3d>();
			loop.add(start[0]);
			Vec3d current = start[1];
			while(current != null && !epsEquals(current, start[0], 0.00001D)) {
				loop.add(current);
				current = findNext(edges, current);
			}
			if(current != null && loop.size() >= 3) loops.add(loop);
		}

		double[] areas = new double[loops.size()];
		double reference = 0;
		for(int l = 0; l < areas.length; l++) {
			List<Vec3d> loop = loops.get(l);
			for(int i = 0; i < loop.size(); i++) {
				Vec3d a = loop.get(i).matTransform(mat);
				Vec3d b = loop.get((i + 1) % loop.size()).matTransform(mat);
				areas[l] += a.x * b.y - b.x * a.y;
			}
			if(Math.abs(areas[l]) > Math.abs(reference)) reference = areas[l];
		}

		List<Triangle> cap = new ArrayList<Triangle>();
		for(int l = 0; l < areas.length; l++) {
			if(areas[l] * reference > 0) fillLoop(loops.get(l), mat, reference > 0, cap, side1, side2);
		}
		return cap.toArray(new Triangle[cap.size()]);
	}

	private static void fillLoop(List<Vec3d> loop, Matrix3f mat, boolean ccw, List<Triangle> cap, List<Triangle> side1, List<Triangle> side2) {
		int n = loop.size();
		Vec3d[] uv = new Vec3d[n];
		List<Integer> rest = new ArrayList<Integer>(n);
		for(int i = 0; i < n; i++) {
			uv[i] = loop.get(i).matTransform(mat);
			rest.add(i);
		}

		while(rest.size() > 3) {
			int m = rest.size();
			int ear = 0;
			for(int i = 0; i < m; i++) {
				if(isEar(uv, rest, i, ccw)) {
					ear = i;
					break;
				}
			}
			addCapTriangle(loop, uv, rest.get((ear + m - 1) % m), rest.get(ear), rest.get((ear + 1) % m), cap, side1, side2);
			rest.remove(ear);
		}
		addCapTriangle(loop, uv, rest.get(0), rest.get(1), rest.get(2), cap, side1, side2);
	}

	private static boolean isEar(Vec3d[] uv, List<Integer> rest, int i, boolean ccw) {
		int m = rest.size();
		int ia = rest.get((i + m - 1) % m);
		int ib = rest.get(i);
		int ic = rest.get((i + 1) % m);
		double sign = ccw ? 1D : -1D;
		if(turn(uv[ia], uv[ib], uv[ic]) * sign <= 0) return false;

		for(int j = 0; j < m; j++) {
			int ip = rest.get(j);
			if(ip == ia || ip == ib || ip == ic) continue;
			if(turn(uv[ia], uv[ib], uv[ip]) * sign >= 0 && turn(uv[ib], uv[ic], uv[ip]) * sign >= 0 && turn(uv[ic], uv[ia], uv[ip]) * sign >= 0) return false;
		}
		return true;
	}

	private static double turn(Vec3d o, Vec3d a, Vec3d b) {
		return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
	}

	private static void addCapTriangle(List<Vec3d> loop, Vec3d[] uv, int ia, int ib, int ic, List<Triangle> cap, List<Triangle> side1, List<Triangle> side2) {
		Vec3d a = loop.get(ia);
		Vec3d b = loop.get(ib);
		Vec3d c = loop.get(ic);
		cap.add(new Triangle(a, c, b, new float[] {(float) uv[ia].x, (float) uv[ia].y, (float) uv[ic].x, (float) uv[ic].y, (float) uv[ib].x, (float) uv[ib].y}));
		side1.add(new Triangle(a, c, b, new float[6]));
		side2.add(new Triangle(a, b, c, new float[6]));
	}

	private static void generateChunks(List<List<CutModelData>> chunks, List<CutModelData> toSort) {
		GJK.margin = 0.01F;
		try {
			List<CutModelData> chunk = new ArrayList<CutModelData>();
			boolean removed = false;
			float grow = 0.01F;
			//Basically we're trying to build little islands of colliders that are together so we don't get stuff like floating geometry
			while(!toSort.isEmpty()) {
				removed = false;
				//Not as efficient as it could be, but it won't matter with the amount of cubes we're dealing with.
				List<CutModelData> toAdd = new ArrayList<CutModelData>(2);
				for(CutModelData c : chunk) {
					Iterator<CutModelData> itr = toSort.iterator();
					while(itr.hasNext()) {
						CutModelData d = itr.next();
						if(d.collider.localBox.expand(grow, grow, grow).intersectsWith(c.collider.localBox)) {
							if(GJK.collidesAny(null, null, c.collider, d.collider)) {
								//Something got removed, which means we have to iterate again to check if that one is connected to anything.
								removed = true;
								toAdd.add(d);
								itr.remove();
							}
						}
					}
				}
				chunk.addAll(toAdd);
				//If nothing else got added to the chunk, it's complete, so we can add it to the final list.
				if(!removed) {
					if(!chunk.isEmpty()) {
						chunks.add(chunk);
						chunk = new ArrayList<CutModelData>();
					}
					chunk.add(toSort.remove(0));
				}
			}
			if(!chunk.isEmpty()) {
				chunks.add(chunk);
			}
		} finally {
			GJK.margin = 0;
		}
	}

	public static class ModelPart {

		public final Matrix4f matrix;
		public final ModelRenderer renderer;

		public ModelPart(Matrix4f matrix, ModelRenderer renderer) {
			this.matrix = matrix;
			this.renderer = renderer;
		}
	}

	public static class CutModelData {

		public final VertexData data;
		public VertexData cap;
		public final boolean flip;
		public final ConvexMeshCollider collider;

		public CutModelData(VertexData data, VertexData cap, boolean flip, ConvexMeshCollider collider) {
			this.data = data;
			this.cap = cap;
			this.flip = flip;
			this.collider = collider;
		}
	}

	public static class VertexData {

		public Vec3d[] positions;
		public int[] positionIndices;
		public float[] texCoords;

		public void tessellate(Tessellator tes, boolean flip) {
			if(positionIndices == null) return;
			for(int i = 0; i < positionIndices.length; i += 3) {
				Vec3d a = positions[positionIndices[i]];
				Vec3d b = positions[positionIndices[i + 1]];
				Vec3d c = positions[positionIndices[i + 2]];
				//Offset into texcoord array
				int tOB = 1;
				int tOC = 2;
				if(flip) {
					Vec3d tmp = b;
					b = c;
					c = tmp;
					tOB = 2;
					tOC = 1;
				}
				Vec3d norm = b.subtract(a).crossProduct(c.subtract(a)).normalize();
				tes.setNormal((float) norm.x, (float) norm.y, (float) norm.z);
				tes.addVertexWithUV(a.x, a.y, a.z, texCoords[i * 2 + 0], texCoords[i * 2 + 1]);
				tes.addVertexWithUV(b.x, b.y, b.z, texCoords[(i + tOB) * 2 + 0], texCoords[(i + tOB) * 2 + 1]);
				tes.addVertexWithUV(c.x, c.y, c.z, texCoords[(i + tOC) * 2 + 0], texCoords[(i + tOC) * 2 + 1]);
			}
		}

		public float[] vertexArray() {
			float[] verts = new float[positions.length * 3];
			for(int i = 0; i < positions.length; i++) {
				Vec3d pos = positions[i];
				verts[i * 3] = (float) pos.x;
				verts[i * 3 + 1] = (float) pos.y;
				verts[i * 3 + 2] = (float) pos.z;
			}
			return verts;
		}
	}
}
