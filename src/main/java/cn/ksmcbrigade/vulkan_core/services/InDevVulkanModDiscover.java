package cn.ksmcbrigade.vulkan_core.services;

import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static cn.ksmcbrigade.vulkan_core.VKCUnsafeUtils.coexistenceCoreAndMod;

public class InDevVulkanModDiscover implements IModFileCandidateLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(InDevVulkanModDiscover.class);

    private final Map<File, VirtualJarManifestEntry> virtualJarMemberIndex = new HashMap<>();

    record VirtualJarManifestEntry(String name, List<File> files) {}

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {

        if(FMLLoader.getCurrent().isProduction()) return;

        coexistenceCoreAndMod(InDevVulkanModDiscover.class);
        loadFromSystemProperty();

        for (var entry : new HashSet<>(virtualJarMemberIndex.values())) {
            var paths = entry.files.stream().map(File::toPath).toList();
                try {
                    pipeline.addJarContent(JarContents.ofPaths(paths), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ALWAYS);
                } catch (IOException e) {
                    pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.brokenfile.invalidzip").withAffectedPath(paths.getFirst()).withCause(e));
                }
        }
    }

    private void loadFromSystemProperty() {
        var modFolders = Optional.ofNullable(System.getenv("MOD_CLASSES"))
                .orElse(System.getProperty("fml.modFolders", ""));
        if (!modFolders.isEmpty()) {
            LOGGER.info(LogMarkers.CORE, "Got mod coordinates {} from env", modFolders);
            // "a/b/;c/d/;" ->"modid%%c:\fish\pepper;modid%%c:\fish2\pepper2\;modid2%%c:\fishy\bums;modid2%%c:\hmm"
            var groupedEntries = Arrays.stream(modFolders.split(File.pathSeparator))
                    .collect(Collectors.groupingBy(
                            inp -> {
                                var splitIdx = inp.indexOf("%%");
                                if (splitIdx != -1) {
                                    return inp.substring(0, splitIdx);
                                } else {
                                    return "defaultmodid";
                                }
                            },
                            Collectors.mapping(inp -> {
                                var splitIdx = inp.indexOf("%%");
                                if (splitIdx != -1) {
                                    inp = inp.substring(splitIdx + "%%".length());
                                }
                                return new File(inp);
                            }, Collectors.toList())));
            for (var group : groupedEntries.entrySet()) {
                var virtualJar = new VirtualJarManifestEntry(
                        group.getKey(),
                        group.getValue());
                for (var file : group.getValue()) {
                    virtualJarMemberIndex.put(file, virtualJar);
                }
            }
        }
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }
}
