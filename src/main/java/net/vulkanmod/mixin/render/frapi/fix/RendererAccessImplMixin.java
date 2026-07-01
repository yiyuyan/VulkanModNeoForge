package net.vulkanmod.mixin.render.frapi.fix;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.impl.renderer.RendererAccessImpl;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RendererAccessImpl.class,remap = false)
public class RendererAccessImplMixin {

    @Shadow
    private Renderer activeRenderer;

    @Inject(method = "registerRenderer",at = @At(value = "INVOKE", target = "Ljava/lang/UnsupportedOperationException;<init>(Ljava/lang/String;)V",shift = At.Shift.BEFORE),cancellable = true)
    public void reg(Renderer renderer, CallbackInfo ci){
        if(activeRenderer instanceof VulkanModRenderer){
            Initializer.LOGGER.warn("VulkanModRenderer was already initialized!");
            ci.cancel();
        }
    }
}
