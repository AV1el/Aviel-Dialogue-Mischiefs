package net.aviel.dialogue.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.Config;
import net.aviel.dialogue.network.OpenNpcTradePacket;
import net.aviel.dialogue.npc.dialogue.NpcDialoguePlayerData;
import net.aviel.dialogue.npc.storage.AdmDataPackManager;
import net.aviel.dialogue.npc.storage.DialogueFileCache;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NpcTradeService {
    private static final int MAX_TRADE_JSON_CHARS = 30000;
    private static final DialogueFileCache<NpcTradeDefinition> FILE_CACHE = new DialogueFileCache<>(MAX_TRADE_JSON_CHARS);

    private NpcTradeService() {
    }

    public static Path getGlobalTradeDirectory() {
        return DialogueStorage.tradeDirectory();
    }

    public static void invalidateCaches() {
        FILE_CACHE.clear();
    }

    public static List<String> listTradeFiles(MinecraftServer server) {
        DialogueStorage.ensureDirectories();
        Set<String> names = new LinkedHashSet<>();
        DialogueStorage.collectJsonFileNames(DialogueStorage.tradeDirectory(), names);
        names.addAll(AdmDataPackManager.TRADES.ids());
        List<String> result = new ArrayList<>(names);
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static String normalizeFileName(String input) {
        return DialogueRepository.normalizeReference(input);
    }

    public static String resolveTradeFileName(MinecraftServer server, String input) {
        String requested = normalizeFileName(input);
        if (requested.isBlank()) {
            return null;
        }
        if (DialogueRepository.isDataId(requested)) {
            return AdmDataPackManager.TRADES.contains(requested) ? requested : null;
        }
        for (String file : listTradeFiles(server)) {
            if (file.equalsIgnoreCase(requested)) {
                return file;
            }
        }
        return null;
    }

    public static String readTradeJson(MinecraftServer server, String fileName) throws IOException {
        String normalized = normalizeFileName(fileName);
        if (normalized.isBlank()) {
            throw new IOException("Invalid trade reference.");
        }
        if (DialogueRepository.isDataId(normalized)) {
            String datapack = AdmDataPackManager.TRADES.rawJson(normalized);
            if (datapack == null) {
                throw new IOException("Trade not found in datapacks: " + normalized);
            }
            return datapack;
        }
        Path path = existingTradeFile(normalized);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        if (json.length() > MAX_TRADE_JSON_CHARS) {
            throw new IOException("Trade file is too large: " + normalized);
        }
        return json;
    }

    public static NpcTradeDefinition loadTrade(MinecraftServer server, String fileName) throws IOException, JsonSyntaxException {
        String normalized = normalizeFileName(fileName);
        if (normalized.isBlank()) {
            throw new IOException("Invalid trade reference.");
        }
        if (DialogueRepository.isDataId(normalized)) {
            return NpcTradeDefinition.fromJson(readTradeJson(server, normalized));
        }
        return FILE_CACHE.get(existingTradeFile(normalized), NpcTradeDefinition::fromJson);
    }

    private static Path existingTradeFile(String normalizedFileName) throws IOException {
        DialogueStorage.ensureDirectories();
        Path path = DialogueStorage.tradeDirectory().resolve(normalizedFileName);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Trade file does not exist: " + normalizedFileName);
        }
        return path;
    }

    public static void openTrade(ServerPlayer player, Entity target, String tradeFile) {
        if (player == null || target == null || tradeFile == null || tradeFile.isBlank()
                || player.distanceToSqr(target) > Config.maxInteractDistanceSqr()) {
            return;
        }
        String normalized = normalizeFileName(tradeFile);
        if (normalized.isBlank()) {
            player.displayClientMessage(Component.literal("Invalid trade file name: " + tradeFile), true);
            return;
        }
        try {
            String resolved = resolveTradeFileName(player.server, normalized);
            if (resolved == null) {
                player.displayClientMessage(Component.literal("Trade file not found: " + normalized), true);
                return;
            }
            String json = readTradeJson(player.server, resolved);
            NpcTradeDefinition definition = NpcTradeDefinition.fromJson(json);
            PacketDistributor.sendToPlayer(player, new OpenNpcTradePacket(
                    target.getUUID(),
                    NpcDialogueService.displayNameFor(target),
                    resolved,
                    annotateLocks(player, definition, json)
            ));
        } catch (IOException | RuntimeException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not open trade {} for player {}", normalized, player.getGameProfile().getName(), ex);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            player.displayClientMessage(Component.literal("Could not open trade " + normalized + ": " + message), true);
        }
    }

    public static void executeTrade(ServerPlayer player, UUID npcUuid, String tradeFile, String offerId, int offerIndex, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        Entity target = npcUuid == null ? null : player.serverLevel().getEntity(npcUuid);
        if (target == null || player.distanceToSqr(target) > Config.maxInteractDistanceSqr()) {
            player.displayClientMessage(Component.translatable("message.adm.npc.trade.msg.too_far"), true);
            return;
        }
        int safeAmount = Math.max(1, Math.min(64, amount));
        try {
            NpcTradeDefinition definition = loadTrade(player.server, tradeFile);
            NpcTradeDefinition.Offer offer = definition.offerByIdOrIndex(offerId, offerIndex);
            if (offer == null || !isOfferAvailable(player, offer)) {
                player.displayClientMessage(Component.translatable("message.adm.npc.trade.msg.unavailable"), true);
                return;
            }
            if (!hasItems(player, offer.cost(), safeAmount)) {
                player.displayClientMessage(Component.translatable("message.adm.npc.trade.msg.not_enough"), true);
                return;
            }
            if (!removeItems(player, offer.cost(), safeAmount)) {
                player.displayClientMessage(Component.translatable("message.adm.npc.trade.msg.take_failed"), true);
                return;
            }
            giveItems(player, offer.result(), safeAmount);
            executeCommands(player, target, offer.commands(), safeAmount);
            player.displayClientMessage(Component.translatable("message.adm.npc.trade.msg.complete", offer.title(), safeAmount), true);
        } catch (IOException | RuntimeException ex) {
            AvielsDialogueMod.LOGGER.warn("Trade {} failed for player {}", tradeFile, player.getGameProfile().getName(), ex);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            player.displayClientMessage(Component.literal("Trade failed: " + message), true);
        }
    }

    public static boolean isOfferAvailable(ServerPlayer player, NpcTradeDefinition.Offer offer) {
        return isOfferUnlocked(player, offer) && itemsValid(offer.cost()) && itemsValid(offer.result());
    }

    /** Flag and tag conditions only — item validity and inventory checks are separate. */
    public static boolean isOfferUnlocked(ServerPlayer player, NpcTradeDefinition.Offer offer) {
        if (player == null || offer == null) {
            return false;
        }
        NpcDialoguePlayerData data = NpcDialoguePlayerData.get(player.server);
        for (String flag : offer.requiresFlags()) {
            if (!data.hasFlag(player.getUUID(), flag)) return false;
        }
        for (String flag : offer.missingFlags()) {
            if (data.hasFlag(player.getUUID(), flag)) return false;
        }
        for (String tag : offer.requiresTags()) {
            if (!player.getTags().contains(normalizeTag(tag))) return false;
        }
        for (String tag : offer.missingTags()) {
            if (player.getTags().contains(normalizeTag(tag))) return false;
        }
        return true;
    }

    /** Stamps a per-player {@code locked} flag into each offer of the raw JSON sent to the client. */
    private static String annotateLocks(ServerPlayer player, NpcTradeDefinition definition, String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return json;
            }
            Map<String, Boolean> lockedById = new LinkedHashMap<>();
            for (NpcTradeDefinition.Offer offer : definition.offers()) {
                lockedById.put(offer.id(), !isOfferUnlocked(player, offer));
            }
            for (String key : List.of("offers", "trades")) {
                JsonElement array = root.getAsJsonObject().get(key);
                if (array == null || !array.isJsonArray()) {
                    continue;
                }
                int index = 0;
                for (JsonElement element : array.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        JsonObject offerObject = element.getAsJsonObject();
                        String id = offerObject.has("id") && offerObject.get("id").isJsonPrimitive()
                                ? offerObject.get("id").getAsString().trim()
                                : "";
                        if (id.isBlank()) {
                            id = "offer_" + index;
                        }
                        Boolean locked = lockedById.get(id);
                        if (locked != null) {
                            offerObject.addProperty("locked", locked);
                        }
                    }
                    index++;
                }
            }
            return root.toString();
        } catch (RuntimeException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not annotate trade locks for player {}", player.getGameProfile().getName(), ex);
            return json;
        }
    }

    private static boolean itemsValid(Iterable<NpcTradeDefinition.TradeItem> items) {
        for (NpcTradeDefinition.TradeItem item : items) {
            if (DialogueItemHandler.resolveItem(item.item()) == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasItems(ServerPlayer player, Iterable<NpcTradeDefinition.TradeItem> rules, int amount) {
        for (Map.Entry<Item, Integer> entry : aggregateItems(rules, amount).entrySet()) {
            if (!DialogueItemHandler.hasItemCount(player, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean removeItems(ServerPlayer player, Iterable<NpcTradeDefinition.TradeItem> rules, int amount) {
        Map<Item, Integer> totals = aggregateItems(rules, amount);
        for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
            if (!DialogueItemHandler.hasItemCount(player, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
            if (!DialogueItemHandler.removeItems(player, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static void giveItems(ServerPlayer player, Iterable<NpcTradeDefinition.TradeItem> rules, int amount) {
        for (NpcTradeDefinition.TradeItem rule : rules) {
            Item item = DialogueItemHandler.resolveItem(rule.item());
            if (item == null) continue;
            int remaining = rule.multipliedCount(amount);
            while (remaining > 0) {
                int size = Math.min(remaining, new ItemStack(item).getMaxStackSize());
                ItemStack stack = new ItemStack(item, size);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= size;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static Map<Item, Integer> aggregateItems(Iterable<NpcTradeDefinition.TradeItem> rules, int amount) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        if (rules == null) {
            return totals;
        }
        for (NpcTradeDefinition.TradeItem rule : rules) {
            Item item = DialogueItemHandler.resolveItem(rule.item());
            if (item == null) {
                return Map.of();
            }
            totals.merge(item, rule.multipliedCount(amount), Integer::sum);
        }
        return totals;
    }

    private static void executeCommands(ServerPlayer player, Entity target, Iterable<String> commands, int amount) {
        if (commands == null) {
            return;
        }
        for (String raw : commands) {
            String command = replacePlaceholders(raw, player, target, amount).trim();
            while (command.startsWith("/")) {
                command = command.substring(1).trim();
            }
            if (command.isBlank()) continue;
            try {
                player.server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4));
            } catch (Exception ex) {
                AvielsDialogueMod.LOGGER.warn("Trade command failed for player {}: '{}'", player.getGameProfile().getName(), command, ex);
            }
        }
    }

    private static String replacePlaceholders(String value, ServerPlayer player, Entity target, int amount) {
        if (value == null) return "";
        String targetName = NpcDialogueService.displayNameFor(target);
        return value
                .replace("{player}", player.getGameProfile().getName())
                .replace("%player%", player.getGameProfile().getName())
                .replace("{player_uuid}", player.getStringUUID())
                .replace("%player_uuid%", player.getStringUUID())
                .replace("{npc}", targetName)
                .replace("%npc%", targetName)
                .replace("{npc_uuid}", target.getStringUUID())
                .replace("%npc_uuid%", target.getStringUUID())
                .replace("{amount}", Integer.toString(amount))
                .replace("%amount%", Integer.toString(amount));
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
