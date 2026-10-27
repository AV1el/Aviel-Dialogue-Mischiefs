package net.aviel.dialogue.entity.ai;

import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

public final class NpcCompanion {
    public enum Mode { IDLE, FOLLOW, GUARD }

    private final DialogueNpcEntity npc;
    private Mode mode = Mode.IDLE;
    private UUID owner;
    private BlockPos anchor;
    private boolean combat;
    private NpcCombatProfile profile = NpcCombatProfile.DEFAULT;
    private int lastTargetSwitch = -200;
    private int lastTargetSeen;

    public UUID owner() {
        return owner;
    }

    public boolean canReach(LivingEntity target) {
        return profile.melee().canReach(target.getBoundingBox(), npc.getEyePosition());
    }

    public static boolean canControl(UUID owner, UUID actor) {
        return actor != null && (owner == null || owner.equals(actor));
    }

    public void claim(UUID player) {
        if (owner == null) owner = player;
    }

    public void release() {
        configure(Mode.IDLE, null);
        setCombat(false);
        owner = null;
    }

    public void applyProfile(NpcCombatProfile next) {
        float fraction = npc.getHealth() / npc.getMaxHealth();
        npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(next.health());
        npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(next.damage());
        npc.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(next.attackSpeed());
        npc.getAttribute(Attributes.ARMOR).setBaseValue(next.armor());
        npc.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(next.melee().knockback());
        npc.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(next.melee().resistance());
        npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(next.speed());
        npc.setHealth(fraction * npc.getMaxHealth());
        profile = next;
        npc.setTarget(null);
        npc.getNavigation().stop();
    }

