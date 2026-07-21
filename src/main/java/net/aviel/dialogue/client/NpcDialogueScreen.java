package net.aviel.dialogue.client;

import com.google.gson.JsonSyntaxException;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.network.NpcDialogueAnimationPacket;
import net.aviel.dialogue.network.NpcDialogueChoicePacket;
import net.aviel.dialogue.npc.NpcDialogueService;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class NpcDialogueScreen extends Screen {
    private static final int PANEL_MARGIN = 24;
    private static final int PANEL_MAX_WIDTH = 820;
    private static final int PANEL_MIN_WIDTH = 460;
    private static final int CHOICE_PANEL_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 18;
    private static final int CHOICE_GAP = 5;
    private static final int MAX_VISIBLE_CHOICES = 4;
    private static final int HEADER_HEIGHT = 24;
    private static final int ANIMATION_MS = 180;
    private static final int INDICATOR_COLUMN = 14;
    private static final int COLOR_MUTED = 0xFF9B8BA8;
    private static final int COLOR_INACTIVE = 0x553A2A4E;
    private static final int COLOR_ERROR = 0xFFFF9D8F;
    private static final char FORMAT_PREFIX = '\u00A7';

    private final UUID npcUuid;
    private final String npcName;
    private final NpcDialogueDefinition definition;
    private final String parseError;
    private final NpcDialogueDefinition.DialogueStyle style;
    private final Deque<String> history = new ArrayDeque<>();
    private final List<Button> choiceButtons = new ArrayList<>();

    private String currentNodeId;
    private int choiceOffset = 0;
    private int highlightedChoice = 0;
    private long animationStartMs = System.currentTimeMillis();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int textLinesVisible;
    private int visibleChars = 0;
    private int fullTextLength = 0;
    private TypewriterPlan typewriterPlan = TypewriterPlan.EMPTY;
    private int typewriterSpeed = NpcDialogueDefinition.DEFAULT_TEXT_SPEED;
    private int typewriterPauseTicks = 0;
    private int nextControlIndex = 0;
    private String fullSoundPlayedForNodeId = "";

    public NpcDialogueScreen(UUID npcUuid, String npcName, String dialogueFile, String startNode, String rawJson) {
        super(Component.translatable("message.adm.npc.dialogue.gui.title"));
        this.npcUuid = npcUuid;
        this.npcName = npcName == null || npcName.isBlank() ? "NPC" : npcName;

        NpcDialogueDefinition parsed = null;
        String error = "";
        try {
            parsed = NpcDialogueDefinition.fromJson(rawJson == null ? "" : rawJson);
        } catch (JsonSyntaxException | IllegalStateException ex) {
            error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        this.definition = parsed;
        this.parseError = error;
        this.style = parsed == null ? NpcDialogueDefinition.DialogueStyle.DEFAULT : parsed.style();
        this.currentNodeId = parsed == null ? "" : (!startNode.isBlank() && parsed.node(startNode) != null ? startNode : parsed.startNode());
        resetTypewriter();
    }

    @Override
    protected void init() {
        updateLayout();
        rebuildChoices();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float progress = animationProgress();
        boolean animating = progress < 1.0F;
        graphics.pose().pushPose();
        if (animating) {
            applyIntroTransform(graphics, progress);
        }
        renderDialoguePanel(graphics);
        // Hover states are suppressed mid-animation because the panel is still moving under the cursor.
        super.render(graphics, animating ? -1 : mouseX, animating ? -1 : mouseY, partialTick);
        graphics.pose().popPose();
    }

    private void applyIntroTransform(GuiGraphics graphics, float progress) {
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        float scale = 0.97F + 0.03F * eased;
        float centerX = this.panelX + this.panelW / 2.0F;
        float bottom = this.panelY + this.panelH;
        graphics.pose().translate(centerX, bottom, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-centerX, -bottom, 0.0F);
        graphics.pose().translate(0.0F, (1.0F - eased) * 16.0F, 0.0F);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void tick() {
        if (this.visibleChars < this.fullTextLength) {
            int previousChars = this.visibleChars;
            if (this.typewriterPauseTicks > 0) {
                this.typewriterPauseTicks--;
                return;
            }
            if (applyTypewriterControlsAtCurrentPosition()) {
                refreshChoiceButtonState();
                return;
            }
            int target = this.typewriterSpeed <= 0 ? this.fullTextLength : this.visibleChars + this.typewriterSpeed;
            int nextControlPosition = nextTypewriterControlPosition();
            if (nextControlPosition >= 0) {
                target = Math.min(target, nextControlPosition);
            }
            this.visibleChars = Math.min(this.fullTextLength, target);
            playRepeatingTextSounds(previousChars, this.visibleChars);
            refreshChoiceButtonState();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInside(mouseX, mouseY, this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        NpcDialogueDefinition.Node node = currentNode();
        if (node == null || node.choices().size() <= MAX_VISIBLE_CHOICES) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int maxOffset = Math.max(0, node.choices().size() - MAX_VISIBLE_CHOICES);
        this.choiceOffset = Mth.clamp(this.choiceOffset + (scrollY < 0 ? 1 : -1), 0, maxOffset);
        this.highlightedChoice = Mth.clamp(this.highlightedChoice, this.choiceOffset, this.choiceOffset + MAX_VISIBLE_CHOICES - 1);
        rebuildChoices();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.visibleChars < this.fullTextLength
                && isInside(mouseX, mouseY, this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH)) {
            this.visibleChars = this.fullTextLength;
            refreshChoiceButtonState();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleDialogueKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Number keys pick a visible row, arrows move through every choice, Enter confirms. */
    private boolean handleDialogueKey(int keyCode) {
        if (!isNavigationKey(keyCode)) {
            return false;
        }
        if (this.visibleChars < this.fullTextLength) {
            this.visibleChars = this.fullTextLength;
            refreshChoiceButtonState();
            return true;
        }
        int total = choiceCount();
        if (total <= 0) {
            return false;
        }
        int row = numberKeyRow(keyCode);
        if (row >= 0) {
            int index = this.choiceOffset + row;
            return index < total && activateChoice(index);
        }
        if (keyCode == 265 || keyCode == 264) {
            this.highlightedChoice = Math.floorMod(this.highlightedChoice + (keyCode == 265 ? -1 : 1), total);
            scrollToHighlighted(total);
            rebuildChoices();
            return true;
        }
        return activateChoice(this.highlightedChoice);
    }

    private static boolean isNavigationKey(int keyCode) {
        return numberKeyRow(keyCode) >= 0 || keyCode == 264 || keyCode == 265 || keyCode == 257 || keyCode == 335 || keyCode == 32;
    }

    /** Maps the 1-9 rows (main row and numpad) to a zero-based visible row, or -1. */
    private static int numberKeyRow(int keyCode) {
        if (keyCode >= 49 && keyCode <= 57) {
            return keyCode - 49;
        }
        if (keyCode >= 321 && keyCode <= 329) {
            return keyCode - 321;
        }
        return -1;
    }

    private void scrollToHighlighted(int total) {
        int maxOffset = Math.max(0, total - MAX_VISIBLE_CHOICES);
        if (this.highlightedChoice < this.choiceOffset) {
            this.choiceOffset = this.highlightedChoice;
        } else if (this.highlightedChoice >= this.choiceOffset + MAX_VISIBLE_CHOICES) {
            this.choiceOffset = this.highlightedChoice - MAX_VISIBLE_CHOICES + 1;
        }
        this.choiceOffset = Mth.clamp(this.choiceOffset, 0, maxOffset);
    }

    private boolean activateChoice(int index) {
        NpcDialogueDefinition.Node node = currentNode();
        if (node == null || index < 0 || index >= node.choices().size()) {
            onClose();
            return true;
        }
        selectChoice(node.choices().get(index), index);
        return true;
    }

    private int choiceCount() {
        NpcDialogueDefinition.Node node = currentNode();
        return node == null ? 0 : node.choices().size();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateLayout() {
        this.panelW = Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, this.width - PANEL_MARGIN * 2));
        int textW = textWidth();
        String fullText = fullNodeText(currentNode());
        this.typewriterPlan = TypewriterPlan.parse(fullText);
        this.fullTextLength = this.typewriterPlan.visibleLength();
        skipPastConsumedControls();
        playFullSoundForCurrentNode();
        int lineCount = wrapDialogueText(fullText, textW).size();
        this.textLinesVisible = Math.max(1, Math.min(6, lineCount));

        int choiceCount = currentNode() == null ? 1 : Math.max(1, Math.min(MAX_VISIBLE_CHOICES, currentNode().choices().size()));
        int textHeight = this.textLinesVisible * 11;
        int choiceHeight = choiceCount * BUTTON_HEIGHT + Math.max(0, choiceCount - 1) * CHOICE_GAP;
        this.panelH = Math.max(104, Math.min(196, HEADER_HEIGHT + Math.max(textHeight, choiceHeight) + 22));
        this.panelX = this.width / 2 - this.panelW / 2;
        this.panelY = this.height - this.panelH - PANEL_MARGIN;
    }

    private void renderDialoguePanel(GuiGraphics graphics) {
        int accent = parseColor(this.style.dividerColor(), 0xFFA986EC);
        AdmGuiKit.fillRounded(graphics, this.panelX - 3, this.panelY - 3, this.panelW + 6, this.panelH + 6, parseColor(this.style.outerColor(), 0x66000000));
        AdmGuiKit.fillRounded(graphics, this.panelX, this.panelY, this.panelW, this.panelH, parseColor(this.style.textBackground(), 0xF60F0817));
        AdmGuiKit.outlineRounded(graphics, this.panelX, this.panelY, this.panelW, this.panelH, 0xFF574A75);

        int choiceX = choicePanelX();
        int cardW = this.panelX + this.panelW - choiceX - 6;
        int cardY = this.panelY + HEADER_HEIGHT + 2;
        int cardH = this.panelH - HEADER_HEIGHT - 8;
        AdmGuiKit.fillRounded(graphics, choiceX, cardY, cardW, cardH, parseColor(this.style.choicesBackground(), 0xF6160D22));
        renderChoiceScrollIndicators(graphics, choiceX, cardY, cardW, cardH, accent);

        if (this.definition == null) {
            renderHeader(graphics, Component.translatable("message.adm.npc.dialogue.gui.invalid").getString(), COLOR_ERROR, accent);
            graphics.drawString(this.font, Component.literal(clip(this.font, this.parseError, textWidth())), this.panelX + 12, this.panelY + HEADER_HEIGHT + 8, COLOR_ERROR, false);
            return;
        }

        NpcDialogueDefinition.Node node = currentNode();
        if (node == null) {
            renderHeader(graphics, this.npcName, COLOR_ERROR, accent);
            graphics.drawString(this.font, Component.translatable("message.adm.npc.dialogue.gui.missing_node", this.currentNodeId), this.panelX + 12, this.panelY + HEADER_HEIGHT + 8, COLOR_ERROR, false);
            return;
        }

        String title = !this.definition.title().isBlank() ? DialogueTextLocalizer.localize(this.definition.title()) : this.npcName;
        String speaker = !node.speaker().isBlank() ? DialogueTextLocalizer.localize(node.speaker()) : title;
        renderHeader(graphics, speaker, parseColor(node.speakerColor(), 0xFFFFD36B), accent);
        renderNodeText(graphics, node);
        renderTypingIndicator(graphics, accent);
    }

    /** Title strip: speaker on the left, NPC name on the right, accent rule underneath. */
    private void renderHeader(GuiGraphics graphics, String speaker, int speakerColor, int accent) {
        graphics.fill(this.panelX + 1, this.panelY + 2, this.panelX + this.panelW - 1, this.panelY + HEADER_HEIGHT, parseColor(this.style.choicesBackground(), 0xFF1A0F28));
        graphics.fill(this.panelX + 1, this.panelY + HEADER_HEIGHT, this.panelX + this.panelW - 1, this.panelY + HEADER_HEIGHT + 1, accent);
        graphics.drawString(this.font, formattedComponent(speaker).withStyle(ChatFormatting.BOLD), this.panelX + 12, this.panelY + 8, speakerColor, false);
        String npc = AdmGuiKit.amp(DialogueTextLocalizer.localize(this.npcName));
        int npcWidth = this.font.width(npc);
        graphics.drawString(this.font, npc, this.panelX + this.panelW - 12 - npcWidth, this.panelY + 8, COLOR_MUTED, false);
    }

    private void renderNodeText(GuiGraphics graphics, NpcDialogueDefinition.Node node) {
        int lineY = this.panelY + HEADER_HEIGHT + 9;
        int maxY = lineY + this.textLinesVisible * 11;
        int textColor = parseColor(node.textColor(), 0xFFF2E7FF);
        int remainingChars = this.visibleChars;
        for (WrappedDialogueLine line : wrapDialogueText(fullNodeText(node), textWidth())) {
            if (lineY >= maxY) {
                break;
            }
            if (remainingChars <= 0 && line.visibleLength() > 0) {
                break;
            }
            String visibleLine = visibleFormattedText(line.text(), remainingChars);
            if (!visibleLine.isBlank()) {
                graphics.drawString(this.font, Component.literal(visibleLine), this.panelX + 12, lineY, textColor, false);
            }
            remainingChars = Math.max(0, remainingChars - line.visibleLength());
            lineY += 11;
        }
    }

    /** Blinking caret while the typewriter is still running. */
    private void renderTypingIndicator(GuiGraphics graphics, int accent) {
        if (this.visibleChars < this.fullTextLength && (System.currentTimeMillis() / 400L) % 2L == 0L) {
            AdmGuiKit.fillRounded(graphics, this.panelX + 12, this.panelY + this.panelH - 12, 4, 4, accent);
        }
    }

    /** Chevrons and a thumb showing that the choice list scrolls beyond the visible rows. */
    private void renderChoiceScrollIndicators(GuiGraphics graphics, int cardX, int cardY, int cardW, int cardH, int accent) {
        int total = choiceCount();
        if (total <= MAX_VISIBLE_CHOICES) {
            return;
        }
        int x = cardX + cardW - 11;
        boolean canScrollUp = this.choiceOffset > 0;
        boolean canScrollDown = this.choiceOffset + MAX_VISIBLE_CHOICES < total;
        drawChevron(graphics, x, cardY + 5, true, canScrollUp ? accent : COLOR_INACTIVE);
        drawChevron(graphics, x, cardY + cardH - 8, false, canScrollDown ? accent : COLOR_INACTIVE);

        int trackY = cardY + 14;
        int trackH = cardH - 26;
        graphics.fill(x + 2, trackY, x + 3, trackY + trackH, 0x40000000);
        int thumbH = Math.max(8, trackH * MAX_VISIBLE_CHOICES / total);
        int travel = Math.max(1, total - MAX_VISIBLE_CHOICES);
        int thumbY = trackY + Math.round((float) this.choiceOffset / travel * (trackH - thumbH));
        graphics.fill(x + 2, thumbY, x + 3, thumbY + thumbH, accent);
    }

    private static void drawChevron(GuiGraphics graphics, int x, int y, boolean pointingUp, int color) {
        for (int row = 0; row < 3; row++) {
            int width = 5 - row * 2;
            int offset = row;
            int lineY = pointingUp ? y + row : y + 2 - row;
            graphics.fill(x + offset, lineY, x + offset + width, lineY + 1, color);
        }
    }

    private void rebuildChoices() {
        for (Button button : this.choiceButtons) {
            this.removeWidget(button);
        }
        this.choiceButtons.clear();

        int choiceX = choicePanelX() + 8;
        int choiceY = this.panelY + HEADER_HEIGHT + 8;
        int reserved = choiceCount() > MAX_VISIBLE_CHOICES ? INDICATOR_COLUMN : 0;
        int choiceW = this.panelX + this.panelW - choiceX - 12 - reserved;

        if (this.definition == null || currentNode() == null) {
            addChoiceButton(choiceX, choiceY, choiceW, 0, Component.translatable("message.adm.npc.dialogue.gui.close"), btn -> onClose());
            return;
        }

        NpcDialogueDefinition.Node node = currentNode();
        List<NpcDialogueDefinition.Choice> choices = node.choices();
        if (choices.isEmpty()) {
            addChoiceButton(choiceX, choiceY, choiceW, 0, Component.translatable("message.adm.npc.dialogue.gui.close"), btn -> onClose());
            return;
        }

        int maxOffset = Math.max(0, choices.size() - MAX_VISIBLE_CHOICES);
        this.choiceOffset = Mth.clamp(this.choiceOffset, 0, maxOffset);
        this.highlightedChoice = Mth.clamp(this.highlightedChoice, 0, choices.size() - 1);
        int visible = Math.min(MAX_VISIBLE_CHOICES, choices.size() - this.choiceOffset);
        Button highlighted = null;
        for (int i = 0; i < visible; i++) {
            int index = this.choiceOffset + i;
            NpcDialogueDefinition.Choice choice = choices.get(index);
            Component label = Component.literal(clip(this.font, displayFormattedText(DialogueTextLocalizer.localize(choice.text())), choiceW - 26));
            int y = choiceY + i * (BUTTON_HEIGHT + CHOICE_GAP);
            Button button = addChoiceButton(choiceX, y, choiceW, i, label, btn -> selectChoice(choice, index));
            if (index == this.highlightedChoice) {
                highlighted = button;
            }
        }
        this.setFocused(highlighted);
        refreshChoiceButtonState();
    }

    private Button addChoiceButton(int x, int y, int width, int index, Component label, Button.OnPress onPress) {
        ChoiceButtonSpec spec = new ChoiceButtonSpec(x, y, width, label, this.style, index);
        Button button = this.addRenderableWidget(new DialogueChoiceButton(spec, onPress));
        this.choiceButtons.add(button);
        return button;
    }

    private void selectChoice(NpcDialogueDefinition.Choice choice, int index) {
        if (this.visibleChars < this.fullTextLength) {
            this.visibleChars = this.fullTextLength;
            refreshChoiceButtonState();
            return;
        }
        if (choice == null) {
            onClose();
            return;
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new NpcDialogueChoicePacket(this.npcUuid, this.currentNodeId, choice.serverIndex()));

        if ("back".equals(choice.action()) && !this.history.isEmpty()) {
            this.currentNodeId = this.history.pop();
            this.choiceOffset = 0;
            resetTypewriter();
            updateLayout();
            rebuildChoices();
            return;
        }
        if (!choice.next().isBlank() && this.definition.node(choice.next()) != null) {
            this.history.push(this.currentNodeId);
            this.currentNodeId = choice.next();
            this.choiceOffset = 0;
            resetTypewriter();
            updateLayout();
            rebuildChoices();
            return;
        }
        if (choice.close() || choice.next().isBlank()) {
            onClose();
        }
    }

    private void resetTypewriter() {
        this.visibleChars = 0;
        this.typewriterSpeed = currentTextSpeed();
        this.typewriterPauseTicks = 0;
        this.nextControlIndex = 0;
        this.fullSoundPlayedForNodeId = "";
        this.highlightedChoice = 0;
        this.animationStartMs = System.currentTimeMillis();
    }

    /** 0 while the panel starts appearing, 1 once the intro animation has finished. */
    private float animationProgress() {
        long elapsed = System.currentTimeMillis() - this.animationStartMs;
        return Mth.clamp(elapsed / (float) ANIMATION_MS, 0.0F, 1.0F);
    }

    private void refreshChoiceButtonState() {
        boolean active = this.visibleChars >= this.fullTextLength;
        for (Button button : this.choiceButtons) {
            button.active = active;
        }
    }

    private List<WrappedDialogueLine> wrapDialogueText(String text, int width) {
        List<WrappedDialogueLine> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        FormatState state = new FormatState();
        String formattedText = displayFormattedText(text) + "\n";
        StringBuilder line = new StringBuilder();
        StringBuilder word = new StringBuilder();
        int lineVisibleLength = 0;
        int wordVisibleLength = 0;

        for (int i = 0; i < formattedText.length(); i++) {
            char current = formattedText.charAt(i);
            if (current == FORMAT_PREFIX && i + 1 < formattedText.length() && isFormatCode(formattedText.charAt(i + 1))) {
                char code = Character.toLowerCase(formattedText.charAt(i + 1));
                word.append(FORMAT_PREFIX).append(code);
                state.apply(code);
                i++;
                continue;
            }

            if (current == ' ' || current == '\n') {
                if (!word.isEmpty()) {
                    if (lineVisibleLength > 0 && this.font.width(line.toString()) + this.font.width(word.toString()) > width) {
                        addWrappedLine(lines, line, lineVisibleLength);
                        line = new StringBuilder(state.prefix());
                        lineVisibleLength = 0;
                    }
                    line.append(word);
                    lineVisibleLength += wordVisibleLength;
                    word = new StringBuilder(state.prefix());
                    wordVisibleLength = 0;
                }

                if (current == '\n') {
                    if (lineVisibleLength > 0 || !line.isEmpty()) {
                        addWrappedLine(lines, line, lineVisibleLength);
                    } else {
                        lines.add(new WrappedDialogueLine("", 0));
                    }
                    line = new StringBuilder(state.prefix());
                    word = new StringBuilder(state.prefix());
                    lineVisibleLength = 0;
                    wordVisibleLength = 0;
                    continue;
                }

                if (lineVisibleLength > 0) {
                    if (this.font.width(line.toString()) + this.font.width(" ") > width) {
                        addWrappedLine(lines, line, lineVisibleLength);
                        line = new StringBuilder(state.prefix());
                        lineVisibleLength = 0;
                    } else {
                        line.append(' ');
                        lineVisibleLength++;
                    }
                }
                continue;
            }

            word.append(current);
            wordVisibleLength++;
        }
        return lines;
    }

    private static void addWrappedLine(List<WrappedDialogueLine> lines, StringBuilder line, int visibleLength) {
        String value = trimTrailingSpaces(line.toString());
        lines.add(new WrappedDialogueLine(value, Math.max(0, visibleLength)));
    }

    private static String trimTrailingSpaces(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    private String fullNodeText(NpcDialogueDefinition.Node node) {
        if (node == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String paragraph : node.text()) {
            if (paragraph != null && !paragraph.isBlank()) {
                parts.add(DialogueTextLocalizer.localize(paragraph));
            }
        }
        return String.join("\n", parts);
    }

    private int currentTextSpeed() {
        NpcDialogueDefinition.Node node = currentNode();
        return node == null ? NpcDialogueDefinition.DEFAULT_TEXT_SPEED : node.textSpeed();
    }

    private MutableComponent formattedComponent(String text) {
        return Component.literal(displayFormattedText(text));
    }

    private static String displayFormattedText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            ParsedControl parsedControl = readControlTag(text, i, 0);
            if (parsedControl != null) {
                i = parsedControl.endIndex();
                continue;
            }
            char current = text.charAt(i);
            if ((current == '&' || current == FORMAT_PREFIX) && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (isFormatCode(code)) {
                    result.append(FORMAT_PREFIX).append(Character.toLowerCase(code));
                    i++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private static String visibleFormattedText(String text, int visiblePlainChars) {
        if (text == null || text.isEmpty() || visiblePlainChars <= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            ParsedControl parsedControl = readControlTag(text, i, visible);
            if (parsedControl != null) {
                i = parsedControl.endIndex();
                continue;
            }
            char current = text.charAt(i);
            if ((current == '&' || current == FORMAT_PREFIX) && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (isFormatCode(code)) {
                    result.append(FORMAT_PREFIX).append(Character.toLowerCase(code));
                    i++;
                    continue;
                }
            }
            if (visible >= visiblePlainChars) {
                break;
            }
            result.append(current);
            visible++;
        }
        return result.toString();
    }

    private static boolean isFormatCode(char code) {
        return "0123456789abcdefklmnor".indexOf(Character.toLowerCase(code)) >= 0;
    }

    private boolean applyTypewriterControlsAtCurrentPosition() {
        boolean paused = false;
        List<ControlTag> controls = this.typewriterPlan.controls();
        while (this.nextControlIndex < controls.size()) {
            ControlTag control = controls.get(this.nextControlIndex);
            if (control.position() > this.visibleChars) {
                break;
            }
            this.nextControlIndex++;
            if (control.kind() == ControlKind.SPEED) {
                this.typewriterSpeed = control.value() < 0 ? currentTextSpeed() : control.value();
            } else if (control.kind() == ControlKind.PAUSE && control.position() == this.visibleChars) {
                this.typewriterPauseTicks = Math.max(this.typewriterPauseTicks, control.value());
                paused = this.typewriterPauseTicks > 0;
            } else if (control.kind() == ControlKind.SOUND && control.position() == this.visibleChars) {
                playDialogueSound(control.payload(), currentSound().volume(), currentSound().pitch());
            } else if (control.kind() == ControlKind.ANIMATION && control.position() == this.visibleChars) {
                playNpcDialogueAnimation(control.payload(), control.value() > 0);
            }
        }
        return paused;
    }

    private int nextTypewriterControlPosition() {
        List<ControlTag> controls = this.typewriterPlan.controls();
        if (this.nextControlIndex >= controls.size()) {
            return -1;
        }
        int position = controls.get(this.nextControlIndex).position();
        return position > this.visibleChars ? position : -1;
    }

    private void skipPastConsumedControls() {
        List<ControlTag> controls = this.typewriterPlan.controls();
        this.nextControlIndex = Mth.clamp(this.nextControlIndex, 0, controls.size());
        while (this.nextControlIndex < controls.size() && controls.get(this.nextControlIndex).position() < this.visibleChars) {
            this.nextControlIndex++;
        }
    }

    private static ParsedControl readControlTag(String text, int startIndex, int visiblePosition) {
        if (text == null || startIndex < 0 || startIndex >= text.length() || text.charAt(startIndex) != '<') {
            return null;
        }
        int endIndex = text.indexOf('>', startIndex + 1);
        if (endIndex < 0 || endIndex - startIndex > 180) {
            return null;
        }
        String raw = text.substring(startIndex + 1, endIndex).trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) {
            return null;
        }
        ControlTag control = parseControlTag(raw, visiblePosition);
        return control == null ? null : new ParsedControl(control, endIndex);
    }

    private static ControlTag parseControlTag(String raw, int visiblePosition) {
        if ("slow".equals(raw)) {
            return new ControlTag(visiblePosition, ControlKind.SPEED, 1, "");
        }
        if ("normal".equals(raw) || "/speed".equals(raw) || "speed:normal".equals(raw) || "speed:default".equals(raw)) {
            return new ControlTag(visiblePosition, ControlKind.SPEED, -1, "");
        }
        if ("fast".equals(raw)) {
            return new ControlTag(visiblePosition, ControlKind.SPEED, 4, "");
        }
        if ("instant".equals(raw)) {
            return new ControlTag(visiblePosition, ControlKind.SPEED, 0, "");
        }
        if (raw.startsWith("pause") || raw.startsWith("wait") || raw.startsWith("p:") || raw.startsWith("p=")) {
            return new ControlTag(visiblePosition, ControlKind.PAUSE, parseTaggedValue(raw, 10, true), "");
        }
        if (raw.startsWith("speed") || raw.startsWith("spd") || raw.startsWith("s:") || raw.startsWith("s=")) {
            return new ControlTag(visiblePosition, ControlKind.SPEED, parseTaggedValue(raw, -1, false), "");
        }
        if (raw.startsWith("sound:") || raw.startsWith("sound=")) {
            int separator = raw.indexOf(':');
            if (separator < 0) {
                separator = raw.indexOf('=');
            }
            String sound = separator >= 0 && separator + 1 < raw.length() ? raw.substring(separator + 1).trim() : "";
            return sound.isBlank() ? null : new ControlTag(visiblePosition, ControlKind.SOUND, 0, sound);
        }
        if (raw.startsWith("anim:") || raw.startsWith("anim=")
                || raw.startsWith("animation:") || raw.startsWith("animation=")
                || raw.startsWith("emote:") || raw.startsWith("emote=")) {
            AnimationSpec spec = parseAnimationSpec(raw);
            return spec.name().isBlank() ? null : new ControlTag(visiblePosition, ControlKind.ANIMATION, spec.repeat() ? 1 : 0, spec.name());
        }
        return null;
    }

    private static AnimationSpec parseAnimationSpec(String raw) {
        int separator = raw.indexOf(':');
        if (separator < 0) {
            separator = raw.indexOf('=');
        }
        if (separator < 0 || separator + 1 >= raw.length()) {
            return new AnimationSpec("", false);
        }
        String spec = raw.substring(separator + 1).trim();
        boolean repeat = false;
        String[] repeatSuffixes = {":loop", ":repeat", ":true", "|loop", "|repeat", "|true", ",loop", ",repeat", ",true"};
        for (String suffix : repeatSuffixes) {
            if (spec.endsWith(suffix)) {
                repeat = true;
                spec = spec.substring(0, spec.length() - suffix.length()).trim();
                break;
            }
        }
        return new AnimationSpec(spec, repeat);
    }

    private static int parseTaggedValue(String raw, int fallback, boolean pause) {
        int separator = raw.indexOf(':');
        if (separator < 0) {
            separator = raw.indexOf('=');
        }
        if (separator < 0 || separator + 1 >= raw.length()) {
            return fallback;
        }
        String value = raw.substring(separator + 1).trim();
        return pause ? parsePauseValue(value, fallback) : parseSpeedValue(value, fallback);
    }

    private static int parsePauseValue(String value, int fallback) {
        return switch (value) {
            case "short", "small", "tiny" -> 8;
            case "medium", "normal" -> 16;
            case "long" -> 30;
            default -> {
                try {
                    if (value.endsWith("s")) {
                        double seconds = Double.parseDouble(value.substring(0, value.length() - 1));
                        yield Mth.clamp((int) Math.round(seconds * 20.0D), 0, 200);
                    }
                    yield Mth.clamp(Integer.parseInt(value), 0, 200);
                } catch (NumberFormatException ignored) {
                    yield fallback;
                }
            }
        };
    }

    private static int parseSpeedValue(String value, int fallback) {
        return switch (value) {
            case "slow" -> 1;
            case "normal", "default", "reset" -> -1;
            case "fast" -> 4;
            case "instant" -> 0;
            default -> {
                try {
                    yield Mth.clamp(Integer.parseInt(value), 0, 12);
                } catch (NumberFormatException ignored) {
                    yield fallback;
                }
            }
        };
    }

    private void playRepeatingTextSounds(int previousChars, int newChars) {
        NpcDialogueDefinition.DialogueSound sound = currentSound();
        if (!"repeating".equals(sound.mode()) || sound.textSound().isBlank() || newChars <= previousChars) {
            return;
        }
        String text = fullNodeText(currentNode());
        int played = 0;
        for (int position = previousChars + 1; position <= newChars && played < 8; position++) {
            if ((position - 1) % sound.letterInterval() != 0) {
                continue;
            }
            char visible = visibleCharAt(text, position);
            if (Character.isWhitespace(visible) || visible == 0) {
                continue;
            }
            playDialogueSound(sound.textSound(), sound.volume(), sound.pitch());
            played++;
        }
    }

    private void playFullSoundForCurrentNode() {
        NpcDialogueDefinition.Node node = currentNode();
        if (node == null || node.id().equals(this.fullSoundPlayedForNodeId)) {
            return;
        }
        NpcDialogueDefinition.DialogueSound sound = node.sound();
        if ("full".equals(sound.mode()) && !sound.fullSound().isBlank()) {
            playDialogueSound(sound.fullSound(), sound.volume(), sound.pitch());
        }
        this.fullSoundPlayedForNodeId = node.id();
    }

    private NpcDialogueDefinition.DialogueSound currentSound() {
        NpcDialogueDefinition.Node node = currentNode();
        return node == null ? NpcDialogueDefinition.DialogueSound.DEFAULT : node.sound();
    }

    private static char visibleCharAt(String text, int targetPosition) {
        if (text == null || targetPosition <= 0) {
            return 0;
        }
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            ParsedControl parsedControl = readControlTag(text, i, visible);
            if (parsedControl != null) {
                i = parsedControl.endIndex();
                continue;
            }
            char current = text.charAt(i);
            if ((current == '&' || current == FORMAT_PREFIX) && i + 1 < text.length() && isFormatCode(text.charAt(i + 1))) {
                i++;
                continue;
            }
            if (current == '\n') {
                continue;
            }
            visible++;
            if (visible == targetPosition) {
                return current;
            }
        }
        return 0;
    }

    private void playNpcDialogueAnimation(String emote, boolean repeat) {
        if (emote == null || emote.isBlank()) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new NpcDialogueAnimationPacket(this.npcUuid, emote, repeat));
    }

    private void playDialogueSound(String rawSound, float volume, float pitch) {
        String normalized = NpcDialogueService.normalizeSound(rawSound);
        if (normalized.isBlank()) {
            return;
        }
        Minecraft mc = this.minecraft == null ? Minecraft.getInstance() : this.minecraft;
        if (mc == null || mc.player == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        if (id == null) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.containsKey(id)
                ? BuiltInRegistries.SOUND_EVENT.get(id)
                : SoundEvent.createVariableRangeEvent(id);
        if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            mc.player.playSound(sound, volume, pitch);
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x")) {
            value = value.substring(2);
        }
        try {
            if (value.length() == 6) {
                return 0xFF000000 | Integer.parseInt(value, 16);
            }
            if (value.length() == 8) {
                return (int) Long.parseLong(value, 16);
            }
            return fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int choicePanelX() {
        return this.panelX + this.panelW - Math.min(CHOICE_PANEL_WIDTH, this.panelW / 3);
    }

    private int textWidth() {
        return Math.max(80, choicePanelX() - this.panelX - 20);
    }

    private NpcDialogueDefinition.Node currentNode() {
        if (this.definition == null) {
            return null;
        }
        return this.definition.node(this.currentNodeId);
    }

    private static boolean isInside(double mouseX, double mouseY, int minX, int minY, int maxX, int maxY) {
        return mouseX >= minX && mouseX < maxX && mouseY >= minY && mouseY < maxY;
    }

    private static String clip(Font font, String text, int width) {
        if (font == null || text == null || text.isBlank()) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static final class DialogueChoiceButton extends Button {
        private final NpcDialogueDefinition.DialogueStyle style;
        private final int index;

        private DialogueChoiceButton(ChoiceButtonSpec spec, OnPress onPress) {
            super(spec.x(), spec.y(), spec.width(), BUTTON_HEIGHT, spec.label(), onPress, Button.DEFAULT_NARRATION);
            this.style = spec.style() == null ? NpcDialogueDefinition.DialogueStyle.DEFAULT : spec.style();
            this.index = spec.index();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.active && this.isHoveredOrFocused();
            int fill = !this.active
                    ? parseColor(this.style.buttonDisabledColor(), 0xFF241732)
                    : hovered
                    ? parseColor(this.style.buttonHoverColor(), 0xFF3C2A55)
                    : parseColor(this.style.buttonColor(), 0xFF1B1026);
            int text = this.active
                    ? parseColor(this.style.buttonTextColor(), 0xFFF2E7FF)
                    : parseColor(this.style.buttonDisabledTextColor(), 0xFF6A5F78);
            int accent = parseColor(this.style.dividerColor(), 0xFFA986EC);

            AdmGuiKit.fillRounded(graphics, this.getX(), this.getY(), this.width, this.height, fill);
            AdmGuiKit.outlineRounded(graphics, this.getX(), this.getY(), this.width, this.height, hovered ? accent : 0xFF3A2A4E);
            if (hovered) {
                graphics.fill(this.getX() + 1, this.getY() + 3, this.getX() + 3, this.getY() + this.height - 3, accent);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.font == null) {
                return;
            }
            String number = Integer.toString(this.index + 1);
            graphics.drawString(mc.font, number, this.getX() + 7, this.getY() + (this.height - 8) / 2, this.active ? accent : text, false);
            graphics.drawString(mc.font, this.getMessage(), this.getX() + 18, this.getY() + (this.height - 8) / 2, text, false);
        }
    }

    /** Layout and styling for one choice button; height is always {@link #BUTTON_HEIGHT}. */
    private record ChoiceButtonSpec(int x, int y, int width, Component label, NpcDialogueDefinition.DialogueStyle style, int index) {
    }

    private record TypewriterPlan(int visibleLength, List<ControlTag> controls) {
        private static final TypewriterPlan EMPTY = new TypewriterPlan(0, List.of());

        private static TypewriterPlan parse(String text) {
            if (text == null || text.isEmpty()) {
                return EMPTY;
            }
            List<ControlTag> controls = new ArrayList<>();
            int visible = 0;
            for (int i = 0; i < text.length(); i++) {
                ParsedControl parsedControl = readControlTag(text, i, visible);
                if (parsedControl != null) {
                    controls.add(parsedControl.control());
                    i = parsedControl.endIndex();
                    continue;
                }
                char current = text.charAt(i);
                if ((current == '&' || current == FORMAT_PREFIX) && i + 1 < text.length() && isFormatCode(text.charAt(i + 1))) {
                    i++;
                    continue;
                }
                if (current == '\n') {
                    continue;
                }
                visible++;
            }
            return new TypewriterPlan(visible, List.copyOf(controls));
        }
    }

    private record WrappedDialogueLine(String text, int visibleLength) {
    }

    private static final class FormatState {
        private char color = 0;
        private boolean bold = false;
        private boolean italic = false;
        private boolean underline = false;
        private boolean strikethrough = false;
        private boolean obfuscated = false;

        private void apply(char code) {
            code = Character.toLowerCase(code);
            if (code == 'r') {
                reset();
                return;
            }
            if ("0123456789abcdef".indexOf(code) >= 0) {
                this.color = code;
                this.bold = false;
                this.italic = false;
                this.underline = false;
                this.strikethrough = false;
                this.obfuscated = false;
                return;
            }
            switch (code) {
                case 'l' -> this.bold = true;
                case 'o' -> this.italic = true;
                case 'n' -> this.underline = true;
                case 'm' -> this.strikethrough = true;
                case 'k' -> this.obfuscated = true;
                default -> {
                }
            }
        }

        private void reset() {
            this.color = 0;
            this.bold = false;
            this.italic = false;
            this.underline = false;
            this.strikethrough = false;
            this.obfuscated = false;
        }

        private String prefix() {
            StringBuilder builder = new StringBuilder();
            if (this.color != 0) {
                builder.append(FORMAT_PREFIX).append(this.color);
            }
            if (this.obfuscated) {
                builder.append(FORMAT_PREFIX).append('k');
            }
            if (this.bold) {
                builder.append(FORMAT_PREFIX).append('l');
            }
            if (this.strikethrough) {
                builder.append(FORMAT_PREFIX).append('m');
            }
            if (this.underline) {
                builder.append(FORMAT_PREFIX).append('n');
            }
            if (this.italic) {
                builder.append(FORMAT_PREFIX).append('o');
            }
            return builder.toString();
        }
    }

    private record ParsedControl(ControlTag control, int endIndex) {
    }

    private record AnimationSpec(String name, boolean repeat) {
    }

    private record ControlTag(int position, ControlKind kind, int value, String payload) {
    }

    private enum ControlKind {
        SPEED,
        PAUSE,
        SOUND,
        ANIMATION
    }
}
