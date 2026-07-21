package net.aviel.dialogue.client;

import net.aviel.dialogue.client.AdmGuiKit.Rect;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.network.NpcEditorActionPacket;
import net.aviel.dialogue.network.OpenNpcEditorPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.aviel.dialogue.client.AdmGuiKit.clip;

/** Admin NPC editor opened by Shift+right-click; every change applies live on the server. */
public class NpcEditorScreen extends Screen {
    private static final String SKIN_PREFIX = "adm:textures/entity/npc/";
    private static final int COLOR_PANEL = 0xF60F0817;
    private static final int COLOR_CARD = 0xFF1B1026;
    private static final int COLOR_CARD_BORDER = 0xFF3A2A4E;
    private static final int COLOR_BOX = 0xFF241732;
    private static final int COLOR_BUY = 0xFF3C2A55;
    private static final int COLOR_TEXT = 0xFFF2E7FF;
    private static final int COLOR_MUTED = 0xFF9B8BA8;
    private static final int COLOR_GOLD = 0xFFFFD36B;
    private static final int COLOR_RED = 0xFFFF9D8F;
    private static final int ACCENT = 0xFFA986EC;

    private final int entityId;
    private final UUID npcUuid;
    private final List<String> dialogueOptions = new ArrayList<>();
    private final List<String> skinOptions = new ArrayList<>();
    private final List<String> templateOptions;
    private final AmethystSlider scaleSlider = new AmethystSlider(0.25F, 3.0F, 0.05F, value -> send("scale", "", value));
    private final AmethystSlider lookSlider = new AmethystSlider(0.0F, 64.0F, 1.0F, value -> send("look", "", value));
    private final AmethystPicker picker = new AmethystPicker();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private EditBox nameBox;
    private String lastSentName = "";
    private int templateIndex;
    private long removeArmedUntil;

    public NpcEditorScreen(OpenNpcEditorPacket packet) {
        super(Component.translatable("message.adm.npc.editor.title"));
        this.entityId = packet.entityId();
        this.npcUuid = packet.npcUuid();
        this.dialogueOptions.add("");
        this.dialogueOptions.addAll(packet.dialogues());
        this.skinOptions.add("");
        this.skinOptions.addAll(packet.skins());
        this.templateOptions = packet.templates();
    }

