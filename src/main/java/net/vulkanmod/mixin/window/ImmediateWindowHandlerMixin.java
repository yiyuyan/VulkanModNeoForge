package net.vulkanmod.mixin.window;

import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import net.vulkanmod.config.Platform;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;

@Mixin(value = ImmediateWindowHandler.class,remap = false)
public class ImmediateWindowHandlerMixin {
    @Inject(method = "setupMinecraftWindow",at = @At("HEAD"))
    private static void setup(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor, CallbackInfoReturnable<Long> cir){
        GLFW.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

        //Fix Gnome Client-Side Decorators
        boolean b = (Platform.isGnome() | Platform.isWeston() | Platform.isGeneric()) && Platform.isWayLand();
        GLFW.glfwWindowHint(GLFW_DECORATED, (b ? GLFW_FALSE : GLFW_TRUE));
    }
}
