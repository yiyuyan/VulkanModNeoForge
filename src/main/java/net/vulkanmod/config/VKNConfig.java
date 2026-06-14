package net.vulkanmod.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import net.vulkanmod.Initializer;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.Charset;

public class VKNConfig {

    public static boolean forceReapplyGLMixins = false;

    public static boolean useVulkanTransformers = true;

    static {
        try {
            File configFile = FMLPaths.CONFIGDIR.get().resolve("vkn-boot-config.json").toFile();
            if(!configFile.exists()){
                JsonObject object = new JsonObject();
                object.addProperty("forceReapplyGLMixins",false);
                object.addProperty("useVulkanTransformers",true);
                FileUtils.writeStringToFile(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(object), Charset.defaultCharset());
            }

            JsonObject object = JsonParser.parseString(FileUtils.readFileToString(configFile,Charset.defaultCharset())).getAsJsonObject();

            forceReapplyGLMixins = object.get("forceReapplyGLMixins").getAsBoolean();
            useVulkanTransformers = object.get("useVulkanTransformers").getAsBoolean();
        } catch (Throwable e) {
            Initializer.LOGGER.error("Failed to load vkn-boot-configs",e);
        }
    }
}
