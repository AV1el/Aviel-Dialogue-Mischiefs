package net.aviel.dialogue.client;

import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.NpcEquipment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;

final class NpcEditorBasicPane {
    private record Slot(EquipmentSlot slot, int x, int y, int width) { }
    private final NpcEditorScreen editor;
    private final List<String> dialogues;
    private final List<String> skins;
    private final List<Slot> slots = new ArrayList<>();
    private final AmethystSlider scale;
    private final AmethystSlider look;
    private final NpcDialogueColorControl color;
    private AdmGuiKit.Rect scaleArea;
    private AdmGuiKit.Rect lookArea;
    private String nameDraft;
    private String lastName;
    private EditBox nameField;
    private long removeArmed;
    private Button removeButton;

    NpcEditorBasicPane(NpcEditorScreen editor, List<String> dialogues, List<String> skins) {
        this.editor = editor;
        color = new NpcDialogueColorControl(editor);
        this.dialogues = new ArrayList<>(dialogues);
        this.skins = new ArrayList<>(skins);
        this.dialogues.add(0, "");
        this.skins.add(0, "");
        scale = new AmethystSlider(0.25F, 3, 0.05F, value -> editor.sendEditorAction("scale", "", value));
        look = new AmethystSlider(0, 64, 1, value -> editor.sendEditorAction("look", "", value));
    }

    private static Component text(String key) { return Component.translatable("message.adm.npc.editor." + key); }

    void buildAppearance(int x, int y, int width, DialogueNpcEntity npc) {
        if (nameDraft == null) { nameDraft = npc.getProfileName().replace('\u00a7', '&'); lastName = nameDraft; }
        editor.addLabel(text("name"), x, y, width);
        nameField = editor.addContentWidget(new EditBox(editor.editorFont(), x, y + 11, width, 18, text("name")));
        nameField.setMaxLength(80);
        nameField.setValue(nameDraft);
        nameField.setResponder(value -> nameDraft = value);
        editor.addLabel(text("skin"), x, y + 36, width);
        editor.addContentWidget(Button.builder(Component.literal(npc.getSkin().isEmpty() ? text("skin_default").getString() : npc.getSkin()),
                button -> editor.pick(text("skin"), skins, value -> value.isEmpty() ? text("skin_default").getString() : value,
                        npc.getSkin().replace("adm:textures/entity/npc/", ""), value -> {
                            editor.sendEditorAction("skin", value, 0);
                            button.setMessage(Component.literal(value.isEmpty() ? text("skin_default").getString() : value));
                        })).bounds(x, y + 47, width, 20).build());
        editor.addContentWidget(CycleButton.<String>builder(Component::literal).withValues("steve", "slim")
                .withInitialValue(npc.getPlayerModel()).create(x, y + 76, width, 20, text("model_label"),
                        (button, value) -> editor.sendEditorAction("model", value, 0)));
        editor.addContentWidget(Checkbox.builder(text("invulnerable_label"), editor.editorFont()).pos(x, y + 106).maxWidth(width)
                .selected(npc.isDialogueInvulnerable()).onValueChange((box, value) -> editor.sendEditorAction("invulnerable", "", value ? 1 : 0)).build());
        editor.addContentWidget(Checkbox.builder(text("name_visible"), editor.editorFont()).pos(x, y + 132).maxWidth(width)
                .selected(npc.isCustomNameVisible()).onValueChange((box, value) -> editor.sendEditorAction("name_visible", "", value ? 1 : 0)).build());
        scaleArea = new AdmGuiKit.Rect(x, y + 166, width, 20);
        lookArea = new AdmGuiKit.Rect(x, y + 200, width, 20);
        buildRemoval(x, y + 236, width);
    }

