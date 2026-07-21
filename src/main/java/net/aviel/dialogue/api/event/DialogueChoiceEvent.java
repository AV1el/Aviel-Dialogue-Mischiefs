package net.aviel.dialogue.api.event;

import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@code NeoForge.EVENT_BUS} after a dialogue choice passed validation but
 * before its actions (flags, items, commands) are applied. Cancel to reject the choice.
 */
public class DialogueChoiceEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final Entity target;
    private final String dialogueFile;
    private final String nodeId;
    private final NpcDialogueDefinition.Choice choice;

    public DialogueChoiceEvent(ServerPlayer player, Entity target, String dialogueFile, String nodeId, NpcDialogueDefinition.Choice choice) {
        this.player = player;
        this.target = target;
        this.dialogueFile = dialogueFile;
        this.nodeId = nodeId;
        this.choice = choice;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getTarget() {
        return target;
    }

    public String getDialogueFile() {
        return dialogueFile;
    }

    public String getNodeId() {
        return nodeId;
    }

    public NpcDialogueDefinition.Choice getChoice() {
        return choice;
    }
}
