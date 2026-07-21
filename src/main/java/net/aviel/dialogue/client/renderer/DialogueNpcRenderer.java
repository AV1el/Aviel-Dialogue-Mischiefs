package net.aviel.dialogue.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.client.NpcEmoteClientCache;
import net.aviel.dialogue.client.NpcPlayerAnimCompat;
import net.aviel.dialogue.client.model.DialogueNpcModel;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.emote.NpcJsonEmoteClip;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class DialogueNpcRenderer extends MobRenderer<DialogueNpcEntity, DialogueNpcModel> {
    /** Bundled ADM look used whenever an NPC has no skin of its own. */
    private static final ResourceLocation DEFAULT_TEXTURE = AvielsDialogueMod.id("textures/entity/npc/aviel__.png");
    private static final ResourceLocation SLIM_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");

    private final DialogueNpcModel steveModel;
    private final DialogueNpcModel slimModel;

    public DialogueNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new DialogueNpcModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.steveModel = this.model;
        this.slimModel = new DialogueNpcModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
    }

    @Override
    public void render(DialogueNpcEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = "slim".equals(entity.getPlayerModel()) ? this.slimModel : this.steveModel;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DialogueNpcEntity entity) {
        String skin = entity.getSkin();
        if (!skin.isBlank()) {
            ResourceLocation custom = ResourceLocation.tryParse(skin);
            if (custom != null) {
                return custom;
            }
        }
        return "slim".equals(entity.getPlayerModel()) ? SLIM_TEXTURE : DEFAULT_TEXTURE;
    }

    // Vanilla players render at 15/16 model scale; without it the NPC looks noticeably bigger than a player.
    private static final float PLAYER_VISUAL_SCALE = 0.9375F;

    @Override
    protected void scale(DialogueNpcEntity entity, PoseStack poseStack, float partialTick) {
        float scale = entity.getModelScale() * PLAYER_VISUAL_SCALE;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected void setupRotations(DialogueNpcEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, float scale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, scale);
        applyNpcBodyTransform(entity, poseStack, partialTicks);
    }

    private void applyNpcBodyTransform(DialogueNpcEntity entity, PoseStack poseStack, float partialTicks) {
        if (entity == null || poseStack == null) return;
        if (!entity.isNpcEmotePlaying()) return;
        if (entity.level() == null) return;

        String fileName = entity.getNpcEmoteFileName();
        if (fileName.isBlank()) return;

        NpcJsonEmoteClip clip = NpcEmoteClientCache.getEmote(fileName);
        if (clip == null) return;
        byte[] jsonBytes = NpcEmoteClientCache.getEmoteBytes(fileName);
        if (jsonBytes == null || jsonBytes.length == 0) return;

        boolean repeat = entity.isNpcEmoteRepeatEnabled();
        float elapsedTick = (entity.level().getGameTime() - entity.getNpcEmoteStartTick()) + partialTicks;
        if (!clip.isPlayingAt(elapsedTick, repeat)) return;

        NpcPlayerAnimCompat.applyBodyTransforms(
                fileName,
                jsonBytes,
                clip,
                elapsedTick,
                repeat,
                poseStack,
                partialTicks
        );
    }
}
