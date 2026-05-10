package net.vulkanmod.vulkan.shader;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.Initializer;
import org.apache.commons.io.IOUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.NativeResource;
import org.lwjgl.util.shaderc.ShadercIncludeResolveI;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultReleaseI;
import org.lwjgl.vulkan.VK12;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memASCII;
import static org.lwjgl.util.shaderc.Shaderc.*;

public class SPIRVUtils {
    private static final boolean DEBUG = true;
    private static final boolean OPTIMIZATIONS = false;

    private static long compiler;
    private static long options;

    //The dedicated Includer and Releaser Inner Classes used to Initialise #include Support for ShaderC
    private static final ShaderIncluder SHADER_INCLUDER = new ShaderIncluder();
    private static final ShaderReleaser SHADER_RELEASER = new ShaderReleaser();
    private static final long pUserData = 0;

    private static ObjectArrayList<String> includePaths;

    static {
        initCompiler();
    }

    private static void initCompiler() {
        compiler = shaderc_compiler_initialize();

        if (compiler == NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }

        options = shaderc_compile_options_initialize();

        if (options == NULL) {
            throw new RuntimeException("Failed to create compiler options");
        }

        if (OPTIMIZATIONS)
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        if (DEBUG)
            shaderc_compile_options_set_generate_debug_info(options);

        shaderc_compile_options_set_target_env(options, shaderc_env_version_vulkan_1_2, VK12.VK_API_VERSION_1_2);
        shaderc_compile_options_set_include_callbacks(options, SHADER_INCLUDER, SHADER_RELEASER, pUserData);

        includePaths = new ObjectArrayList<>();
        addIncludePath("/assets/vulkanmod/shaders/include/");
    }

    public static void addIncludePath(String path) {
        URL url = SPIRVUtils.class.getResource(path);

        if (url != null)
            includePaths.add(url.toExternalForm());
    }

