package net.vulkanmod.render.chunk.buffer;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.ChunkAreaManager;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.cull.GpuCuller;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.EnumMap;

import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {
    private static final int VERTEX_SIZE = PipelineManager.terrainVertexFormat.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;
    private static final int CMD_STRIDE = 20; // VkDrawIndexedIndirectCommand stride

    private final int index;
    private final Vector3i origin;
    private final int minHeight;

    private boolean allocated = false;
    AreaBuffer indexBuffer;
    private final EnumMap<TerrainRenderType, AreaBuffer> vertexBuffers = new EnumMap<>(TerrainRenderType.class);
    private final Object2LongOpenHashMap<DrawParameters> metaRegistrations = new Object2LongOpenHashMap<>();

    public interface DirtyListener { void markDirty(int areaIndex); }
    private static DirtyListener dirtyListener = null;
    public static void setDirtyListener(DirtyListener listener) { dirtyListener = listener; }
    void markMetaDirty() { if (dirtyListener != null) dirtyListener.markDirty(this.index); }

    public DrawBuffers(int index, Vector3i origin, int minHeight) {
        this.index = index;
        this.origin = origin;
        this.minHeight = minHeight;
    }

    public void upload(RenderSection section, UploadBuffer buffer, TerrainRenderType renderType) {
        var vertexBuffers = buffer.getVertexBuffers();

        if (buffer.indexOnly) {
            DrawParameters dp = section.getDrawParameters(renderType, QuadFacing.UNDEFINED.ordinal());
            AreaBuffer.Segment seg = this.indexBuffer.upload(buffer.getIndexBuffer(), dp.firstIndex, dp);
            dp.firstIndex = seg.offset / INDEX_SIZE;
            buffer.release();
            return;
        }

        for (int i = 0; i < QuadFacing.COUNT; i++) {
            DrawParameters dp = section.getDrawParameters(renderType, i);
            int vertexOffset = dp.vertexOffset;
            int firstIndex = 0;
            int indexCount = 0;

            var faceBuffer = vertexBuffers[i];
            if (faceBuffer != null) {
                AreaBuffer.Segment seg = getAreaBufferOrAlloc(renderType).upload(faceBuffer, vertexOffset, dp);
                vertexOffset = seg.offset / VERTEX_SIZE;
                dp.baseInstance = encodeSectionOffset(section.xOffset(), section.yOffset(), section.zOffset());
                indexCount = faceBuffer.limit() / VERTEX_SIZE * 6 / 4;
            }


            if (i == QuadFacing.UNDEFINED.ordinal() && !buffer.autoIndices && buffer.getIndexBuffer() != null) {
                if (this.indexBuffer == null) {
                    this.indexBuffer = new AreaBuffer(AreaBuffer.Usage.INDEX, 60000, INDEX_SIZE);
                }
                AreaBuffer.Segment seg = this.indexBuffer.upload(buffer.getIndexBuffer(), dp.firstIndex, dp);
                firstIndex = seg.offset / INDEX_SIZE;
            }

            dp.firstIndex = firstIndex;
            dp.vertexOffset = vertexOffset;
            dp.indexCount = indexCount;
        }


        int typeIdx = GpuCuller.typeIndex(renderType);
        if (typeIdx >= 0) {
            int lx = section.xOffset() - this.origin.x();
            int ly = section.yOffset() - this.origin.y();
            int lz = section.zOffset() - this.origin.z();
            long packed = ((long) lx << 24) | ((long) ly << 16) | ((long) lz << 8) | typeIdx;
            this.metaRegistrations.put(section.getDrawParameters(renderType, QuadFacing.UNDEFINED.ordinal()), packed);
            markMetaDirty();
        }
        buffer.release();
    }

    private AreaBuffer getAreaBufferOrAlloc(TerrainRenderType renderType) {
        this.allocated = true;
        int initialSize = switch (renderType) {
            case SOLID, CUTOUT -> 100000;
            case CUTOUT_MIPPED -> 250000;
            case TRANSLUCENT, TRIPWIRE -> 60000;
        };
        return this.vertexBuffers.computeIfAbsent(renderType, r -> new AreaBuffer(AreaBuffer.Usage.VERTEX, initialSize, VERTEX_SIZE));
    }

    public AreaBuffer getAreaBuffer(TerrainRenderType r) { return this.vertexBuffers.get(r); }
    public EnumMap<TerrainRenderType, AreaBuffer> getVertexBuffers() { return vertexBuffers; }
    public AreaBuffer getIndexBuffer() { return indexBuffer; }

    private int encodeSectionOffset(int xOffset, int yOffset, int zOffset) {
        return (yOffset - this.minHeight & 127) << 16 | (zOffset & 127) << 8 | (xOffset & 127);
    }

    public static final float POS_OFFSET = PipelineManager.terrainVertexFormat == CustomVertexFormat.COMPRESSED_TERRAIN ? 4.0f : 0.0f;

    private void updateChunkAreaOrigin(VkCommandBuffer commandBuffer, Pipeline pipeline, double camX, double camY, double camZ, MemoryStack stack) {
        float xOff = (float)(origin.x + POS_OFFSET - camX);
        float yOff = (float)(origin.y + POS_OFFSET - camY);
        float zOff = (float)(origin.z + POS_OFFSET - camZ);
        ByteBuffer buf = stack.malloc(12);
        buf.putFloat(0, xOff).putFloat(4, yOff).putFloat(8, zOff);
        vkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, buf);
    }

    private int getMask(Vec3 camera, RenderSection section) {
        final int sx = section.xOffset, sy = section.yOffset, sz = section.zOffset;
        int mask = 1 << QuadFacing.UNDEFINED.ordinal();
        mask |= camera.x - sx >= 0 ? 1 << QuadFacing.X_POS.ordinal() : 0;
        mask |= camera.y - sy >= 0 ? 1 << QuadFacing.Y_POS.ordinal() : 0;
        mask |= camera.z - sz >= 0 ? 1 << QuadFacing.Z_POS.ordinal() : 0;
        mask |= camera.x - (sx + 16) < 0 ? 1 << QuadFacing.X_NEG.ordinal() : 0;
        mask |= camera.y - (sy + 16) < 0 ? 1 << QuadFacing.Y_NEG.ordinal() : 0;
        mask |= camera.z - (sz + 16) < 0 ? 1 << QuadFacing.Z_NEG.ordinal() : 0;
        return mask;
    }

    public void buildDrawBatchesIndirect(Vec3 cameraPos, IndirectBuffer indirectBuffer,
                                         StaticQueue<RenderSection> queue, TerrainRenderType terrainRenderType) {
        boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;
        int drawCount = 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = stack.malloc(CMD_STRIDE * queue.size() * QuadFacing.COUNT);
            long bufferPtr = MemoryUtil.memAddress0(byteBuffer);

            for (var it = queue.iterator(isTranslucent); it.hasNext(); ) {
                RenderSection section = it.next();
                int mask = getMask(cameraPos, section);

                for (int i = 0; i < QuadFacing.COUNT; i++) {
                    if ((mask & (1 << i)) == 0) continue;
                    DrawParameters dp = section.getDrawParameters(terrainRenderType, i);
                    if (dp.indexCount <= 0) continue;

                    long ptr = bufferPtr + ((long) drawCount * CMD_STRIDE);
                    MemoryUtil.memPutInt(ptr, dp.indexCount);
                    MemoryUtil.memPutInt(ptr + 4, 1);
                    MemoryUtil.memPutInt(ptr + 8, dp.firstIndex == -1 ? 0 : dp.firstIndex);
                    MemoryUtil.memPutInt(ptr + 12, dp.vertexOffset);
                    MemoryUtil.memPutInt(ptr + 16, dp.baseInstance);
                    drawCount++;
                }
            }

            if (drawCount == 0) return;

            byteBuffer.limit(drawCount * CMD_STRIDE);
            byteBuffer.position(0);
            indirectBuffer.recordCopyCmd(byteBuffer);
            vkCmdDrawIndexedIndirect(Renderer.getCommandBuffer(), indirectBuffer.getId(),
                    indirectBuffer.getOffset(), drawCount, CMD_STRIDE);
        }
    }

    public void buildDrawBatchesDirect(Vec3 cameraPos, StaticQueue<RenderSection> queue,
                                       TerrainRenderType renderType) {
        boolean isTranslucent = renderType == TerrainRenderType.TRANSLUCENT;
        VkCommandBuffer cmd = Renderer.getCommandBuffer();
        for (var it = queue.iterator(isTranslucent); it.hasNext(); ) {
            RenderSection section = it.next();
            int mask = getMask(cameraPos, section);
            for (int i = 0; i < QuadFacing.COUNT; i++) {
                if ((mask & (1 << i)) == 0) continue;
                DrawParameters dp = section.getDrawParameters(renderType, i);
                if (dp.indexCount <= 0) continue;
                int firstIndex = dp.firstIndex == -1 ? 0 : dp.firstIndex;
                vkCmdDrawIndexed(cmd, dp.indexCount, 1, firstIndex, dp.vertexOffset, dp.baseInstance);
            }
        }
    }

    public void bindBuffers(VkCommandBuffer commandBuffer, Pipeline pipeline,
                            TerrainRenderType terrainRenderType, double camX, double camY, double camZ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var vertexBuffer = getAreaBuffer(terrainRenderType);
            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(vertexBuffer.getId()), stack.npointer(0));
            updateChunkAreaOrigin(commandBuffer, pipeline, camX, camY, camZ, stack);
        }

        if (terrainRenderType == TerrainRenderType.TRANSLUCENT && this.indexBuffer != null) {
            vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }
    }

    public void releaseBuffers() {
        if (!allocated) return;
        vertexBuffers.values().forEach(AreaBuffer::freeBuffer);
        vertexBuffers.clear();
        if (indexBuffer != null) indexBuffer.freeBuffer();
        indexBuffer = null;
        allocated = false;
    }

    public boolean isAllocated() { return !vertexBuffers.isEmpty(); }

    public int writeSectionMeta(java.nio.ByteBuffer scratch) {
        long ptr = MemoryUtil.memAddress0(scratch);
        int count = 0;
        for (var entry : this.metaRegistrations.object2LongEntrySet()) {
            if (count >= GpuCuller.AREA_META_CAP) break;
            DrawParameters dp = entry.getKey();
            if (dp.indexCount <= 0) continue;
            long packed = entry.getLongValue();
            long p = ptr + (long) count * GpuCuller.META_ENTRY_SIZE;
            MemoryUtil.memPutInt(p,      (int)((packed >> 24) & 0xFF));
            MemoryUtil.memPutInt(p + 4,  (int)((packed >> 16) & 0xFF));
            MemoryUtil.memPutInt(p + 8,  (int)((packed >> 8) & 0xFF));
            MemoryUtil.memPutInt(p + 12, (int)(packed & 0xF));
            MemoryUtil.memPutInt(p + 16, dp.indexCount);
            MemoryUtil.memPutInt(p + 20, dp.firstIndex);
            MemoryUtil.memPutInt(p + 24, dp.vertexOffset);
            MemoryUtil.memPutInt(p + 28, dp.baseInstance);
            count++;
        }
        return count;
    }

    public static class DrawParameters {
        int indexCount = 0;
        int firstIndex = -1;
        int vertexOffset = -1;
        int baseInstance;

        public void reset(ChunkArea chunkArea, TerrainRenderType r) {
            AreaBuffer areaBuffer = chunkArea.getDrawBuffers().getAreaBuffer(r);
            if (areaBuffer != null && this.vertexOffset != -1) {
                areaBuffer.setSegmentFree(this.vertexOffset * VERTEX_SIZE);
            }
            DrawBuffers db = chunkArea.getDrawBuffers();
            if (db.metaRegistrations.containsKey(this)) {
                db.metaRegistrations.removeLong(this);
                db.markMetaDirty();
            }
            this.indexCount = 0;
            this.firstIndex = -1;
            this.vertexOffset = -1;
        }
    }
}