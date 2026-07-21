package net.aviel.dialogue.npc;

import net.aviel.dialogue.Config;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class DialogueEntityEvents {
    private DialogueEntityEvents() {
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof DialogueNpcEntity) {
            return;
        }
        if (!Config.ENABLE_ENTITY_DIALOGUES.get()) {
            return;
        }
        String dialogueFile = NpcDialogueService.resolveEntityDialogueFile(player.server, target);
        if (dialogueFile.isBlank()) {
            return;
        }
        NpcDialogueService.openDialogue(player, target, dialogueFile);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
