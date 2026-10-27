package net.aviel.dialogue.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aviel.dialogue.entity.ai.NpcCombatProfile;
import net.aviel.dialogue.entity.ai.NpcCompanion;
import net.aviel.dialogue.network.OpenNpcCombatEditorPacket;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class NpcTemplateEditorPane {
    private static final List<String> STATS = List.of("health", "damage", "armor", "speed", "attack_speed", "attack_range", "knockback", "knockback_resistance", "radius", "movement_multiplier");
    private final NpcEditorScreen editor;
    private final UUID npc;
    private final String template;
    private final JsonObject draft;
    private final Map<String, String> inputs = new HashMap<>();
    private NpcCompanion.Mode mode;
    private boolean combat;
    private String status = "";
    private int left;
    private int top;
    private int contentWidth;
    private net.minecraft.client.gui.Font font;

    NpcTemplateEditorPane(NpcEditorScreen editor, OpenNpcCombatEditorPacket packet) {
        this.editor = editor;
        npc = packet.npc();
        template = packet.template();
        draft = JsonParser.parseString(packet.profile()).getAsJsonObject();
        mode = packet.mode();
        combat = packet.combat();
    }

    boolean matches(OpenNpcCombatEditorPacket packet) { return npc.equals(packet.npc()) && template.equals(packet.template()); }
    void acknowledge(OpenNpcCombatEditorPacket packet) { status = packet.status(); }
    String status() { return status; }
    private static Component text(String key) { return Component.translatable("message.adm.combat_editor." + key); }
    private <T extends AbstractWidget> T addRenderableWidget(T widget) { return editor.addContentWidget(widget); }
    void build(int page, int x, int y, int width) {
        left = x; top = y - 60; contentWidth = width; font = editor.editorFont();
        int half = (width - 8) / 2;
        switch (page) {
            case 0 -> buildStats();
            case 1 -> buildBehavior(half);
            case 3 -> buildOrders();
            default -> throw new IllegalArgumentException("Unknown editor pane");
        }
    }
    int contentHeight(int page, int width) {
        return switch (page) { case 0 -> width < 300 ? 340 : 170; case 1 -> 152; default -> 60; };
    }
    private void buildStats() {
        int columns = contentWidth < 300 ? 1 : 2;
        int fieldWidth = (contentWidth - (columns - 1) * 8) / columns;
        for (int i = 0; i < STATS.size(); i++) {
            String key = STATS.get(i);
            input(key, draft.get(key).getAsString(), left + (i % columns) * (fieldWidth + 8), top + 60 + (i / columns) * 34, fieldWidth, 32);
        }
    }

    private void buildBehavior(int half) {
        var targeting = draft.getAsJsonObject("targeting");
        addRenderableWidget(CycleButton.<NpcCombatProfile.Policy>builder(value -> text("policy." + value.name().toLowerCase(Locale.ROOT)))
                .withValues(NpcCombatProfile.Policy.values()).withInitialValue(NpcCombatProfile.Policy.valueOf(draft.get("policy").getAsString().toUpperCase(Locale.ROOT)))
                .create(left, top + 60, contentWidth, 20, text("policy"), (button, value) -> {
                    draft.addProperty("policy", value.name());
                    status = "unsaved";
                }));
        checkbox("switch_on_attack", targeting, left, top + 87, contentWidth);
        checkbox("protect_owner", targeting, left, top + 113, contentWidth);
        input("switch_cooldown", targeting.get("switch_cooldown").getAsString(), left, top + 143, half, 3);
        input("lost_sight_ticks", targeting.get("lost_sight_ticks").getAsString(), left + half + 8, top + 143, half, 3);
        String targets = String.join(",", draft.getAsJsonArray("targets").asList().stream().map(value -> value.getAsString()).toList());
        input("targets", targets, left, top + 179, contentWidth, 4096);
    }

    private void buildOrders() {
        addRenderableWidget(CycleButton.<NpcCompanion.Mode>builder(value -> text("mode." + value.name().toLowerCase(Locale.ROOT)))
                .withValues(NpcCompanion.Mode.values()).withInitialValue(mode)
                .create(left, top + 62, contentWidth, 20, text("order"), (button, value) -> {
                    mode = value;
                    send("combat_mode", value.name());
                }));
        addRenderableWidget(Checkbox.builder(text("combat"), font).pos(left, top + 95).maxWidth(contentWidth)
                .selected(combat).onValueChange((box, value) -> {
                    combat = value;
                    send("combat_toggle", Boolean.toString(value));
                }).build());
    }

    private void checkbox(String key, JsonObject targeting, int x, int y, int width) {
        addRenderableWidget(Checkbox.builder(text(key), font).pos(x, y).maxWidth(width).selected(targeting.get(key).getAsBoolean())
                .onValueChange((box, value) -> {
                    targeting.addProperty(key, value);
                    status = "unsaved";
                }).build());
    }

    private EditBox input(String key, String initial, int x, int y, int width, int maxLength) {
        editor.addLabel(key, x, y, width);
        EditBox field = addRenderableWidget(new EditBox(font, x, y + 11, width, 18, text(key)));
        field.setMaxLength(maxLength);
        field.setValue(inputs.getOrDefault(key, initial));
        field.setResponder(value -> {
            inputs.put(key, value);
            status = "unsaved";
        });
        return field;
    }

    void save() {
        try {
            JsonObject profile = draft.deepCopy();
            for (String key : STATS) {
                if (inputs.containsKey(key)) profile.addProperty(key, Double.parseDouble(inputs.get(key)));
            }
            var targeting = profile.getAsJsonObject("targeting");
            for (String key : List.of("switch_cooldown", "lost_sight_ticks")) {
                if (inputs.containsKey(key)) targeting.addProperty(key, Double.parseDouble(inputs.get(key)));
            }
            if (inputs.containsKey("targets")) {
                JsonArray targets = new JsonArray();
                for (String id : inputs.get("targets").split(",")) if (!id.isBlank()) targets.add(id.trim());
                profile.add("targets", targets);
            }
            JsonObject request = new JsonObject();
            request.addProperty("template", template);
            request.addProperty("appearance", true);
            request.add("profile", NpcCombatProfile.fromJson(profile).toJson());
            send("combat_save", request.toString());
            status = "pending";
        } catch (IllegalArgumentException ex) {
            status = "invalid";
        }
    }

    private void send(String action, String value) {
        editor.sendEditorAction(action, value, 0);
    }

}
