package net.vulkanmod.vulkan.shader.layout;

import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.function.Supplier;

public class Vec1i extends Uniform {
    private Supplier<Integer> intSupplier;

    Vec1i(Info info) {
        super(info);
        this.intSupplier = info.intSupplier;
    }

    @Override
    public void setSupplier(Supplier<MappedBuffer> supplier) {
        this.intSupplier = () -> {
            MappedBuffer buffer = supplier.get();
            return MemoryUtil.memGetInt(buffer.ptr);
        };
        this.values = null;
    }

    @Override
    void update(long ptr) {
        int value = intSupplier.get();
        MemoryUtil.memPutInt(ptr + this.offset, value);
    }
}