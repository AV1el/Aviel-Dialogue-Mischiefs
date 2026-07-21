package net.aviel.dialogue.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.api.event.DialogueChoiceEvent;
import net.aviel.dialogue.api.event.DialogueOpenEvent;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.network.OpenNpcDialoguePacket;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.aviel.dialogue.npc.dialogue.NpcDialoguePlayerData;
import net.aviel.dialogue.npc.storage.ConfigAssetPackBuilder;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.aviel.dialogue.util.JsonReaders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class NpcDialogueService {
    private static final Map<ResourceLocation, String> API_ENTITY_DIALOGUES = new LinkedHashMap<>();
    private static String apiDefaultEntityDialogue = "";

    private NpcDialogueService() {
    }

    public static String normalizeReference(String input) {
        return DialogueRepository.normalizeReference(input);
    }

    public static String normalizePlayerModel(String input) {
        String raw = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return "slim".equals(raw) || "alex".equals(raw) ? "slim" : DialogueNpcEntity.DEFAULT_PLAYER_MODEL;
    }

    public static String normalizeSkin(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isBlank() || raw.length() > 220 || raw.contains("..")) {
            return "";
        }
        if (raw.contains(":")) {
            return ResourceLocation.tryParse(raw) == null ? "" : raw;
        }
        String local = ConfigAssetPackBuilder.normalizeConfigAssetFile(raw, "skins/", ".png", true);
        return local.isBlank() ? "" : ConfigAssetPackBuilder.CONFIG_ASSET_NAMESPACE + ":textures/entity/npc/" + local;
    }

    public static String normalizeSound(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isBlank() || raw.length() > 220 || raw.contains("..")) {
            return "";
        }
        if (raw.contains(":")) {
            return ResourceLocation.tryParse(raw) == null ? "" : raw;
        }
        String local = ConfigAssetPackBuilder.normalizeConfigAssetFile(raw, "sounds/", ".ogg", false);
        if (local.isBlank()) {
            return "";
        }
        if (local.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            local = local.substring(0, local.length() - ".ogg".length());
        }
        if (local.startsWith("dialogue/")) {
            local = local.substring("dialogue/".length());
        }
        return ConfigAssetPackBuilder.CONFIG_ASSET_NAMESPACE + ":dialogue/" + local;
    }

    public static void openDialogue(ServerPlayer player, DialogueNpcEntity npc) {
        if (player == null || npc == null) {
            return;
        }
        openDialogue(player, npc, npc.getDialogueFile());
    }

    public static void openDialogue(ServerPlayer player, Entity target, String dialogueFile) {
        if (player == null || target == null) {
            return;
        }
        if (dialogueFile == null || dialogueFile.isBlank()) {
            player.displayClientMessage(Component.literal("This entity has no dialogue file assigned."), true);
            return;
        }
        DialogueOpenEvent event = new DialogueOpenEvent(player, target, dialogueFile);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled() || event.getDialogueFile().isBlank()) {
            return;
        }
        String requestedFile = event.getDialogueFile();
        try {
            String resolvedFile = DialogueRepository.resolveDialogueFileName(player.server, requestedFile);
            if (resolvedFile == null) {
                player.displayClientMessage(Component.literal("Dialogue file not found: " + requestedFile), true);
                return;
            }
            NpcDialogueDefinition definition = DialogueRepository.loadDialogue(player.server, resolvedFile);
            String startNode = selectStartNode(definition, player);
            String json = DialogueClientJson.toClientJson(definition, player, target);
            DialogueSessionManager.open(player, target.getUUID(), resolvedFile, startNode);
            PacketDistributor.sendToPlayer(player, new OpenNpcDialoguePacket(target.getUUID(), displayNameFor(target), resolvedFile, startNode, json));
        } catch (Exception ex) {
            AvielsDialogueMod.LOGGER.warn("Could not open dialogue {} for player {}", requestedFile, player.getGameProfile().getName(), ex);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            player.displayClientMessage(Component.literal("Dialogue error: " + message), true);
        }
    }

    public static String selectStartNode(NpcDialogueDefinition definition, ServerPlayer player) {
        if (definition == null || definition.startNodes().isEmpty()) {
            return NpcDialogueDefinition.DEFAULT_START_NODE;
        }
        java.util.List<String> starts = definition.startNodes();
        if (starts.size() == 1) {
            return starts.get(0);
        }
        return starts.get(player.getRandom().nextInt(starts.size()));
    }

    public static boolean isChoiceAvailable(ServerPlayer player, Entity target, NpcDialogueDefinition.Choice choice) {
        if (player == null || target == null || choice == null) {
            return false;
        }
        NpcDialoguePlayerData data = NpcDialoguePlayerData.get(player.server);
        for (String flag : choice.requiresFlags()) {
            if (!data.hasFlag(player.getUUID(), flag)) return false;
        }
        for (String flag : choice.missingFlags()) {
            if (data.hasFlag(player.getUUID(), flag)) return false;
        }
        for (String flag : choice.requiresMissingFlags()) {
            if (data.hasFlag(player.getUUID(), flag)) return false;
        }
        for (String tag : choice.requiresTags()) {
            if (!player.getTags().contains(normalizeTag(tag))) return false;
        }
        for (String tag : choice.missingTags()) {
            if (player.getTags().contains(normalizeTag(tag))) return false;
        }
        for (String tag : choice.requiresMissingTags()) {
            if (player.getTags().contains(normalizeTag(tag))) return false;
        }
        for (String requiredChoice : choice.requiresChoices()) {
            if (!data.hasChoice(player.getUUID(), requiredChoice)) return false;
        }
        return DialogueItemHandler.hasItemRules(player, choice.requiresItems())
                && DialogueItemHandler.hasItemRules(player, choice.takeItems());
    }

    public static boolean applyChoiceActions(
            ServerPlayer player,
            Entity target,
            String dialogueFile,
            String nodeId,
            NpcDialogueDefinition.Choice choice
    ) {
        if (player == null || target == null || choice == null) {
            return false;
        }
        if (!isChoiceAvailable(player, target, choice)) {
            return false;
        }
        DialogueChoiceEvent event = new DialogueChoiceEvent(player, target, dialogueFile, nodeId, choice);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return false;
        }
        if (!DialogueItemHandler.removeItemRules(player, choice.takeItems())) {
            return false;
        }

        NpcDialoguePlayerData data = NpcDialoguePlayerData.get(player.server);
        String choiceKey = choice.id().isBlank() ? Integer.toString(choice.serverIndex()) : choice.id();
        data.recordChoice(player.getUUID(), dialogueChoiceKey(dialogueFile, nodeId, choiceKey), choice.text());
        if (!choice.id().isBlank()) {
            data.recordChoice(player.getUUID(), choice.id(), choice.text());
        }

        for (String flag : choice.setFlags()) {
            data.setFlag(player.getUUID(), flag);
        }
        for (String flag : choice.addFlags()) {
            data.setFlag(player.getUUID(), flag);
        }
        for (String flag : choice.clearFlags()) {
            data.clearFlag(player.getUUID(), flag);
        }
        for (String flag : choice.removeFlags()) {
            data.clearFlag(player.getUUID(), flag);
        }
        for (String tag : choice.addTags()) {
            String normalized = normalizeTag(tag);
            if (!normalized.isBlank()) {
                player.addTag(normalized);
            }
        }
        for (String tag : choice.removeTags()) {
            String normalized = normalizeTag(tag);
            if (!normalized.isBlank()) {
                player.removeTag(normalized);
            }
        }
        for (String command : choice.commands()) {
            executeDialogueCommand(player, target, command);
        }
        DialogueItemHandler.giveItemRules(player, choice.giveItems());
        return true;
    }

    public static String resolveEntityDialogueFile(MinecraftServer server, Entity target) {
        if (server == null || target == null || target instanceof DialogueNpcEntity) {
            return "";
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        String apiDialogue = normalizeReference(API_ENTITY_DIALOGUES.get(typeId));
        if (!apiDialogue.isBlank() && DialogueRepository.resolveDialogueFileName(server, apiDialogue) != null) {
            return apiDialogue;
        }
        String apiDefault = normalizeReference(apiDefaultEntityDialogue);
        if (!apiDefault.isBlank() && DialogueRepository.resolveDialogueFileName(server, apiDefault) != null) {
            return apiDefault;
        }
        DialogueStorage.ensureDirectories();
        Path path = DialogueStorage.entityDialogueConfigPath();
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            JsonElement element = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                return "";
            }
            JsonObject root = element.getAsJsonObject();
            String direct = JsonReaders.readMapping(root, "entities", typeId.toString());
            if (direct.isBlank()) {
                direct = JsonReaders.readMapping(root, "entity_types", typeId.toString());
            }
            if (direct.isBlank()) {
                direct = JsonReaders.readString(root, "default", "");
            }
            String normalized = normalizeReference(direct);
            return normalized.isBlank() || DialogueRepository.resolveDialogueFileName(server, normalized) == null ? "" : normalized;
        } catch (IOException | RuntimeException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not read entity dialogue mappings from {}", path, ex);
            return "";
        }
    }

    public static void registerEntityDialogue(ResourceLocation entityTypeId, String dialogueFile) {
        if (entityTypeId == null) {
            return;
        }
        String normalized = normalizeReference(dialogueFile);
        if (normalized.isBlank()) {
            API_ENTITY_DIALOGUES.remove(entityTypeId);
            return;
        }
        API_ENTITY_DIALOGUES.put(entityTypeId, normalized);
    }

    public static void clearEntityDialogue(ResourceLocation entityTypeId) {
        if (entityTypeId != null) {
            API_ENTITY_DIALOGUES.remove(entityTypeId);
        }
    }

    public static void setDefaultEntityDialogue(String dialogueFile) {
        apiDefaultEntityDialogue = normalizeReference(dialogueFile);
    }

    public static Map<ResourceLocation, String> registeredEntityDialogues() {
        return Map.copyOf(API_ENTITY_DIALOGUES);
    }

    public static String dialogueChoiceKey(String dialogueFile, String nodeId, String choiceKey) {
        String file = NpcDialoguePlayerData.normalizeKey(dialogueFile == null ? "" : dialogueFile.replace(".json", ""));
        String node = NpcDialoguePlayerData.normalizeKey(nodeId);
        String choice = NpcDialoguePlayerData.normalizeKey(choiceKey);
        if (file.isBlank() || node.isBlank() || choice.isBlank()) {
            return choice;
        }
        return file + ":" + node + ":" + choice;
    }

    private static void executeDialogueCommand(ServerPlayer player, Entity target, String rawCommand) {
        String command = replacePlaceholders(rawCommand, player, target).trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isBlank()) {
            return;
        }

        try {
            player.server.getCommands().getDispatcher().execute(
                    command,
                    player.createCommandSourceStack().withPermission(4)
            );
        } catch (CommandSyntaxException | RuntimeException ex) {
            AvielsDialogueMod.LOGGER.warn("Dialogue command failed for player {}: '{}'", player.getGameProfile().getName(), command, ex);
        }
    }

    private static String replacePlaceholders(String value, ServerPlayer player, Entity target) {
        if (value == null) return "";
        String targetName = displayNameFor(target);
        return value
                .replace("{player}", player.getGameProfile().getName())
                .replace("%player%", player.getGameProfile().getName())
                .replace("{player_uuid}", player.getStringUUID())
                .replace("%player_uuid%", player.getStringUUID())
                .replace("{npc}", targetName)
                .replace("%npc%", targetName)
                .replace("{entity}", targetName)
                .replace("%entity%", targetName)
                .replace("{npc_uuid}", target.getStringUUID())
                .replace("%npc_uuid%", target.getStringUUID())
                .replace("{entity_uuid}", target.getStringUUID())
                .replace("%entity_uuid%", target.getStringUUID());
    }

    public static String displayNameFor(Entity target) {
        if (target instanceof DialogueNpcEntity npc) {
            return npc.getProfileName();
        }
        Component name = target.getDisplayName();
        return name == null ? "Entity" : name.getString();
    }

    private static String normalizeTag(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isBlank() || value.length() > 64 || value.contains(" ")) {
            return "";
        }
        return value;
    }
}
