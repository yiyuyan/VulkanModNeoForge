package net.vulkanmod.vulkan.memory.buffer;

import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryType;

public abstract class Buffer {
    public final MemoryType type;
    public final int usage;

    protected long id;
    protected long allocation;

    protected long bufferSize;
    protected long usedBytes;
    protected long offset;

    protected long data;

    protected Buffer(int usage, MemoryType type) {
        this.usage = usage;
        this.type = type;
    }

    protected void createBuffer(long bufferSize) {
        this.type.createBuffer(this, bufferSize);

        if(this.type.mappable()) {
            this.data = MemoryManager.getInstance().Map(this.allocation).get(0);
        }
    }

    public void freeBuffer() {
        MemoryManager.getInstance().addToFreeable(this);
    }

    public void scheduleFree() {
        MemoryManager.getInstance().addToFreeable(this);
    }

    public void reset() { usedBytes = 0; }

    public long getAllocation() { return allocation; }

    public long getUsedBytes() { return usedBytes; }

    public long getOffset() { return offset; }

    public long getId() { return id; }

    public long getBufferSize() { return bufferSize; }

    public void setBufferSize(long size) { this.bufferSize = size; }

    public void setId(long id) { this.id = id; }

    public void setAllocation(long allocation) {this.allocation = allocation; }

    /** Mapped host pointer — valid for host-mappable memory types only. */
    public long getDataPtr() {
        if (this.data == 0)
            throw new IllegalStateException("buffer is not host-mappable (type=" + this.type.getType() + ")");
        return this.data;
    }

    public BufferInfo getBufferInfo() { return new BufferInfo(this.id, this.allocation, this.bufferSize, this.type.getType()); }

    public record BufferInfo(long id, long allocation, long bufferSize, MemoryType.Type type) {

    }
}
