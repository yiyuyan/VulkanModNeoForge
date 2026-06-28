package net.vulkanmod.mixin.render.shader;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.CompiledShader;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.shader.PipelineConfig;
import net.vulkanmod.interfaces.shader.ShaderMixed;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.converter.GlslConverter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;

@Mixin(ShaderManager.CompilationCache.class)
public abstract class CompilationCacheM {

    @Shadow @Final private ShaderManager.Configs configs;
    @Shadow @Final public Map<ShaderManager.ShaderCompilationKey, CompiledShader> shaders;

    @Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
    public void compileProgram(ShaderProgram shaderProgram,
                               CallbackInfoReturnable<CompiledShaderProgram> cir) throws ShaderManager.CompilationException {
        ShaderProgramConfig shaderProgramConfig = this.configs.programs().get(shaderProgram.configId());
        if (shaderProgramConfig == null) {
            throw new ShaderManager.CompilationException("Could not find program with id: " + shaderProgram.configId());
        } else {
            cir.setReturnValue(createShaderProgram(shaderProgram, shaderProgramConfig));
        }
    }

    private CompiledShaderProgram createShaderProgram(ShaderProgram shaderProgram, ShaderProgramConfig shaderProgramConfig) {
        String configName = PipelineConfig.of(shaderProgramConfig).getName();

        JsonObject config = ShaderLoadUtil.getJsonConfig("core", configName);

        if (config == null) {
            GlslConverter converter = new GlslConverter();
            Pipeline.Builder builder = new Pipeline.Builder(shaderProgram.vertexFormat(), configName);

            ShaderDefines shaderDefines = shaderProgramConfig.defines().withOverrides(shaderProgram.defines());
            String vshSrc = getShaderSource(shaderProgramConfig.vertex(), CompiledShader.Type.VERTEX, shaderDefines);
            String fshSrc = getShaderSource(shaderProgramConfig.fragment(), CompiledShader.Type.FRAGMENT, shaderDefines);

            converter.process(vshSrc, fshSrc);

            UBO ubo = converter.createUBO();

            builder.setUniforms(Collections.singletonList(ubo), converter.getSamplerList());
            builder.compileShaders(configName, converter.getVshConverted(), converter.getFshConverted());

            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            CompiledShaderProgram compiledShaderProgram = createProgram();

            compiledShaderProgram.setupUniforms(shaderProgramConfig.uniforms(), shaderProgramConfig.samplers());
            ShaderMixed shaderMixed = ShaderMixed.of(compiledShaderProgram);
            shaderMixed.setPipeline(pipeline);

            shaderMixed.setupUniformSuppliers(ubo);
            shaderMixed.setUniformsUpdate();

            return compiledShaderProgram;
        }

        CompiledShaderProgram compiledShaderProgram = createProgram();
        compiledShaderProgram.setupUniforms(shaderProgramConfig.uniforms(), shaderProgramConfig.samplers());
        ShaderMixed shaderMixed = ShaderMixed.of(compiledShaderProgram);

        Pipeline.Builder builder = new Pipeline.Builder(shaderProgram.vertexFormat(), configName);
        builder.setUniformSupplierGetter(info -> shaderMixed.getUniformSupplier(info.name));

        builder.parseBindings(config);

        ShaderDefines shaderDefines = shaderProgramConfig.defines().withOverrides(shaderProgram.defines());

        if (!shaderDefines.isEmpty()) {
            Initializer.LOGGER.error("Shader {} is using external defines that are unsupported.", configName);
        }

        ShaderLoadUtil.loadShaders(builder, config, configName, "core");

        GraphicsPipeline pipeline = builder.createGraphicsPipeline();
        shaderMixed.setPipeline(pipeline);

        return compiledShaderProgram;
    }

    private String getShaderSource(ResourceLocation resourceLocation, CompiledShader.Type type, ShaderDefines shaderDefines) {
        ShaderManager.ShaderCompilationKey shaderCompilationKey = new ShaderManager.ShaderCompilationKey(resourceLocation, type, shaderDefines);

        String source = this.configs.shaderSources().get(new ShaderManager.ShaderSourceKey(shaderCompilationKey.id(), shaderCompilationKey.type()));
        String processedSource = GlslPreprocessor.injectDefines(source, shaderCompilationKey.defines());

        return processedSource;
    }

    private static CompiledShaderProgram createProgram() {
        return new CompiledShaderProgram(0);
    }
}
