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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static double maxInteractDistanceSqr() {
        double distance = MAX_INTERACT_DISTANCE.get();
        return distance * distance;
    }
}
