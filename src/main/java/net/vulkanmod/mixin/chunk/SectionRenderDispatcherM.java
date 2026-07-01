package net.vulkanmod.mixin.chunk;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkRenderDispatcher.class)
public class SectionRenderDispatcherM {

	@Redirect(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/renderer/LevelRenderer;Ljava/util/concurrent/Executor;ZLnet/minecraft/client/renderer/ChunkBufferBuilderPack;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/ProcessorMailbox;tell(Ljava/lang/Object;)V"))
	private void redirectTask(ProcessorMailbox instance, Object object) {}
}
