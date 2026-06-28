package net.vulkanmod.gl;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.vulkanmod.vulkan.shader.Pipeline;

public class GlProgram {
    private static int ID_COUNTER = 1;
    private static final Int2ReferenceOpenHashMap<GlProgram> map = new Int2ReferenceOpenHashMap<>();
    private static int boundProgramId = 0;
    private static GlProgram boundProgram;

    public static GlProgram getBoundProgram() {
        return boundProgram;
    }

    public static GlProgram getProgram(int id) {
        return map.get(id);
    }

    public static int genProgramId() {
        int id = ID_COUNTER;
        map.put(id, new GlProgram(id));
        ID_COUNTER++;
        return id;
    }

    public static void glUseProgram(int id) {
        boundProgramId = id;
        boundProgram = map.get(id);

        if (id <= 0) {
            return;
        }

        if (boundProgram == null) {
            throw new NullPointerException("bound texture is null");
        }

    }

    int id;
    Pipeline pipeline;

    GlProgram(int i) {
        this.id = i;
    }

    public void bindPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public Pipeline getPipeline() {
        return this.pipeline;
    }
}
