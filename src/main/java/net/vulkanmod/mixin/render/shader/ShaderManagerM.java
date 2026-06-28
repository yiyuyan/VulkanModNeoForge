package net.vulkanmod.mixin.render.shader;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.CompiledShader;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.FileUtil;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.profiling.ProfilerFiller;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.shader.PipelineConfig;
import net.vulkanmod.interfaces.shader.ShaderMixed;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(ShaderManager.class)
public abstract class ShaderManagerM {

    @Shadow @Final private static FileToIdConverter PROGRAM_ID_CONVERTER;

    @Shadow private ShaderManager.CompilationCache compilationCache;

    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private static FileToIdConverter POST_CHAIN_ID_CONVERTER;

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void preloadForStartup(ResourceProvider resourceProvider, ShaderProgram... shaderPrograms) throws IOException {
        for (ShaderProgram shaderProgram : shaderPrograms) {
            ResourceLocation location = PROGRAM_ID_CONVERTER.idToFile(shaderProgram.configId());
            Resource resource = resourceProvider.getResourceOrThrow(location);
            Reader reader = resource.openAsReader();

            String locationPath = location.getPath();
            String configName = locationPath.split("/")[2];

            // Remove .json suffix
            configName = configName.substring(0, configName.length() - 5);

            try {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                ShaderProgramConfig shaderProgramConfig = ShaderProgramConfig.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(
                        JsonSyntaxException::new);

                PipelineConfig.of(shaderProgramConfig).setName(configName);

                Pipeline.Builder builder = new Pipeline.Builder(shaderProgram.vertexFormat());

                JsonObject config = ShaderLoadUtil.getJsonConfig("core", configName);
                builder.parseBindings(config);

                ShaderLoadUtil.loadShader(builder, configName, shaderProgramConfig.vertex().getPath(), SPIRVUtils.ShaderKind.VERTEX_SHADER);
                ShaderLoadUtil.loadShader(builder, configName, shaderProgramConfig.fragment().getPath(), SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

                GraphicsPipeline pipeline = builder.createGraphicsPipeline();
                CompiledShaderProgram compiledShaderProgram = createProgram(pipeline);

                this.compilationCache.programs.put(shaderProgram, Optional.of(compiledShaderProgram));
            } catch (Throwable var16) {
                try {
                    reader.close();
                } catch (Throwable var15) {
                    var16.addSuppressed(var15);
                }

                throw var16;
            }

            reader.close();
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public ShaderManager.Configs prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        ImmutableMap.Builder<ResourceLocation, ShaderProgramConfig> builder = ImmutableMap.builder();
        ImmutableMap.Builder<ShaderManager.ShaderSourceKey, String> builder2 = ImmutableMap.builder();

        Predicate<ResourceLocation> filter = location -> !location.getNamespace().equals("vulkanmod") && (isProgram(location) || isShader(location));

        Map<ResourceLocation, Resource> map = resourceManager.listResources("shaders", filter);

        for (Map.Entry<ResourceLocation, Resource> entry : map.entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            CompiledShader.Type type = CompiledShader.Type.byLocation(resourceLocation);
            if (type != null) {
                loadShader(resourceLocation, entry.getValue(), type, map, builder2);
            } else if (isProgram(resourceLocation)) {
                loadProgram(resourceLocation, entry.getValue(), builder);
            }
        }

        ImmutableMap.Builder<ResourceLocation, PostChainConfig> builder3 = ImmutableMap.builder();

        for (Map.Entry<ResourceLocation, Resource> entry2 : POST_CHAIN_ID_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            loadPostChain(entry2.getKey(), entry2.getValue(), builder3);
        }

        return new ShaderManager.Configs(builder.build(), builder2.build(), builder3.build());
    }

    private static GlslPreprocessor createPreprocessor(Map<ResourceLocation, Resource> map, ResourceLocation resourceLocation) {
        final ResourceLocation resourceLocation2 = resourceLocation.withPath(FileUtil::getFullResourcePath);
        return new GlslPreprocessor() {
            private final Set<ResourceLocation> importedLocations = new ObjectArraySet<>();

            @Override
            public String applyImport(boolean bl, String string) {
                ResourceLocation resourceLocation;
                try {
                    if (bl) {
                        resourceLocation = resourceLocation2.withPath(
                                string2 -> FileUtil.normalizeResourcePath(string2 + string));
                    } else {
                        resourceLocation = ResourceLocation.parse(string).withPrefix("shaders/include/");
                    }
                } catch (ResourceLocationException var8) {
                    LOGGER.error("Malformed GLSL import {}: {}", string, var8.getMessage());
                    return "#error " + var8.getMessage();
                }

                if (!this.importedLocations.add(resourceLocation)) {
                    return null;
                } else {
                    try {
                        Reader reader = map.get(resourceLocation).openAsReader();

                        String var5;
                        try {
                            var5 = IOUtils.toString(reader);
                        } catch (Throwable var9) {
                            if (reader != null) {
                                try {
                                    reader.close();
                                } catch (Throwable var7) {
                                    var9.addSuppressed(var7);
                                }
                            }

                            throw var9;
                        }

                        if (reader != null) {
                            reader.close();
                        }

                        return var5;
                    } catch (IOException var10) {
                        LOGGER.error("Could not open GLSL import {}: {}", resourceLocation, var10.getMessage());
                        return "#error " + var10.getMessage();
                    }
                }
            }
        };
    }

    private static void loadProgram(ResourceLocation resourceLocation, Resource resource, ImmutableMap.Builder<ResourceLocation, ShaderProgramConfig> builder) {
        ResourceLocation resourceLocation2 = PROGRAM_ID_CONVERTER.fileToId(resourceLocation);

        try {
            Reader reader = resource.openAsReader();

            try {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                ShaderProgramConfig shaderProgramConfig = ShaderProgramConfig.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(JsonSyntaxException::new);

                String configPath = resourceLocation.getPath();
                String configName = configPath.split("/")[2];

                // Remove .json suffix
                configName = configName.substring(0, configName.length() - 5);

                PipelineConfig.of(shaderProgramConfig).setName(configName);

                builder.put(resourceLocation2, shaderProgramConfig);
            } catch (Throwable var8) {
                try {
                    reader.close();
                } catch (Throwable var7) {
                    var8.addSuppressed(var7);
                }

                throw var8;
            }

            reader.close();
        } catch (JsonParseException | IOException var9) {
            LOGGER.error("Failed to parse shader config at {}", resourceLocation, var9);
        }
    }

    private static void loadPostChain(ResourceLocation resourceLocation, Resource resource, ImmutableMap.Builder<ResourceLocation, PostChainConfig> builder) {
        ResourceLocation resourceLocation2 = POST_CHAIN_ID_CONVERTER.fileToId(resourceLocation);

        try {
            Reader reader = resource.openAsReader();

            try {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                builder.put(resourceLocation2, PostChainConfig.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(JsonSyntaxException::new));
            } catch (Throwable var8) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Throwable var7) {
                        var8.addSuppressed(var7);
                    }
                }

                throw var8;
            }

            if (reader != null) {
                reader.close();
            }
        } catch (JsonParseException | IOException var9) {
            LOGGER.error("Failed to parse post chain at {}", resourceLocation, var9);
        }
    }

    private static boolean isProgram(ResourceLocation resourceLocation) {
        return resourceLocation.getPath().endsWith(".json");
    }

    private static boolean isShader(ResourceLocation resourceLocation) {
        return CompiledShader.Type.byLocation(resourceLocation) != null || resourceLocation.getPath().endsWith(".glsl");
    }

    private static void loadShader(
            ResourceLocation resourceLocation, Resource resource, CompiledShader.Type type, Map<ResourceLocation, Resource> map, ImmutableMap.Builder<ShaderManager.ShaderSourceKey, String> builder
    ) {
        ResourceLocation resourceLocation2 = type.idConverter().fileToId(resourceLocation);
        GlslPreprocessor glslPreprocessor = createPreprocessor(map, resourceLocation);

        try {
            Reader reader = resource.openAsReader();

            try {
                String string = IOUtils.toString(reader);
                builder.put(new ShaderManager.ShaderSourceKey(resourceLocation2, type), String.join("", glslPreprocessor.process(string)));
            } catch (Throwable var11) {
                try {
                    reader.close();
                } catch (Throwable var10) {
                    var11.addSuppressed(var10);
                }

                throw var11;
            }

            reader.close();
        } catch (IOException var12) {
            LOGGER.error("Failed to load shader source at {}", resourceLocation, var12);
        }
    }

    private static CompiledShaderProgram createProgram(GraphicsPipeline pipeline) {
        CompiledShaderProgram compiledShaderProgram = new CompiledShaderProgram(0);
        ShaderMixed shaderMixed = ShaderMixed.of(compiledShaderProgram);
        shaderMixed.setPipeline(pipeline);

        return compiledShaderProgram;
    }

}
