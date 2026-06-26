package net.vulkanmod.render.chunk.cull;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.ChunkAreaManager;
import net.vulkanmod.render.chunk.frustum.VFrustum;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.shader.ImageComputePipeline;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.vkCmdDrawIndexedIndirectCount;

/**
 * Owns the GPU-driven culling resources: persistent per-area section metadata,
 * per-frame AreaInfo, and the compute-generated indirect command + count buffers.
 *
 * Lifecycle: created per world load (area count fixed for a given render
 * distance/world height), destroyed on world unload or render-distance change.
 */
public class GpuCuller {
    // 512 sections per area x up to 3 opaque render types = 1536 possible entries;
    // a smaller cap would silently drop draws in dense areas. Must match cull.comp.
    public static final int AREA_META_CAP = 1536;
    public static final int META_ENTRY_SIZE = 32;
    public static final int AREA_META_BYTES = AREA_META_CAP * META_ENTRY_SIZE; // 49152 <= vkCmdUpdateBuffer max 65536
    public static final int AREA_TYPE_CMD_CAP = 512;
    public static final int CMD_SIZE = 20;
    public static final int OPAQUE_TYPES = 3;
    public static final int AREA_INFO_SIZE = 16;
    public static final int PUSH_SIZE = 112;
    // std430 stride of cull.comp's OcclParam (mat4 64 + ivec2 8 + 3*4, rounded up to 16).
    public static final int OCCL_PARAM_SIZE = 96;

    public static boolean isSupported() {
        return DeviceManager.supportsDrawIndirectCount;
    }

    public static boolean isEnabled() {
        return Initializer.CONFIG.gpuCulling && isSupported();
    }

    public static int typeIndex(TerrainRenderType type) {
        return switch (type) {
            case SOLID -> 0;
            case CUTOUT_MIPPED -> 1;
            case CUTOUT -> 2;
            default -> -1; // TRANSLUCENT / TRIPWIRE: not GPU-culled
        };
    }

    private final int areaCount;

    private final StorageBuffer metaBuffer;     // device-local, persistent
    private final StorageBuffer areaInfoBuffer; // host-visible, framesNum regions
    private final StorageBuffer cmdBuffer;      // device-local, compute-written, indirect-read
    private final StorageBuffer countBuffer;    // device-local, compute-written, indirect-read

    private final ImageComputePipeline pipeline;
    private final int framesNum;
    private final StorageBuffer occlParamsBuffer;   // host-visible, framesNum regions (Hi-Z params)
    private final VulkanImage dummyHiZ;             // 1x1 placeholder bound at binding 4 when occlusion off
    private final long dummyView, dummySampler;
    private final long[] boundHiZView;             // per-set: image view currently bound at binding 4

    private final IntOpenHashSet dirtyAreas = new IntOpenHashSet();
    private final int[] entryCounts; // per area: entry count from last mirror rebuild
    private final ByteBuffer metaScratch = MemoryUtil.memAlloc(AREA_META_BYTES);

    private final Vector4f planeScratch = new Vector4f();

    // Hi-Z occlusion pyramid (previous-frame depth). Null until the first build, or whenever
    // occlusion is disabled. hiZValid only becomes true after a build has been recorded, so the
    // pyramid is never consumed before it holds real depth (frame 0 / post-recreate guard).
    private HiZPyramid hiZPyramid;
    private boolean hiZValid = false;

