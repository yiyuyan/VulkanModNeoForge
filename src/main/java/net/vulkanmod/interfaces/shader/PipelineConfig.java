package net.vulkanmod.interfaces.shader;

import net.minecraft.client.renderer.ShaderProgramConfig;

public interface PipelineConfig {

    static PipelineConfig of(ShaderProgramConfig shaderProgramConfig) {
        return (PipelineConfig) (Object) shaderProgramConfig;
    }

    String getName();

    void setName(String s);
}
