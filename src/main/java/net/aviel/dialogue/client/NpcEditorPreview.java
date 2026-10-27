package net.aviel.dialogue.client;

import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class NpcEditorPreview {
    private static boolean rendering;

    private NpcEditorPreview() { }

    public static boolean rendering() {
        return rendering;
    }

    static void render(GuiGraphics graphics, AdmGuiKit.Rect area, DialogueNpcEntity npc) {
        boolean previous = rendering;
        rendering = true;
        try {
            int scale = (int) (Math.min(area.w() * 0.43, area.h() * 0.42) / Math.max(0.5, npc.getModelScale()));
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, area.x(), area.y(), area.x() + area.w(),
                    area.y() + area.h(), Math.max(10, scale), 0, area.centerX() - 12, area.y() + area.h() / 2, npc);
        } finally {
            rendering = previous;
        }
    }
}
