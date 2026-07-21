package net.aviel.dialogue.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.regex.Pattern;

/** Drawing helpers for ADM's rounded "amethyst glass" screens. */
public final class AdmGuiKit {
    private static final Pattern AMP_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private AdmGuiKit() {
    }

    /** Filled rectangle with 2px-cut corners, reads as rounded at GUI scale. */
    public static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width < 5 || height < 5) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
    }

    /** 1px border matching {@link #fillRounded}'s corner cut. */
    public static void outlineRounded(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + 1, y + height - 2, color);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    /** Small right-pointing arrow, 7px wide, centered on {@code y}. */
    public static void drawArrow(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y - 1, x + 5, y + 1, color);
        graphics.fill(x + 4, y - 2, x + 5, y + 2, color);
        graphics.fill(x + 5, y - 1, x + 6, y + 1, color);
        graphics.fill(x + 6, y, x + 7, y + 1, color);
    }

    /** Converts {@code &}-style formatting codes to the section signs the font renderer understands. */
    public static String amp(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return AMP_CODE.matcher(text).replaceAll("§$1");
    }

    public static boolean isInside(double mouseX, double mouseY, int minX, int minY, int maxX, int maxY) {
        return mouseX >= minX && mouseX < maxX && mouseY >= minY && mouseY < maxY;
    }

    /** Screen-space rectangle for hit testing and pill rendering. */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mouseX, double mouseY) {
            return isInside(mouseX, mouseY, x, y, x + w, y + h);
        }

        public int centerX() {
            return x + w / 2;
        }
    }

    public static String clip(Font font, String text, int width) {
        if (font == null || text == null || text.isBlank()) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    public static int parseColor(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x")) {
            value = value.substring(2);
        }
        try {
            if (value.length() == 6) {
                return 0xFF000000 | Integer.parseInt(value, 16);
            }
            if (value.length() == 8) {
                return (int) Long.parseLong(value, 16);
            }
            return fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
