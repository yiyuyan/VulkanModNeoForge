package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.vulkanmod.render.profiling.Profiler;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    // These are cosmetic FPS-profiler push/pop hooks. Their injection points target
    // renderSectionLayer/ParticleEngine#render by ordinal inside renderLevel, which
    // NeoForge coremods can shift or remove. require = 0 keeps a moved/absent target
    // from throwing a MixinTransformerError and crashing the game during init.
    @Inject(require = 0, method ="renderClouds", at = @At("HEAD"))
    private void pushProfiler(PoseStack arg, Matrix4f matrix4f, float g, double d, double e, double h, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.push("Clouds");
    }

    @Inject(require = 0, method = "renderClouds", at = @At("RETURN"))
    private void popProfiler(PoseStack arg, Matrix4f matrix4f, float g, double d, double e, double h, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            shift = At.Shift.BEFORE))
    private void pushProfiler3(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.push("Particles");
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            shift = At.Shift.AFTER))
    private void popProfiler3(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            ordinal = 0,
            shift = At.Shift.BEFORE))
    private void profilerTerrain1(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.push("Opaque_terrain");
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            ordinal = 2,
            shift = At.Shift.BEFORE))
    private void profilerTerrain2(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
        profiler.push("entities");
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            ordinal = 3,
            shift = At.Shift.BEFORE))
    private void profilerTerrain3_0(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
        profiler.push("Translucent_terrain");
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            ordinal = 5,
            shift = At.Shift.BEFORE))
    private void profilerTerrain3_1(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
        profiler.push("Translucent_terrain");
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            ordinal = 4,
            shift = At.Shift.BEFORE))
    private void profilerTerrain4_0(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
    }

    @Inject(require = 0, method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            ordinal = 6,
            shift = At.Shift.BEFORE))
    private void profilerTerrain4_1(PoseStack arg, float g, long l, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f2, CallbackInfo ci) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
    }
}
