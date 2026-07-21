package net.aviel.dialogue.npc.emote;

import java.util.Locale;

public enum NpcEmoteEasing {
    LINEAR,
    CONSTANT,
    INSINE,
    OUTSINE,
    INOUTSINE,
    INQUAD,
    OUTQUAD,
    INOUTQUAD,
    INCUBIC,
    OUTCUBIC,
    INOUTCUBIC,
    INQUART,
    OUTQUART,
    INOUTQUART,
    INQUINT,
    OUTQUINT,
    INOUTQUINT,
    INEXPO,
    OUTEXPO,
    INOUTEXPO,
    INCIRC,
    OUTCIRC,
    INOUTCIRC,
    INBACK,
    OUTBACK,
    INOUTBACK,
    INELASTIC,
    OUTELASTIC,
    INOUTELASTIC,
    INBOUNCE,
    OUTBOUNCE,
    INOUTBOUNCE;

    private static final float C1 = 1.70158F;
    private static final float C2 = C1 * 1.525F;
    private static final float C3 = C1 + 1.0F;
    private static final float C4 = (float) ((2.0D * Math.PI) / 3.0D);
    private static final float C5 = (float) ((2.0D * Math.PI) / 4.5D);
    private static final float N1 = 7.5625F;
    private static final float D1 = 2.75F;

    public static NpcEmoteEasing fromString(String raw) {
        if (raw == null || raw.isBlank()) return LINEAR;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("EASE")) value = value.substring(4);
        if ("STEP".equals(value)) return CONSTANT;
        try {
            return NpcEmoteEasing.valueOf(value);
        } catch (Exception ignored) {
            return LINEAR;
        }
    }

    public float apply(float input) {
        float x = clamp01(input);
        return switch (this) {
            case CONSTANT -> 0.0F;
            case INSINE -> (float) (1.0D - Math.cos((x * Math.PI) / 2.0D));
            case OUTSINE -> (float) Math.sin((x * Math.PI) / 2.0D);
            case INOUTSINE -> (float) (-(Math.cos(Math.PI * x) - 1.0D) / 2.0D);
            case INQUAD -> x * x;
            case OUTQUAD -> 1.0F - (1.0F - x) * (1.0F - x);
            case INOUTQUAD -> (float) (x < 0.5F ? 2.0F * x * x : 1.0D - Math.pow(-2.0F * x + 2.0F, 2.0D) / 2.0D);
            case INCUBIC -> x * x * x;
            case OUTCUBIC -> (float) (1.0D - Math.pow(1.0F - x, 3.0D));
            case INOUTCUBIC -> (float) (x < 0.5F ? 4.0F * x * x * x : 1.0D - Math.pow(-2.0F * x + 2.0F, 3.0D) / 2.0D);
            case INQUART -> x * x * x * x;
            case OUTQUART -> (float) (1.0D - Math.pow(1.0F - x, 4.0D));
            case INOUTQUART -> (float) (x < 0.5F ? 8.0F * x * x * x * x : 1.0D - Math.pow(-2.0F * x + 2.0F, 4.0D) / 2.0D);
            case INQUINT -> x * x * x * x * x;
            case OUTQUINT -> (float) (1.0D - Math.pow(1.0F - x, 5.0D));
            case INOUTQUINT -> (float) (x < 0.5F ? 16.0F * x * x * x * x * x : 1.0D - Math.pow(-2.0F * x + 2.0F, 5.0D) / 2.0D);
            case INEXPO -> (float) (x == 0.0F ? 0.0D : Math.pow(2.0D, 10.0F * x - 10.0F));
            case OUTEXPO -> (float) (x == 1.0F ? 1.0D : 1.0D - Math.pow(2.0D, -10.0F * x));
            case INOUTEXPO -> (float) (
                    x == 0.0F ? 0.0D :
                            x == 1.0F ? 1.0D :
                                    x < 0.5F ? Math.pow(2.0D, 20.0F * x - 10.0F) / 2.0D :
                                            (2.0D - Math.pow(2.0D, -20.0F * x + 10.0F)) / 2.0D
            );
            case INCIRC -> (float) (1.0D - Math.sqrt(1.0D - Math.pow(x, 2.0D)));
            case OUTCIRC -> (float) Math.sqrt(1.0D - Math.pow(x - 1.0F, 2.0D));
            case INOUTCIRC -> (float) (
                    x < 0.5F ?
                            (1.0D - Math.sqrt(1.0D - Math.pow(2.0F * x, 2.0D))) / 2.0D :
                            (Math.sqrt(1.0D - Math.pow(-2.0F * x + 2.0F, 2.0D)) + 1.0D) / 2.0D
            );
            case INBACK -> C3 * x * x * x - C1 * x * x;
            case OUTBACK -> (float) (1.0D + C3 * Math.pow(x - 1.0F, 3.0D) + C1 * Math.pow(x - 1.0F, 2.0D));
            case INOUTBACK -> (float) (
                    x < 0.5F ?
                            (Math.pow(2.0F * x, 2.0D) * ((C2 + 1.0F) * 2.0F * x - C2)) / 2.0D :
                            (Math.pow(2.0F * x - 2.0F, 2.0D) * ((C2 + 1.0F) * (x * 2.0F - 2.0F) + C2) + 2.0D) / 2.0D
            );
            case INELASTIC -> (float) (
                    x == 0.0F ? 0.0D :
                            x == 1.0F ? 1.0D :
                                    -Math.pow(2.0D, 10.0F * x - 10.0F) * Math.sin((x * 10.0F - 10.75F) * C4)
            );
            case OUTELASTIC -> (float) (
                    x == 0.0F ? 0.0D :
                            x == 1.0F ? 1.0D :
                                    Math.pow(2.0D, -10.0F * x) * Math.sin((x * 10.0F - 0.75F) * C4) + 1.0D
            );
            case INOUTELASTIC -> (float) (
                    x == 0.0F ? 0.0D :
                            x == 1.0F ? 1.0D :
                                    x < 0.5F ?
                                            -(Math.pow(2.0D, 20.0F * x - 10.0F) * Math.sin((20.0F * x - 11.125F) * C5)) / 2.0D :
                                            (Math.pow(2.0D, -20.0F * x + 10.0F) * Math.sin((20.0F * x - 11.125F) * C5)) / 2.0D + 1.0D
            );
            case INBOUNCE -> 1.0F - outBounce(1.0F - x);
            case OUTBOUNCE -> outBounce(x);
            case INOUTBOUNCE -> x < 0.5F ? (1.0F - outBounce(1.0F - 2.0F * x)) / 2.0F : (1.0F + outBounce(2.0F * x - 1.0F)) / 2.0F;
            default -> x;
        };
    }

    private static float outBounce(float x) {
        if (x < 1.0F / D1) {
            return N1 * x * x;
        } else if (x < 2.0F / D1) {
            x -= 1.5F / D1;
            return N1 * x * x + 0.75F;
        } else if (x < 2.5F / D1) {
            x -= 2.25F / D1;
            return N1 * x * x + 0.9375F;
        } else {
            x -= 2.625F / D1;
            return N1 * x * x + 0.984375F;
        }
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        return Math.min(value, 1.0F);
    }
}
