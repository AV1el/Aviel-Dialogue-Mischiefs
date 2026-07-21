package net.aviel.dialogue;

import net.aviel.dialogue.client.renderer.DialogueNpcRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AvielsDialogueMod.MODID, value = Dist.CLIENT)
public final class AvielsDialogueModClient {
    private AvielsDialogueModClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AvielsDialogueMod.DIALOGUE_NPC.get(), DialogueNpcRenderer::new);
    }
}
