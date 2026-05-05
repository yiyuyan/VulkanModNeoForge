package net.vulkanmod;

import net.neoforged.fml.ModList;
import net.neoforged.fml.i18n.MavenVersionTranslator;
import net.neoforged.fml.loading.FMLPaths;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//import net.fabricmc.fabric.api.renderer.v1.Renderer;

import java.nio.file.Path;

public class Initializer {
	public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

	private static String VERSION;
	public static Config CONFIG;

	public void onInitializeClient() {

		VERSION = MavenVersionTranslator.artifactVersionToString(ModList.get().getModContainerById("vulkanmod").get().getModInfo().getVersion());
		LOGGER.info("== VulkanMod ==");

		Platform.init();

		var configPath = FMLPaths.CONFIGDIR.get()
				.resolve("vulkanmod_settings.json");

		CONFIG = loadConfig(configPath);
	}

	private static Config loadConfig(Path path) {
        return Config.load(path);
	}

	public static String getVersion() {
		return VERSION;
	}
}
