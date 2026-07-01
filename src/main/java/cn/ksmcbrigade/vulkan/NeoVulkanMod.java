package cn.ksmcbrigade.vulkan;

import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.VKNConfig;
import net.vulkanmod.config.gui.VOptionScreen;

@Mod(NeoVulkanMod.MOD_ID)
public final class NeoVulkanMod {
    public static final String MOD_ID = "vulkanmod";

    public NeoVulkanMod() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like registries and resources) may still be uninitialized.
        // Proceed with mild caution.
        new Initializer().onInitializeClient();

       ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,()-> new ConfigScreenHandler.ConfigScreenFactory((client,parent)->new VOptionScreen(Component.literal("Video Setting"),parent)));

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onFMLCompleted);
    }

    public void onFMLCompleted(FMLLoadCompleteEvent event) {
        VKNConfig.hide();
    }
}
