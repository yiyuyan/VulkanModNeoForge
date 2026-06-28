package net.vulkanmod.config.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.List;

public abstract class GuiRenderer {

    public static Minecraft minecraft;
    public static Font font;
    public static GuiGraphics guiGraphics;
    public static PoseStack pose;

    public static BufferBuilder bufferBuilder;
    private static boolean batching = false;
    private static boolean drawing = false;

    public static void setPoseStack(PoseStack poseStack) {
        pose = poseStack;
    }

    public static void disableScissor() {
        RenderSystem.disableScissor();
    }

    public static void enableScissor(int x, int y, int width, int height) {
        Window window = Minecraft.getInstance().getWindow();
        int wHeight = window.getHeight();
        double scale = window.getGuiScale();
        int xScaled = (int) (x * scale);
        int yScaled = (int) (wHeight - (y + height) * scale);
        int widthScaled = (int) (width * scale);
        int heightScaled = (int) (height * scale);
        RenderSystem.enableScissor(xScaled, yScaled, Math.max(0, widthScaled), Math.max(0, heightScaled));
    }

    public static void fillBox(float x0, float y0, float width, float height, int color) {
        fill(x0, y0, x0 + width, y0 + height, 0, color);
    }

    public static void fill(float x0, float y0, float x1, float y1, int color) {
        fill(x0, y0, x1, y1, 0, color);
    }

    public static void fill(float x0, float y0, float x1, float y1, float z, int color) {
        Matrix4f matrix4f = pose.last().pose();

        RenderSystem.setShader(CoreShaders.POSITION_COLOR);

        setupBufferBuilder();

        bufferBuilder.addVertex(matrix4f, x0, y0, z).setColor(color);
        bufferBuilder.addVertex(matrix4f, x0, y1, z).setColor(color);
        bufferBuilder.addVertex(matrix4f, x1, y1, z).setColor(color);
        bufferBuilder.addVertex(matrix4f, x1, y0, z).setColor(color);

        submitIfNeeded();
    }

    public static void fillGradient(float x0, float y0, float x1, float y1, int color1, int color2) {
        fillGradient(x0, y0, x1, y1, 0, color1, color2);
    }

    public static void fillGradient(float x0, float y0, float x1, float y1, float z, int color1, int color2) {
        Matrix4f matrix4f = pose.last().pose();

        RenderSystem.setShader(CoreShaders.POSITION_COLOR);

        if (!batching)
            bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        setupBufferBuilder();

        bufferBuilder.addVertex(matrix4f, x0, y0, z).setColor(color1);
        bufferBuilder.addVertex(matrix4f, x0, y1, z).setColor(color2);
        bufferBuilder.addVertex(matrix4f, x1, y1, z).setColor(color2);
        bufferBuilder.addVertex(matrix4f, x1, y0, z).setColor(color1);

        submitIfNeeded();
    }

    public static void renderBoxBorder(float x0, float y0, float width, float height, float borderWidth, int color) {
        renderBorder(x0, y0, x0 + width, y0 + height, borderWidth, color);
    }

    public static void renderBorder(float x0, float y0, float x1, float y1, float width, int color) {
        GuiRenderer.fill(x0, y0, x1, y0 + width, color);
        GuiRenderer.fill(x0, y1 - width, x1, y1, color);

        GuiRenderer.fill(x0, y0 + width, x0 + width, y1 - width, color);
        GuiRenderer.fill(x1 - width, y0 + width, x1, y1 - width, color);
    }

    public static void drawString(Font font, Component component, int x, int y, int color) {
        drawString(font, component.getVisualOrderText(), x, y, color);
    }

    public static void drawString(Font font, FormattedCharSequence formattedCharSequence, int x, int y, int color) {
        guiGraphics.drawString(font, formattedCharSequence, x, y, color);
    }

    public static void drawString(Font font, Component component, int x, int y, int color, boolean shadow) {
        drawString(font, component.getVisualOrderText(), x, y, color, shadow);
    }

    public static void drawString(Font font, FormattedCharSequence formattedCharSequence, int x, int y, int color, boolean shadow) {
        guiGraphics.drawString(font, formattedCharSequence, x, y, color, shadow);
    }

    public static void drawCenteredString(Font font, Component component, int x, int y, int color) {
        FormattedCharSequence formattedCharSequence = component.getVisualOrderText();
        guiGraphics.drawString(font, formattedCharSequence, x - font.width(formattedCharSequence) / 2, y, color);
    }

    public static int getMaxTextWidth(Font font, List<FormattedCharSequence> list) {
        int maxWidth = 0;
        for (var text : list) {
            int width = font.width(text);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        return maxWidth;
    }

    public static void beginBatch() {
        batching = true;
    }

    public static void endBatch() {
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        batching = false;
    }

    public static void flush() {
        guiGraphics.flush();

        if (batching) {
            endBatch();
        }
    }

    private static void setupBufferBuilder() {
        if (!batching || !drawing) {
            bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            drawing = true;
        }
    }

    private static void submitIfNeeded() {
        if (!batching) {
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
            drawing = false;
        }
    }
}
