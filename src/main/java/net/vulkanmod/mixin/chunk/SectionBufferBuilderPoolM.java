package net.vulkanmod.mixin.chunk;

import net.minecraft.client.renderer.SectionBufferBuilderPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SectionBufferBuilderPool.class)
public class SectionBufferBuilderPoolM {

    @ModifyVariable(method = "allocate", at = @At("STORE"), name = "j")
    private static int skipAllocation(int j) {
        return 0;
    }
}