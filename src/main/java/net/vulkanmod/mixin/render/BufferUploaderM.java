package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BufferUploader.class)
public abstract class BufferUploaderM {

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

        if (parameters.vertexCount() > 0) {
            ShaderInstance shaderInstance = RenderSystem.getShader();

            // Prevent drawing if formats don't match to avoid disturbing visual bugs
            if (shaderInstance.getVertexFormat() != parameters.format()) {
                meshData.close();
                return;
            }

            VRenderSystem.setPrimitiveTopologyGL(parameters.mode().asGLMode);

            // Used to update legacy shader uniforms
            // TODO it would be faster to allocate a buffer from stack and set all values
            shaderInstance.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(),
                                              RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            shaderInstance.apply();

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
