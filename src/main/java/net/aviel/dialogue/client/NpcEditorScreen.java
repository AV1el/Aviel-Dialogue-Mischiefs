package net.aviel.dialogue.client;

import net.aviel.dialogue.client.AdmGuiKit.Rect;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.network.NpcEditorActionPacket;
import net.aviel.dialogue.network.OpenNpcCombatEditorPacket;
import net.aviel.dialogue.network.OpenNpcEditorPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class NpcEditorScreen extends Screen {
    enum Category { DISPLAY, STATS, AI, INVENTORY, DIALOGUE }
    private record Label(Component text, int x, int y, int width) { }
    private final OpenNpcEditorPacket info;
    private final NpcEditorBasicPane basic;
    private final AmethystPicker picker = new AmethystPicker();
    private final List<AbstractWidget> chrome = new ArrayList<>();
    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final Map<EditBox, Rect> fields = new HashMap<>();
    private final List<AbstractWidget> selected = new ArrayList<>();
    private NpcEditorLayout layout;
    private NpcTemplateEditorPane templatePane;
    private String template;
    private String requestedTemplate;
    private Category category = Category.DISPLAY;
    private boolean orders;
    private int scroll;
    private int contentHeight;
    private boolean appearanceDirty;

    public NpcEditorScreen(OpenNpcEditorPacket packet) {
        super(text("title"));
        info = packet;
        basic = new NpcEditorBasicPane(this, packet.dialogues(), packet.skins());
    }

    private static Component text(String key) { return Component.translatable("message.adm.npc.editor." + key); }
    Font editorFont() { return font; }
    private DialogueNpcEntity npc() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getEntity(info.entityId()) instanceof DialogueNpcEntity npc && npc.isAlive()
                && npc.getUUID().equals(info.npcUuid()) ? npc : null;
    }

    @Override
    protected void init() {
        layout = NpcEditorLayout.of(width, height);
        chrome.clear(); content.clear(); labels.clear(); fields.clear(); selected.clear();
        var npc = npc();
        if (npc == null) return;
        if (template == null) template = info.templates().contains(npc.getTemplateId()) ? npc.getTemplateId()
                : info.templates().stream().findFirst().orElse("");
        buildChrome();
        buildContent(npc);
        if (!template.isEmpty() && !template.equals(requestedTemplate)) {
            requestedTemplate = template;
            sendEditorAction("combat_open", template, 0);
        }
    }

    private void buildChrome() {
        Rect tabs = layout.tabs();
        int count = Category.values().length;
        int tabWidth = (tabs.w() - (count - 1) * 3) / count;
        for (var tab : Category.values()) {
            String fullKey = "tab." + tab.name().toLowerCase(java.util.Locale.ROOT);
            String key = fullKey;
            if (tabWidth < 78) key += "_short";
            Button button = addChrome(Button.builder(text(key), clicked -> {
                basic.pushName(); category = tab; scroll = 0; rebuildWidgets();
            }).tooltip(Tooltip.create(text(fullKey))).bounds(tabs.x() + tab.ordinal() * (tabWidth + 3), tabs.y(), tabWidth, tabs.h()).build());
            if (category == tab) selected.add(button);
        }
        Rect frame = layout.frame();
        addChrome(Button.builder(Component.literal("x"), button -> onClose()).bounds(frame.x() + frame.w() - 25, frame.y() + 6, 18, 18).build());
        Rect footer = layout.footer();
        int actionWidth = Math.max(64, Math.min(125, footer.w() / 4));
        int templateWidth = footer.w() - actionWidth * 2 - 12;
        addChrome(Button.builder(Component.literal(template.isEmpty() ? text("none").getString() : template), button ->
                pick(text("template"), info.templates(), Function.identity(), template, value -> {
                    basic.pushName(); template = value; templatePane = null; scroll = 0; rebuildWidgets();
                })).bounds(footer.x(), footer.y(), templateWidth, 20).build());
        addChrome(Button.builder(text(actionWidth < 100 ? "save_short" : "save_template"), button -> {
            basic.pushName(); if (basic.applyDialogueColor() && templatePane != null) templatePane.save();
        }).tooltip(Tooltip.create(text("save_template"))).bounds(footer.x() + templateWidth + 6, footer.y(), actionWidth, 20).build()).active = templatePane != null;
        addChrome(Button.builder(text("apply"), button -> {
            basic.pushName(); sendEditorAction("combat_apply", template, 0);
        }).bounds(footer.x() + templateWidth + actionWidth + 12, footer.y(), actionWidth, 20).build()).active = templatePane != null;
    }

    private void buildContent(DialogueNpcEntity npc) {
        Rect area = layout.content();
        int pane = category == Category.STATS ? 0 : orders ? 3 : 1;
        contentHeight = switch (category) {
            case DISPLAY -> 284;
            case INVENTORY -> 224;
            case DIALOGUE -> 24 + 80 + NpcDialogueColorControl.height(area.w());
            default -> 24 + (templatePane == null ? 20 : templatePane.contentHeight(pane, area.w())) + (category == Category.AI ? 28 : 0);
        };
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - area.h())));
        int y = area.y() + 24 - scroll;
        addLabel(text("tab." + category.name().toLowerCase(java.util.Locale.ROOT)), area.x(), area.y() + 5 - scroll, area.w());
        switch (category) {
            case DISPLAY -> basic.buildAppearance(area.x(), y, area.w(), npc);
            case INVENTORY -> basic.buildInventory(area.x(), y, area.w());
            case DIALOGUE -> basic.buildDialogue(area.x(), y, area.w(), npc);
            default -> {
                if (category == Category.AI) {
                    int half = (area.w() - 6) / 2;
                    var targeting = addContentWidget(Button.builder(text("targeting"), button -> { orders = false; scroll = 0; rebuildWidgets(); })
                            .bounds(area.x(), y, half, 20).build());
                    var order = addContentWidget(Button.builder(Component.translatable("message.adm.combat_editor.orders"), button -> { orders = true; scroll = 0; rebuildWidgets(); })
                            .bounds(area.x() + half + 6, y, half, 20).build());
                    selected.add(orders ? order : targeting);
                    y += 28;
                }
                if (templatePane != null) templatePane.build(pane, area.x(), y, area.w());
                else addLabel(text("loading"), area.x(), y, area.w());
            }
        }
    }

    private <T extends AbstractWidget> T addChrome(T widget) { chrome.add(widget); return addRenderableWidget(widget); }
    <T extends AbstractWidget> T addContentWidget(T widget) {
        if (widget instanceof EditBox field) {
            fields.put(field, NpcEditorTheme.field(field));
        }
        Rect bounds = widget instanceof EditBox field ? fields.get(field) : new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        Rect viewport = layout.content();
        widget.visible = bounds.y() >= viewport.y() && bounds.y() + bounds.h() <= viewport.y() + viewport.h();
        content.add(widget);
        return addRenderableWidget(widget);
    }

    void addLabel(String key, int x, int y, int width) { addLabel(Component.translatable("message.adm.combat_editor." + key), x, y, width); }
    void addLabel(Component text, int x, int y, int width) { labels.add(new Label(text, x, y, width)); }
    void pick(Component title, List<String> values, Function<String, String> display, String current, Consumer<String> accept) {
        picker.open(font, new AmethystPicker.Request(title.getString(), values, display, current, accept));
    }
    void sendEditorAction(String action, String value, float number) {
        if (!action.startsWith("combat_") && !action.equals("dialogue_test")) appearanceDirty = true;
        PacketDistributor.sendToServer(new NpcEditorActionPacket(info.npcUuid(), action, value, number));
    }

    public void acceptTemplate(OpenNpcCombatEditorPacket packet) {
        if (!info.npcUuid().equals(packet.npc()) || !packet.template().equals(template)) return;
        if (templatePane == null || !templatePane.matches(packet) || packet.status().isEmpty() || packet.status().equals("applied")) templatePane = new NpcTemplateEditorPane(this, packet);
        if (packet.status().equals("saved") || packet.status().equals("applied")) appearanceDirty = false;
        if (packet.status().equals("applied")) basic.resetDialogueColor();
        templatePane.acknowledge(packet);
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var npc = npc();
        if (npc == null) { onClose(); return; }
        graphics.fill(0, 0, width, height, 0x77000000);
        Rect frame = layout.frame();
        NpcEditorTheme.box(graphics, frame, NpcEditorTheme.BACKGROUND, NpcEditorTheme.BORDER);
        graphics.fill(frame.x() + 1, frame.y() + 1, frame.x() + frame.w() - 1, frame.y() + 28, 0xFF1A1026);
        graphics.drawString(font, title, frame.x() + 10, frame.y() + 10, NpcEditorTheme.GOLD, false);
        graphics.drawString(font, AdmGuiKit.clip(font, npc.getProfileName(), frame.w() - 160), frame.x() + 120, frame.y() + 10, NpcEditorTheme.TEXT, false);
        int mx = picker.isOpen() ? -1 : mouseX;
        int my = picker.isOpen() ? -1 : mouseY;
        drawPreview(graphics, npc);
        Rect area = layout.content();
        graphics.enableScissor(area.x(), area.y(), area.x() + area.w(), area.y() + area.h());
        for (Label label : labels) graphics.drawString(font, AdmGuiKit.clip(font, label.text().getString(), label.width()), label.x(), label.y(), NpcEditorTheme.MUTED, false);
        for (AbstractWidget widget : content) {
            if (widget instanceof EditBox field && field.visible) NpcEditorTheme.box(graphics, fields.get(field), NpcEditorTheme.SURFACE, field.isFocused() ? NpcEditorTheme.ACCENT : NpcEditorTheme.BORDER);
            NpcEditorTheme.widget(graphics, widget, mx, my, partialTick, selected.contains(widget));
        }
        basic.render(graphics, mx, my, npc, category);
        graphics.disableScissor();
        for (AbstractWidget widget : chrome) NpcEditorTheme.widget(graphics, widget, mx, my, partialTick, selected.contains(widget));
        drawStatus(graphics);
        drawScrollbar(graphics);
        picker.render(graphics, font, frame, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics graphics, DialogueNpcEntity npc) {
        Rect rect = layout.preview();
        graphics.fill(rect.x() - 3, rect.y(), rect.x() + rect.w() + 2, rect.y() + rect.h(), 0xAA120C1D);
        NpcEditorPreview.render(graphics, new Rect(rect.x(), rect.y() + 4, rect.w(), Math.max(20, rect.h() - 38)), npc);
        graphics.drawCenteredString(font, AdmGuiKit.clip(font, npc.getProfileName(), rect.w() - 4), rect.centerX(), rect.y() + rect.h() - 28, NpcEditorTheme.TEXT);
        graphics.drawCenteredString(font, AdmGuiKit.clip(font, String.format(java.util.Locale.ROOT, "%.0f / %.0f HP", npc.getHealth(), npc.getMaxHealth()), rect.w() - 4),
                rect.centerX(), rect.y() + rect.h() - 14, NpcEditorTheme.GOLD);
    }

    private void drawStatus(GuiGraphics graphics) {
        String status = templatePane == null ? "pending" : templatePane.status();
        if (appearanceDirty && !status.equals("pending") && !status.equals("error")) status = "unsaved";
        if (!status.isEmpty()) graphics.drawString(font, AdmGuiKit.clip(font, Component.translatable("message.adm.combat_editor." + status).getString(), layout.footer().w()),
                layout.footer().x(), layout.footer().y() + 26, status.equals("invalid") || status.equals("error") ? 0xFFFF9D8F : NpcEditorTheme.MUTED, false);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        Rect area = layout.content();
        int max = contentHeight - area.h();
        if (max <= 0) return;
        int thumb = Math.max(12, area.h() * area.h() / contentHeight);
        int y = area.y() + scroll * (area.h() - thumb) / max;
        graphics.fill(area.x() + area.w() + 4, area.y(), area.x() + area.w() + 6, area.y() + area.h(), NpcEditorTheme.SURFACE);
        graphics.fill(area.x() + area.w() + 4, y, area.x() + area.w() + 6, y + thumb, NpcEditorTheme.ACCENT);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (picker.isOpen()) { picker.mouseClicked(x, y); return true; }
        if (layout.content().contains(x, y) && category == Category.DISPLAY && basic.click(x, y)) return true;
        for (var field : fields.entrySet()) {
            if (field.getKey().visible && field.getValue().contains(x, y)) {
                setFocused(field.getKey());
                return field.getKey().mouseClicked(Math.max(field.getKey().getX(), Math.min(x, field.getKey().getX() + field.getKey().getWidth() - 1)), field.getKey().getY() + 3, button);
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return category == Category.DISPLAY && basic.drag(x) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override
    public boolean mouseReleased(double x, double y, int button) { basic.release(); return super.mouseReleased(x, y, button); }
    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (picker.isOpen()) return picker.mouseScrolled(dy);
        if (layout.content().contains(x, y) && contentHeight > layout.content().h()) {
            scroll = Math.max(0, Math.min(contentHeight - layout.content().h(), scroll - (int) (dy * 18)));
            rebuildWidgets(); return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (picker.keyPressed(key, scanCode, modifiers)) return true;
        if (key == 257) basic.pushName();
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override
    public boolean charTyped(char value, int modifiers) { return picker.charTyped(value, modifiers) || super.charTyped(value, modifiers); }
    @Override
    public void onClose() { basic.pushName(); super.onClose(); }
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float tick) { }
    @Override
    public boolean isPauseScreen() { return false; }
}
