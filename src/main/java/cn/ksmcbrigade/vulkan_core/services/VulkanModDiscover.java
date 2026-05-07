package cn.ksmcbrigade.vulkan_core.services;

import cn.ksmcbrigade.vulkan_core.VKCUnsafeUtils;
import com.mojang.logging.LogUtils;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/9/16 下午7:06
 */
public class VulkanModDiscover implements IModFileCandidateLocator {

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {

        String os = System.getProperty("os.name");

        LogUtils.getLogger().info(LogMarkers.SCAN,"VulkanMod Library Discover loading...");
        LogUtils.getLogger().info(LogMarkers.SCAN,"OS: "+os);

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
                throw new RuntimeException(e);
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

        this.coexistenceCoreAndMod(context);
    }

    public void coexistenceCoreAndMod(ILaunchContext context){
        Set<Path> located = new HashSet<>();
        for (Path locatedPaths : (Set<Path>)Objects.requireNonNull(VKCUnsafeUtils.getFieldValue(context, "locatedPaths", Set.class))) {
            if(!VKCUnsafeUtils.getJarPath(VulkanModDiscover.class).equals(locatedPaths.toString())){
                if(!FMLLoader.isProduction() && locatedPaths.toFile().isDirectory() && locatedPaths.toString().contains("VulkanModNeoForge")){
                    //ignore
                }
                else if(!locatedPaths.toFile().getParentFile().getName().equals("mods")){
                    located.add(locatedPaths);
                }
            }
        }
        located.remove(Path.of(VKCUnsafeUtils.getJarPath(VulkanModDiscover.class)));
        VKCUnsafeUtils.coexistenceCoreAndMod();
        VKCUnsafeUtils.setFieldValue(context,"locatedPaths",located);

        //LogUtils.getLogger().info(LogMarkers.SCAN,Arrays.toString(VKCUnsafeUtils.getFieldValue(context, "locatedPaths", Set.class).toArray()));
    }
}
