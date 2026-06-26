package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Compute pipeline whose descriptor set may mix storage buffers and images.
 * Binding i's type is given by descriptorTypes[i] (VK_DESCRIPTOR_TYPE_*).
 *
 * Allocates {@code numSets} identical-layout descriptor sets. Updating a descriptor set while it
 * is bound to a recording command buffer is illegal (it invalidates the whole command buffer), so
 * callers that vary bindings across dispatches within one command buffer must use a distinct,
 * pre-written set per dispatch (e.g. one per Hi-Z mip).
 */
public class ImageComputePipeline {
    private static final VkDevice DEVICE = Vulkan.getVkDevice();

    private final int[] types;
    private final long[] descriptorSets;
    private long descriptorSetLayout, descriptorPool, pipelineLayout, pipeline;

    public ImageComputePipeline(String name, String glslSource, int[] descriptorTypes, int pushConstantSize) {
        this(name, glslSource, descriptorTypes, pushConstantSize, 1);
    }

    public ImageComputePipeline(String name, String glslSource, int[] descriptorTypes, int pushConstantSize, int numSets) {
        this.types = descriptorTypes;
        this.descriptorSets = new long[numSets];
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(types.length, stack);
            for (int i = 0; i < types.length; i++) {
                bindings.get(i).binding(i).descriptorType(types[i]).descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO).pBindings(bindings);
            LongBuffer p = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(DEVICE, layoutInfo, null, p) != VK_SUCCESS)
                throw new RuntimeException("descriptor set layout: " + name);
            this.descriptorSetLayout = p.get(0);

            // pool sized per distinct type * numSets
            java.util.Map<Integer,Integer> counts = new java.util.HashMap<>();
            for (int t : types) counts.merge(t, 1, Integer::sum);
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(counts.size(), stack);
            int idx = 0;
            for (var e : counts.entrySet()) poolSizes.get(idx++).type(e.getKey()).descriptorCount(e.getValue() * numSets);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO).pPoolSizes(poolSizes).maxSets(numSets);
            if (vkCreateDescriptorPool(DEVICE, poolInfo, null, p) != VK_SUCCESS)
                throw new RuntimeException("descriptor pool: " + name);
            this.descriptorPool = p.get(0);

            LongBuffer layouts = stack.mallocLong(numSets);
            for (int i = 0; i < numSets; i++) layouts.put(i, this.descriptorSetLayout);
            VkDescriptorSetAllocateInfo alloc = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool).pSetLayouts(layouts);
            LongBuffer pSets = stack.mallocLong(numSets);
            if (vkAllocateDescriptorSets(DEVICE, alloc, pSets) != VK_SUCCESS)
                throw new RuntimeException("descriptor sets: " + name);
            for (int i = 0; i < numSets; i++) this.descriptorSets[i] = pSets.get(i);

            // Pipeline layout (compute push-constant range) — same as ComputePipeline
            VkPushConstantRange.Buffer pcRange = VkPushConstantRange.calloc(1, stack);
            pcRange.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushConstantSize);
            VkPipelineLayoutCreateInfo plInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(this.descriptorSetLayout)).pPushConstantRanges(pcRange);
            if (vkCreatePipelineLayout(DEVICE, plInfo, null, p) != VK_SUCCESS)
                throw new RuntimeException("pipeline layout: " + name);
            this.pipelineLayout = p.get(0);

            // Shader module + compute pipeline (SPIRVUtils + Pipeline are in this same package)
            SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShader(name, glslSource, SPIRVUtils.ShaderKind.COMPUTE_SHADER);
            long shaderModule = Pipeline.createShaderModule(spirv.bytecode());
            VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT).module(shaderModule).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO).stage(stageInfo).layout(this.pipelineLayout);
            if (vkCreateComputePipelines(DEVICE, VK_NULL_HANDLE, pipelineInfo, null, p) != VK_SUCCESS)
                throw new RuntimeException("compute pipeline: " + name);
            this.pipeline = p.get(0);
            vkDestroyShaderModule(DEVICE, shaderModule, null);
            spirv.free();
        }
    }

    public int numSets() { return descriptorSets.length; }

    public void updateBuffer(int set, int binding, Buffer buffer) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorBufferInfo.Buffer bi = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer.getId()).offset(0).range(VK_WHOLE_SIZE);
            VkWriteDescriptorSet.Buffer w = VkWriteDescriptorSet.calloc(1, stack);
            w.get(0).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(descriptorSets[set])
                    .dstBinding(binding).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).pBufferInfo(bi);
            vkUpdateDescriptorSets(DEVICE, w, null);
        }
    }

    public void updateSampledImage(int set, int binding, long imageView, long sampler) {
        writeImage(set, binding, imageView, sampler, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    public void updateStorageImage(int set, int binding, long imageView) {
        writeImage(set, binding, imageView, VK_NULL_HANDLE, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                VK_IMAGE_LAYOUT_GENERAL);
    }

    private void writeImage(int set, int binding, long imageView, long sampler, int type, int layout) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer ii = VkDescriptorImageInfo.calloc(1, stack)
                    .imageView(imageView).imageLayout(layout);
            if (sampler != VK_NULL_HANDLE) ii.sampler(sampler);
            VkWriteDescriptorSet.Buffer w = VkWriteDescriptorSet.calloc(1, stack);
            w.get(0).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(descriptorSets[set])
                    .dstBinding(binding).descriptorCount(1)
                    .descriptorType(type).pImageInfo(ii);
            vkUpdateDescriptorSets(DEVICE, w, null);
        }
    }

    public void bindAndDispatch(int set, VkCommandBuffer cmd, ByteBuffer push, int gx, int gy, int gz) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0,
                new long[]{descriptorSets[set]}, null);
        if (push != null) vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
        vkCmdDispatch(cmd, gx, gy, gz);
    }

    public void cleanUp() {
        vkDestroyPipeline(DEVICE, pipeline, null);
        vkDestroyPipelineLayout(DEVICE, pipelineLayout, null);
        vkDestroyDescriptorPool(DEVICE, descriptorPool, null); // frees all sets allocated from it
        vkDestroyDescriptorSetLayout(DEVICE, descriptorSetLayout, null);
    }
}