    private static String processSource(String source){
        StringBuilder builder = new StringBuilder();
        for (String string : source.split("\n")) {
            if(string.startsWith("#include")){
                try {
                    String included = string.replace("#include","").replace(" ","")
                            .replace("\"","").replace("\r","").replace("\n","");
                    builder.append(IOUtils.toString(Objects.requireNonNull(SPIRVUtils.class.getResourceAsStream("/assets/vulkanmod/shaders/include/" + included))));
                } catch (IOException e) {
                    Initializer.LOGGER.error("Can't process the shader source: {}",source);
                    builder.append(string);
                }
            }
            else{
                builder.append(string);
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    public static SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {
        if (source == null) {
            throw new NullPointerException("source for %s.%s is null".formatted(filename, shaderKind));
        }

        if(filename.contains("item_entity_translucent_cull") && shaderKind==ShaderKind.VERTEX_SHADER){
            source = """
                    #version 450
                                        
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec4 Color;
                    layout(location = 2) in vec2 UV0;
                    layout(location = 4) in ivec2 UV2;
                    layout(location = 5) in vec3 Normal;
                                        
                    layout(binding = 0) uniform UniformBufferObject {
                       mat4 MVP;
                       vec3 Light0_Direction;
                       vec3 Light1_Direction;
                    };
                                        
                    layout(binding = 3) uniform sampler2D Sampler2;
                                        
                    layout(location = 0) out vec4 vertexColor;
                    layout(location = 1) out vec2 texCoord0;
                    layout(location = 2) out float vertexDistance;
                                        
                    const float MINECRAFT_LIGHT_POWER = (0.6);
                    const float MINECRAFT_AMBIENT_LIGHT = (0.4);
                                        
                    float fog_distance(vec3 pos, int shape) {
                        if (shape == 0) {
                            return length(pos);
                        } else {
                            float distXZ = length(pos.xz);
                            float distY = abs(pos.y);
                            return max(distXZ, distY);
                        }
                    }
                                        
                    vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
                        lightDir0 = normalize(lightDir0);
                        lightDir1 = normalize(lightDir1);
                        float light0 = max(0.0, dot(lightDir0, normal));
                        float light1 = max(0.0, dot(lightDir1, normal));
                        float lightAccum = min(1.0, fma((light0 + light1), MINECRAFT_LIGHT_POWER, MINECRAFT_AMBIENT_LIGHT));
                        return vec4(color.rgb * lightAccum, color.a);
                    }
                                        
                                        
                    void main() {
                        gl_Position = MVP * vec4(Position, 1.0);
                                        
                        vertexDistance = fog_distance(Position.xyz, 0);
                        vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * texelFetch(Sampler2, UV2 / 16, 0);
                        texCoord0 = UV0;
                    }
                    """;
        }
        else if(filename.contains("item_entity_translucent_cull") && shaderKind==ShaderKind.FRAGMENT_SHADER){
            source = """
                    #version 450
                                        
                    layout(binding = 2) uniform sampler2D Sampler0;
                                        
                    layout(binding = 1) uniform UBO{
                        vec4 ColorModulator;
                        vec4 FogColor;
                        float FogStart;
                        float FogEnd;
                    };
                                        
                    layout(location = 0) in vec4 vertexColor;
                    layout(location = 1) in vec2 texCoord0;
                    layout(location = 2) in float vertexDistance;
                                        
                    layout(location = 0) out vec4 fragColor;
                                        
                    vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
                        return (vertexDistance <= fogStart) ? inColor : mix(inColor, fogColor, smoothstep(fogStart, fogEnd, vertexDistance) * fogColor.a);
                    }
                                        
                    void main() {
                        vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
                        if (color.a < 0.1) {
                            discard;
                        }
                        fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
                    }
                    """;
        }

        source = processSource(source);

        long result = shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", options);

        if (result == NULL) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            String errorMessage = shaderc_result_get_error_message(result);
            if(filename.contains("item_entity_translucent_cull")){
                System.out.println(source);
            }
            throw new RuntimeException("Failed to compile shader %s into SPIR-V:\n\t%s".formatted(filename, errorMessage));
        }

        return new SPIRV(result, shaderc_result_get_bytes(result));
    }

    public enum ShaderKind {
        VERTEX_SHADER(shaderc_glsl_vertex_shader),
        GEOMETRY_SHADER(shaderc_glsl_geometry_shader),
        FRAGMENT_SHADER(shaderc_glsl_fragment_shader),
        COMPUTE_SHADER(shaderc_glsl_compute_shader);

        private final int kind;

        ShaderKind(int kind) {
            this.kind = kind;
        }
    }

    private static class ShaderIncluder implements ShadercIncludeResolveI {

        private static final int MAX_PATH_LENGTH = 4096; //Maximum Linux/Unix Path Length

        @Override
        public long invoke(long user_data, long requested_source, int type, long requesting_source, long include_depth) {
            var requesting = memASCII(requesting_source);
            var requested = memASCII(requested_source);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                Path path;

                for (String includePath : includePaths) {
                    path = Paths.get(new URI(String.format("%s%s", includePath, requested)));

                    if (Files.exists(path)) {
                        byte[] bytes = Files.readAllBytes(path);

                        return ShadercIncludeResult.malloc(stack)
                                                   .source_name(stack.ASCII(requested))
                                                   .content(stack.bytes(bytes))
                                                   .user_data(user_data).address();
                    }
                }
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }

            throw new RuntimeException(String.format("%s: Unable to find %s in include paths", requesting, requested));
        }
    }

    //TODO: Don't actually need the Releaser at all, (MemoryStack frees this for us)
    //But ShaderC won't let us create the Includer without a corresponding Releaser, (so we need it anyway)
    private static class ShaderReleaser implements ShadercIncludeResultReleaseI {

        @Override
        public void invoke(long user_data, long include_result) {
            //TODO:Maybe dump Shader Compiled Binaries here to a .Misc Diretcory to allow easy caching.recompilation...
        }
    }

    public static final class SPIRV implements NativeResource {

        private final long handle;
        private ByteBuffer bytecode;

        public SPIRV(long handle, ByteBuffer bytecode) {
            this.handle = handle;
            this.bytecode = bytecode;
        }

        public ByteBuffer bytecode() {
            return bytecode;
        }

        @Override
        public void free() {
//            shaderc_result_release(handle);
            bytecode = null; // Help the GC
        }
    }

}