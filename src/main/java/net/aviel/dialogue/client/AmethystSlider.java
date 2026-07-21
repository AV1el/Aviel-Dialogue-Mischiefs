package net.aviel.dialogue.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;

/** Minimal draggable slider in the amethyst style; commits the value on mouse release. */
final class AmethystSlider {
    private final float min;
    private final float max;
    private final float step;
    private final Consumer<Float> onCommit;
    private AdmGuiKit.Rect track = new AdmGuiKit.Rect(0, 0, 0, 0);
    private boolean dragging;
    private float pending = Float.NaN;

    AmethystSlider(float min, float max, float step, Consumer<Float> onCommit) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.onCommit = onCommit;
    }

    void render(GuiGraphics graphics, Font font, AdmGuiKit.Rect area, String label, float liveValue) {
        this.track = new AdmGuiKit.Rect(area.x(), area.y() + 10, area.w(), 6);
        float value = this.dragging && !Float.isNaN(this.pending) ? this.pending : liveValue;
        String text = label + ": " + String.format(Locale.ROOT, this.max > 10 ? "%.0f" : "%.2f", value);
        graphics.drawString(font, text, area.x(), area.y(), 0xFF9B8BA8, false);
        AdmGuiKit.fillRounded(graphics, this.track.x(), this.track.y(), this.track.w(), 6, 0xFF241732);
        float progress = Mth.clamp((value - this.min) / (this.max - this.min), 0.0F, 1.0F);
        int thumbX = this.track.x() + Math.round(progress * (this.track.w() - 8));
        AdmGuiKit.fillRounded(graphics, this.track.x(), this.track.y(), Math.max(6, thumbX - this.track.x() + 4), 6, 0xFF3C2A55);
        AdmGuiKit.fillRounded(graphics, thumbX, this.track.y() - 2, 8, 10, 0xFFA986EC);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!new AdmGuiKit.Rect(this.track.x(), this.track.y() - 4, this.track.w(), 14).contains(mouseX, mouseY)) {
            return false;
        }
        this.dragging = true;
        updatePending(mouseX);
        return true;
    }

    boolean mouseDragged(double mouseX) {
        if (!this.dragging) {
            return false;
        }
        updatePending(mouseX);
        return true;
    }

    void mouseReleased() {
        if (this.dragging && !Float.isNaN(this.pending)) {
            this.onCommit.accept(this.pending);
        }
        this.dragging = false;
    }

    private void updatePending(double mouseX) {
        float progress = Mth.clamp((float) (mouseX - this.track.x()) / Math.max(1, this.track.w() - 8), 0.0F, 1.0F);
        float raw = this.min + progress * (this.max - this.min);
        this.pending = Mth.clamp(Math.round(raw / this.step) * this.step, this.min, this.max);
    }
}
