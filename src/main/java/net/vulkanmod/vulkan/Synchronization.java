package net.vulkanmod.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.render.profiling.Profiler;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization(ALLOCATION_SIZE);

    private final LongBuffer fences;
    private int idx = 0;

    private ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    Synchronization(int allocSize) {
        this.fences = MemoryUtil.memAllocLong(allocSize);
    }

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.addFence(commandBuffer.getFence());
        this.commandBuffers.add(commandBuffer);
    }

    public synchronized void addFence(long fence) {
        if (idx == ALLOCATION_SIZE)
            waitFences();

        fences.put(idx, fence);
        idx++;
    }

    public synchronized void waitFences() {
        if (idx == 0)
            return;

        VkDevice device = Vulkan.getVkDevice();

        fences.limit(idx);

        if (RenderSystem.isOnRenderThread()) {
            Profiler p = Profiler.getMainProfiler();
            p.push("CPU_fence_wait");
            vkWaitForFences(device, fences, true, VUtil.UINT64_MAX);
            p.pop();
        } else {
            vkWaitForFences(device, fences, true, VUtil.UINT64_MAX);
        }

        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();

        fences.limit(ALLOCATION_SIZE);
        idx = 0;
    }

    public static void waitFence(long fence) {
        VkDevice device = Vulkan.getVkDevice();

        vkWaitForFences(device, fence, true, VUtil.UINT64_MAX);
    }

    public static boolean checkFenceStatus(long fence) {
        VkDevice device = Vulkan.getVkDevice();
        return vkGetFenceStatus(device, fence) == VK_SUCCESS;
    }

    public synchronized void pollFences() {
        if (idx == 0)
            return;

        VkDevice device = Vulkan.getVkDevice();

        // Single status snapshot per fence: drives BOTH compactions (no TOCTOU window)
        LongOpenHashSet signaled = new LongOpenHashSet();
        int write = 0;
        for (int i = 0; i < idx; i++) {
            long fence = fences.get(i);
            if (vkGetFenceStatus(device, fence) == VK_SUCCESS) {
                signaled.add(fence);
            } else {
                fences.put(write++, fence);
            }
        }
        idx = write;

        var iterator = commandBuffers.iterator();
        while (iterator.hasNext()) {
            CommandPool.CommandBuffer cb = iterator.next();
            if (signaled.contains(cb.getFence())) {
                cb.reset();
                iterator.remove();
            }
        }
    }

}
