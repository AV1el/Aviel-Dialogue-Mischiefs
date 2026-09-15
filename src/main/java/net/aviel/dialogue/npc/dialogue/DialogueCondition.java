package net.aviel.dialogue.npc.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public sealed interface DialogueCondition permits DialogueCondition.All, DialogueCondition.Any,
        DialogueCondition.Not, DialogueCondition.Predicate {
    int MAX_DEPTH = 16;
    int MAX_CHILDREN = 64;

    static DialogueCondition read(JsonObject choice) {
        JsonElement condition = choice == null ? null : choice.get("condition");
        if ((condition == null || condition.isJsonNull()) && choice != null) {
            condition = choice.get("conditions");
        }
        return condition == null || condition.isJsonNull() ? new All(List.of()) : parse(condition, 0);
    }

    private static DialogueCondition parse(JsonElement element, int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonSyntaxException("Dialogue condition nesting exceeds " + MAX_DEPTH + " levels.");
        }
        if (element == null || element.isJsonNull()) {
            throw new JsonSyntaxException("Dialogue condition cannot be null.");
        }
        if (element.isJsonArray()) {
            return new All(parseChildren(element.getAsJsonArray(), depth));
        }
        if (!element.isJsonObject()) {
            throw new JsonSyntaxException("Dialogue condition must be an object or array.");
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("all")) {
            return new All(parseChildren(requireArray(object, "all"), depth));
        }
        if (object.has("any")) {
            return new Any(parseChildren(requireArray(object, "any"), depth));
        }
        if (object.has("not")) {
            return new Not(parse(object.get("not"), depth + 1));
        }

        String type = readString(object, "type", "").toLowerCase(Locale.ROOT);
        if (type.isBlank()) {
            throw new JsonSyntaxException("Dialogue condition predicate requires a 'type'.");
        }

        String id = firstString(object, "id", "item", "entity", "advancement", "flag", "tag", "dimension", "biome");
        String value = firstString(object, "value", "mode", "weather", "team");
        String objective = readString(object, "objective", "");
        int count = Math.max(1, readInt(object, "count", 1));
        Double min = readOptionalDouble(object, "min");
        Double max = readOptionalDouble(object, "max");
        if (object.has("amount")) {
            double amount = readDouble(object, "amount", 0.0D);
            min = amount;
            max = amount;
        }
        boolean expected = readBoolean(object, "expected", readBoolean(object, "done", true));
        return new Predicate(type, id, value, objective, count, min, max, expected, readArguments(object));
    }

    private static List<DialogueCondition> parseChildren(JsonArray array, int depth) {
        if (array.size() > MAX_CHILDREN) {
            throw new JsonSyntaxException("A dialogue condition group cannot contain more than " + MAX_CHILDREN + " entries.");
        }
        List<DialogueCondition> children = new ArrayList<>();
        for (JsonElement child : array) {
            children.add(parse(child, depth + 1));
        }
        return List.copyOf(children);
    }

    private static JsonArray requireArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new JsonSyntaxException("Dialogue condition '" + key + "' must be an array.");
        }
        return element.getAsJsonArray();
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = readString(object, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString().trim();
        } catch (RuntimeException ex) {
            throw new JsonSyntaxException("Dialogue condition '" + key + "' must be a string.", ex);
        }
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ex) {
            throw new JsonSyntaxException("Dialogue condition '" + key + "' must be an integer.", ex);
        }
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) ? object.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ex) {
            throw new JsonSyntaxException("Dialogue condition '" + key + "' must be a number.", ex);
        }
    }

    private static Double readOptionalDouble(JsonObject object, String key) {
        return object.has(key) ? readDouble(object, key, 0.0D) : null;
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ex) {
            throw new JsonSyntaxException("Dialogue condition '" + key + "' must be a boolean.", ex);
        }
    }

    private static Map<String, JsonElement> readArguments(JsonObject object) {
        Map<String, JsonElement> arguments = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            arguments.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return Map.copyOf(arguments);
    }

    record All(List<DialogueCondition> conditions) implements DialogueCondition {
        public All {
            conditions = List.copyOf(conditions == null ? List.of() : conditions);
        }
    }

    record Any(List<DialogueCondition> conditions) implements DialogueCondition {
        public Any {
            conditions = List.copyOf(conditions == null ? List.of() : conditions);
        }
    }

    record Not(DialogueCondition condition) implements DialogueCondition {
        public Not {
            if (condition == null) {
                throw new IllegalArgumentException("Negated dialogue condition cannot be null.");
            }
        }
    }

    record Predicate(
            String type,
            String id,
            String value,
            String objective,
            int count,
            Double min,
            Double max,
            boolean expected,
            Map<String, JsonElement> arguments
    ) implements DialogueCondition {
        public Predicate {
            type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
            id = id == null ? "" : id.trim();
            value = value == null ? "" : value.trim();
            objective = objective == null ? "" : objective.trim();
            count = Math.max(1, count);
            arguments = Map.copyOf(arguments == null ? Map.of() : arguments);
        }

        public String stringArgument(String key, String fallback) {
            JsonElement argument = arguments.get(key);
            return argument != null && argument.isJsonPrimitive() ? argument.getAsString() : fallback;
        }

        public double numberArgument(String key, double fallback) {
            JsonElement argument = arguments.get(key);
            if (argument == null || !argument.isJsonPrimitive() || !argument.getAsJsonPrimitive().isNumber()) {
                return fallback;
            }
            return argument.getAsDouble();
        }
    }
}
