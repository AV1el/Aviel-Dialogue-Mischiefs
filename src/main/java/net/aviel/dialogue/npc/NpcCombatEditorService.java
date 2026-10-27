package net.aviel.dialogue.npc;

import com.google.gson.JsonParser;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.entity.ai.NpcCombatProfile;
import net.aviel.dialogue.entity.ai.NpcCompanion;
import net.aviel.dialogue.network.OpenNpcCombatEditorPacket;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.aviel.dialogue.npc.storage.NpcTemplateProfiles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.util.Locale;

final class NpcCombatEditorService {
    private NpcCombatEditorService() {
    }

    static void handle(ServerPlayer player, DialogueNpcEntity npc, String action, String value) {
        String template = npc.getTemplateId();
        try {
            switch (action) {
                case "combat_open" -> {
                    template = value;
                    var loaded = NpcTemplateService.loadNpcTemplate(player.server, template);
                    reply(player, npc, template, loaded.combat() == null ? NpcCombatProfile.DEFAULT : loaded.combat(), loaded.social(), "");
                }
                case "combat_save" -> {
                    var request = JsonParser.parseString(value).getAsJsonObject();
                    template = request.get("template").getAsString();
                    var profile = NpcCombatProfile.fromJson(request.getAsJsonObject("profile"));
                    var social = NpcTemplateService.loadNpcTemplate(player.server, template).social();
                    var appearance = request.has("appearance") && request.get("appearance").getAsBoolean()
                            ? JsonParser.parseString(NpcTemplateService.templateJsonFor(npc)).getAsJsonObject() : null;
                    NpcTemplateProfiles.saveSettings(DialogueStorage.npcTemplateDirectory(), template, profile, null, appearance);
                    reply(player, npc, template, profile, social, "saved");
                }
                case "combat_apply" -> {
                    template = value;
                    NpcTemplateService.applyTemplate(npc, NpcTemplateService.loadNpcTemplate(player.server, template));
                    reply(player, npc, template, npc.companion().profile(), npc.social(), "applied");
                }
                case "combat_mode" -> {
                    var mode = NpcCompanion.Mode.valueOf(value.toUpperCase(Locale.ROOT));
                    npc.companion().claim(player.getUUID());
                    npc.companion().configure(mode, mode == NpcCompanion.Mode.FOLLOW ? player.getUUID() : null);
                }
                case "combat_toggle" -> {
                    if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid combat toggle");
                    npc.companion().setCombat(Boolean.parseBoolean(value));
                }
                default -> throw new IllegalArgumentException("Unknown combat editor action");
            }
        } catch (IOException | RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            player.displayClientMessage(Component.literal("Combat editor: " + message), false);
            reply(player, npc, NpcTemplateService.normalizeTemplateId(template), npc.companion().profile(), npc.social(), "error");
        }
    }

    private static void reply(ServerPlayer player, DialogueNpcEntity npc, String template, NpcCombatProfile profile,
                              net.aviel.dialogue.npc.social.NpcSocialProfile social, String status) {
        PacketDistributor.sendToPlayer(player, new OpenNpcCombatEditorPacket(npc.getUUID(), template, profile.toJson().toString(),
                npc.companion().mode(), npc.companion().combatEnabled(), status, social.toJson().toString(),
                net.aviel.dialogue.npc.social.NpcSocialService.factionIds()));
    }
}
