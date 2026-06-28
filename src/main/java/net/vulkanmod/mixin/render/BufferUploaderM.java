package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.vulkanmod.interfaces.shader.ShaderMixed;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;

import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BufferUploader.class)
public class BufferUploaderM {

    /**
     * @author
     */
    @Overwrite
    public static void reset() {}

    /**
     * @author
     */
    @Overwrite
    public static void drawWithShader(MeshData meshData) {
        RenderSystem.assertOnRenderThread();

        MeshData.DrawState parameters = meshData.drawState();

        Renderer renderer = Renderer.getInstance();

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        Matrix4f modelView = RenderSystem.getModelViewStack();

        VRenderSystem.applyMVP(modelView, projection);

        if (parameters.vertexCount() > 0) {
            CompiledShaderProgram shaderProgram = RenderSystem.getShader();

            // Prevent drawing if formats don't match to avoid disturbing visual bugs
//            if (shaderProgram.getVertexFormat() != parameters.format()) {
//                meshData.close();
//                return;
//            }

            // Used to update legacy shader uniforms
            // TODO it would be faster to allocate a buffer from stack and set all values
            shaderProgram.apply();
//            shaderProgram.setDefaultUniforms(parameters.mode(), modelView, projection, Minecraft.getInstance().getWindow());

            GraphicsPipeline pipeline = ((ShaderMixed)(shaderProgram)).getPipeline();

            if (pipeline == null) {
//                throw new NullPointerException("Shader %s has no initialized pipeline".formatted(shaderProgram.getName()));
                throw new NullPointerException("Shader %d has no initialized pipeline".formatted(shaderProgram.getProgramId()));
            }

            VRenderSystem.setPrimitiveTopologyGL(parameters.mode().asGLMode);
            renderer.bindGraphicsPipeline(pipeline);
            VTextureSelector.bindShaderTextures(pipeline);
            renderer.uploadAndBindUBOs(pipeline);

            Renderer.getDrawer().draw(meshData.vertexBuffer(), meshData.indexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
        }

        meshData.close();
    }

    /**
     * @author
     */
    @Overwrite
    public static void draw(MeshData meshData) {
        MeshData.DrawState parameters = meshData.drawState();

        if (parameters.vertexCount() > 0) {
            Renderer renderer = Renderer.getInstance();
            Pipeline pipeline = renderer.getBoundPipeline();
            renderer.uploadAndBindUBOs(pipeline);

            Renderer.getDrawer().draw(meshData.vertexBuffer(), null, parameters.mode(), parameters.format(), parameters.vertexCount());
        }

        meshData.close();
    }

}
