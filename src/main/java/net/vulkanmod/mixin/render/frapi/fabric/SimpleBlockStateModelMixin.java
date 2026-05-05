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
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(SingleVariant.class)
public abstract class SimpleBlockStateModelMixin implements BlockStateModel, FabricBlockStateModel {
    @Shadow
    @Final
    private BlockModelPart model;

    // Not strictly necessary for FRAPI compatibility like other mixins, but saves a list allocation and some other
    // operations over this method's default impl.
    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
        ((FabricBlockModelPart)model).emitQuads(emitter, cullTest);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random) {
        return this;
    }
}
