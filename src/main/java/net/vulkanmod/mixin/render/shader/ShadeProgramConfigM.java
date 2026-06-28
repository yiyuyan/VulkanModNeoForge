package net.vulkanmod.mixin.render.shader;

import net.minecraft.client.renderer.ShaderProgramConfig;
import net.vulkanmod.interfaces.shader.PipelineConfig;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ShaderProgramConfig.class)
public class ShadeProgramConfigM implements PipelineConfig {

    String name;


    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }
}
