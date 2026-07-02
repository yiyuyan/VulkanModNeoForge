package net.vulkanmod.mixin.render.frapi;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.vulkanmod.render.chunk.build.frapi.mesh.ExtendedQuadEmitter;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(QuadEmitter.class)
public interface QuadEmitterM extends ExtendedQuadEmitter {
}
