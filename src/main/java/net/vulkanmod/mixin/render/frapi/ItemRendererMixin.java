package net.vulkanmod.mixin.render.frapi;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.vulkanmod.render.chunk.build.frapi.render.ItemRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    @Unique
    private static final ThreadLocal<ItemRenderContext> CONTEXTS = ThreadLocal.withInitial(ItemRenderContext::new);

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private static void hookRenderItem(ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, int[] is, BakedModel model, RenderType renderType, ItemStackRenderState.FoilType foilType, CallbackInfo ci) {
        if (!((FabricBakedModel)model).isVanillaAdapter()) {
            CONTEXTS.get().renderModel(itemDisplayContext, poseStack, multiBufferSource, i, j, is, model, renderType, foilType);
            poseStack.popPose();
            ci.cancel();
        }
    }
}
