package net.aviel.dialogue.client;

import net.aviel.dialogue.client.AdmGuiKit.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.aviel.dialogue.client.AdmGuiKit.clip;

/** Searchable scrollable list popup for choosing one value (dialogues, skins, templates). */
final class AmethystPicker {
    private static final int ROW_HEIGHT = 13;

    /** Everything one picker session needs: heading, choices, how to label them, and the callback. */
    record Request(String title, List<String> options, Function<String, String> display, String current, Consumer<String> onPick) {
    }

    private Request request = new Request("", List.of(), value -> value, "", value -> {
    });
    private List<String> filtered = List.of();
    private EditBox search;
    private int scroll;
    private boolean open;
    private Rect popup = new Rect(0, 0, 0, 0);

    boolean isOpen() {
        return this.open;
    }

    void open(Font font, Request request) {
        this.request = request;
        this.search = new EditBox(font, 0, 0, 10, 12, Component.empty());
        this.search.setBordered(false);
        this.search.setMaxLength(60);
        this.search.setTextColor(0xFFF2E7FF);
        this.search.setFocused(true);
        this.open = true;
        refilter();
    }

    void close() {
        this.open = false;
    }

    private void refilter() {
        String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        this.filtered = query.isBlank()
                ? this.request.options()
                : this.request.options().stream()
                        .filter(option -> option.toLowerCase(Locale.ROOT).contains(query)
                                || this.request.display().apply(option).toLowerCase(Locale.ROOT).contains(query))
                        .toList();
        this.scroll = 0;
    }

    void render(GuiGraphics graphics, Font font, Rect panel, int mouseX, int mouseY) {
        if (!this.open) {
            return;
        }
        // Lift the popup above the screen's batched text so controls never bleed through.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(panel.x(), panel.y(), panel.x() + panel.w(), panel.y() + panel.h(), 0xB0060310);
        int w = Math.min(250, panel.w() - 24);
        int h = Math.min(216, panel.h() - 16);
        this.popup = new Rect(panel.x() + (panel.w() - w) / 2, panel.y() + (panel.h() - h) / 2, w, h);
        AdmGuiKit.fillRounded(graphics, this.popup.x(), this.popup.y(), w, h, 0xFF160D22);
        AdmGuiKit.outlineRounded(graphics, this.popup.x(), this.popup.y(), w, h, 0xFF574A75);
        graphics.drawString(font, clip(font, this.request.title(), w - 40), this.popup.x() + 8, this.popup.y() + 6, 0xFFFFD36B, false);
        boolean closeHover = closeRect().contains(mouseX, mouseY);
        graphics.drawCenteredString(font, "x", closeRect().centerX(), closeRect().y() + 3, closeHover ? 0xFFF2E7FF : 0xFF9B8BA8);

        Rect searchRect = searchRect();
        AdmGuiKit.fillRounded(graphics, searchRect.x(), searchRect.y(), searchRect.w(), searchRect.h(), 0xFF241732);
        this.search.setX(searchRect.x() + 5);
        this.search.setY(searchRect.y() + 4);
        this.search.setWidth(searchRect.w() - 10);
        this.search.render(graphics, mouseX, mouseY, 0.0F);

        renderRows(graphics, font, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderRows(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        Rect list = listRect();
        int visible = visibleRows();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.filtered.size() - visible));
        for (int row = 0; row < visible; row++) {
            int index = this.scroll + row;
            if (index >= this.filtered.size()) {
                break;
            }
            String option = this.filtered.get(index);
            Rect rowRect = new Rect(list.x(), list.y() + row * ROW_HEIGHT, list.w(), ROW_HEIGHT);
            boolean hover = rowRect.contains(mouseX, mouseY);
            boolean selected = option.equals(this.request.current());
            if (hover || selected) {
                AdmGuiKit.fillRounded(graphics, rowRect.x(), rowRect.y(), rowRect.w(), rowRect.h(), hover ? 0xFF3C2A55 : 0xFF241538);
            }
            graphics.drawString(font, clip(font, this.request.display().apply(option), rowRect.w() - 12), rowRect.x() + 5, rowRect.y() + 3,
                    selected ? 0xFFA986EC : 0xFFF2E7FF, false);
        }
        if (this.filtered.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("message.adm.npc.editor.no_matches"), list.x() + list.w() / 2, list.y() + 8, 0xFF9B8BA8);
        }
        if (this.filtered.size() > visible) {
            int x = list.x() + list.w() + 2;
            int trackH = visible * ROW_HEIGHT;
            graphics.fill(x, list.y(), x + 2, list.y() + trackH, 0x33000000);
            int thumbH = Math.max(12, trackH * visible / this.filtered.size());
            int travel = Math.max(1, this.filtered.size() - visible);
            int thumbY = list.y() + (int) Math.round((double) this.scroll / travel * (trackH - thumbH));
            graphics.fill(x, thumbY, x + 2, thumbY + thumbH, 0xFFA986EC);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!this.open) {
            return false;
        }
        if (closeRect().contains(mouseX, mouseY) || !this.popup.contains(mouseX, mouseY)) {
            close();
            return true;
        }
        Rect list = listRect();
        if (list.contains(mouseX, mouseY)) {
            int index = this.scroll + ((int) mouseY - list.y()) / ROW_HEIGHT;
            if (index >= 0 && index < this.filtered.size()) {
                this.request.onPick().accept(this.filtered.get(index));
                close();
            }
            return true;
        }
        this.search.setFocused(true);
        return true;
    }

    boolean mouseScrolled(double scrollY) {
        if (!this.open) {
            return false;
        }
        this.scroll = Mth.clamp(this.scroll + (scrollY < 0 ? 1 : -1), 0, Math.max(0, this.filtered.size() - visibleRows()));
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.open) {
            return false;
        }
        if (keyCode == 256) {
            close();
            return true;
        }
        if (keyCode == 257 && this.filtered.size() == 1) {
            this.request.onPick().accept(this.filtered.get(0));
            close();
            return true;
        }
        boolean handled = this.search.keyPressed(keyCode, scanCode, modifiers);
        refilter();
        return handled || keyCode != 258;
    }

    boolean charTyped(char character, int modifiers) {
        if (!this.open) {
            return false;
        }
        boolean handled = this.search.charTyped(character, modifiers);
        refilter();
        return handled;
    }

    private Rect closeRect() {
        return new Rect(this.popup.x() + this.popup.w() - 18, this.popup.y() + 4, 14, 14);
    }

    private Rect searchRect() {
        return new Rect(this.popup.x() + 8, this.popup.y() + 20, this.popup.w() - 16, 16);
    }

    private Rect listRect() {
        return new Rect(this.popup.x() + 8, this.popup.y() + 42, this.popup.w() - 20, this.popup.h() - 50);
    }

    private int visibleRows() {
        return Math.max(1, listRect().h() / ROW_HEIGHT);
    }
}
