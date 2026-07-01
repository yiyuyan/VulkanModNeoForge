package net.vulkanmod;

import cn.ksmcbrigade.vulkan_core.VKCUnsafeUtils;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.impl.renderer.RendererAccessImpl;
import net.minecraftforge.common.util.MavenVersionStringHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;

public class Initializer {
	public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

	private static String VERSION;
	public static Config CONFIG;

	static {

        Platform.init();
		VideoModeManager.init();

		var configPath = FMLPaths.CONFIGDIR.get()
				.resolve("vulkanmod_settings.json");

		CONFIG = loadConfig(configPath);

		if(RendererAccess.INSTANCE.getRenderer() != null && RendererAccess.INSTANCE instanceof RendererAccessImpl rendererAccess) {
            try {
                VKCUnsafeUtils.setFieldValue(rendererAccess, "activeRenderer", null);
            } catch (Exception e) {
                try {
                    Field field = RendererAccessImpl.class.getDeclaredField("activeRenderer");
                    field.setAccessible(true);
                    field.set(rendererAccess,null);
                } catch (NoSuchFieldException | IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }

        }
		RendererAccess.INSTANCE.registerRenderer(VulkanModRenderer.INSTANCE);
	}

	@SuppressWarnings("OptionalGetWithoutIsPresent")
    public void onInitializeClient() {

        try {
            VERSION = MavenVersionStringHelper.artifactVersionToString(ModList.get().getModContainerById("vulkanmod")
                    .get()
                    .getModInfo()
                    .getVersion());
        } catch (Exception e) {
            VERSION = "0.5.0-dev";

			LOGGER.warn("Failed to get the version: {}",e.getMessage());
        }

        LOGGER.info("== VulkanMod ==");
	}

	private static Config loadConfig(Path path) {
		Config config = Config.load(path);

		if(config == null) {
			config = new Config();
			config.write();
		}

		return config;
	}

	public static String getVersion() {
		return VERSION;
	}
}
