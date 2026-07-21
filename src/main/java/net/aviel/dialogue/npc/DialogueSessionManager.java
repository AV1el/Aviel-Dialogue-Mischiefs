package net.aviel.dialogue.npc;

import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueSessionManager {
    /** Mirrors the client history stack so "back" choices stay in sync. */
    public static final class Session {
        private final UUID targetUuid;
        private final String dialogueFile;
        private String currentNode;
        private final Deque<String> history = new ArrayDeque<>();

        private Session(UUID targetUuid, String dialogueFile, String startNode) {
            this.targetUuid = targetUuid;
            this.dialogueFile = dialogueFile;
            this.currentNode = startNode;
        }

        public UUID targetUuid() {
            return targetUuid;
        }

        public String dialogueFile() {
            return dialogueFile;
        }

        public String currentNode() {
            return currentNode;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private DialogueSessionManager() {
    }

    public static void open(ServerPlayer player, UUID targetUuid, String dialogueFile, String startNode) {
        SESSIONS.put(player.getUUID(), new Session(targetUuid, dialogueFile, startNode));
    }

    public static Session get(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static boolean isTalkingTo(ServerPlayer player, UUID targetUuid) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.targetUuid.equals(targetUuid);
    }

    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void closeAllFor(UUID playerUuid) {
        SESSIONS.remove(playerUuid);
    }

    /**
     * Applies the same node transition the client performs after clicking a choice.
     * Must be called after the choice has been validated against {@link Session#currentNode()}.
     */
    public static void advance(ServerPlayer player, NpcDialogueDefinition definition, NpcDialogueDefinition.Choice choice) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if ("back".equals(choice.action())) {
            if (!session.history.isEmpty()) {
                session.currentNode = session.history.pop();
            }
            return;
        }
        if (!choice.next().isBlank() && definition.node(choice.next()) != null) {
            session.history.push(session.currentNode);
            session.currentNode = choice.next();
            return;
        }
        if (choice.close() || choice.next().isBlank()) {
            SESSIONS.remove(player.getUUID());
        }
    }
}
