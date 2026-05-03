package net.vulkanmod;

import net.neoforged.fml.ModList;
import net.neoforged.fml.i18n.MavenVersionTranslator;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Initializer {
	public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

	private static String VERSION;
	public static Config CONFIG;

	static {

        Platform.init();
		VideoModeManager.init();

		var configPath = Paths.get("config")
				.resolve("vulkanmod_settings.json");

		CONFIG = loadConfig(configPath);
	}

	public void onInitializeClient() {

		VERSION = MavenVersionTranslator.artifactVersionToString(ModList.get().getModContainerById("vulkanmod")
				.get()
				.getModInfo()
				.getVersion());

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
