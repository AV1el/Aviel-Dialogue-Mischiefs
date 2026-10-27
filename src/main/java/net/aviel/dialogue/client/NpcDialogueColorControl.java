package net.aviel.dialogue.client;

import com.google.gson.JsonObject;
import net.aviel.dialogue.npc.dialogue.DialoguePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NpcDialogueColorControl {
    private record ColorField(String key, EditBox field, AdmGuiKit.Rect swatch) { }
    private final NpcEditorScreen editor;
    private final Map<String, String> draft = new LinkedHashMap<>();
    private final List<ColorField> fields = new ArrayList<>();
    private boolean edited;
    private boolean valid = true;
    private String lastSeen;
    private Button applyButton;

    NpcDialogueColorControl(NpcEditorScreen editor) { this.editor = editor; }

    void resetDraft() { draft.clear(); fields.clear(); edited = false; valid = true; lastSeen = null; applyButton = null; }

    private static Component text(String key) { return Component.translatable("message.adm.npc.editor." + key); }

    private static int columns(int width) { return width < 360 ? 1 : 2; }

    static int height(int width) {
        int columns = columns(width);
        return 44 + ((DialoguePalette.KEYS.size() + columns - 1) / columns) * 38;
    }

    void build(int x, int y, int width, String current) {
        if (!edited) load(current);
        lastSeen = current;
        fields.clear();
        int half = (width - 8) / 2;
        applyButton = editor.addContentWidget(Button.builder(text("apply"), button -> apply())
                .bounds(x, y + 12, half, 20).build());
        editor.addContentWidget(Button.builder(text("palette_reset"), button -> {
            for (ColorField entry : fields) entry.field().setValue("");
            edited = true;
            apply();
        }).bounds(x + half + 8, y + 12, half, 20).build());
        editor.addLabel(text("palette"), x, y, width);
        int columns = columns(width);
        int cellWidth = (width - (columns - 1) * 12) / columns;
        for (int i = 0; i < DialoguePalette.KEYS.size(); i++) {
            addField(DialoguePalette.KEYS.get(i), x + i % columns * (cellWidth + 12), y + 44 + i / columns * 38, cellWidth);
        }
        validate();
    }

    private void addField(String key, int x, int y, int width) {
        Component label = text("palette." + key);
        editor.addLabel(label, x, y, width);
        EditBox field = editor.addContentWidget(new EditBox(editor.editorFont(), x, y + 12, width - 56, 20, label));
        field.setMaxLength(9);
        field.setValue(draft.getOrDefault(key, ""));
        field.setHint(Component.literal("#RRGGBB"));
        field.setTooltip(Tooltip.create(label));
        fields.add(new ColorField(key, field, new AdmGuiKit.Rect(x + width - 50, y + 12, 20, 20)));
        editor.addContentWidget(Button.builder(Component.literal("x"), button -> field.setValue(""))
                .tooltip(Tooltip.create(text("color_reset"))).bounds(x + width - 24, y + 12, 24, 20).build());
        field.setResponder(value -> {
            draft.put(key, value);
            edited = true;
            validate();
        });
    }

    private void load(String current) {
        draft.clear();
        DialoguePalette.read(current).entrySet().forEach(entry -> draft.put(entry.getKey(), entry.getValue().getAsString()));
    }

    private void validate() {
        valid = true;
        for (ColorField entry : fields) {
            boolean fieldValid = validColor(draft.getOrDefault(entry.key(), ""));
            valid &= fieldValid;
            entry.field().setTextColor(fieldValid ? NpcEditorTheme.TEXT : 0xFFFF9D8F);
        }
        if (applyButton != null) applyButton.active = valid;
    }

    private static boolean validColor(String value) {
        try {
            DialoguePalette.normalize(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    boolean apply() {
        if (!valid) return false;
        if (edited) {
            JsonObject colors = new JsonObject();
            draft.forEach(colors::addProperty);
            editor.sendEditorAction("dialogue_palette", DialoguePalette.normalizePalette(colors.toString()), 0);
            edited = false;
        }
        return true;
    }

    void render(GuiGraphics graphics, String current) {
        if (!current.equals(lastSeen)) {
            lastSeen = current;
            if (!edited) {
                load(current);
                for (ColorField entry : fields) entry.field().setValue(draft.getOrDefault(entry.key(), ""));
                edited = false;
                validate();
            }
        }
        for (ColorField entry : fields) {
            String value = draft.getOrDefault(entry.key(), "").trim();
            int color = NpcEditorTheme.SURFACE;
            boolean fieldValid = validColor(value);
            if (fieldValid && !value.isEmpty()) {
                color = (int) Long.parseLong(value.substring(1), 16);
                if (value.length() == 7) color |= 0xFF000000;
            }
            NpcEditorTheme.box(graphics, entry.swatch(), color, fieldValid ? NpcEditorTheme.BORDER : 0xFFFF9D8F);
        }
    }
}
