package net.aviel.dialogue.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.NpcEquipment;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** {@code /adm_npc equip} — armor and held items on the targeted NPC. */
final class NpcEquipCommands {
    private NpcEquipCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> subtree(CommandBuildContext buildContext) {
        return Commands.literal("equip")
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("clear_all")
                        .executes(context -> clearAll(context.getSource())))
                .then(Commands.literal("clear")
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcEquipment.slotNames(), builder))
                                .executes(context -> clearSlot(context.getSource(), StringArgumentType.getString(context, "slot")))))
                .then(Commands.literal("from_me")
                        .executes(context -> copyFromPlayer(context.getSource())))
                .then(Commands.argument("slot", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcEquipment.slotNames(), builder))
                        .executes(context -> equipHeldItem(context.getSource(), StringArgumentType.getString(context, "slot")))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> equipItem(context, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> equipItem(context, IntegerArgumentType.getInteger(context, "count"))))));
    }

    private static int equipItem(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        EquipmentSlot slot = requireSlot(source, StringArgumentType.getString(context, "slot"));
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (slot == null || npc == null) {
            return 0;
        }
        ItemStack stack;
        try {
            stack = ItemArgument.getItem(context, "item").createItemStack(count, false);
        } catch (CommandSyntaxException ex) {
            source.sendFailure(Component.literal("Invalid item or count: " + ex.getMessage()));
            return 0;
        }
        npc.setItemSlot(slot, stack);
        source.sendSuccess(() -> Component.literal("Equipped " + stack.getHoverName().getString() + " to " + NpcEquipment.slotName(slot) + " of " + npc.getProfileName()), true);
        return 1;
    }

    private static int equipHeldItem(CommandSourceStack source, String slotName) {
        EquipmentSlot slot = requireSlot(source, slotName);
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (slot == null || npc == null) {
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can equip their held item. Use /adm_npc equip <slot> <item>."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to equip, or use /adm_npc equip <slot> <item>."));
            return 0;
        }
        ItemStack copy = held.copy();
        npc.setItemSlot(slot, copy);
        source.sendSuccess(() -> Component.literal("Equipped " + copy.getHoverName().getString() + " to " + NpcEquipment.slotName(slot) + " of " + npc.getProfileName()), true);
        return 1;
    }

    private static int copyFromPlayer(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can copy their own equipment."));
            return 0;
        }
        for (EquipmentSlot slot : NpcEquipment.SLOTS) {
            npc.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }
        source.sendSuccess(() -> Component.literal("Copied your equipment to " + npc.getProfileName()), true);
        return 1;
    }

    private static int clearSlot(CommandSourceStack source, String slotName) {
        EquipmentSlot slot = requireSlot(source, slotName);
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (slot == null || npc == null) {
            return 0;
        }
        npc.setItemSlot(slot, ItemStack.EMPTY);
        source.sendSuccess(() -> Component.literal("Cleared " + NpcEquipment.slotName(slot) + " of " + npc.getProfileName()), true);
        return 1;
    }

    private static int clearAll(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        NpcEquipment.clearAll(npc);
        source.sendSuccess(() -> Component.literal("Cleared all equipment of " + npc.getProfileName()), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }
        Map<String, String> summary = NpcEquipment.summary(npc);
        if (summary.isEmpty()) {
            source.sendSuccess(() -> Component.literal(npc.getProfileName() + " has no equipment."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Equipment of " + npc.getProfileName() + ":"), false);
        for (Map.Entry<String, String> entry : summary.entrySet()) {
            source.sendSuccess(() -> Component.literal("- " + entry.getKey() + ": " + entry.getValue()), false);
        }
        return summary.size();
    }

    private static EquipmentSlot requireSlot(CommandSourceStack source, String slotName) {
        EquipmentSlot slot = NpcEquipment.parseSlot(slotName);
        if (slot == null) {
            source.sendFailure(Component.literal("Unknown slot '" + slotName + "'. Use: " + String.join(", ", NpcEquipment.slotNames())));
        }
        return slot;
    }
}
