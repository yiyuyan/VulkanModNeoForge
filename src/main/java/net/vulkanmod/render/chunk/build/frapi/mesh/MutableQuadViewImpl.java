/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.vulkanmod.render.chunk.build.frapi.mesh;

import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.HEADER_BITS;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.HEADER_FACE_NORMAL;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.HEADER_STRIDE;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.HEADER_TAG;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.HEADER_TINT_INDEX;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.VERTEX_COLOR;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.VERTEX_LIGHTMAP;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.VERTEX_NORMAL;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.VERTEX_STRIDE;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.VERTEX_U;
import static net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat.VERTEX_X;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import net.vulkanmod.render.chunk.build.frapi.helper.ColorHelper;
import net.vulkanmod.render.chunk.build.frapi.helper.NormalHelper;
import net.vulkanmod.render.chunk.build.frapi.helper.TextureHelper;
import net.vulkanmod.render.chunk.build.frapi.material.RenderMaterialImpl;
import net.vulkanmod.render.model.quad.ModelQuadView;

public abstract class MutableQuadViewImpl extends QuadViewImpl implements QuadEmitter {
	private static final QuadTransform NO_TRANSFORM = q -> true;

	private static final int[] DEFAULT_QUAD_DATA = new int[EncodingFormat.TOTAL_STRIDE];

	static {
		MutableQuadViewImpl quad = new MutableQuadViewImpl() {
			@Override
			protected void emitDirectly() {
			}
		};

		quad.data = DEFAULT_QUAD_DATA;
		quad.color(-1, -1, -1, -1);
		quad.cullFace(null);
		quad.material(VulkanModRenderer.STANDARD_MATERIAL);
		quad.tintIndex(-1);
	}

	private QuadTransform activeTransform = NO_TRANSFORM;
	private final ObjectArrayList<QuadTransform> transformStack = new ObjectArrayList<>();
	private final QuadTransform stackTransform = q -> {
		int i = transformStack.size() - 1;

		while (i >= 0) {
			if (!transformStack.get(i--).transform(q)) {
				return false;
			}
		}

		return true;
	};

	public final void clear() {
		System.arraycopy(DEFAULT_QUAD_DATA, 0, data, baseIndex, EncodingFormat.TOTAL_STRIDE);
		isGeometryInvalid = true;
		nominalFace = null;
	}

	@Override
	public final MutableQuadViewImpl pos(int vertexIndex, float x, float y, float z) {
		final int index = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_X;
		data[index] = Float.floatToRawIntBits(x);
		data[index + 1] = Float.floatToRawIntBits(y);
		data[index + 2] = Float.floatToRawIntBits(z);
		isGeometryInvalid = true;
		return this;
	}

