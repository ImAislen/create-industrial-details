package com.aislen.createindustrialdetails.content.block.pneumatic;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
public final class PneumaticEvents {
    private PneumaticEvents() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(View.TYPE, View.CODEC, (message, context) ->
                context.enqueueWork(() -> net.createmod.catnip.platform.CatnipServices.PLATFORM.executeOnClientOnly(
                        () -> () -> com.aislen.createindustrialdetails.client.model.PneumaticRendering.receive(message))))
                .playToServer(SelectionCancel.TYPE, SelectionCancel.CODEC,
                        (message, context) -> PneumaticTubingSelection.cancel(context.player(), message.id));
    }

    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, entity, side) -> {
            if (!(level instanceof ServerLevel server)) return null;
            return new FuelHandler(server, PneumaticStationBlock.base(state, pos));
        }, ModBlocks.PNEUMATIC_LAUNCHER.get(), ModBlocks.PNEUMATIC_BOOSTER.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, entity, side) ->
                level instanceof ServerLevel server && !state.getValue(PneumaticStationBlock.UPPER)
                        && side != null && side != Direction.UP ? new ReceiverOutput(server, pos.immutable()) : null,
                ModBlocks.PNEUMATIC_RECEIVER.get());
    }

    private record ReceiverOutput(ServerLevel level, BlockPos pos) implements IItemHandler {
        @Override public int getSlots() { return PneumaticNetwork.PACKAGE_SLOTS; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return extractItem(slot, Integer.MAX_VALUE, true);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return PneumaticNetwork.get(level).receiverOutput(level, pos, slot, amount, simulate);
        }
    }

    private record FuelHandler(ServerLevel level, BlockPos pos) implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public int getSlotLimit(int slot) { return Items.WIND_CHARGE.getDefaultMaxStackSize(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && stack.is(Items.WIND_CHARGE); }

        private boolean available() {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk != null && chunk.getBlockState(pos).getBlock() instanceof PneumaticStationBlock station
                    && (station.kind == PneumaticStationBlock.Kind.LAUNCHER || station.kind == PneumaticStationBlock.Kind.BOOSTER);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 && available() ? fuelStack(PneumaticNetwork.get(level).fuel(pos)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack) || !available()) return stack;
            return stack.copyWithCount(stack.getCount() - PneumaticNetwork.get(level).insertFuel(pos, stack.getCount(), simulate));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 && available() && amount > 0
                    ? fuelStack(PneumaticNetwork.get(level).extractFuel(pos, amount, simulate)) : ItemStack.EMPTY;
        }

        private static ItemStack fuelStack(int count) {
            return count == 0 ? ItemStack.EMPTY : new ItemStack(Items.WIND_CHARGE, count);
        }
    }

    record SelectionCancel(UUID id) implements CustomPacketPayload {
        static final Type<SelectionCancel> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateIndustrialDetails.MOD_ID, "pneumatic_selection_cancel"));
        static final StreamCodec<RegistryFriendlyByteBuf, SelectionCancel> CODEC = new StreamCodec<>() {
            @Override public SelectionCancel decode(RegistryFriendlyByteBuf buffer) {
                return new SelectionCancel(buffer.readUUID());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, SelectionCancel message) {
                buffer.writeUUID(message.id);
            }
        };

        @Override public Type<SelectionCancel> type() { return TYPE; }
    }

    public record View(CompoundTag info) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "pneumatic_view"));
        static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override public View decode(RegistryFriendlyByteBuf buffer) { return new View(buffer.readNbt()); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, View message) { buffer.writeNbt(message.info); }
        };
        @Override public Type<View> type() { return TYPE; }
    }

    private static CompoundTag message(ServerLevel level, String type) {
        var tag = new CompoundTag();
        tag.putString("Dimension", level.dimension().location().toString());
        tag.putString("Type", type);
        return tag;
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Sent event) {
        var level = (ServerLevel) event.getLevel();
        var network = PneumaticNetwork.get(level);
        if (!network.spansIn(event.getPos()).isEmpty() || !network.receiversIn(event.getPos()).isEmpty())
            PacketDistributor.sendToPlayer(event.getPlayer(), chunkView(level, event.getPos(), network));
        for (var flight : network.flightsIn(event.getPos(), level.getGameTime()))
            PacketDistributor.sendToPlayer(event.getPlayer(), flightView(level, flight));
    }

    static void sync(ServerLevel level, Set<ChunkPos> chunks) {
        var network = PneumaticNetwork.get(level);
        for (ChunkPos chunk : chunks)
            PacketDistributor.sendToPlayersTrackingChunk(level, chunk, chunkView(level, chunk, network));
    }

    private static View chunkView(ServerLevel level, ChunkPos chunk, PneumaticNetwork network) {
        var tag = message(level, "Chunk");
        tag.putLong("Chunk", chunk.toLong());
        var list = new ListTag();
        for (var span : network.spansIn(chunk)) list.add(span.save());
        tag.put("Spans", list);
        var receivers = new ListTag();
        for (var display : network.receiversIn(chunk)) receivers.add(display.save());
        tag.put("Receivers", receivers);
        return new View(tag);
    }

    static void receiver(ServerLevel level, PneumaticNetwork.ReceiverDisplay display) {
        var tag = message(level, "Receiver");
        tag.put("Receiver", display.save());
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(display.pos()), new View(tag));
    }

    static void selection(ServerPlayer player, UUID id, PneumaticSpline.Port port, InteractionHand hand, int slot) {
        var tag = message(player.serverLevel(), "Selection");
        tag.putUUID("Id", id);
        if (port != null) {
            tag.put("Port", port.save());
            tag.putBoolean("Offhand", hand == InteractionHand.OFF_HAND);
            tag.putInt("Slot", slot);
        }
        PacketDistributor.sendToPlayer(player, new View(tag));
    }

    private static View flightView(ServerLevel level, PneumaticNetwork.Flight flight) {
        var tag = message(level, "Flight");
        tag.put("Flight", flight.save());
        return new View(tag);
    }

    static void launch(ServerLevel level, PneumaticNetwork.Flight flight) {
        sendFlight(level, flight, flightView(level, flight));
    }

    static void cancel(ServerLevel level, PneumaticNetwork.Flight flight) {
        var tag = message(level, "Cancel");
        tag.putUUID("Flight", flight.id());
        sendFlight(level, flight, new View(tag));
    }

    private static void sendFlight(ServerLevel level, PneumaticNetwork.Flight flight, View view) {
        var players = new HashSet<ServerPlayer>();
        for (ChunkPos chunk : flight.route().chunks()) players.addAll(level.getChunkSource().chunkMap.getPlayers(chunk, false));
        for (ServerPlayer player : players) PacketDistributor.sendToPlayer(player, view);
    }

    @SubscribeEvent
    public static void levelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) PneumaticNetwork.get(level);
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PneumaticNetwork.unload(level);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) PneumaticNetwork.tickActive(level);
    }

    static void clunk(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .75f, .95f);
    }

    static void launcherSound(ServerLevel level, BlockPos pos) {
        AllSoundEvents.FWOOMP.playOnServer(level, pos, 1, 1);
    }

    static void impulse(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, .9f, .75f);
    }
}

