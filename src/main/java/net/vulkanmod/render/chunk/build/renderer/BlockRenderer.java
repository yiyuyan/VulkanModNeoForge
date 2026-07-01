package net.vulkanmod.render.chunk.build.renderer;

import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.frapi.fabric.ShadeMode;
import net.vulkanmod.render.chunk.build.frapi.fabric.interfaces.MaterialViewShade;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.frapi.render.AbstractBlockRenderContext;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.model.quad.QuadUtils;
import net.vulkanmod.render.model.quad.ModelQuadView;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.vertex.format.I32_SNorm;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Vector3f;

public class BlockRenderer extends AbstractBlockRenderContext {
    private Vector3f pos;
    private BuilderResources resources;
    private TerrainBuilder terrainBuilder;
    private TerrainRenderType renderType;

    public void setResources(BuilderResources resources) {
        this.resources = resources;
    }

    public BlockRenderer(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
        super();
        this.setupLightPipelines(flatLightPipeline, smoothLightPipeline);
        this.random = new SingleThreadedRandomSource(42L);
    }

    public void renderBlock(BlockState blockState, BlockPos blockPos, Vector3f pos) {
        this.pos = pos;
        this.blockPos = blockPos;
        this.blockState = blockState;
        this.seed = blockState.getSeed(blockPos);

        TerrainRenderType renderType = TerrainRenderType.get(ItemBlockRenderTypes.getChunkRenderType(blockState));
        renderType = TerrainRenderType.getRemapped(renderType);
        this.renderType = renderType;
        this.terrainBuilder = this.resources.builderPack.builder(renderType);
        this.terrainBuilder.setBlockAttributes(blockState);

        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);
        BlockAndTintGetter renderRegion = this.renderRegion;
        Vec3 offset = blockState.getOffset(renderRegion, blockPos);
        pos.add((float) offset.x, (float) offset.y, (float) offset.z);

        this.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
        ((FabricBakedModel)model).emitBlockQuads(renderRegion, blockState, blockPos, this.randomSupplier, this);
    }

    protected void endRenderQuad(MutableQuadViewImpl quad) {
        final RenderMaterial mat = quad.material();
        final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();
        final TriState aoMode = mat.ambientOcclusion();
        final boolean ao = this.useAO && (aoMode == TriState.TRUE || (aoMode == TriState.DEFAULT && this.defaultAO));
        final boolean emissive = mat.emissive();
        final boolean vanillaShade = ((MaterialViewShade)mat).shadeMode() == ShadeMode.VANILLA;

        TerrainBuilder terrainBuilder = getBufferBuilder(mat.blendMode());
        LightPipeline lightPipeline = ao ? this.smoothLightPipeline : this.flatLightPipeline;

        colorizeQuad(quad, colorIndex);
        shadeQuad(quad, lightPipeline, emissive, vanillaShade);
        bufferQuad(terrainBuilder, this.pos, quad, this.quadLightData);
    }

    private TerrainBuilder getBufferBuilder(BlendMode blendMode) {
        if (blendMode == BlendMode.DEFAULT) {
            return this.terrainBuilder;
        } else {
            TerrainRenderType type = TerrainRenderType.get(blendMode.blockRenderLayer);
            type = TerrainRenderType.getRemapped(type);
            TerrainBuilder builder = this.resources.builderPack.builder(type);
            builder.setBlockAttributes(this.blockState);
            return builder;
        }
    }

    public void bufferQuad(TerrainBuilder terrainBuilder, Vector3f pos, ModelQuadView quad, QuadLightData quadLightData) {
        QuadFacing quadFacing = quad.getQuadFacing();

        if (renderType == TerrainRenderType.TRANSLUCENT || !Initializer.CONFIG.backFaceCulling) {
            quadFacing = QuadFacing.UNDEFINED;
        }

        TerrainBufferBuilder bufferBuilder = terrainBuilder.getBufferBuilder(quadFacing.ordinal());

        Vec3i normal = quad.getFacingDirection().getNormal();
        int packedNormal = I32_SNorm.packNormal(normal.getX(), normal.getY(), normal.getZ());

        float[] brightnessArr = quadLightData.br;
        int[] lights = quadLightData.lm;
        int idx = QuadUtils.getIterationStartIdx(brightnessArr, lights);

        bufferBuilder.ensureCapacity();

        for (byte i = 0; i < 4; ++i) {
            final float x = pos.x() + quad.getX(idx);
            final float y = pos.y() + quad.getY(idx);
            final float z = pos.z() + quad.getZ(idx);

            final int quadColor = quad.getColor(idx);
            int color = ColorUtil.ARGB.toRGBA(quadColor);

            final int light = lights[idx];
            final float u = quad.getU(idx);
            final float v = quad.getV(idx);

            bufferBuilder.vertex(x, y, z, color, u, v, light, packedNormal);
            idx = (idx + 1) & 0b11;
        }
    }
}