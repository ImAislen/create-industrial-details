package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.cake.struts.content.block.StrutBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class CableSpans extends SavedData {
    private final Map<BlockPos, Set<Span>> byEndpoint = new HashMap<>();
    private final Set<Span> all = new HashSet<>();
    private final Map<net.minecraft.world.level.ChunkPos, Set<Span>> trayChunks = new HashMap<>();
    public List<Span> traySpans(net.minecraft.world.level.ChunkPos chunk) { return List.copyOf(trayChunks.getOrDefault(chunk, Set.of())); }
    private ServerLevel level;
    private final CableSpanIndex spatial = new CableSpanIndex();
    public CableSpanIndex spatial() { return spatial; }

    public record Span(IndividualCableColor color, BlockPos from, BlockPos to, CableRoute fromRoute, CableRoute toRoute, int cost, int fromPort, int toPort, Vec3 fromOffset, Vec3 toOffset) {
        public Span(IndividualCableColor color, BlockPos from, BlockPos to, CableRoute fromRoute, CableRoute toRoute, int cost, int fromPort, int toPort) { this(color, from, to, fromRoute, toRoute, cost, fromPort, toPort, Vec3.ZERO, Vec3.ZERO); }
        public Span(IndividualCableColor color, BlockPos from, BlockPos to, CableRoute fromRoute, CableRoute toRoute, int cost) { this(color, from, to, fromRoute, toRoute, cost, 0, 0); }
        public record Key(IndividualCableColor color, BlockPos from, BlockPos to, CableRoute fromRoute, CableRoute toRoute, int fromPort, int toPort) {}
        public Key key() { return new Key(color, from, to, fromRoute, toRoute, fromPort, toPort); }
        public CableEndpoint start() { return new CableEndpoint(from, fromRoute, fromPort); }
        public CableEndpoint end() { return new CableEndpoint(to, toRoute, toPort); }
        public static Span between(IndividualCableColor color, CableEndpoint a, CableEndpoint b, int cost) {
            var first = a.pos().compareTo(b.pos()) < 0 ? a : b; var second = first == a ? b : a;
            return new Span(color, first.pos(), second.pos(), first.route(), second.route(), cost, first.port(), second.port());
        }
        public static Span between(net.minecraft.world.level.LevelReader level, IndividualCableColor color, CableEndpoint a, CableEndpoint b, int cost) {
            return between(color, a, b, cost).resolved(level);
        }
        public Span resolved(net.minecraft.world.level.LevelReader level) {
            return new Span(color, from, to, fromRoute, toRoute, cost, fromPort, toPort,
                    start().point(level, color, to).subtract(start().point(color, to)), end().point(level, color, from).subtract(end().point(color, from)));
        }
        public BlockPos peer(BlockPos pos) { return pos.equals(from) ? to : from; }
        public CompoundTag write() {
            var tag = new CompoundTag(); tag.putByte("Color", (byte) color.ordinal());
            tag.putLong("From", from.asLong()); tag.putLong("To", to.asLong());
            tag.putByte("FromRoute", (byte) fromRoute.ordinal()); tag.putByte("ToRoute", (byte) toRoute.ordinal()); tag.putInt("Cost", cost);
            if (fromPort != 0) { tag.putString("FromType", start().isTray() ? "tray_lane" : "control_box_port"); tag.putInt("FromPort", fromPort); }
            if (toPort != 0) { tag.putString("ToType", end().isTray() ? "tray_lane" : "control_box_port"); tag.putInt("ToPort", toPort); }
            writeOffset(tag, "FromOffset", fromOffset); writeOffset(tag, "ToOffset", toOffset);
            return tag;
        }
        private static void writeOffset(CompoundTag tag, String key, Vec3 point) {
            if (point.equals(Vec3.ZERO)) return;
            var offset = new CompoundTag(); offset.putDouble("X", point.x); offset.putDouble("Y", point.y); offset.putDouble("Z", point.z); tag.put(key, offset);
        }
        private static Vec3 readOffset(CompoundTag tag, String key) {
            var offset = tag.getCompound(key); var point = new Vec3(offset.getDouble("X"), offset.getDouble("Y"), offset.getDouble("Z"));
            return Double.isFinite(point.lengthSqr()) && point.lengthSqr() <= .25 * .25 ? point : Vec3.ZERO;
        }
        public static Span read(CompoundTag tag) {
            int c = tag.getByte("Color"), a = tag.getByte("FromRoute"), b = tag.getByte("ToRoute"), ap = tag.getInt("FromPort"), bp = tag.getInt("ToPort");
            if (c < 0 || c >= IndividualCableColor.values().length || a < 0 || a >= CableRoute.values().length || b < 0 || b >= CableRoute.values().length
                    || ap < 0 || ap > 48 || bp < 0 || bp > 48 || a == 0 && ap == 0 || b == 0 && bp == 0) return null;
            return new Span(IndividualCableColor.values()[c], BlockPos.of(tag.getLong("From")), BlockPos.of(tag.getLong("To")), CableRoute.values()[a], CableRoute.values()[b], Math.clamp(tag.getInt("Cost"), 1, StrutBlock.MAX_SPAN), ap, bp, readOffset(tag, "FromOffset"), readOffset(tag, "ToOffset"));
        }
    }
    public static CableSpans get(ServerLevel level) {
        CableSpans spans = level.getDataStorage().computeIfAbsent(new Factory<>(CableSpans::new, CableSpans::load), "cid_individual_cable_spans");
        spans.level = level; return spans;
    }
    public static CableSpans load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new CableSpans();
        for (Tag entry : tag.getList("Spans", Tag.TAG_COMPOUND)) { Span span = Span.read((CompoundTag) entry); if (span != null) data.index(span); }
        return data;
    }
    private void index(Span span) {
        if (!all.add(span)) return;
        spatial.add(span);
        byEndpoint.computeIfAbsent(span.from, p -> new HashSet<>()).add(span);
        byEndpoint.computeIfAbsent(span.to, p -> new HashSet<>()).add(span);
        for (var endpoint : List.of(span.start(), span.end())) if (endpoint.isTray())
            trayChunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(endpoint.pos()), p -> new HashSet<>()).add(span);
    }
    public List<Span> at(BlockPos pos) { return List.copyOf(byEndpoint.getOrDefault(pos, Set.of())); }
    public static IndividualCableBlockEntity endpoint(ServerLevel level, BlockPos pos, IndividualCableColor color) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) instanceof IndividualCableBlockEntity cable)) return null;
        return cable.getBlockState().getValue(color.property) == CableRoute.NONE ? null : cable;
    }
    public boolean connect(IndividualCableColor color, BlockPos a, BlockPos b, int paid) {
        var first = endpoint(level, a, color); var second = endpoint(level, b, color);
        if (first == null || second == null) return false;
        return connect(color, a, b, first.getBlockState().getValue(color.property), second.getBlockState().getValue(color.property), paid);
    }
    public boolean connect(IndividualCableColor color, BlockPos a, BlockPos b, CableRoute aRoute, CableRoute bRoute, int paid) {
        return connect(color, new CableEndpoint(a, aRoute, 0), new CableEndpoint(b, bRoute, 0), paid);
    }
    public boolean connect(IndividualCableColor color, CableEndpoint a, CableEndpoint b, int paid) {
        if (a.pos().equals(b.pos()) || a.pos().distSqr(b.pos()) > StrutBlock.MAX_SPAN * StrutBlock.MAX_SPAN
                || paid != Math.max(1, (int) Math.ceil(Math.sqrt(a.pos().distSqr(b.pos())))) || !a.exists(level, color) || !b.exists(level, color)) return false;
        var span = Span.between(level, color, a, b, paid); if (at(span.from()).stream().anyMatch(s -> s.key().equals(span.key())) || !CableSpanGeometry.clearEnclosures(span)) return false;
        index(span); setDirty(); update(a.pos()); update(b.pos());
        syncTrays(span);
        com.aislen.createindustrialdetails.content.block.cable.CableNetworks.get(level).spanChanged(span); return true;
    }
    public boolean remove(Span span) {
        if (!unindex(span)) return false;
        setDirty(); update(span.from); update(span.to); syncTrays(span); com.aislen.createindustrialdetails.content.block.cable.CableNetworks.get(level).spanChanged(span); return true;
    }
    private boolean unindex(Span span) {
        if (!all.remove(span)) return false;
        spatial.remove(span);
        for (BlockPos pos : List.of(span.from, span.to)) {
            var set = byEndpoint.get(pos); if (set != null && set.remove(span) && set.isEmpty()) byEndpoint.remove(pos);
        }
        for (var endpoint : List.of(span.start(), span.end())) if (endpoint.isTray()) {
            var key = new net.minecraft.world.level.ChunkPos(endpoint.pos()); var set = trayChunks.get(key);
            if (set != null && set.remove(span) && set.isEmpty()) trayChunks.remove(key);
        }
        return true;
    }
    public void refreshGeometry(BlockPos pos) {
        for (var span : at(pos)) {
            if (!span.start().exists(level, span.color) || !span.end().exists(level, span.color)) continue;
            var next = span.resolved(level); if (span.equals(next)) continue;
            unindex(span); index(next); setDirty(); update(next.from); update(next.to); syncTrays(next);
            com.aislen.createindustrialdetails.content.block.cable.CableNetworks.get(level).spanChanged(span);
        }
    }
    public List<Span> detach(BlockPos pos, IndividualCableColor color) {
        var removed = at(pos).stream().filter(s -> s.color == color).toList(); for (var span : removed) remove(span); return removed;
    }
    public List<Span> detach(BlockPos pos, IndividualCableColor color, CableRoute route) {
        var removed = at(pos).stream().filter(s -> s.color == color && (s.from.equals(pos) ? s.fromRoute : s.toRoute) == route).toList();
        for (var span : removed) remove(span); return removed;
    }
    private void update(BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) instanceof IndividualCableBlockEntity host) host.changed();
        else if (chunk != null && chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) instanceof com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlockEntity box) { box.setChanged(); level.sendBlockUpdated(pos, box.getBlockState(), box.getBlockState(), 2); }
    }
    private void syncTrays(Span span) {
        var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
        for (var end : List.of(span.start(), span.end())) if (end.isTray()) chunks.add(new net.minecraft.world.level.ChunkPos(end.pos()));
        for (var chunk : chunks) CableSpanSync.changed(level, chunk);
    }
    public void validate(BlockPos pos) {
        for (Span span : at(pos)) for (var end : List.of(span.start(), span.end())) {
            var chunk = level.getChunkSource().getChunkNow(end.pos().getX() >> 4, end.pos().getZ() >> 4);
            if (chunk == null) continue;
            if (end.isTray() && !level.hasChunkAt(end.pos().relative(com.aislen.createindustrialdetails.content.block.cable.CableTrayPorts.side(end.port())))) continue;
            if (!end.isPort() && chunk.getBlockState(end.pos()).getBlock() instanceof IndividualCableBlock
                    && chunk.getBlockEntity(end.pos(), net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) == null) continue;
            if (!end.exists(level, span.color)) {
                if (remove(span)) IndividualCableBlock.drop(level, pos, span.color, span.cost); break;
            }
        }
        refreshGeometry(pos);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag(); for (Span span : all) list.add(span.write()); tag.put("Spans", list); return tag;
    }
}
