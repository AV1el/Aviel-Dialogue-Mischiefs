package net.aviel.dialogue.npc.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NpcJsonEmoteParser {
    private NpcJsonEmoteParser() {
    }

    public static NpcJsonEmoteClip parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty emote data");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    public static NpcJsonEmoteClip parse(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            throw new IllegalArgumentException("Empty emote text");
        }
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject emote = object(root, "emote");
        if (emote == null) {
            throw new IllegalArgumentException("Missing 'emote' object");
        }

        String name = string(root, "name", "");
        String description = string(root, "description", "");
        String author = string(root, "author", "");

        int beginTick = integer(emote, "beginTick", 0);
        int endTick = integer(emote, "endTick", beginTick + 1);
        int stopTick = integer(emote, "stopTick", endTick + 1);
        boolean isLoop = bool(emote, "isLoop", false);
        int returnTick = integer(emote, "returnTick", beginTick);
        boolean degrees = bool(emote, "degrees", false);

        Map<NpcJsonEmoteClip.BodyPart, Map<NpcJsonEmoteClip.Channel, List<NpcJsonEmoteClip.Keyframe>>> tracks =
                new EnumMap<>(NpcJsonEmoteClip.BodyPart.class);

        JsonElement movesElement = emote.get("moves");
        if (movesElement != null && movesElement.isJsonArray()) {
            for (JsonElement moveElement : movesElement.getAsJsonArray()) {
                if (!moveElement.isJsonObject()) continue;
                JsonObject move = moveElement.getAsJsonObject();
                int tick = integer(move, "tick", -1);
                if (tick < 0) continue;
                NpcEmoteEasing easing = NpcEmoteEasing.fromString(string(move, "easing", "LINEAR"));
                int turn = integer(move, "turn", 0);

                for (Map.Entry<String, JsonElement> entry : move.entrySet()) {
                    String key = entry.getKey();
                    if (isMetaKey(key)) continue;
                    NpcJsonEmoteClip.BodyPart part = NpcJsonEmoteClip.BodyPart.fromString(key);
                    if (part == null) continue;
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject channels = entry.getValue().getAsJsonObject();
                    for (Map.Entry<String, JsonElement> channelEntry : channels.entrySet()) {
                        NpcJsonEmoteClip.Channel channel = NpcJsonEmoteClip.Channel.fromString(channelEntry.getKey());
                        if (channel == null) continue;
                        if (!channelEntry.getValue().isJsonPrimitive()) continue;
                        float value = floatValue(channelEntry.getValue(), 0.0F);
                        if (turn != 0 && channel.isAngle()) {
                            value += Mth.TWO_PI * turn;
                        }
                        addKeyframe(tracks, part, channel, new NpcJsonEmoteClip.Keyframe(tick, value, easing));
                    }
                }
            }
        }

        return new NpcJsonEmoteClip(
                name,
                description,
                author,
                beginTick,
                endTick,
                stopTick,
                isLoop,
                returnTick,
                degrees,
                tracks
        );
    }

    private static void addKeyframe(
            Map<NpcJsonEmoteClip.BodyPart, Map<NpcJsonEmoteClip.Channel, List<NpcJsonEmoteClip.Keyframe>>> tracks,
            NpcJsonEmoteClip.BodyPart part,
            NpcJsonEmoteClip.Channel channel,
            NpcJsonEmoteClip.Keyframe keyframe
    ) {
        Map<NpcJsonEmoteClip.Channel, List<NpcJsonEmoteClip.Keyframe>> partTracks =
                tracks.computeIfAbsent(part, ignored -> new EnumMap<>(NpcJsonEmoteClip.Channel.class));
        List<NpcJsonEmoteClip.Keyframe> list = partTracks.computeIfAbsent(channel, ignored -> new ArrayList<>());
        list.add(keyframe);
    }

    private static boolean isMetaKey(String key) {
        if (key == null) return true;
        String value = key.toLowerCase(Locale.ROOT);
        return "tick".equals(value) || "easing".equals(value) || "turn".equals(value);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonObject()) return null;
        return element.getAsJsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float floatValue(JsonElement element, float fallback) {
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
