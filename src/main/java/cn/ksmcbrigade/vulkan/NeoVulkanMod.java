package cn.ksmcbrigade.vulkan;

import net.neoforged.fml.common.Mod;
import net.vulkanmod.Initializer;

@Mod(NeoVulkanMod.MOD_ID)
public final class NeoVulkanMod {
    public static final String MOD_ID = "vulkanmod";

    public NeoVulkanMod() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like registries and resources) may still be uninitialized.
        // Proceed with mild caution.
        new Initializer().onInitializeClient();
    }
}
