package net.vulkanmod.render.chunk.build.frapi.mesh;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.impl.renderer.VanillaModelEncoder;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

public interface ExtendedQuadEmitter {
    static ExtendedQuadEmitter of(QuadEmitter emitter) {
        return (ExtendedQuadEmitter) emitter;
    }

    default void emitBlockQuads(QuadEmitter emitter, BakedModel model, @Nullable BlockState state, Supplier<RandomSource> randomSupplier, Predicate<@Nullable Direction> cullTest) {
        VanillaModelEncoder.emitBlockQuads(emitter, model, state, randomSupplier, cullTest);
    }

    default void emitItemQuads(QuadEmitter emitter, BakedModel model, @Nullable BlockState state, Supplier<RandomSource> randomSupplier) {
        VanillaModelEncoder.emitItemQuads(emitter, model, state, randomSupplier);
    }
}
