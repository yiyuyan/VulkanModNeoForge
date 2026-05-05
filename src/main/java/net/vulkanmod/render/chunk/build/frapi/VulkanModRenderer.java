package net.vulkanmod.render.chunk.build.frapi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.mixin.render.frapi.BlockRenderDispatcherAccessor;
import net.vulkanmod.render.chunk.build.frapi.accessor.AccessLayerRenderState;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.QuadEmitter;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableMeshImpl;
import net.vulkanmod.render.chunk.build.frapi.mesh.QuadViewImpl;
import net.vulkanmod.render.chunk.build.frapi.render.BlockRenderContext;
import net.vulkanmod.render.chunk.build.frapi.render.SimpleBlockRenderContext;

/**
 * Fabric renderer implementation.
 */
public class VulkanModRenderer {
	public static final VulkanModRenderer INSTANCE = new VulkanModRenderer();

	private VulkanModRenderer() {}

	//@Override
	public MutableMeshImpl mutableMesh() {
		return new MutableMeshImpl();
	}

	//@Override
	public void render(ModelBlockRenderer modelBlockRenderer, BlockAndTintGetter blockAndTintGetter,
					   BlockStateModel blockStateModel, BlockState blockState, BlockPos blockPos, PoseStack poseStack,
					   VertexConsumer blockVertexConsumerProvider, boolean cull, long seed, int overlay) {
		BlockRenderContext.POOL.get().render(blockAndTintGetter, blockStateModel, blockState, blockPos, poseStack, blockVertexConsumerProvider, cull, seed, overlay);
	}

	//@Override
	public void render(PoseStack.Pose pose, VertexConsumer blockVertexConsumerProvider, BlockStateModel blockStateModel,
					   float v, float v1, float v2, int i, int i1, BlockAndTintGetter blockAndTintGetter,
					   BlockPos blockPos, BlockState blockState) {
		SimpleBlockRenderContext.POOL.get().bufferModel(pose, blockVertexConsumerProvider, blockStateModel, v, v1, v2, i, i1, blockAndTintGetter, blockPos, blockState);
	}

	//@Override
	public void renderBlockAsEntity(BlockRenderDispatcher blockRenderDispatcher, BlockState blockState,
									PoseStack poseStack, MultiBufferSource multiBufferSource, int light, int overlay,
									BlockAndTintGetter blockAndTintGetter, BlockPos pos) {
		RenderShape blockRenderType = blockState.getRenderShape();

		if (blockRenderType != RenderShape.INVISIBLE) {
			BlockStateModel model = blockRenderDispatcher.getBlockModel(blockState);
			int tint = ((BlockRenderDispatcherAccessor) blockRenderDispatcher).getBlockColors().getColor(blockState, null, null, 0);
			float red = (tint >> 16 & 255) / 255.0F;
			float green = (tint >> 8 & 255) / 255.0F;
			float blue = (tint & 255) / 255.0F;
			//render(poseStack.last(),multiBufferSource.getBuffer((RenderLayerHelper.getEntityBlockLayer(layer)),)
			//FabricBlockModelRenderer.render(poseStack.last(), layer -> multiBufferSource.getBuffer(RenderLayerHelper.getEntityBlockLayer(layer)), model, red, green, blue, light, overlay, blockAndTintGetter, pos, blockState);
            ((BlockRenderDispatcherAccessor) blockRenderDispatcher).getBlockEntityModelsGetter().get().renderByBlock(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, Minecraft.getInstance().gameRenderer.getSubmitNodeStorage(), light, overlay, 0);
		}
	}

	//@Override
	public QuadEmitter getLayerRenderStateEmitter(ItemStackRenderState.LayerRenderState layer) {
		return ((AccessLayerRenderState) layer).getMutableMesh().emitter();
	}


}
