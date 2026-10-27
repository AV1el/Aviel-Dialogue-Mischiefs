package net.aviel.dialogue.client;

import net.aviel.dialogue.client.AdmGuiKit.Rect;

public record NpcEditorLayout(Rect frame, Rect tabs, Rect preview, Rect content, Rect footer) {
    public static NpcEditorLayout of(int width, int height) {
        int w = Math.min(760, width - 16);
        int h = Math.min(440, height - 16);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        int side = Math.max(80, Math.min(170, w / 4));
        int bodyY = y + 56;
        int footerY = y + h - 44;
        return new NpcEditorLayout(new Rect(x, y, w, h), new Rect(x + 8, y + 31, w - 16, 20),
                new Rect(x + 8, bodyY, side, footerY - bodyY - 6),
                new Rect(x + side + 20, bodyY, w - side - 34, footerY - bodyY - 6),
                new Rect(x + 8, footerY, w - 16, 38));
    }
}
