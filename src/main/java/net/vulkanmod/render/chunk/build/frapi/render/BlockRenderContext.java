package net.vulkanmod.render.chunk.build.frapi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.TriState;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.FabricBlockStateModel;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.ShadeMode;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.light.LightMode;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.ArrayLightDataCache;
import net.vulkanmod.render.chunk.build.light.flat.FlatLightPipeline;
import net.vulkanmod.render.chunk.build.light.smooth.NewSmoothLightPipeline;
import net.vulkanmod.render.chunk.build.light.smooth.SmoothLightPipeline;

/**
 * Context for non-terrain block rendering.
 */
public class BlockRenderContext extends AbstractBlockRenderContext {
	public static final ThreadLocal<BlockRenderContext> POOL = ThreadLocal.withInitial(BlockRenderContext::new);

	private VertexConsumer vertexConsumers;
    private ChunkSectionLayer defaultRenderLayer;

	private final ArrayLightDataCache lightDataCache = new ArrayLightDataCache();

	public BlockRenderContext() {

		LightPipeline flatLightPipeline = new FlatLightPipeline(this.lightDataCache);

		LightPipeline smoothLightPipeline;
		if (Initializer.CONFIG.ambientOcclusion == LightMode.SUB_BLOCK) {
			smoothLightPipeline = new NewSmoothLightPipeline(lightDataCache);
		}
		else {
			smoothLightPipeline = new SmoothLightPipeline(lightDataCache);
		}

		this.setupLightPipelines(flatLightPipeline, smoothLightPipeline);

		random = RandomSource.create();
    }

	public void render(BlockAndTintGetter blockView, BlockStateModel model, BlockState state, BlockPos pos, PoseStack matrixStack, VertexConsumer buffers, boolean cull, long seed, int overlay) {
		Vec3 offset = state.getOffset(pos);
		matrixStack.translate(offset.x, offset.y, offset.z);

		this.blockPos = pos;
		this.vertexConsumers = buffers;
        this.defaultRenderLayer = ItemBlockRenderTypes.getChunkRenderType(state);
		this.matrices = matrixStack.last();
		this.overlay = overlay;
		this.random.setSeed(seed);

		this.lightDataCache.reset(blockView, pos);

		this.prepareForWorld(blockView, cull);
		this.prepareForBlock(state, pos, state.getLightEmission() == 0);

		((FabricBlockStateModel)model).emitQuads(getEmitter(), blockView, pos, state, random, this::isFaceCulled);

		this.vertexConsumers = null;
	}

	@Override
	protected VertexConsumer getVertexConsumer(ChunkSectionLayer layer) {
		return vertexConsumers;
	}

	protected void endRenderQuad(MutableQuadViewImpl quad) {
		final TriState aoMode = quad.ambientOcclusion();
		final boolean ao = this.useAO && (aoMode == TriState.TRUE || (aoMode == TriState.DEFAULT && this.defaultAO));
		final boolean emissive = quad.emissive();
		final boolean vanillaShade = quad.shadeMode() == ShadeMode.VANILLA;
        final ChunkSectionLayer quadRenderLayer = quad.renderLayer();
        final ChunkSectionLayer renderLayer = quadRenderLayer == null ? defaultRenderLayer : quadRenderLayer;
		final VertexConsumer vertexConsumer = getVertexConsumer(renderLayer);

		LightPipeline lightPipeline = ao ? this.smoothLightPipeline : this.flatLightPipeline;

		tintQuad(quad);
		shadeQuad(quad, lightPipeline, emissive, vanillaShade);
		copyLightData(quad);
        bufferQuad(quad, vertexConsumer);
	}

	private void copyLightData(MutableQuadViewImpl quad) {
        for (int i = 0; i < 4; i++) {
			quad.lightmap(i, this.quadLightData.lm[i]);
		}
	}

}
