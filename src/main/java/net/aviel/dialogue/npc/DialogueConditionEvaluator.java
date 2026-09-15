package net.aviel.dialogue.npc;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.api.DialogueConditionHandler;
import net.aviel.dialogue.npc.dialogue.DialogueCondition;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.aviel.dialogue.npc.dialogue.NpcDialoguePlayerData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueConditionEvaluator {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "flag", "tag", "choice", "item", "has_item", "advancement", "kills", "kill_count",
            "dimension", "biome", "game_mode", "gamemode", "level", "xp_level", "experience",
            "experience_points", "xp", "health", "food", "hunger", "permission", "permission_level",
            "score", "scoreboard", "time", "day_time", "weather", "distance", "distance_to_npc",
            "sneaking", "crouching", "on_ground", "team", "name", "player_name"
    );
    private static final Map<String, DialogueConditionHandler> CUSTOM_HANDLERS = new ConcurrentHashMap<>();

    private DialogueConditionEvaluator() {
    }

    public static boolean matches(DialogueCondition condition, ServerPlayer player, Entity target) {
        if (condition instanceof DialogueCondition.All all) {
            return all.conditions().stream().allMatch(child -> matches(child, player, target));
        }
        if (condition instanceof DialogueCondition.Any any) {
            return !any.conditions().isEmpty()
                    && any.conditions().stream().anyMatch(child -> matches(child, player, target));
        }
        if (condition instanceof DialogueCondition.Not not) {
            return !matches(not.condition(), player, target);
        }
        if (condition instanceof DialogueCondition.Predicate predicate) {
            if (!supports(predicate.type()) || !hasRequiredArguments(predicate)) {
                return false;
            }
            DialogueConditionHandler handler = CUSTOM_HANDLERS.get(predicate.type());
            if (handler != null) {
                try {
                    return predicate.expected() == handler.test(player, target, predicate);
                } catch (RuntimeException ex) {
                    AvielsDialogueMod.LOGGER.warn("Custom dialogue condition '{}' failed for player {}",
                            predicate.type(), player.getGameProfile().getName(), ex);
                    return false;
                }
            }
            return predicate.expected() == matchesPredicate(predicate, player, target);
        }
        return false;
    }

    static boolean supports(String type) {
        return SUPPORTED_TYPES.contains(type) || CUSTOM_HANDLERS.containsKey(type);
    }

    static boolean hasRequiredArguments(DialogueCondition.Predicate condition) {
        return switch (condition.type()) {
            case "flag", "tag", "choice", "item", "has_item", "advancement", "kills", "kill_count",
                    "dimension", "biome" -> !condition.id().isBlank();
            case "game_mode", "gamemode" -> !(condition.value().isBlank() && condition.id().isBlank());
            case "weather", "team", "name", "player_name" -> !condition.value().isBlank();
            case "score", "scoreboard" -> !condition.objective().isBlank();
            default -> true;
        };
    }

    public static void register(String type, DialogueConditionHandler handler) {
        String normalized = normalizeCustomType(type);
        if (SUPPORTED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Built-in dialogue condition type cannot be replaced: " + normalized);
        }
        CUSTOM_HANDLERS.put(normalized, java.util.Objects.requireNonNull(handler, "handler"));
    }

    public static void unregister(String type) {
        String normalized = normalizeCustomType(type);
        CUSTOM_HANDLERS.remove(normalized);
    }

    private static String normalizeCustomType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid dialogue condition type: " + type);
        }
        return normalized;
    }

    private static boolean matchesPredicate(
            DialogueCondition.Predicate condition,
            ServerPlayer player,
            Entity target
    ) {
        return switch (condition.type()) {
            case "flag" -> playerData(player).hasFlag(player.getUUID(), condition.id());
            case "tag" -> player.getTags().contains(condition.id());
            case "choice" -> playerData(player).hasChoice(player.getUUID(), condition.id());
            case "item", "has_item" -> DialogueItemHandler.hasItemRules(
                    player,
                    List.of(new NpcDialogueDefinition.ItemRule(condition.id(), condition.count()))
            );
            case "advancement" -> hasAdvancement(player, condition.id());
            case "kills", "kill_count" -> hasKills(player, condition.id(), condition.count());
            case "dimension" -> resourceEquals(player.serverLevel().dimension().location(), condition.id());
            case "biome" -> player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
                    .map(key -> resourceEquals(key.location(), condition.id()))
                    .orElse(false);
            case "game_mode", "gamemode" -> matchesGameMode(player, condition);
            case "level", "xp_level" -> inRange(player.experienceLevel, condition);
            case "experience", "experience_points", "xp" -> inRange(player.totalExperience, condition);
            case "health" -> inRange(player.getHealth(), condition);
            case "food", "hunger" -> inRange(player.getFoodData().getFoodLevel(), condition);
            case "permission", "permission_level" -> player.hasPermissions(permissionLevel(condition));
            case "score", "scoreboard" -> matchesScore(player, condition);
            case "time", "day_time" -> matchesTime(player.serverLevel().getDayTime() % 24000L, condition);
            case "weather" -> matchesWeather(player, condition.value());
            case "distance", "distance_to_npc" -> inRange(Math.sqrt(player.distanceToSqr(target)), condition);
            case "sneaking", "crouching" -> player.isShiftKeyDown();
            case "on_ground" -> player.onGround();
            case "team" -> player.getTeam() != null && player.getTeam().getName().equals(condition.value());
            case "name", "player_name" -> player.getGameProfile().getName().equalsIgnoreCase(condition.value());
            default -> false;
        };
    }

    private static NpcDialoguePlayerData playerData(ServerPlayer player) {
        return NpcDialoguePlayerData.get(player.server);
    }

    static boolean hasAdvancement(ServerPlayer player, String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return false;
        }
        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    static boolean hasKills(ServerPlayer player, String rawId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return false;
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(id);
        return player.getStats().getValue(Stats.ENTITY_KILLED.get(entityType)) >= count;
    }

    private static boolean matchesGameMode(ServerPlayer player, DialogueCondition.Predicate condition) {
        GameType gameMode = player.gameMode.getGameModeForPlayer();
        String expected = condition.value().isBlank() ? condition.id() : condition.value();
        return gameMode.getName().equalsIgnoreCase(expected);
    }

    private static boolean matchesScore(ServerPlayer player, DialogueCondition.Predicate condition) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(condition.objective());
        if (objective == null) {
            return false;
        }
        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(player, objective);
        return score != null && inRange(score.value(), condition);
    }

    private static boolean matchesTime(long time, DialogueCondition.Predicate condition) {
        double min = condition.min() == null ? 0.0D : condition.min();
        double max = condition.max() == null ? 23999.0D : condition.max();
        if (min <= max) {
            return time >= min && time <= max;
        }
        return time >= min || time <= max;
    }

    private static boolean matchesWeather(ServerPlayer player, String rawWeather) {
        String weather = rawWeather.toLowerCase(Locale.ROOT);
        return switch (weather) {
            case "clear" -> !player.serverLevel().isRaining();
            case "rain", "raining" -> player.serverLevel().isRaining() && !player.serverLevel().isThundering();
            case "thunder", "thundering", "storm" -> player.serverLevel().isThundering();
            default -> false;
        };
    }

    private static int permissionLevel(DialogueCondition.Predicate condition) {
        if (condition.min() != null) {
            return Math.max(0, Math.min(4, condition.min().intValue()));
        }
        return Math.max(0, Math.min(4, condition.count()));
    }

    private static boolean resourceEquals(ResourceLocation actual, String expected) {
        ResourceLocation id = ResourceLocation.tryParse(expected);
        return id != null && id.equals(actual);
    }

    private static boolean inRange(double value, DialogueCondition.Predicate condition) {
        return (condition.min() == null || value >= condition.min())
                && (condition.max() == null || value <= condition.max());
    }
}
