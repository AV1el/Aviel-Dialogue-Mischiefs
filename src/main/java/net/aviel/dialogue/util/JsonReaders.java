package net.aviel.dialogue.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class JsonReaders {
    private JsonReaders() {
    }

    public static String readString(JsonObject object, String key, String fallback) {
        JsonElement value = member(object, key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    public static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement value = member(object, key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    public static float readFloat(JsonObject object, String key, float fallback) {
        JsonElement value = member(object, key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : fallback;
    }

    private static JsonElement member(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key);
    }
}
