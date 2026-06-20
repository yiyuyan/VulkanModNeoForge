package net.vulkanmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.config.video.VideoModeSet;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class Config {

    public int frameQueueSize = 2;
    public VideoModeSet.VideoMode videoMode = VideoModeManager.getFirstAvailable().getVideoMode();
    public boolean windowedFullscreen = false;

    public int advCulling = 2;
    public boolean indirectDraw = false;
    public boolean gpuCulling = true;
    // Hi-Z occlusion culling. Requires gpuCulling + device support. Default OFF: the GPU
    // wiring (pyramid build + occlusion test) is still pending in-game validation under
    // Vulkan validation layers, so it ships opt-in to keep the default render path unchanged.
    public boolean occlusionCulling = false;

    public boolean uniqueOpaqueLayer = true;
    public boolean entityCulling = true;
    public int device = -1;

    public int ambientOcclusion = 1;

    public void write() {

        if(!Files.exists(CONFIG_PATH.getParent())) {
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
            } catch (IOException e) {
                Initializer.LOGGER.error("Failed to create config directory", e);
            }
        }

        try {
            Files.write(CONFIG_PATH, Collections.singleton(GSON.toJson(this)));
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to write config file", e);
        }
    }

    private static Path CONFIG_PATH;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static Config load(Path path) {
        Config config;
        Config.CONFIG_PATH = path;

        if (Files.exists(path)) {
            try (FileReader fileReader = new FileReader(path.toFile())) {
                config = GSON.fromJson(fileReader, Config.class);
                if (config != null && config.advCulling < 1)
                    config.advCulling = 1;
            }
            catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
        else {
            config = null;
        }

        return config;
    }
}
