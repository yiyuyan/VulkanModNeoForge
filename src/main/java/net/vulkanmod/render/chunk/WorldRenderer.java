package net.vulkanmod.render.chunk;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.build.RenderRegionBuilder;
import net.vulkanmod.render.chunk.build.task.TaskDispatcher;
import net.vulkanmod.render.chunk.build.task.ChunkTask;
import net.vulkanmod.render.chunk.cull.GpuCuller;
import net.vulkanmod.render.chunk.graph.SectionGraph;
import net.vulkanmod.render.profiling.BuildTimeProfiler;
import net.vulkanmod.render.profiling.Profiler;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.memory.buffer.IndexBuffer;
import net.vulkanmod.vulkan.memory.buffer.IndirectBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.util.*;

public class WorldRenderer {
    private static WorldRenderer INSTANCE;

    private final Minecraft minecraft;
    private ClientLevel level;
    private int renderDistance;
    private final RenderBuffers renderBuffers;

    private Vec3 cameraPos;
    private int lastCameraSectionX;
    private int lastCameraSectionY;
    private int lastCameraSectionZ;
    private float lastCameraX;
    private float lastCameraY;
    private float lastCameraZ;
    private float lastCamRotX;
    private float lastCamRotY;

    private SectionGrid sectionGrid;
    private SectionGraph sectionGraph;
    private boolean graphNeedsUpdate;
    private int lastSingleplayerViewDistance = -1;

    private final Set<BlockEntity> globalBlockEntities = Sets.newHashSet();
    private final TaskDispatcher taskDispatcher;

    private double xTransparentOld;
    private int translucentSortCursor = 0;
    private double yTransparentOld;
    private double zTransparentOld;

    IndirectBuffer[] indirectBuffers;
    private GpuCuller gpuCuller;

    // Hi-Z occlusion data
    private final Matrix4f hiZViewProj = new Matrix4f();
    private final Matrix4f prevViewProjScratch = new Matrix4f();
    private double hiZCamX, hiZCamY, hiZCamZ;
    private boolean hiZMatrixValid = false;

    public RenderRegionBuilder renderRegionCache;
    private final List<Runnable> onAllChangedCallbacks = new ObjectArrayList<>();

    private WorldRenderer(RenderBuffers renderBuffers) {
        this.minecraft = Minecraft.getInstance();
        this.renderBuffers = renderBuffers;
        this.renderRegionCache = new RenderRegionBuilder();
        this.taskDispatcher = new TaskDispatcher();
        ChunkTask.setTaskDispatcher(this.taskDispatcher);
        allocateIndirectBuffers();
        TerrainRenderType.updateMapping();
        Renderer.getInstance().addOnResizeCallback(() -> {
            if (this.indirectBuffers.length != Renderer.getFramesNum())
                allocateIndirectBuffers();
        });
    }

    private void allocateIndirectBuffers() {
        if (this.indirectBuffers != null)
            Arrays.stream(this.indirectBuffers).forEach(Buffer::scheduleFree);
        this.indirectBuffers = new IndirectBuffer[Renderer.getFramesNum()];
        for (int i = 0; i < this.indirectBuffers.length; ++i) {
            this.indirectBuffers[i] = new IndirectBuffer(1000000, MemoryTypes.HOST_MEM);
        }
    }

    public static WorldRenderer init(RenderBuffers renderBuffers) {
        return Objects.requireNonNullElseGet(INSTANCE, () -> INSTANCE = new WorldRenderer(renderBuffers));
    }

    public static WorldRenderer getInstance() { return INSTANCE; }
    public static ClientLevel getLevel() { return INSTANCE.level; }
    public static Vec3 getCameraPos() { return INSTANCE.cameraPos; }

    private void benchCallback() {
        BuildTimeProfiler.runBench(this.graphNeedsUpdate || !this.taskDispatcher.isIdle());
    }

    public void setupRenderer(Camera camera, Frustum frustum, boolean isCapturedFrustum, boolean spectator) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.push("Setup_Renderer");

        ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();

        benchCallback();

        this.cameraPos = camera.getPosition();
        if (this.minecraft.options.getEffectiveRenderDistance() != this.renderDistance) {
            this.allChanged();
        }