    public GpuCuller(int areaCount, int framesNum) {
        this.areaCount = areaCount;
        this.entryCounts = new int[areaCount];

        this.metaBuffer = new StorageBuffer(areaCount * AREA_META_BYTES,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.GPU_MEM);
        this.areaInfoBuffer = new StorageBuffer(framesNum * areaCount * AREA_INFO_SIZE,
                0, MemoryTypes.HOST_MEM);
        this.cmdBuffer = new StorageBuffer(areaCount * OPAQUE_TYPES * AREA_TYPE_CMD_CAP * CMD_SIZE,
                VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, MemoryTypes.GPU_MEM);
        this.countBuffer = new StorageBuffer(areaCount * OPAQUE_TYPES * Integer.BYTES,
                VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.GPU_MEM);

        this.framesNum = framesNum;
        this.occlParamsBuffer = new StorageBuffer(framesNum * OCCL_PARAM_SIZE, 0, MemoryTypes.HOST_MEM);

        // Cull pipeline: 4 storage buffers (meta/area/cmd/count) + Hi-Z sampler (binding 4) +
        // occlusion params (binding 5). ONE descriptor set PER frame-in-flight so the per-frame Hi-Z
        // binding can be re-pointed without touching a set still read by an in-flight frame.
        this.pipeline = new ImageComputePipeline("cull", loadShaderSource(),
                new int[]{ VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                           VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                           VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER },
                PUSH_SIZE, framesNum);

        // A 1x1 placeholder image kept bound at binding 4 whenever occlusion is off or before the
        // first pyramid exists (a combined-image-sampler descriptor must always reference a live image).
        this.dummyHiZ = VulkanImage.createWhiteTexture();
        this.dummyHiZ.readOnlyLayout();
        this.dummyView = this.dummyHiZ.getImageView();
        this.dummySampler = this.dummyHiZ.getSampler();

        this.boundHiZView = new long[framesNum];
        for (int s = 0; s < framesNum; s++) {
            this.pipeline.updateBuffer(s, 0, this.metaBuffer);
            this.pipeline.updateBuffer(s, 1, this.areaInfoBuffer);
            this.pipeline.updateBuffer(s, 2, this.cmdBuffer);
            this.pipeline.updateBuffer(s, 3, this.countBuffer);
            this.pipeline.updateSampledImage(s, 4, this.dummyView, this.dummySampler);
            this.pipeline.updateBuffer(s, 5, this.occlParamsBuffer);
            this.boundHiZView[s] = this.dummyView;
        }
    }

    private static String loadShaderSource() {
        try (InputStream in = GpuCuller.class.getResourceAsStream("/assets/vulkanmod/shaders/compute/cull.comp")) {
            if (in == null) throw new IOException("cull.comp resource missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load cull.comp", e);
        }
    }

    public void markDirty(int areaIndex) {
        this.dirtyAreas.add(areaIndex);
    }

