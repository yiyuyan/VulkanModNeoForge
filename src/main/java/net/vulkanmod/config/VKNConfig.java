package net.vulkanmod.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
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

    public static boolean hideForgifiedFabricAPIs = true;

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
            hideForgifiedFabricAPIs = object.get("hideForgifiedFabricAPIs").getAsBoolean();
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
            object.addProperty("hideForgifiedFabricAPIs",hideForgifiedFabricAPIs);
            FileUtils.writeStringToFile(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(object), Charset.defaultCharset());
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to save vkn-boot-configs.",e);
        }
    }

    @SuppressWarnings({"unchecked"})
    public static void hide(){
        try {

            ArrayList<ModInfo> modInfos = new ArrayList<>();
            ArrayList<ModFile> modFiles = new ArrayList<>();

            ArrayList<ModContainer> modContainers = new ArrayList<>();

            for (IModFileInfo modFile : ModList.get().getModFiles()) {
                if (NoNeedHide(modFile.getFile().getFilePath().toAbsolutePath().toString(),modFile.getMods().toArray(new IModInfo[0])) && modFile.getFile() instanceof ModFile modFile1) {
                    modFiles.add(modFile1);
                }
            }

            for (IModInfo info : ModList.get().getMods()) {
                IModFile modFile = info.getOwningFile().getFile();
                if (NoNeedHide(modFile.getFilePath().toAbsolutePath().toString(),info) && info instanceof ModInfo modInfo) {
                    modInfos.add(modInfo);
                }
            }

            Field modsF = ModList.class.getDeclaredField("mods");
            modsF.setAccessible(true);
            for (ModContainer modContainer : ((List<ModContainer>) modsF.get(ModList.get()))) {
                if (modInfos.contains((ModInfo) modContainer.getModInfo())){
                    modContainers.add(modContainer);
                }
            }

            ModList.of(modFiles, modInfos);

            Method setLoadedModsM = ModList.class.getDeclaredMethod("setLoadedMods", List.class);
            setLoadedModsM.setAccessible(true);
            setLoadedModsM.invoke(ModList.get(), modContainers);

            Initializer.LOGGER.info("HideMods Successfully!");
        } catch (Throwable e) {
            Initializer.LOGGER.warn("Failed to hide Vulkan libs: {}", e.getMessage());
        }
    }

    private static boolean NoNeedHide(String s, IModInfo... mods){
        boolean result = true;
        if(VKNConfig.hideVulkanLibs) result = !s.contains("vulkan-libs");
        if(VKNConfig.hideForgifiedFabricAPIs){
            for (IModInfo mod : mods) {
                if(mod.getDisplayName().contains("Forgified Fabric")&&mod.getNamespace().contains("fabric_")){
                    result = false;
                    break;
                }
            }
        }
        return result;
    }
}
