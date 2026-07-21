package net.aviel.dialogue.npc;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber(modid = AvielsDialogueMod.MODID)
public final class NpcEmoteSyncEvents {
    private NpcEmoteSyncEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        DialogueStorage.ensureDirectories();
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof DialogueNpcEntity npc)) return;
        String fileName = npc.getNpcEmoteFileName();
        if (fileName.isBlank()) return;
        NpcEmoteService.syncEmoteToPlayer(player, fileName);
    }
}
