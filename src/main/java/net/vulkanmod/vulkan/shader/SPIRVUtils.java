package net.vulkanmod.vulkan.shader;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeResource;
import org.lwjgl.system.NativeType;
import org.lwjgl.util.shaderc.ShadercIncludeResolveI;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultReleaseI;
import org.lwjgl.vulkan.VK12;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memASCII;
import static org.lwjgl.util.shaderc.Shaderc.*;

public class SPIRVUtils {
    private static final boolean DEBUG = false;
    private static final boolean OPTIMIZATIONS = true;

    private static long compiler;
    private static long options;

    //The dedicated Includer and Releaser Inner Classes used to Initialise #include Support for ShaderC
    private static final ShaderIncluder SHADER_INCLUDER = new ShaderIncluder();
    private static final ShaderReleaser SHADER_RELEASER = new ShaderReleaser();
    private static final long pUserData = 0;

    private static ObjectArrayList<String> includePaths;

    private static float time = 0.0f;

    static {
        initCompiler();
    }

    private static void initCompiler() {
        compiler = shaderc_compiler_initialize();

        if(compiler == NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }

        options = shaderc_compile_options_initialize();

        if(options == NULL) {
            throw new RuntimeException("Failed to create compiler options");
        }

        if(OPTIMIZATIONS)
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        if(DEBUG)
            shaderc_compile_options_set_generate_debug_info(options);

        shaderc_compile_options_set_target_env(options, shaderc_env_version_vulkan_1_2, VK12.VK_API_VERSION_1_2);
        shaderc_compile_options_set_include_callbacks(options, SHADER_INCLUDER, SHADER_RELEASER, pUserData);

        includePaths = new ObjectArrayList<>();
        addIncludePath("/assets/vulkanmod/shaders/include/");
    }

    public static void addIncludePath(String path) {
        URL url = SPIRVUtils.class.getResource(path);

        if(url != null)
            includePaths.add(url.toExternalForm());
    }

    public static SPIRV compileShaderAbsoluteFile(String shaderFile, ShaderKind shaderKind) {
        try {
            String source = new String(Files.readAllBytes(Paths.get(new URI(shaderFile))));
            return compileShader(shaderFile, source, shaderKind);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    @NativeType("shaderc_compilation_result_t")
    public static long self_shaderc_compile_into_spv(@NativeType("shaderc_compiler_t const") long compiler, @NativeType("char const *") CharSequence source_text, @NativeType("shaderc_shader_kind") int shader_kind, @NativeType("char const *") CharSequence input_file_name, @NativeType("char const *") CharSequence entry_point_name, @NativeType("shaderc_compile_options_t const") long additional_options) {
        MemoryStack stack = MemoryStack.stackGet();
        int stackPointer = stack.getPointer();

        long var17;
        try {
            int source_textEncodedLength = stack.nUTF8(source_text, false);
            long source_textEncoded = stack.getPointerAddress();
            stack.nUTF8(input_file_name, true);
            long input_file_nameEncoded = stack.getPointerAddress();
            stack.nUTF8(entry_point_name, true);
            long entry_point_nameEncoded = stack.getPointerAddress();

            System.out.println(source_text);
            System.out.println(input_file_name);
            System.out.println(entry_point_name);

            System.out.println(input_file_nameEncoded);
            System.out.println(entry_point_nameEncoded);
            System.out.println(source_textEncoded);
            var17 = nshaderc_compile_into_spv(compiler, source_textEncoded, (long)source_textEncodedLength, shader_kind, input_file_nameEncoded, entry_point_nameEncoded, additional_options);
        } finally {
            stack.setPointer(stackPointer);
        }

        return var17;
    }

    public static SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {
        long startTime = System.nanoTime();

        filename = new File(filename).getName().split("\\.")[0];

        System.out.println(filename);
        System.out.println(compiler);
        System.out.println(shaderKind);
        System.out.println(options);
        long result = shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", options);

        if(result == NULL) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if(shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V:\n" + shaderc_result_get_error_message(result));
        }

        time += (System.nanoTime() - startTime) / 1000000.0f;

        return new SPIRV(result, shaderc_result_get_bytes(result));
    }

    private static SPIRV readFromStream(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.position(0);

            return new SPIRV(MemoryUtil.memAddress(buffer), buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new RuntimeException("unable to read inputStream");
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

            try(MemoryStack stack = MemoryStack.stackPush()) {
                Path path;

                for(String includePath : includePaths) {
                    path = Paths.get(new URI(String.format("%s%s", includePath, requested)));

                    if(Files.exists(path)) {
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