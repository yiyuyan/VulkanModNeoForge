package net.vulkanmod.mixin.chunk;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChunkRenderDispatcher.class)
public class SectionBufferBuilderPoolM {

    @ModifyVariable(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/renderer/LevelRenderer;Ljava/util/concurrent/Executor;ZLnet/minecraft/client/renderer/ChunkBufferBuilderPack;I)V", at = @At("STORE"), ordinal = 3)
    private static int skipAllocation(int value) {
        return 0;
    }
}