        mcProfiler.push("camera");
        float cameraX = (float) cameraPos.x();
        float cameraY = (float) cameraPos.y();
        float cameraZ = (float) cameraPos.z();
        int sectionX = SectionPos.posToSectionCoord(cameraX);
        int sectionY = SectionPos.posToSectionCoord(cameraY);
        int sectionZ = SectionPos.posToSectionCoord(cameraZ);

        profiler.push("reposition");
        if (this.lastCameraSectionX != sectionX || this.lastCameraSectionY != sectionY || this.lastCameraSectionZ != sectionZ) {
            this.lastCameraSectionX = sectionX;
            this.lastCameraSectionY = sectionY;
            this.lastCameraSectionZ = sectionZ;
            this.sectionGrid.repositionCamera(cameraX, cameraZ);
        }
        profiler.pop();

        double entityDistanceScaling = this.minecraft.options.entityDistanceScaling().get();
        Entity.setViewScale(Mth.clamp((double) this.renderDistance / 8.0D, 1.0D, 2.5D) * entityDistanceScaling);

        mcProfiler.popPush("cull");
        mcProfiler.popPush("update");

        boolean cameraMoved = false;
        float d_xRot = Math.abs(camera.getXRot() - this.lastCamRotX);
        float d_yRot = Math.abs(camera.getYRot() - this.lastCamRotY);
        cameraMoved |= d_xRot > 2.0f || d_yRot > 2.0f;
        cameraMoved |= cameraX != this.lastCameraX || cameraY != this.lastCameraY || cameraZ != this.lastCameraZ;
        this.graphNeedsUpdate |= cameraMoved;
        this.graphNeedsUpdate |= !this.taskDispatcher.isIdle();

        if (!isCapturedFrustum) {
            if (this.graphNeedsUpdate) {
                this.graphNeedsUpdate = false;
                this.lastCameraX = cameraX;
                this.lastCameraY = cameraY;
                this.lastCameraZ = cameraZ;
                this.lastCamRotX = camera.getXRot();
                this.lastCamRotY = camera.getYRot();
                this.sectionGraph.update(camera, frustum, spectator);
            }
        }

        this.indirectBuffers[Renderer.getCurrentFrame()].reset();

        if (this.gpuCuller != null && this.sectionGraph.getFrustum() != null) {
            mcProfiler.push("gpu_cull");
            Renderer renderer = Renderer.getInstance();
            var prevRenderPass = renderer.getBoundRenderPass();
            var prevFramebuffer = renderer.getBoundFramebuffer();
            renderer.endRenderPass();

            var vf = this.sectionGraph.getFrustum();
            if (this.hiZMatrixValid) {
                float dx = (float) (vf.getCamX() - this.hiZCamX);
                float dy = (float) (vf.getCamY() - this.hiZCamY);
                float dz = (float) (vf.getCamZ() - this.hiZCamZ);
                this.hiZViewProj.translate(dx, dy, dz, this.prevViewProjScratch);
            } else {
                this.prevViewProjScratch.identity();
            }

            this.gpuCuller.recordCull(Renderer.getCommandBuffer(),
                    this.sectionGrid.getChunkAreaManager(),
                    this.sectionGraph.getChunkAreaQueue().queue(), vf, this.prevViewProjScratch);

            if (prevFramebuffer != null) {
                renderer.beginRendering(prevRenderPass, prevFramebuffer);
            }
            mcProfiler.pop();
        }

