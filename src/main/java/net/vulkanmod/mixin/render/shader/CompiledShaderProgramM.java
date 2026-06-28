package net.vulkanmod.mixin.render.shader;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.CompiledShader;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.ShaderProgramConfig;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.shader.ShaderMixed;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(CompiledShaderProgram.class)
public abstract class CompiledShaderProgramM implements ShaderMixed {

    @Shadow @Final private IntList samplerLocations;
    @Shadow @Final private Object2IntMap<String> samplerTextures;
    @Shadow @Final private List<ShaderProgramConfig.Sampler> samplers;
    @Shadow @Final private Map<String, Uniform> uniformsByName;

    @Shadow @Nullable public Uniform MODEL_VIEW_MATRIX;
    @Shadow @Nullable public Uniform PROJECTION_MATRIX;
    @Shadow @Nullable public Uniform COLOR_MODULATOR;
    @Shadow @Nullable public Uniform GLINT_ALPHA;
    @Shadow @Nullable public Uniform FOG_START;

    GraphicsPipeline pipeline;
    boolean updateUniforms = false;

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static CompiledShaderProgram link(CompiledShader compiledShader, CompiledShader compiledShader2, VertexFormat vertexFormat) throws ShaderManager.CompilationException {
        return new CompiledShaderProgram(0);
    }

    @Shadow @Nullable public Uniform TEXTURE_MATRIX;
    @Shadow @Nullable public Uniform SCREEN_SIZE;
    @Shadow @Nullable public Uniform LIGHT0_DIRECTION;
    @Shadow @Nullable public Uniform LIGHT1_DIRECTION;
    @Shadow @Nullable public Uniform FOG_END;
    @Shadow @Nullable public Uniform FOG_COLOR;
    @Shadow @Nullable public Uniform FOG_SHAPE;
    @Shadow @Nullable public Uniform LINE_WIDTH;
    @Shadow @Nullable public Uniform GAME_TIME;
    @Shadow @Nullable public Uniform MODEL_OFFSET;

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void apply() {
        if (!this.updateUniforms) {
            return;
        }

        for (int j = 0; j < this.samplerLocations.size(); j++) {
            String string = this.samplers.get(j).name();
            int k = this.samplerTextures.getInt(string);
            if (k != -1) {
                int l = this.samplerLocations.getInt(j);
                RenderSystem.activeTexture(33984 + j);
                RenderSystem.bindTexture(k);
                RenderSystem.setShaderTexture(j, k);
            }
        }

        if (this.MODEL_VIEW_MATRIX != null) {
            this.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }

        if (this.PROJECTION_MATRIX != null) {
            this.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }

        if (this.COLOR_MODULATOR != null) {
            this.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }

        if (this.GLINT_ALPHA != null) {
            this.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        }

        FogParameters fogParameters = RenderSystem.getShaderFog();
        if (this.FOG_START != null) {
            this.FOG_START.set(fogParameters.start());
        }

        if (this.FOG_END != null) {
            this.FOG_END.set(fogParameters.end());
        }

        if (this.FOG_COLOR != null) {
            this.FOG_COLOR.set(fogParameters.red(), fogParameters.green(), fogParameters.blue(), fogParameters.alpha());
        }

        if (this.FOG_SHAPE != null) {
            this.FOG_SHAPE.set(fogParameters.shape().getIndex());
        }

        if (this.TEXTURE_MATRIX != null) {
            this.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }

        if (this.GAME_TIME != null) {
            this.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        if (this.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            this.SCREEN_SIZE.set((float)window.getWidth(), (float)window.getHeight());
        }

        if (this.LINE_WIDTH != null) {
            this.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        }
    }



    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        this.pipeline.scheduleCleanUp();
    }

    @Override
    public void setPipeline(GraphicsPipeline graphicsPipeline) {
        this.pipeline = graphicsPipeline;
    }

    @Override
    public GraphicsPipeline getPipeline() {
        return pipeline;
    }

    public void setupUniformSuppliers(UBO ubo) {
        for (net.vulkanmod.vulkan.shader.layout.Uniform vUniform : ubo.getUniforms()) {
            com.mojang.blaze3d.shaders.Uniform uniform = this.uniformsByName.get(vUniform.getName());

            if (uniform == null) {
                Initializer.LOGGER.error(String.format("Error: field %s not present in uniform map", vUniform.getName()));
                continue;
            }

            Supplier<MappedBuffer> supplier;
            ByteBuffer byteBuffer;

            if (uniform.getType() <= 3) {
                byteBuffer = MemoryUtil.memByteBuffer(uniform.getIntBuffer());
            } else if (uniform.getType() <= 10) {
                byteBuffer = MemoryUtil.memByteBuffer(uniform.getFloatBuffer());
            } else {
                throw new RuntimeException("out of bounds value for uniform " + uniform);
            }


            MappedBuffer mappedBuffer = MappedBuffer.createFromBuffer(byteBuffer);
            supplier = () -> mappedBuffer;

            vUniform.setSupplier(supplier);
        }

    }

    public Supplier<MappedBuffer> getUniformSupplier(String name) {
        com.mojang.blaze3d.shaders.Uniform uniform1 = this.uniformsByName.get(name);

        if (uniform1 == null) {
            Initializer.LOGGER.error(String.format("Error: field %s not present in uniform map", name));
            return null;
        }

        Supplier<MappedBuffer> supplier;
        ByteBuffer byteBuffer;

        if (uniform1.getType() <= 3) {
            byteBuffer = MemoryUtil.memByteBuffer(uniform1.getIntBuffer());
        } else if (uniform1.getType() <= 10) {
            byteBuffer = MemoryUtil.memByteBuffer(uniform1.getFloatBuffer());
        } else {
            throw new RuntimeException("out of bounds value for uniform " + uniform1);
        }

        MappedBuffer mappedBuffer = MappedBuffer.createFromBuffer(byteBuffer);
        supplier = () -> mappedBuffer;

        return supplier;
    }

    @Override
    public void setUniformsUpdate() {
        this.updateUniforms = true;
    }
}
