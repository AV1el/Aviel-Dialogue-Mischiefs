package net.aviel.dialogue.api;

import net.aviel.dialogue.npc.dialogue.DialogueCondition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface DialogueConditionHandler {
    boolean test(ServerPlayer player, Entity target, DialogueCondition.Predicate condition);
}
