package net.vulkanmod.vulkan.shader.layout;

import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.function.Supplier;

public class Vec1f extends Uniform {
    private Supplier<Float> floatSupplier;

    Vec1f(Info info) {
        super(info);
        this.floatSupplier = info.floatSupplier;
    }

    @Override
    public void setSupplier(Supplier<MappedBuffer> supplier) {
        this.floatSupplier = () -> {
            MappedBuffer buffer = supplier.get();
            return MemoryUtil.memGetFloat(buffer.ptr);
        };
        this.values = null;
    }

    @Override
    void update(long ptr) {
        float value = floatSupplier.get();
        MemoryUtil.memPutFloat(ptr + this.offset, value);
    }
}