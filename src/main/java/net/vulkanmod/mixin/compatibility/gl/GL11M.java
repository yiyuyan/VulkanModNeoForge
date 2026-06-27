package net.vulkanmod.mixin.compatibility.gl;

import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(GL11.class)
public class GL11M {

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static int glGetTexParameteri(@NativeType("GLenum") int target, @NativeType("GLenum") int pname) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("getTexParameteri", int.class, int.class);
            method.setAccessible(true);
            return (int) method.invoke(null, target, pname);
        }
        catch (Throwable e) {
            return VkGlTexture.getTexParameteri(target, pname);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glPixelStorei(@NativeType("GLenum") int pname, @NativeType("GLint") int param) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("pixelStoreI", int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, pname, param);
        }
        catch (Throwable e) {
            VkGlTexture.pixelStoreI(pname, param);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glBlendFunc(@NativeType("GLenum") int sfactor, @NativeType("GLenum") int dfactor) {
        // TODO
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glPolygonOffset(@NativeType("GLfloat") float factor, @NativeType("GLfloat") float units) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.VRenderSystem", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("polygonOffset", float.class, float.class);
            method.setAccessible(true);
            method.invoke(null, factor, units);
        }
        catch (Throwable e) {
            VRenderSystem.polygonOffset(factor, units);
        }
    }

    /**
     * @author
     * @reason ideally Scissor should be used. but using vkCmdSetScissor() caused glitches with invisible menus with replay mod, so disabled for now as temp fix
     */
    @Overwrite(remap = false)
    public static void glScissor(@NativeType("GLint") int x, @NativeType("GLint") int y, @NativeType("GLsizei") int width, @NativeType("GLsizei") int height) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.Renderer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("setScissor", int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, x, y, width, height);
        }
        catch (Throwable e) {
            Renderer.setScissor(x, y, width, height);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glViewport(@NativeType("GLint") int x, @NativeType("GLint") int y, @NativeType("GLsizei") int w, @NativeType("GLsizei") int h) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.Renderer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("setViewport", int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, x, y, w, h);
        }
        catch (Throwable e) {
            Renderer.setViewport(x, y, w, h);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glBindTexture(@NativeType("GLenum") int target, @NativeType("GLuint") int texture) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("bindTexture", int.class);
            method.setAccessible(true);
            method.invoke(null, texture);
        }
        catch (Throwable e) {
            VkGlTexture.bindTexture(texture);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glLineWidth(@NativeType("GLfloat") float width) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.VRenderSystem", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("setLineWidth", float.class);
            method.setAccessible(true);
            method.invoke(null, width);
        }
        catch (Throwable e) {
            VRenderSystem.setLineWidth(width);
        }
    }

    /**
     * @author
     * @reason
     */
    @NativeType("void")
    @Overwrite(remap = false)
    public static int glGenTextures() {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("genTextureId");
            method.setAccessible(true);
            return (int) method.invoke(null);
        }
        catch (Throwable e) {
            return VkGlTexture.genTextureId();
        }
    }

    /**
     * @author
     * @reason
     */
    @NativeType("GLboolean")
    @Overwrite(remap = false)
    public static boolean glIsEnabled(@NativeType("GLenum") int cap) {
        return true;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glClear(@NativeType("GLbitfield") int mask) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.VRenderSystem", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("clear", int.class);
            method.setAccessible(true);
            method.invoke(null, mask);
        }
        catch (Throwable e) {
            VRenderSystem.clear(mask);
        }
    }

    /**
     * @author
     * @reason
     */
    @NativeType("GLenum")
    @Overwrite(remap = false)
    public static int glGetError() {
        return 0;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glClearColor(@NativeType("GLfloat") float red, @NativeType("GLfloat") float green, @NativeType("GLfloat") float blue, @NativeType("GLfloat") float alpha) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.VRenderSystem", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("setClearColor", float.class, float.class, float.class, float.class);
            method.setAccessible(true);
            method.invoke(null, red, green, blue, alpha);
        }
        catch (Throwable e) {
            VRenderSystem.setClearColor(red, green, blue, alpha);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glDepthMask(@NativeType("GLboolean") boolean flag) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.VRenderSystem", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("depthMask", boolean.class);
            method.setAccessible(true);
            method.invoke(null, flag);
        }
        catch (Throwable e) {
            VRenderSystem.depthMask(flag);
        }
    }

    /**
     * @author
     * @reason
     */
    @NativeType("void")
    @Overwrite(remap = false)
    public static int glGetInteger(@NativeType("GLenum") int pname) {
        return 0;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("texImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class);
            method.setAccessible(true);
            method.invoke(null, target, level, internalformat, width, height, border, format, type, pixels);
        }
        catch (Throwable e) {
            VkGlTexture.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glTexImage2D(@NativeType("GLenum") int target, @NativeType("GLint") int level, @NativeType("GLint") int internalformat, @NativeType("GLsizei") int width, @NativeType("GLsizei") int height, @NativeType("GLint") int border, @NativeType("GLenum") int format, @NativeType("GLenum") int type, @NativeType("void const *") long pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("texImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, long.class);
            method.setAccessible(true);
            method.invoke(null, target, level, internalformat, width, height, border, format, type, pixels);
        }
        catch (Throwable e) {
            VkGlTexture.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, long pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("texSubImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, long.class);
            method.setAccessible(true);
            method.invoke(null, target, level, xOffset, yOffset, width, height, format, type, pixels);
        }
        catch (Throwable e) {
            VkGlTexture.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, @Nullable ByteBuffer pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("texSubImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class);
            method.setAccessible(true);
            method.invoke(null, target, level, xOffset, yOffset, width, height, format, type, pixels);
        }
        catch (Throwable e) {
            VkGlTexture.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, @Nullable IntBuffer pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("texSubImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, IntBuffer.class);
            method.setAccessible(true);
            method.invoke(null, target, level, xOffset, yOffset, width, height, format, type, pixels);
        }
        catch (Throwable e) {
            VkGlTexture.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, MemoryUtil.memByteBuffer(pixels));
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glTexParameteri(@NativeType("GLenum") int target, @NativeType("GLenum") int pname, @NativeType("GLint") int param) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("texParameteri", int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, target, pname, param);
        }
        catch (Throwable e) {
            VkGlTexture.texParameteri(target, pname, param);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glTexParameterf(@NativeType("GLenum") int target, @NativeType("GLenum") int pname, @NativeType("GLfloat") float param) {

    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static int glGetTexLevelParameteri(@NativeType("GLenum") int target, @NativeType("GLint") int level, @NativeType("GLenum") int pname) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("getTexLevelParameter", int.class, int.class, int.class);
            method.setAccessible(true);
            return (int) method.invoke(null, target, level, pname);
        }
        catch (Throwable e) {
            return VkGlTexture.getTexLevelParameter(target, level, pname);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glEnable(@NativeType("GLenum") int target) {

    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glDisable(@NativeType("GLenum") int target) {
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glFinish() {
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glHint(@NativeType("GLenum") int target, @NativeType("GLenum") int hint) {
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glDeleteTextures(@NativeType("GLuint const *") int texture) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glDeleteTextures", int.class);
            method.setAccessible(true);
            method.invoke(null, texture);
        }
        catch (Throwable e) {
            VkGlTexture.glDeleteTextures(texture);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glDeleteTextures(@NativeType("GLuint const *") IntBuffer textures) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glDeleteTextures", IntBuffer.class);
            method.setAccessible(true);
            method.invoke(null, textures);
        }
        catch (Throwable e) {
            VkGlTexture.glDeleteTextures(textures);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glGetTexImage(@NativeType("GLenum") int tex, @NativeType("GLint") int level, @NativeType("GLenum") int format, @NativeType("GLenum") int type, @NativeType("void *") long pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("getTexImage", int.class, int.class, int.class, int.class, long.class);
            method.setAccessible(true);
            method.invoke(null, tex, level, format, type, pixels);
        }
        catch (Throwable e) {
            VkGlTexture.getTexImage(tex, level, format, type, pixels);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glGetTexImage(@NativeType("GLenum") int tex, @NativeType("GLint") int level, @NativeType("GLenum") int format, @NativeType("GLenum") int type, @NativeType("void *") ByteBuffer pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("getTexImage", int.class, int.class, int.class, int.class, long.class);
            method.setAccessible(true);
            method.invoke(null, tex, level, format, type, MemoryUtil.memAddress(pixels));
        }
        catch (Throwable e) {
            VkGlTexture.getTexImage(tex, level, format, type, MemoryUtil.memAddress(pixels));
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glGetTexImage(@NativeType("GLenum") int tex, @NativeType("GLint") int level, @NativeType("GLenum") int format, @NativeType("GLenum") int type, @NativeType("void *") IntBuffer pixels) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.VkGlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("getTexImage", int.class, int.class, int.class, int.class, long.class);
            method.setAccessible(true);
            method.invoke(null, tex, level, format, type, MemoryUtil.memAddress(pixels));
        }
        catch (Throwable e) {
            VkGlTexture.getTexImage(tex, level, format, type, MemoryUtil.memAddress(pixels));
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glCopyTexSubImage2D(@NativeType("GLenum") int target, @NativeType("GLint") int level, @NativeType("GLint") int xoffset, @NativeType("GLint") int yoffset, @NativeType("GLint") int x, @NativeType("GLint") int y, @NativeType("GLsizei") int width, @NativeType("GLsizei") int height) {
        // TODO
    }
}