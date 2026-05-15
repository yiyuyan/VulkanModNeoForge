package net.vulkanmod.render.chunk.build.frapi.render;

import net.vulkanmod.render.chunk.build.frapi.helper.fabric.QuadView;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class QuadToPosPipe implements Consumer<QuadView> {
    private final Consumer<Vector3fc> posConsumer;
    private final Vector3f vec;
    public Matrix4fc matrix;

    public QuadToPosPipe(Consumer<Vector3fc> posConsumer, Vector3f vec) {
        this.posConsumer = posConsumer;
        this.vec = vec;
    }

    @Override
    public void accept(QuadView quad) {
        for (int i = 0; i < 4; i++) {
            posConsumer.accept(quad.copyPos(i, vec).mulPosition(matrix));
        }
    }
}
