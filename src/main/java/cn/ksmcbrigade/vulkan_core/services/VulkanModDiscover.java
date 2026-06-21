package cn.ksmcbrigade.vulkan_core.services;

import cn.ksmcbrigade.vulkan_core.earlydisplay.VKCDisplayWindow;
import cn.ksmcbrigade.vulkan_core.VKCUnsafeUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.lwjgl.glfw.GLFW.*;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/9/16 下午7:06
 */
public class VulkanModDiscover implements IModFileCandidateLocator {

    static {
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

            if(earlyDisplay && directExit()){
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
            else if(earlyDisplay){
                ImmediateWindowProvider provider = new ImmediateWindowProvider() {

                    private static Method NV_HANDOFF;
                    private static Method NV_POSITION;
                    private static Method NV_OVERLAY;
                    private static Method NV_VERSION;

                    @Override
                    public String name() {
                        return "vkn_window_provider";
                    }

                    @Override
                    public Runnable initialize(String[] args) {
                        return () -> {};
                    }

                    @Override
                    public void updateFramebufferSize(final IntConsumer width, final IntConsumer height) {}

                    @Override
                    public long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitor) {
                        try {
                            var longsupplier = (LongSupplier) NV_HANDOFF.invoke(null, width, height, title, monitor);
                            return longsupplier.getAsLong();
                        } catch (Throwable e) {
                            throw new IllegalStateException("How did you get here?", e);
                        }
                    }

                    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
                        try {
                            return (boolean) NV_POSITION.invoke(null, monitor, widthSetter, heightSetter, xSetter, ySetter);
                        } catch (Throwable e) {
                            throw new IllegalStateException("How did you get here?", e);
                        }
                    }

                    @SuppressWarnings("unchecked")
                    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
                        try {
                            return (Supplier<T>) NV_OVERLAY.invoke(null, mc, ri, ex, fade);
                        } catch (Throwable e) {
                            throw new IllegalStateException("How did you get here?", e);
                        }
                    }

                    @Override
                    public String getGLVersion() {
                        try {
                            return (String) NV_VERSION.invoke(null);
                        } catch (Throwable e) {
                            return "3.2"; // Vanilla sets 3.2 in com.mojang.blaze3d.platform.Window
                        }
                    }

                    @Override
                    public void updateModuleReads(final ModuleLayer layer) {
                        var fm = layer.findModule("neoforge");
                        if (fm.isPresent()) {
                            getClass().getModule().addReads(fm.get());
                            var clz = fm.map(l -> Class.forName(l, "net.neoforged.neoforge.client.loading.NoVizFallback")).orElseThrow();
                            var methods = Arrays.stream(clz.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers())).collect(Collectors.toMap(Method::getName, Function.identity()));
                            NV_HANDOFF = methods.get("windowHandoff");
                            NV_OVERLAY = methods.get("loadingOverlay");
                            NV_POSITION = methods.get("windowPositioning");
                            NV_VERSION = methods.get("glVersion");
                        }
                    }

                    @Override
                    public void periodicTick() {
                        // NOOP
                    }

                    @Override
                    public void crash(final String message) {
                        // NOOP for unsupported environments
                    }
                };

                final Object current = VKCUnsafeUtils.getFieldValue(ImmediateWindowHandler.class,"provider", Object.class);
                FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL,false);
                try {
                    Field f = ImmediateWindowHandler.class.getDeclaredField("provider");
                    f.setAccessible(true);
                    f.set(null,provider);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    e.printStackTrace();
                }
                FMLLoader.progressWindowTick = () -> {};
                if(current instanceof DisplayWindow displayWindow){
                    try {
                        long window;
                        Field field = displayWindow.getClass().getDeclaredField("window");
                        field.setAccessible(true);
                        window = (long) field.get(displayWindow);
                        Field windowTickF = displayWindow.getClass().getDeclaredField("windowTick");
                        windowTickF.setAccessible(true);
                        windowTickF.set(displayWindow,new ScheduledFuture<>(){
                            @Override
                            public boolean cancel(boolean mayInterruptIfRunning) {
                                return false;
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }

                            @Override
                            public boolean isDone() {
                                return false;
                            }

                            @Override
                            public Object get() throws InterruptedException, ExecutionException {
                                return null;
                            }

                            @Override
                            public Object get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                                return null;
                            }

                            @Override
                            public int compareTo(@NotNull Delayed o) {
                                return 0;
                            }

                            @Override
                            public long getDelay(@NotNull TimeUnit unit) {
                                return 0;
                            }
                        });
                        Field repaintTickF = DisplayWindow.class.getDeclaredField("repaintTick");
                        repaintTickF.setAccessible(true);
                        repaintTickF.set(displayWindow, (Runnable) () -> {
                        });
                        Field renderLockF = DisplayWindow.class.getDeclaredField("renderLock");
                        renderLockF.setAccessible(true);
                        renderLockF.set(displayWindow,new Semaphore(1){
                            @Override
                            public boolean tryAcquire() {
                                return false;
                            }
                        });
                        LogUtils.getLogger().info("Get current window: {}",window);
                        glfwDestroyWindow(window);
                        VKCUnsafeUtils.setClass(displayWindow, VKCDisplayWindow.class);

                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }

                }


                LogUtils.getLogger().info("Replaced the window handler's provider runtime.");
            }

            LogUtils.getLogger().info(LogMarkers.CORE,"VulkanTransformationService is Loaded.");
        } catch (IOException e) {
            LogUtils.getLogger().info(LogMarkers.CORE,"[VulkanCore] Can't close the early window control.");
            e.printStackTrace();
        }
    }

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

    private static boolean directExit(){
        try {
            File file = new File("config/vkn-early-config.json");
            if(!file.exists()){
                JsonObject obj = new JsonObject();
                obj.addProperty("directExit",false);
                FileUtils.writeStringToFile(file,obj.toString());
            }
            JsonObject obj = JsonParser.parseString(FileUtils.readFileToString(file)).getAsJsonObject();
            return obj.get("directExit").getAsBoolean();
        } catch (Throwable e) {
            e.printStackTrace();
            return true;
        }
    }
}
