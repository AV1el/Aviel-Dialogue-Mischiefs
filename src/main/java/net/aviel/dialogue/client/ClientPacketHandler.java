package net.aviel.dialogue.client;

import net.aviel.dialogue.network.OpenNpcEditorPacket;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void openDialogue(UUID npcUuid, String npcName, String dialogueFile, String startNode, String rawJson) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new NpcDialogueScreen(npcUuid, npcName, dialogueFile, startNode, rawJson));
    }

    public static void openTrade(UUID npcUuid, String npcName, String tradeFile, String rawJson) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new NpcTradeScreen(npcUuid, npcName, tradeFile, rawJson));
    }

    public static void handleNpcEmoteSync(String emoteFileName, byte[] jsonBytes) {
        NpcEmoteClientCache.putEmote(emoteFileName, jsonBytes);
    }

    public static void openNpcEditor(OpenNpcEditorPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new NpcEditorScreen(packet));
    }
}