    /**
     * Records the whole cull pass into the frame's command buffer. MUST be called
     * outside a render pass (caller ends the bound pass first).
     */
    public void recordCull(VkCommandBuffer commandBuffer, ChunkAreaManager areaManager,
                           StaticQueue<ChunkArea> visibleAreas, VFrustum frustum, Matrix4f prevViewProj) {
        int frame = Renderer.getCurrentFrame() % this.framesNum;
        int frameBase = frame * this.areaCount;

        // Hi-Z occlusion setup. Re-point binding 4 only when the target view changes; set[frame] was
        // last used 2+ frames ago (complete), so updating it here (before this frame binds it) is safe.
        boolean occ = Initializer.CONFIG.occlusionCulling && this.hiZValid();
        long hiZView = occ ? this.hiZPyramid.getSampledView() : this.dummyView;
        long hiZSampler = occ ? this.hiZPyramid.getSampler() : this.dummySampler;
        if (this.boundHiZView[frame] != hiZView) {
            this.pipeline.updateSampledImage(frame, 4, hiZView, hiZSampler);
            this.boundHiZView[frame] = hiZView;
        }
        // Per-frame occlusion params (host-visible; this frame writes its own region, the in-flight
        // frame reads a different one). Made visible to the dispatch by the queue submit.
        long opBase = this.occlParamsBuffer.getDataPtr() + (long) frame * OCCL_PARAM_SIZE;
        if (occ) {
            java.nio.ByteBuffer opbb = MemoryUtil.memByteBuffer(opBase, OCCL_PARAM_SIZE);
            prevViewProj.get(0, opbb); // column-major mat4 at bytes 0..63
        }
        MemoryUtil.memPutInt(opBase + 64, occ ? this.hiZPyramid.getWidth() : 0);
        MemoryUtil.memPutInt(opBase + 68, occ ? this.hiZPyramid.getHeight() : 0);
        MemoryUtil.memPutInt(opBase + 72, occ ? this.hiZPyramid.getMipCount() : 0);
        MemoryUtil.memPutInt(opBase + 76, occ ? 1 : 0);
        MemoryUtil.memPutFloat(opBase + 80, 0.0025f); // depthBias: slack so coplanar/visible sections aren't false-culled

        // 1. Re-upload metadata mirrors for dirty areas (chunk builds/frees since last frame).
        boolean anyMetaUpload = !this.dirtyAreas.isEmpty();
        if (anyMetaUpload) {
            // WAR guard: the previous in-flight frame's dispatch may still be reading
            // metaBuffer; the transfer write below needs an execution dependency on it.
            // (Queue submission order alone does not order our write after its reads.)
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer war = VkBufferMemoryBarrier.calloc(1, stack);
                fillBarrier(war.get(0), this.metaBuffer.getId(),
                        VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
                vkCmdPipelineBarrier(commandBuffer,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, war, null);
            }

            var it = this.dirtyAreas.intIterator();
            while (it.hasNext()) {
                int areaIdx = it.nextInt();
                ChunkArea area = areaManager.getChunkArea(areaIdx);
                this.metaScratch.clear();
                int count = area.getDrawBuffers().writeSectionMeta(this.metaScratch);
                this.entryCounts[areaIdx] = count;
                // count == 0 leaves stale device-side meta bytes; safe because the
                // shader gates on AreaInfo.count before reading meta for an area.
                if (count > 0) {
                    this.metaScratch.position(0).limit(count * META_ENTRY_SIZE);
                    vkCmdUpdateBuffer(commandBuffer, this.metaBuffer.getId(),
                            (long) areaIdx * AREA_META_BYTES, this.metaScratch);
                    this.metaScratch.limit(this.metaScratch.capacity());
                }
            }
            this.dirtyAreas.clear();
        }

        // 2. Per-frame AreaInfo: zero this frame's region, then fill visible areas.
        long infoPtr = this.areaInfoBuffer.getDataPtr() + (long) frameBase * AREA_INFO_SIZE;
        MemoryUtil.memSet(infoPtr, 0, (long) this.areaCount * AREA_INFO_SIZE);

        final float camX = (float) frustum.getCamX();
        final float camY = (float) frustum.getCamY();
        final float camZ = (float) frustum.getCamZ();
        for (var iterator = visibleAreas.iterator(false); iterator.hasNext(); ) {
            ChunkArea area = iterator.next();
            int count = this.entryCounts[area.index];
            if (count == 0) continue;
            long p = infoPtr + (long) area.index * AREA_INFO_SIZE;
            MemoryUtil.memPutFloat(p,      area.getPosition().x() - camX);
            MemoryUtil.memPutFloat(p + 4,  area.getPosition().y() - camY);
            MemoryUtil.memPutFloat(p + 8,  area.getPosition().z() - camZ);
            MemoryUtil.memPutInt(p + 12,   count);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 3. WAR/WAW guard: previous frame's indirect reads vs this pass's writes.
            VkBufferMemoryBarrier.Buffer pre = VkBufferMemoryBarrier.calloc(2, stack);
            fillBarrier(pre.get(0), this.cmdBuffer.getId(),
                    VK_ACCESS_INDIRECT_COMMAND_READ_BIT, VK_ACCESS_SHADER_WRITE_BIT);
            fillBarrier(pre.get(1), this.countBuffer.getId(),
                    VK_ACCESS_INDIRECT_COMMAND_READ_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, pre, null);

            // 4. Zero all counters.
            vkCmdFillBuffer(commandBuffer, this.countBuffer.getId(), 0, VK_WHOLE_SIZE, 0);

            // 5. Transfer writes (counters + meta uploads) visible to compute.
            VkBufferMemoryBarrier.Buffer mid = VkBufferMemoryBarrier.calloc(anyMetaUpload ? 2 : 1, stack);
            fillBarrier(mid.get(0), this.countBuffer.getId(),
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
            if (anyMetaUpload) {
                fillBarrier(mid.get(1), this.metaBuffer.getId(),
                        VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
            }
            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, null, mid, null);

            // 6. Push constants: 6 frustum planes + frameBase.
            ByteBuffer push = stack.malloc(PUSH_SIZE);
            Matrix4f m = frustum.getMatrix();
            for (int i = 0; i < 6; i++) {
                m.frustumPlane(i, this.planeScratch);
                push.putFloat(i * 16,      this.planeScratch.x);
                push.putFloat(i * 16 + 4,  this.planeScratch.y);
                push.putFloat(i * 16 + 8,  this.planeScratch.z);
                push.putFloat(i * 16 + 12, this.planeScratch.w);
            }
            push.putInt(96, frameBase);
            push.putInt(100, frame).putInt(104, 0).putInt(108, 0);

            // 7. Dispatch: one thread per metadata slot of every area.
            int groups = (this.areaCount * AREA_META_CAP + 255) / 256;
            this.pipeline.bindAndDispatch(frame, commandBuffer, push, groups, 1, 1);

            // 8. Compute writes visible to the indirect-draw stage.
            VkBufferMemoryBarrier.Buffer post = VkBufferMemoryBarrier.calloc(2, stack);
            fillBarrier(post.get(0), this.cmdBuffer.getId(),
                    VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            fillBarrier(post.get(1), this.countBuffer.getId(),
                    VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    0, null, post, null);
        }
    }

    private static void fillBarrier(VkBufferMemoryBarrier barrier, long buffer, int srcAccess, int dstAccess) {
        barrier.sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .buffer(buffer)
                .offset(0)
                .size(VK_WHOLE_SIZE);
    }

    /** Issues the count-based indirect draw for one (area, opaque type). */
    public void drawArea(VkCommandBuffer commandBuffer, int areaIndex, int typeIdx) {
        int region = areaIndex * OPAQUE_TYPES + typeIdx;
        vkCmdDrawIndexedIndirectCount(commandBuffer,
                this.cmdBuffer.getId(), (long) region * AREA_TYPE_CMD_CAP * CMD_SIZE,
                this.countBuffer.getId(), (long) region * Integer.BYTES,
                AREA_TYPE_CMD_CAP, CMD_SIZE);
    }

    public boolean hasEntries(int areaIndex) {
        return this.entryCounts[areaIndex] > 0;
    }

    // ---- Hi-Z occlusion pyramid -------------------------------------------------------------

    /**
     * Lazily allocate the pyramid for the given source (depth) resolution. Recreates it if the
     * resolution changed (window/render-distance resize), invalidating the stale pyramid first.
     */
    public void ensureHiZ(VulkanImage sceneDepth) {
        if (this.hiZPyramid != null
                && (this.hiZPyramid.getSrcWidth() != sceneDepth.width
                    || this.hiZPyramid.getSrcHeight() != sceneDepth.height)) {
            this.hiZPyramid.cleanUp();
            this.hiZPyramid = null;
            this.hiZValid = false;
        }
        if (this.hiZPyramid == null)
            this.hiZPyramid = new HiZPyramid(sceneDepth);
    }

    /**
     * Records the pyramid build from the just-rendered opaque depth. The source depth must already
     * be in SHADER_READ_ONLY layout (caller transitions it; the depth view is bound into the
     * per-mip descriptor sets at {@link #ensureHiZ}). The pyramid becomes consumable next frame.
     * MUST be called outside a render pass (caller suspends/resumes the bound pass).
     */
    public void recordHiZBuild(VkCommandBuffer commandBuffer) {
        if (this.hiZPyramid == null) return;
        this.hiZPyramid.build(commandBuffer);
        this.hiZValid = true;
    }

    public boolean hiZValid() { return this.hiZValid && this.hiZPyramid != null; }

    public HiZPyramid getHiZPyramid() { return this.hiZPyramid; }

    public void cleanUp() {
        this.pipeline.cleanUp();
        this.metaBuffer.scheduleFree();
        this.areaInfoBuffer.scheduleFree();
        this.cmdBuffer.scheduleFree();
        this.countBuffer.scheduleFree();
        this.occlParamsBuffer.scheduleFree();
        MemoryUtil.memFree(this.metaScratch);
        this.dummyHiZ.free();
        if (this.hiZPyramid != null) {
            this.hiZPyramid.cleanUp();
            this.hiZPyramid = null;
        }
        this.hiZValid = false;
    }
}
