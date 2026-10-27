package net.aviel.dialogue.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;

final class NpcEditorTheme {
    static final int BACKGROUND = 0xF50E0917;
    static final int SURFACE = 0xFF1D142B;
    static final int BORDER = 0xFF49365F;
    static final int ACCENT = 0xFFAE86E5;
    static final int TEXT = 0xFFF0E7FA;
    static final int MUTED = 0xFFA898B9;
    static final int GOLD = 0xFFE4C579;

    private NpcEditorTheme() { }

    static void box(GuiGraphics graphics, AdmGuiKit.Rect rect, int fill, int border) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.w() - 1, rect.y() + rect.h() - 1, fill);
    }

    static void widget(GuiGraphics graphics, AbstractWidget widget, int mouseX, int mouseY, float tick, boolean selected) {
        var font = Minecraft.getInstance().font;
        if (!widget.visible) return;
        widget.render(graphics, mouseX, mouseY, tick);
        if (!(widget instanceof AbstractButton) || widget instanceof Checkbox) return;
        boolean hover = widget.isHoveredOrFocused();
        var rect = new AdmGuiKit.Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        box(graphics, rect, selected ? 0xFF38234F : hover && widget.active ? 0xFF2D1D40 : SURFACE,
                selected || hover && widget.active ? ACCENT : BORDER);
        if (selected) graphics.fill(rect.x() + 1, rect.y() + rect.h() - 2, rect.x() + rect.w() - 1, rect.y() + rect.h() - 1, GOLD);
        graphics.drawCenteredString(font, AdmGuiKit.clip(font, widget.getMessage().getString(), rect.w() - 8), rect.centerX(),
                rect.y() + (rect.h() - 8) / 2, selected ? TEXT : widget.active ? TEXT : MUTED);
    }

    static AdmGuiKit.Rect field(EditBox field) {
        var frame = new AdmGuiKit.Rect(field.getX(), field.getY(), field.getWidth(), field.getHeight());
        field.setBordered(false);
        field.setX(frame.x() + 5); field.setY(frame.y() + 5);
        field.setWidth(Math.max(1, frame.w() - 10)); field.setHeight(10);
        field.setTextColor(TEXT);
        return frame;
    }
}
