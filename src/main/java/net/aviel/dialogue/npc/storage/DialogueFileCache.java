package net.aviel.dialogue.npc.storage;

import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches parsed JSON definitions, invalidated by file modification time and size,
 * so dialogue and trade files are not re-read from disk on every interaction.
 */
public final class DialogueFileCache<T> {
    @FunctionalInterface
    public interface Parser<T> {
        T parse(String json) throws JsonSyntaxException;
    }

    private record Entry<T>(long modifiedMillis, long size, T value) {
    }

    private final Map<Path, Entry<T>> entries = new ConcurrentHashMap<>();
    private final int maxChars;

    public DialogueFileCache(int maxChars) {
        this.maxChars = maxChars;
    }

    public T get(Path path, Parser<T> parser) throws IOException, JsonSyntaxException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        long modified = attributes.lastModifiedTime().toMillis();
        long size = attributes.size();
        Entry<T> cached = entries.get(path);
        if (cached != null && cached.modifiedMillis() == modified && cached.size() == size) {
            return cached.value();
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        if (json.length() > maxChars) {
            throw new IOException("File is too large: " + path.getFileName());
        }
        T value = parser.parse(json);
        entries.put(path, new Entry<>(modified, size, value));
        return value;
    }

    public void clear() {
        entries.clear();
    }
}
