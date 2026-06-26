package net.vulkanmod.mixin.render.target;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.VkGlFramebuffer;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.DrawUtil;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {

    @Shadow public int viewWidth;
    @Shadow public int viewHeight;
    @Shadow public int width;
    @Shadow public int height;

    @Shadow protected int depthBufferId;
    @Shadow protected int colorTextureId;
    @Shadow public int frameBufferId;

    @Shadow @Final private float[] clearChannels;
    @Shadow @Final public boolean useDepth;

    @Unique
    boolean needClear = false;
    @Unique
    boolean bound = false;

    @Inject(method = "_resize",at = @At("HEAD"),cancellable = true)
    private void resize(int i, int j, boolean bl, CallbackInfo ci){

    }

    /**
     * @author
     */
    @Overwrite
    public void clear(boolean getError) {
        RenderSystem.assertOnRenderThreadOrInit();

        if(!Renderer.isRecording())
            return;

        // If the framebuffer is not bound postpone clear
        VkGlFramebuffer glFramebuffer = VkGlFramebuffer.getFramebuffer(this.frameBufferId);
        if(!bound || VkGlFramebuffer.getBoundFramebuffer() != glFramebuffer) {
            needClear = true;
            return;
        }

        GlStateManager._clearColor(this.clearChannels[0], this.clearChannels[1], this.clearChannels[2], this.clearChannels[3]);
        int i = 16384;
        if (this.useDepth) {
            GlStateManager._clearDepth(1.0);
            i |= 256;
        }

        GlStateManager._clear(i, getError);
        needClear = false;
    }

    /**
     * @author
     */
    @Overwrite
    public void bindRead() {
        RenderSystem.assertOnRenderThread();

        applyClear();

        VkGlTexture.bindTexture(this.colorTextureId);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkGlTexture.getBoundTexture().getVulkanImage()
                    .readOnlyLayout(stack, Renderer.getCommandBuffer());
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void unbindRead() {
        RenderSystem.assertOnRenderThreadOrInit();
        VkGlTexture.bindTexture(0);
    }

    /**
     * @author
     */
    @Overwrite
    private void _bindWrite(boolean bl) {
        RenderSystem.assertOnRenderThreadOrInit();

        VkGlFramebuffer.bindFramebuffer(GL30.GL_FRAMEBUFFER, this.frameBufferId);
        if (bl) {
            GlStateManager._viewport(0, 0, this.viewWidth, this.viewHeight);
        }

        this.bound = true;
        if (needClear)
            clear(false);
    }

    /**
     * @author
     */
    @Overwrite
    public void unbindWrite() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> {
                GlStateManager._glBindFramebuffer(36160, 0);
                this.bound = false;
            });
        } else {
            GlStateManager._glBindFramebuffer(36160, 0);
            this.bound = false;
        }
    }

    @Inject(method = "_blitToScreen", at = @At("HEAD"), cancellable = true)
    private void _blitToScreen(int width, int height, boolean disableBlend, CallbackInfo ci) {
        // If the target needs clear it means it has not been used, thus we can skip blit
        if (!this.needClear) {
            Framebuffer framebuffer = VkGlFramebuffer.getFramebuffer(this.frameBufferId).getFramebuffer();
            VTextureSelector.bindTexture(0, framebuffer.getColorAttachment());

            DrawUtil.blitToScreen();
        }

        ci.cancel();
    }

    @Inject(method = "getColorTextureId", at = @At("HEAD"))
    private void injClear(CallbackInfoReturnable<Integer> cir) {
        applyClear();
    }

    @Unique
    private void applyClear() {
        if (this.needClear) {
            VkGlFramebuffer currentFramebuffer = VkGlFramebuffer.getBoundFramebuffer();

            this._bindWrite(false);

            if (currentFramebuffer != null) {
                VkGlFramebuffer.beginRendering(currentFramebuffer);
            }
        }
    }
}
