package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Minimal compute pipeline: N storage buffers + one push-constant range,
 * one statically-allocated descriptor set. Not tied to the graphics
 * Pipeline/PipelineState machinery (which is render-pass oriented).
 */
public class ComputePipeline {
    private static final VkDevice DEVICE = Vulkan.getVkDevice();

    private final int bindingCount;

    private long descriptorSetLayout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipelineLayout;
    private long pipeline;

    public ComputePipeline(String name, String glslSource, int bindingCount, int pushConstantSize) {
        this.bindingCount = bindingCount;

        try (MemoryStack stack = stackPush()) {
            // Descriptor set layout: bindingCount storage buffers, compute stage
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(bindingCount, stack);
            for (int i = 0; i < bindingCount; i++) {
                bindings.get(i)
                        .binding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings);
            LongBuffer pLong = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(DEVICE, layoutInfo, null, pLong) != VK_SUCCESS)
                throw new RuntimeException("Failed to create compute descriptor set layout: " + name);
            this.descriptorSetLayout = pLong.get(0);

            // Descriptor pool with exactly one set
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(bindingCount);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSizes)
                    .maxSets(1);
            if (vkCreateDescriptorPool(DEVICE, poolInfo, null, pLong) != VK_SUCCESS)
                throw new RuntimeException("Failed to create compute descriptor pool: " + name);
            this.descriptorPool = pLong.get(0);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(this.descriptorPool)
                    .pSetLayouts(stack.longs(this.descriptorSetLayout));
            if (vkAllocateDescriptorSets(DEVICE, allocInfo, pLong) != VK_SUCCESS)
                throw new RuntimeException("Failed to allocate compute descriptor set: " + name);
            this.descriptorSet = pLong.get(0);

            // Pipeline layout
            VkPushConstantRange.Buffer pcRange = VkPushConstantRange.calloc(1, stack);
            pcRange.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushConstantSize);
            VkPipelineLayoutCreateInfo plInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(this.descriptorSetLayout))
                    .pPushConstantRanges(pcRange);
            if (vkCreatePipelineLayout(DEVICE, plInfo, null, pLong) != VK_SUCCESS)
                throw new RuntimeException("Failed to create compute pipeline layout: " + name);
            this.pipelineLayout = pLong.get(0);

            // Shader module + pipeline
            SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShader(name, glslSource, SPIRVUtils.ShaderKind.COMPUTE_SHADER);
            long shaderModule = Pipeline.createShaderModule(spirv.bytecode());

            VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .stage(stageInfo)
                    .layout(this.pipelineLayout);
            if (vkCreateComputePipelines(DEVICE, VK_NULL_HANDLE, pipelineInfo, null, pLong) != VK_SUCCESS)
                throw new RuntimeException("Failed to create compute pipeline: " + name);
            this.pipeline = pLong.get(0);

            vkDestroyShaderModule(DEVICE, shaderModule, null);
            spirv.free();
        }
    }

    /** Writes all storage-buffer bindings (0..n-1, whole-buffer ranges) into the static set. */
    public void updateDescriptors(Buffer... buffers) {
        if (buffers.length != this.bindingCount)
            throw new IllegalArgumentException("expected " + bindingCount + " buffers, got " + buffers.length);

        try (MemoryStack stack = stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(buffers.length, stack);
            for (int i = 0; i < buffers.length; i++) {
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(buffers[i].getId())
                        .offset(0)
                        .range(VK_WHOLE_SIZE);
                writes.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(this.descriptorSet)
                        .dstBinding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .pBufferInfo(bufferInfo);
            }
            vkUpdateDescriptorSets(DEVICE, writes, null);
        }
    }

    public void bindAndDispatch(VkCommandBuffer commandBuffer, ByteBuffer pushConstants, int groupCountX) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
        try (MemoryStack stack = stackPush()) {
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout,
                    0, stack.longs(this.descriptorSet), null);
        }
        vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
        vkCmdDispatch(commandBuffer, groupCountX, 1, 1);
    }

    public void cleanUp() {
        vkDestroyPipeline(DEVICE, this.pipeline, null);
        vkDestroyPipelineLayout(DEVICE, this.pipelineLayout, null);
        vkDestroyDescriptorPool(DEVICE, this.descriptorPool, null);
        vkDestroyDescriptorSetLayout(DEVICE, this.descriptorSetLayout, null);
    }
}
