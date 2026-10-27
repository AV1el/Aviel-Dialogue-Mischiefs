package net.aviel.dialogue.npc.storage;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NpcTemplateProfilesTest {
    @TempDir
    Path directory;

    @Test
    void paletteSnapshotReplacesLegacyBackgroundWithoutChangingSharedDialogueReference() throws IOException {
        Path file = directory.resolve("palette_guard.json");
        Files.writeString(file, "{\"dialogue\":\"guard.json\",\"dialogue_color\":\"#123456\"}");
        var snapshot = JsonParser.parseString("""
                {"dialogue":"guard.json","dialogue_style":{"border_color":"#ABCDEF","text_color":"#FEDCBA"}}
                """).getAsJsonObject();
        NpcTemplateProfiles.saveSettings(directory, "palette_guard", net.aviel.dialogue.entity.ai.NpcCombatProfile.DEFAULT, null, snapshot);
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(snapshot.get("dialogue_style"), saved.get("dialogue_style"));
        assertFalse(saved.has("dialogue_color"));
        assertEquals("guard.json", saved.get("dialogue").getAsString());
    }

    @Test
    void editorSavePreservesJsonOnlyFactionSettingsAndStoresDialogueColor() throws IOException {
        Path file = directory.resolve("json_faction.json");
        var original = JsonParser.parseString("""
                {"social":{"faction":"wardens","opinion":{"scope":"faction","hostile_at":-70},"extension":true}}
                """).getAsJsonObject();
        Files.writeString(file, original.toString());
        var snapshot = JsonParser.parseString("{\"dialogue_color\":\"#F6123456\"}").getAsJsonObject();
        NpcTemplateProfiles.saveSettings(directory, "json_faction", net.aviel.dialogue.entity.ai.NpcCombatProfile.DEFAULT, null, snapshot);
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(original.get("social"), saved.get("social"));
        assertEquals("#F6123456", saved.get("dialogue_color").getAsString());
    }

    @Test
    void unifiedEditorSnapshotSavesAppearanceAndClearsRemovedEquipment() throws IOException {
        Path file = directory.resolve("display_guard.json");
        Files.writeString(file, """
                {"name":"Old","dialogue":"old.json","custom":true,"equipment":{"head":"minecraft:iron_helmet"}}
                """);
        var snapshot = JsonParser.parseString("""
                {"name":"New","dialogue":"new.json","model":"slim","scale":1.2}
                """).getAsJsonObject();
        NpcTemplateProfiles.saveSettings(directory, "display_guard", net.aviel.dialogue.entity.ai.NpcCombatProfile.DEFAULT,
                net.aviel.dialogue.npc.social.NpcSocialProfile.DEFAULT, snapshot);
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("New", saved.get("name").getAsString());
        assertEquals("new.json", saved.get("dialogue").getAsString());
        assertEquals("slim", saved.get("model").getAsString());
        assertFalse(saved.has("equipment"));
        assertTrue(saved.get("custom").getAsBoolean());
        NpcTemplateProfiles.saveSettings(directory, "new_guard", net.aviel.dialogue.entity.ai.NpcCombatProfile.DEFAULT,
                net.aviel.dialogue.npc.social.NpcSocialProfile.DEFAULT, snapshot);
        assertTrue(Files.isRegularFile(directory.resolve("new_guard.json")));
    }

    @Test
    void combinedSaveKeepsExtensionsAndRemovesDisabledHostilityAndOldScope() throws IOException {
        Path file = directory.resolve("social_guard.json");
        Files.writeString(file, """
                {"dialogue":"guard.json","equipment":{"mainhand":"minecraft:iron_sword"},
                 "combat":{"health":20,"custom_ability":"fire"},
                 "social":{"faction":"wardens","custom_field":true,
                  "opinion":{"scope":"faction","hostile_at":-50,"custom_rule":"keep"}}}
                """);
        var social = net.aviel.dialogue.npc.social.NpcSocialProfile.read(JsonParser.parseString("""
                {"faction":"bandits","combat":true,"opinion":{"key":"captain",
                "discounts":[{"min":80,"percent":20}]}}
                """).getAsJsonObject());
        var combat = net.aviel.dialogue.entity.ai.NpcCombatProfile.DEFAULT;
        NpcTemplateProfiles.saveSettings(directory, "social_guard", combat, social);
        assertEquals(social, NpcTemplateProfiles.readSocial(directory, "social_guard"));
        assertEquals(combat, NpcTemplateProfiles.read(directory, "social_guard"));
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("guard.json", saved.get("dialogue").getAsString());
        assertTrue(saved.getAsJsonObject("social").get("custom_field").getAsBoolean());
        assertEquals("fire", saved.getAsJsonObject("combat").get("custom_ability").getAsString());
        var opinion = saved.getAsJsonObject("social").getAsJsonObject("opinion");
        assertFalse(opinion.has("scope"));
        assertFalse(opinion.has("hostile_at"));
        assertEquals("keep", opinion.get("custom_rule").getAsString());
    }

    @Test
    void editsTemplateWithoutLosingDialogueEquipmentOrExtensionFields() throws IOException {
        Path file = directory.resolve("guard.json");
        Files.writeString(file, """
                {"name":"Guard","dialogue":"guard.json","equipment":{"mainhand":"minecraft:iron_sword"},
                "extension":true,"combat":{"health":20,"custom_ability":"fire"}}
                """);
        NpcTemplateProfiles.edit(directory, "guard", json -> json.addProperty("health", 60));
        assertEquals(60, NpcTemplateProfiles.read(directory, "guard").health());
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("guard.json", saved.get("dialogue").getAsString());
        assertEquals("minecraft:iron_sword", saved.getAsJsonObject("equipment").get("mainhand").getAsString());
        assertTrue(saved.get("extension").getAsBoolean());
        assertEquals("fire", saved.getAsJsonObject("combat").get("custom_ability").getAsString());
        String before = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> NpcTemplateProfiles.edit(directory, "guard", json -> json.addProperty("health", -1)));
        assertEquals(before, Files.readString(file));
        assertThrows(IOException.class, () -> NpcTemplateProfiles.read(directory, "../guard"));
        assertThrows(IOException.class, () -> NpcTemplateProfiles.read(directory, "C:guard"));
    }
}
