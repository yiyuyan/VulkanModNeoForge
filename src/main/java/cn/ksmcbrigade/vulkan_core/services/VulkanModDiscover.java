package cn.ksmcbrigade.vulkan_core.services;

import cn.ksmcbrigade.vulkan_core.VKCUnsafeUtils;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static cn.ksmcbrigade.vulkan_core.VKCUnsafeUtils.coexistenceCoreAndMod;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/9/16 下午7:06
 */
public class VulkanModDiscover implements IModFileCandidateLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VulkanModDiscover.class);

    static {
        LOGGER.info(LogMarkers.CORE,"VulkanTransformationService is Loading...");

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

            LOGGER.info(LogMarkers.CORE,"VulkanTransformationService is Loaded.");
        } catch (IOException e) {
            LOGGER.error(LogMarkers.CORE,"[VulkanCore] Can't close the early window control.",e);
        }
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {

        String os = System.getProperty("os.name");

        LOGGER.info(LogMarkers.SCAN,"VulkanMod Library Discover loading...");
        LOGGER.info(LogMarkers.SCAN, "OS: {}", os);

        os = os.toLowerCase();
        if(os.contains("windows")) os = "windows";
        else if(os.contains("mac")) os = "macos";
        else os = "linux";

        File dir = new File("vulkan-libs");
        File[] files = dir.listFiles();
        if(files==null || files.length<13){
            File file = new File(RandomStringUtils.randomNumeric(8)+"-vulkan-tmp.zip");
            try {
                FileUtils.writeByteArrayToFile(file, IOUtils.toByteArray(Objects.requireNonNull(VulkanModDiscover.class.getResourceAsStream("/vulkan-libs.zip"))));
                try(ZipFile zipFile = new ZipFile(file)){
                    Enumeration<? extends ZipEntry> entryEnumeration = zipFile.entries();
                    while (entryEnumeration.hasMoreElements()){
                        ZipEntry entry = entryEnumeration.nextElement();
                        File jarFile = new File(entry.getName());
                        if(jarFile.isDirectory() || !jarFile.getName().toLowerCase().endsWith(".jar")) continue;
                        FileUtils.writeByteArrayToFile(jarFile,IOUtils.toByteArray(zipFile.getInputStream(entry)));
                    }
                }
            } catch (IOException e) {
                LOGGER.error(LogMarkers.SCAN,"Failed to unzip vulkan libs.",e);
            }
            finally {
                file.delete();
            }
        }

        files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".jar"));
        if(files!=null){
            for (File file : files) {
                String name = file.getName();
                if(name.toLowerCase().contains("natives") && !name.contains(os)) continue;
                pipeline.addPath(file.toPath(),ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ALWAYS);
            }
        }

        if(FMLLoader.getCurrent().isProduction()){
            final Path path = Paths.get(VKCUnsafeUtils.getJarPath(VulkanModDiscover.class));
            coexistenceCoreAndMod(VulkanModDiscover.class);
            try {
                if(path!=null){ // it probably null when using coexistenceCoreAndMod
                    pipeline.addJarContent(JarContents.ofPath(path),ModFileDiscoveryAttributes.DEFAULT,IncompatibleFileReporting.WARN_ALWAYS);
                }
            } catch (IOException e) {
                LOGGER.error(LogMarkers.SCAN,"Failed to coexistence core and mod.",e);
            }
        }

        LOGGER.info(LogMarkers.SCAN,"VulkanMod Library Discover loaded.");
    }


}
