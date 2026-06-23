package net.vulkanmod.render.chunk.build.renderer;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.chunk.util.Util;
import net.vulkanmod.render.model.quad.ModelQuad;
import net.vulkanmod.render.model.quad.ModelQuadFlags;
import net.vulkanmod.render.model.quad.QuadUtils;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.vertex.format.I32_SNorm;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Vector3f;

public class FluidRenderer {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;

    private final BlockPos.MutableBlockPos mBlockPos = new BlockPos.MutableBlockPos();
    private final ModelQuad modelQuad = new ModelQuad();

    BuilderResources resources;
    private final LightPipeline smoothLightPipeline;
    private final LightPipeline flatLightPipeline;
    private final int[] quadColors = new int[4];

    public FluidRenderer(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
        this.smoothLightPipeline = smoothLightPipeline;
        this.flatLightPipeline = flatLightPipeline;
    }

    public void setResources(BuilderResources resources) { this.resources = resources; }

    public void renderLiquid(BlockState blockState, FluidState fluidState, BlockPos blockPos) {
        TerrainRenderType renderType = TerrainRenderType.get(ItemBlockRenderTypes.getRenderLayer(fluidState));
        renderType = TerrainRenderType.getRemapped(renderType);
        TerrainBufferBuilder bufferBuilder = this.resources.builderPack.builder(renderType)
                .getBufferBuilder(QuadFacing.UNDEFINED.ordinal());
        tessellate(blockState, fluidState, blockPos, bufferBuilder);
    }


