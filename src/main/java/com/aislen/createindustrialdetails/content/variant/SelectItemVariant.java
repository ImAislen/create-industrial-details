package com.aislen.createindustrialdetails.content.variant;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SelectItemVariant(int slot, ResourceLocation item, String previous, String selected)
        implements CustomPacketPayload {
    public static final Type<SelectItemVariant> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "select_item_variant"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectItemVariant> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectItemVariant::slot,
            ResourceLocation.STREAM_CODEC, SelectItemVariant::item,
            ByteBufCodecs.stringUtf8(64), SelectItemVariant::previous,
            ByteBufCodecs.stringUtf8(64), SelectItemVariant::selected,
            SelectItemVariant::new);

    @Override
    public Type<SelectItemVariant> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                request.apply(player);
            }
        });
    }

    public boolean apply(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || player.containerMenu != player.inventoryMenu
                || !Inventory.isHotbarSlot(slot) || player.getInventory().selected != slot) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof VariantItem family)
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)
                || !family.selectedVariant(stack).equals(previous)
                || !family.selectVariant(stack, selected)) {
            return false;
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
