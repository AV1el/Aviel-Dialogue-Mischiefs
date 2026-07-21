package net.aviel.dialogue.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.NpcDialogueService;
import net.aviel.dialogue.npc.NpcTemplateService;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** {@code /adm_npc npc|set|template} subcommands that spawn or edit dialogue NPCs. */
final class NpcEditCommands {
    private NpcEditCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> npcSubtree() {
        return Commands.literal("npc")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> spawn(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("remove")
                        .executes(context -> remove(context.getSource())))
                .then(Commands.literal("remove_nearest")
                        .executes(context -> remove(context.getSource())))
                .then(Commands.literal("remove_all")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(context -> removeAll(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> rename(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("tp_here")
                        .executes(context -> teleportHere(context.getSource())))
                .then(Commands.literal("save_template")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> saveTemplate(context.getSource(), StringArgumentType.getString(context, "id"), false))
                                .then(Commands.literal("force")
                                        .executes(context -> saveTemplate(context.getSource(), StringArgumentType.getString(context, "id"), true)))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())));
    }

    static LiteralArgumentBuilder<CommandSourceStack> setSubtree() {
        return Commands.literal("set")
                .then(Commands.literal("dialogue")
                        .then(Commands.argument("file", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(DialogueRepository.listDialogueFiles(context.getSource().getServer()), builder))
                                .executes(context -> setDialogue(context.getSource(), StringArgumentType.getString(context, "file")))))
                .then(Commands.literal("clear_dialogue")
                        .executes(context -> setDialogue(context.getSource(), "")))
                .then(Commands.literal("scale")
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.25F, 3.0F))
                                .executes(context -> setScale(context.getSource(), FloatArgumentType.getFloat(context, "scale")))))
                .then(Commands.literal("invulnerable")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> setInvulnerable(context.getSource(), BoolArgumentType.getBool(context, "value")))))
                .then(Commands.literal("look_distance")
                        .then(Commands.argument("distance", FloatArgumentType.floatArg(0.0F, 64.0F))
                                .executes(context -> setLookDistance(context.getSource(), FloatArgumentType.getFloat(context, "distance")))))
                .then(Commands.literal("skin")
                        .then(Commands.argument("texture", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(DialogueStorage.listSkinFiles(), builder))
                                .executes(context -> setSkin(context.getSource(), StringArgumentType.getString(context, "texture")))))
                .then(Commands.literal("clear_skin")
                        .executes(context -> setSkin(context.getSource(), "")))
                .then(Commands.literal("model")
                        .then(Commands.argument("model", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("steve", "alex"), builder))
                                .executes(context -> setModel(context.getSource(), StringArgumentType.getString(context, "model")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> templateSubtree() {
        return Commands.literal("template")
                .then(Commands.literal("list")
                        .executes(context -> templates(context.getSource())))
                .then(Commands.literal("apply")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcTemplateService.listNpcTemplates(context.getSource().getServer()), builder))
                                .executes(context -> applyTemplate(context.getSource(), StringArgumentType.getString(context, "template")))))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcTemplateService.listNpcTemplates(context.getSource().getServer()), builder))
                                .executes(context -> spawnTemplate(context.getSource(), StringArgumentType.getString(context, "template")))))
                .then(Commands.literal("spawn_aviel")
                        .executes(context -> spawnTemplate(context.getSource(), NpcTemplateService.AVIEL_TEMPLATE_ID)));
    }

    private static int spawn(CommandSourceStack source, String name) {
        DialogueNpcEntity npc = AvielsDialogueMod.DIALOGUE_NPC.get().create(source.getLevel());
        if (npc == null) {
            return 0;
        }
        npc.moveTo(source.getPosition().x, source.getPosition().y, source.getPosition().z, source.getRotation().y, 0.0F);
        npc.setHomeYaw(source.getRotation().y);
        npc.setCustomName(Component.literal(name.isBlank() ? "NPC" : name.trim()));
        npc.setCustomNameVisible(true);
        npc.setModelScale(DialogueNpcEntity.DEFAULT_MODEL_SCALE);
        npc.setPersistenceRequired();
        source.getLevel().addFreshEntity(npc);
        selectSpawned(source, npc);
        source.sendSuccess(() -> Component.literal("Spawned dialogue NPC: " + npc.getProfileName()), true);
        return 1;
    }

    /** Spawned NPCs become the sender's selection so follow-up edit commands hit them. */
    private static void selectSpawned(CommandSourceStack source, DialogueNpcEntity npc) {
        if (source.getEntity() instanceof ServerPlayer player) {
            NpcSelections.select(player, npc);
        }
    }

    private static int spawnTemplate(CommandSourceStack source, String templateId) {
        try {
            NpcTemplateService.NpcTemplate template = NpcTemplateService.loadNpcTemplate(source.getServer(), templateId);
            DialogueNpcEntity npc = AvielsDialogueMod.DIALOGUE_NPC.get().create(source.getLevel());
            if (npc == null) {
                return 0;
            }
            npc.moveTo(source.getPosition().x, source.getPosition().y, source.getPosition().z, source.getRotation().y, 0.0F);
            npc.setHomeYaw(source.getRotation().y);
            NpcTemplateService.applyTemplate(npc, template);
            source.getLevel().addFreshEntity(npc);
            selectSpawned(source, npc);
            source.sendSuccess(() -> Component.literal("Spawned NPC template: " + template.id()), true);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Template error: " + NpcCommandTargets.message(ex)));
            return 0;
        }
    }

    private static int setDialogue(CommandSourceStack source, String file) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        String normalized = DialogueRepository.normalizeReference(file);
        if (!normalized.isBlank() && DialogueRepository.resolveDialogueFileName(source.getServer(), normalized) == null) {
            source.sendFailure(Component.literal("Dialogue file not found: " + normalized));
            return 0;
        }
        npc.setDialogueFile(normalized);
        source.sendSuccess(() -> Component.literal(normalized.isBlank()
                ? "Cleared dialogue from " + npc.getProfileName()
                : "Assigned " + normalized + " to " + npc.getProfileName()), true);
        return 1;
    }

    private static int setScale(CommandSourceStack source, float scale) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        npc.setModelScale(scale);
        source.sendSuccess(() -> Component.literal("Set scale for " + npc.getProfileName() + " to " + npc.getModelScale()), true);
        return 1;
    }

    private static int setInvulnerable(CommandSourceStack source, boolean invulnerable) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        npc.setDialogueInvulnerable(invulnerable);
        source.sendSuccess(() -> Component.literal("Set invulnerable for " + npc.getProfileName() + " to " + invulnerable), true);
        return 1;
    }

    private static int setLookDistance(CommandSourceStack source, float distance) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        npc.setLookDistance(distance);
        source.sendSuccess(() -> Component.literal("Set look distance for " + npc.getProfileName() + " to " + npc.getLookDistance()), true);
        return 1;
    }

    private static int setSkin(CommandSourceStack source, String skin) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        String normalized = NpcDialogueService.normalizeSkin(skin);
        if (!skin.isBlank() && normalized.isBlank()) {
            source.sendFailure(Component.literal("Skin must be a resource location or a PNG from config/adm-dialogues/skins, for example guard.png or adm:textures/entity/npc/guard.png"));
            return 0;
        }
        npc.setSkin(normalized);
        source.sendSuccess(() -> Component.literal(normalized.isBlank()
                ? "Cleared custom skin for " + npc.getProfileName()
                : "Set skin for " + npc.getProfileName() + " to " + normalized), true);
        return 1;
    }

    private static int setModel(CommandSourceStack source, String model) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        npc.setPlayerModel(model);
        source.sendSuccess(() -> Component.literal("Set model for " + npc.getProfileName() + " to " + npc.getPlayerModel()), true);
        return 1;
    }

    private static int applyTemplate(CommandSourceStack source, String templateId) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        try {
            NpcTemplateService.NpcTemplate template = NpcTemplateService.loadNpcTemplate(source.getServer(), templateId);
            NpcTemplateService.applyTemplate(npc, template);
            source.sendSuccess(() -> Component.literal("Applied template " + template.id() + " to " + npc.getProfileName()), true);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Template error: " + NpcCommandTargets.message(ex)));
            return 0;
        }
    }

    private static int remove(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        String name = npc.getProfileName();
        npc.discard();
        source.sendSuccess(() -> Component.literal("Removed dialogue NPC: " + name), true);
        return 1;
    }

    private static int removeAll(CommandSourceStack source, int radius) {
        List<DialogueNpcEntity> npcs = NpcCommandTargets.nearbyNpcs(source, radius);
        for (DialogueNpcEntity npc : npcs) {
            npc.discard();
        }
        int count = npcs.size();
        source.sendSuccess(() -> Component.literal("Removed " + count + " dialogue NPC(s) within " + radius + " blocks."), true);
        return count;
    }

    private static int rename(CommandSourceStack source, String name) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        String formatted = name.trim().replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
        npc.setCustomName(Component.literal(formatted.isBlank() ? "NPC" : formatted));
        source.sendSuccess(() -> Component.literal("Renamed NPC to " + npc.getProfileName()), true);
        return 1;
    }

    private static int teleportHere(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        npc.moveTo(source.getPosition().x, source.getPosition().y, source.getPosition().z, source.getRotation().y, 0.0F);
        npc.setHomeYaw(source.getRotation().y);
        source.sendSuccess(() -> Component.literal("Teleported " + npc.getProfileName() + " to you."), true);
        return 1;
    }

    private static int saveTemplate(CommandSourceStack source, String templateId, boolean force) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        String normalized = NpcTemplateService.normalizeTemplateId(templateId);
        if (normalized.isBlank()) {
            source.sendFailure(Component.literal("Invalid template id: " + templateId));
            return 0;
        }
        Path path = DialogueStorage.npcTemplateDirectory().resolve(normalized + ".json");
        if (!force && Files.isRegularFile(path)) {
            source.sendFailure(Component.literal("Template " + normalized + " already exists. Add 'force' to overwrite."));
            return 0;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, NpcTemplateService.templateJsonFor(npc), StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal("Saved template " + normalized + " to " + path), true);
            return 1;
        } catch (IOException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not save NPC template {}", path, ex);
            source.sendFailure(Component.literal("Could not save template: " + NpcCommandTargets.message(ex)));
            return 0;
        }
    }

    private static int list(CommandSourceStack source) {
        List<DialogueNpcEntity> npcs = NpcCommandTargets.nearbyNpcs(source, 64.0D);
        if (npcs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No dialogue NPCs nearby."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dialogue NPCs nearby:"), false);
        for (DialogueNpcEntity npc : npcs) {
            source.sendSuccess(() -> Component.literal("- " + npc.getProfileName()
                    + " | dialogue=" + npc.getDialogueFile()
                    + " | model=" + npc.getPlayerModel()
                    + " | scale=" + npc.getModelScale()
                    + " | skin=" + (npc.getSkin().isBlank() ? "default" : npc.getSkin())
                    + " | invulnerable=" + npc.isDialogueInvulnerable()
                    + " | look=" + npc.getLookDistance()
                    + " | " + npc.blockPosition().toShortString()), false);
        }
        return npcs.size();
    }

    private static int templates(CommandSourceStack source) {
        List<String> templates = NpcTemplateService.listNpcTemplates(source.getServer());
        source.sendSuccess(() -> Component.literal("NPC templates: " + String.join(", ", templates)), false);
        return templates.size();
    }

}
