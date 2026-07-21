package net.aviel.dialogue.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@code NeoForge.EVENT_BUS} before a dialogue screen is opened for a player.
 * Cancel to suppress the dialogue, or replace {@link #setDialogueFile(String)} to open
 * a different dialogue instead.
 */
public class DialogueOpenEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final Entity target;
    private String dialogueFile;

    public DialogueOpenEvent(ServerPlayer player, Entity target, String dialogueFile) {
        this.player = player;
        this.target = target;
        this.dialogueFile = dialogueFile;
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

    public void setDialogueFile(String dialogueFile) {
        this.dialogueFile = dialogueFile == null ? "" : dialogueFile;
    }
}
