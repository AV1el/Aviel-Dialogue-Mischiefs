package net.aviel.dialogue.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.npc.emote.NpcJsonEmoteClip;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional runtime bridge to player-animation-lib.
 * It is fully reflective so the mod can run without the library installed.
 */
public final class NpcPlayerAnimCompat {
    private static final Object RESOLVE_LOCK = new Object();
    private static final Set<ModelPart> INITIALIZED_PARTS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, CachedAnimation> ANIMATION_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean resolved = false;
    private static volatile boolean available = false;
    private static volatile boolean loggedUnavailable = false;

    // Bend bridge
    private static Object bendHelperInstance;
    private static Method initBendMethod;
    private static Method bendMethod;
    private static Constructor<?> pairCtor;

    // Animation bridge
    private static Method deserializeAnimationMethod;
    private static Constructor<?> keyframePlayerCtor;
    private static Method setupAnimMethod;
    private static Constructor<?> animationApplierCtor;
    private static Method updatePartMethod;
    private static Method setTickDeltaMethod;
    private static Method get3DTransformMethod;
    private static Object transformPosition;
    private static Object transformRotation;
    private static Object vec3fZero;
    private static Method vec3fGetXMethod;
    private static Method vec3fGetYMethod;
    private static Method vec3fGetZMethod;

    private NpcPlayerAnimCompat() {
    }

    public static boolean applyToModel(
            String emoteFileName,
            byte[] jsonBytes,
            NpcJsonEmoteClip clip,
            float elapsedTick,
            boolean forceLoop,
            ModelPart head,
            ModelPart body,
            ModelPart leftArm,
            ModelPart rightArm,
            ModelPart leftLeg,
            ModelPart rightLeg
    ) {
        if (jsonBytes == null || jsonBytes.length == 0) return false;
        if (clip == null) return false;

        resolve();
        if (!available) return false;
        try {
            Object applier = createAnimationApplier(emoteFileName, jsonBytes, clip, elapsedTick, forceLoop);
            if (applier == null) return false;

            // Mirrors player-animation-lib PlayerModelMixin ordering.
            updatePartMethod.invoke(applier, "head", head);
            updatePartMethod.invoke(applier, "leftArm", leftArm);
            updatePartMethod.invoke(applier, "rightArm", rightArm);
            updatePartMethod.invoke(applier, "leftLeg", leftLeg);
            updatePartMethod.invoke(applier, "rightLeg", rightLeg);
            updatePartMethod.invoke(applier, "torso", body);
            return true;
        } catch (Exception ex) {
            logBridgeFailure("[ADM] player-animation runtime bridge failed, fallback mode.", ex);
            return false;
        }
    }

    public static boolean applyBodyTransforms(
            String emoteFileName,
            byte[] jsonBytes,
            NpcJsonEmoteClip clip,
            float elapsedTick,
            boolean forceLoop,
            PoseStack poseStack,
            float tickDelta
    ) {
        if (poseStack == null) return false;
        if (jsonBytes == null || jsonBytes.length == 0) return false;
        if (clip == null) return false;

        resolve();
        if (!available) return false;
        try {
            Object applier = createAnimationApplier(emoteFileName, jsonBytes, clip, elapsedTick, forceLoop);
            if (applier == null) return false;

            setTickDeltaMethod.invoke(applier, clampTickDelta(tickDelta));

            Object bodyPos = get3DTransformMethod.invoke(applier, "body", transformPosition, vec3fZero);
            float posX = component(bodyPos, vec3fGetXMethod);
            float posY = component(bodyPos, vec3fGetYMethod);
            float posZ = component(bodyPos, vec3fGetZMethod);
            poseStack.translate(posX, posY + 0.7D, posZ);

            Object bodyRot = get3DTransformMethod.invoke(applier, "body", transformRotation, vec3fZero);
            float rotX = component(bodyRot, vec3fGetXMethod);
            float rotY = component(bodyRot, vec3fGetYMethod);
            float rotZ = component(bodyRot, vec3fGetZMethod);
            poseStack.mulPose(Axis.ZP.rotation(rotZ));
            poseStack.mulPose(Axis.YP.rotation(rotY));
            poseStack.mulPose(Axis.XP.rotation(rotX));
            poseStack.translate(0.0D, -0.7D, 0.0D);
            return true;
        } catch (Exception ex) {
            logBridgeFailure("[ADM] player-animation body transform bridge failed, fallback mode.", ex);
            return false;
        }
    }

    public static boolean applyBend(ModelPart part, Direction direction, float bendValue) {
        if (part == null || direction == null) return false;
        resolve();
        if (!available) return false;
        try {
            initPartIfNeeded(part, direction);
            Object pair = pairCtor.newInstance(Float.valueOf(0.0F), Float.valueOf(bendValue));
            bendMethod.invoke(bendHelperInstance, part, pair);
            return true;
        } catch (Exception ex) {
            logBridgeFailure("[ADM] player-animation bend bridge failed, fallback mode.", ex);
            return false;
        }
    }

    private static void logBridgeFailure(String message, Exception ex) {
        if (loggedUnavailable) return;
        loggedUnavailable = true;
        AvielsDialogueMod.LOGGER.warn(message, ex);
    }

