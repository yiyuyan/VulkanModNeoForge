package net.vulkanmod.mixin.render.frapi.fix;

import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.vulkanmod.render.chunk.build.frapi.fabric.interfaces.MaterialFinderShade;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MaterialFinder.class)
public interface MaterialFinderMixin extends MaterialFinderShade {
}
