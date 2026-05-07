package cn.ksmcbrigade.vulkan_core.services;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.*;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.LogMarkers;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/9/20 下午5:21
 */
public class VulkanTransformationService implements ITransformationService {

    @Override
    public @NotNull String name() {
        return "vulkan_transformer_loader";
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
        LogUtils.getLogger().info(LogMarkers.CORE,"VulkanTransformationService is Loading...");

        final boolean earlyDisplay = FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL);

        try {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL,false);
            File file = new File("config/fml.toml");
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL,false);
            if(!file.exists()){
                FileUtils.writeStringToFile(file, """
                        #Early window height
                        earlyWindowHeight = 480
                        #Enable NeoForge global version checking
                        versionCheck = true
                        #Should we control the window. Disabling this disables new GL features and can be bad for mods that rely on them.
                        earlyWindowControl = false
                        #Early window framebuffer scale
                        earlyWindowFBScale = 1
                        #Disables File Watcher. Used to automatically update config if its file has been modified.
                        disableConfigWatcher = false
                        #Early window provider
                        earlyWindowProvider = "fmlearlywindow"
                        #Early window width
                        earlyWindowWidth = 854
                        #Early window starts maximized
                        earlyWindowMaximized = false
                        #Default config path for servers
                        defaultConfigPath = "defaultconfigs"
                        #Disables Optimized DFU client-side - already disabled on servers
                        disableOptimizedDFU = true
                        #Skip specific GL versions, may help with buggy graphics card drivers
                        earlyWindowSkipGLVersions = []
                        #Max threads for early initialization parallelism,  -1 is based on processor count
                        maxThreads = -1
                        #Squir?
                        earlyWindowSquir = false
                                            
                        """);
            }
            else{
                StringBuilder builder = new StringBuilder();
                for (String string : FileUtils.readFileToString(file).split("\n")) {
                    if(string.startsWith("earlyWindowControl")){
                        builder.append(string.replace("true","false"));
                    }
                    else{
                        builder.append(string);
                    }
                    builder.append("\n");
                }
                FileUtils.writeStringToFile(file,builder.toString());
            }

            if(earlyDisplay){
                JOptionPane.showMessageDialog(
                        null,
                        "The earlyWindowControl has been turned off.\nPlease restart the game.",
                            "VulkanModNeoForge",
                        JOptionPane.WARNING_MESSAGE
                        );
                LogUtils.getLogger().info(LogMarkers.CORE,"The earlyWindowControl has been turned.Please restart the game.");
                LogUtils.getLogger().warn(LogMarkers.CORE,"Exiting...");
                System.exit(0);
            }

            LogUtils.getLogger().info(LogMarkers.CORE,"VulkanTransformationService is Loaded.");
        } catch (IOException e) {
            LogUtils.getLogger().info(LogMarkers.CORE,"[VulkanCore] Can't close the early window control.");
            e.printStackTrace();
        }
    }

    @Override
    public @NotNull List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
