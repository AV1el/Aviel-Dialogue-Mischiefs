package net.aviel.dialogue.client;

import com.google.gson.JsonSyntaxException;
import net.aviel.dialogue.network.NpcTradeActionPacket;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

import static net.aviel.dialogue.client.AdmGuiKit.clip;
import static net.aviel.dialogue.client.AdmGuiKit.isInside;
import static net.aviel.dialogue.client.NpcTradeItems.displayName;
import static net.aviel.dialogue.client.NpcTradeItems.firstStack;
import static net.aviel.dialogue.client.NpcTradeItems.stackFor;

public class NpcTradeScreen extends Screen {
    private static final int CARD_HEIGHT = 44;
    private static final int CARD_GAP = 6;
    private static final int[] QUANTITY_PRESETS = {1, 8, 64};

    private static final int COLOR_PANEL = 0xF60F0817;
    private static final int COLOR_HEADER = 0xFF1A0F28;
    private static final int COLOR_CARD = 0xFF1B1026;
    private static final int COLOR_CARD_HOVER = 0xFF221430;
    private static final int COLOR_CARD_SELECTED = 0xFF241538;
    private static final int COLOR_CARD_BORDER = 0xFF3A2A4E;
    private static final int COLOR_ICON_BOX = 0xFF241732;
    private static final int COLOR_DETAILS = 0xFF160D22;
    private static final int COLOR_STRIP = 0xFF120A1C;
    private static final int COLOR_BUY = 0xFF3C2A55;
    private static final int COLOR_BUY_HOVER = 0xFF4A3568;
    private static final int COLOR_TEXT = 0xFFF2E7FF;
    private static final int COLOR_MUTED = 0xFF9B8BA8;
    private static final int COLOR_GOLD = 0xFFFFD36B;
    private static final int COLOR_GREEN = 0xFF7FE0A9;
    private static final int COLOR_RED = 0xFFFF9D8F;
    private static final int COLOR_LOCKED_DOT = 0xFF6A5F78;

    private final UUID npcUuid;
    private final String npcName;
    private final String tradeFile;
    private final NpcTradeDefinition definition;
    private final String parseError;
    private final NpcTradeDefinition.TradeStyle style;
    private final int accent;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int offerOffset;
    private int selectedIndex;
    private int quantity = 1;

