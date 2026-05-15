package net.vulkanmod.mixin.render.frapi.fabric;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.MeshBakedGeometry;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.FabricBlockModelPart;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.QuadEmitter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Predicate;

@Mixin(SimpleModelWrapper.class)
public class GeometryBakedModelMixin implements BlockModelPart, FabricBlockModelPart {
    @Shadow
    @Final
    private QuadCollection quads;
    @Shadow
    @Final
    private boolean useAmbientOcclusion;

    GeometryBakedModelMixin() {
    }

    public void emitQuads(QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
        QuadCollection var4 = this.quads;
        if (var4 instanceof MeshBakedGeometry meshBakedGeometry) {
            if (this.useAmbientOcclusion) {
                meshBakedGeometry.getMesh().outputTo(emitter);
            } else {
                emitter.pushTransform((quad) -> {
                    if (quad.ambientOcclusion() == TriState.DEFAULT) {
                        quad.ambientOcclusion(TriState.FALSE);
                    }

                    return true;
                });
                meshBakedGeometry.getMesh().outputTo(emitter);
                emitter.popTransform();
            }
        } else {
            FabricBlockModelPart.super.emitQuads(emitter,cullTest);
        }

    }

    @Override
    public List<BakedQuad> getQuads(@Nullable Direction arg) {
        return List.of();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public TextureAtlasSprite particleIcon() {
        return null;
    }
}
