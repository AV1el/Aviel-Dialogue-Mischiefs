package net.aviel.dialogue.npc.social;

import com.google.gson.JsonParser;
import net.aviel.dialogue.npc.dialogue.DialogueCondition;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OpinionSystemTest {
    @Test
    void savesIndependentOpinionsAndClampsOverflow() {
        OpinionData data = new OpinionData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(0, data.score(first, "faction:wardens"));
        data.add(first, "faction:wardens", 80);
        data.add(first, "faction:wardens", Integer.MAX_VALUE);
        assertEquals(100, data.score(first, "faction:wardens"));
        data.add(first, "faction:wardens", Integer.MIN_VALUE);
        assertEquals(-100, data.score(first, "faction:wardens"));
        data.set(second, "faction:wardens", 70);
        data.set(first, "custom:captain", 40);
        OpinionData loaded = OpinionData.load(data.save(new CompoundTag(), null), null);
        assertEquals(-100, loaded.score(first, "faction:wardens"));
        assertEquals(70, loaded.score(second, "faction:wardens"));
        assertEquals(40, loaded.score(first, "custom:captain"));
        assertEquals(0, loaded.score(second, "custom:captain"));
    }

    @Test
    void hostilityAndDiscountsAreExplicitAndRoundTrip() {
        assertEquals("faction:wardens", new OpinionReference("", "wardens", "").resolve(null));
        assertEquals("custom:captain", new OpinionReference("", "", "captain").resolve(null));
        assertEquals("", new OpinionReference("npc", "", "").resolve(null));
        var social = NpcSocialProfile.read(JsonParser.parseString("""
                {"faction":"wardens","combat":true,"opinion":{"scope":"faction","hostile_at":-50,
                 "discounts":[{"min":80,"percent":20},{"min":50,"percent":10}]}}
                """).getAsJsonObject());
        assertTrue(social.opinion().hostile(-50));
        assertFalse(social.opinion().hostile(-49));
        assertFalse(OpinionPolicy.DEFAULT.hostile(-100));
        assertEquals(0, social.opinion().discount(49));
        assertEquals(10, social.opinion().discount(50));
        assertEquals(20, social.opinion().discount(80));
        assertEquals(social, NpcSocialProfile.read(social.toJson()));
        assertThrows(IllegalArgumentException.class, () -> NpcSocialProfile.read(JsonParser.parseString(
                "{\"opinion\":{\"scope\":\"faction\"}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> OpinionPolicy.read(JsonParser.parseString(
                "{\"discounts\":[{\"min\":50,\"percent\":100}]}").getAsJsonObject()));
    }

    @Test
    void dialogueParsesExplicitChangesAndOpinionGate() {
        var definition = NpcDialogueDefinition.fromJson("""
                {"nodes":{"start":{"text":"Hello","choices":[{"text":"Quest",
                 "condition":{"type":"opinion","faction":"wardens","min":80},
                 "opinion_changes":[{"amount":10},{"faction":"wardens","set":50}]}]}}}
                """);
        var choice = definition.node("start").choices().getFirst();
        assertEquals(2, choice.opinionChanges().size());
        assertEquals(10, choice.opinionChanges().getFirst().amount());
        assertTrue(choice.opinionChanges().get(1).replace());
        var restored = NpcDialogueDefinition.fromJson(definition.toJson()).node("start").choices().getFirst();
        assertEquals(choice.opinionChanges(), restored.opinionChanges());
        var condition = (DialogueCondition.Predicate) choice.condition();
        assertEquals("opinion", condition.type());
        assertEquals(80, condition.min());
        for (String change : new String[]{"{\"amount\":0.5}", "{\"amount\":1,\"set\":2}", "{\"set\":101}", "{\"scope\":\"bad\",\"amount\":1}"}) {
            assertThrows(IllegalArgumentException.class, () -> OpinionChange.read(JsonParser.parseString(
                    "{\"opinion_changes\":[" + change + "]}").getAsJsonObject()));
        }
    }
}
