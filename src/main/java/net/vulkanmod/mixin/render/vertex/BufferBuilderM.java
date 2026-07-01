package net.vulkanmod.mixin.render.vertex;

import com.mojang.blaze3d.vertex.*;
import net.vulkanmod.interfaces.ExtendedVertexBuilder;
import net.vulkanmod.render.vertex.format.I32_SNorm;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderM
        implements VertexConsumer, ExtendedVertexBuilder {

    @Shadow private boolean fastFormat;
    @Shadow private boolean fullFormat;
    @Shadow private VertexFormat format;

    @Shadow
    private int nextElementByte;
    @Shadow
    private ByteBuffer buffer;
    @Shadow
    @Nullable
    private VertexFormatElement currentElement;

    @Shadow
    public abstract void nextElement();

    private long bufferPtr;
    private long ptr;

    private long nextElementPtr() {
        return this.bufferPtr + (long)this.nextElementByte;
    }

    protected void setNextElementByte(int i) {
        this.nextElementByte = i;
    }

    @Inject(
            method = {"<init>"},
            at = {@At("RETURN")}
    )
    private void setPtrC(int initialCapacity, CallbackInfo ci) {
        this.bufferPtr = MemoryUtil.memAddress0(this.buffer);
    }

    @Inject(
            method = {"ensureCapacity"},
            at = {@At("RETURN")}
    )
    private void setPtr(int initialCapacity, CallbackInfo ci) {
        this.bufferPtr = MemoryUtil.memAddress0(this.buffer);
    }

    public void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
        this.ptr = this.nextElementPtr();

        if (this.format == DefaultVertexFormat.NEW_ENTITY) {
            MemoryUtil.memPutFloat(ptr + 0, x);
            MemoryUtil.memPutFloat(ptr + 4, y);
            MemoryUtil.memPutFloat(ptr + 8, z);

            MemoryUtil.memPutInt(ptr + 12, packedColor);

            MemoryUtil.memPutFloat(ptr + 16, u);
            MemoryUtil.memPutFloat(ptr + 20, v);

            MemoryUtil.memPutInt(ptr + 24, overlay);

            MemoryUtil.memPutInt(ptr + 28, light);
            MemoryUtil.memPutInt(ptr + 32, packedNormal);
            this.nextElementByte+=36;
            this.endVertex();
        }
        else {

            this.position(x, y, z);
            this.fastColor(packedColor);
            this.fastUv(u, v);
            this.fastOverlay(overlay);
            this.light(light);
            this.fastNormal(packedNormal);
            this.endVertex();

//            throw new RuntimeException("unaccepted format: " + this.format);
        }

    }

    public void vertex(float x, float y, float z, float u, float v, int packedColor, int light) {
        this.ptr = this.nextElementPtr();

        MemoryUtil.memPutFloat(ptr + 0, x);
        MemoryUtil.memPutFloat(ptr + 4, y);
        MemoryUtil.memPutFloat(ptr + 8, z);

        MemoryUtil.memPutFloat(ptr + 12, u);
        MemoryUtil.memPutFloat(ptr + 16, v);

        MemoryUtil.memPutInt(ptr + 20, packedColor);

        MemoryUtil.memPutInt(ptr + 24, light);

        this.nextElementByte+=28;

        this.endVertex();
    }

    public void position(float x, float y, float z) {
        MemoryUtil.memPutFloat(ptr + 0, x);
        MemoryUtil.memPutFloat(ptr + 4, y);
        MemoryUtil.memPutFloat(ptr + 8, z);
        this.nextElement();
    }

    public void fastColor(int packedColor) {
        if (this.currentElement.getUsage()==VertexFormatElement.Usage.COLOR) {
            MemoryUtil.memPutInt(this.ptr+12, packedColor);
            this.nextElement();
        }
    }

    public void fastUv(float u, float v) {
        if(this.currentElement.getUsage()==VertexFormatElement.Usage.UV){
            MemoryUtil.memPutFloat(this.ptr + 16L, u);
            MemoryUtil.memPutFloat(this.ptr + 20L, v);
            this.nextElement();
        }
    }

    public void fastOverlay(int o) {
        if(this.currentElement.getUsage()==VertexFormatElement.Usage.UV){
            MemoryUtil.memPutInt(this.ptr + 24L, o);
            this.nextElement();
        }
    }

    public void light(int l) {
        if(this.currentElement.getUsage()==VertexFormatElement.Usage.UV){
            MemoryUtil.memPutInt(this.ptr + 28L,l);
            this.nextElement();
        }
    }

    public void fastNormal(int packedNormal) {
        if(this.currentElement.getUsage()==VertexFormatElement.Usage.NORMAL){
            MemoryUtil.memPutInt(this.ptr + 32L, packedNormal);
            this.nextElement();
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        if (this.fastFormat) {
            long ptr = this.nextElementPtr();
            MemoryUtil.memPutFloat(ptr + 0, x);
            MemoryUtil.memPutFloat(ptr + 4, y);
            MemoryUtil.memPutFloat(ptr + 8, z);

            int temp = ColorUtil.RGBA.pack(red, green, blue, alpha);
            MemoryUtil.memPutInt(ptr + 12L, temp);

            MemoryUtil.memPutFloat(ptr + 16, u);
            MemoryUtil.memPutFloat(ptr + 20, v);

            byte i;
            if (this.fullFormat) {
                MemoryUtil.memPutInt(ptr + 24, overlay);
                i = 28;
            } else {
                i = 24;
            }

            MemoryUtil.memPutInt(ptr + i, light);

            temp = I32_SNorm.packNormal(normalX, normalY, normalZ);
            MemoryUtil.memPutInt(ptr + i + 4, temp);
            this.nextElementByte+= i + 8;
            this.endVertex();
        } else {
            VertexConsumer.super.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
        }
    }

    /*@Override
    public void putBulkData(PoseStack.Pose matrixEntry, BakedQuad quad, float[] brightness, float red, float green,
                            float blue, float alpha, int[] lights, int overlay, boolean useQuadColorData) {
        putQuadData(matrixEntry, quad, brightness, red, green, blue, alpha, lights, overlay, useQuadColorData);
    }

    @SuppressWarnings("UnreachableCode")
    @Unique
    private void putQuadData(PoseStack.Pose matrixEntry, BakedQuad quad, float[] brightness, float red, float green, float blue, float alpha, int[] lights, int overlay, boolean useQuadColorData) {
        int[] quadData = quad.getVertices();
        Vec3i vec3i = quad.getDirection().getNormal();
        Matrix4f matrix4f = matrixEntry.pose();

        boolean trustedNormals = ((PoseAccessor)(Object)matrixEntry).trustedNormals();
        int normal = MathUtil.packTransformedNorm(matrixEntry.normal(), trustedNormals, vec3i.getX(), vec3i.getY(), vec3i.getZ());

        for (int k = 0; k < 4; ++k) {
            float r, g, b;

            float quadR, quadG, quadB;

            int i = k * 8;
            float x = Float.intBitsToFloat(quadData[i]);
            float y = Float.intBitsToFloat(quadData[i + 1]);
            float z = Float.intBitsToFloat(quadData[i + 2]);

            float tx = MathUtil.transformX(matrix4f, x, y, z);
            float ty = MathUtil.transformY(matrix4f, x, y, z);
            float tz = MathUtil.transformZ(matrix4f, x, y, z);

            if (useQuadColorData) {
                int color = quadData[i + 3];
                quadR = ColorUtil.RGBA.unpackR(color);
                quadG = ColorUtil.RGBA.unpackG(color);
                quadB = ColorUtil.RGBA.unpackB(color);
                r = quadR * brightness[k] * red;
                g = quadG * brightness[k] * green;
                b = quadB * brightness[k] * blue;
            } else {
                r = brightness[k] * red;
                g = brightness[k] * green;
                b = brightness[k] * blue;
            }

            int color = ColorUtil.RGBA.pack(r, g, b, alpha);

            int light = lights[k];
            float u = Float.intBitsToFloat(quadData[i + 4]);
            float v = Float.intBitsToFloat(quadData[i + 5]);

            this.vertex(tx, ty, tz, color, u, v, overlay, light, normal);
        }
    }*/

}