    public void tessellate(BlockState blockState, FluidState fluidState, BlockPos blockPos, TerrainBufferBuilder bufferBuilder) {
        BlockAndTintGetter region = this.resources.getRegion();
        final FluidRenderHandler handler = getFluidRenderHandler(fluidState);
        int color = handler.getFluidColor(region, blockPos, fluidState);
        TextureAtlasSprite[] sprites = handler.getFluidSprites(region, blockPos, fluidState);
        float r = ColorUtil.ARGB.unpackR(color);
        float g = ColorUtil.ARGB.unpackG(color);
        float b = ColorUtil.ARGB.unpackB(color);

        final int posX = blockPos.getX();
        final int posY = blockPos.getY();
        final int posZ = blockPos.getZ();

        boolean useAO = blockState.getLightEmission() == 0 && Minecraft.useAmbientOcclusion();
        LightPipeline lightPipeline = useAO ? this.smoothLightPipeline : this.flatLightPipeline;

        BlockState downState = getAdjBlockState(region, posX, posY, posZ, Direction.DOWN);
        BlockState upState = getAdjBlockState(region, posX, posY, posZ, Direction.UP);
        BlockState northState = getAdjBlockState(region, posX, posY, posZ, Direction.NORTH);
        BlockState southState = getAdjBlockState(region, posX, posY, posZ, Direction.SOUTH);
        BlockState westState = getAdjBlockState(region, posX, posY, posZ, Direction.WEST);
        BlockState eastState = getAdjBlockState(region, posX, posY, posZ, Direction.EAST);

        boolean rUf = shouldRenderFace(region, blockPos, fluidState, blockState, Direction.UP, upState);
        boolean rDf = shouldRenderFace(region, blockPos, fluidState, blockState, Direction.DOWN, downState)
                && !isFaceOccludedByState(region, MAX_FLUID_HEIGHT, Direction.DOWN, blockPos, downState);
        boolean rNf = shouldRenderFace(region, blockPos, fluidState, blockState, Direction.NORTH, northState);
        boolean rSf = shouldRenderFace(region, blockPos, fluidState, blockState, Direction.SOUTH, southState);
        boolean rWf = shouldRenderFace(region, blockPos, fluidState, blockState, Direction.WEST, westState);
        boolean rEf = shouldRenderFace(region, blockPos, fluidState, blockState, Direction.EAST, eastState);
        if (!(rUf || rDf || rEf || rWf || rNf || rSf)) return;

        float brightnessUp = region.getShade(Direction.UP, true);
        Fluid fluid = fluidState.getType();
        float height = getHeight(region, fluid, blockPos, blockState);
        float neHeight, nwHeight, seHeight, swHeight;
        if (height >= 1.0F) {
            neHeight = nwHeight = seHeight = swHeight = 1.0F;
        } else {
            float s = getHeight(region, fluid, mBlockPos.set(blockPos).offset(Direction.NORTH.getNormal()), northState);
            float t = getHeight(region, fluid, mBlockPos.set(blockPos).offset(Direction.SOUTH.getNormal()), southState);
            float u = getHeight(region, fluid, mBlockPos.set(blockPos).offset(Direction.EAST.getNormal()), eastState);
            float v = getHeight(region, fluid, mBlockPos.set(blockPos).offset(Direction.WEST.getNormal()), westState);
            neHeight = calculateAverageHeight(region, fluid, height, s, u, mBlockPos.set(blockPos).offset(Direction.NORTH.getNormal()).offset(Direction.EAST.getNormal()));
            nwHeight = calculateAverageHeight(region, fluid, height, s, v, mBlockPos.set(blockPos).offset(Direction.NORTH.getNormal()).offset(Direction.WEST.getNormal()));
            seHeight = calculateAverageHeight(region, fluid, height, t, u, mBlockPos.set(blockPos).offset(Direction.SOUTH.getNormal()).offset(Direction.EAST.getNormal()));
            swHeight = calculateAverageHeight(region, fluid, height, t, v, mBlockPos.set(blockPos).offset(Direction.SOUTH.getNormal()).offset(Direction.WEST.getNormal()));
        }

        float x0 = (posX & 15), y0 = (posY & 15), z0 = (posZ & 15);
        float y = rDf ? 0.001F : 0.0F;

        modelQuad.setFlags(0);

        if (rUf && !isFaceOccludedByState(region, Math.min(Math.min(nwHeight, swHeight), Math.min(seHeight, neHeight)), Direction.UP, blockPos, upState)) {
            float u0, u1, u2, u3, v0, v1, v2, v3;
            nwHeight -= 0.001f; swHeight -= 0.001f; seHeight -= 0.001f; neHeight -= 0.001f;
            Vec3 flow = fluidState.getFlow(region, blockPos);
            TextureAtlasSprite sprite;
            if (flow.x == 0.0 && flow.z == 0.0) {
                sprite = sprites[0];
                u0 = sprite.getU(0.0f); v0 = sprite.getV(0.0f);
                u1 = u0; v1 = sprite.getV(1.0f);
                u2 = sprite.getU(1.0f); v2 = v1;
                u3 = u2; v3 = v0;
            } else {
                sprite = sprites[1];
                float ah = (float) Mth.atan2(flow.z, flow.x) - 1.5707964F;
                float ai = Mth.sin(ah) * 0.25f;
                float aj = Mth.cos(ah) * 0.25f;
                u0 = sprite.getU(0.5f + (-aj - ai)); v0 = sprite.getV(0.5f - aj + ai);
                u1 = sprite.getU(0.5f - aj + ai); v1 = sprite.getV(0.5f + aj + ai);
                u2 = sprite.getU(0.5f + aj + ai); v2 = sprite.getV(0.5f + (aj - ai));
                u3 = sprite.getU(0.5f + (aj - ai)); v3 = sprite.getV(0.5f + (-aj - ai));
            }
            float uA = (u0+u1+u2+u3)/4f, vA = (v0+v1+v2+v3)/4f;
            float ai = sprites[0].uvShrinkRatio();
            u0 = Mth.lerp(ai, u0, uA); u1 = Mth.lerp(ai, u1, uA); u2 = Mth.lerp(ai, u2, uA); u3 = Mth.lerp(ai, u3, uA);
            v0 = Mth.lerp(ai, v0, vA); v1 = Mth.lerp(ai, v1, vA); v2 = Mth.lerp(ai, v2, vA); v3 = Mth.lerp(ai, v3, vA);

            setVertex(modelQuad, 0, 0.0f, nwHeight, 0.0f, u0, v0);
            setVertex(modelQuad, 1, 0.0f, swHeight, 1.0f, u1, v1);
            setVertex(modelQuad, 2, 1.0f, seHeight, 1.0f, u2, v2);
            setVertex(modelQuad, 3, 1.0f, neHeight, 0.0f, u3, v3);

            updateQuad(modelQuad, blockPos, lightPipeline, Direction.UP);
            updateColor(r, g, b, brightnessUp);
            int normal = calculateNormal(modelQuad);
            putQuad(modelQuad, bufferBuilder, x0, y0, z0, false, normal);
            if (fluidState.shouldRenderBackwardUpFace(region, blockPos.above())) {
                putQuad(modelQuad, bufferBuilder, x0, y0, z0, true, normal);
            }
        }

        if (rDf) {
            float u0 = sprites[0].getU0(), u1 = sprites[0].getU1();
            float v0 = sprites[0].getV0(), v1 = sprites[0].getV1();
            float brightness = region.getShade(Direction.DOWN, true);
            setVertex(modelQuad, 0, 0.0f, y, 1.0f, u0, v1);
            setVertex(modelQuad, 1, 0.0f, y, 0.0f, u0, v0);
            setVertex(modelQuad, 2, 1.0f, y, 0.0f, u1, v0);
            setVertex(modelQuad, 3, 1.0f, y, 1.0f, u1, v1);
            updateQuad(modelQuad, blockPos, lightPipeline, Direction.DOWN);
            updateColor(r, g, b, brightness);
            int normal = calculateNormal(modelQuad);
            putQuad(modelQuad, bufferBuilder, x0, y0, z0, false, normal);
        }

        modelQuad.setFlags(ModelQuadFlags.IS_PARALLEL | ModelQuadFlags.IS_ALIGNED);

        for (Direction dir : Util.XZ_DIRECTIONS) {
            float h1, h2, x1, z1, x2, z2;
            final float E = 0.001f, E2 = 0.999f;
            BlockState adjState;
            switch (dir) {
                case NORTH: if (!rNf) continue; h1=nwHeight; h2=neHeight; x1=0; x2=1; z1=E; z2=E; adjState=northState; break;
                case SOUTH: if (!rSf) continue; h1=seHeight; h2=swHeight; x1=1; x2=0; z1=E2; z2=E2; adjState=southState; break;
                case WEST: if (!rWf) continue; h1=swHeight; h2=nwHeight; x1=E; x2=E; z1=1; z2=0; adjState=westState; break;
                case EAST: if (!rEf) continue; h1=neHeight; h2=seHeight; x1=E2; x2=E2; z1=0; z2=1; adjState=eastState; break;
                default: continue;
            }
            if (isFaceOccludedByState(region, Math.max(h1, h2), dir, blockPos, adjState)) continue;

            TextureAtlasSprite sprite = sprites[1];
            boolean isOverlay = false;
            if (sprites.length > 2 && FluidRenderHandlerRegistry.INSTANCE.isBlockTransparent(adjState.getBlock())) {
                sprite = sprites[2]; isOverlay = true;
            }

            float u0 = sprite.getU(0f), u1 = sprite.getU(0.5f);
            float v0 = sprite.getV((1f - h1) * 0.5f), v1 = sprite.getV((1f - h2) * 0.5f), v2 = sprite.getV(0.5f);
            float brightness = region.getShade(dir, true);

            setVertex(modelQuad, 0, x2, h2, z2, u1, v1);
            setVertex(modelQuad, 1, x2, y, z2, u1, v2);
            setVertex(modelQuad, 2, x1, y, z1, u0, v2);
            setVertex(modelQuad, 3, x1, h1, z1, u0, v0);

            updateQuad(modelQuad, blockPos, lightPipeline, dir);
            updateColor(r, g, b, brightness);
            int normal = calculateNormal(modelQuad);
            putQuad(modelQuad, bufferBuilder, x0, y0, z0, false, normal);
            if (!isOverlay) putQuad(modelQuad, bufferBuilder, x0, y0, z0, true, normal);
        }
    }

