package net.vulkanmod.mixin.render.frapi;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.chunk.build.frapi.mesh.ExtendedQuadEmitter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;
import java.util.function.Supplier;

@Mixin(BakedModel.class)
public interface BakedModelM extends FabricBakedModel {

    @Override
    default void emitBlockQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, Predicate<@Nullable Direction> cullTest) {
        ExtendedQuadEmitter.of(emitter).emitBlockQuads(emitter, (BakedModel) this, state, randomSupplier, cullTest);
    }

    @Override
    default void emitItemQuads(QuadEmitter emitter, Supplier<RandomSource> randomSupplier) {
        ExtendedQuadEmitter.of(emitter).emitItemQuads(emitter, (BakedModel) this, null, randomSupplier);
    }
}
