package net.vulkanmod.mixin.compatibility.gl;

import net.vulkanmod.vulkan.VRenderSystem;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Method;

@Mixin(GL14.class)
public class GL14M {

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    public static void glBlendFuncSeparate(@NativeType("GLenum") int sfactorRGB, @NativeType("GLenum") int dfactorRGB, @NativeType("GLenum") int sfactorAlpha, @NativeType("GLenum") int dfactorAlpha) {
        try {
            Class<?> clazz = Class.forName("net.vulkanmod.vulkan.VRenderSystem",false,Thread.currentThread().getContextClassLoader());
            Method method = clazz.getMethod("blendFuncSeparate", int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null,sfactorRGB,dfactorRGB,sfactorAlpha,dfactorAlpha);
        }
        catch (Throwable e){
            VRenderSystem.blendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
        }
    }
}