	@Override
	public final MutableQuadViewImpl color(int vertexIndex, int color) {
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_COLOR] = color;
		return this;
	}

	@Override
	public final MutableQuadViewImpl uv(int vertexIndex, float u, float v) {
		final int i = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_U;
		data[i] = Float.floatToRawIntBits(u);
		data[i + 1] = Float.floatToRawIntBits(v);
		return this;
	}

	@Override
	public final MutableQuadViewImpl spriteBake(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int bakeFlags) {
		TextureHelper.bakeSprite(this, sprite, bakeFlags);
		return this;
	}

	@Override
	public final MutableQuadViewImpl lightmap(int vertexIndex, int lightmap) {
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_LIGHTMAP] = lightmap;
		return this;
	}

	protected final void normalFlags(int flags) {
		data[baseIndex + HEADER_BITS] = EncodingFormat.normalFlags(data[baseIndex + HEADER_BITS], flags);
	}

	@Override
	public final MutableQuadViewImpl normal(int vertexIndex, float x, float y, float z) {
		normalFlags(normalFlags() | (1 << vertexIndex));
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_NORMAL] = NormalHelper.packNormal(x, y, z);
		return this;
	}

	public final void populateMissingNormals() {
		final int normalFlags = this.normalFlags();

		if (normalFlags == 0b1111) return;

		final int packedFaceNormal = packedFaceNormal();

		for (int v = 0; v < 4; v++) {
			if ((normalFlags & (1 << v)) == 0) {
				data[baseIndex + v * VERTEX_STRIDE + VERTEX_NORMAL] = packedFaceNormal;
			}
		}

		normalFlags(0b1111);
	}

	@Override
	public final MutableQuadViewImpl cullFace(@Nullable Direction face) {
		data[baseIndex + HEADER_BITS] = EncodingFormat.cullFace(data[baseIndex + HEADER_BITS], face);
		nominalFace(face);
		return this;
	}

	@Override
	public final MutableQuadViewImpl nominalFace(@Nullable Direction face) {
		nominalFace = face;
		return this;
	}

	@Override
	public final MutableQuadViewImpl material(RenderMaterial material) {
		if (material == null) {
			material = VulkanModRenderer.STANDARD_MATERIAL;
		}

		data[baseIndex + HEADER_BITS] = EncodingFormat.material(data[baseIndex + HEADER_BITS], (RenderMaterialImpl) material);
		return this;
	}

	@Override
	public final MutableQuadViewImpl tintIndex(int tintIndex) {
		data[baseIndex + HEADER_TINT_INDEX] = tintIndex;
		return this;
	}

	@Override
	public final MutableQuadViewImpl tag(int tag) {
		data[baseIndex + HEADER_TAG] = tag;
		return this;
	}

	@Override
	public final MutableQuadViewImpl copyFrom(QuadView quad) {
		final QuadViewImpl q = (QuadViewImpl) quad;
		System.arraycopy(q.data, q.baseIndex, data, baseIndex, EncodingFormat.TOTAL_STRIDE);
		nominalFace = q.nominalFace;
		isGeometryInvalid = q.isGeometryInvalid;

		if (!isGeometryInvalid) {
			faceNormal.set(q.faceNormal);
		}

		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(int[] quadData, int startIndex) {
		System.arraycopy(quadData, startIndex, data, baseIndex + HEADER_STRIDE, VANILLA_QUAD_STRIDE);
		isGeometryInvalid = true;

		int colorIndex = baseIndex + VERTEX_COLOR;

		for (int i = 0; i < 4; i++) {
			data[colorIndex] = ColorHelper.fromVanillaColor(data[colorIndex]);
			colorIndex += VERTEX_STRIDE;
		}

		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(BakedQuad quad, RenderMaterial material, @Nullable Direction cullFace) {
		fromVanilla(quad.getVertices(), 0);
		data[baseIndex + HEADER_BITS] = EncodingFormat.cullFace(0, cullFace);
		nominalFace(quad.getDirection());
		tintIndex(quad.getTintIndex());

		if (!quad.isShade()) {
			material = RenderMaterialImpl.setDisableDiffuse((RenderMaterialImpl) material, true);
		}

		material(material);
		tag(0);

		ModelQuadView quadView = (ModelQuadView) quad;
		int normal = quadView.getNormal();
		data[baseIndex + HEADER_FACE_NORMAL] = normal;
		NormalHelper.unpackNormalTo(normal, faceNormal);

		Direction lightFace = quadView.lightFace();
		data[baseIndex + HEADER_BITS] = EncodingFormat.lightFace(data[baseIndex + HEADER_BITS], lightFace);

		data[baseIndex + HEADER_BITS] = EncodingFormat.geometryFlags(data[baseIndex + HEADER_BITS], quadView.getFlags());

		this.facing = quadView.getQuadFacing();
		this.isGeometryInvalid = false;

		int lightEmission = quad.getLightEmission();

		if (lightEmission > 0) {
			for (int i = 0; i < 4; i++) {
				lightmap(i, LightTexture.lightCoordsWithEmission(lightmap(i), lightEmission));
			}
		}

		return this;
	}

	@Override
	public void pushTransform(QuadTransform transform) {
		if (transform == null) {
			throw new NullPointerException("QuadTransform cannot be null!");
		}

		transformStack.push(transform);

		if (transformStack.size() == 1) {
			activeTransform = transform;
		} else if (transformStack.size() == 2) {
			activeTransform = stackTransform;
		}
	}

	@Override
	public void popTransform() {
		transformStack.pop();

		if (transformStack.size() == 0) {
			activeTransform = NO_TRANSFORM;
		} else if (transformStack.size() == 1) {
			activeTransform = transformStack.get(0);
		}
	}

	protected abstract void emitDirectly();

	public final void transformAndEmit() {
		if (activeTransform.transform(this)) {
			emitDirectly();
		}
	}

	@Override
	public final MutableQuadViewImpl emit() {
		transformAndEmit();
		clear();
		return this;
	}
}
