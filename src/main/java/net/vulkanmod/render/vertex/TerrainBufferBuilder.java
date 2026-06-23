package net.vulkanmod.render.vertex;

import net.vulkanmod.Initializer;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class TerrainBufferBuilder {
    private static final Logger LOGGER = Initializer.LOGGER;
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);

    private int capacity;
    private final int vertexSize;
    protected long bufferPtr;
    protected int nextElementByte;
    private int vertices;
    private final VertexBuilder vertexBuilder;

    public TerrainBufferBuilder(int size, int vertexSize, VertexBuilder vertexBuilder) {
        this.bufferPtr = ALLOCATOR.malloc(size);
        this.capacity = size;
        this.vertexSize = vertexSize;
        this.vertexBuilder = vertexBuilder;
    }

    public void ensureCapacity() {
        ensureCapacity(vertexSize * 4);
    }

    private void ensureCapacity(int size) {
        if (nextElementByte + size > capacity) {
            int newSize = (capacity + size) * 2;
            bufferPtr = ALLOCATOR.realloc(bufferPtr, newSize);
            LOGGER.debug("Grew vertex buffer from {} to {} bytes", capacity, newSize);
            capacity = newSize;
        }
    }

    public void vertex(float x, float y, float z, int color, float u, float v, int light, int packedNormal) {
        long ptr = bufferPtr + nextElementByte;
        vertexBuilder.vertex(ptr, x, y, z, color, u, v, light, packedNormal);
        nextElementByte += vertexSize;
        vertices++;
    }

    public void end() {}

    public void clear() {
        nextElementByte = 0;
        vertices = 0;
    }

    public ByteBuffer getBuffer() {
        return MemoryUtil.memByteBuffer(bufferPtr, vertices * vertexSize);
    }

    public long getPtr() { return bufferPtr; }
    public int getVertices() { return vertices; }
    public int getNextElementByte() { return nextElementByte; }

    public void free() {
        if (bufferPtr != 0L) {
            ALLOCATOR.free(bufferPtr);
            bufferPtr = 0L;
        }
    }
}