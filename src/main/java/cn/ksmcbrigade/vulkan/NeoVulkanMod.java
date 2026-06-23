package cn.ksmcbrigade.vulkan;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.VKNConfig;
import net.vulkanmod.config.gui.VOptionScreen;

@Mod(NeoVulkanMod.MOD_ID)
@EventBusSubscriber(modid = NeoVulkanMod.MOD_ID)
public final class NeoVulkanMod {
    public static final String MOD_ID = "vulkanmod";

    public NeoVulkanMod() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like registries and resources) may still be uninitialized.
        // Proceed with mild caution.
        new Initializer().onInitializeClient();

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,()-> (IConfigScreenFactory) (modContainer, arg) -> new VOptionScreen(Component.literal("Video Setting"),arg));
    }

    @SubscribeEvent
    public static void onFMLCompleted(FMLLoadCompleteEvent event) {
        VKNConfig.hide();
    }
}
