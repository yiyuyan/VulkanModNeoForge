package net.vulkanmod.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.vulkanmod.vulkan.VRenderSystem;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "setupFog", at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;onSetupFog(Lnet/minecraft/client/renderer/fog/environment/FogEnvironment;Lnet/minecraft/world/level/material/FogType;Lnet/minecraft/client/Camera;FFLnet/minecraft/client/renderer/fog/FogData;)V",
    shift = At.Shift.BEFORE)
    )
    private void onSetupFog(Camera camera, int i, boolean bl, DeltaTracker deltaTracker, float f,
                            ClientLevel clientLevel, CallbackInfoReturnable<Vector4f> cir,
                            @Local FogData fogdata, @Local Vector4f fogColor) {
        VRenderSystem.fogData = fogdata;
        VRenderSystem.setShaderFogColor(fogColor.x(), fogColor.y(), fogColor.z(), fogColor.w());
    }
}
