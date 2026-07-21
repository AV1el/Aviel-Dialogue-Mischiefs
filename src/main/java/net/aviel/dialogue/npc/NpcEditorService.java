package net.aviel.dialogue.npc;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.Config;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.network.OpenNpcEditorPacket;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** Server side of the Shift+right-click NPC editor: permission gate and action handling. */
public final class NpcEditorService {
    private NpcEditorService() {
    }

    /** Editing needs the same permission level as {@code /adm_npc} — ops or cheats. */
    public static boolean canEdit(ServerPlayer player) {
        return player != null && player.hasPermissions(Config.COMMAND_PERMISSION_LEVEL.get());
    }

    public static void openEditor(ServerPlayer player, DialogueNpcEntity npc) {
        if (!canEdit(player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenNpcEditorPacket(
                npc.getId(),
                npc.getUUID(),
                DialogueRepository.listDialogueFiles(player.server),
                DialogueStorage.listSkinFiles(),
                NpcTemplateService.listNpcTemplates(player.server)
        ));
    }

    public static void handleAction(ServerPlayer player, java.util.UUID npcUuid, String action, String value, float number) {
        if (!canEdit(player)) {
            AvielsDialogueMod.LOGGER.warn("Player {} tried NPC editor action '{}' without permission", player.getGameProfile().getName(), action);
            return;
        }
        Entity entity = player.serverLevel().getEntity(npcUuid);
        if (!(entity instanceof DialogueNpcEntity npc)
                || player.distanceToSqr(npc) > 64.0D * 64.0D) {
            return;
        }
        apply(player, npc, action == null ? "" : action.toLowerCase(Locale.ROOT), value == null ? "" : value, number);
    }

    private static void apply(ServerPlayer player, DialogueNpcEntity npc, String action, String value, float number) {
        switch (action) {
            case "name" -> {
                String formatted = value.trim().replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
                npc.setCustomName(Component.literal(formatted.isBlank() ? "NPC" : formatted));
            }
            case "dialogue" -> {
                String normalized = DialogueRepository.normalizeReference(value);
                if (normalized.isBlank() || DialogueRepository.resolveDialogueFileName(player.server, normalized) != null) {
                    npc.setDialogueFile(normalized);
                }
            }
            case "skin" -> npc.setSkin(NpcDialogueService.normalizeSkin(value));
            case "model" -> npc.setPlayerModel(value);
            case "invulnerable" -> npc.setDialogueInvulnerable(number >= 0.5F);
            case "name_visible" -> npc.setCustomNameVisible(number >= 0.5F);
            case "scale" -> npc.setModelScale(Mth.clamp(number, 0.25F, 3.0F));
            case "look" -> npc.setLookDistance(Mth.clamp(number, 0.0F, 64.0F));
            case "template" -> applyTemplate(player, npc, value);
            case "equip_from_me" -> {
                for (EquipmentSlot slot : NpcEquipment.SLOTS) {
                    npc.setItemSlot(slot, player.getItemBySlot(slot).copy());
                }
            }
            case "equip_clear" -> NpcEquipment.clearAll(npc);
            case "remove" -> {
                AvielsDialogueMod.LOGGER.info("NPC {} removed via editor by {}", npc.getProfileName(), player.getGameProfile().getName());
                npc.discard();
            }
            default -> AvielsDialogueMod.LOGGER.warn("Unknown NPC editor action '{}' from {}", action, player.getGameProfile().getName());
        }
    }

    private static void applyTemplate(ServerPlayer player, DialogueNpcEntity npc, String templateId) {
        try {
            NpcTemplateService.applyTemplate(npc, NpcTemplateService.loadNpcTemplate(player.server, templateId));
        } catch (Exception ex) {
            AvielsDialogueMod.LOGGER.warn("Editor could not apply template {} for {}", templateId, player.getGameProfile().getName(), ex);
            player.displayClientMessage(Component.literal("Template error: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())), true);
        }
    }
}
