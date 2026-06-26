package net.vulkanmod.vulkan.memory.buffer;

import net.vulkanmod.vulkan.memory.MemoryType;

public class StorageBuffer extends Buffer {

    public StorageBuffer(long size, int extraUsage, MemoryType type) {
        super(org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | extraUsage, type);
        this.createBuffer(size);
    }
}
