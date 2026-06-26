package net.vulkanmod.vulkan.memory.buffer;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;

import java.nio.ByteBuffer;

import static org.lwjgl.system.libc.LibCString.nmemcpy;
import static org.lwjgl.vulkan.VK10.*;

public class StagingBuffer extends Buffer {

    public StagingBuffer(long size) {
        super(VK_BUFFER_USAGE_TRANSFER_SRC_BIT, MemoryTypes.HOST_MEM);
        this.createBuffer(size);
    }

    public void copyBuffer(int size, ByteBuffer byteBuffer) {
        this.copyBuffer(size, org.lwjgl.system.MemoryUtil.memAddress(byteBuffer));
    }

    public void copyBuffer(int size, long srcPtr) {
        if (size > this.bufferSize) {
            throw new IllegalArgumentException("Upload size is greater than staging buffer size.");
        }

        if (size > this.bufferSize - this.usedBytes) {
            submitUploads();
        }

        nmemcpy(this.data + this.usedBytes, srcPtr, size);

        this.offset = this.usedBytes;
        this.usedBytes += size;
    }

    public void align(int alignment) {
        int alignedValue = Util.align((int) usedBytes, alignment);

        if(alignedValue > this.bufferSize) {
            resizeBuffer((this.bufferSize) * 2);
        }

        usedBytes = alignedValue;
    }

    private void submitUploads() {
        this.usedBytes = 0;
    }

}