    public NpcCombatProfile profile() {
        return new NpcCombatProfile(npc.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),
                npc.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue(), npc.getAttribute(Attributes.ARMOR).getBaseValue(),
                npc.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(), profile.radius(),
                profile.movementMultiplier(), profile.policy(), profile.targets(), npc.getAttribute(Attributes.ATTACK_SPEED).getBaseValue(), profile.targeting(),
                new NpcMeleeSettings(profile.melee().range(), npc.getAttribute(Attributes.ATTACK_KNOCKBACK).getBaseValue(),
                        npc.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue()));
    }

    public NpcCompanion(DialogueNpcEntity npc) {
        this.npc = npc;
    }

    public Mode mode() {
        return mode;
    }

    public boolean combatEnabled() {
        return combat;
    }

    public void setCombat(boolean enabled) {
        combat = enabled;
        npc.setTarget(null);
        npc.getNavigation().stop();
    }

    public void configure(Mode next, UUID player) {
        mode = next;
        if (player != null) owner = player;
        anchor = npc.blockPosition();
        npc.stopWalking();
        npc.setTarget(null);
        npc.stopNpcEmote();
    }

    public void tick() {
        if (npc.level().isClientSide || (mode == Mode.IDLE && !npc.social().combat()) || npc.isWalking()) {
            return;
        }
        if (anchor == null) anchor = npc.blockPosition();
        Player player = owner == null ? null : npc.level().getPlayerByUUID(owner);
        if (mode == Mode.FOLLOW && (player == null || !player.isAlive() || player.isSpectator())) {
            npc.setTarget(null);
            npc.getNavigation().stop();
            return;
        }
        Vec3 center = mode == Mode.FOLLOW ? player.position() : Vec3.atBottomCenterOf(anchor);
        LivingEntity target = npc.getTarget();
        if (target != null && (!combat || !canKeepTarget(target, center))) {
            npc.setTarget(null);
            npc.getNavigation().stop();
        }
        if ((npc.tickCount + npc.getId()) % 20 != 0) {
            return;
        }
        if (combat && profile.policy() != NpcCombatProfile.Policy.PASSIVE) updateTarget(player, center);
        if (npc.getTarget() == null) {
            double distance = npc.position().distanceToSqr(center);
            double stopDistance = mode == Mode.FOLLOW ? 9 : 2.25;
            if (distance > stopDistance && distance < 4096) {
                npc.getNavigation().moveTo(center.x, center.y, center.z, profile.movementMultiplier());
            } else {
                npc.getNavigation().stop();
            }
        }
    }

    private boolean validTarget(LivingEntity target, Vec3 center) {
        return eligibleTarget(target, center) && npc.getSensing().hasLineOfSight(target);
    }

    private boolean canKeepTarget(LivingEntity target, Vec3 center) {
        if (!eligibleTarget(target, center)) return false;
        if (npc.getSensing().hasLineOfSight(target)) lastTargetSeen = npc.tickCount;
        return npc.tickCount - lastTargetSeen <= profile.targeting().lostSightTicks();
    }

    private LivingEntity recentAttacker(LivingEntity victim, Vec3 center) {
        if (victim == null || victim.tickCount - victim.getLastHurtByMobTimestamp() > 100) return null;
        LivingEntity attacker = victim.getLastHurtByMob();
        return attacker != null && validTarget(attacker, center) ? attacker : null;
    }

    private void updateTarget(Player player, Vec3 center) {
        LivingEntity current = npc.getTarget();
        LivingEntity ownerThreat = profile.targeting().protectOwner() && profile.policy() != NpcCombatProfile.Policy.RETALIATE
                ? recentAttacker(player, center) : null;
        LivingEntity selfThreat = profile.targeting().switchOnAttack() || current == null ? recentAttacker(npc, center) : null;
        LivingEntity candidate = ownerThreat != null ? ownerThreat : selfThreat;
        int candidatePriority = ownerThreat != null ? 3 : selfThreat != null ? 2 : 0;
        int currentPriority = current != null && current == ownerThreat ? 3 : current != null && current == selfThreat ? 2 : 0;
        if (current == null && candidate == null && profile.policy() == NpcCombatProfile.Policy.HOSTILE) {
            candidate = npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(profile.radius()),
                    entity -> (entity instanceof Enemy || net.aviel.dialogue.npc.social.NpcSocialService.hostile(npc, entity)) && validTarget(entity, center)).stream()
                    .min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
        }
        if (candidate != null && candidate != current && (current == null || NpcTargetingSettings.shouldSwitch(currentPriority,
                candidatePriority, npc.tickCount - lastTargetSwitch >= profile.targeting().switchCooldown()))) {
            npc.setTarget(candidate);
            npc.getNavigation().stop();
            lastTargetSwitch = npc.tickCount;
            lastTargetSeen = npc.tickCount;
        }
    }

    private boolean eligibleTarget(LivingEntity target, Vec3 center) {
        double radius = profile.radius();
        boolean socialTarget = net.aviel.dialogue.npc.social.NpcSocialService.hostile(npc, target);
        return target != npc && profile.policy() != NpcCombatProfile.Policy.PASSIVE
                && target.isAlive() && (!(target instanceof Player || target instanceof DialogueNpcEntity) || socialTarget)
                && !target.isInvulnerable() && !npc.isAlliedTo(target)
                && (profile.targets().isEmpty() || profile.targets().contains(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString()))
                && target.distanceToSqr(center) <= radius * radius && npc.distanceToSqr(target) <= (radius + 4) * (radius + 4);
    }

    public void save(CompoundTag tag) {
        tag.putString("CompanionMode", mode.name());
        tag.putBoolean("CompanionCombat", combat);
        tag.putString("CompanionProfile", profile.toJson().toString());
        if (owner != null) tag.putUUID("CompanionOwner", owner);
        if (anchor != null) tag.putLong("CompanionAnchor", anchor.asLong());
    }

    public void load(CompoundTag tag) {
        String saved = tag.getString("CompanionMode");
        mode = switch (saved) {
            case "FOLLOW" -> Mode.FOLLOW;
            case "GUARD" -> Mode.GUARD;
            default -> Mode.IDLE;
        };
        combat = tag.getBoolean("CompanionCombat");
        owner = tag.hasUUID("CompanionOwner") ? tag.getUUID("CompanionOwner") : null;
        anchor = tag.contains("CompanionAnchor") ? BlockPos.of(tag.getLong("CompanionAnchor")) : npc.blockPosition();
        if (tag.contains("CompanionProfile")) {
            try {
                profile = NpcCombatProfile.fromJson(JsonParser.parseString(tag.getString("CompanionProfile")).getAsJsonObject());
            } catch (RuntimeException ex) {
                profile = NpcCombatProfile.DEFAULT;
                net.aviel.dialogue.AvielsDialogueMod.LOGGER.warn("Invalid saved NPC combat profile; using defaults", ex);
            }
        }
    }

    public static final class AttackGoal extends MeleeAttackGoal {
        private final DialogueNpcEntity npc;
        private long nextAttackAt;

        public AttackGoal(DialogueNpcEntity npc) {
            // Keep attacking after the path ends; the companion controller enforces visibility and leash distance.
            super(npc, 0.6, true);
            this.npc = npc;
        }

        @Override
        public void tick() {
            super.tick();
            LivingEntity target = npc.getTarget();
            if (target != null && !npc.getNavigation().isDone()) {
                npc.getNavigation().setSpeedModifier(npc.companion().profile.movementMultiplier());
            }
        }

        private boolean enabled() {
            return npc.companion().combatEnabled() && (npc.companion().mode() != Mode.IDLE || npc.social().combat()) && !npc.isWalking();
        }

        @Override
        public boolean canUse() {
            return enabled() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return enabled() && super.canContinueToUse();
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (enabled() && canPerformAttack(target)) {
                if (npc.isAlliedTo(target) || (target instanceof Player || target instanceof DialogueNpcEntity)
                        && !net.aviel.dialogue.npc.social.NpcSocialService.hostile(npc, target)) return;
                resetAttackCooldown();
                npc.stopNpcEmote();
                npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                boolean hit = npc.doHurtTarget(target);
                npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                        hit ? net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG : net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_NODAMAGE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        @Override
        protected void resetAttackCooldown() {
            nextAttackAt = npc.level().getGameTime() + NpcAttackTiming.cooldownTicks(npc.getAttributeValue(Attributes.ATTACK_SPEED));
        }

        @Override
        protected boolean isTimeToAttack() {
            return npc.level().getGameTime() >= nextAttackAt;
        }
    }
}
