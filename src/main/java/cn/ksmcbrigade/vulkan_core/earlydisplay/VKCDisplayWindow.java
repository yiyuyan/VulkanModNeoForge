package cn.ksmcbrigade.vulkan_core.earlydisplay;


import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import org.jetbrains.annotations.Nullable;

public class VKCDisplayWindow extends DisplayWindow {
    @Override
    public void render(int alpha) {

    }

    @Override
    public Runnable initialize(String[] arguments) {
        return ()->{};
    }

    @Override
    public Runnable start(@Nullable String mcVersion, String forgeVersion) {
        return ()->{};
    }

    private void renderThreadFunc() {}
}
