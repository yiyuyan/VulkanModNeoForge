package net.vulkanmod.mixin.wayland;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import net.vulkanmod.config.Platform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow @Final private Window window;
    @Shadow @Final public Options options;

    @Shadow @Final private VanillaPackResources vanillaPackResources;

    @Shadow @Final private PackRepository resourcePackRepository;

    @Mutable
    @Shadow @Final private ReloadableResourceManager resourceManager;

    @Shadow private static Minecraft instance;

    /**
     * @author
     * @reason Only KWin supports setting the Icon on Wayland AFAIK
     */
    @Redirect(method="<init>", at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/platform/Window;setIcon(Lnet/minecraft/server/packs/PackResources;Lcom/mojang/blaze3d/platform/IconSet;)V"))
    private void bypassWaylandIcon(Window instance, PackResources packResources, IconSet iconSet) throws IOException {
        if(!Platform.isWayLand())
        {
            this.window.setIcon(this.vanillaPackResources, SharedConstants.getCurrentVersion().isStable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        }
    }

    @Redirect(method = "<init>",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setWindowActive(Z)V"))
    private void windowsActive(Minecraft instance, boolean bl){
        instance.setWindowActive(bl);
        this.resourceManager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
        ClientModLoader.begin(this.instance, this.resourcePackRepository, this.resourceManager);
    }

    @Redirect(method = "<init>",at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/loading/ClientModLoader;begin(Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/packs/resources/ReloadableResourceManager;)V"))
    private void notBegin(Minecraft e, PackRepository minecraft, ReloadableResourceManager defaultResourcePacks){
    }
}
