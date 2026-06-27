package net.vulkanmod.mixin.compatibility.gl;

import net.vulkanmod.gl.VkGlBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(GL15.class)
public class GL15M {

    /**
     * @author
     */
    @Overwrite(remap = false)
    @NativeType("void")
    public static int glGenBuffers() {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glGenBuffers");
            method.setAccessible(true);
            return (int) method.invoke(null);
        }
        catch (Throwable e) {
            return VkGlBuffer.glGenBuffers();
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void glBindBuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int buffer) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glBindBuffer", int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, target, buffer);
        }
        catch (Throwable e) {
            VkGlBuffer.glBindBuffer(target, buffer);
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void glBufferData(@NativeType("GLenum") int target, @NativeType("void const *") ByteBuffer data, @NativeType("GLenum") int usage) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glBufferData", int.class, ByteBuffer.class, int.class);
            method.setAccessible(true);
            method.invoke(null, target, data, usage);
        }
        catch (Throwable e) {
            VkGlBuffer.glBufferData(target, data, usage);
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void glBufferData(int i, long l, int j) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glBufferData", int.class, long.class, int.class);
            method.setAccessible(true);
            method.invoke(null, i, l, j);
        }
        catch (Throwable e) {
            VkGlBuffer.glBufferData(i, l, j);
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    @NativeType("void *")
    public static ByteBuffer glMapBuffer(@NativeType("GLenum") int target, @NativeType("GLenum") int access) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glMapBuffer", int.class, int.class);
            method.setAccessible(true);
            return (ByteBuffer) method.invoke(null, target, access);
        }
        catch (Throwable e) {
            return VkGlBuffer.glMapBuffer(target, access);
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    @Nullable
    @NativeType("void *")
    public static ByteBuffer glMapBuffer(@NativeType("GLenum") int target, @NativeType("GLenum") int access, long length, @Nullable ByteBuffer old_buffer) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glMapBuffer", int.class, int.class);
            method.setAccessible(true);
            return (ByteBuffer) method.invoke(null, target, access);
        }
        catch (Throwable e) {
            return VkGlBuffer.glMapBuffer(target, access);
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    @NativeType("GLboolean")
    public static boolean glUnmapBuffer(@NativeType("GLenum") int target) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glUnmapBuffer", int.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, target);
        }
        catch (Throwable e) {
            return VkGlBuffer.glUnmapBuffer(target);
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void glDeleteBuffers(int i) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glDeleteBuffers", int.class);
            method.setAccessible(true);
            method.invoke(null, i);
        }
        catch (Throwable e) {
            VkGlBuffer.glDeleteBuffers(i);
        }
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void glDeleteBuffers(@NativeType("GLuint const *") IntBuffer buffers) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlBuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glDeleteBuffers", IntBuffer.class);
            method.setAccessible(true);
            method.invoke(null, buffers);
        }
        catch (Throwable e) {
            VkGlBuffer.glDeleteBuffers(buffers);
        }
    }
}