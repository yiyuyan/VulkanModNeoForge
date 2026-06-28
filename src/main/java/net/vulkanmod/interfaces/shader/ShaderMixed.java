package net.vulkanmod.interfaces.shader;

import net.minecraft.client.renderer.CompiledShaderProgram;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.util.MappedBuffer;

import java.util.function.Supplier;

public interface ShaderMixed {

    static ShaderMixed of(CompiledShaderProgram compiledShaderProgram) {
        return (ShaderMixed) compiledShaderProgram;
    }

    void setPipeline(GraphicsPipeline graphicsPipeline);

    GraphicsPipeline getPipeline();

    void setupUniformSuppliers(UBO ubo);

    Supplier<MappedBuffer> getUniformSupplier(String name);

    void setUniformsUpdate();
}
