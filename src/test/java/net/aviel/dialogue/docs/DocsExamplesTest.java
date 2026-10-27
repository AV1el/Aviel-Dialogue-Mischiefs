package net.aviel.dialogue.docs;

import net.aviel.dialogue.npc.dialogue.DialogueCondition;
import net.aviel.dialogue.npc.dialogue.DialogueTranslation;
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
    void factionOpinionExamplesParseTogether() throws IOException {
        var factions = net.aviel.dialogue.npc.social.FactionRelations.parse(com.google.gson.JsonParser.parseString(
                read(examples().resolve("factions.json"))).getAsJsonObject());
        assertEquals(net.aviel.dialogue.npc.social.FactionRelations.Relation.HOSTILE, factions.relation("wardens", "bandits"));
        var social = net.aviel.dialogue.npc.social.NpcSocialProfile.read(com.google.gson.JsonParser.parseString(
                read(examples().resolve("npc_templates/opinion_warden.json"))).getAsJsonObject().getAsJsonObject("social"));
        assertEquals(20, social.opinion().discount(80));
        var dialogue = NpcDialogueDefinition.fromJson(read(examples().resolve("dialogues/opinion_warden.json")));
        assertEquals(2, dialogue.node("start").choices().get(1).opinionChanges().size());
        assertEquals(1, NpcTradeDefinition.fromJson(read(examples().resolve("trades/opinion_shop.json"))).offers().size());
    }

    @Test
    void companionTemplateContainsValidCombatProfile() throws IOException {
        var json = com.google.gson.JsonParser.parseString(read(examples().resolve("npc_templates/companion_guard.json"))).getAsJsonObject();
        var profile = net.aviel.dialogue.entity.ai.NpcCombatProfile.fromJson(json.getAsJsonObject("combat"));
        assertEquals(40, profile.health());
        assertEquals(net.aviel.dialogue.entity.ai.NpcCombatProfile.Policy.HOSTILE, profile.policy());
        assertTrue(Files.isRegularFile(examples().resolve("dialogues").resolve(json.get("dialogue").getAsString())));
    }

    @Test
    void companionCommandsUseExplicitDialogueTarget() throws IOException {
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(read(examples().resolve("dialogues/companion.json")));
        assertEquals(6, definition.node("start").choices().size());
        for (var choice : definition.node("start").choices()) {
            assertTrue(choice.commands().getFirst().startsWith("npc companion {npc_uuid} "));
            assertNotNull(definition.node(choice.next()));
        }
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

    @Test
    void translatedHunterQuestParses() throws IOException {
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(
                read(examples().resolve("dialogues/hunter_quest.json"))
        );
        DialogueTranslation translation = DialogueTranslation.fromJson(
                read(examples().resolve("dialogues/langs/ru_ru/hunter_quest.json"))
        );
        NpcDialogueDefinition.Choice completion = definition.node("start").choices().get(0);
        assertEquals("Дорожный смотритель", translation.titleOr(definition.title()));
        assertEquals("Я с ними разобрался.", translation.choiceTextFor(definition.node("start"), completion));
        assertTrue(completion.condition() instanceof DialogueCondition.All);
    }
}
