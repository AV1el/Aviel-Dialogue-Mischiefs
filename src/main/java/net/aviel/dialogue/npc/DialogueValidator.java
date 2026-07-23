package net.aviel.dialogue.npc;

import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.aviel.dialogue.npc.poi.NpcPoiData;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    }

    private static void checkItems(String nodeId, List<NpcDialogueDefinition.ItemRule> rules, String field, List<String> warnings) {
        for (NpcDialogueDefinition.ItemRule rule : rules) {
            if (DialogueItemHandler.resolveItem(rule.item()) == null) {
                warnings.add("node '" + nodeId + "': " + field + " references unknown item '" + rule.item() + "'");
            }
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
