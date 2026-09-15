package net.aviel.dialogue.npc.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ServerAssetPackTest {
    @TempDir
    Path directory;

    @Test
    void exportsStableArchiveAndDetectsChanges() throws Exception {
        Path root = Files.createDirectory(directory.resolve("pack"));
        Path asset = root.resolve("assets/example/skins/npc.png");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "first");
        Files.writeString(root.resolve("pack.mcmeta"), "{}");
        Path archive = directory.resolve("pack.zip");
        ServerAssetPack.export(root, archive);
        String first = ServerAssetPack.sha1(archive);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("assets/example/skins/npc.png"));
        }
        ServerAssetPack.export(root, archive);
        assertEquals(first, ServerAssetPack.sha1(archive));
        Files.writeString(asset, "second");
        ServerAssetPack.export(root, archive);
        assertNotEquals(first, ServerAssetPack.sha1(archive));
    }

    @Test
    void rejectsNonDownloadUrls() {
        assertDoesNotThrow(() -> ServerAssetPack.validateUrl("https://example.com/adm.zip"));
        for (String url : new String[]{"file:///tmp/pack.zip", "https://user:password@example.com/pack.zip", "https://example.com/pack.zip#fragment", "relative.zip"}) {
            assertThrows(IllegalArgumentException.class, () -> ServerAssetPack.validateUrl(url));
        }
    }
}
