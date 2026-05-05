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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.vulkanmod.mixin.render.frapi.fabric.accessors.MultipartBlockStateModelMultipartBakedModelAccessor;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.FabricBlockStateModel;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces.QuadEmitter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.multipart.MultiPartModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(MultiPartModel.class)
public abstract class MultipartBlockStateModelMixin implements BlockStateModel, FabricBlockStateModel {
    @Shadow
    @Final
    private MultiPartModel.SharedBakedState shared;

    @Shadow
    @Final
    private BlockState blockState;

    @Shadow
    @Nullable
    private List<BlockStateModel> models;

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
        if (models == null) {
            models = shared.selectModels(this.blockState);
        }

        long seed = random.nextLong();

        for (BlockStateModel model : models) {
            random.setSeed(seed);
            ((FabricBlockStateModel)model).emitQuads(emitter, blockView, pos, state, random, cullTest);
        }
    }

    @Override
    @Nullable
    public Object createGeometryKey(BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random) {
        if (models == null) {
            models = shared.selectModels(this.blockState);
        }

        int count = models.size();
        long seed = random.nextLong();

        if (count == 1) {
            random.setSeed(seed);
            return models.getFirst().createGeometryKey(blockView, pos, state, random);
        } else {
            List<Object> subkeys = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                random.setSeed(seed);
                Object subkey = models.get(i).createGeometryKey(blockView, pos, state, random);

                if (subkey == null) {
                    return null;
                }

                subkeys.add(subkey);
            }

            record Key(List<Object> subkeys) {
            }

            return new Key(subkeys);
        }
    }

    @Override
    public TextureAtlasSprite particleSprite(BlockAndTintGetter blockView, BlockPos pos, BlockState state) {
        return ((FabricBlockStateModel)((MultipartBlockStateModelMultipartBakedModelAccessor) (Object) shared).getSelectors().getFirst().model()).particleSprite(blockView, pos, state);
    }
}
