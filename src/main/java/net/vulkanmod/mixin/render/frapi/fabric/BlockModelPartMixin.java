package net.vulkanmod.mixin.render.frapi.fabric;

import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.FabricBlockModelPart;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockModelPart.class)
public interface BlockModelPartMixin extends FabricBlockModelPart {
}
