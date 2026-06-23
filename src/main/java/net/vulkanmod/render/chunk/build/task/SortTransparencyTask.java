package net.vulkanmod.render.chunk.build.task;

import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;

public class SortTransparencyTask extends ChunkTask {
    public SortTransparencyTask(RenderSection section) { super(section); }

    public String name() { return "rend_chk_sort"; }

    public Result runTask(BuilderResources context) {
        if (cancelled.get()) return Result.CANCELLED;
        Vec3 cam = WorldRenderer.getCameraPos();
        float x = (float)cam.x, y = (float)cam.y, z = (float)cam.z;

        CompiledSection compiled = this.section.getCompiledSection();
        TerrainBuilder builder = context.builderPack.builder(TerrainRenderType.TRANSLUCENT);
        builder.begin();
        builder.restoreSortState(compiled.transparencyState);
        builder.setupQuadSorting(x - section.xOffset(), y - section.yOffset(), z - section.zOffset());
        TerrainBuilder.DrawState drawState = builder.endDrawing();

        CompileResult result = new CompileResult(this.section, false);
        result.renderedLayers.put(TerrainRenderType.TRANSLUCENT, new UploadBuffer(builder, drawState));
        builder.reset();

        if (cancelled.get()) return Result.CANCELLED;
        taskDispatcher.scheduleSectionUpdate(result);
        return Result.SUCCESSFUL;
    }
}