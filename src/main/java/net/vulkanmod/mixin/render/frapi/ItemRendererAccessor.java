package net.vulkanmod.mixin.render.frapi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemRenderer.class)
public interface ItemRendererAccessor {
	@Invoker("getCompassFoilBuffer")
	static VertexConsumer getCompassFoilBuffer(MultiBufferSource provider, RenderType layer, PoseStack.Pose entry) {
		throw new AssertionError();
	}
}
