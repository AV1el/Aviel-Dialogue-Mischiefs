package net.aviel.dialogue.client;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.npc.emote.NpcJsonEmoteClip;
import net.aviel.dialogue.npc.emote.NpcJsonEmoteParser;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcEmoteClientCache {
    private static final Map<String, NpcJsonEmoteClip> EMOTES = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> EMOTE_BYTES = new ConcurrentHashMap<>();

    private NpcEmoteClientCache() {
    }

    public static NpcJsonEmoteClip getEmote(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        return EMOTES.get(normalize(fileName));
    }

    public static byte[] getEmoteBytes(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        byte[] bytes = EMOTE_BYTES.get(normalize(fileName));
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    public static void putEmote(String fileName, byte[] jsonBytes) {
        if (fileName == null || fileName.isBlank()) return;
        if (jsonBytes == null || jsonBytes.length == 0) return;
        String key = normalize(fileName);
        EMOTE_BYTES.put(key, Arrays.copyOf(jsonBytes, jsonBytes.length));
        try {
            NpcJsonEmoteClip clip = NpcJsonEmoteParser.parse(jsonBytes);
            EMOTES.put(key, clip);
        } catch (Exception ex) {
            AvielsDialogueMod.LOGGER.warn("[ADM] Failed to parse emote '{}' on client.", fileName, ex);
        }
    }

    private static String normalize(String fileName) {
        return fileName.trim().toLowerCase(Locale.ROOT);
    }
}