    private static Object createAnimationApplier(
            String emoteFileName,
            byte[] jsonBytes,
            NpcJsonEmoteClip clip,
            float elapsedTick,
            boolean forceLoop
    ) throws Exception {
        Object animation = getOrParseAnimation(emoteFileName, jsonBytes);
        if (animation == null) return null;

        float playbackTick = clip.playbackTick(elapsedTick, forceLoop);
        int tick = Math.max(0, (int) Math.floor(playbackTick));
        float partial = clampAnimPartial(playbackTick - tick);

        Object player = keyframePlayerCtor.newInstance(animation, tick);
        setupAnimMethod.invoke(player, partial);
        return animationApplierCtor.newInstance(player);
    }

    private static float clampAnimPartial(float partial) {
        if (partial < 0.0F) return 0.0F;
        if (partial > 0.999F) return 0.999F;
        return partial;
    }

    private static float clampTickDelta(float tickDelta) {
        if (tickDelta < 0.0F) return 0.0F;
        if (tickDelta > 1.0F) return 1.0F;
        return tickDelta;
    }

    private static float component(Object vec3f, Method getter) throws Exception {
        if (vec3f == null || getter == null) return 0.0F;
        Object number = getter.invoke(vec3f);
        if (number instanceof Number n) return n.floatValue();
        return 0.0F;
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) return null;
        for (Object value : constants) {
            if (value instanceof Enum<?> e && e.name().equals(name)) {
                return value;
            }
        }
        return null;
    }

    private static Object getOrParseAnimation(String emoteFileName, byte[] jsonBytes) throws Exception {
        String key = normalizeKey(emoteFileName);
        int hash = java.util.Arrays.hashCode(jsonBytes);
        CachedAnimation cached = ANIMATION_CACHE.get(key);
        if (cached != null && cached.hash == hash) {
            return cached.animation;
        }

        String text = new String(jsonBytes, StandardCharsets.UTF_8);
        Object listObj = deserializeAnimationMethod.invoke(null, new StringReader(text));
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object animation = list.get(0);
        ANIMATION_CACHE.put(key, new CachedAnimation(hash, animation));
        return animation;
    }

    private static String normalizeKey(String emoteFileName) {
        if (emoteFileName == null) return "";
        return emoteFileName.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void initPartIfNeeded(ModelPart part, Direction direction) throws Exception {
        synchronized (INITIALIZED_PARTS) {
            if (INITIALIZED_PARTS.contains(part)) return;
            initBendMethod.invoke(bendHelperInstance, part, direction);
            INITIALIZED_PARTS.add(part);
        }
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (RESOLVE_LOCK) {
            if (resolved) return;
            resolved = true;
            try {
                ClassLoader loader = NpcPlayerAnimCompat.class.getClassLoader();

                Class<?> helperClass = Class.forName("dev.kosmx.playerAnim.impl.animation.IBendHelper", false, loader);
                Class<?> pairClass = Class.forName("dev.kosmx.playerAnim.core.util.Pair", false, loader);
                bendHelperInstance = helperClass.getField("INSTANCE").get(null);
                initBendMethod = helperClass.getMethod("initBend", ModelPart.class, Direction.class);
                bendMethod = helperClass.getMethod("bend", ModelPart.class, pairClass);
                pairCtor = pairClass.getConstructor(Object.class, Object.class);

                Class<?> animationSerializingClass =
                        Class.forName("dev.kosmx.playerAnim.core.data.gson.AnimationSerializing", false, loader);
                deserializeAnimationMethod =
                        animationSerializingClass.getMethod("deserializeAnimation", java.io.Reader.class);

                Class<?> iAnimationClass = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation", false, loader);
                Class<?> keyframeAnimationClass =
                        Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation", false, loader);
                Class<?> keyframePlayerClass =
                        Class.forName("dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer", false, loader);
                Class<?> animationApplierClass =
                        Class.forName("dev.kosmx.playerAnim.impl.animation.AnimationApplier", false, loader);
                Class<?> transformTypeClass = Class.forName("dev.kosmx.playerAnim.api.TransformType", false, loader);
                Class<?> vec3fClass = Class.forName("dev.kosmx.playerAnim.core.util.Vec3f", false, loader);

                keyframePlayerCtor = keyframePlayerClass.getConstructor(keyframeAnimationClass, int.class);
                setupAnimMethod = keyframePlayerClass.getMethod("setupAnim", float.class);
                animationApplierCtor = animationApplierClass.getConstructor(iAnimationClass);
                updatePartMethod = animationApplierClass.getMethod("updatePart", String.class, ModelPart.class);
                setTickDeltaMethod = animationApplierClass.getMethod("setTickDelta", float.class);
                get3DTransformMethod = animationApplierClass.getMethod(
                        "get3DTransform",
                        String.class,
                        transformTypeClass,
                        vec3fClass
                );
                transformPosition = enumConstant(transformTypeClass, "POSITION");
                transformRotation = enumConstant(transformTypeClass, "ROTATION");
                if (transformPosition == null || transformRotation == null) {
                    throw new IllegalStateException("Cannot resolve TransformType enum constants.");
                }
                vec3fZero = vec3fClass.getField("ZERO").get(null);
                vec3fGetXMethod = vec3fClass.getMethod("getX");
                vec3fGetYMethod = vec3fClass.getMethod("getY");
                vec3fGetZMethod = vec3fClass.getMethod("getZ");

                available = true;
                AvielsDialogueMod.LOGGER.info("[ADM] player-animation runtime bridge enabled.");
            } catch (Exception ex) {
                available = false;
                AvielsDialogueMod.LOGGER.info("[ADM] player-animation runtime bridge unavailable, fallback mode.");
            }
        }
    }

    private record CachedAnimation(int hash, Object animation) {
    }
}
