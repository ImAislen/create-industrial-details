package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.cake.struts.content.CableStrutInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class CableSpanGeometry {
    private CableSpanGeometry() {}
    public static CableStrutInfo sag(IndividualCableColor color, double length) {
        return new CableStrutInfo(Math.min(.09f, .035f + (float) (color.ordinal() * .125 / Math.max(length, .1))));
    }
    public static Vec3 from(CableSpans.Span span) {
        return span.start().point(span.color(), span.to()).add(span.fromOffset());
    }
    public static Vec3 to(CableSpans.Span span) {
        return span.end().point(span.color(), span.from()).add(span.toOffset());
    }
    public static List<Vec3> points(CableSpans.Span span) {
        var from = from(span); var to = to(span);
        List<Vec3> result = List.of();
        // A short tangent lead seals the rectangular anchor joint; only enclosure clearance may extend a port lead.
        for (double lead = .25; lead <= 1; lead += .125) {
            result = joined(from, to, span.color(), span.start().outward(span.to()), span.end().outward(span.from()),
                    span.start().isPort() ? lead : .125, span.end().isPort() ? lead : .125);
            if (outsideEnclosures(span, result)) break;
        }
        return result;
    }
    public static List<Vec3> preview(net.minecraft.world.level.LevelReader level, CableEndpoint from, IndividualCableColor color, net.minecraft.core.BlockPos peer, Vec3 to) {
        return joined(from.point(level, color, peer), to, color, from.outward(peer), null, from.isPort() ? .25 : .125, 0);
    }
    private static List<Vec3> joined(Vec3 from, Vec3 to, IndividualCableColor color, net.minecraft.core.Direction fromAxis, net.minecraft.core.Direction toAxis, double fromLead, double toLead) {
        Vec3 a = fromAxis == null ? from : from.add(Vec3.atLowerCornerOf(fromAxis.getNormal()).scale(fromLead));
        Vec3 b = toAxis == null ? to : to.add(Vec3.atLowerCornerOf(toAxis.getNormal()).scale(toLead));
        var result = new ArrayList<Vec3>(); if (fromLead != 0) result.add(from);
        result.addAll(points(a, b, color)); if (toLead != 0) result.add(to); return List.copyOf(result);
    }
    public static boolean clearEnclosures(CableSpans.Span span) {
        return outsideEnclosures(span, points(span));
    }
    private static boolean outsideEnclosures(CableSpans.Span span, List<Vec3> points) {
        if (span.fromPort() == 0 && span.toPort() == 0) return true;
        for (var endpoint : List.of(span.start(), span.end())) {
            if (!endpoint.isPort()) continue;
            var facing = com.aislen.createindustrialdetails.content.block.cable.ControlBoxPorts.facing(endpoint.port());
            var a = com.aislen.createindustrialdetails.content.block.cable.ControlBoxGeometry.fromModel(facing, new Vec3(2.0 / 16, 0, 0)).add(Vec3.atLowerCornerOf(endpoint.pos()));
            var b = com.aislen.createindustrialdetails.content.block.cable.ControlBoxGeometry.fromModel(facing, new Vec3(14.0 / 16, 1, 4.0 / 16)).add(Vec3.atLowerCornerOf(endpoint.pos()));
            var body = new AABB(a, b).deflate(1e-6);
            for (int i = 1; i < points.size(); i++) if (body.contains(points.get(i - 1)) || body.clip(points.get(i - 1), points.get(i)).isPresent()) return false;
        }
        return true;
    }
    public static List<Vec3> points(Vec3 from, Vec3 to, IndividualCableColor color) {
        // Strut's public sampler is client-only. Match its parabolic sampling on the server too.
        Vec3 delta = to.subtract(from); double length = delta.length();
        if (length < 1e-6) return List.of(from, to);
        var info = sag(color, length);
        double step = Math.min(1, Math.max(info.minStep(), length / info.maxSegments()) / length);
        var result = new ArrayList<Vec3>();
        for (double t = 0; t < 1; t += step)
            result.add(from.add(delta.scale(t)).add(0, -info.sag() * length * 4 * t * (1 - t), 0));
        result.add(to); return List.copyOf(result);
    }
    public record Segment(Vec3 center, Vec3 tangent, Vec3 right, Vec3 up, double halfLength) {
        static Segment between(Vec3 a, Vec3 b, double roll) {
            Vec3 tangent = b.subtract(a).normalize();
            Vec3 right = new Vec3(tangent.z, 0, -tangent.x);
            if (right.lengthSqr() < 1e-10) right = new Vec3(1, 0, 0); else right = right.normalize();
            var up = tangent.cross(right);
            return new Segment(a.add(b).scale(.5), tangent, right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll))),
                    up.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll))), a.distanceTo(b) / 2);
        }
        double intersect(Vec3 from, Vec3 delta) {
            Vec3 local = from.subtract(center); double near = 0, far = 1;
            for (int i = 0; i < 3; i++) {
                Vec3 axis = i == 0 ? right : i == 1 ? up : tangent;
                double extent = i == 0 ? .75 / 16 + .006 : i == 1 ? .5 / 16 + .006 : halfLength + .002;
                double origin = local.dot(axis), speed = delta.dot(axis);
                if (Math.abs(speed) < 1e-10) { if (Math.abs(origin) > extent) return Double.NaN; continue; }
                double a = (-extent - origin) / speed, b = (extent - origin) / speed;
                near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
                if (near > far) return Double.NaN;
            }
            return near;
        }
    }
    public record Path(List<Vec3> points, List<Segment> segments, AABB bounds) {
        public static Path of(CableSpans.Span span) {
            var points = CableSpanGeometry.points(span); var segments = new ArrayList<Segment>();
            AABB bounds = new AABB(points.getFirst(), points.getFirst());
            for (int i = 1; i < points.size(); i++) {
                bounds = bounds.minmax(new AABB(points.get(i), points.get(i)));
                double roll = span.start().roll() + (span.end().roll() - span.start().roll()) * (i - 1) / Math.max(1, points.size() - 2);
                if (points.get(i).distanceToSqr(points.get(i - 1)) > 1e-12) segments.add(Segment.between(points.get(i - 1), points.get(i), roll));
            }
            return new Path(points, List.copyOf(segments), bounds.inflate(.065));
        }
        public Vec3 intersect(Vec3 from, Vec3 to) {
            if (!bounds.contains(from) && bounds.clip(from, to).isEmpty()) return null;
            Vec3 delta = to.subtract(from); double nearest = Double.POSITIVE_INFINITY;
            for (var segment : segments) { double t = segment.intersect(from, delta); if (!Double.isNaN(t)) nearest = Math.min(nearest, t); }
            return Double.isFinite(nearest) ? from.add(delta.scale(nearest)) : null;
        }
    }
}
