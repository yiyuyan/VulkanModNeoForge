package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8_UNORM;

@Mixin(TextureUtil.class)
public class MTextureUtil {

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static int generateTextureId() {
        RenderSystem.assertOnRenderThreadOrInit();
        return VkGlTexture.genTextureId();
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public static void prepareImage(NativeImage.InternalGlFormat internalGlFormat, int id, int mipLevels, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        VkGlTexture.bindTexture(id);
        VkGlTexture glTexture = VkGlTexture.getBoundTexture();
        VulkanImage image = glTexture.getVulkanImage();

        if (mipLevels > 0) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, mipLevels);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_LOD, 0);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LOD, mipLevels);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_LOD_BIAS, 0.0F);
        }

        if (image == null || image.mipLevels != mipLevels || image.width != width || image.height != height) {
            if (image != null)
                image.free();

            image = new VulkanImage.Builder(width, height)
                    .setName(String.format("Texture %d", id))
                    .setMipLevels(mipLevels + 1)
                    .setFormat(convertFormat(internalGlFormat))
                    .setLinearFiltering(false)
                    .setClamp(false)
                    .createVulkanImage();

            glTexture.setVulkanImage(image);
            VTextureSelector.bindTexture(image);
        }
    }

    @Unique
    private static int convertFormat(NativeImage.InternalGlFormat format) {
        return switch (format) {
            case RGBA -> VK_FORMAT_R8G8B8A8_UNORM;
            case RED -> VK_FORMAT_R8_UNORM;
            default -> throw new IllegalArgumentException(String.format("Unxepcted format: %s", format));
        };
    }
}
