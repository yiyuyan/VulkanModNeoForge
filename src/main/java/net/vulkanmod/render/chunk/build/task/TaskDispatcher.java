package net.vulkanmod.render.chunk.build.task;

import com.google.common.collect.Queues;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.ChunkAreaManager;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.chunk.build.thread.ThreadBuilderPack;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;

public class TaskDispatcher {
    private final Queue<CompileResult> compileResults = Queues.newLinkedBlockingDeque();
    public final ThreadBuilderPack fixedBuffers;

    private volatile boolean stopThreads;
    private Thread[] threads;
    private BuilderResources[] resources;
    private int idleThreads;
    private final java.util.concurrent.PriorityBlockingQueue<ChunkTask> taskQueue =
            new java.util.concurrent.PriorityBlockingQueue<>(64,
                    java.util.Comparator.<ChunkTask>comparingInt(t -> t.highPriority ? 0 : 1)
                            .thenComparingDouble(t -> t.distSq));

    public TaskDispatcher() {
        this.fixedBuffers = new ThreadBuilderPack();
        this.stopThreads = true;
    }

    public void createThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        int n = Math.max(1, (cores * 5) / 8);
        createThreads(n);
    }

    public void createThreads(int n) {
        if(!this.stopThreads) {
            this.stopThreads();
        }
        this.stopThreads = false;

        if(this.resources != null) {
            for (BuilderResources resources : this.resources) {
                resources.clear();
            }
        }

        // Auto select thread count
        if (n == 0) {
            n = Math.max((Runtime.getRuntime().availableProcessors() - 1) / 2, 1);
        }

        this.threads = new Thread[n];
        this.resources = new BuilderResources[n];

        for (int i = 0; i < n; i++) {
            BuilderResources builderResources = new BuilderResources();
            Thread thread = new Thread(() -> runTaskThread(builderResources), "Builder-" + i);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
            this.threads[i] = thread;
            this.resources[i] = builderResources;
            thread.start();
        }
    }

    private void runTaskThread(BuilderResources builderResources) {
        while(!this.stopThreads) {
            ChunkTask task = this.pollTask();
            if(task == null) {
                synchronized (this) {
                    if (this.stopThreads) break;
                    if (this.taskQueue.isEmpty()) {
                        try {
                            this.idleThreads++;
                            this.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        this.idleThreads--;
                    }
                }
                continue;
            }
            task.runTask(builderResources);
        }
    }

    public void schedule(ChunkTask chunkTask) {
        if (chunkTask == null) return;

        Vec3 cam = WorldRenderer.getCameraPos();
        RenderSection sec = chunkTask.getSection();
        double dx = sec.xOffset() + 8 - cam.x;
        double dy = sec.yOffset() + 8 - cam.y;
        double dz = sec.zOffset() + 8 - cam.z;
        chunkTask.distSq = (float) (dx * dx + dy * dy + dz * dz);

        this.taskQueue.offer(chunkTask);
        synchronized (this) { this.notify(); }
    }

    @Nullable
    private ChunkTask pollTask() {
        return this.taskQueue.poll();
    }

    public void stopThreads() {
        if(this.stopThreads) return;
        this.stopThreads = true;
        synchronized (this) { this.notifyAll(); }

        for (Thread thread : this.threads) {
            try { thread.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }

        if (this.resources != null) {
            for (BuilderResources resources : this.resources) {
                if (resources != null) resources.free();
            }
            this.resources = null;
        }
        this.threads = null;
    }

    public boolean updateSections() {
        CompileResult result;
        boolean flag = false;
        int backlog = this.compileResults.size();
        int budget = Math.min(256, Math.max(64, backlog));
        while (budget-- > 0 && (result = this.compileResults.poll()) != null) {
            flag = true;
            doSectionUpdate(result);
        }
        return flag;
    }

    public void scheduleSectionUpdate(CompileResult compileResult) {
        this.compileResults.add(compileResult);
    }

    private void doSectionUpdate(CompileResult compileResult) {
        RenderSection section = compileResult.renderSection;
        ChunkArea renderArea = section.getChunkArea();
        DrawBuffers drawBuffers = renderArea.getDrawBuffers();

        ChunkAreaManager chunkAreaManager = WorldRenderer.getInstance().getChunkAreaManager();
        if (chunkAreaManager.getChunkArea(renderArea.index) != renderArea) return;

        if(compileResult.fullUpdate) {
            var renderLayers = compileResult.renderedLayers;
            for(TerrainRenderType renderType : TerrainRenderType.VALUES) {
                UploadBuffer uploadBuffer = renderLayers.get(renderType);
                if(uploadBuffer != null) {
                    drawBuffers.upload(section, uploadBuffer, renderType);
                } else {
                    section.resetDrawParameters(renderType);
                }
            }
            compileResult.updateSection();
        } else {
            UploadBuffer uploadBuffer = compileResult.renderedLayers.get(TerrainRenderType.TRANSLUCENT);
            drawBuffers.upload(section, uploadBuffer, TerrainRenderType.TRANSLUCENT);
        }
    }

    public boolean isIdle() {
        return this.threads == null || (this.idleThreads == this.threads.length && this.compileResults.isEmpty());
    }

    public void clearBatchQueue() {
        ChunkTask chunkTask;
        while ((chunkTask = this.taskQueue.poll()) != null) {
            chunkTask.cancel();
        }
    }

    public String getStats() {
        return String.format("iT: %d Ts: %d", this.idleThreads, taskQueue.size());
    }

    public BuilderResources[] getResourcesArray() {
        return resources;
    }
}