    private int calculateNormal(ModelQuad quad) {
        Vector3f a = new Vector3f(quad.getX(1)-quad.getX(0), quad.getY(1)-quad.getY(0), quad.getZ(1)-quad.getZ(0));
        Vector3f b = new Vector3f(quad.getX(2)-quad.getX(0), quad.getY(2)-quad.getY(0), quad.getZ(2)-quad.getZ(0));
        Vector3f n = a.cross(b).normalize();
        return I32_SNorm.packNormal(n.x(), n.y(), n.z());
    }

    private void putQuad(ModelQuad quad, TerrainBufferBuilder bufferBuilder, float xOff, float yOff, float zOff, boolean flip, int normal) {
        QuadLightData qld = resources.quadLightData;
        int k = QuadUtils.getIterationStartIdx(qld.br);
        bufferBuilder.ensureCapacity();
        for (int j = 0; j < 4; j++) {
            int i = k;
            float x = xOff + quad.getX(i);
            float y = yOff + quad.getY(i);
            float z = zOff + quad.getZ(i);
            bufferBuilder.vertex(x, y, z, quadColors[i], quad.getU(i), quad.getV(i), qld.lm[i], normal);
            k += (flip ? -1 : 1);
            k &= 0b11;
        }
    }

    private boolean isFaceOccludedByState(BlockGetter blockGetter, float h, Direction direction, BlockPos blockPos, BlockState blockState) {
        mBlockPos.set(blockPos).offset(Direction.DOWN.getNormal());
        if (blockState.canOcclude()) {
            VoxelShape occlusionShape = blockState.getOcclusionShape(blockGetter, mBlockPos);
            if (occlusionShape == Shapes.block()) return direction != Direction.UP;
            if (occlusionShape.isEmpty()) return false;
            VoxelShape voxelShape = Shapes.box(0.0, 0.0, 0.0, 1.0, h, 1.0);
            return Shapes.blockOccudes(voxelShape, occlusionShape, direction);
        }
        return false;
    }

