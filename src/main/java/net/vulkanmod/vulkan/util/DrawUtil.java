package net.vulkanmod.vulkan.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;

public class DrawUtil {

    public static void blitToScreen() {
//        defaultBlit();
        fastBlit();
    }

    public static void fastBlit() {
        GraphicsPipeline blitPipeline = PipelineManager.getFastBlitPipeline();

        RenderSystem.disableCull();
        VRenderSystem.setPrimitiveTopologyGL(GL11.GL_TRIANGLES);

        Renderer renderer = Renderer.getInstance();
        renderer.bindGraphicsPipeline(blitPipeline);
        renderer.uploadAndBindUBOs(blitPipeline);

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        VK11.vkCmdDraw(commandBuffer, 3, 1, 0, 0);

        RenderSystem.enableCull();
    }

    public static void defaultBlit() {
        Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack posestack = RenderSystem.getModelViewStack();
        posestack.pushPose();
        posestack.last().pose().identity();
        RenderSystem.applyModelViewMatrix();
        posestack.popPose();

        ShaderInstance shaderInstance = Minecraft.getInstance().gameRenderer.blitShader;
//        RenderSystem.setShader(() -> shaderInstance);

        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.vertex(-1.0f, -1.0f, 0.0f).uv(0.0F, 1.0F);
        bufferBuilder.vertex(1.0f, -1.0f, 0.0f).uv(1.0F, 1.0F);
        bufferBuilder.vertex(1.0f, 1.0f, 0.0f).uv(1.0F, 0.0F);
        bufferBuilder.vertex(-1.0f, 1.0f, 0.0f).uv(0.0F, 0.0F);
        var meshData = bufferBuilder.end();

        BufferBuilder.DrawState parameters = meshData.drawState();

        Renderer renderer = Renderer.getInstance();

        GraphicsPipeline pipeline = ((ShaderMixed)(shaderInstance)).getPipeline();
        renderer.bindGraphicsPipeline(pipeline);
        renderer.uploadAndBindUBOs(pipeline);
        Renderer.getDrawer().draw(meshData.vertexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
    }
}
