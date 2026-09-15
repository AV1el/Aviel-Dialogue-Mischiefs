package net.aviel.dialogue.npc;

import net.aviel.dialogue.npc.dialogue.DialogueCondition;
import net.aviel.dialogue.npc.dialogue.DialogueTranslation;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.aviel.dialogue.npc.poi.NpcPoiData;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cross-checks every dialogue, trade and NPC template and reports problems. */
public final class DialogueValidator {
    private static final Pattern EMOTE_TAG = Pattern.compile("<(?:anim|animation|emote):([^:>]+)(?::(?:loop|repeat))?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVE_TAG = Pattern.compile("<(?:moveto|walkto|goto):([^>]+)>", Pattern.CASE_INSENSITIVE);
    private static final Set<String> EMOTE_CONTROL_WORDS = Set.of("stop", "clear", "idle", "none");

    public record FileReport(String category, String name, List<String> errors, List<String> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }

        public boolean clean() {
            return errors.isEmpty() && warnings.isEmpty();
        }
    }

    private DialogueValidator() {
    }

    public static List<FileReport> validateAll(MinecraftServer server) {
        List<FileReport> reports = new ArrayList<>();
        for (String fileName : DialogueRepository.listDialogueFiles(server)) {
            reports.add(validateDialogue(server, fileName));
        }
        for (String fileName : NpcTradeService.listTradeFiles(server)) {
            reports.add(validateTrade(server, fileName));
        }
        for (String templateId : NpcTemplateService.listNpcTemplates(server)) {
            reports.add(validateTemplate(server, templateId));
        }
        return reports;
    }

