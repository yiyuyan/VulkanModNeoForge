package net.vulkanmod.render.chunk.build.frapi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.vulkanmod.render.chunk.build.frapi.mesh.MeshImpl;

public record MeshItemCommand(PoseStack.Pose positionMatrix, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads, RenderType renderLayer, ItemStackRenderState.FoilType glintType, MeshImpl mesh) {
}
