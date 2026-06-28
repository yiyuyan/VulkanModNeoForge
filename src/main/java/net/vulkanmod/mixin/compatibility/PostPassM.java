package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.Renderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Map;

@Mixin(PostPass.class)
public abstract class PostPassM {
    @Shadow @Final private String name;

    @Shadow @Final private List<PostPass.Input> inputs;
    @Shadow @Final private ResourceLocation outputTargetId;
    @Shadow @Final private CompiledShaderProgram shader;
    @Shadow @Final private List<PostChainConfig.Uniform> uniforms;

    @Shadow protected abstract void restoreDefaultUniforms();

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void addToFrame(FrameGraphBuilder frameGraphBuilder, Map<ResourceLocation, ResourceHandle<RenderTarget>> map, Matrix4f matrix4f) {
        FramePass framePass = frameGraphBuilder.addPass(this.name);

        for (PostPass.Input input : this.inputs) {
            input.addToPass(framePass, map);
        }

        ResourceHandle<RenderTarget> resourceHandle = map.computeIfPresent(
                this.outputTargetId, (resourceLocation, resourceHandlex) -> framePass.readsAndWrites(resourceHandlex)
        );
        if (resourceHandle == null) {
            throw new IllegalStateException("Missing handle for target " + this.outputTargetId);
        } else {
            framePass.executes(() -> {
                RenderTarget renderTarget = resourceHandle.get();
                RenderSystem.viewport(0, 0, renderTarget.width, renderTarget.height);

                for (PostPass.Input inputx : this.inputs) {
                    if (inputx instanceof PostPass.TargetInput) {
                        var targetId = ((PostPass.TargetInput) inputx).targetId();
                        var inTarget = map.get(targetId).get();

                        if (inTarget instanceof MainTarget) {
                            inTarget.bindRead();
                        }
                    }

                    inputx.bindTo(this.shader, map);
                }

                this.shader.safeGetUniform("OutSize").set((float)renderTarget.width, (float)renderTarget.height);

                for (PostChainConfig.Uniform uniform : this.uniforms) {
                    Uniform uniform2 = this.shader.getUniform(uniform.name());
                    if (uniform2 != null) {
                        uniform2.setFromConfig(uniform.values(), uniform.values().size());
                    }
                }

                renderTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                renderTarget.clear();
                renderTarget.bindWrite(false);
                RenderSystem.depthFunc(519);
                RenderSystem.setShader(this.shader);
                RenderSystem.backupProjectionMatrix();
                RenderSystem.setProjectionMatrix(matrix4f, ProjectionType.ORTHOGRAPHIC);

                Renderer.setInvertedViewport(0, 0, renderTarget.width, renderTarget.height);
                Renderer.resetScissor();

                BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                bufferBuilder.addVertex(0.0F, 0.0F, 500.0F);
                bufferBuilder.addVertex((float)renderTarget.width, 0.0F, 500.0F);
                bufferBuilder.addVertex((float)renderTarget.width, (float)renderTarget.height, 500.0F);
                bufferBuilder.addVertex(0.0F, (float)renderTarget.height, 500.0F);

                BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
                RenderSystem.depthFunc(515);
                RenderSystem.restoreProjectionMatrix();
                renderTarget.unbindWrite();

                Renderer.resetViewport();

                for (PostPass.Input input2 : this.inputs) {
                    input2.cleanup(map);
                }

                this.restoreDefaultUniforms();
            });
        }
    }

}
