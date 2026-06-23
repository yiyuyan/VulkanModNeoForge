package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class TerrainBuilder {
    private static final Logger LOGGER = Initializer.LOGGER;
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);

    protected long indexBufferPtr;
    private int indexBufferCapacity;

    private final VertexFormat format;
    private final VertexBuilder vertexBuilder;
    private final TerrainBufferBuilder[] bufferBuilders;
    private boolean building;
    private final QuadSorter quadSorter = new QuadSorter();
    private boolean needsSorting;
    private boolean indexOnly;

    public TerrainBuilder(int size) {
        this.indexBufferPtr = ALLOCATOR.malloc(size);
        this.indexBufferCapacity = size;

        this.format = PipelineManager.TERRAIN_VERTEX_FORMAT;
        this.vertexBuilder = PipelineManager.TERRAIN_VERTEX_FORMAT == CustomVertexFormat.COMPRESSED_TERRAIN
                ? new VertexBuilder.CompressedVertexBuilder() : new VertexBuilder.DefaultVertexBuilder();

        bufferBuilders = new TerrainBufferBuilder[QuadFacing.COUNT];
        for (int i = 0; i < QuadFacing.COUNT; i++) {
            bufferBuilders[i] = new TerrainBufferBuilder(size, format.getVertexSize(), vertexBuilder);
        }
    }

    public TerrainBufferBuilder getBufferBuilder(int i) {
        return bufferBuilders[i];
    }

    public void begin() {
        if (building) throw new IllegalStateException("Already building!");
        building = true;
    }

    public void setupQuadSortingPoints() {
        TerrainBufferBuilder noneBuilder = bufferBuilders[QuadFacing.NONE.ordinal()];
        quadSorter.setupQuadSortingPoints(noneBuilder.getPtr(), noneBuilder.getVertices(), format);
    }

    public void setupQuadSorting(float x, float y, float z) {
        quadSorter.setQuadSortOrigin(x, y, z);
        needsSorting = true;
    }

    public QuadSorter.SortState getSortState() {
        return quadSorter.getSortState();
    }

    public void restoreSortState(QuadSorter.SortState state) {
        quadSorter.restoreSortState(state);
        indexOnly = true;
    }

    public DrawState endDrawing() {
        for (TerrainBufferBuilder builder : bufferBuilders) {
            builder.end();
        }

        int vertexCount = quadSorter.getVertexCount();
        int indexCount = vertexCount / 4 * 6;
        VertexFormat.IndexType indexType = VertexFormat.IndexType.least(indexCount);
        boolean sequentialIndexing = true;

        if (needsSorting) {
            int indexBufferSize = indexCount * indexType.bytes;
            ensureIndexCapacity(indexBufferSize);
            quadSorter.putSortedQuadIndices(this, indexType);
            sequentialIndexing = false;
        }

        return new DrawState(format.getVertexSize(), indexCount, indexType, indexOnly, sequentialIndexing);
    }

    public ByteBuffer getIndexBuffer() {
        int indexCount = quadSorter.getVertexCount() * 6 / 4;
        return MemoryUtil.memByteBuffer(indexBufferPtr, indexCount * 2);
    }

    private void ensureIndexCapacity(int size) {
        if (size > indexBufferCapacity) {
            int newSize = (indexBufferCapacity + size) * 2;
            indexBufferPtr = ALLOCATOR.realloc(indexBufferPtr, newSize);
            LOGGER.debug("Grew index buffer from {} to {} bytes", indexBufferCapacity, newSize);
            indexBufferCapacity = newSize;
        }
    }

    public void reset() {
        building = false;
        indexOnly = false;
        needsSorting = false;
    }

    public void clear() {
        reset();
        for (TerrainBufferBuilder builder : bufferBuilders) builder.clear();
    }

    /** 释放所有本地内存（顶点缓冲区和索引缓冲区） */
    public void free() {
        for (TerrainBufferBuilder builder : bufferBuilders) {
            builder.free();
        }
        if (indexBufferPtr != 0L) {
            ALLOCATOR.free(indexBufferPtr);
            indexBufferPtr = 0L;
        }
    }

    public void setBlockAttributes(BlockState state) {}

    public record DrawState(int vertexSize, int indexCount, VertexFormat.IndexType indexType,
                            boolean indexOnly, boolean sequentialIndex) {
        public int indexCount() { return indexCount; }
        public boolean indexOnly() { return indexOnly; }
        public boolean sequentialIndex() { return sequentialIndex; }
    }
}