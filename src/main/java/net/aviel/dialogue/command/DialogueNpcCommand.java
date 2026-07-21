package net.aviel.dialogue.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.DialogueValidator;
import net.aviel.dialogue.npc.NpcEmoteService;
import net.aviel.dialogue.npc.NpcEquipment;
import net.aviel.dialogue.npc.NpcTradeService;
import net.aviel.dialogue.npc.storage.ConfigAssetPackBuilder;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DialogueNpcCommand {
    private DialogueNpcCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("adm_npc")
                .requires(source -> source.hasPermission(net.aviel.dialogue.Config.COMMAND_PERMISSION_LEVEL.get()))
                .then(NpcEditCommands.npcSubtree())
                .then(NpcEditCommands.setSubtree())
                .then(NpcEditCommands.templateSubtree())
                .then(NpcEquipCommands.subtree(buildContext))
                .then(Commands.literal("select")
                        .executes(context -> select(context.getSource(), ""))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> select(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("deselect")
                        .executes(context -> deselect(context.getSource())))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource())))
                .then(Commands.literal("dialogue")
                        .then(Commands.literal("files")
                                .executes(context -> files(context.getSource())))
                        .then(Commands.literal("reload")
                                .executes(context -> reload(context.getSource()))))
                .then(Commands.literal("validate")
                        .executes(context -> validate(context.getSource())))
                .then(Commands.literal("emote")
                        .then(Commands.literal("files")
                                .executes(context -> emoteFiles(context.getSource())))
                        .then(Commands.literal("play")
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcEmoteService.listEmoteFiles(context.getSource().getServer()), builder))
                                        .executes(context -> playEmote(context.getSource(), StringArgumentType.getString(context, "file"), false))
                                        .then(Commands.argument("loop", BoolArgumentType.bool())
                                                .executes(context -> playEmote(context.getSource(), StringArgumentType.getString(context, "file"), BoolArgumentType.getBool(context, "loop"))))))
                        .then(Commands.literal("stop")
                                .executes(context -> stopEmote(context.getSource()))))
                .then(Commands.literal("trade")
                        .then(Commands.literal("files")
                                .executes(context -> tradeFiles(context.getSource())))
                        .then(Commands.literal("open")
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcTradeService.listTradeFiles(context.getSource().getServer()), builder))
                                        .executes(context -> openTrade(context.getSource(), StringArgumentType.getString(context, "file"))))))
                .then(Commands.literal("entity")
                        .then(Commands.literal("config_path")
                                .executes(context -> entityConfigPath(context.getSource()))))
                .then(Commands.literal("assets")
                        .then(Commands.literal("path")
                                .executes(context -> assetsPath(context.getSource())))
                        .then(Commands.literal("reload")
                                .executes(context -> reloadAssets(context.getSource())))));
    }

    private static int select(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can select NPCs."));
            return 0;
        }
        DialogueNpcEntity npc = name.isBlank() ? preferLookedAt(source) : findByName(source, name);
        if (npc == null) {
            source.sendFailure(Component.literal(name.isBlank()
                    ? "No dialogue NPC in sight or nearby."
                    : "No dialogue NPC named '" + name + "' within 64 blocks."));
            return 0;
        }
        NpcSelections.select(player, npc);
        source.sendSuccess(() -> Component.literal("Selected " + npc.getProfileName() + ". Edit commands now target it; /adm_npc deselect to release."), false);
        return 1;
    }

    private static DialogueNpcEntity preferLookedAt(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcSelections.lookedAt(source);
        return npc != null ? npc : NpcCommandTargets.nearestNpc(source);
    }

    private static DialogueNpcEntity findByName(CommandSourceStack source, String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        return NpcCommandTargets.nearbyNpcs(source, 64.0D).stream()
                .filter(npc -> npc.getProfileName().toLowerCase(Locale.ROOT).contains(needle))
                .min(Comparator.comparingDouble(npc -> npc.distanceToSqr(source.getPosition())))
                .orElse(null);
    }

    private static int deselect(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            NpcSelections.deselect(player);
            source.sendSuccess(() -> Component.literal("Selection cleared."), false);
            return 1;
        }
        return 0;
    }

    private static int info(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("NPC " + npc.getProfileName() + " (" + npc.getStringUUID() + ")"), false);
        source.sendSuccess(() -> Component.literal("- dialogue: " + (npc.getDialogueFile().isBlank() ? "none" : npc.getDialogueFile())), false);
        source.sendSuccess(() -> Component.literal("- template: " + (npc.getTemplateId().isBlank() ? "none" : npc.getTemplateId())), false);
        source.sendSuccess(() -> Component.literal("- model: " + npc.getPlayerModel() + ", scale: " + npc.getModelScale() + ", look: " + npc.getLookDistance()), false);
        source.sendSuccess(() -> Component.literal("- skin: " + (npc.getSkin().isBlank() ? "default" : npc.getSkin())), false);
        source.sendSuccess(() -> Component.literal("- invulnerable: " + npc.isDialogueInvulnerable() + ", pos: " + npc.blockPosition().toShortString()), false);
        Map<String, String> equipment = NpcEquipment.summary(npc);
        source.sendSuccess(() -> Component.literal("- equipment: " + (equipment.isEmpty() ? "none"
                : String.join(", ", equipment.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()))), false);
        return 1;
    }

    private static int files(CommandSourceStack source) {
        List<String> files = DialogueRepository.listDialogueFiles(source.getServer());
        if (files.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No dialogue JSON files found in " + DialogueStorage.dialogueDirectory()), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dialogue files: " + String.join(", ", files)), false);
        return files.size();
    }

    private static int reload(CommandSourceStack source) {
        DialogueRepository.invalidateCaches();
        NpcTradeService.invalidateCaches();
        ConfigAssetPackBuilder.prepare();
        source.sendSuccess(() -> Component.literal("Cleared dialogue caches and regenerated config assets."), true);
        return validate(source);
    }

    private static int validate(CommandSourceStack source) {
        List<DialogueValidator.FileReport> reports = DialogueValidator.validateAll(source.getServer());
        int valid = 0;
        for (DialogueValidator.FileReport report : reports) {
            valid += report.valid() ? 1 : 0;
            reportToSource(source, report);
        }
        int total = reports.size();
        int validCount = valid;
        source.sendSuccess(() -> Component.literal("Validated " + total + " files: " + validCount + " valid."), true);
        return validCount;
    }

    private static void reportToSource(CommandSourceStack source, DialogueValidator.FileReport report) {
        String prefix = "[" + report.category() + "] " + report.name();
        if (report.clean()) {
            source.sendSuccess(() -> Component.literal("[OK] " + prefix), false);
            return;
        }
        for (String error : report.errors()) {
            source.sendFailure(Component.literal("[ERROR] " + prefix + ": " + error));
        }
        for (String warning : report.warnings()) {
            source.sendSuccess(() -> Component.literal("[WARN] " + prefix + ": " + warning), false);
        }
    }

    private static int emoteFiles(CommandSourceStack source) {
        List<String> files = NpcEmoteService.listEmoteFiles(source.getServer());
        if (files.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No emote JSON files found in " + NpcEmoteService.getGlobalEmoteDirectory()), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Emote files: " + String.join(", ", files)), false);
        return files.size();
    }

    private static int playEmote(CommandSourceStack source, String file, boolean loop) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        String fileName = NpcEmoteService.resolveEmoteFileName(source.getServer(), file);
        if (fileName == null) {
            source.sendFailure(Component.literal("Emote file not found: " + file));
            return 0;
        }
        NpcEmoteService.syncEmoteToAll(source.getServer(), fileName);
        npc.playNpcEmote(fileName, npc.level().getGameTime(), loop);
        source.sendSuccess(() -> Component.literal("Playing emote " + fileName + " on " + npc.getProfileName() + (loop ? " (looping)" : "")), true);
        return 1;
    }

    private static int stopEmote(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        npc.stopNpcEmote();
        source.sendSuccess(() -> Component.literal("Stopped emote on " + npc.getProfileName()), true);
        return 1;
    }

    private static int tradeFiles(CommandSourceStack source) {
        List<String> files = NpcTradeService.listTradeFiles(source.getServer());
        if (files.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No trade JSON files found in " + NpcTradeService.getGlobalTradeDirectory()), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Trade files: " + String.join(", ", files)), false);
        return files.size();
    }

    private static int openTrade(CommandSourceStack source, String file) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can open a trade screen."));
            return 0;
        }
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        String fileName = NpcTradeService.resolveTradeFileName(source.getServer(), file);
        if (fileName == null) {
            source.sendFailure(Component.literal("Trade file not found: " + file));
            return 0;
        }
        NpcTradeService.openTrade(player, npc, fileName);
        return 1;
    }

    private static int entityConfigPath(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Entity dialogue config path: " + DialogueStorage.entityDialogueConfigPath()), false);
        return 1;
    }

    private static int assetsPath(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("ADM skin folder: " + DialogueStorage.skinDirectory()), false);
        source.sendSuccess(() -> Component.literal("ADM sound folder: " + DialogueStorage.soundDirectory()), false);
        source.sendSuccess(() -> Component.literal("Generated resource pack: " + DialogueStorage.resourcePackDirectory()), false);
        return 1;
    }

    private static int reloadAssets(CommandSourceStack source) {
        ConfigAssetPackBuilder.prepare();
        source.sendSuccess(() -> Component.literal("Regenerated ADM config assets. Reload client resources with F3+T or restart the client to pick up new textures and sounds."), true);
        return 1;
    }
}
