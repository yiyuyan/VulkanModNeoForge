package net.vulkanmod.render.chunk;

import net.minecraft.core.BlockPos;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.util.StaticQueue;
import org.joml.Vector3i;

public class ChunkArea {
    public final int index;
    final Vector3i position;
    final byte[] frustumBuffer = new byte[64];
    int sectionsContained = 0;

    DrawBuffers drawBuffers;

    //Help JIT optimisations by hardcoding the queue size to the max possible ChunkArea limit
    public final StaticQueue<RenderSection> sectionQueue = new StaticQueue<>(512);

    public ChunkArea(int i, Vector3i origin, int minHeight) {
        this.index = i;
        this.position = origin;
        this.drawBuffers = new DrawBuffers(i, origin, minHeight);
    }

    public byte getFrustumIndex(BlockPos pos) {
        return getFrustumIndex(pos.getX(), pos.getY(), pos.getZ());
    }

    public byte getFrustumIndex(int x, int y, int z) {
        int dx = x - this.position.x;
        int dy = y - this.position.y;
        int dz = z - this.position.z;

        int i = ((dx >> 1) & 0b100_000)
                + ((dy >> 2) & 0b10_000)
                + ((dz >> 3) & 0b1_000);

        int xSub = (dx >> 3) & 0b100;
        int ySub = (dy >> 4) & 0b10;
        int zSub = (dz >> 5) & 0b1;

        return (byte) (i + xSub + ySub + zSub);
    }

    public byte inFrustum(byte i) {
        return this.frustumBuffer[i];
    }

    public byte[] getFrustumBuffer() {
        return this.frustumBuffer;
    }

    public DrawBuffers getDrawBuffers() {
        return this.drawBuffers;
    }

    public void resetQueue() {
        this.sectionQueue.clear();
    }

    public void setPosition(int x, int y, int z) {
        this.position.set(x, y, z);
    }

    public Vector3i getPosition() {
        return this.position;
    }

    public void addSection() {
        this.sectionsContained++;
    }

    public void removeSection() {
        this.sectionsContained--;

        if (this.sectionsContained == 0) {
            this.releaseBuffers();
        }
    }

    public void releaseBuffers() {
        this.drawBuffers.releaseBuffers();
    }
}
