package net.vulkanmod.mixin.render.frapi.fix;

import net.fabricmc.fabric.api.renderer.v1.material.MaterialView;
import net.vulkanmod.render.chunk.build.frapi.fabric.interfaces.MaterialViewShade;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MaterialView.class)
public interface MaterialViewMixin extends MaterialViewShade {
}
