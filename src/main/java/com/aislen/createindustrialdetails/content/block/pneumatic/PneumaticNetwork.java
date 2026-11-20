package com.aislen.createindustrialdetails.content.block.pneumatic;

import com.aislen.createindustrialdetails.registry.ModItems;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.aislen.createindustrialdetails.content.block.pneumatic.PneumaticSpline.Port;
import static com.aislen.createindustrialdetails.content.block.pneumatic.PneumaticStationBlock.Kind;

/** Per-dimension ownership for cargo, fuel, and destination reservations. */
public final class PneumaticNetwork extends SavedData {
    public static final double IMPULSE_RANGE = 64;
    public static final double SPEED = 4;
    public static final int RECEIVER_CAPACITY = 8;
    static final int PACKAGE_SLOTS = 9;
    private static final int MAX_ROUTE_SPANS = 64;
    private static final double RANGE_EPSILON = 1e-7;
    private static final SavedData.Factory<PneumaticNetwork> FACTORY = new SavedData.Factory<>(PneumaticNetwork::new, PneumaticNetwork::load);
    private static final Map<ServerLevel, PneumaticNetwork> ACTIVE = new WeakHashMap<>();

    private final Map<Port, Span> connections = new HashMap<>();
    private final Set<Span> spans = new LinkedHashSet<>();
    private final Map<ChunkPos, Set<Span>> chunkSpans = new HashMap<>();
    private final Map<BlockPos, Store> stores = new HashMap<>();
    private final Map<ChunkPos, Set<BlockPos>> receivers = new HashMap<>();
    private final Map<BlockPos, Optional<Route>> routes = new HashMap<>();
    private final Map<UUID, Transit> transits = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> reservations = new HashMap<>();
    private final Map<BlockPos, Deque<ItemStack>> recovery = new HashMap<>();

    private static final class Store {
        int fuel;
        boolean receiver;
        final Deque<ItemStack> packages = new ArrayDeque<>();
        ItemStackHandler output;
        Optional<UnopenedOutput> preview;

        int receiverOccupancy() {
            return packages.size() + (output == null ? 0 : 1);
        }
    }

    public record Span(Port from, Port to, int paid, PneumaticSpline curve) {
        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.put("From", from.save());
            tag.put("To", to.save());
            tag.putInt("Paid", paid);
            return tag;
        }