    public static boolean shouldRenderFace(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos, FluidState fluidState, BlockState blockState, Direction direction, BlockState adjBlockState) {
        if (adjBlockState.getFluidState().getType().isSame(fluidState.getType())) return false;
        if (blockState.canOcclude()) return !blockState.isFaceSturdy(blockAndTintGetter, blockPos, direction);
        return true;
    }

    public BlockState getAdjBlockState(BlockAndTintGetter blockAndTintGetter, int x, int y, int z, Direction dir) {
        mBlockPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
        return blockAndTintGetter.getBlockState(mBlockPos);
    }

    private static FluidRenderHandler getFluidRenderHandler(FluidState fluidState) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluidState.getType());
        return handler != null ? handler : FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER);
    }

    private float calculateAverageHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, float f, float g, float h, BlockPos blockPos) {
        if (!(h >= 1.0F) && !(g >= 1.0F)) {
            float[] fs = new float[2];
            if (h > 0.0F || g > 0.0F) {
                float i = this.getHeight(blockAndTintGetter, fluid, blockPos);
                if (i >= 1.0F) return 1.0F;
                this.addWeightedHeight(fs, i);
            }
            this.addWeightedHeight(fs, f);
            this.addWeightedHeight(fs, h);
            this.addWeightedHeight(fs, g);
            return fs[0] / fs[1];
        }
        return 1.0F;
    }

    private void addWeightedHeight(float[] fs, float f) {
        if (f >= 0.8F) { fs[0] += f * 10.0F; fs[1] += 10.0F; }
        else if (f >= 0.0F) { fs[0] += f; fs[1]++; }
    }

    private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos) {
        return getHeight(blockAndTintGetter, fluid, blockPos, blockAndTintGetter.getBlockState(blockPos));
    }

    private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos, BlockState adjBlockState) {
        FluidState adjFluidState = adjBlockState.getFluidState();
        if (fluid.isSame(adjFluidState.getType())) {
            BlockState blockState2 = blockAndTintGetter.getBlockState(blockPos.offset(Direction.UP.getNormal()));
            return fluid.isSame(blockState2.getFluidState().getType()) ? 1.0F : adjFluidState.getOwnHeight();
        }
        return !adjBlockState.isSolid() ? 0.0F : -1.0f;
    }

    private void setVertex(ModelQuad quad, int i, float x, float y, float z, float u, float v) {
        quad.setX(i, x); quad.setY(i, y); quad.setZ(i, z);
        quad.setU(i, u); quad.setV(i, v);
    }

    private void updateQuad(ModelQuad quad, BlockPos blockPos, LightPipeline lightPipeline, Direction dir) {
        lightPipeline.calculate(quad, blockPos, resources.quadLightData, null, dir, false);
    }

    private void updateColor(float r, float g, float b, float brightness) {
        QuadLightData qld = resources.quadLightData;
        for (int i = 0; i < 4; i++) {
            float br = qld.br[i] * brightness;
            quadColors[i] = ColorUtil.RGBA.pack(r * br, g * br, b * br, 1.0f);
        }
    }
}