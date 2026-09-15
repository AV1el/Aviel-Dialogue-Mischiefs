package net.aviel.dialogue;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MAX_INTERACT_DISTANCE = BUILDER
            .comment("Maximum distance (in blocks) at which dialogue choices, trades and emotes are accepted by the server.")
            .defineInRange("maxInteractDistance", 8.0D, 2.0D, 64.0D);

    public static final ModConfigSpec.IntValue COMMAND_PERMISSION_LEVEL = BUILDER
            .comment("Permission level required for the /npc command and the in-game NPC editor.")
            .defineInRange("commandPermissionLevel", 2, 0, 4);

    public static final ModConfigSpec.ConfigValue<String> RESOURCE_PACK_URL = BUILDER
            .comment("Public HTTP(S) URL of the exported adm-server-assets.zip. Empty disables delivery. Restart after changes.")
            .define("resourcePackUrl", "");
    public static final ModConfigSpec.BooleanValue RESOURCE_PACK_REQUIRED = BUILDER
            .comment("Disconnect players who decline or cannot load the ADM server resource pack.")
            .define("resourcePackRequired", false);

    public static final ModConfigSpec.BooleanValue ASSET_HTTP_ENABLED = BUILDER
            .comment("Serve generated NPC assets over HTTP. Requires an accessible TCP port. Restart after changes.")
            .define("assetHttpEnabled", false);
    public static final ModConfigSpec.IntValue ASSET_HTTP_PORT = BUILDER
            .defineInRange("assetHttpPort", 25566, 1024, 65535);
    public static final ModConfigSpec.ConfigValue<String> ASSET_HTTP_BIND = BUILDER
            .comment("Local interface to listen on; 0.0.0.0 listens on all IPv4 interfaces.")
            .define("assetHttpBind", "0.0.0.0");
    public static final ModConfigSpec.ConfigValue<String> ASSET_HTTP_PUBLIC_URL = BUILDER
            .comment("Public base URL reachable by players, e.g. http://play.example.com:25566 (no trailing path).")
            .define("assetHttpPublicUrl", "");

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static double maxInteractDistanceSqr() {
        double distance = MAX_INTERACT_DISTANCE.get();
        return distance * distance;
    }
}
