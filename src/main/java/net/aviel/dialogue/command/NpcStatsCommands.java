package net.aviel.dialogue.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.aviel.dialogue.entity.ai.NpcCombatProfile;
import net.aviel.dialogue.npc.NpcTemplateService;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.aviel.dialogue.npc.storage.NpcTemplateProfiles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class NpcStatsCommands {
    private NpcStatsCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        var stats = Commands.literal("stats").executes(context -> {
            try {
                var profile = NpcTemplateProfiles.read(DialogueStorage.npcTemplateDirectory(), StringArgumentType.getString(context, "template"));
                context.getSource().sendSuccess(() -> Component.literal("Template combat stats: " + profile.toJson()), false);
                return 1;
            } catch (IOException ex) {
                context.getSource().sendFailure(Component.literal(ex.getMessage()));
                return 0;
            }
        });
        for (String stat : List.of("health", "damage", "armor", "speed", "attack_speed", "attack_range", "knockback", "knockback_resistance", "radius", "movement_multiplier")) {
            stats.then(Commands.literal(stat).then(Commands.argument("value", DoubleArgumentType.doubleArg())
                    .executes(context -> edit(context.getSource(), StringArgumentType.getString(context, "template"),
                            json -> json.addProperty(stat, DoubleArgumentType.getDouble(context, "value"))))));
        }
        var policies = Commands.literal("policy");
        for (var policy : NpcCombatProfile.Policy.values()) {
            policies.then(Commands.literal(policy.name().toLowerCase(Locale.ROOT)).executes(context ->
                    edit(context.getSource(), StringArgumentType.getString(context, "template"), json -> json.addProperty("policy", policy.name()))));
        }
        stats.then(policies);
        var template = Commands.argument("template", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcTemplateService.listNpcTemplates(context.getSource().getServer()), builder))
                .then(stats).then(Commands.literal("apply").executes(context -> {
                    var npc = NpcCommandTargets.requireTarget(context.getSource());
                    if (npc == null) return 0;
                    try {
                        NpcTemplateService.applyTemplate(npc, NpcTemplateService.loadNpcTemplate(context.getSource().getServer(),
                                StringArgumentType.getString(context, "template")));
                        context.getSource().sendSuccess(() -> Component.literal("Template applied to selected NPC."), true);
                        return 1;
                    } catch (IOException ex) {
                        context.getSource().sendFailure(Component.literal(ex.getMessage()));
                        return 0;
                    }
                }));
        return Commands.literal("template").then(template);
    }

    private static int edit(CommandSourceStack source, String template, java.util.function.Consumer<com.google.gson.JsonObject> change) {
        try {
            NpcTemplateProfiles.edit(DialogueStorage.npcTemplateDirectory(), template, change);
            source.sendSuccess(() -> Component.literal("Saved stats in template '" + template
                    + "'. Use /npc template " + template + " apply to update the selected NPC."), true);
            return 1;
        } catch (IOException | IllegalArgumentException ex) {
            source.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }
}
