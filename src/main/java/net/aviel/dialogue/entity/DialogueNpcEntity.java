package net.aviel.dialogue.entity;

import net.aviel.dialogue.npc.NpcDialogueService;
import net.aviel.dialogue.npc.NpcEditorService;
import net.aviel.dialogue.npc.NpcTemplateService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class DialogueNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_DIALOGUE_FILE = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_TEMPLATE_ID = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_MODEL_SCALE = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DIALOGUE_INVULNERABLE = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_LOOK_DISTANCE = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PLAYER_MODEL = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_EMOTE_FILE = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DATA_EMOTE_START_TICK = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_EMOTE_REPEAT = SynchedEntityData.defineId(DialogueNpcEntity.class, EntityDataSerializers.BOOLEAN);
    public static final float DEFAULT_MODEL_SCALE = 1.0F;
    public static final float DEFAULT_LOOK_DISTANCE = 8.0F;
    public static final String DEFAULT_PLAYER_MODEL = "steve";
    private static final int RETURN_LOOK_DELAY_TICKS = 45;

    private float homeYaw = 0.0F;
    private int lastLookAtPlayerTick = -RETURN_LOOK_DELAY_TICKS;

    public DialogueNpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setNoAi(false);
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DIALOGUE_FILE, "");
        builder.define(DATA_TEMPLATE_ID, "");
        builder.define(DATA_MODEL_SCALE, DEFAULT_MODEL_SCALE);
        builder.define(DATA_DIALOGUE_INVULNERABLE, true);
        builder.define(DATA_LOOK_DISTANCE, DEFAULT_LOOK_DISTANCE);
        builder.define(DATA_SKIN, "");
        builder.define(DATA_PLAYER_MODEL, DEFAULT_PLAYER_MODEL);
        builder.define(DATA_EMOTE_FILE, "");
        builder.define(DATA_EMOTE_START_TICK, 0L);
        builder.define(DATA_EMOTE_REPEAT, false);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isShiftKeyDown() && NpcEditorService.canEdit(serverPlayer)) {
                NpcEditorService.openEditor(serverPlayer, this);
            } else {
                NpcDialogueService.openDialogue(serverPlayer, this);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        updateLookTarget();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // NPC gear is decoration set by builders, not loot: it must never drop or be picked up.
    @Override
    protected void dropEquipment() {
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return isDialogueInvulnerable() || super.isInvulnerable();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return isDialogueInvulnerable() || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isDialogueInvulnerable() && super.hurt(source, amount);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return Player.STANDING_DIMENSIONS.scale(getModelScale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MODEL_SCALE.equals(key)) {
            refreshDimensions();
        }
    }

    public String getDialogueFile() {
        return this.entityData.get(DATA_DIALOGUE_FILE);
    }

    public void setDialogueFile(String dialogueFile) {
        this.entityData.set(DATA_DIALOGUE_FILE, NpcDialogueService.normalizeReference(dialogueFile));
    }

    public String getTemplateId() {
        return this.entityData.get(DATA_TEMPLATE_ID);
    }

    public void setTemplateId(String templateId) {
        this.entityData.set(DATA_TEMPLATE_ID, NpcTemplateService.normalizeTemplateId(templateId));
    }

    public float getModelScale() {
        return this.entityData.get(DATA_MODEL_SCALE);
    }

    public void setModelScale(float scale) {
        float clamped = Math.max(0.25F, Math.min(3.0F, scale));
        this.entityData.set(DATA_MODEL_SCALE, clamped);
        refreshDimensions();
    }

    public void setHomeYaw(float homeYaw) {
        this.homeYaw = Mth.wrapDegrees(homeYaw);
    }

    public float getHomeYaw() {
        return this.homeYaw;
    }

    public boolean isDialogueInvulnerable() {
        return this.entityData.get(DATA_DIALOGUE_INVULNERABLE);
    }

    public void setDialogueInvulnerable(boolean invulnerable) {
        this.entityData.set(DATA_DIALOGUE_INVULNERABLE, invulnerable);
        this.setInvulnerable(invulnerable);
    }

    public float getLookDistance() {
        return this.entityData.get(DATA_LOOK_DISTANCE);
    }

    public void setLookDistance(float distance) {
        this.entityData.set(DATA_LOOK_DISTANCE, Math.max(0.0F, Math.min(64.0F, distance)));
    }

    public String getSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkin(String skin) {
        this.entityData.set(DATA_SKIN, NpcDialogueService.normalizeSkin(skin));
    }

    public String getPlayerModel() {
        return this.entityData.get(DATA_PLAYER_MODEL);
    }

    public void setPlayerModel(String playerModel) {
        this.entityData.set(DATA_PLAYER_MODEL, NpcDialogueService.normalizePlayerModel(playerModel));
    }

    public String getNpcEmoteFileName() {
        String value = this.entityData.get(DATA_EMOTE_FILE);
        return value == null || value.isBlank() ? "" : value;
    }

    public long getNpcEmoteStartTick() {
        return this.entityData.get(DATA_EMOTE_START_TICK);
    }

    public boolean isNpcEmoteRepeatEnabled() {
        return this.entityData.get(DATA_EMOTE_REPEAT);
    }

    public boolean isNpcEmotePlaying() {
        return !getNpcEmoteFileName().isBlank();
    }

    public void playNpcEmote(String fileName, long startTick, boolean repeat) {
        String normalized = sanitizeEmoteFileName(fileName);
        if (normalized.isBlank()) {
            stopNpcEmote();
            return;
        }
        this.entityData.set(DATA_EMOTE_FILE, normalized);
        this.entityData.set(DATA_EMOTE_START_TICK, Math.max(0L, startTick));
        this.entityData.set(DATA_EMOTE_REPEAT, repeat);
    }

    public void stopNpcEmote() {
        this.entityData.set(DATA_EMOTE_FILE, "");
        this.entityData.set(DATA_EMOTE_START_TICK, 0L);
        this.entityData.set(DATA_EMOTE_REPEAT, false);
    }

    private static String sanitizeEmoteFileName(String raw) {
        if (raw == null) return "";
        String value = raw.trim().replace('\\', '/');
        if (value.isBlank() || value.contains("/") || value.contains("..") || value.length() > 260) {
            return "";
        }
        return value;
    }

    public String getProfileName() {
        Component name = this.getCustomName();
        return name == null ? "NPC" : name.getString();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("DialogueFile", getDialogueFile());
        tag.putString("NpcTemplate", getTemplateId());
        tag.putFloat("ModelScale", getModelScale());
        tag.putBoolean("DialogueInvulnerable", isDialogueInvulnerable());
        tag.putFloat("LookDistance", getLookDistance());
        tag.putString("Skin", getSkin());
        tag.putString("PlayerModel", getPlayerModel());
        tag.putFloat("HomeYaw", getHomeYaw());
        tag.putString("EmoteFile", getNpcEmoteFileName());
        tag.putLong("EmoteStartTick", getNpcEmoteStartTick());
        tag.putBoolean("EmoteRepeat", isNpcEmoteRepeatEnabled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDialogueFile(tag.getString("DialogueFile"));
        setTemplateId(tag.getString("NpcTemplate"));
        setModelScale(tag.contains("ModelScale") ? tag.getFloat("ModelScale") : DEFAULT_MODEL_SCALE);
        setDialogueInvulnerable(!tag.contains("DialogueInvulnerable") || tag.getBoolean("DialogueInvulnerable"));
        setLookDistance(tag.contains("LookDistance") ? tag.getFloat("LookDistance") : DEFAULT_LOOK_DISTANCE);
        setSkin(tag.getString("Skin"));
        setPlayerModel(tag.getString("PlayerModel"));
        setHomeYaw(tag.contains("HomeYaw") ? tag.getFloat("HomeYaw") : this.getYRot());
        String emoteFile = tag.getString("EmoteFile");
        if (emoteFile.isBlank()) {
            stopNpcEmote();
        } else {
            playNpcEmote(emoteFile, tag.getLong("EmoteStartTick"), tag.getBoolean("EmoteRepeat"));
        }
        this.setCustomNameVisible(true);
        this.setNoAi(false);
        this.setPersistenceRequired();
        refreshDimensions();
    }

    private void updateLookTarget() {
        if (this.level().isClientSide) {
            return;
        }
        float distance = getLookDistance();
        if (distance <= 0.0F) {
            returnLookHome();
            return;
        }
        Player player = this.level().getNearestPlayer(this, distance);
        if (player == null) {
            if (this.tickCount - this.lastLookAtPlayerTick >= RETURN_LOOK_DELAY_TICKS) {
                returnLookHome();
            }
            return;
        }
        this.lastLookAtPlayerTick = this.tickCount;
        this.getLookControl().setLookAt(player, 35.0F, 35.0F);
    }

    private void returnLookHome() {
        float yaw = Mth.rotateIfNecessary(this.getYRot(), this.homeYaw, 3.0F);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        double radians = Math.toRadians(this.homeYaw);
        double lookX = this.getX() - Math.sin(radians) * 4.0D;
        double lookZ = this.getZ() + Math.cos(radians) * 4.0D;
        this.getLookControl().setLookAt(lookX, this.getEyeY(), lookZ, 20.0F, 20.0F);
    }
}
