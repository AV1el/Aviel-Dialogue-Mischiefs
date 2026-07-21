package net.aviel.dialogue.npc;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.network.NpcEmoteSyncPacket;
import net.aviel.dialogue.npc.storage.AdmDataPackManager;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NpcEmoteService {
    private static final long MAX_EMOTE_SIZE_BYTES = 1024L * 1024L;

    private NpcEmoteService() {
    }

    public static Path getGlobalEmoteDirectory() {
        return DialogueStorage.emoteDirectory();
    }

    public static List<String> listEmoteFiles(MinecraftServer server) {
        DialogueStorage.ensureDirectories();
        Set<String> names = new LinkedHashSet<>();
        DialogueStorage.collectJsonFileNames(DialogueStorage.emoteDirectory(), names);
        names.addAll(AdmDataPackManager.EMOTES.ids());
        List<String> result = new ArrayList<>(names);
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static String resolveEmoteFileName(MinecraftServer server, String input) {
        String requested = DialogueRepository.normalizeReference(input);
        if (requested.isBlank()) {
            return null;
        }
        if (DialogueRepository.isDataId(requested)) {
            return AdmDataPackManager.EMOTES.contains(requested) ? requested : null;
        }
        for (String file : listEmoteFiles(server)) {
            if (file.equalsIgnoreCase(requested)) {
                return file;
            }
        }
        return null;
    }

    public static byte[] readEmoteBytes(MinecraftServer server, String fileName) {
        String normalized = DialogueRepository.normalizeReference(fileName);
        if (normalized.isBlank()) {
            return null;
        }
        if (DialogueRepository.isDataId(normalized)) {
            String json = AdmDataPackManager.EMOTES.rawJson(normalized);
            return json == null ? null : json.getBytes(StandardCharsets.UTF_8);
        }
        DialogueStorage.ensureDirectories();
        Path filePath = DialogueStorage.emoteDirectory().resolve(normalized);
        if (!Files.isRegularFile(filePath)) {
            return null;
        }
        try {
            long size = Files.size(filePath);
            if (size <= 0 || size > MAX_EMOTE_SIZE_BYTES) {
                AvielsDialogueMod.LOGGER.warn("Emote file {} skipped: size {} bytes is out of range", normalized, size);
                return null;
            }
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not read emote file {}", filePath, ex);
            return null;
        }
    }

    public static void syncEmoteToPlayer(ServerPlayer player, String fileName) {
        if (player == null || fileName == null || fileName.isBlank()) return;
        byte[] bytes = readEmoteBytes(player.server, fileName);
        if (bytes == null || bytes.length == 0) return;
        PacketDistributor.sendToPlayer(player, new NpcEmoteSyncPacket(fileName, bytes));
    }

    public static void syncEmoteToAll(MinecraftServer server, String fileName) {
        if (server == null || fileName == null || fileName.isBlank()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncEmoteToPlayer(player, fileName);
        }
    }
}
