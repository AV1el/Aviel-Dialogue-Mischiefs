package net.aviel.dialogue.client;

import net.minecraft.client.resources.language.I18n;

/**
 * Resolves inline {{translation.key}} placeholders against the client language.
 * Keys come from lang files in config/adm-dialogues/lang (delivered via the generated resource pack)
 * or from any other loaded resource pack / mod.
 */
public final class DialogueTextLocalizer {
    private DialogueTextLocalizer() {
    }

    public static String localize(String text) {
        if (text == null || text.isEmpty() || !text.contains("{{")) {
            return text == null ? "" : text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf("{{", index);
            if (start < 0) {
                result.append(text, index, text.length());
                break;
            }
            int end = text.indexOf("}}", start + 2);
            if (end < 0) {
                result.append(text, index, text.length());
                break;
            }
            result.append(text, index, start);
            String key = text.substring(start + 2, end).trim();
            if (!key.isBlank()) {
                result.append(I18n.get(key));
            }
            index = end + 2;
        }
        return result.toString();
    }
}
