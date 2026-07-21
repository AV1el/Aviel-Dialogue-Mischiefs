package net.aviel.dialogue.client.model;

import net.aviel.dialogue.client.NpcEmoteClientCache;
import net.aviel.dialogue.client.NpcPlayerAnimCompat;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.emote.NpcJsonEmoteClip;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

public class DialogueNpcModel extends PlayerModel<DialogueNpcEntity> {
    public DialogueNpcModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(
            DialogueNpcEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        applyJsonEmote(entity, ageInTicks);
    }

    private void applyJsonEmote(DialogueNpcEntity entity, float ageInTicks) {
        if (entity == null || !entity.isNpcEmotePlaying()) return;
        String fileName = entity.getNpcEmoteFileName();
        if (fileName.isBlank()) return;
        NpcJsonEmoteClip clip = NpcEmoteClientCache.getEmote(fileName);
        if (clip == null) return;
        if (entity.level() == null) return;
        byte[] jsonBytes = NpcEmoteClientCache.getEmoteBytes(fileName);
        boolean forceLoop = entity.isNpcEmoteRepeatEnabled();

        float partialTick = ageInTicks - entity.tickCount;
        float elapsedTick = (entity.level().getGameTime() - entity.getNpcEmoteStartTick()) + partialTick;
        if (!clip.isPlayingAt(elapsedTick, forceLoop)) return;

        resetToDefaultPlayerPivot();

        // Prefer exact player-animation runtime transform path when available.
        if (NpcPlayerAnimCompat.applyToModel(
                fileName,
                jsonBytes,
                clip,
                elapsedTick,
                forceLoop,
                this.head,
                this.body,
                this.leftArm,
                this.rightArm,
                this.leftLeg,
                this.rightLeg
        )) {
            copyWearLayersFromBaseParts();
            return;
        }

        applyPart(this.head, clip, NpcJsonEmoteClip.BodyPart.HEAD, elapsedTick, forceLoop);
        applyPart(this.body, clip, NpcJsonEmoteClip.BodyPart.BODY, elapsedTick, forceLoop);
        applyPart(this.leftArm, clip, NpcJsonEmoteClip.BodyPart.LEFT_ARM, elapsedTick, forceLoop);
        applyPart(this.rightArm, clip, NpcJsonEmoteClip.BodyPart.RIGHT_ARM, elapsedTick, forceLoop);
        applyPart(this.leftLeg, clip, NpcJsonEmoteClip.BodyPart.LEFT_LEG, elapsedTick, forceLoop);
        applyPart(this.rightLeg, clip, NpcJsonEmoteClip.BodyPart.RIGHT_LEG, elapsedTick, forceLoop);

        copyWearLayersFromBaseParts();
    }

    private void copyWearLayersFromBaseParts() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }

    private void applyPart(
            ModelPart part,
            NpcJsonEmoteClip clip,
            NpcJsonEmoteClip.BodyPart bodyPart,
            float elapsedTick,
            boolean forceLoop
    ) {
        part.x = clip.sample(bodyPart, NpcJsonEmoteClip.Channel.X, elapsedTick, forceLoop);
        part.y = clip.sample(bodyPart, NpcJsonEmoteClip.Channel.Y, elapsedTick, forceLoop);
        part.z = clip.sample(bodyPart, NpcJsonEmoteClip.Channel.Z, elapsedTick, forceLoop);
        float bend = clip.sample(bodyPart, NpcJsonEmoteClip.Channel.BEND, elapsedTick, forceLoop);
        part.xRot = clip.sample(bodyPart, NpcJsonEmoteClip.Channel.PITCH, elapsedTick, forceLoop);
        part.yRot = clip.sample(bodyPart, NpcJsonEmoteClip.Channel.YAW, elapsedTick, forceLoop);
        part.zRot = clip.sample(bodyPart, NpcJsonEmoteClip.Channel.ROLL, elapsedTick, forceLoop);

        Direction bendDirection = switch (bodyPart) {
            case BODY -> Direction.DOWN;
            case LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG -> Direction.UP;
            default -> null;
        };
        if (bendDirection != null) {
            boolean nativeBendApplied = NpcPlayerAnimCompat.applyBend(part, bendDirection, bend);
            if (!nativeBendApplied) {
                // Fallback approximation if player-animation-lib bend helper is unavailable.
                part.xRot += bend;
            }
        }
    }

    private void resetToDefaultPlayerPivot() {
        this.head.setPos(0.0F, 0.0F, 0.0F);
        this.head.xRot = 0.0F;
        this.head.yRot = 0.0F;
        this.head.zRot = 0.0F;

        this.body.setPos(0.0F, 0.0F, 0.0F);
        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;

        this.rightArm.setPos(-5.0F, 2.0F, 0.0F);
        this.rightArm.xRot = 0.0F;
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;

        this.leftArm.setPos(5.0F, 2.0F, 0.0F);
        this.leftArm.xRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        this.leftArm.zRot = 0.0F;

        this.rightLeg.setPos(-1.9F, 12.0F, 0.1F);
        this.rightLeg.xRot = 0.0F;
        this.rightLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;

        this.leftLeg.setPos(1.9F, 12.0F, 0.1F);
        this.leftLeg.xRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.leftLeg.zRot = 0.0F;
    }
}
