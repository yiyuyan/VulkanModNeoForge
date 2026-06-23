package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.util.SortUtil;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class QuadSorter {

    private Vector3f[] sortingPoints;
    private float sortX = Float.NaN;
    private float sortY = Float.NaN;
    private float sortZ = Float.NaN;
    private boolean indexOnly;

    private VertexFormat format;
    private int vertexCount;
    private int indexCount;

    private float[] distances;
    private int[] sortingPointsIndices;

    public void setQuadSortOrigin(float x, float y, float z) {
        this.sortX = x;
        this.sortY = y;
        this.sortZ = z;
    }

    public SortState getSortState() {
        return new SortState(this.vertexCount, this.sortingPoints, this.distances, this.sortingPointsIndices);
    }

    public void restoreSortState(SortState sortState) {
        this.vertexCount = sortState.vertexCount;
        this.sortingPoints = sortState.sortingPoints;
        this.distances = sortState.distances;
        this.sortingPointsIndices = sortState.sortingPointsIndices;
        this.indexOnly = true;
    }

    public void setupQuadSortingPoints(long bufferPtr, int vertexCount, VertexFormat format) {
        this.vertexCount = vertexCount;
        int pointCount = vertexCount / 4;
        Vector3f[] sortingPoints = new Vector3f[pointCount];

        int vertexSize = format.getVertexSize();
        int quadStride = vertexSize * 4;
        int offset = vertexSize * 2;

        if (format == CustomVertexFormat.COMPRESSED_TERRAIN) {
            final float invConv = 1.0f / VertexBuilder.CompressedVertexBuilder.POS_CONV_MUL;
            final float convOffset = -VertexBuilder.CompressedVertexBuilder.POS_OFFSET;

            for (int m = 0; m < pointCount; ++m) {
                long ptr = bufferPtr + (long) m * quadStride;
                short x0 = MemoryUtil.memGetShort(ptr);
                short y0 = MemoryUtil.memGetShort(ptr + 2);
                short z0 = MemoryUtil.memGetShort(ptr + 4);
                short x2 = MemoryUtil.memGetShort(ptr + offset);
                short y2 = MemoryUtil.memGetShort(ptr + offset + 2);
                short z2 = MemoryUtil.memGetShort(ptr + offset + 4);

                float xa = (x0 + x2) * invConv * 0.5f + convOffset;
                float ya = (y0 + y2) * invConv * 0.5f + convOffset;
                float za = (z0 + z2) * invConv * 0.5f + convOffset;
                sortingPoints[m] = new Vector3f(xa, ya, za);
            }
        } else {
            for (int m = 0; m < pointCount; ++m) {
                long ptr = bufferPtr + (long) m * quadStride;
                float x0 = MemoryUtil.memGetFloat(ptr);
                float y0 = MemoryUtil.memGetFloat(ptr + 4);
                float z0 = MemoryUtil.memGetFloat(ptr + 8);
                float x2 = MemoryUtil.memGetFloat(ptr + offset);
                float y2 = MemoryUtil.memGetFloat(ptr + offset + 4);
                float z2 = MemoryUtil.memGetFloat(ptr + offset + 8);

                sortingPoints[m] = new Vector3f(
                        (x0 + x2) * 0.5f,
                        (y0 + y2) * 0.5f,
                        (z0 + z2) * 0.5f
                );
            }
        }

        this.sortingPoints = sortingPoints;
        this.distances = new float[pointCount];
        this.sortingPointsIndices = new int[pointCount];
    }

    /** 写入 TerrainBufferBuilder 的索引区（兼容旧逻辑） */
    public void putSortedQuadIndices(TerrainBufferBuilder bufferBuilder, VertexFormat.IndexType indexType) {
        float[] distances = this.distances;
        int[] indices = this.sortingPointsIndices;

        for (int i = 0; i < this.sortingPoints.length; indices[i] = i++) {
            float dx = this.sortingPoints[i].x() - this.sortX;
            float dy = this.sortingPoints[i].y() - this.sortY;
            float dz = this.sortingPoints[i].z() - this.sortZ;
            distances[i] = dx * dx + dy * dy + dz * dz;
        }

        SortUtil.mergeSort(indices, distances);

        long ptr = bufferBuilder.getPtr();
        final int size = indexType.bytes;
        for (int i = 0; i < indices.length; ++i) {
            int baseVertex = indices[i] * 4;
            if (size == 2) {
                MemoryUtil.memPutShort(ptr,      (short) baseVertex);
                MemoryUtil.memPutShort(ptr + 2,  (short)(baseVertex + 1));
                MemoryUtil.memPutShort(ptr + 4,  (short)(baseVertex + 2));
                MemoryUtil.memPutShort(ptr + 6,  (short)(baseVertex + 2));
                MemoryUtil.memPutShort(ptr + 8,  (short)(baseVertex + 3));
                MemoryUtil.memPutShort(ptr + 10, (short) baseVertex);
                ptr += 12;
            } else {
                MemoryUtil.memPutInt(ptr,      baseVertex);
                MemoryUtil.memPutInt(ptr + 4,  baseVertex + 1);
                MemoryUtil.memPutInt(ptr + 8,  baseVertex + 2);
                MemoryUtil.memPutInt(ptr + 12, baseVertex + 2);
                MemoryUtil.memPutInt(ptr + 16, baseVertex + 3);
                MemoryUtil.memPutInt(ptr + 20, baseVertex);
                ptr += 24;
            }
        }
    }

    public void putSortedQuadIndices(TerrainBuilder bufferBuilder, VertexFormat.IndexType indexType) {
        float[] distances = new float[this.sortingPoints.length];
        int[] sortingPoints = new int[this.sortingPoints.length];

        for (int i = 0; i < this.sortingPoints.length; sortingPoints[i] = i++) {
            float dx = this.sortingPoints[i].x() - this.sortX;
            float dy = this.sortingPoints[i].y() - this.sortY;
            float dz = this.sortingPoints[i].z() - this.sortZ;
            distances[i] = dx * dx + dy * dy + dz * dz;
        }

        SortUtil.mergeSort(sortingPoints, distances);

        long ptr = bufferBuilder.indexBufferPtr;
        final int size = indexType.bytes;
        for (int i = 0; i < sortingPoints.length; ++i) {
            int baseVertex = sortingPoints[i] * 4;
            MemoryUtil.memPutInt(ptr,      baseVertex);
            MemoryUtil.memPutInt(ptr + 4,  baseVertex + 1);
            MemoryUtil.memPutInt(ptr + 8,  baseVertex + 2);
            MemoryUtil.memPutInt(ptr + 12, baseVertex + 2);
            MemoryUtil.memPutInt(ptr + 16, baseVertex + 3);
            MemoryUtil.memPutInt(ptr + 20, baseVertex);
            ptr += size * 6L;
        }
    }

    public void reset() {
        this.vertexCount = 0;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public int getIndexCount() {
        return indexCount;
    }

    public static class SortState {
        final int vertexCount;
        final Vector3f[] sortingPoints;
        final float[] distances;
        final int[] sortingPointsIndices;

        SortState(int vertexCount, Vector3f[] sortingPoints, float[] distances, int[] sortingPointsIndices) {
            this.vertexCount = vertexCount;
            this.sortingPoints = sortingPoints;
            this.distances = distances;
            this.sortingPointsIndices = sortingPointsIndices;
        }
    }
}