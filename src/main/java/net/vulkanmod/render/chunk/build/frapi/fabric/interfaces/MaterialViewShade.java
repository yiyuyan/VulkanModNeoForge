package net.vulkanmod.render.chunk.build.frapi.fabric.interfaces;

import net.fabricmc.fabric.api.renderer.v1.material.MaterialView;
import net.vulkanmod.render.chunk.build.frapi.fabric.ShadeMode;

public interface MaterialViewShade extends MaterialView {
    default ShadeMode shadeMode() {
        return ShadeMode.ENHANCED;
    }
}
