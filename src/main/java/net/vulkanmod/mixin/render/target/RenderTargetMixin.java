package net.vulkanmod.mixin.render.target;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.CoreShaders;
import net.vulkanmod.gl.GlFramebuffer;
import net.vulkanmod.gl.GlTexture;
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

import java.util.Objects;

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

    @Inject(method = "resize",at = @At("HEAD"),cancellable = true)
    private void resize(int i, int j, CallbackInfo ci){

    }

    /**
     * @author
     */
    @Overwrite
    public void clear() {
        RenderSystem.assertOnRenderThreadOrInit();

        if(!Renderer.isRecording())
            return;

        // If the framebuffer is not bound postpone clear
        GlFramebuffer glFramebuffer = GlFramebuffer.getFramebuffer(this.frameBufferId);
        if (!bound || GlFramebuffer.getBoundFramebuffer() != glFramebuffer) {
            needClear = true;
            return;
        }

        GlStateManager._clearColor(this.clearChannels[0], this.clearChannels[1], this.clearChannels[2], this.clearChannels[3]);
        int i = 16384;
        if (this.useDepth) {
            GlStateManager._clearDepth(1.0);
            i |= 256;
        }

        GlStateManager._clear(i);
        needClear = false;
    }

    /**
     * @author
     */
    @Overwrite
    public void bindRead() {
        RenderSystem.assertOnRenderThread();

        applyClear();

        GlTexture.bindTexture(this.colorTextureId);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            GlTexture.getBoundTexture().getVulkanImage()
                    .readOnlyLayout(stack, Renderer.getCommandBuffer());
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void unbindRead() {
        RenderSystem.assertOnRenderThreadOrInit();
        GlTexture.bindTexture(0);
    }

    /**
     * @author
     */
    @Overwrite
    public void bindWrite(boolean bl) {
        RenderSystem.assertOnRenderThreadOrInit();

        GlFramebuffer.bindFramebuffer(GL30.GL_FRAMEBUFFER, this.frameBufferId);
        if (bl) {
            GlStateManager._viewport(0, 0, this.viewWidth, this.viewHeight);
        }

        this.bound = true;
        if (needClear)
            clear();
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

    @Inject(method = "blitToScreen", at = @At("HEAD"), cancellable = true)
    private void blitToScreen(int width, int height, CallbackInfo ci) {
        // If the target needs clear it means it has not been used, thus we can skip blit
        if (!this.needClear) {
            Framebuffer framebuffer = GlFramebuffer.getFramebuffer(this.frameBufferId).getFramebuffer();
            VTextureSelector.bindTexture(0, framebuffer.getColorAttachment());

            DrawUtil.blitToScreen();
        }

        ci.cancel();
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void blitAndBlendToScreen(int i, int j) {
        RenderSystem.assertOnRenderThread();

        if (this.needClear) {
            return;
        }

        GlStateManager._colorMask(true, true, true, false);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._viewport(0, 0, i, j);

        CompiledShaderProgram compiledShaderProgram = Objects.requireNonNull(
                RenderSystem.setShader(CoreShaders.BLIT_SCREEN), "Blit shader not loaded"
        );

        int prevTexture = RenderSystem.getShaderTexture(0);
        RenderSystem.setShaderTexture(0, this.colorTextureId);

//        compiledShaderProgram.bindSampler("InSampler", this.colorTextureId);
        BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
        bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.setShaderTexture(0, prevTexture);
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
    }

    @Inject(method = "getColorTextureId", at = @At("HEAD"))
    private void injClear(CallbackInfoReturnable<Integer> cir) {
        applyClear();
    }

    @Unique
    private void applyClear() {
        if (this.needClear) {
            GlFramebuffer currentFramebuffer = GlFramebuffer.getBoundFramebuffer();

            this.bindWrite(false);

            if (currentFramebuffer != null) {
                GlFramebuffer.beginRendering(currentFramebuffer);
            }
        }
    }
}
