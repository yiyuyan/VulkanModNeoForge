package net.vulkanmod.mixin.compatibility.gl;

import net.vulkanmod.gl.GlFramebuffer;
import net.vulkanmod.gl.GlRenderbuffer;
import net.vulkanmod.gl.GlTexture;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Method;

@Mixin(GL30.class)
public class GL30M {

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glBlitFramebuffer(@NativeType("GLint") int srcX0, @NativeType("GLint") int srcY0, @NativeType("GLint") int srcX1, @NativeType("GLint") int srcY1, @NativeType("GLint") int dstX0, @NativeType("GLint") int dstY0, @NativeType("GLint") int dstX1, @NativeType("GLint") int dstY1, @NativeType("GLbitfield") int mask, @NativeType("GLenum") int filter) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlFramebuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glBlitFramebuffer", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
        }
        catch (Throwable e) {
            GlFramebuffer.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glGenerateMipmap(@NativeType("GLenum") int target) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlTexture", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("generateMipmap", int.class);
            method.setAccessible(true);
            method.invoke(null, target);
        }
        catch (Throwable e) {
            GlTexture.generateMipmap(target);
        }
    }

    /**
     * @author
     * @reason
     */
    @NativeType("void")
    @Overwrite(remap = false)
    public static int glGenFramebuffers() {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlFramebuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("genFramebufferId");
            method.setAccessible(true);
            return (int) method.invoke(null);
        }
        catch (Throwable e) {
            return GlFramebuffer.genFramebufferId();
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glBindFramebuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int framebuffer) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlFramebuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("bindFramebuffer", int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, target, framebuffer);
        }
        catch (Throwable e) {
            GlFramebuffer.bindFramebuffer(target, framebuffer);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glFramebufferTexture2D(@NativeType("GLenum") int target, @NativeType("GLenum") int attachment, @NativeType("GLenum") int textarget, @NativeType("GLuint") int texture, @NativeType("GLint") int level) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlFramebuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("framebufferTexture2D", int.class, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, target, attachment, textarget, texture, level);
        }
        catch (Throwable e) {
            GlFramebuffer.framebufferTexture2D(target, attachment, textarget, texture, level);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glFramebufferRenderbuffer(@NativeType("GLenum") int target, @NativeType("GLenum") int attachment, @NativeType("GLenum") int renderbuffertarget, @NativeType("GLuint") int renderbuffer) {
//        GL30C.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glDeleteFramebuffers(@NativeType("GLuint const *") int framebuffer) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlFramebuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("deleteFramebuffer", int.class);
            method.setAccessible(true);
            method.invoke(null, framebuffer);
        }
        catch (Throwable e) {
            GlFramebuffer.deleteFramebuffer(framebuffer);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    @NativeType("GLenum")
    public static int glCheckFramebufferStatus(@NativeType("GLenum") int target) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlFramebuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("glCheckFramebufferStatus", int.class);
            method.setAccessible(true);
            return (int) method.invoke(null, target);
        }
        catch (Throwable e) {
            return GlFramebuffer.glCheckFramebufferStatus(target);
        }
    }

    //RENDER BUFFER

    /**
     * @author
     * @reason
     */
    @NativeType("void")
    @Overwrite(remap = false)
    public static int glGenRenderbuffers() {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlRenderbuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("genId");
            method.setAccessible(true);
            return (int) method.invoke(null);
        }
        catch (Throwable e) {
            return GlRenderbuffer.genId();
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glBindRenderbuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int framebuffer) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlRenderbuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("bindRenderbuffer", int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, target, framebuffer);
        }
        catch (Throwable e) {
            GlRenderbuffer.bindRenderbuffer(target, framebuffer);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glRenderbufferStorage(@NativeType("GLenum") int target, @NativeType("GLenum") int internalformat, @NativeType("GLsizei") int width, @NativeType("GLsizei") int height) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlRenderbuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("renderbufferStorage", int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, target, internalformat, width, height);
        }
        catch (Throwable e) {
            GlRenderbuffer.renderbufferStorage(target, internalformat, width, height);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glDeleteRenderbuffers(@NativeType("GLuint const *") int renderbuffer) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.gl.GlRenderbuffer", false, Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("deleteRenderbuffer", int.class);
            method.setAccessible(true);
            method.invoke(null, renderbuffer);
        }
        catch (Throwable e) {
            GlRenderbuffer.deleteRenderbuffer(renderbuffer);
        }
    }
}