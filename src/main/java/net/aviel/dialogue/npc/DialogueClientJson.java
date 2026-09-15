package net.aviel.dialogue.npc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.aviel.dialogue.npc.dialogue.DialogueTranslation;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Serializes a dialogue definition into the per-player JSON sent to the client. */
public final class DialogueClientJson {
    private static final Gson GSON = new Gson();

    private DialogueClientJson() {
    }

    public static String toClientJson(NpcDialogueDefinition definition, ServerPlayer player, Entity target) {
        return toClientJson(definition, DialogueTranslation.EMPTY, player, target);
    }

    public static String toClientJson(
            NpcDialogueDefinition definition,
            DialogueTranslation translation,
            ServerPlayer player,
            Entity target
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("title", translation.titleOr(definition.title()));
        root.addProperty("start", definition.startNode());
        addStyle(root, definition.style());

        JsonObject nodes = new JsonObject();
        for (NpcDialogueDefinition.Node node : definition.nodes().values()) {
            nodes.add(node.id(), nodeJson(definition, node, translation, player, target));
        }
        root.add("nodes", nodes);
        return GSON.toJson(root);
    }

    private static JsonObject nodeJson(
            NpcDialogueDefinition definition,
            NpcDialogueDefinition.Node node,
            DialogueTranslation translation,
            ServerPlayer player,
            Entity target
    ) {
        JsonObject nodeObject = new JsonObject();
        nodeObject.addProperty("speaker", translation.speakerFor(definition, node));
        nodeObject.addProperty("text_speed", node.textSpeed());
        nodeObject.addProperty("text_color", node.textColor());
        nodeObject.addProperty("speaker_color", node.speakerColor());
        addSound(nodeObject, node.sound());
        JsonArray text = new JsonArray();
        for (String line : translation.textFor(node)) {
            text.add(line);
        }
        nodeObject.add("text", text);

        JsonArray choices = new JsonArray();
        for (NpcDialogueDefinition.Choice choice : node.choices()) {
            if (!NpcDialogueService.isChoiceAvailable(player, target, choice)) {
                continue;
            }
            JsonObject choiceObject = new JsonObject();
            choiceObject.addProperty("server_index", choice.serverIndex());
            choiceObject.addProperty("id", choice.id());
            choiceObject.addProperty("text", translation.choiceTextFor(node, choice));
            choiceObject.addProperty("next", choice.next());
            choiceObject.addProperty("close", choice.close());
            choiceObject.addProperty("action", choice.action());
            choiceObject.addProperty("trade", choice.trade());
            choices.add(choiceObject);
        }
        nodeObject.add("choices", choices);
        return nodeObject;
    }

    private static void addSound(JsonObject object, NpcDialogueDefinition.DialogueSound sound) {
        if (object == null || sound == null) {
            return;
        }
        JsonObject soundObject = new JsonObject();
        soundObject.addProperty("mode", sound.mode());
        soundObject.addProperty("text", sound.textSound());
        soundObject.addProperty("full", sound.fullSound());
        soundObject.addProperty("volume", sound.volume());
        soundObject.addProperty("pitch", sound.pitch());
        soundObject.addProperty("letter_interval", sound.letterInterval());
        object.add("sound", soundObject);
    }

    private static void addStyle(JsonObject object, NpcDialogueDefinition.DialogueStyle style) {
        if (object == null || style == null) {
            return;
        }
        JsonObject styleObject = new JsonObject();
        styleObject.addProperty("outer_color", style.outerColor());
        styleObject.addProperty("text_background", style.textBackground());
        styleObject.addProperty("choices_background", style.choicesBackground());
        styleObject.addProperty("divider_color", style.dividerColor());
        styleObject.addProperty("button_color", style.buttonColor());
        styleObject.addProperty("button_hover_color", style.buttonHoverColor());
        styleObject.addProperty("button_disabled_color", style.buttonDisabledColor());
        styleObject.addProperty("button_text_color", style.buttonTextColor());
        styleObject.addProperty("button_disabled_text_color", style.buttonDisabledTextColor());
        object.add("style", styleObject);
    }
}