    public static FileReport validateDialogue(MinecraftServer server, String fileName) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        NpcDialogueDefinition definition = null;
        try {
            definition = DialogueRepository.loadDialogue(server, fileName);
        } catch (Exception ex) {
            errors.add(message(ex));
        }
        if (definition != null) {
            checkNodes(server, definition, warnings);
            checkTranslations(server, fileName, definition, warnings);
        }
        return new FileReport("dialogue", fileName, errors, warnings);
    }

    private static void checkNodes(MinecraftServer server, NpcDialogueDefinition definition, List<String> warnings) {
        for (NpcDialogueDefinition.Node node : definition.nodes().values()) {
            for (String line : node.text()) {
                checkEmoteTags(server, node.id(), line, warnings);
                checkMoveTags(server, node.id(), line, warnings);
            }
            for (NpcDialogueDefinition.Choice choice : node.choices()) {
                checkChoice(server, definition, node.id(), choice, warnings);
            }
        }
    }

    private static void checkChoice(MinecraftServer server, NpcDialogueDefinition definition, String nodeId, NpcDialogueDefinition.Choice choice, List<String> warnings) {
        if (!choice.next().isBlank() && definition.node(choice.next()) == null) {
            warnings.add("node '" + nodeId + "': choice '" + choice.text() + "' points to missing node '" + choice.next() + "'");
        }
        if (!choice.trade().isBlank() && NpcTradeService.resolveTradeFileName(server, choice.trade()) == null) {
            warnings.add("node '" + nodeId + "': choice '" + choice.text() + "' opens missing trade '" + choice.trade() + "'");
        }
        checkItems(nodeId, choice.requiresItems(), "requires_items", warnings);
        checkItems(nodeId, choice.takeItems(), "take_items", warnings);
        checkItems(nodeId, choice.giveItems(), "give_items", warnings);
        checkAdvancements(server, nodeId, choice.requiresAdvancements(), "requires_advancements", warnings);
        checkAdvancements(server, nodeId, choice.missingAdvancements(), "missing_advancements", warnings);
        checkKills(nodeId, choice.requiresKills(), warnings);
        checkCondition(server, nodeId, choice.condition(), warnings);
    }

    private static void checkTranslations(
            MinecraftServer server,
            String fileName,
            NpcDialogueDefinition definition,
            List<String> warnings
    ) {
        for (String language : DialogueRepository.listDialogueTranslationLanguages(server, fileName)) {
            try {
                DialogueTranslation translation = DialogueRepository.loadDialogueTranslation(server, fileName, language);
                for (Map.Entry<String, DialogueTranslation.NodeTranslation> entry : translation.nodes().entrySet()) {
                    NpcDialogueDefinition.Node node = definition.node(entry.getKey());
                    if (node == null) {
                        warnings.add("translation '" + language + "' references missing node '" + entry.getKey() + "'");
                        continue;
                    }
                    for (String line : entry.getValue().text()) {
                        checkEmoteTags(server, node.id(), line, warnings);
                        checkMoveTags(server, node.id(), line, warnings);
                    }
                    checkTranslatedChoices(language, node, entry.getValue(), warnings);
                }
            } catch (Exception ex) {
                warnings.add("translation '" + language + "' could not be loaded: " + message(ex));
            }
        }
    }

    private static void checkTranslatedChoices(
            String language,
            NpcDialogueDefinition.Node node,
            DialogueTranslation.NodeTranslation translation,
            List<String> warnings
    ) {
        Set<String> choiceKeys = new java.util.HashSet<>();
        for (NpcDialogueDefinition.Choice choice : node.choices()) {
            choiceKeys.add(Integer.toString(choice.serverIndex()));
            if (!choice.id().isBlank()) {
                choiceKeys.add(choice.id());
            }
        }
        for (String key : translation.choices().keySet()) {
            if (!choiceKeys.contains(key)) {
                warnings.add("translation '" + language + "', node '" + node.id()
                        + "' references missing choice '" + key + "'");
            }
        }
    }

    private static void checkItems(String nodeId, List<NpcDialogueDefinition.ItemRule> rules, String field, List<String> warnings) {
        for (NpcDialogueDefinition.ItemRule rule : rules) {
            if (DialogueItemHandler.resolveItem(rule.item()) == null) {
                warnings.add("node '" + nodeId + "': " + field + " references unknown item '" + rule.item() + "'");
            }
        }
    }

    private static void checkAdvancements(MinecraftServer server, String nodeId, List<String> ids, String field, List<String> warnings) {
        for (String rawId : ids) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null || server == null || server.getAdvancements().get(id) == null) {
                warnings.add("node '" + nodeId + "': " + field + " references unknown advancement '" + rawId + "'");
            }
        }
    }

    private static void checkKills(String nodeId, List<NpcDialogueDefinition.KillRule> rules, List<String> warnings) {
        for (NpcDialogueDefinition.KillRule rule : rules) {
            ResourceLocation id = ResourceLocation.tryParse(rule.entity());
            if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                warnings.add("node '" + nodeId + "': requires_kills references unknown entity '" + rule.entity() + "'");
            }
        }
    }

    private static void checkCondition(
            MinecraftServer server,
            String nodeId,
            DialogueCondition condition,
            List<String> warnings
    ) {
        if (condition instanceof DialogueCondition.All all) {
            all.conditions().forEach(child -> checkCondition(server, nodeId, child, warnings));
            return;
        }
        if (condition instanceof DialogueCondition.Any any) {
            if (any.conditions().isEmpty()) {
                warnings.add("node '" + nodeId + "': condition 'any' is empty and can never pass");
            }
            any.conditions().forEach(child -> checkCondition(server, nodeId, child, warnings));
            return;
        }
        if (condition instanceof DialogueCondition.Not not) {
            checkCondition(server, nodeId, not.condition(), warnings);
            return;
        }
        if (!(condition instanceof DialogueCondition.Predicate predicate)) {
            return;
        }
        if (!DialogueConditionEvaluator.supports(predicate.type())) {
            warnings.add("node '" + nodeId + "': unknown condition type '" + predicate.type() + "'");
            return;
        }
        if (!DialogueConditionEvaluator.hasRequiredArguments(predicate)) {
            warnings.add("node '" + nodeId + "': condition '" + predicate.type() + "' is missing a required field");
            return;
        }
        checkConditionReference(server, nodeId, predicate, warnings);
    }

    private static void checkConditionReference(
            MinecraftServer server,
            String nodeId,
            DialogueCondition.Predicate predicate,
            List<String> warnings
    ) {
        switch (predicate.type()) {
            case "item", "has_item" -> {
                if (DialogueItemHandler.resolveItem(predicate.id()) == null) {
                    warnings.add("node '" + nodeId + "': item condition references unknown item '" + predicate.id() + "'");
                }
            }
            case "advancement" -> checkAdvancements(
                    server,
                    nodeId,
                    List.of(predicate.id()),
                    "condition",
                    warnings
            );
            case "kills", "kill_count" -> checkConditionEntity(nodeId, predicate.id(), warnings);
            case "dimension", "biome" -> checkResourceId(nodeId, predicate.type(), predicate.id(), warnings);
            case "game_mode", "gamemode" -> {
                String value = predicate.value().isBlank() ? predicate.id() : predicate.value();
                if (!Set.of("survival", "creative", "adventure", "spectator").contains(value.toLowerCase(Locale.ROOT))) {
                    warnings.add("node '" + nodeId + "': unknown game mode '" + value + "'");
                }
            }
            case "weather" -> {
                if (!Set.of("clear", "rain", "raining", "thunder", "thundering", "storm")
                        .contains(predicate.value().toLowerCase(Locale.ROOT))) {
                    warnings.add("node '" + nodeId + "': unknown weather '" + predicate.value() + "'");
                }
            }
            case "score", "scoreboard" -> {
                if (predicate.objective().isBlank()
                        || server == null
                        || server.getScoreboard().getObjective(predicate.objective()) == null) {
                    warnings.add("node '" + nodeId + "': unknown scoreboard objective '" + predicate.objective() + "'");
                }
            }
            default -> {
            }
        }
    }

    private static void checkConditionEntity(String nodeId, String rawId, List<String> warnings) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            warnings.add("node '" + nodeId + "': kill condition references unknown entity '" + rawId + "'");
        }
    }

    private static void checkResourceId(String nodeId, String type, String rawId, List<String> warnings) {
        if (ResourceLocation.tryParse(rawId) == null) {
            warnings.add("node '" + nodeId + "': " + type + " condition has invalid id '" + rawId + "'");
        }
    }

    /** Flags {@code <moveto:...>} tags that point at a place no marker defines. */
    private static void checkMoveTags(MinecraftServer server, String nodeId, String line, List<String> warnings) {
        if (server == null) {
            return;
        }

        Matcher matcher = MOVE_TAG.matcher(line);
        while (matcher.find()) {
            String spec = matcher.group(1).trim();
            int split = spec.indexOf(':');
            String point = split < 0 ? spec : spec.substring(split + 1).trim();
            if (point.isBlank()) {
                warnings.add("node '" + nodeId + "': moveto tag has no destination");
                continue;
            }

            if (NpcPoiData.get(server).find(point) == null) {
                warnings.add("node '" + nodeId + "': unknown point '" + point + "'");
            }
        }
    }

    private static void checkEmoteTags(MinecraftServer server, String nodeId, String line, List<String> warnings) {
        Matcher matcher = EMOTE_TAG.matcher(line);
        while (matcher.find()) {
            String emote = matcher.group(1).trim();
            if (EMOTE_CONTROL_WORDS.contains(emote.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (NpcEmoteService.resolveEmoteFileName(server, emote) == null) {
                warnings.add("node '" + nodeId + "': unknown emote '" + emote + "'");
            }
        }
    }

    public static FileReport validateTrade(MinecraftServer server, String fileName) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            NpcTradeDefinition definition = NpcTradeService.loadTrade(server, fileName);
            for (NpcTradeDefinition.Offer offer : definition.offers()) {
                checkTradeItems(offer, offer.cost(), "cost", warnings);
                checkTradeItems(offer, offer.result(), "result", warnings);
            }
        } catch (Exception ex) {
            errors.add(message(ex));
        }
        return new FileReport("trade", fileName, errors, warnings);
    }

    private static void checkTradeItems(NpcTradeDefinition.Offer offer, Iterable<NpcTradeDefinition.TradeItem> items, String field, List<String> warnings) {
        for (NpcTradeDefinition.TradeItem item : items) {
            if (DialogueItemHandler.resolveItem(item.item()) == null) {
                warnings.add("offer '" + offer.title() + "': " + field + " references unknown item '" + item.item() + "'");
            }
        }
    }

    public static FileReport validateTemplate(MinecraftServer server, String templateId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            NpcTemplateService.NpcTemplate template = NpcTemplateService.loadNpcTemplate(server, templateId);
            String dialogue = template.dialogueFile();
            if (!dialogue.isBlank() && DialogueRepository.resolveDialogueFileName(server, dialogue) == null) {
                warnings.add("references missing dialogue '" + dialogue + "'");
            }
        } catch (Exception ex) {
            errors.add(message(ex));
        }
        return new FileReport("template", templateId, errors, warnings);
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
