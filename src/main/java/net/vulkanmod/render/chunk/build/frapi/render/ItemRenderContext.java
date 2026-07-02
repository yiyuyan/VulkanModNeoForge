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

package net.vulkanmod.render.chunk.build.frapi.render;

import java.util.Arrays;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;

import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.GlintMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.mixin.render.frapi.ItemRendererAccessor;
import net.vulkanmod.render.chunk.build.frapi.helper.ColorHelper;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;

public class ItemRenderContext extends AbstractRenderContext {
	private static final long ITEM_RANDOM_SEED = 42L;

	private final RandomSource random = RandomSource.create();
	private final Supplier<RandomSource> randomSupplier = () -> {
		random.setSeed(ITEM_RANDOM_SEED);
		return random;
	};

	private ItemStack itemStack;
	private ItemDisplayContext transformMode;
	private PoseStack matrixStack;
	private MultiBufferSource vertexConsumerProvider;
	private int lightmap;
	private int[] tints;

	private boolean isDefaultTranslucent;
	private boolean isTranslucentDirect;

	private RenderType defaultLayer;
	private GlintMode defaultGlint;

	private PoseStack.Pose specialGlintEntry;
	private final VertexConsumer[] vertexConsumerCache = new VertexConsumer[12];

	public void renderModel(ItemStack itemStack, ItemDisplayContext transformMode, boolean invert, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int lightmap, int overlay, BakedModel model) {
		this.itemStack = itemStack;
		this.transformMode = transformMode;
		this.matrixStack = matrixStack;
		this.vertexConsumerProvider = vertexConsumerProvider;
		this.lightmap = lightmap;
		this.overlay = overlay;
		this.tints = computeTints(itemStack);

		computeOutputInfo();

		matrix = matrixStack.last().pose();
		normalMatrix = matrixStack.last().normal();

		((FabricBakedModel)model).emitItemQuads(getEmitter(), randomSupplier);

		this.itemStack = null;
		this.matrixStack = null;
		this.vertexConsumerProvider = null;
		this.tints = null;

		specialGlintEntry = null;
		Arrays.fill(vertexConsumerCache, null);
	}

	public void renderModel(ItemDisplayContext transformMode, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int lightmap, int overlay, int[] tints, BakedModel model, RenderType renderType, ItemStackRenderState.FoilType foilType) {
		this.itemStack = null;
		this.transformMode = transformMode;
		this.matrixStack = matrixStack;
		this.vertexConsumerProvider = vertexConsumerProvider;
		this.lightmap = lightmap;
		this.overlay = overlay;
		this.tints = tints;

		isDefaultTranslucent = true;
		isTranslucentDirect = true;
		this.defaultLayer = renderType;
		this.defaultGlint = foilType == ItemStackRenderState.FoilType.NONE ? GlintMode.NONE : GlintMode.DEFAULT;

		matrix = matrixStack.last().pose();
		normalMatrix = matrixStack.last().normal();

		((FabricBakedModel)model).emitItemQuads(getEmitter(), randomSupplier);

		this.matrixStack = null;
		this.vertexConsumerProvider = null;
		this.tints = null;

		specialGlintEntry = null;
		Arrays.fill(vertexConsumerCache, null);
	}

	private int[] computeTints(ItemStack stack) {
		int[] tints = new int[32];
		Arrays.fill(tints, -1);
		return tints;
	}

	private void computeOutputInfo() {
		isDefaultTranslucent = true;
		isTranslucentDirect = true;

		Item item = itemStack.getItem();

		if (item instanceof BlockItem blockItem) {
			BlockState state = blockItem.getBlock().defaultBlockState();
			RenderType renderLayer = ItemBlockRenderTypes.getChunkRenderType(state);

			if (renderLayer != RenderType.translucent()) {
				isDefaultTranslucent = false;
			}

			if (transformMode != ItemDisplayContext.GUI && !transformMode.firstPerson()) {
				isTranslucentDirect = false;
			}
		}

		defaultLayer = isDefaultTranslucent ? Sheets.translucentItemSheet() : Sheets.cutoutBlockSheet();
		defaultGlint = GlintMode.DEFAULT;
	}

	@Override
	protected void bufferQuad(MutableQuadViewImpl quad) {
		final RenderMaterial mat = quad.material();
		final boolean emissive = mat.emissive();
		final VertexConsumer vertexConsumer = getVertexConsumer(mat.blendMode(), mat.glintMode());

		tintQuad(quad);
		shadeQuad(quad, emissive);
		bufferQuad(quad, vertexConsumer);
	}

	private void tintQuad(MutableQuadViewImpl quad) {
		int tintIndex = quad.tintIndex();

		if (tintIndex != -1 && tintIndex < tints.length) {
			final int tint = tints[tintIndex];

			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyColor(tint, quad.color(i)));
			}
		}
	}

	private void shadeQuad(MutableQuadViewImpl quad, boolean emissive) {
		if (emissive) {
			for (int i = 0; i < 4; i++) {
				quad.lightmap(i, LightTexture.FULL_BRIGHT);
			}
		} else {
			final int lightmap = this.lightmap;

			for (int i = 0; i < 4; i++) {
				quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), lightmap));
			}
		}
	}

	private VertexConsumer getVertexConsumer(BlendMode blendMode, GlintMode glintMode) {
		RenderType layer;
		GlintMode glint;

		if (blendMode == BlendMode.DEFAULT) {
			layer = defaultLayer;
		} else {
			layer = blendMode == BlendMode.TRANSLUCENT ? Sheets.translucentItemSheet() : Sheets.cutoutBlockSheet();
		}

		if (glintMode == GlintMode.DEFAULT) {
			glint = defaultGlint;
		} else {
			glint = glintMode;
		}

		int cacheIndex;

		if (layer == Sheets.translucentItemSheet()) {
			cacheIndex = 0;
		} else if (layer == Sheets.cutoutBlockSheet()) {
			cacheIndex = 4;
		} else {
			cacheIndex = 8;
		}

		cacheIndex += glint.ordinal();
		VertexConsumer vertexConsumer = vertexConsumerCache[cacheIndex];

		if (vertexConsumer == null) {
			vertexConsumer = createVertexConsumer(layer, glint);
			vertexConsumerCache[cacheIndex] = vertexConsumer;
		}

		return vertexConsumer;
	}

	private VertexConsumer createVertexConsumer(RenderType layer, GlintMode glint) {
		if (glint == GlintMode.SPECIAL) {
			if (specialGlintEntry == null) {
				specialGlintEntry = matrixStack.last().copy();

				if (transformMode == ItemDisplayContext.GUI) {
					MatrixUtil.mulComponentWise(specialGlintEntry.pose(), 0.5F);
				} else if (transformMode.firstPerson()) {
					MatrixUtil.mulComponentWise(specialGlintEntry.pose(), 0.75F);
				}
			}

			return ItemRendererAccessor.getCompassFoilBuffer(vertexConsumerProvider, layer, specialGlintEntry);
		}

		return ItemRenderer.getFoilBuffer(vertexConsumerProvider, layer, true, glint.glint != null);
	}
}
