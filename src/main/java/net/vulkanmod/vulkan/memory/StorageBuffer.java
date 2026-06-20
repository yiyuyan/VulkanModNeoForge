package net.vulkanmod.vulkan.memory;

public class StorageBuffer extends Buffer {

    public StorageBuffer(int size, int extraUsage, MemoryType type) {
        super(org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | extraUsage, type);
        this.createBuffer(size);
    }
}