    void buildDialogue(int x, int y, int width, DialogueNpcEntity npc) {
        editor.addLabel(text("dialogue"), x, y, width);
        editor.addContentWidget(Button.builder(Component.literal(npc.getDialogueFile().isEmpty() ? text("none").getString() : npc.getDialogueFile()),
                button -> editor.pick(text("dialogue"), dialogues, value -> value.isEmpty() ? text("none").getString() : value,
                        npc.getDialogueFile(), value -> {
                            editor.sendEditorAction("dialogue", value, 0);
                            button.setMessage(Component.literal(value.isEmpty() ? text("none").getString() : value));
                        })).bounds(x, y + 13, width, 20).build());
        editor.addContentWidget(Button.builder(text("test_dialogue"), button -> {
            if (color.apply()) editor.sendEditorAction("dialogue_test", "", 0);
        })
                .bounds(x, y + 46, width, 20).build());
        color.build(x, y + 80, width, npc.getDialoguePalette());
    }

    void buildInventory(int x, int y, int width) {
        slots.clear();
        int half = (width - 8) / 2;
        editor.addContentWidget(Button.builder(text("equip_from_me"), button -> editor.sendEditorAction("equip_from_me", "", 0))
                .bounds(x, y, half, 20).build());
        editor.addContentWidget(Button.builder(text("equip_clear"), button -> editor.sendEditorAction("equip_clear", "", 0))
                .bounds(x + half + 8, y, half, 20).build());
        int index = 0;
        for (EquipmentSlot slot : NpcEquipment.SLOTS) {
            slots.add(new Slot(slot, x, y + 32 + index * 28, width));
            index++;
        }
    }

    private void buildRemoval(int x, int y, int width) {
        removeButton = editor.addContentWidget(Button.builder(text("remove"), button -> {
            if (System.currentTimeMillis() < removeArmed) {
                editor.sendEditorAction("remove", "", 0);
                editor.onClose();
            } else {
                removeArmed = System.currentTimeMillis() + 3000;
                button.setMessage(text("remove_confirm"));
            }
        }).bounds(x, y, width, 20).build());
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, DialogueNpcEntity npc, NpcEditorScreen.Category category) {
        if (category == NpcEditorScreen.Category.DISPLAY) {
            if (removeButton != null && System.currentTimeMillis() >= removeArmed) removeButton.setMessage(text("remove"));
            scale.render(graphics, editor.editorFont(), scaleArea, text("scale").getString(), npc.getModelScale());
            look.render(graphics, editor.editorFont(), lookArea, text("look").getString(), npc.getLookDistance());
            if (nameField != null && !nameField.isFocused() && nameDraft.equals(lastName)) {
                String current = npc.getProfileName().replace('\u00a7', '&');
                if (!current.equals(nameDraft)) { nameDraft = current; lastName = current; nameField.setValue(current); }
            }
        }
        if (category == NpcEditorScreen.Category.DIALOGUE) color.render(graphics, npc.getDialoguePalette());
        if (category != NpcEditorScreen.Category.INVENTORY) return;
        for (Slot slot : slots) {
            var item = npc.getItemBySlot(slot.slot());
            NpcEditorTheme.box(graphics, new AdmGuiKit.Rect(slot.x(), slot.y(), 22, 22), 0xFF120D1D, NpcEditorTheme.BORDER);
            graphics.renderItem(item, slot.x() + 3, slot.y() + 3);
            graphics.drawString(editor.editorFont(), text("slot." + slot.slot().getName()), slot.x() + 30, slot.y() + 1, NpcEditorTheme.MUTED, false);
            graphics.drawString(editor.editorFont(), AdmGuiKit.clip(editor.editorFont(), item.isEmpty() ? text("none").getString() : item.getHoverName().getString(), slot.width() - 30),
                    slot.x() + 30, slot.y() + 12, NpcEditorTheme.TEXT, false);
        }
    }

    boolean click(double x, double y) { return scale.mouseClicked(x, y) || look.mouseClicked(x, y); }
    boolean drag(double x) { return scale.mouseDragged(x) || look.mouseDragged(x); }
    void release() { scale.mouseReleased(); look.mouseReleased(); }
    boolean applyDialogueColor() { return color.apply(); }
    void resetDialogueColor() { color.resetDraft(); }
    void pushName() {
        if (nameDraft != null && !nameDraft.isBlank() && !nameDraft.equals(lastName)) {
            editor.sendEditorAction("name", nameDraft, 0);
            lastName = nameDraft;
        }
    }
}