    private DialogueNpcEntity npc() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        return this.minecraft.level.getEntity(this.entityId) instanceof DialogueNpcEntity npc && npc.isAlive() ? npc : null;
    }

    @Override
    protected void init() {
        this.panelW = Mth.clamp(this.width - 32, 440, 500);
        this.panelH = Mth.clamp(this.height - 32, 290, 320);
        this.panelX = this.width / 2 - this.panelW / 2;
        this.panelY = this.height / 2 - this.panelH / 2;
        DialogueNpcEntity npc = npc();
        String name = npc == null ? "" : npc.getProfileName().replace('§', '&');
        this.nameBox = this.addRenderableWidget(new EditBox(this.font, rightX() + 4, this.panelY + 46, rightW() - 8, 14, Component.translatable("message.adm.npc.editor.name")));
        this.nameBox.setBordered(false);
        this.nameBox.setMaxLength(80);
        this.nameBox.setValue(name);
        this.nameBox.setTextColor(COLOR_TEXT);
        this.lastSentName = name;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DialogueNpcEntity npc = npc();
        if (npc == null) {
            this.onClose();
            return;
        }
        AdmGuiKit.fillRounded(graphics, this.panelX - 3, this.panelY - 3, this.panelW + 6, this.panelH + 6, 0x66000000);
        AdmGuiKit.fillRounded(graphics, this.panelX, this.panelY, this.panelW, this.panelH, COLOR_PANEL);
        AdmGuiKit.outlineRounded(graphics, this.panelX, this.panelY, this.panelW, this.panelH, 0xFF574A75);
        int controlsMouseX = this.picker.isOpen() ? -1 : mouseX;
        int controlsMouseY = this.picker.isOpen() ? -1 : mouseY;
        drawHeader(graphics, controlsMouseX, controlsMouseY, npc);
        drawPreview(graphics, mouseX, mouseY, npc);
        drawControls(graphics, controlsMouseX, controlsMouseY, npc);
        super.render(graphics, controlsMouseX, controlsMouseY, partialTick);
        this.picker.render(graphics, this.font, new Rect(this.panelX, this.panelY, this.panelW, this.panelH), mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY, DialogueNpcEntity npc) {
        graphics.fill(this.panelX + 1, this.panelY + 2, this.panelX + this.panelW - 1, this.panelY + 26, 0xFF1A0F28);
        graphics.fill(this.panelX + 1, this.panelY + 26, this.panelX + this.panelW - 1, this.panelY + 27, ACCENT);
        graphics.drawString(this.font, Component.translatable("message.adm.npc.editor.title"), this.panelX + 12, this.panelY + 9, COLOR_GOLD, false);
        graphics.drawString(this.font, clip(this.font, npc.getProfileName(), this.panelW - 200), this.panelX + 120, this.panelY + 9, COLOR_TEXT, false);
        boolean closeHover = closeRect().contains(mouseX, mouseY);
        graphics.drawCenteredString(this.font, "x", closeRect().centerX(), closeRect().y() + 3, closeHover ? COLOR_TEXT : COLOR_MUTED);
    }

    private void drawPreview(GuiGraphics graphics, int mouseX, int mouseY, DialogueNpcEntity npc) {
        int x = this.panelX + 10;
        int y = this.panelY + 34;
        int w = 140;
        int h = this.panelH - 44;
        AdmGuiKit.fillRounded(graphics, x, y, w, h, 0xFF120A1C);
        AdmGuiKit.outlineRounded(graphics, x, y, w, h, COLOR_CARD_BORDER);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, x + 4, y + 4, x + w - 4, y + h - 16,
                (int) (55 / Math.max(0.5F, npc.getModelScale())), 0.1F, mouseX, mouseY, npc);
        graphics.drawCenteredString(this.font, clip(this.font, modelLabel(npc.getPlayerModel()) + " · ×" + String.format(Locale.ROOT, "%.2f", npc.getModelScale()), w - 8), x + w / 2, y + h - 12, COLOR_MUTED);
    }

    private void drawControls(GuiGraphics graphics, int mouseX, int mouseY, DialogueNpcEntity npc) {
        graphics.drawString(this.font, Component.translatable("message.adm.npc.editor.name"), rightX(), this.panelY + 35, COLOR_MUTED, false);
        AdmGuiKit.fillRounded(graphics, rightX(), this.panelY + 43, rightW(), 18, COLOR_BOX);

        drawCycleRow(graphics, mouseX, mouseY, 0, Component.translatable("message.adm.npc.editor.dialogue").getString(),
                npc.getDialogueFile().isBlank() ? none() : npc.getDialogueFile());
        drawCycleRow(graphics, mouseX, mouseY, 1, Component.translatable("message.adm.npc.editor.skin").getString(), displaySkin(npc));
        drawPill(graphics, mouseX, mouseY, toggleRect(2, 0), Component.translatable("message.adm.npc.editor.model", modelLabel(npc.getPlayerModel())).getString(), COLOR_TEXT);
        drawPill(graphics, mouseX, mouseY, toggleRect(2, 1),
                Component.translatable(npc.isDialogueInvulnerable() ? "message.adm.npc.editor.invulnerable_on" : "message.adm.npc.editor.invulnerable_off").getString(), COLOR_TEXT);
        this.scaleSlider.render(graphics, this.font, rowRect(3), Component.translatable("message.adm.npc.editor.scale").getString(), npc.getModelScale());
        this.lookSlider.render(graphics, this.font, rowRect(4), Component.translatable("message.adm.npc.editor.look").getString(), npc.getLookDistance());
        drawTemplateRow(graphics, mouseX, mouseY);
        drawActionRow(graphics, mouseX, mouseY);
    }

    private void drawCycleRow(GuiGraphics graphics, int mouseX, int mouseY, int row, String label, String value) {
        int y = rowY(row);
        graphics.drawString(this.font, label, rightX(), y + 4, COLOR_MUTED, false);
        drawValueBox(graphics, mouseX, mouseY, cycleValueRect(row), value);
    }

    /** Clickable dropdown-style value box that opens the picker popup. */
    private void drawValueBox(GuiGraphics graphics, int mouseX, int mouseY, Rect rect, String value) {
        boolean hover = rect.contains(mouseX, mouseY);
        AdmGuiKit.fillRounded(graphics, rect.x(), rect.y(), rect.w(), rect.h(), hover ? COLOR_BUY : COLOR_BOX);
        AdmGuiKit.outlineRounded(graphics, rect.x(), rect.y(), rect.w(), rect.h(), hover ? ACCENT : COLOR_CARD_BORDER);
        graphics.drawString(this.font, clip(this.font, value, rect.w() - 20), rect.x() + 6, rect.y() + 3, COLOR_TEXT, false);
        int ax = rect.x() + rect.w() - 11;
        int ay = rect.y() + 6;
        graphics.fill(ax, ay, ax + 5, ay + 1, hover ? COLOR_TEXT : COLOR_MUTED);
        graphics.fill(ax + 1, ay + 1, ax + 4, ay + 2, hover ? COLOR_TEXT : COLOR_MUTED);
        graphics.fill(ax + 2, ay + 2, ax + 3, ay + 3, hover ? COLOR_TEXT : COLOR_MUTED);
    }

    private void drawPill(GuiGraphics graphics, int mouseX, int mouseY, Rect rect, String label, int textColor) {
        boolean hover = rect.contains(mouseX, mouseY);
        AdmGuiKit.fillRounded(graphics, rect.x(), rect.y(), rect.w(), rect.h(), hover ? COLOR_BUY : COLOR_CARD);
        AdmGuiKit.outlineRounded(graphics, rect.x(), rect.y(), rect.w(), rect.h(), hover ? ACCENT : COLOR_CARD_BORDER);
        graphics.drawCenteredString(this.font, clip(this.font, label, rect.w() - 6), rect.centerX(), rect.y() + 3, hover ? COLOR_TEXT : textColor);
    }

    private void drawTemplateRow(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = rowY(5);
        graphics.drawString(this.font, Component.translatable("message.adm.npc.editor.template"), rightX(), y + 4, COLOR_MUTED, false);
        drawValueBox(graphics, mouseX, mouseY, templateValueRect(), currentTemplate());
        drawPill(graphics, mouseX, mouseY, templateApplyRect(), Component.translatable("message.adm.npc.editor.apply").getString(), COLOR_TEXT);
    }

    private void drawActionRow(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPill(graphics, mouseX, mouseY, actionRect(0), Component.translatable("message.adm.npc.editor.equip_from_me").getString(), COLOR_TEXT);
        drawPill(graphics, mouseX, mouseY, actionRect(1), Component.translatable("message.adm.npc.editor.equip_clear").getString(), COLOR_TEXT);
        Rect remove = actionRect(2);
        boolean armed = System.currentTimeMillis() < this.removeArmedUntil;
        boolean hover = remove.contains(mouseX, mouseY);
        AdmGuiKit.fillRounded(graphics, remove.x(), remove.y(), remove.w(), remove.h(), armed ? 0xFF7A2E2E : hover ? 0xFF4A2333 : COLOR_CARD);
        AdmGuiKit.outlineRounded(graphics, remove.x(), remove.y(), remove.w(), remove.h(), armed || hover ? COLOR_RED : COLOR_CARD_BORDER);
        String label = Component.translatable(armed ? "message.adm.npc.editor.remove_confirm" : "message.adm.npc.editor.remove").getString();
        graphics.drawCenteredString(this.font, clip(this.font, label, remove.w() - 6), remove.centerX(), remove.y() + 3, armed ? COLOR_TEXT : COLOR_RED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.picker.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (closeRect().contains(mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        DialogueNpcEntity npc = npc();
        if (npc == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pickerClicks(mouseX, mouseY, npc)
                || toggleClicks(mouseX, mouseY, npc)
                || templateClicks(mouseX, mouseY)
                || actionClicks(mouseX, mouseY)
                || this.scaleSlider.mouseClicked(mouseX, mouseY)
                || this.lookSlider.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean pickerClicks(double mouseX, double mouseY, DialogueNpcEntity npc) {
        if (cycleValueRect(0).contains(mouseX, mouseY)) {
            this.picker.open(this.font, new AmethystPicker.Request(
                    Component.translatable("message.adm.npc.editor.dialogue").getString(),
                    this.dialogueOptions,
                    option -> option.isBlank() ? none() : option,
                    npc.getDialogueFile(),
                    option -> send("dialogue", option, 0.0F)));
            return true;
        }
        if (cycleValueRect(1).contains(mouseX, mouseY)) {
            this.picker.open(this.font, new AmethystPicker.Request(
                    Component.translatable("message.adm.npc.editor.skin").getString(),
                    this.skinOptions,
                    option -> option.isBlank() ? Component.translatable("message.adm.npc.editor.skin_default").getString() : option,
                    skinFileName(npc),
                    option -> send("skin", option, 0.0F)));
            return true;
        }
        if (templateValueRect().contains(mouseX, mouseY) && !this.templateOptions.isEmpty()) {
            this.picker.open(this.font, new AmethystPicker.Request(
                    Component.translatable("message.adm.npc.editor.template").getString(),
                    this.templateOptions,
                    option -> option,
                    currentTemplate(),
                    option -> this.templateIndex = Math.max(0, this.templateOptions.indexOf(option))));
            return true;
        }
        return false;
    }

    private boolean toggleClicks(double mouseX, double mouseY, DialogueNpcEntity npc) {
        if (toggleRect(2, 0).contains(mouseX, mouseY)) {
            send("model", "slim".equals(npc.getPlayerModel()) ? "steve" : "slim", 0.0F);
            return true;
        }
        if (toggleRect(2, 1).contains(mouseX, mouseY)) {
            send("invulnerable", "", npc.isDialogueInvulnerable() ? 0.0F : 1.0F);
            return true;
        }
        return false;
    }

    private boolean templateClicks(double mouseX, double mouseY) {
        if (!this.templateOptions.isEmpty() && templateApplyRect().contains(mouseX, mouseY)) {
            send("template", currentTemplate(), 0.0F);
            return true;
        }
        return false;
    }

    private boolean actionClicks(double mouseX, double mouseY) {
        if (actionRect(0).contains(mouseX, mouseY)) {
            send("equip_from_me", "", 0.0F);
            return true;
        }
        if (actionRect(1).contains(mouseX, mouseY)) {
            send("equip_clear", "", 0.0F);
            return true;
        }
        if (actionRect(2).contains(mouseX, mouseY)) {
            if (System.currentTimeMillis() < this.removeArmedUntil) {
                send("remove", "", 0.0F);
                this.onClose();
            } else {
                this.removeArmedUntil = System.currentTimeMillis() + 3000L;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scaleSlider.mouseDragged(mouseX) || this.lookSlider.mouseDragged(mouseX)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scaleSlider.mouseReleased();
        this.lookSlider.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.picker.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 257 && this.nameBox != null && this.nameBox.isFocused()) {
            pushNameIfChanged();
            this.nameBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (this.picker.charTyped(character, modifiers)) {
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.picker.mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void pushNameIfChanged() {
        if (this.nameBox == null) {
            return;
        }
        String value = this.nameBox.getValue().trim();
        if (!value.isBlank() && !value.equals(this.lastSentName)) {
            send("name", value, 0.0F);
            this.lastSentName = value;
        }
    }

    @Override
    public void onClose() {
        pushNameIfChanged();
        super.onClose();
    }

    private void send(String action, String value, float number) {
        PacketDistributor.sendToServer(new NpcEditorActionPacket(this.npcUuid, action, value, number));
    }

    private String currentTemplate() {
        return this.templateOptions.isEmpty() ? none()
                : this.templateOptions.get(Mth.clamp(this.templateIndex, 0, this.templateOptions.size() - 1));
    }

    private String none() {
        return Component.translatable("message.adm.npc.editor.none").getString();
    }

    private String displaySkin(DialogueNpcEntity npc) {
        String file = skinFileName(npc);
        return file.isBlank() ? Component.translatable("message.adm.npc.editor.skin_default").getString() : file;
    }

    private String skinFileName(DialogueNpcEntity npc) {
        String skin = npc.getSkin();
        return skin.startsWith(SKIN_PREFIX) ? skin.substring(SKIN_PREFIX.length()) : skin;
    }

    /** The slim player model is shown to users under its familiar name. */
    private static String modelLabel(String model) {
        return "slim".equals(model) ? "alex" : model;
    }

    private int rightX() {
        return this.panelX + 160;
    }

    private int rightW() {
        return this.panelX + this.panelW - rightX() - 10;
    }

    private int rowY(int index) {
        return this.panelY + 68 + index * 26;
    }

    private Rect rowRect(int row) {
        return new Rect(rightX(), rowY(row), rightW(), 20);
    }

    private Rect cycleValueRect(int row) {
        return new Rect(rightX() + 72, rowY(row), rightW() - 72, 14);
    }

    private Rect templateValueRect() {
        return new Rect(rightX() + 72, rowY(5), rightW() - 138, 14);
    }

    private Rect templateApplyRect() {
        return new Rect(rightX() + rightW() - 60, rowY(5), 60, 14);
    }

    private Rect toggleRect(int row, int index) {
        int half = (rightW() - 6) / 2;
        return new Rect(rightX() + index * (half + 6), rowY(row), half, 14);
    }

    private Rect actionRect(int index) {
        int third = (rightW() - 12) / 3;
        return new Rect(rightX() + index * (third + 6), rowY(6), third, 14);
    }

    private Rect closeRect() {
        return new Rect(this.panelX + this.panelW - 20, this.panelY + 6, 14, 14);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