        public static Span load(CompoundTag tag) {
            Port from = Port.load(tag.getCompound("From"));
            Port to = Port.load(tag.getCompound("To"));
            return new Span(from, to, tag.getInt("Paid"), new PneumaticSpline(from, to));
        }
    }

    public record Route(List<Span> legs, List<BlockPos> boosters, List<Double> impulses,
                        Set<ChunkPos> chunks, double length) {
        public BlockPos receiver() { return legs.getLast().to.pos(); }

        public Pose pose(double distance) {
            for (int i = 0; i < legs.size(); i++) {
                Span leg = legs.get(i);
                double length = leg.curve.length();
                if (distance <= length || i == legs.size() - 1)
                    return new Pose(leg.curve.at(distance), leg.curve.tangent(distance));
                distance -= length;
                var next = legs.get(i + 1);
                if (distance <= 1) {
                    var start = leg.to.center();
                    var end = next.from.center();
                    return new Pose(start.lerp(end, distance), end.subtract(start).normalize());
                }
                distance -= 1;
            }
            throw new IllegalStateException("Empty pneumatic route");
        }
    }

    public record Pose(Vec3 pos, Vec3 direction) {}

    public record Flight(UUID id, long launch, Route route, List<Integer> boosts) {
        public double distance(double gameTime) { return Math.max(0, (gameTime - launch) * SPEED); }

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putLong("Launch", launch);
            var list = new ListTag();
            for (Span leg : route.legs) list.add(leg.save());
            tag.put("Legs", list);
            tag.putIntArray("Impulses", boosts);
            return tag;
        }

        public static Flight load(CompoundTag tag) {
            var legs = new ArrayList<Span>();
            for (Tag entry : tag.getList("Legs", Tag.TAG_COMPOUND)) legs.add(Span.load((CompoundTag) entry));
            if (legs.isEmpty() || legs.size() > MAX_ROUTE_SPANS) throw new IllegalArgumentException("Invalid pneumatic flight length");
            var route = makeRoute(legs);
            if (route == null) throw new IllegalArgumentException("Invalid pneumatic flight range");
            var boosts = new ArrayList<Integer>();
            int previous = -1;
            for (int stop : tag.getIntArray("Impulses")) {
                if (stop > previous && stop < route.boosters.size()) {
                    boosts.add(stop);
                    previous = stop;
                }
            }
            return new Flight(tag.getUUID("Id"), tag.getLong("Launch"), route, List.copyOf(boosts));
        }
    }

    private static final class Transit {
        final Flight flight;
        final ItemStack cargo;
        int nextImpulse;
        long retryArrival;
        Transit(Flight flight, ItemStack cargo) { this.flight = flight; this.cargo = cargo; }
    }

    public static PneumaticNetwork get(ServerLevel level) {
        var network = level.getDataStorage().computeIfAbsent(FACTORY, "cid_pneumatic_dispatch");
        if (!network.transits.isEmpty()) ACTIVE.put(level, network);
        return network;
    }

    static void tickActive(ServerLevel level) {
        PneumaticNetwork active = ACTIVE.get(level);
        if (active == null) return;
        active.tick(level);
        if (active.transits.isEmpty()) ACTIVE.remove(level);
    }

    static void unload(ServerLevel level) { ACTIVE.remove(level); }

    public Collection<Span> spansIn(ChunkPos chunk) {
        return chunkSpans.getOrDefault(chunk, Set.of());
    }

    public record ReceiverDisplay(BlockPos pos, ResourceLocation style, long arrival) {
        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            if (style != null) tag.putString("Style", style.toString());
            tag.putLong("Arrival", arrival);
            return tag;
        }

        public static ReceiverDisplay load(CompoundTag tag) {
            return new ReceiverDisplay(BlockPos.of(tag.getLong("Pos")), ResourceLocation.tryParse(tag.getString("Style")), tag.getLong("Arrival"));
        }
    }

    private static ResourceLocation style(Store receiver) {
        return receiver.packages.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(receiver.packages.getFirst().getItem());
    }

    public List<ReceiverDisplay> receiversIn(ChunkPos chunk) {
        return receivers.getOrDefault(chunk, Set.of()).stream()
                .map(pos -> new ReceiverDisplay(pos, style(stores.get(pos)), 0)).toList();
    }

    private void unindexReceiver(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        Set<BlockPos> positions = receivers.get(chunk);
        if (positions != null && positions.remove(pos) && positions.isEmpty()) receivers.remove(chunk);
    }

    public List<Flight> flightsIn(ChunkPos chunk, long time) {
        var result = new ArrayList<Flight>();
        for (var transit : transits.values()) {
            var flight = transit.flight;
            if (flight.distance(time) < flight.route.length && flight.route.chunks.contains(chunk)) result.add(flight);
        }
        return result;
    }

    public boolean occupied(Port port) { return connections.containsKey(port); }

    @Nullable
    Port connectedTo(Port port) {
        Span span = connections.get(port);
        return span == null ? null : (span.from.equals(port) ? span.to : span.from);
    }

    boolean connect(ServerLevel level, Port from, Port to, PneumaticSpline curve, int paid) {
        if (!validPort(level, from) || !validPort(level, to) || from.pos().equals(to.pos())
                || occupied(from) || occupied(to) || !curve.valid() || paid < 0 || (paid != 0 && paid != curve.cost()))
            return false;
        var span = new Span(from, to, paid, curve);
        index(span);
        routes.clear();
        setDirty();
        PneumaticEvents.sync(level, curve.chunks);
        return true;
    }

    private void index(Span span) {
        if (occupied(span.from) || occupied(span.to)) return;
        spans.add(span);
        connections.put(span.from, span);
        connections.put(span.to, span);
        for (ChunkPos chunk : span.curve.chunks)
            chunkSpans.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(span);
    }

    boolean removeSpan(ServerLevel level, Port port, @Nullable Player player) {
        Span span = connections.get(port);
        if (span == null) return false;
        unlink(span);
        PneumaticEvents.sync(level, span.curve.chunks);
        if (span.paid > 0 && (player == null || !player.isCreative())) {
            var item = ModItems.PNEUMATIC_TUBING.get();
            for (int left = span.paid; left > 0; left -= item.getDefaultMaxStackSize()) {
                ItemStack tubing = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
                if (player == null) Block.popResource(level, port.pos(), tubing);
                else player.getInventory().placeItemBackInInventory(tubing);
            }
        }
        return true;
    }

    private void unlink(Span span) {
        spans.remove(span);
        connections.remove(span.from);
        connections.remove(span.to);
        for (ChunkPos chunk : span.curve.chunks) {
            Set<Span> inChunk = chunkSpans.get(chunk);
            inChunk.remove(span);
            if (inChunk.isEmpty()) chunkSpans.remove(chunk);
        }
        routes.clear();
        setDirty();
    }

    void disconnectStation(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values())
            for (Kind kind : Kind.values()) removeSpan(level, new Port(pos, side, kind), null);
    }

    void removeStation(ServerLevel level, BlockPos pos, Kind kind) {
        disconnectStation(level, pos);
        Store store = stores.remove(pos);
        if (store != null) {
            setDirty();
            if (store.receiver) {
                unindexReceiver(pos);
                PneumaticEvents.receiver(level, new ReceiverDisplay(pos, null, 0));
            }
            if (store.fuel > 0) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.WIND_CHARGE, store.fuel));
            if (!store.packages.isEmpty() || store.output != null) {
                var items = recovery.computeIfAbsent(pos, ignored -> new ArrayDeque<>());
                items.addAll(store.packages);
                if (store.output != null) {
                    for (int slot = 0; slot < store.output.getSlots(); slot++) {
                        ItemStack stack = store.output.getStackInSlot(slot);
                        if (!stack.isEmpty()) items.add(stack);
                    }
                }
            }
        }
        if (kind != Kind.RECEIVER || !reservations.containsKey(pos)) return;
        var iterator = transits.values().iterator();
        while (iterator.hasNext()) {
            Transit transit = iterator.next();
            if (!transit.flight.route.receiver().equals(pos)) continue;
            retain(transit);
            iterator.remove();
            PneumaticEvents.cancel(level, transit.flight);
        }
    }

    public int fuel(BlockPos pos) {
        Store store = stores.get(pos);
        return store == null ? 0 : store.fuel;
    }

    public int insertFuel(BlockPos pos, int amount, boolean simulate) {
        int accepted = Math.min(amount, Items.WIND_CHARGE.getDefaultMaxStackSize() - fuel(pos));
        if (!simulate && accepted > 0) {
            stores.computeIfAbsent(pos.immutable(), ignored -> new Store()).fuel += accepted;
            setDirty();
        }
        return accepted;
    }

    public int extractFuel(BlockPos pos, int amount, boolean simulate) {
        int extracted = Math.min(amount, fuel(pos));
        if (!simulate && extracted > 0) {
            Store store = stores.get(pos);
            store.fuel -= extracted;
            prune(pos, store);
            setDirty();
        }
        return extracted;
    }

    ItemStack insertPackage(ServerLevel level, BlockPos pos, ItemStack stack, boolean simulate) {
        if (!PackageItem.isPackage(stack) || !peek(pos).isEmpty() || !isStation(level, pos, Kind.LAUNCHER)) return stack;
        if (!simulate) {
            stores.computeIfAbsent(pos.immutable(), ignored -> new Store()).packages.add(stack.copyWithCount(1));
            setDirty();
            displayCargo(level, pos, true);
            if (level.getBlockEntity(pos) instanceof PneumaticLauncherBlockEntity launcher) launcher.packageLoaded();
            PneumaticEvents.clunk(level, pos);
        }
        return stack.copyWithCount(stack.getCount() - 1);
    }

    public ItemStack peek(BlockPos pos) {
        Store store = stores.get(pos);
        return store == null || store.packages.isEmpty() ? ItemStack.EMPTY : store.packages.getFirst().copy();
    }

    ItemStack extractPackage(ServerLevel level, BlockPos pos, boolean simulate) {
        if (!isStation(level, pos, Kind.RECEIVER)) return ItemStack.EMPTY;
        Store store = stores.get(pos);
        if (store == null || store.packages.isEmpty()) return ItemStack.EMPTY;
        if (simulate) return store.packages.getFirst().copy();
        var oldStyle = style(store);
        ItemStack packageStack = store.packages.removeFirst();
        receiverChanged(level, pos, store, oldStyle);
        return packageStack;
    }

    private record UnopenedOutput(ItemStack cargo, ItemStackHandler contents) {}

    @Nullable
    private static UnopenedOutput nextUnopenedOutput(Store store) {
        if (store.preview != null) return store.preview.orElse(null);
        for (ItemStack cargo : store.packages) {
            ItemStackHandler contents = PackageItem.getContents(cargo);
            if (hasContents(contents)) {
                var unopened = new UnopenedOutput(cargo, contents);
                store.preview = Optional.of(unopened);
                return unopened;
            }
        }
        store.preview = Optional.empty();
        return null;
    }

    private static boolean hasContents(ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++)
            if (!handler.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }

    ItemStack receiverOutput(ServerLevel level, BlockPos pos, int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= PACKAGE_SLOTS || amount <= 0 || !isStation(level, pos, Kind.RECEIVER))
            return ItemStack.EMPTY;
        Store store = stores.get(pos);
        if (store == null) return ItemStack.EMPTY;
        ItemStackHandler output = store.output;
        UnopenedOutput unopened = null;
        if (output == null) {
            unopened = nextUnopenedOutput(store);
            if (unopened == null) return ItemStack.EMPTY;
            output = unopened.contents;
        }
        ItemStack available = output.getStackInSlot(slot);
        if (available.isEmpty()) return ItemStack.EMPTY;
        if (simulate) return output.extractItem(slot, amount, true).copy();

        var previousStyle = style(store);
        if (unopened != null) {
            store.packages.removeFirstOccurrence(unopened.cargo);
            store.output = output;
        }
        ItemStack extracted = output.extractItem(slot, amount, false);
        if (!hasContents(output)) store.output = null;
        receiverChanged(level, pos, store, previousStyle);
        return extracted;
    }

    private void receiverChanged(ServerLevel level, BlockPos pos, Store store, @Nullable ResourceLocation previousStyle) {
        store.preview = null;
        var nextStyle = style(store);
        if (nextStyle == null) unindexReceiver(pos);
        if (!Objects.equals(previousStyle, nextStyle))
            PneumaticEvents.receiver(level, new ReceiverDisplay(pos, nextStyle, 0));
        displayCargo(level, pos, nextStyle != null);
        prune(pos, store);
        setDirty();
    }

    private void prune(BlockPos pos, Store store) {
        if (store.fuel == 0 && store.packages.isEmpty() && store.output == null) stores.remove(pos);
    }

    boolean recover(BlockPos pos, Player player) {
        var cargo = recovery.get(pos);
        if (cargo == null) return false;
        ItemStack stack = cargo.getFirst();
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(player.getInventory()), stack.copy(), false);
        if (remainder.getCount() == stack.getCount()) return false;
        cargo.removeFirst();
        if (!remainder.isEmpty()) cargo.addFirst(remainder);
        if (cargo.isEmpty()) recovery.remove(pos);
        setDirty();
        return true;
    }

    void refreshLauncherDisplay(ServerLevel level, BlockPos pos) {
        Store store = stores.get(pos);
        displayCargo(level, pos, store != null && !store.packages.isEmpty());
    }

    private static void displayCargo(ServerLevel level, BlockPos pos, boolean loaded) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof PneumaticStationBlock && state.getValue(PneumaticStationBlock.LOADED) != loaded)
            level.setBlock(pos, state.setValue(PneumaticStationBlock.LOADED, loaded), Block.UPDATE_CLIENTS);
    }

    private static BlockState loadedState(Level level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }

    private static boolean isStation(Level level, BlockPos pos, Kind kind) {
        var state = loadedState(level, pos);
        if (!(state.getBlock() instanceof PneumaticStationBlock station) || station.kind != kind
                || state.getValue(PneumaticStationBlock.UPPER)) return false;
        if (!kind.tall) return true;
        var upper = loadedState(level, pos.above());
        return upper.is(station) && upper.getValue(PneumaticStationBlock.UPPER);
    }

    public static boolean validPort(Level level, Port port) {
        return isStation(level, port.pos(), port.kind())
                && port.equals(PneumaticStationBlock.port(loadedState(level, port.pos()), port.pos(), port.face()));
    }

    @Nullable
    static Route makeRoute(List<Span> legs) {
        double length = 0;
        double fuelOrigin = 0;
        var boosters = new ArrayList<BlockPos>();
        var impulses = new ArrayList<Double>();
        var chunks = new HashSet<ChunkPos>();
        for (int i = 0; i < legs.size(); i++) {
            Span span = legs.get(i);
            chunks.addAll(span.curve.chunks);
            length += span.curve.length();
            if (i != legs.size() - 1) {
                if (span.to.kind() == Kind.BOOSTER) {
                    double chamberCenter = length + .5;
                    if (chamberCenter - fuelOrigin > IMPULSE_RANGE + RANGE_EPSILON) return null;
                    fuelOrigin = chamberCenter;
                    boosters.add(span.to.pos());
                    impulses.add(chamberCenter);
                }
                length += 1;
            }
        }
        if (length - fuelOrigin > IMPULSE_RANGE + RANGE_EPSILON) return null;
        for (Span span : legs) {
            chunks.add(new ChunkPos(span.from.pos()));
            chunks.add(new ChunkPos(span.to.pos()));
        }
        return new Route(List.copyOf(legs), List.copyOf(boosters), List.copyOf(impulses), Set.copyOf(chunks), length);
    }

    private Optional<Route> findRoute(BlockPos launcher) {
        var origin = new Port(launcher, Direction.UP, Kind.LAUNCHER);
        var visited = new HashSet<BlockPos>();
        visited.add(launcher);
        var legs = new ArrayList<Span>();
        var outgoing = origin;
        for (int i = 0; i < MAX_ROUTE_SPANS; i++) {
            Span span = connections.get(outgoing);
            if (span == null) break;
            if (!span.from.equals(outgoing)) span = new Span(span.to, span.from, span.paid, new PneumaticSpline(span.to, span.from));
            Port incoming = span.to;
            if (!visited.add(incoming.pos())) break;
            legs.add(span);
            if (incoming.kind() == Kind.RECEIVER) return Optional.ofNullable(makeRoute(legs));
            if (incoming.kind() == Kind.LAUNCHER) break;
            outgoing = new Port(incoming.pos(), incoming.face().getOpposite(), incoming.kind());
        }
        return Optional.empty();
    }

    boolean dispatch(ServerLevel level, BlockPos launcher) {
        Store origin = stores.get(launcher);
        if (origin == null || origin.fuel <= 0 || origin.packages.isEmpty() || !isStation(level, launcher, Kind.LAUNCHER)) return false;
        var route = routes.computeIfAbsent(launcher, this::findRoute).orElse(null);
        if (route == null) return false;
        for (ChunkPos chunk : route.chunks)
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return false;
        for (Span span : route.legs)
            if (!validPort(level, span.from) || !validPort(level, span.to)) return false;
        BlockPos receiver = route.receiver();
        Store destination = stores.get(receiver);
        int reserved = reservations.getOrDefault(receiver, 0);
        if (reserved + (destination == null ? 0 : destination.receiverOccupancy()) >= RECEIVER_CAPACITY) return false;
        List<Integer> boosts = requiredBoosts(route);
        if (boosts == null) return false;

        // This entire commit runs on the server thread against one persisted owner.
        var flight = new Flight(UUID.randomUUID(), level.getGameTime(), route, boosts);
        ItemStack cargo = origin.packages.removeFirst();
        origin.fuel--;
        prune(launcher, origin);
        for (int stop : boosts) {
            BlockPos booster = route.boosters.get(stop);
            Store store = stores.get(booster);
            store.fuel--;
            prune(booster, store);
        }
        reservations.put(receiver, reserved + 1);
        transits.put(flight.id, new Transit(flight, cargo));
        ACTIVE.put(level, this);
        setDirty();
        displayCargo(level, launcher, false);
        PneumaticEvents.launcherSound(level, launcher);
        PneumaticEvents.launch(level, flight);
        return true;
    }

    @Nullable
    private List<Integer> requiredBoosts(Route route) {
        if (route.length <= IMPULSE_RANGE + RANGE_EPSILON) return List.of();
        var result = new ArrayList<Integer>();
        double last = 0;
        int next = 0;
        while (route.length - last > IMPULSE_RANGE + RANGE_EPSILON) {
            int farthest = -1;
            while (next < route.impulses.size() && route.impulses.get(next) - last <= IMPULSE_RANGE + RANGE_EPSILON) {
                if (fuel(route.boosters.get(next)) > 0) farthest = next;
                next++;
            }
            if (farthest < 0) return null;
            result.add(farthest);
            last = route.impulses.get(farthest);
        }
        return List.copyOf(result);
    }

    private void tick(ServerLevel level) {
        long time = level.getGameTime();
        var iterator = transits.values().iterator();
        while (iterator.hasNext()) {
            Transit transit = iterator.next();
            Flight flight = transit.flight;
            Route route = flight.route;
            double distance = flight.distance(time);
            while (transit.nextImpulse < flight.boosts.size()) {
                int stop = flight.boosts.get(transit.nextImpulse);
                if (distance < route.impulses.get(stop)) break;
                transit.nextImpulse++;
                BlockPos booster = route.boosters.get(stop);
                if (isStation(level, booster, Kind.BOOSTER)) PneumaticEvents.impulse(level, booster);
                setDirty();
            }
            if (distance < route.length || time < transit.retryArrival) continue;
            BlockPos receiver = route.receiver();
            if (level.getChunkSource().getChunkNow(receiver.getX() >> 4, receiver.getZ() >> 4) == null) {
                transit.retryArrival = time + 20;
                continue;
            }
            if (!isStation(level, receiver, Kind.RECEIVER)) {
                retain(transit);
            } else {
                var store = stores.computeIfAbsent(receiver, ignored -> new Store());
                if (store.receiverOccupancy() >= RECEIVER_CAPACITY) continue;
                boolean first = store.packages.isEmpty();
                store.receiver = true;
                store.packages.add(transit.cargo);
                store.preview = null;
                if (first) {
                    receivers.computeIfAbsent(new ChunkPos(receiver), ignored -> new HashSet<>()).add(receiver);
                    PneumaticEvents.receiver(level, new ReceiverDisplay(receiver, style(store), time));
                }
                unreserve(receiver);
                setDirty();
                displayCargo(level, receiver, true);
                PneumaticEvents.clunk(level, receiver.above());
            }
            iterator.remove();
        }
    }

    private void retain(Transit transit) {
        recovery.computeIfAbsent(transit.flight.route.legs.getFirst().from.pos(), ignored -> new ArrayDeque<>()).add(transit.cargo);
        unreserve(transit.flight.route.receiver());
        setDirty();
    }

    private void unreserve(BlockPos receiver) {
        reservations.computeIfPresent(receiver, (ignored, count) -> count == 1 ? null : count - 1);
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("Version", 1);
        var connections = new ListTag();
        for (Span span : spans) connections.add(span.save());
        root.put("Spans", connections);
        var items = new ListTag();
        stores.forEach((pos, store) -> {
            var tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putInt("Fuel", store.fuel);
            if (store.receiver) tag.putBoolean("Receiver", true);
            tag.put("Packages", savePackages(store.packages, registries));
            if (store.output != null) tag.put("Output", store.output.serializeNBT(registries));
            items.add(tag);
        });
        root.put("Stations", items);
        var active = new ListTag();
        for (Transit transit : transits.values()) {
            var tag = transit.flight.save();
            tag.put("Cargo", transit.cargo.save(registries));
            tag.putInt("NextImpulse", transit.nextImpulse);
            active.add(tag);
        }
        root.put("Transit", active);
        var returned = new ListTag();
        recovery.forEach((pos, cargo) -> {
            var tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.put("Packages", savePackages(cargo, registries));
            returned.add(tag);
        });
        root.put("Recovery", returned);
        return root;
    }

    private static ListTag savePackages(Collection<ItemStack> stacks, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (ItemStack stack : stacks) list.add(stack.save(registries));
        return list;
    }

    public static PneumaticNetwork load(CompoundTag root, HolderLookup.Provider registries) {
        var network = new PneumaticNetwork();
        for (Tag entry : root.getList("Spans", Tag.TAG_COMPOUND)) network.index(Span.load((CompoundTag) entry));
        for (Tag entry : root.getList("Stations", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) entry;
            Store store = new Store();
            store.fuel = tag.getInt("Fuel");
            store.receiver = tag.getBoolean("Receiver");
            readPackages(tag, registries, store.packages);
            if (tag.contains("Output", Tag.TAG_COMPOUND)) {
                var output = new ItemStackHandler(PACKAGE_SLOTS);
                output.deserializeNBT(registries, tag.getCompound("Output"));
                if (hasContents(output)) store.output = output;
            }
            BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            network.stores.put(pos, store);
            if (store.receiver && !store.packages.isEmpty())
                network.receivers.computeIfAbsent(new ChunkPos(pos), ignored -> new HashSet<>()).add(pos);
        }
        for (Tag entry : root.getList("Transit", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) entry;
            var flight = Flight.load(tag);
            var cargo = ItemStack.parseOptional(registries, tag.getCompound("Cargo"));
            if (cargo.isEmpty()) continue;
            var transit = new Transit(flight, cargo);
            transit.nextImpulse = tag.getInt("NextImpulse");
            network.transits.put(flight.id, transit);
            network.reservations.merge(flight.route.receiver(), 1, Integer::sum);
        }
        for (Tag entry : root.getList("Recovery", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) entry;
            var packages = new ArrayDeque<ItemStack>();
            readPackages(tag, registries, packages);
            if (!packages.isEmpty()) network.recovery.put(BlockPos.of(tag.getLong("Pos")), packages);
        }
        return network;
    }

    private static void readPackages(CompoundTag tag, HolderLookup.Provider registries, Deque<ItemStack> into) {
        for (Tag entry : tag.getList("Packages", Tag.TAG_COMPOUND))
            ItemStack.parse(registries, entry).ifPresent(into::add);
    }
}
