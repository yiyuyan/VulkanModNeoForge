package net.vulkanmod.vulkan.queue;

import org.lwjgl.system.MemoryStack;

public class GraphicsQueue extends Queue {

    public GraphicsQueue(MemoryStack stack, int familyIndex) {
        super(stack, familyIndex);
    }

}