        mcProfiler.pop();
        profiler.pop();
    }

    public void uploadSections() {
        ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
        mcProfiler.push("upload");
        Profiler profiler = Profiler.getMainProfiler();
        profiler.push("Uploads");
        try {
            if (this.taskDispatcher.updateSections())
                this.graphNeedsUpdate = true;
        } catch (Exception e) {
            Initializer.LOGGER.error("Failed to upload section geometry", e);
            this.graphNeedsUpdate = true;
        }
        profiler.pop();
        mcProfiler.pop();
    }

    public boolean isSectionCompiled(BlockPos blockPos) {
        RenderSection renderSection = this.sectionGrid.getSectionAtBlockPos(blockPos);
        return renderSection != null && renderSection.isCompiled();
    }

    public void allChanged() {
        if (this.level != null) {
            this.level.clearTintCaches();
            this.renderRegionCache.clear();
            this.taskDispatcher.createThreads(Initializer.CONFIG.builderThreads);
            this.graphNeedsUpdate = true;
            this.syncSingleplayerViewDistance();
            this.renderDistance = this.minecraft.options.getEffectiveRenderDistance();
            if (this.sectionGrid != null) {
                this.sectionGrid.freeAllBuffers();
            }
            this.taskDispatcher.clearBatchQueue();
            synchronized (this.globalBlockEntities) {
                this.globalBlockEntities.clear();
            }
            this.sectionGrid = new SectionGrid(this.level, this.renderDistance);
            this.sectionGraph = new SectionGraph(this.level, this.sectionGrid, this.taskDispatcher);

            if (this.gpuCuller != null) {
                VK10.vkDeviceWaitIdle(Vulkan.getVkDevice());
                this.gpuCuller.cleanUp();
                this.gpuCuller = null;
            }
            if (GpuCuller.isEnabled()) {
                ChunkAreaManager areaManager = this.sectionGrid.getChunkAreaManager();
                this.gpuCuller = new GpuCuller(areaManager.size, Renderer.getFramesNum());
                DrawBuffers.setDirtyListener(this.gpuCuller::markDirty);
                for (int i = 0; i < areaManager.size; i++) {
                    if (areaManager.getChunkArea(i).getDrawBuffers().isAllocated())
                        this.gpuCuller.markDirty(i);
                }
            } else {
                DrawBuffers.setDirtyListener(null);
            }

            this.onAllChangedCallbacks.forEach(Runnable::run);
            Entity entity = this.minecraft.getCameraEntity();
            if (entity != null) {
                this.sectionGrid.repositionCamera(entity.getX(), entity.getZ());
            }
        }
    }

    private void syncSingleplayerViewDistance() {
        if (!this.minecraft.hasSingleplayerServer()) return;
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null || server.getPlayerList() == null) return;
        int viewDistance = this.minecraft.options.renderDistance().get();
        if (viewDistance == this.lastSingleplayerViewDistance) return;
        this.lastSingleplayerViewDistance = viewDistance;
        this.minecraft.options.setServerRenderDistance(viewDistance);
        server.execute(() -> server.getPlayerList().setViewDistance(viewDistance));
    }

    public void setLevel(@Nullable ClientLevel level) {
        this.lastCameraX = Float.MIN_VALUE;
        this.lastCameraY = Float.MIN_VALUE;
        this.lastCameraZ = Float.MIN_VALUE;
        this.lastCameraSectionX = Integer.MIN_VALUE;
        this.lastCameraSectionY = Integer.MIN_VALUE;
        this.lastCameraSectionZ = Integer.MIN_VALUE;
        this.level = level;
        ChunkStatusMap.createInstance(renderDistance);
        if (level != null) {
            this.allChanged();
        } else {
            if (this.sectionGrid != null) {
                this.sectionGrid.freeAllBuffers();
                this.sectionGrid = null;
            }
            this.taskDispatcher.stopThreads();
            this.graphNeedsUpdate = true;
        }
    }

    public void addOnAllChangedCallback(Runnable runnable) { this.onAllChangedCallbacks.add(runnable); }
    public void clearOnAllChangedCallbacks() { this.onAllChangedCallbacks.clear(); }

    public void renderSectionLayer(RenderType renderType, double camX, double camY, double camZ,
                                   Matrix4f modelView, Matrix4f projection) {
        TerrainRenderType terrainRenderType = TerrainRenderType.get(renderType);
        renderType.setupRenderState();
        this.sortTranslucentSections(camX, camY, camZ);

        ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
        Zone zone = mcProfiler.zone(() -> "render_" + renderType);

        final boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;
        final boolean indirectDraw = Initializer.CONFIG.indirectDraw;

        VRenderSystem.applyMVP(modelView, projection);
        VRenderSystem.setPrimitiveTopologyGL(GL11.GL_TRIANGLES);

        Renderer renderer = Renderer.getInstance();
        GraphicsPipeline pipeline = PipelineManager.getTerrainShader(terrainRenderType);
        renderer.bindGraphicsPipeline(pipeline);
        VTextureSelector.bindShaderTextures(pipeline);

        IndexBuffer globalIndexBuffer = Renderer.getDrawer().getQuadsIndexBuffer().getIndexBuffer();
        Renderer.getDrawer().bindIndexBuffer(Renderer.getCommandBuffer(), globalIndexBuffer, globalIndexBuffer.indexType.value);

        int currentFrame = Renderer.getCurrentFrame();
        Set<TerrainRenderType> allowedRenderTypes = Initializer.CONFIG.uniqueOpaqueLayer
                ? TerrainRenderType.COMPACT_RENDER_TYPES
                : TerrainRenderType.SEMI_COMPACT_RENDER_TYPES;

        if (allowedRenderTypes.contains(terrainRenderType)) {
            terrainRenderType.setCutoutUniform();
            renderer.uploadAndBindUBOs(pipeline);

            final int gpuTypeIdx = this.gpuCuller != null ? GpuCuller.typeIndex(terrainRenderType) : -1;
            Vec3 cameraVec = new Vec3(camX, camY, camZ);

            for (Iterator<ChunkArea> iterator = this.sectionGraph.getChunkAreaQueue().iterator(isTranslucent); iterator.hasNext(); ) {
                ChunkArea chunkArea = iterator.next();
                DrawBuffers drawBuffers = chunkArea.drawBuffers;

                if (gpuTypeIdx >= 0) {
                    if (drawBuffers.getAreaBuffer(terrainRenderType) != null && this.gpuCuller.hasEntries(chunkArea.index)) {
                        drawBuffers.bindBuffers(Renderer.getCommandBuffer(), pipeline, terrainRenderType, camX, camY, camZ);
                        this.gpuCuller.drawArea(Renderer.getCommandBuffer(), chunkArea.index, gpuTypeIdx);
                    }
                    continue;
                }

                var queue = chunkArea.sectionQueue;
                if (drawBuffers.getAreaBuffer(terrainRenderType) != null && queue.size() > 0) {
                    drawBuffers.bindBuffers(Renderer.getCommandBuffer(), pipeline, terrainRenderType, camX, camY, camZ);
                    if (indirectDraw)
                        drawBuffers.buildDrawBatchesIndirect(cameraVec, indirectBuffers[currentFrame], queue, terrainRenderType);
                    else
                        drawBuffers.buildDrawBatchesDirect(cameraVec, queue, terrainRenderType);

                    if (isTranslucent && drawBuffers.getIndexBuffer() != null) {
                        Renderer.getDrawer().bindIndexBuffer(Renderer.getCommandBuffer(), globalIndexBuffer, globalIndexBuffer.indexType.value);
                    }
                }
            }
        }

        if (terrainRenderType == TerrainRenderType.CUTOUT || terrainRenderType == TerrainRenderType.TRIPWIRE) {
            indirectBuffers[currentFrame].submitUploads();
        }

        if (!indirectDraw || this.gpuCuller != null) {
            VRenderSystem.setModelOffset(0, 0, 0);
            renderer.pushConstants(pipeline);
        }

        zone.close();
        renderType.clearRenderState();

        if (terrainRenderType == TerrainRenderType.CUTOUT
                && this.gpuCuller != null && Initializer.CONFIG.occlusionCulling) {
            mcProfiler.push("hiz_build");
            VulkanImage depth = Renderer.getInstance().getSwapChain().getDepthAttachment();
            var cmd = Renderer.getCommandBuffer();
            renderer.endRenderPass();
            this.gpuCuller.ensureHiZ(depth);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                depth.readOnlyLayout(stack, cmd);
                this.gpuCuller.recordHiZBuild(cmd);
                depth.transitionImageLayout(stack, cmd, VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }
            renderer.getMainPass().rebindMainTarget();

            var hzf = this.sectionGraph.getFrustum();
            if (hzf != null) {
                this.hiZViewProj.set(hzf.getMatrix());
                this.hiZCamX = hzf.getCamX();
                this.hiZCamY = hzf.getCamY();
                this.hiZCamZ = hzf.getCamZ();
                this.hiZMatrixValid = true;
            }
            mcProfiler.pop();
        }
    }

    private void sortTranslucentSections(double camX, double camY, double camZ) {
        ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
        mcProfiler.push("translucent_sort");
        double d0 = camX - this.xTransparentOld;
        double d1 = camY - this.yTransparentOld;
        double d2 = camZ - this.zTransparentOld;
        if (d0 * d0 + d1 * d1 + d2 * d2 > 2.0D) {
            this.xTransparentOld = camX;
            this.yTransparentOld = camY;
            this.zTransparentOld = camZ;
            var queue = this.sectionGraph.getSectionQueue();
            final int n = Math.min(queue.size(), 15);
            for (int i = 0; i < n; i++) {
                queue.get(i).resortTransparency(this.taskDispatcher);
            }
        }
        mcProfiler.pop();
    }

    public void renderBlockEntities(PoseStack poseStack, double camX, double camY, double camZ,
                                    Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress, float gameTime) {
        Profiler profiler = Profiler.getMainProfiler();
        profiler.pop();
        profiler.push("Block-entities");
        MultiBufferSource bufferSource = this.renderBuffers.bufferSource();
        for (RenderSection renderSection : this.sectionGraph.getBlockEntitiesSections()) {
            List<BlockEntity> list = renderSection.getCompiledSection().getBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockEntity : list) {
                    BlockPos blockPos = blockEntity.getBlockPos();
                    MultiBufferSource bufferSource1 = bufferSource;
                    poseStack.pushPose();
                    poseStack.translate((double) blockPos.getX() - camX, (double) blockPos.getY() - camY, (double) blockPos.getZ() - camZ);
                    SortedSet<BlockDestructionProgress> sortedset = destructionProgress.get(blockPos.asLong());
                    if (sortedset != null && !sortedset.isEmpty()) {
                        int j1 = sortedset.last().getProgress();
                        if (j1 >= 0) {
                            PoseStack.Pose pose = poseStack.last();
                            VertexConsumer vertexconsumer = new SheetedDecalTextureGenerator(this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(j1)), pose, 1.0f);
                            bufferSource1 = (renderType) -> {
                                VertexConsumer vertexConsumer2 = bufferSource.getBuffer(renderType);
                                return renderType.affectsCrumbling() ? VertexMultiConsumer.create(vertexconsumer, vertexConsumer2) : vertexConsumer2;
                            };
                        }
                    }
                    this.minecraft.getBlockEntityRenderDispatcher().render(blockEntity, gameTime, poseStack, bufferSource1);
                    poseStack.popPose();
                }
            }
        }
    }

    public void scheduleGraphUpdate() { this.graphNeedsUpdate = true; }
    public void onChunkLoaded(int chunkX, int chunkZ) {
        if (this.sectionGrid == null) return;
        for (RenderSection section : this.sectionGrid.getRenderSectionsAt(chunkX, chunkZ)) {
            section.setDirty(false);
        }
        this.renderRegionCache.remove(chunkX, chunkZ);
        this.graphNeedsUpdate = true;
    }
    public boolean graphNeedsUpdate() { return this.graphNeedsUpdate; }
    public int getVisibleSectionsCount() { return this.sectionGraph.getSectionQueue().size(); }
    public void setSectionDirty(int x, int y, int z, boolean flag) {
        this.sectionGrid.setDirty(x, y, z, flag);
        this.renderRegionCache.remove(x, z);
        this.graphNeedsUpdate = true;
    }
    public SectionGrid getSectionGrid() { return this.sectionGrid; }
    public ChunkAreaManager getChunkAreaManager() { return this.sectionGrid.chunkAreaManager; }
    public TaskDispatcher getTaskDispatcher() { return taskDispatcher; }
    public short getLastFrame() { return this.sectionGraph.getLastFrame(); }
    public int getRenderDistance() { return this.renderDistance; }
    public String getChunkStatistics() { return this.sectionGraph.getStatistics(); }
    public void cleanUp() {
        if (indirectBuffers != null)
            Arrays.stream(indirectBuffers).forEach(Buffer::scheduleFree);
        if (gpuCuller != null) {
            VK10.vkDeviceWaitIdle(Vulkan.getVkDevice());
            gpuCuller.cleanUp();
            gpuCuller = null;
            DrawBuffers.setDirtyListener(null);
        }
    }
}