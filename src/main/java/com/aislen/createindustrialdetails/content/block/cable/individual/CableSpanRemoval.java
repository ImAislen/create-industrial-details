package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CableSpanRemoval {
    private CableSpanRemoval() {}
    public record Request(BlockPos from, BlockPos to, int color, int fromRoute, int toRoute, int fromPort, int toPort) implements CustomPacketPayload {
        public Request(BlockPos from, BlockPos to, int color) { this(from, to, color, 0, 0, 0, 0); }
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "remove_cable_span"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            public Request decode(RegistryFriendlyByteBuf buffer) { return new Request(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()); }
            public void encode(RegistryFriendlyByteBuf buffer, Request value) { buffer.writeBlockPos(value.from); buffer.writeBlockPos(value.to); buffer.writeVarInt(value.color); buffer.writeVarInt(value.fromRoute); buffer.writeVarInt(value.toRoute); buffer.writeVarInt(value.fromPort); buffer.writeVarInt(value.toPort); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static Request of(CableSpans.Span span) { return new Request(span.from(), span.to(), span.color().ordinal(), span.fromRoute().ordinal(), span.toRoute().ordinal(), span.fromPort(), span.toPort()); }
    }
    public static boolean isWrench(ItemStack stack) { return AllItems.WRENCH.isIn(stack) || stack.is(Tags.Items.TOOLS_WRENCH); }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToServer(Request.TYPE, Request.CODEC, (message, context) -> {
            if (context.player() instanceof ServerPlayer player) remove(player, message);
        });
    }
    public static boolean remove(ServerPlayer player, Request request) {
        if (!player.isShiftKeyDown() || !player.mayBuild() || player.isSpectator() || !isWrench(player.getMainHandItem())) return false;
        var level = player.serverLevel(); var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        var ledger = CableSpans.get(level); var hit = ledger.spatial().trace(start, end);
        if (hit == null) return false;
        var span = hit.span();
        if (!Request.of(span).equals(request)) return false;
        for (var pos : java.util.List.of(span.from(), span.to())) {
            if (!(pos.equals(span.from()) ? span.start() : span.end()).exists(level, span.color()) || !level.mayInteract(player, pos)) return false;
            var event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
            NeoForge.EVENT_BUS.post(event); if (event.isCanceled()) return false;
        }
        if (!ledger.remove(span)) return false;
        if (!player.isCreative()) IndividualCableBlock.returnItems(player, span.color(), span.cost());
        IWrenchable.playRemoveSound(level, BlockPos.containing(hit.point()));
        return true;
    }
}
