package com.aislen.createindustrialdetails.content.block.cable;

import com.mojang.logging.LogUtils;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class CableNetworkManager {
    private record Node(BlockPos pos, IndividualCableColor color,
                        CableSpans.Span span) {}
    private final java.util.Map<Node, Long> nodeIds = new java.util.HashMap<>();
    private final Long2ObjectMap<Node> nodes = new Long2ObjectOpenHashMap<>();
    private final java.util.Map<BlockPos, NetworkState> compositeSnapshots = new java.util.HashMap<>();
    private long nextNode = 1;
    private long key(BlockPos pos, IndividualCableColor color) {
        var node = new Node(pos.immutable(), color, null); var known = nodeIds.get(node);
        if (known != null) return known;
        var chunk = loadedPosition(pos); var block = chunk == null ? null : chunk.getBlockState(pos).getBlock();
        return block instanceof CableTrayBlock || block instanceof IndividualCableBlock || block instanceof ControlBoxBlock ? key(node) : 0;

    }
    private long key(Node node) { return nodeIds.computeIfAbsent(node, n -> { long id = nextNode++; nodes.put(id, n); return id; }); }
    private boolean conductive(long key) {
        Node node = nodes.get(key); if (node == null || loadedChunk(key) == null) return false;
        if (node.span != null) return loadedPosition(node.span.to()) != null && node.span.start().exists(level, node.color)
                && node.span.end().exists(level, node.color) && CableSpans.get(level).at(node.pos).contains(node.span);
        BlockState state = state(key);
        return state.getBlock() instanceof CableTrayBlock || state.getBlock() instanceof IndividualCableBlock
                && state.getValue(node.color.property) != CableRoute.NONE
                && loadedChunk(key).getBlockEntity(node.pos, LevelChunk.EntityCreationType.CHECK) != null;
    }
    static final int WORK_BUDGET = 2048;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final ServerLevel level;
    private final Long2ObjectMap<Network> membership = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<LongSet> chunks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<Set<Network>> sources = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<Set<Observation>> observers = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet seeds = new LongLinkedOpenHashSet();
    private final LongSet unloaded = new LongOpenHashSet();
    private final Long2ObjectLinkedOpenHashMap<LevelChunk> pendingLoads = new Long2ObjectLinkedOpenHashMap<>();
    private final Set<Observation> notifications = new LinkedHashSet<>();
    private Discovery discovery;
    private ChunkScan scan;
    private boolean processing;
    private long nextId = 1;

    CableNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public record NetworkState(long id, int signals) {
        public static final NetworkState DISCONNECTED = new NetworkState(0, 0);

        public int signal(ControlChannel channel) {
            return channel.read(signals);
        }

        public boolean connected() {
            return id != 0;
        }
    }

    public NetworkState read(BlockPos tray) {
        for (var color : IndividualCableColor.values()) {
            long key = key(tray, color); var known = membership.get(key);
            if ((known == null || !known.valid) && conductive(key)) seeds.add(key);
        }
        processPending(); return composite(tray);
    }
    public NetworkState read(BlockPos pos, IndividualCableColor color) { return read(key(pos, color)); }
    public NetworkState read(CableSpans.Span span) {
        return read(key(new Node(span.from(), span.color(), span)));
    }
    private NetworkState composite(BlockPos pos) {
        int signals = 0; long identity = 1; boolean any = false;
        for (var color : IndividualCableColor.values()) {
            long key = key(pos, color); if (!conductive(key)) continue;
            any = true; var network = membership.get(key); if (network == null || !network.valid) return NetworkState.DISCONNECTED;
            signals |= network.state().signals(); identity = 31 * identity + network.id;
        }
        if (!any) return NetworkState.DISCONNECTED;
        var next = new NetworkState(Math.max(1, identity & Long.MAX_VALUE), signals);
        var previous = compositeSnapshots.get(pos); if (next.equals(previous)) return previous;
        compositeSnapshots.put(pos.immutable(), next); return next;
    }
    private NetworkState read(long key) {
        Network network = membership.get(key);
        if ((network == null || !network.valid) && conductive(key)) seeds.add(key);
        processPending();
        network = membership.get(key);
        return network != null && network.valid ? network.state() : NetworkState.DISCONNECTED;
    }

    public Observation observe(BlockPos tray, Consumer<NetworkState> listener) {
        read(tray);
        var group = new Observation(0, listener);
        for (var color : IndividualCableColor.values())
            group.children.add(observe(key(new Node(tray.immutable(), color, null)), ignored -> {
                var value = composite(tray); if (!value.equals(group.last)) { group.last = value; listener.accept(value); }
            }));
        return group;
    }
    public Observation observe(BlockPos pos, IndividualCableColor color, Consumer<NetworkState> listener) {
        return observe(key(new Node(pos.immutable(), color, null)), listener);
    }
    private Observation observe(long key, Consumer<NetworkState> listener) {
        java.util.Objects.requireNonNull(listener);
        read(key);
        Observation observation = new Observation(key, listener);
        observers.computeIfAbsent(observation.position, ignored -> new HashSet<>()).add(observation);
        observation.attach(membership.get(observation.position));
        observation.queue(observation.network != null && observation.network.valid ? observation.network.state() : NetworkState.DISCONNECTED);
        flushNotifications();
        return observation;
    }

    public final class Observation implements AutoCloseable {
        private final java.util.List<Observation> children = new java.util.ArrayList<>();
        private final long position;
        private final Consumer<NetworkState> listener;
        private Network network;
        private NetworkState last;
        private NetworkState pending;
        private boolean closed;

        private Observation(long position, Consumer<NetworkState> listener) {
            this.position = position;
            this.listener = listener;
        }

        private void attach(Network next) {
            if (network == next) return;
            if (network != null) network.observers.remove(this);
            network = next;
            if (network != null) network.observers.add(this);
        }

        private void queue(NetworkState next) {
            pending = next;
            notifications.add(this);
        }

        @Override
        public void close() {
            for (var child : children) child.close();
            closed = true;
            attach(null);
            Set<Observation> entries = observers.get(position);
            if (entries != null && entries.remove(this) && entries.isEmpty()) observers.remove(position);
            notifications.remove(this);
        }
    }

    public void topologyChanged(BlockPos pos, BlockState previous) {
        var colors = java.util.EnumSet.allOf(IndividualCableColor.class);
        var chunk = loadedPosition(pos); var current = chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        if (previous.getBlock() instanceof IndividualCableBlock && current.getBlock() instanceof IndividualCableBlock && !previous.equals(current))
            colors.removeIf(color -> previous.getValue(color.property) == current.getValue(color.property));
        if (!(previous.getBlock() instanceof CableTrayBlock || previous.getBlock() instanceof ControlBoxBlock || current.getBlock() instanceof CableTrayBlock || current.getBlock() instanceof ControlBoxBlock))
            colors.removeIf(color -> !(previous.getBlock() instanceof IndividualCableBlock && previous.getValue(color.property) != CableRoute.NONE)
                    && !(current.getBlock() instanceof IndividualCableBlock && current.getValue(color.property) != CableRoute.NONE));
        topologyChanged(pos, colors);
    }
    public void topologyChanged(BlockPos pos, BlockState previous, IndividualCableColor color) { topologyChanged(pos, java.util.List.of(color)); }
    private void topologyChanged(BlockPos pos, Iterable<IndividualCableColor> colors) {
        for (var color : colors) if (discovery != null && discovery.network.color == color) { cancelDiscovery(); break; }
        for (var offset : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
            var neighbor = pos.offset(offset);
            for (var color : colors) {
                long key = key(neighbor, color); invalidate(membership.get(key));
                if (conductive(key)) seeds.add(key); else removeMember(key);
            }
        }
        for (var span : CableSpans.get(level).at(pos)) {
            for (var color : colors) if (span.color() == color) {
                long key = key(new Node(span.from(), span.color(), span)); invalidate(membership.get(key));
                if (conductive(key)) seeds.add(key); else removeMember(key);
            }
        }
    }

    public void spanChanged(CableSpans.Span span) {
        if (discovery != null && discovery.network.color == span.color()) cancelDiscovery(); long key = key(new Node(span.from(), span.color(), span));
        invalidate(membership.get(key)); if (conductive(key)) seeds.add(key); else removeMember(key);
        topologyChanged(span.from(), state(key), span.color()); topologyChanged(span.to(), state(key), span.color());
    }
    public record ChannelState(NetworkState network, boolean hasSource, boolean hasTray) {
        public boolean hasControlBox() { return hasSource; }
    }
    public ChannelState inspect(CableSpans.Span span) {
        var snapshot = read(span); var network = membership.get(key(new Node(span.from(), span.color(), span)));
        return new ChannelState(snapshot, network != null && network.valid && network.activeSources != 0,
                network != null && network.valid && network.hasTray);
    }
    public ChannelState inspect(BlockPos pos, IndividualCableColor color) {
        var snapshot = read(pos, color); var network = membership.get(key(pos, color));
        return new ChannelState(snapshot, network != null && network.valid && network.activeSources != 0,
                network != null && network.valid && network.hasTray);
    }
    void sourceChanged(BlockPos pos) {
        Set<Network> attached = sources.get(pos.asLong());
        if (attached != null) for (Network network : attached) refresh(network);
        if (!processing) flushNotifications();
    }

    void queueChunkLoad(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        unloaded.remove(key);
        pendingLoads.put(key, chunk);
    }

    void unloadChunk(LevelChunk chunk) {
        boolean previous = processing;
        processing = true;
        try {
            removeChunk(chunk);
        } finally {
            processing = previous;
        }
        if (!processing) flushNotifications();
    }

    private void removeChunk(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        unloaded.add(key);
        pendingLoads.remove(key);
        if (scan != null && scan.chunk == chunk) scan = null;
        cancelDiscovery();
        LongSet positions = chunks.remove(key);
        if (positions != null) for (long position : positions) {
            Network network = membership.remove(position);
            invalidate(network);
            BlockPos pos = nodes.get(position).pos;
            for (Direction direction : DIRECTIONS) {
                BlockPos neighbor = pos.relative(direction);
                if (new ChunkPos(neighbor).toLong() == key) continue;
                long next = key(neighbor, nodes.get(position).color);
                if (conductive(next)) seeds.add(next);
            }
            Set<Observation> watching = observers.get(position);
            if (watching != null) for (Observation observer : watching) { observer.attach(null); observer.queue(NetworkState.DISCONNECTED); }
        }
        for (BlockPos pos : chunk.getBlockEntities().keySet()) sourceChanged(pos);
    }

    // Only outstanding load/topology jobs spend this budget; stable networks never propagate on ticks.
    void processPending() {
        processPending(WORK_BUDGET);
    }

    void processPending(int budget) {
        if (processing || scan == null && pendingLoads.isEmpty() && discovery == null && seeds.isEmpty() && notifications.isEmpty()) return;
        processing = true;
        try {
            for (int work = 0; work < budget; work++) {
                if (scan == null && !pendingLoads.isEmpty()) {
                    LevelChunk next = pendingLoads.get(pendingLoads.firstLongKey());
                    if (level.getChunkSource().getChunkNow(next.getPos().x, next.getPos().z) == null) break;
                    scan = new ChunkScan(pendingLoads.removeFirst());
                }
                if (scan != null) {
                    if (scan.step()) scan = null;
                    continue;
                }
                if (discovery == null) {
                    if (seeds.isEmpty()) break;
                    long seed = seeds.removeFirstLong();
                    Network known = membership.get(seed);
                    if ((known != null && known.valid) || !conductive(seed)) continue;
                    discovery = new Discovery(seed);
                }
                discovery.step();
                if (discovery.queue.isEmpty()) {
                    Network network = discovery.network;
                    network.valid = true;
                    for (SourcePort source : network.sources) sources.computeIfAbsent(source.position, ignored -> new HashSet<>()).add(network);
                    refresh(network);
                    for (Observation observer : network.observers) observer.queue(network.state());
                    discovery = null;
                }
            }
            flushNotifications();
        } finally {
            processing = false;
        }
    }

    private void cancelDiscovery() {
        if (discovery == null) return;
        seeds.add(discovery.seed);
        invalidate(discovery.network);
        discovery = null;
    }

    private void invalidate(Network network) {
        if (network == null || network.invalidated) return;
        network.invalidated = true;
        network.valid = false;
        seeds.addAll(network.members);
        for (SourcePort source : network.sources) {
            Set<Network> attached = sources.get(source.position);
            if (attached != null && attached.remove(network) && attached.isEmpty()) sources.remove(source.position);
        }
        for (Observation observer : network.observers) observer.queue(NetworkState.DISCONNECTED);
    }

    private void refresh(Network network) {
        if (!network.valid) return;
        int signals = 0; network.activeSources = 0;
        for (SourcePort port : network.sources) {
            BlockPos pos = BlockPos.of(port.position);
            LevelChunk chunk = loadedPosition(pos);
            if (chunk == null || !(chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) instanceof ControlBoxBlockEntity box)) continue;
            if (box.canConnectCable(port.side)) network.activeSources++;
            ControlChannel channel = network.color.channel;
            signals = channel.write(signals, Math.max(channel.read(signals), box.getSignal(channel, port.side)));
        }
        if (signals == network.signals) return;
        network.signals = signals;
        for (Observation observer : network.observers) observer.queue(network.state());
    }

    private void flushNotifications() {
        boolean previous = processing;
        processing = true;
        try {
            var batch = notifications.toArray(Observation[]::new);
            notifications.clear();
            for (Observation observer : batch) {
                if (observer.closed || observer.pending.equals(observer.last)) continue;
                observer.last = observer.pending;
                try { observer.listener.accept(observer.last); }
                catch (RuntimeException exception) { LogUtils.getLogger().error("Cable network listener failed", exception); }
            }
        } finally { processing = previous; }
    }

    private long chunkKey(long pos) {
        return new ChunkPos(nodes.get(pos).pos).toLong();
    }

    private LevelChunk loadedChunk(long pos) {
        return nodes.containsKey(pos) ? loadedPosition(nodes.get(pos).pos) : null;
    }
    private LevelChunk loadedPosition(BlockPos pos) {
        if (unloaded.contains(new ChunkPos(pos).toLong())) return null;
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private BlockState state(long pos) {
        LevelChunk chunk = loadedChunk(pos);
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(nodes.get(pos).pos);
    }

    private void removeMember(long pos) {
        if (pos == 0 || !nodes.containsKey(pos)) return;
        var node = nodes.get(pos); membership.remove(pos);
        LongSet index = chunks.get(chunkKey(pos));
        if (index != null && index.remove(pos) && index.isEmpty()) chunks.remove(chunkKey(pos));
        if (node.span != null) {
            long peerChunk = new ChunkPos(node.span.to()).toLong(); var peerIndex = chunks.get(peerChunk);
            if (peerIndex != null && peerIndex.remove(pos) && peerIndex.isEmpty()) chunks.remove(peerChunk);
        }
        if (!observers.containsKey(pos) && !conductive(pos)) { nodeIds.remove(node); nodes.remove(pos); }
    }

    private record SourcePort(long position, Direction side) {}

    private final class Network {
        final IndividualCableColor color;
        final LongSet members = new LongOpenHashSet();
        Network(IndividualCableColor color) { this.color = color; }
        final long id = nextId++;
        final Set<SourcePort> sources = new HashSet<>();
        final Set<Observation> observers = new HashSet<>();
        boolean valid;
        boolean hasTray;
        int activeSources;
        boolean invalidated;
        int signals;
        NetworkState snapshot;

        NetworkState state() {
            if (snapshot == null || snapshot.signals != signals) snapshot = new NetworkState(id, signals);
            return snapshot;
        }
    }

    private final class Discovery {
        final long seed;
        final Network network;
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        Discovery(long seed) { this.seed = seed; network = new Network(nodes.get(seed).color); add(seed); }

        void add(long pos) {
            if (!conductive(pos) || membership.get(pos) == network) return;
            Network previous = membership.put(pos, network);
            if (previous != network) invalidate(previous);
            chunks.computeIfAbsent(chunkKey(pos), ignored -> new LongOpenHashSet()).add(pos);
            network.members.add(pos);
            if (nodes.get(pos).span != null) chunks.computeIfAbsent(new ChunkPos(nodes.get(pos).span.to()).toLong(), ignored -> new LongOpenHashSet()).add(pos);
            Set<Observation> watching = observers.get(pos);
            if (watching != null) for (Observation observer : watching) observer.attach(network);
            queue.enqueue(pos);
        }

        void step() {
            long key = queue.dequeueLong();
            Node node = nodes.get(key);
            BlockPos pos = node.pos;
            if (node.span != null) { endpoint(node.span.start()); endpoint(node.span.end()); return; }
            BlockState tray = state(key);
            if (tray.getBlock() instanceof CableTrayBlock) network.hasTray = true;
            if (tray.getBlock() instanceof IndividualCableBlock) {
                for (var link : IndividualCableTopology.links(level, pos, node.color))
                    if (!link.span()) add(CableNetworkManager.this.key(link.peer(), node.color));
                for (var route : IndividualCableComponents.routes(level, pos, tray, node.color))
                    for (var contact : CablePhysicalConnections.contacts(level, pos, tray, node.color, route)) {
                        if (contact.port() == 0) add(CableNetworkManager.this.key(contact.peer(), node.color));
                        else if (loadedPosition(contact.peer()) != null) network.sources.add(new SourcePort(contact.peer().asLong(), ControlBoxPorts.side(contact.port())));
                    }
            } else
            for (Direction direction : DIRECTIONS) {
                BlockPos neighbor = pos.relative(direction);
                long next = CableNetworkManager.this.key(neighbor, node.color);
                BlockState adjacent = state(next);
                if (adjacent.getBlock() instanceof IndividualCableBlock)
                    for (var route : IndividualCableComponents.routes(level, neighbor, adjacent, node.color))
                        if (CablePhysicalConnections.contacts(level, neighbor, adjacent, node.color, route).stream().anyMatch(c -> c.peer().equals(pos))) add(next);
                if (!CableConnections.canConnect(tray, adjacent, direction)) continue;
                if (adjacent.getBlock() instanceof CableTrayBlock) {
                    if (membership.get(next) != network) add(next);
                } else if (adjacent.getBlock() instanceof ControlBoxBlock)
                    network.sources.add(new SourcePort(neighbor.asLong(), direction.getOpposite()));
            }
            for (var span : CableSpans.get(level).at(pos))
                if (span.color() == node.color) add(CableNetworkManager.this.key(new Node(span.from(), span.color(), span)));
        }
        void endpoint(CableEndpoint endpoint) {
            if (loadedPosition(endpoint.pos()) == null || !endpoint.exists(level, network.color)) return;
            if (endpoint.isPort()) network.sources.add(new SourcePort(endpoint.pos().asLong(), ControlBoxPorts.side(endpoint.port())));
            else add(CableNetworkManager.this.key(endpoint.pos(), network.color));
        }
    }

    private final class ChunkScan {
        final LevelChunk chunk;
        int section;
        int cell;

        ChunkScan(LevelChunk chunk) { this.chunk = chunk; }

        boolean step() {
            if (unloaded.contains(chunk.getPos().toLong())) return true;
            LevelChunkSection[] sections = chunk.getSections();
            if (section >= sections.length) {
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (chunk.getBlockState(pos).getBlock() instanceof ControlBoxBlock) topologyChanged(pos, Blocks.AIR.defaultBlockState());
                }
                return true;
            }
            LevelChunkSection current = sections[section];
            if (cell == 0 && !current.maybeHas(state -> state.getBlock() instanceof CableTrayBlock || state.getBlock() instanceof IndividualCableBlock)) { section++; return false; }
            int x = cell & 15, z = cell >> 4 & 15, y = cell >> 8;
            if (current.getBlockState(x, y, z).getBlock() instanceof CableTrayBlock || current.getBlockState(x, y, z).getBlock() instanceof IndividualCableBlock) {
                BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, chunk.getMinBuildHeight() + section * 16 + y, chunk.getPos().getMinBlockZ() + z);
                CableSpans.get(level).validate(pos);
                CableSpans.get(level).validate(pos);
                topologyChanged(pos, Blocks.AIR.defaultBlockState());
            }
            if (++cell == 4096) { cell = 0; section++; }
            return false;
        }
    }
}