    public NpcTradeScreen(UUID npcUuid, String npcName, String tradeFile, String rawJson) {
        super(Component.translatable("message.adm.npc.trade.gui.title"));
        this.npcUuid = npcUuid == null ? new UUID(0L, 0L) : npcUuid;
        this.npcName = npcName == null || npcName.isBlank() ? "NPC" : npcName;
        this.tradeFile = tradeFile == null ? "" : tradeFile;
        NpcTradeDefinition parsed = null;
        String error = "";
        try {
            parsed = NpcTradeDefinition.fromJson(rawJson == null ? "" : rawJson);
        } catch (JsonSyntaxException | IllegalStateException ex) {
            error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        this.definition = parsed;
        this.parseError = error;
        this.style = parsed == null ? NpcTradeDefinition.TradeStyle.DEFAULT : parsed.style();
        this.accent = AdmGuiKit.parseColor(this.style.accentColor(), 0xFFA986EC);
        this.selectedIndex = parsed == null || parsed.offers().isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        this.panelW = Mth.clamp(this.width - 32, 440, 560);
        this.panelH = Mth.clamp(this.height - 32, 290, 340);
        this.panelX = this.width / 2 - this.panelW / 2;
        this.panelY = this.height / 2 - this.panelH / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AdmGuiKit.fillRounded(graphics, this.panelX - 3, this.panelY - 3, this.panelW + 6, this.panelH + 6, 0x66000000);
        AdmGuiKit.fillRounded(graphics, this.panelX, this.panelY, this.panelW, this.panelH, styleColor(this.style.panelColor(), COLOR_PANEL));
        AdmGuiKit.outlineRounded(graphics, this.panelX, this.panelY, this.panelW, this.panelH, 0xFF574A75);
        drawHeader(graphics, mouseX, mouseY);
        if (this.definition == null) {
            graphics.drawString(this.font, Component.translatable("message.adm.npc.trade.gui.invalid"), this.panelX + 14, this.panelY + 46, COLOR_RED, false);
            graphics.drawString(this.font, Component.literal(AdmGuiKit.clip(this.font, this.parseError, this.panelW - 28)), this.panelX + 14, this.panelY + 60, COLOR_TEXT, false);
        } else {
            drawOfferCards(graphics, mouseX, mouseY);
            drawShowcase(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(this.panelX + 1, this.panelY + 2, this.panelX + this.panelW - 1, this.panelY + 34, COLOR_HEADER);
        graphics.fill(this.panelX + 1, this.panelY + 34, this.panelX + this.panelW - 1, this.panelY + 35, accent);
        String name = AdmGuiKit.amp(DialogueTextLocalizer.localize(this.npcName));
        AdmGuiKit.fillRounded(graphics, this.panelX + 10, this.panelY + 6, 22, 22, COLOR_BUY);
        AdmGuiKit.outlineRounded(graphics, this.panelX + 10, this.panelY + 6, 22, 22, 0xFF8A6FC0);
        String initial = name.replaceAll("§.", "").isBlank() ? "?" : name.replaceAll("§.", "").substring(0, 1);
        graphics.drawCenteredString(this.font, initial, this.panelX + 21, this.panelY + 13, 0xFFD9C8FF);
        graphics.drawString(this.font, name, this.panelX + 40, this.panelY + 8, COLOR_GOLD, false);
        String subtitle = this.definition == null
                ? ""
                : AdmGuiKit.amp(DialogueTextLocalizer.localize((this.definition.title() + (this.definition.subtitle().isBlank() ? "" : " — " + this.definition.subtitle()))));
        graphics.drawString(this.font, clip(this.font, subtitle, this.panelW - 120), this.panelX + 40, this.panelY + 19, styleColor(this.style.mutedTextColor(), COLOR_MUTED), false);
        boolean closeHover = isInside(mouseX, mouseY, closeX(), closeY(), closeX() + 14, closeY() + 14);
        graphics.drawCenteredString(this.font, "x", closeX() + 7, closeY() + 3, closeHover ? COLOR_TEXT : COLOR_MUTED);
    }

    private void drawOfferCards(GuiGraphics graphics, int mouseX, int mouseY) {
        List<NpcTradeDefinition.Offer> offers = this.definition.offers();
        for (int row = 0; row < visibleCards(); row++) {
            int index = this.offerOffset + row;
            if (index >= offers.size()) {
                break;
            }
            drawOfferCard(graphics, mouseX, mouseY, offers.get(index), index, listY() + row * (CARD_HEIGHT + CARD_GAP));
        }
        if (offers.size() > visibleCards()) {
            int x = listX() + listW() + 3;
            int trackH = visibleCards() * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
            graphics.fill(x, listY(), x + 2, listY() + trackH, 0x33000000);
            int maxOffset = offers.size() - visibleCards();
            int thumbH = Math.max(14, trackH * visibleCards() / offers.size());
            int thumbY = listY() + (int) Math.round((double) this.offerOffset / maxOffset * (trackH - thumbH));
            graphics.fill(x, thumbY, x + 2, thumbY + thumbH, this.accent);
        }
    }

    private void drawOfferCard(GuiGraphics graphics, int mouseX, int mouseY, NpcTradeDefinition.Offer offer, int index, int cardY) {
        int x = listX();
        int w = listW();
        boolean selected = index == this.selectedIndex;
        boolean hovered = AdmGuiKit.isInside(mouseX, mouseY, x, cardY, x + w, cardY + CARD_HEIGHT);
        AdmGuiKit.fillRounded(graphics, x, cardY, w, CARD_HEIGHT, selected ? COLOR_CARD_SELECTED : hovered ? COLOR_CARD_HOVER : COLOR_CARD);
        AdmGuiKit.outlineRounded(graphics, x, cardY, w, CARD_HEIGHT, selected ? this.accent : COLOR_CARD_BORDER);
        AdmGuiKit.fillRounded(graphics, x + 8, cardY + 8, 28, 28, COLOR_ICON_BOX);
        ItemStack icon = firstStack(offer.result());
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 14, cardY + 14);
        }
        graphics.drawString(this.font, clip(this.font, AdmGuiKit.amp(DialogueTextLocalizer.localize(offer.title())), w - 58), x + 42, cardY + 8, COLOR_TEXT, false);
        drawPriceChips(graphics, offer, x + 42, cardY + 22);
        int dotColor = offer.locked() ? COLOR_LOCKED_DOT : canAfford(offer, 1) ? COLOR_GREEN : COLOR_RED;
        AdmGuiKit.fillRounded(graphics, x + w - 11, cardY + 6, 5, 5, dotColor);
    }

    private void drawPriceChips(GuiGraphics graphics, NpcTradeDefinition.Offer offer, int x, int y) {
        int chipX = x;
        int shown = 0;
        for (NpcTradeDefinition.TradeItem item : offer.cost()) {
            String label = item.count() + "×";
            int chipW = this.font.width(label) + 18;
            if (chipX + chipW > listX() + listW() - 14 || shown >= 3) {
                break;
            }
            AdmGuiKit.fillRounded(graphics, chipX, y, chipW, 14, COLOR_ICON_BOX);
            graphics.drawString(this.font, label, chipX + 4, y + 3, styleColor(this.style.priceColor(), COLOR_GOLD), false);
            ItemStack stack = stackFor(item);
            if (!stack.isEmpty()) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(chipX + chipW - 13.0F, y + 1.5F, 0.0F);
                pose.scale(0.7F, 0.7F, 1.0F);
                graphics.renderItem(stack, 0, 0);
                pose.popPose();
            }
            chipX += chipW + 4;
            shown++;
        }
    }

