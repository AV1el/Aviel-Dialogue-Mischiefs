package net.aviel.dialogue.npc.storage;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ServerAssetPack {
    private static ClientboundResourcePackPushPacket offer;
    private static AssetHttpServer http;
    private static String publicBase;

    private ServerAssetPack() {
    }

    public static void onServerStarting(ServerStartingEvent event) {
        offer = null;
        String url = Config.RESOURCE_PACK_URL.get().trim();
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        try {
            if (Config.ASSET_HTTP_ENABLED.get()) {
                publicBase = Config.ASSET_HTTP_PUBLIC_URL.get().trim().replaceAll("/+$", "");
                validateUrl(publicBase);
                URI base = URI.create(publicBase);
                if (!base.getPath().isEmpty() || base.getQuery() != null) {
                    throw new IllegalArgumentException("assetHttpPublicUrl must have no path or query");
                }
                http = new AssetHttpServer(Config.ASSET_HTTP_BIND.get(), Config.ASSET_HTTP_PORT.get());
                rebuildHosted();
                return;
            }
            if (url.isEmpty()) {
                return;
            }
            validateUrl(url);
            Path root = ConfigAssetPackBuilder.prepareChecked();
            Path archive = root.resolveSibling("adm-server-assets.zip");
            export(root, archive);
            String hash = sha1(archive);
            Files.writeString(archive.resolveSibling("adm-server-assets.zip.sha1"), hash, StandardCharsets.US_ASCII);
            UUID id = UUID.nameUUIDFromBytes((url + "#" + hash).getBytes(StandardCharsets.UTF_8));
            offer = new ClientboundResourcePackPushPacket(id, url, hash,
                    Config.RESOURCE_PACK_REQUIRED.get(),
                    Optional.of(Component.literal("This server uses ADM NPC skins and sounds.")));
            AvielsDialogueMod.LOGGER.info("ADM resource pack exported to {} (SHA-1 {}). Publish this exact ZIP at {}", archive, hash, url);
        } catch (IOException | IllegalArgumentException ex) {
            stop();
            throw new IllegalStateException("Cannot prepare configured ADM server resource pack", ex);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }

    private static void stop() {
        offer = null;
        if (http != null) {
            http.close();
            http = null;
        }
        publicBase = null;
    }

    public static int reload(net.minecraft.commands.CommandSourceStack source) {
        if (http == null) {
            source.sendFailure(Component.literal("Enable assetHttpEnabled and configure assetHttpPublicUrl, then restart the dedicated server."));
            return 0;
        }
        try {
            rebuildHosted();
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                player.connection.send(offer);
            }
            source.sendSuccess(() -> Component.literal("ADM assets published and offered to connected players."), true);
            return 1;
        } catch (IOException ex) {
            AvielsDialogueMod.LOGGER.error("Failed to rebuild ADM assets; previous pack remains available", ex);
            source.sendFailure(Component.literal("Could not publish assets. Previous pack remains active; see server log."));
            return 0;
        }
    }

    private static void rebuildHosted() throws IOException {
        Path root = ConfigAssetPackBuilder.prepareChecked();
        Path archive = root.resolveSibling("adm-server-assets.zip");
        export(root, archive);
        if (Files.size(archive) > 64L * 1024 * 1024) {
            throw new IOException("ADM HTTP pack exceeds the 64 MiB limit");
        }
        String hash = sha1(archive);
        byte[] bytes = Files.readAllBytes(archive);
        String url = publicBase + "/" + hash + ".zip";
        UUID id = UUID.nameUUIDFromBytes((publicBase + "/adm-assets").getBytes(StandardCharsets.UTF_8));
        ClientboundResourcePackPushPacket next = new ClientboundResourcePackPushPacket(id, url, hash,
                Config.RESOURCE_PACK_REQUIRED.get(), Optional.of(Component.literal("This server uses ADM NPC skins and sounds.")));
        http.publish(hash, bytes);
        offer = next;
        AvielsDialogueMod.LOGGER.info("ADM assets published at {}", url);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (offer != null && event.getEntity() instanceof ServerPlayer player) {
            player.connection.send(offer);
        }
    }

    static void validateUrl(String url) {
        URI uri = URI.create(url);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("resourcePackUrl must be a public HTTP(S) URL without credentials or a fragment");
        }
    }

    static void export(Path root, Path archive) throws IOException {
        Path temporary = Files.createTempFile(archive.getParent(), "adm-pack-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary));
                 var paths = Files.walk(root)) {
                for (Path file : paths.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    ZipEntry entry = new ZipEntry(root.relativize(file).toString().replace('\\', '/'));
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String sha1(Path archive) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (DigestInputStream input = new DigestInputStream(Files.newInputStream(archive), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM does not provide SHA-1", ex);
        }
    }
}
