package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
public final class CableSpanSync {
    private CableSpanSync() {}
    public record Snapshot(ChunkPos chunk, List<CableSpans.Span> spans) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "tray_cable_spans"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(RegistryFriendlyByteBuf buffer) {
                var chunk = new ChunkPos(buffer.readLong()); int count = buffer.readVarInt();
                if (count < 0 || count > 65536) throw new IllegalArgumentException("Invalid cable snapshot size");
                var spans = new ArrayList<CableSpans.Span>();
                for (int i = 0; i < count; i++) { var tag = buffer.readNbt(); var span = tag == null ? null : CableSpans.Span.read(tag); if (span != null) spans.add(span); }
                return new Snapshot(chunk, List.copyOf(spans));
            }
            public void encode(RegistryFriendlyByteBuf buffer, Snapshot value) {
                buffer.writeLong(value.chunk.toLong()); buffer.writeVarInt(value.spans.size()); for (var span : value.spans) buffer.writeNbt(span.write());
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Snapshot.TYPE, Snapshot.CODEC, (message, context) ->
                context.enqueueWork(() -> net.createmod.catnip.platform.CatnipServices.PLATFORM.executeOnClientOnly(() -> () ->
                        com.aislen.createindustrialdetails.client.model.TrayCableSpans.receive(message))));
    }
    public static void changed(ServerLevel level, ChunkPos chunk) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, new Snapshot(chunk, CableSpans.get(level).traySpans(chunk)));
    }
    @SubscribeEvent public static void watch(ChunkWatchEvent.Watch event) {
        var spans = CableSpans.get(event.getLevel()).traySpans(event.getPos());
        if (!spans.isEmpty()) PacketDistributor.sendToPlayer(event.getPlayer(), new Snapshot(event.getPos(), spans));
    }
}
