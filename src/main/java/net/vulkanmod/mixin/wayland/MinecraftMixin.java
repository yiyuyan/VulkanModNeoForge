package net.vulkanmod.mixin.wayland;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.vulkanmod.config.Platform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow @Final private Window window;

    @Shadow @Final private VanillaPackResources vanillaPackResources;

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

    // REMOVED: the `windowsActive`/`notBegin` redirects that moved NeoForge's
    // ClientModLoader.begin(...) earlier and recreated the ReloadableResourceManager.
    // That ran unconditionally (not just on Wayland) and broke mod resource-reload
    // listener registration (RegisterClientReloadListenersEvent) for EVERY mod — e.g.
    // Cobblemon's data-registry listener never ran, so its stores were empty and the
    // player crashed with "Invalid player data" on world-join. Letting vanilla call
    // ClientModLoader.begin at its normal point restores correct mod compatibility.
}
