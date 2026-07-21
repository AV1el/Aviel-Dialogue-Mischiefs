package net.aviel.dialogue.api;

import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.NpcDialogueService;
import net.aviel.dialogue.npc.NpcEmoteService;
import net.aviel.dialogue.npc.NpcTemplateService;
import net.aviel.dialogue.npc.NpcTradeService;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.nio.file.Path;
import java.util.Map;

/**
 * Public entry point for other mods. Dialogues, trades and emotes are referenced either by
 * config-folder file name ({@code guard.json}) or by namespaced id ({@code mymod:guard})
 * for datapack and API-registered content.
 *
 * <p>Server-side events {@code DialogueOpenEvent} and {@code DialogueChoiceEvent} are fired
 * on {@code NeoForge.EVENT_BUS}; see {@link net.aviel.dialogue.api.event}.</p>
 */
public final class AdmDialogueApi {
    public static final String MOD_ID = "adm";

    private AdmDialogueApi() {
    }

    public static Path globalRootDirectory() {
        return DialogueStorage.rootDirectory();
    }

    public static Path globalDialogueDirectory() {
        return DialogueStorage.dialogueDirectory();
    }

    public static Path globalNpcTemplateDirectory() {
        return DialogueStorage.npcTemplateDirectory();
    }

    public static Path globalEntityDialogueConfigPath() {
        return DialogueStorage.entityDialogueConfigPath();
    }

    public static Path globalTradeDirectory() {
        return DialogueStorage.tradeDirectory();
    }

    public static Path globalEmoteDirectory() {
        return DialogueStorage.emoteDirectory();
    }

    public static void registerEntityDialogue(EntityType<?> entityType, String dialogueFile) {
        if (entityType == null) {
            return;
        }
        registerEntityDialogue(BuiltInRegistries.ENTITY_TYPE.getKey(entityType), dialogueFile);
    }

    public static void registerEntityDialogue(ResourceLocation entityTypeId, String dialogueFile) {
        NpcDialogueService.registerEntityDialogue(entityTypeId, dialogueFile);
    }

    public static void clearEntityDialogue(EntityType<?> entityType) {
        if (entityType != null) {
            NpcDialogueService.clearEntityDialogue(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
        }
    }

    public static void setDefaultEntityDialogue(String dialogueFile) {
        NpcDialogueService.setDefaultEntityDialogue(dialogueFile);
    }

    public static Map<ResourceLocation, String> registeredEntityDialogues() {
        return NpcDialogueService.registeredEntityDialogues();
    }

    /**
     * Registers an in-memory dialogue under a namespaced id (e.g. {@code mymod:intro}) so it
     * can be opened without a file on disk. Throws {@link IllegalArgumentException} for
     * non-namespaced ids and {@link com.google.gson.JsonSyntaxException} for invalid dialogues.
     */
    public static void registerDialogue(String id, DialogueBuilder builder) {
        DialogueRepository.registerRuntimeDialogue(id, builder.toJsonString());
    }

    public static void registerDialogue(String id, String dialogueJson) {
        DialogueRepository.registerRuntimeDialogue(id, dialogueJson);
    }

    public static void unregisterDialogue(String id) {
        DialogueRepository.unregisterRuntimeDialogue(id);
    }

    public static void openDialogue(ServerPlayer player, Entity target, String dialogueFile) {
        NpcDialogueService.openDialogue(player, target, dialogueFile);
    }

    public static void openTrade(ServerPlayer player, Entity target, String tradeFile) {
        NpcTradeService.openTrade(player, target, tradeFile);
    }

    public static void playNpcEmote(DialogueNpcEntity npc, MinecraftServer server, String emoteFile, boolean loop) {
        if (npc == null || server == null) {
            return;
        }
        String fileName = NpcEmoteService.resolveEmoteFileName(server, emoteFile);
        if (fileName == null) {
            return;
        }
        NpcEmoteService.syncEmoteToAll(server, fileName);
        npc.playNpcEmote(fileName, npc.level().getGameTime(), loop);
    }

    public static void stopNpcEmote(DialogueNpcEntity npc) {
        if (npc != null) {
            npc.stopNpcEmote();
        }
    }

    public static void applyNpcTemplate(DialogueNpcEntity npc, String templateId, MinecraftServer server) throws Exception {
        NpcTemplateService.applyTemplate(npc, NpcTemplateService.loadNpcTemplate(server, templateId));
    }
}
