//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package net.vulkanmod.render.chunk.build.frapi.helper.fabric;

import java.util.List;
import net.minecraft.client.resources.model.QuadCollection;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.Mesh;


public final class MeshBakedGeometry extends QuadCollection {
    private final Mesh mesh;

    public MeshBakedGeometry(Mesh mesh) {
        super(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        this.mesh = mesh;
    }

    public Mesh getMesh() {
        return this.mesh;
    }
}
