package net.vulkanmod.mixin.render.frapi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ModelBlockRenderer.class)
public abstract class BlockModelRendererM {
    /**
     * @author
     * @reason
     */
    @Overwrite
    public static void renderModel(PoseStack.Pose entry, VertexConsumer vertexConsumer, BlockStateModel model, float red, float green, float blue, int light, int overlay) {
        VulkanModRenderer.INSTANCE.render(entry,vertexConsumer,model,red,green,blue,light,overlay,EmptyBlockAndTintGetter.INSTANCE,BlockPos.ZERO, Blocks.AIR.defaultBlockState());
        //FabricBlockModelRenderer.render(entry, layer -> vertexConsumer, model, red, green, blue, light, overlay, EmptyBlockAndTintGetter.INSTANCE, BlockPos.ZERO, Blocks.AIR.defaultBlockState());
    }
}
