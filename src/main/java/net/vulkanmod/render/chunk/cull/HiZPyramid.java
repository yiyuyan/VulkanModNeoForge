package net.vulkanmod.render.chunk.cull;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.ImageComputePipeline;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Hierarchical-Z depth pyramid built from the previous frame's depth attachment.
 * Mip 0 = downsample of scene depth; each higher mip = max-reduction of the one below.
 * The whole pyramid is sampled by cull.comp next frame for occlusion culling.
 *
 * One descriptor set per mip is written ONCE at construction (binding 0 = that mip's source view,
 * binding 1 = that mip's destination view). build() therefore only binds + dispatches — updating a
 * descriptor set while it is bound to the recording command buffer is illegal and would invalidate
 * the whole frame's command buffer.
 */
public class HiZPyramid {
    private final VulkanImage image;          // R32_SFLOAT, full mip chain, per-level views
    private final long depthSampleView;       // depth-ASPECT-ONLY view of the source depth (for sampling)
    private final int srcWidth, srcHeight;    // source (depth) resolution this pyramid was built for
    private final int width, height, mipCount;
    private final ImageComputePipeline pipeline;

    public HiZPyramid(VulkanImage srcDepth) {
        this.srcWidth = srcDepth.width;
        this.srcHeight = srcDepth.height;
        // mip0 is half the source (standard for Hi-Z); round up to >=1.
        this.width = Math.max(1, srcWidth / 2);
        this.height = Math.max(1, srcHeight / 2);
        this.mipCount = 1 + (int) (Math.log(Math.max(width, height)) / Math.log(2));

        // R32_SFLOAT, STORAGE+SAMPLED, mipCount levels, per-level views, clamp.
        // Do NOT enable linear filtering — in-level NEAREST keeps the conservative max exact.
        this.image = VulkanImage.builder(width, height)
                .setFormat(VK_FORMAT_R32_SFLOAT)
                .setUsage(VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                .setMipLevels(mipCount)
                .setLevelViews(true)
                .setClamp(true)
                .createVulkanImage();

        // Sampling a depth/stencil image requires a depth-ASPECT-ONLY view; the attachment's own
        // view carries DEPTH|STENCIL for D24S8, which is illegal for a combined image sampler.
        this.depthSampleView = VulkanImage.createImageView(
                srcDepth.getId(), srcDepth.format, VK_IMAGE_ASPECT_DEPTH_BIT, 1);

        // One descriptor set per mip, written once. Views are stable across frames, so no per-frame
        // (and crucially no while-bound) descriptor updates are needed.
        this.pipeline = new ImageComputePipeline("hiz", load(),
                new int[]{ VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE },
                16, mipCount); // push: ivec2 dstSize + int isDepth + pad
        for (int mip = 0; mip < mipCount; mip++) {
            long srcView = (mip == 0) ? depthSampleView : image.getLevelView(mip - 1);
            pipeline.updateSampledImage(mip, 0, srcView, image.getSampler());
            pipeline.updateStorageImage(mip, 1, image.getLevelView(mip));
        }
    }

    private static String load() {
        try (InputStream in = HiZPyramid.class.getResourceAsStream("/assets/vulkanmod/shaders/compute/hiz_downsample.comp")) {
            if (in == null) throw new RuntimeException("hiz_downsample.comp missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getMipCount() { return mipCount; }
    public int getWidth()    { return width; }
    public int getHeight()   { return height; }
    public int getSrcWidth()  { return srcWidth; }
    public int getSrcHeight() { return srcHeight; }
    public long getSampledView() { return image.getImageView(); } // full-chain view for sampling
    public long getSampler()     { return image.getSampler(); }

    /**
     * Records the pyramid build into cmd. The source depth must already be in SHADER_READ_ONLY
     * layout (caller transitions it). Each mip binds its own pre-written descriptor set.
     */
    public void build(VkCommandBuffer cmd) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int w = width, h = height;
            for (int mip = 0; mip < mipCount; mip++) {
                // transition this dst mip to GENERAL for the storage write (contents discarded)
                imageBarrier(stack, cmd, image.getId(), mip,
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,
                        0, VK_ACCESS_SHADER_WRITE_BIT,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);

                ByteBuffer push = stack.malloc(16);
                push.putInt(0, w).putInt(4, h).putInt(8, mip == 0 ? 1 : 0).putInt(12, 0);
                pipeline.bindAndDispatch(mip, cmd, push, (w + 7) / 8, (h + 7) / 8, 1);

                // make this mip readable by the next iteration's sample (and by the cull pass next frame)
                imageBarrier(stack, cmd, image.getId(), mip,
                        VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);

                w = Math.max(1, w / 2);
                h = Math.max(1, h / 2);
            }
        }
    }

    private static void imageBarrier(MemoryStack stack, VkCommandBuffer cmd, long img, int mip,
                                     int oldL, int newL, int srcA, int dstA, int srcS, int dstS) {
        VkImageMemoryBarrier.Buffer b = VkImageMemoryBarrier.calloc(1, stack);
        b.get(0).sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldL).newLayout(newL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(img).srcAccessMask(srcA).dstAccessMask(dstA);
        b.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(mip).levelCount(1).baseArrayLayer(0).layerCount(1);
        vkCmdPipelineBarrier(cmd, srcS, dstS, 0, null, null, b);
    }

    public void cleanUp() {
        pipeline.cleanUp();
        vkDestroyImageView(Vulkan.getVkDevice(), depthSampleView, null);
        image.free();
    }
}
