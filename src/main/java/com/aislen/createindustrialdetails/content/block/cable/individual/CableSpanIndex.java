package com.aislen.createindustrialdetails.content.block.cable.individual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class CableSpanIndex {
    private static final Map<Level, CableSpanIndex> CLIENT = new WeakHashMap<>();
    private final Map<CableSpans.Span, AABB> bounds = new HashMap<>();
    private final Map<CableSpans.Span, CableSpanGeometry.Path> paths = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<CableSpans.Span, CableSpanGeometry.Path> entry) { return size() > 256; }
    };
    private final Map<Long, Set<CableSpans.Span>> sections = new HashMap<>();
    private final Map<BlockPos, List<CableSpans.Span>> owners = new HashMap<>();
    private final Map<CableSpans.Span, Integer> references = new HashMap<>();
    private final Map<CableSpans.Span.Key, Integer> keys = new HashMap<>();
    public static CableSpanIndex client(Level level) { return CLIENT.computeIfAbsent(level, key -> new CableSpanIndex()); }
    public boolean contains(CableSpans.Span span) { return keys.containsKey(span.key()); }
    public boolean containsRecord(CableSpans.Span span) { return bounds.containsKey(span); }
    public AABB bounds(CableSpans.Span span) { return bounds.get(span); }
    public void replace(BlockPos owner, List<CableSpans.Span> spans) {
        var old = owners.put(owner.immutable(), List.copyOf(spans));
        if (old != null) for (var span : old) {
            if (spans.contains(span)) continue;
            int count = references.getOrDefault(span, 1) - 1;
            if (count == 0) { references.remove(span); remove(span); } else references.put(span, count);
        }
        for (var span : spans) if (old == null || !old.contains(span)) { references.merge(span, 1, Integer::sum); add(span); }
    }
    public void forget(BlockPos owner) { replace(owner, List.of()); owners.remove(owner); }
    public void add(CableSpans.Span span) {
        if (bounds.containsKey(span)) return;
        keys.merge(span.key(), 1, Integer::sum);
        var path = CableSpanGeometry.Path.of(span); var box = path.bounds(); paths.put(span, path);
        bounds.put(span, box);
        eachSection(box, section -> sections.computeIfAbsent(section, key -> new HashSet<>()).add(span));
    }
    public void remove(CableSpans.Span span) {
        var box = bounds.remove(span); paths.remove(span); if (box == null) return;
        keys.computeIfPresent(span.key(), (key, count) -> count == 1 ? null : count - 1);
        eachSection(box, section -> { var set = sections.get(section); if (set != null && set.remove(span) && set.isEmpty()) sections.remove(section); });
    }
    public record Hit(CableSpans.Span span, Vec3 point) {}
    public Set<CableSpans.Span> nearby(Vec3 point, double radius) {
        var candidates = new HashSet<CableSpans.Span>();
        if (sections.isEmpty()) return candidates;
        eachSection(new AABB(point, point).inflate(radius), section -> candidates.addAll(sections.getOrDefault(section, Set.of())));
        return candidates;
    }
    public Hit trace(Vec3 from, Vec3 to) {
        if (sections.isEmpty()) return null;
        var candidates = new HashSet<CableSpans.Span>();
        eachSection(new AABB(from, to).inflate(.065), section -> candidates.addAll(sections.getOrDefault(section, Set.of())));
        Hit result = null; double nearest = from.distanceToSqr(to) + 1e-8;
        for (var span : candidates) {
            Vec3 point = paths.computeIfAbsent(span, CableSpanGeometry.Path::of).intersect(from, to);
            if (point == null) continue;
            double distance = point.distanceToSqr(from);
            if (distance < nearest - 1e-8 || Math.abs(distance - nearest) <= 1e-8 && (result == null || compare(span, result.span()) < 0)) {
                nearest = distance; result = new Hit(span, point);
            }
        }
        return result;
    }
    private static int compare(CableSpans.Span a, CableSpans.Span b) {
        int order = Integer.compare(a.color().ordinal(), b.color().ordinal());
        if (order == 0) order = a.from().compareTo(b.from());
        if (order == 0) order = a.to().compareTo(b.to());
        if (order == 0) order = Integer.compare(a.fromRoute().ordinal(), b.fromRoute().ordinal());
        if (order == 0) order = Integer.compare(a.toRoute().ordinal(), b.toRoute().ordinal());
        if (order == 0) order = Integer.compare(a.fromPort(), b.fromPort());
        return order == 0 ? Integer.compare(a.toPort(), b.toPort()) : order;
    }
    private static void eachSection(AABB box, java.util.function.LongConsumer consumer) {
        for (int x = SectionPos.blockToSectionCoord(box.minX); x <= SectionPos.blockToSectionCoord(box.maxX); x++)
            for (int y = SectionPos.blockToSectionCoord(box.minY); y <= SectionPos.blockToSectionCoord(box.maxY); y++)
                for (int z = SectionPos.blockToSectionCoord(box.minZ); z <= SectionPos.blockToSectionCoord(box.maxZ); z++) consumer.accept(SectionPos.asLong(x, y, z));
    }
}
