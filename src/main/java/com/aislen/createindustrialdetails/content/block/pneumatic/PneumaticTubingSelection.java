package com.aislen.createindustrialdetails.content.block.pneumatic;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
public final class PneumaticTubingSelection {
    private static final Map<Player, Selection> SERVER = new WeakHashMap<>();
    private static final Map<Player, Selection> CLIENT = new WeakHashMap<>();

    private PneumaticTubingSelection() {}

    private record Selection(PneumaticSpline.Port port, InteractionHand hand, int slot, ResourceKey<Level> dimension, ItemStack stack, UUID id) {
        boolean heldBy(Player player) {
            ItemStack held = player.getItemInHand(hand);
            return player.level().dimension().equals(dimension) && (hand != InteractionHand.MAIN_HAND || player.getInventory().selected == slot)
                    && held.getItem() instanceof PneumaticTubingItem && (player.level().isClientSide || stack == held);
        }
    }

    private static Map<Player, Selection> selections(Player player) {
        return player.level().isClientSide ? CLIENT : SERVER;
    }

    private static Selection current(Player player) {
        Selection selection = selections(player).get(player);
        if (selection != null && !selection.heldBy(player)) {
            clear(player, selection.hand);
            if (player.level().isClientSide)
                PacketDistributor.sendToServer(new PneumaticEvents.SelectionCancel(selection.id));
            return null;
        }
        return selection;
    }

    public static PneumaticSpline.Port port(Player player, InteractionHand hand) {
        Selection selection = current(player);
        return selection != null && selection.hand == hand ? selection.port : null;
    }

    static void select(Player player, InteractionHand hand, PneumaticSpline.Port port) {
        Selection selection = new Selection(port, hand, player.getInventory().selected, player.level().dimension(),
                player.getItemInHand(hand), UUID.randomUUID());
        selections(player).put(player, selection);
        if (player instanceof ServerPlayer serverPlayer) {
            PneumaticEvents.selection(serverPlayer, selection.id, port, hand, selection.slot);
        }
    }

    static boolean clear(Player player, InteractionHand hand) {
        Selection selection = selections(player).get(player);
        if (selection == null || selection.hand != hand) return false;
        selections(player).remove(player);
        if (player instanceof ServerPlayer serverPlayer)
            PneumaticEvents.selection(serverPlayer, selection.id, null, hand, selection.slot);
        return true;
    }

    static void cancel(Player player, UUID id) {
        Selection selection = SERVER.get(player);
        if (selection != null && selection.id.equals(id)) clear(player, selection.hand);
    }

    public static void receive(Player player, CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        if (!tag.contains("Port")) {
            var previous = CLIENT.get(player);
            if (previous != null && previous.id.equals(id)) CLIENT.remove(player);
            return;
        }
        InteractionHand hand = tag.getBoolean("Offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Selection selection = new Selection(PneumaticSpline.Port.load(tag.getCompound("Port")), hand, tag.getInt("Slot"),
                player.level().dimension(), player.getItemInHand(hand), id);
        if (selection.heldBy(player)) CLIENT.put(player, selection);
    }

    @SubscribeEvent
    public static void heldItemChanged(PlayerTickEvent.Post event) {
        if (!selections(event.getEntity()).isEmpty()) current(event.getEntity());
    }
}
