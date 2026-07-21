package net.aviel.dialogue.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;

import java.util.function.Consumer;

/**
 * Builds a dialogue programmatically, producing the same JSON structure that dialogue
 * files use. Register the result with {@link AdmDialogueApi#registerDialogue(String, DialogueBuilder)}
 * to make it openable by id without a file on disk.
 */
public final class DialogueBuilder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JsonObject root = new JsonObject();
    private final JsonObject nodes = new JsonObject();

    private DialogueBuilder() {
        root.add("nodes", nodes);
    }

    public static DialogueBuilder create() {
        return new DialogueBuilder();
    }

    public DialogueBuilder title(String title) {
        root.addProperty("title", title);
        return this;
    }

    public DialogueBuilder speaker(String speaker) {
        root.addProperty("speaker", speaker);
        return this;
    }

    public DialogueBuilder textSpeed(int speed) {
        root.addProperty("text_speed", speed);
        return this;
    }

    public DialogueBuilder start(String... nodeIds) {
        JsonArray starts = new JsonArray();
        for (String nodeId : nodeIds) {
            starts.add(nodeId);
        }
        root.add("random_start", starts);
        return this;
    }

    public DialogueBuilder style(Consumer<JsonObject> style) {
        JsonObject styleObject = new JsonObject();
        style.accept(styleObject);
        root.add("style", styleObject);
        return this;
    }

    public DialogueBuilder node(String id, Consumer<NodeBuilder> node) {
        NodeBuilder builder = new NodeBuilder();
        node.accept(builder);
        nodes.add(id, builder.json);
        return this;
    }

    public String toJsonString() {
        return GSON.toJson(root);
    }

    public NpcDialogueDefinition build() {
        return NpcDialogueDefinition.fromJson(toJsonString());
    }

    public static final class NodeBuilder {
        private final JsonObject json = new JsonObject();
        private final JsonArray text = new JsonArray();
        private final JsonArray choices = new JsonArray();

        private NodeBuilder() {
            json.add("text", text);
            json.add("choices", choices);
        }

        public NodeBuilder speaker(String speaker) {
            json.addProperty("speaker", speaker);
            return this;
        }

        public NodeBuilder text(String... lines) {
            for (String line : lines) {
                text.add(line);
            }
            return this;
        }

        public NodeBuilder textSpeed(int speed) {
            json.addProperty("text_speed", speed);
            return this;
        }

        public NodeBuilder choice(Consumer<ChoiceBuilder> choice) {
            ChoiceBuilder builder = new ChoiceBuilder();
            choice.accept(builder);
            choices.add(builder.json);
            return this;
        }
    }

    public static final class ChoiceBuilder {
        private final JsonObject json = new JsonObject();

        private ChoiceBuilder() {
        }

        public ChoiceBuilder id(String id) {
            json.addProperty("id", id);
            return this;
        }

        public ChoiceBuilder text(String text) {
            json.addProperty("text", text);
            return this;
        }

        public ChoiceBuilder next(String nodeId) {
            json.addProperty("next", nodeId);
            return this;
        }

        public ChoiceBuilder close() {
            json.addProperty("close", true);
            return this;
        }

        public ChoiceBuilder trade(String tradeFile) {
            json.addProperty("trade", tradeFile);
            return this;
        }

        public ChoiceBuilder command(String command) {
            appendTo("commands", command);
            return this;
        }

        public ChoiceBuilder setFlag(String flag) {
            appendTo("set_flags", flag);
            return this;
        }

        public ChoiceBuilder clearFlag(String flag) {
            appendTo("clear_flags", flag);
            return this;
        }

        public ChoiceBuilder requiresFlag(String flag) {
            appendTo("requires_flags", flag);
            return this;
        }

        public ChoiceBuilder missingFlag(String flag) {
            appendTo("missing_flags", flag);
            return this;
        }

        public ChoiceBuilder requiresItem(String itemId, int count) {
            appendItem("requires_items", itemId, count);
            return this;
        }

        public ChoiceBuilder takeItem(String itemId, int count) {
            appendItem("take_items", itemId, count);
            return this;
        }

        public ChoiceBuilder giveItem(String itemId, int count) {
            appendItem("give_items", itemId, count);
            return this;
        }

        private void appendTo(String key, String value) {
            JsonArray array = json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
            array.add(value);
            json.add(key, array);
        }

        private void appendItem(String key, String itemId, int count) {
            JsonArray array = json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
            JsonObject item = new JsonObject();
            item.addProperty("item", itemId);
            item.addProperty("count", count);
            array.add(item);
            json.add(key, array);
        }
    }
}
