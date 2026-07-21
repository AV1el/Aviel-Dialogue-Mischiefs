package net.aviel.dialogue.docs;

import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the shipped docs/examples content in sync with the parsers. */
class DocsExamplesTest {
    private static Path examples() {
        Path candidate = Path.of("docs", "examples");
        for (int up = 0; up < 4 && !Files.isDirectory(candidate); up++) {
            candidate = Path.of("..").resolve(candidate);
        }
        assertTrue(Files.isDirectory(candidate), "docs/examples not found from working dir " + Path.of("").toAbsolutePath());
        return candidate;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void guardDialogueParsesAndLinksResolve() throws IOException {
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(read(examples().resolve("dialogues/guard.json")));
        assertEquals("start", definition.startNode());
        for (NpcDialogueDefinition.Node node : definition.nodes().values()) {
            for (NpcDialogueDefinition.Choice choice : node.choices()) {
                if (!choice.next().isBlank()) {
                    assertNotNull(definition.node(choice.next()),
                            "node '" + node.id() + "' links to missing node '" + choice.next() + "'");
                }
            }
        }
    }

    @Test
    void guardArmoryTradeParses() throws IOException {
        NpcTradeDefinition definition = NpcTradeDefinition.fromJson(read(examples().resolve("trades/guard_armory.json")));
        assertEquals(3, definition.offers().size());
        assertNotNull(definition.offerByIdOrIndex("veteran_blade", -1));
    }
}
