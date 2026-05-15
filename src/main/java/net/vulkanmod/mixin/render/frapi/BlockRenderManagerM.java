package net.vulkanmod.mixin.render.frapi;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderManagerM {

    @Shadow
    @Final
    private ModelBlockRenderer modelRenderer;

    @Shadow
    @Final
    private BlockColors blockColors;

    @Inject(method = "renderBreakingTexture", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;getBlockModel(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/model/BlockStateModel;", shift = At.Shift.AFTER), cancellable = true)
    private void afterGetModel(BlockState blockState, BlockPos blockPos, BlockAndTintGetter world, PoseStack matrixStack, VertexConsumer vertexConsumer, CallbackInfo ci, @Local BlockStateModel model) {
        VulkanModRenderer.INSTANCE.render(modelRenderer,world,model,blockState,blockPos,matrixStack,vertexConsumer,true,blockState.getSeed(blockPos),OverlayTexture.NO_OVERLAY);
        //modelRenderer.render(world, model, blockState, blockPos, matrixStack, layer -> vertexConsumer, true, blockState.getSeed(blockPos), OverlayTexture.NO_OVERLAY);
        ci.cancel();
    }

    @Redirect(method = "renderSingleBlock(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/block/model/BlockStateModel;FFFIILnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void renderProxy(PoseStack.Pose entry, MultiBufferSource bufferSource, BlockStateModel arg2, float f, float g, float h, int i, int j, BlockAndTintGetter level, BlockPos pos, BlockState state) {
        VulkanModRenderer.INSTANCE.render(entry, (VertexConsumer) bufferSource,arg2,f,g,h,i,j,EmptyBlockAndTintGetter.INSTANCE,BlockPos.ZERO, Blocks.AIR.defaultBlockState());
        //FabricBlockModelRenderer.render(entry, RenderLayerHelper.entityDelegate(vertexConsumers), model, red, green, blue, light, overlay, EmptyBlockAndTintGetter.INSTANCE, BlockPos.ZERO, state);
    }
}