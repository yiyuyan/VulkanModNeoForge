package net.vulkanmod.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.vulkanmod.Initializer;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class VKNConfig {

    public static boolean forceReapplyGLMixins = false;

    public static boolean useVulkanTransformers = true;

    public static boolean hideVulkanLibs = true;

    static {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("vkn-boot-config.json").toFile();
        try {
            if(!configFile.exists()){
                save(configFile);
            }

            JsonObject object = JsonParser.parseString(FileUtils.readFileToString(configFile,Charset.defaultCharset())).getAsJsonObject();

            forceReapplyGLMixins = object.get("forceReapplyGLMixins").getAsBoolean();
            useVulkanTransformers = object.get("useVulkanTransformers").getAsBoolean();
            hideVulkanLibs = object.get("hideVulkanLibs").getAsBoolean();
        } catch (Throwable e) {
            Initializer.LOGGER.error("Failed to load vkn-boot-configs",e);
        } finally {
            save(configFile);
        }
    }

    private static void save(File configFile){
        try {
            JsonObject object = new JsonObject();
            object.addProperty("forceReapplyGLMixins",forceReapplyGLMixins);
            object.addProperty("useVulkanTransformers",useVulkanTransformers);
            object.addProperty("hideVulkanLibs",hideVulkanLibs);
            FileUtils.writeStringToFile(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(object), Charset.defaultCharset());
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to save vkn-boot-configs.",e);
        }
    }

    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    public static void hide(){
        if(VKNConfig.hideVulkanLibs){
            try {

                ArrayList<ModInfo> modInfos = new ArrayList<>();
                ArrayList<ModFile> modFiles = new ArrayList<>();

                ArrayList<ModContainer> modContainers = new ArrayList<>();

                for (IModFileInfo modFile : ModList.get().getModFiles()) {
                    if(!modFile.getFile().getFilePath().toFile().getParentFile().getName().equals("vulkan-libs") && modFile.getFile() instanceof ModFile modFile1){
                        modFiles.add(modFile1);
                    }
                }

                for (IModInfo info : ModList.get().getMods()) {
                    IModFile modFile = info.getOwningFile().getFile();
                    if(!modFile.getFilePath().toFile().getParentFile().getName().equals("vulkan-libs") && info instanceof ModInfo modInfo){
                        modInfos.add(modInfo);
                    }
                }

                Field modsF = ModList.class.getDeclaredField("mods");
                modsF.setAccessible(true);
                for (ModContainer modContainer : ((List<ModContainer>) modsF.get(ModList.get()))) {
                    if(modInfos.contains((ModInfo) modContainer.getModInfo())) modContainers.add(modContainer);
                }

                ModList.of(modFiles,modInfos);

                Method setLoadedModsM = ModList.class.getDeclaredMethod("setLoadedMods", List.class);
                setLoadedModsM.setAccessible(true);
                setLoadedModsM.invoke(ModList.get(),modContainers);

            } catch (Throwable e) {
                Initializer.LOGGER.warn("Failed to hide Vulkan libs: {}",e.getMessage());
            }
        }
    }
}
