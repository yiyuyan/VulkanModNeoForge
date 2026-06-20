package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.util.SortUtil;
import org.lwjgl.system.MemoryUtil;

public class QuadSorter {

    private float[] points;        // x,y,z per quad, flat
    private int pointCount;
    private float sortX = Float.NaN, sortY = Float.NaN, sortZ = Float.NaN;
    private boolean indexOnly;
    private int vertexCount;

    // reusable scratch (one QuadSorter per builder thread)
    private float[] scratchDist = new float[0];
    private int[] scratchIdx = new int[0];

    public void setQuadSortOrigin(float x, float y, float z) {
        this.sortX = x; this.sortY = y; this.sortZ = z;
    }

    public SortState getSortState() {
        return new SortState(this.vertexCount, this.points, this.pointCount);
    }

    public void restoreSortState(SortState sortState) {
        this.vertexCount = sortState.vertexCount;
        this.points = sortState.points;
        this.pointCount = sortState.pointCount;
        this.indexOnly = true;
    }

    public void setupQuadSortingPoints(long bufferPtr, int vertexCount, VertexFormat format) {
        this.vertexCount = vertexCount;
        this.pointCount = vertexCount / 4;
        if (this.points == null || this.points.length < this.pointCount * 3 || this.indexOnly) {
            // never write into an array that a SortState may still reference
            this.points = new float[this.pointCount * 3];
            this.indexOnly = false;
        }
        float[] pts = this.points;

        int vertexSize = format.getVertexSize();
        int quadStride = vertexSize * 4;
        int offset = vertexSize * 2;

        if (format == CustomVertexFormat.COMPRESSED_TERRAIN) {
            final float invConv = 1.0f / VertexBuilder.CompressedVertexBuilder.POS_CONV_MUL;
            final float convOffset = -VertexBuilder.CompressedVertexBuilder.POS_OFFSET;

            for (int m = 0; m < this.pointCount; ++m) {
                long ptr = bufferPtr + (long) m * quadStride;
                short x0 = MemoryUtil.memGetShort(ptr);
                short y0 = MemoryUtil.memGetShort(ptr + 2);
                short z0 = MemoryUtil.memGetShort(ptr + 4);
                short x2 = MemoryUtil.memGetShort(ptr + offset);
                short y2 = MemoryUtil.memGetShort(ptr + offset + 2);
                short z2 = MemoryUtil.memGetShort(ptr + offset + 4);
                pts[m * 3]     = (x0 + x2) * invConv * 0.5f + convOffset;
                pts[m * 3 + 1] = (y0 + y2) * invConv * 0.5f + convOffset;
                pts[m * 3 + 2] = (z0 + z2) * invConv * 0.5f + convOffset;
            }
        } else {
            for (int m = 0; m < this.pointCount; ++m) {
                long ptr = bufferPtr + (long) m * quadStride;
                pts[m * 3]     = (MemoryUtil.memGetFloat(ptr)     + MemoryUtil.memGetFloat(ptr + offset))     * 0.5f;
                pts[m * 3 + 1] = (MemoryUtil.memGetFloat(ptr + 4) + MemoryUtil.memGetFloat(ptr + offset + 4)) * 0.5f;
                pts[m * 3 + 2] = (MemoryUtil.memGetFloat(ptr + 8) + MemoryUtil.memGetFloat(ptr + offset + 8)) * 0.5f;
            }
        }
    }

    public void putSortedQuadIndices(TerrainBufferBuilder bufferBuilder, VertexFormat.IndexType indexType) {
        final int n = this.pointCount;
        if (scratchDist.length < n) {
            scratchDist = new float[n];
            scratchIdx = new int[n];
        }
        final float[] dist = scratchDist;
        final int[] idx = scratchIdx;

        for (int i = 0; i < n; i++) {
            float dx = points[i * 3] - sortX;
            float dy = points[i * 3 + 1] - sortY;
            float dz = points[i * 3 + 2] - sortZ;
            dist[i] = dx * dx + dy * dy + dz * dz;
            idx[i] = i;
        }

        SortUtil.mergeSort(idx, dist); // same algorithm + order as before

        long ptr = bufferBuilder.getPtr();
        final boolean shortType = indexType.bytes == 2;
        for (int i = 0; i < n; ++i) {
            final int baseVertex = idx[i] * 4;
            if (shortType) {
                MemoryUtil.memPutShort(ptr,      (short) baseVertex);
                MemoryUtil.memPutShort(ptr + 2,  (short) (baseVertex + 1));
                MemoryUtil.memPutShort(ptr + 4,  (short) (baseVertex + 2));
                MemoryUtil.memPutShort(ptr + 6,  (short) (baseVertex + 2));
                MemoryUtil.memPutShort(ptr + 8,  (short) (baseVertex + 3));
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

    public static class SortState {
        final int vertexCount;
        final float[] points;
        final int pointCount;

        SortState(int vertexCount, float[] points, int pointCount) {
            this.vertexCount = vertexCount;
            // SortState must own a stable copy: the sorter's array is reused per thread
            this.points = java.util.Arrays.copyOf(points, pointCount * 3);
            this.pointCount = pointCount;
        }
    }
}
