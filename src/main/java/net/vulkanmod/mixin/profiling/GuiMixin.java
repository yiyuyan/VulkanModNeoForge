package net.vulkanmod.mixin.profiling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.vulkanmod.render.profiling.ProfilerOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Shadow
    @Final
    protected DebugScreenOverlay debugScreen;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void createProfilerOverlay(Minecraft arg, ItemRenderer arg2, CallbackInfo ci) {
        ProfilerOverlay.createInstance(arg);
    }

    @Inject(method = "render", at = @At(value = "RETURN"))
    private void renderProfilerOverlay(GuiGraphics guiGraphics, float delta, CallbackInfo ci) {
        if(ProfilerOverlay.shouldRender && this.minecraft.options.renderDebug)
            ProfilerOverlay.INSTANCE.render(guiGraphics);
    }
}
