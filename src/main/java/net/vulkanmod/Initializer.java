package net.vulkanmod;

import cn.ksmcbrigade.mr.utils.mixin.MixinUtils;
import net.neoforged.fml.ModList;
import net.neoforged.fml.i18n.MavenVersionTranslator;
import net.neoforged.fml.loading.FMLPaths;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.mixin.compatibility.gl.GL11M;
import net.vulkanmod.mixin.compatibility.gl.GL14M;
import net.vulkanmod.mixin.compatibility.gl.GL15M;
import net.vulkanmod.mixin.compatibility.gl.GL30M;
import net.vulkanmod.mixin.matrix.Matrix4fM;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Initializer {
	public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

	private static String VERSION;
	public static Config CONFIG;

	public void onInitializeClient() {

		VERSION = MavenVersionTranslator.artifactVersionToString(ModList.get().getModContainerById("vulkanmod").get().getModInfo().getVersion());
		LOGGER.info("== VulkanMod ==");

		Platform.init();

		/*  With the help of MixinRuntime,we don't need to reapply mixins by hand

		LOGGER.info("Reapply mixins...");
		MixinUtils.reapply(GL11M.class);
		MixinUtils.reapply(GL14M.class);
		MixinUtils.reapply(GL15M.class);
		MixinUtils.reapply(GL30M.class);
		MixinUtils.reapply(Matrix4fM.class);

		*/

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