    private void drawShowcase(GuiGraphics graphics, int mouseX, int mouseY) {
        NpcTradeDefinition.Offer offer = selectedOffer();
        int x = detailsX();
        int y = listY();
        int w = detailsW();
        int h = detailsH();
        AdmGuiKit.fillRounded(graphics, x, y, w, h, COLOR_DETAILS);
        AdmGuiKit.outlineRounded(graphics, x, y, w, h, COLOR_CARD_BORDER);
        if (offer == null) {
            graphics.drawCenteredString(this.font, Component.translatable("message.adm.npc.trade.gui.no_offer"), x + w / 2, y + h / 2 - 4, COLOR_MUTED);
            return;
        }
        int centerX = x + w / 2;
        AdmGuiKit.fillRounded(graphics, centerX - 22, y + 10, 44, 44, COLOR_ICON_BOX);
        AdmGuiKit.outlineRounded(graphics, centerX - 22, y + 10, 44, 44, 0xFF574A75);
        ItemStack icon = firstStack(offer.result());
        if (!icon.isEmpty()) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(centerX - 16.0F, y + 16.0F, 0.0F);
            pose.scale(2.0F, 2.0F, 1.0F);
            graphics.renderItem(icon, 0, 0);
            pose.popPose();
        }
        graphics.drawCenteredString(this.font, AdmGuiKit.amp(DialogueTextLocalizer.localize(offer.title())), centerX, y + 60, COLOR_TEXT);
        String description = offer.description().isBlank() ? this.definition.subtitle() : offer.description();
        if (!description.isBlank()) {
            graphics.drawCenteredString(this.font, clip(this.font, AdmGuiKit.amp(DialogueTextLocalizer.localize(description)), w - 24), centerX, y + 72, styleColor(this.style.mutedTextColor(), COLOR_MUTED));
        }
        drawTransactionStrip(graphics, offer, x + 10, y + 86, w - 20);
        drawBottomControls(graphics, mouseX, mouseY, offer);
    }

    private void drawTransactionStrip(GuiGraphics graphics, NpcTradeDefinition.Offer offer, int x, int y, int w) {
        AdmGuiKit.fillRounded(graphics, x, y, w, 34, COLOR_STRIP);
        int columns = offer.cost().size() + offer.result().size();
        int colW = Math.max(60, (w - 20) / Math.max(1, columns));
        int cursor = x + 8;
        for (NpcTradeDefinition.TradeItem item : offer.cost()) {
            int needed = item.multipliedCount(this.quantity);
            int have = have(item);
            String line = needed + "× " + displayName(item);
            int lineColor = have >= needed ? styleColor(this.style.priceColor(), COLOR_GOLD) : COLOR_RED;
            graphics.drawString(this.font, clip(this.font, line, colW - 6), cursor, y + 7, lineColor, false);
            Component haveLine = Component.translatable("message.adm.npc.trade.gui.have", have);
            graphics.drawString(this.font, haveLine, cursor, y + 19, have >= needed ? COLOR_GREEN : COLOR_RED, false);
            cursor += colW;
        }
        AdmGuiKit.drawArrow(graphics, cursor + 1, y + 16, this.accent);
        cursor += 12;
        for (NpcTradeDefinition.TradeItem item : offer.result()) {
            String line = item.multipliedCount(this.quantity) + "× " + displayName(item);
            graphics.drawString(this.font, clip(this.font, line, colW - 6), cursor, y + 13, styleColor(this.style.rewardColor(), COLOR_GREEN), false);
            cursor += colW;
        }
    }

    private void drawBottomControls(GuiGraphics graphics, int mouseX, int mouseY, NpcTradeDefinition.Offer offer) {
        int y = controlsY();
        int x = detailsX() + 10;
        for (int i = 0; i < QUANTITY_PRESETS.length; i++) {
            int preset = QUANTITY_PRESETS[i];
            int pillX = presetX(i);
            boolean active = this.quantity == preset;
            AdmGuiKit.fillRounded(graphics, pillX, y, presetW(), 18, active ? COLOR_BUY : COLOR_CARD);
            AdmGuiKit.outlineRounded(graphics, pillX, y, presetW(), 18, active ? accent : COLOR_CARD_BORDER);
            graphics.drawCenteredString(this.font, "×" + preset, pillX + presetW() / 2, y + 5, active ? COLOR_TEXT : COLOR_MUTED);
        }
        int buyX = buyX();
        int buyW = detailsX() + detailsW() - 10 - buyX;
        boolean ready = !offer.locked() && canAfford(offer, this.quantity);
        boolean hovered = isInside(mouseX, mouseY, buyX, y, buyX + buyW, y + 18);
        AdmGuiKit.fillRounded(graphics, buyX, y, buyW, 18, ready ? (hovered ? COLOR_BUY_HOVER : COLOR_BUY) : COLOR_CARD_BORDER);
        AdmGuiKit.outlineRounded(graphics, buyX, y, buyW, 18, ready ? accent : COLOR_CARD_BORDER);
        Component label = buyLabel(offer, ready);
        graphics.drawCenteredString(this.font, Component.literal(clip(this.font, label.getString(), buyW - 8)), buyX + buyW / 2, y + 5, ready ? COLOR_TEXT : COLOR_MUTED);
    }

    private Component buyLabel(NpcTradeDefinition.Offer offer, boolean ready) {
        if (ready) {
            return Component.translatable("message.adm.npc.trade.gui.buy", this.quantity);
        }
        if (offer.locked()) {
            return Component.translatable("message.adm.npc.trade.gui.locked");
        }
        for (NpcTradeDefinition.TradeItem item : offer.cost()) {
            int needed = item.multipliedCount(this.quantity);
            if (have(item) < needed) {
                return Component.translatable("message.adm.npc.trade.gui.need", needed, displayName(item));
            }
        }
        return Component.translatable("message.adm.npc.trade.gui.missing_items");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInside(mouseX, mouseY, closeX(), closeY(), closeX() + 14, closeY() + 14)) {
            this.onClose();
            return true;
        }
        if (this.definition == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (isInside(mouseX, mouseY, listX(), listY(), listX() + listW(), listY() + visibleCards() * (CARD_HEIGHT + CARD_GAP))) {
            int row = ((int) mouseY - listY()) / (CARD_HEIGHT + CARD_GAP);
            int index = this.offerOffset + row;
            if (row >= 0 && row < visibleCards() && index < this.definition.offers().size()) {
                this.selectedIndex = index;
                this.quantity = 1;
                return true;
            }
        }
        int y = controlsY();
        for (int i = 0; i < QUANTITY_PRESETS.length; i++) {
            if (isInside(mouseX, mouseY, presetX(i), y, presetX(i) + presetW(), y + 18)) {
                this.quantity = QUANTITY_PRESETS[i];
                return true;
            }
        }
        NpcTradeDefinition.Offer offer = selectedOffer();
        if (offer != null && isInside(mouseX, mouseY, buyX(), y, detailsX() + detailsW() - 10, y + 18)
                && !offer.locked() && canAfford(offer, this.quantity)) {
            PacketDistributor.sendToServer(new NpcTradeActionPacket(this.npcUuid, this.tradeFile, offer.id(), offer.index(), this.quantity));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.definition == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (isInside(mouseX, mouseY, listX(), listY(), listX() + listW(), listY() + visibleCards() * (CARD_HEIGHT + CARD_GAP))) {
            int maxOffset = Math.max(0, this.definition.offers().size() - visibleCards());
            this.offerOffset = Mth.clamp(this.offerOffset + (scrollY < 0 ? 1 : -1), 0, maxOffset);
            return true;
        }
        if (isInside(mouseX, mouseY, detailsX(), controlsY() - 4, detailsX() + detailsW(), controlsY() + 22)) {
            this.quantity = Mth.clamp(this.quantity + (scrollY > 0 ? 1 : -1), 1, 64);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int styleColor(String raw, int fallback) {
        return AdmGuiKit.parseColor(raw, fallback);
    }

    private int listX() {
        return this.panelX + 10;
    }

    private int listY() {
        return this.panelY + 42;
    }

    private int listW() {
        return 200;
    }

    private int visibleCards() {
        return Math.max(1, (this.panelH - 52 + CARD_GAP) / (CARD_HEIGHT + CARD_GAP));
    }

    private int detailsX() {
        return listX() + listW() + 12;
    }

    private int detailsW() {
        return this.panelX + this.panelW - detailsX() - 10;
    }

    private int detailsH() {
        return this.panelH - 52;
    }

    private int controlsY() {
        return listY() + detailsH() - 28;
    }

    private int presetW() {
        return 30;
    }

    private int presetX(int index) {
        return detailsX() + 10 + index * (presetW() + 5);
    }

    private int buyX() {
        return presetX(QUANTITY_PRESETS.length - 1) + presetW() + 8;
    }

    private int closeX() {
        return this.panelX + this.panelW - 22;
    }

    private int closeY() {
        return this.panelY + 10;
    }

    private NpcTradeDefinition.Offer selectedOffer() {
        if (this.definition == null || this.selectedIndex < 0 || this.selectedIndex >= this.definition.offers().size()) {
            return null;
        }
        return this.definition.offers().get(this.selectedIndex);
    }

    private boolean canAfford(NpcTradeDefinition.Offer offer, int amount) {
        return this.minecraft != null && NpcTradeItems.canAfford(this.minecraft.player, offer, amount);
    }

    private int have(NpcTradeDefinition.TradeItem item) {
        if (this.minecraft == null) {
            return 0;
        }
        return NpcTradeItems.countInventory(this.minecraft.player, NpcTradeItems.resolveItem(item.item()));
    }
}
