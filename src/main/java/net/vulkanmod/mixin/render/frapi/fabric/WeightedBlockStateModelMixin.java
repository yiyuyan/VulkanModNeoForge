/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.vulkanmod.mixin.render.frapi.fabric;

import java.util.function.Predicate;

import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.FabricBlockModelPart;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.FabricBlockStateModel;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.QuadEmitter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.WeightedVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(WeightedVariants.class)
public abstract class WeightedBlockStateModelMixin implements BlockStateModel, FabricBlockStateModel {
    @Shadow
    @Final
    private WeightedList<BlockStateModel> list;

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
        ((FabricBlockStateModel) list.getRandomOrThrow(random)).emitQuads(emitter, blockView, pos, state, random, cullTest);
    }

    @Override
    @Nullable
    public Object createGeometryKey(BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random) {
        return list.getRandomOrThrow(random).createGeometryKey(blockView, pos, state, random);
    }

    @Override
    public TextureAtlasSprite particleSprite(BlockAndTintGetter blockView, BlockPos pos, BlockState state) {
        return ((FabricBlockStateModel) list.unwrap().getFirst().value()).particleSprite(blockView, pos, state);
    }
}
