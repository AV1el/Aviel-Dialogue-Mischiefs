package net.aviel.dialogue.npc.emote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NpcJsonEmoteClip {
    private final String name;
    private final String description;
    private final String author;
    private final int beginTick;
    private final int endTick;
    private final int stopTick;
    private final boolean loop;
    private final int returnTick;
    private final boolean degrees;
    private final EnumMap<BodyPart, EnumMap<Channel, Track>> tracks;

    public NpcJsonEmoteClip(
            String name,
            String description,
            String author,
            int beginTick,
            int endTick,
            int stopTick,
            boolean loop,
            int returnTick,
            boolean degrees,
            Map<BodyPart, Map<Channel, List<Keyframe>>> keyframes
    ) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.author = author == null ? "" : author;
        this.beginTick = Math.max(0, beginTick);
        this.endTick = Math.max(this.beginTick + 1, endTick);
        this.stopTick = stopTick <= this.endTick ? this.endTick + 1 : stopTick;
        this.loop = loop;
        this.returnTick = clampReturnTick(returnTick, this.beginTick, this.endTick);
        this.degrees = degrees;
        this.tracks = buildTracks(keyframes);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String author() {
        return author;
    }

    public int beginTick() {
        return beginTick;
    }

    public int endTick() {
        return endTick;
    }

    public int stopTick() {
        return stopTick;
    }

    public boolean isLoop() {
        return loop;
    }

    public int returnTick() {
        return returnTick;
    }

    public boolean usesDegrees() {
        return degrees;
    }

    public boolean isPlayingAt(float elapsedTick, boolean forceLoop) {
        if (elapsedTick < 0.0F) return false;
        return this.loop || forceLoop || elapsedTick < this.stopTick;
    }

    public float playbackTick(float elapsedTick, boolean forceLoop) {
        float tick = Math.max(0.0F, elapsedTick);
        if ((!this.loop && !forceLoop) || tick <= this.endTick) return tick;
        int cycle = Math.max(1, this.endTick - this.returnTick + 1);
        return this.returnTick + ((tick - this.returnTick) % cycle);
    }

    public float sample(BodyPart part, Channel channel, float elapsedTick, boolean forceLoop) {
        Track track = track(part, channel);
        if (track == null) {
            return defaultValue(part, channel);
        }
        float tick = playbackTick(elapsedTick, forceLoop);
        return track.sample(tick, this.beginTick, this.endTick, this.loop || forceLoop, this.returnTick);
    }

    private Track track(BodyPart part, Channel channel) {
        EnumMap<Channel, Track> partTracks = this.tracks.get(part);
        if (partTracks == null) return null;
        return partTracks.get(channel);
    }

    private EnumMap<BodyPart, EnumMap<Channel, Track>> buildTracks(Map<BodyPart, Map<Channel, List<Keyframe>>> input) {
        EnumMap<BodyPart, EnumMap<Channel, Track>> result = new EnumMap<>(BodyPart.class);
        for (BodyPart part : BodyPart.values()) {
            EnumMap<Channel, Track> perPart = new EnumMap<>(Channel.class);
            Map<Channel, List<Keyframe>> sourceChannels = input == null ? null : input.get(part);
            for (Channel channel : Channel.values()) {
                List<Keyframe> list = sourceChannels == null ? null : sourceChannels.get(channel);
                float defaultValue = defaultValue(part, channel);
                perPart.put(channel, new Track(defaultValue, list == null ? List.of() : list));
            }
            result.put(part, perPart);
        }
        return result;
    }

    private static int clampReturnTick(int returnTick, int beginTick, int endTick) {
        int min = beginTick;
        int max = Math.max(min, endTick);
        if (returnTick < min) return min;
        return Math.min(returnTick, max);
    }

    private static float defaultValue(BodyPart part, Channel channel) {
        if (channel == Channel.PITCH || channel == Channel.YAW || channel == Channel.ROLL || channel == Channel.BEND) {
            return 0.0F;
        }
        return switch (part) {
            case HEAD, BODY, LEFT_ITEM, RIGHT_ITEM -> 0.0F;
            case RIGHT_ARM -> switch (channel) {
                case X -> -5.0F;
                case Y -> 2.0F;
                default -> 0.0F;
            };
            case LEFT_ARM -> switch (channel) {
                case X -> 5.0F;
                case Y -> 2.0F;
                default -> 0.0F;
            };
            case LEFT_LEG -> switch (channel) {
                case X -> 1.9F;
                case Y -> 12.0F;
                case Z -> 0.1F;
                default -> 0.0F;
            };
            case RIGHT_LEG -> switch (channel) {
                case X -> -1.9F;
                case Y -> 12.0F;
                case Z -> 0.1F;
                default -> 0.0F;
            };
        };
    }

    public enum BodyPart {
        HEAD("head"),
        BODY("body"),
        LEFT_ARM("leftArm"),
        RIGHT_ARM("rightArm"),
        LEFT_LEG("leftLeg"),
        RIGHT_LEG("rightLeg"),
        LEFT_ITEM("leftItem"),
        RIGHT_ITEM("rightItem");

        private final String id;
        private static final Map<String, BodyPart> LOOKUP = createLookup();

        BodyPart(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static BodyPart fromString(String raw) {
            if (raw == null || raw.isBlank()) return null;
            return LOOKUP.get(raw.trim().toLowerCase(Locale.ROOT));
        }

        private static Map<String, BodyPart> createLookup() {
            Map<String, BodyPart> map = new HashMap<>();
            for (BodyPart value : values()) {
                map.put(value.id.toLowerCase(Locale.ROOT), value);
            }
            // EmoteCraft internals often refer to body as "torso".
            map.put("torso", BODY);
            return map;
        }
    }

    public enum Channel {
        X("x", false),
        Y("y", false),
        Z("z", false),
        PITCH("pitch", true),
        YAW("yaw", true),
        ROLL("roll", true),
        BEND("bend", true);

        private final String id;
        private final boolean angle;
        private static final Map<String, Channel> LOOKUP = createLookup();

        Channel(String id, boolean angle) {
            this.id = id;
            this.angle = angle;
        }

        public String id() {
            return id;
        }

        public boolean isAngle() {
            return angle;
        }

        public static Channel fromString(String raw) {
            if (raw == null || raw.isBlank()) return null;
            return LOOKUP.get(raw.trim().toLowerCase(Locale.ROOT));
        }

        private static Map<String, Channel> createLookup() {
            Map<String, Channel> map = new HashMap<>();
            for (Channel value : values()) {
                map.put(value.id.toLowerCase(Locale.ROOT), value);
            }
            return map;
        }
    }

    public static final class Keyframe {
        private final int tick;
        private final float value;
        private final NpcEmoteEasing easing;

        public Keyframe(int tick, float value, NpcEmoteEasing easing) {
            this.tick = Math.max(0, tick);
            this.value = value;
            this.easing = easing == null ? NpcEmoteEasing.LINEAR : easing;
        }

        public int tick() {
            return tick;
        }

        public float value() {
            return value;
        }

        public NpcEmoteEasing easing() {
            return easing;
        }
    }

    private static final class Track {
        private final float defaultValue;
        private final List<Keyframe> frames;

        private Track(float defaultValue, List<Keyframe> inputFrames) {
            this.defaultValue = defaultValue;
            List<Keyframe> copy = new ArrayList<>(inputFrames);
            copy.sort(Comparator.comparingInt(Keyframe::tick));
            this.frames = Collections.unmodifiableList(copy);
        }

        private float sample(float tick, int beginTick, int endTick, boolean loop, int returnTick) {
            if (this.frames.isEmpty()) return this.defaultValue;
            if (tick < beginTick) return this.defaultValue;

            int pos = findBefore(tick);
            if (pos < 0) return this.defaultValue;
            Keyframe before = this.frames.get(pos);

            if (pos >= this.frames.size() - 1) {
                if (!loop) {
                    return before.value();
                }
                int cycle = Math.max(1, endTick - returnTick + 1);
                Keyframe after = firstAtOrAfter(returnTick);
                if (after == null) {
                    after = new Keyframe(returnTick, this.defaultValue, before.easing());
                }
                float beforeTick = before.tick();
                float afterTick = after.tick();
                if (afterTick <= beforeTick) {
                    afterTick += cycle;
                }
                float evalTick = tick;
                if (evalTick < beforeTick) {
                    evalTick += cycle;
                }
                return interpolate(before, after.value(), evalTick, beforeTick, afterTick, before.easing());
            }

            Keyframe after = this.frames.get(pos + 1);
            return interpolate(before, after.value(), tick, before.tick(), after.tick(), before.easing());
        }

        private int findBefore(float tick) {
            int left = 0;
            int right = this.frames.size() - 1;
            int result = -1;
            while (left <= right) {
                int mid = (left + right) >>> 1;
                int midTick = this.frames.get(mid).tick();
                if (midTick <= tick) {
                    result = mid;
                    left = mid + 1;
                } else {
                    right = mid - 1;
                }
            }
            return result;
        }

        private Keyframe firstAtOrAfter(int targetTick) {
            for (Keyframe frame : this.frames) {
                if (frame.tick() >= targetTick) {
                    return frame;
                }
            }
            return null;
        }

        private float interpolate(
                Keyframe before,
                float afterValue,
                float currentTick,
                float beforeTick,
                float afterTick,
                NpcEmoteEasing easing
        ) {
            if (afterTick <= beforeTick) return before.value();
            float alpha = (currentTick - beforeTick) / (afterTick - beforeTick);
            float eased = easing.apply(alpha);
            return lerp(before.value(), afterValue, eased);
        }

        private float lerp(float from, float to, float alpha) {
            float clamped = Math.max(0.0F, Math.min(1.0F, alpha));
            return from + (to - from) * clamped;
        }
    }
}
