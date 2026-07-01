package net.vulkanmod.render.chunk.build.frapi.fabric.interfaces;

import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.vulkanmod.render.chunk.build.frapi.fabric.ShadeMode;

public interface MaterialFinderShade extends MaterialFinder {
    /**
     * A hint to the renderer about how the quad is intended to be shaded, for example through ambient occlusion and
     * diffuse shading. The renderer is free to ignore this hint.
     *
     * <p>The default value is {@link ShadeMode#ENHANCED}.
     *
     * <p>This property is respected only in block contexts. It will not have an effect in other contexts.
     *
     * @see ShadeMode
     *
     * @apiNote The default implementation will be removed in the next breaking release.
     */
    default MaterialFinder shadeMode(ShadeMode mode) {
        return this;
    }
}
