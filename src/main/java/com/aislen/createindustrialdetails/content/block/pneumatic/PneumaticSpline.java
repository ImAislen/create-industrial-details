package com.aislen.createindustrialdetails.content.block.pneumatic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class PneumaticSpline {
    public static final double MAX_SPAN = 96;
    public static final double RADIUS = .25;
    private static final double MAX_LENGTH = MAX_SPAN * 1.5;
    private static final double MIN_DISTANCE = 1e-6;
    // Improve the sweep where possible. The profile radius is a handle-selection target only.
    private static final double PROFILE_CORNER = RADIUS * Math.sqrt(2);
    private static final double[] HANDLE_SCALES = {1.4, 1.8, 2.2, .75, .5, .25};

    private final Vec3 origin;
    private final Cubic cubic;
    private final double[] distances;
    private final boolean aligned;
    private final boolean tooLong;
    public final AABB bounds;
    public final Set<ChunkPos> chunks;

    public record Port(BlockPos pos, Direction face, PneumaticStationBlock.Kind kind) {
        public Port {
            pos = pos.immutable();
        }

        public Vec3 center() {
            Vec3 center = Vec3.atCenterOf(kind.tall ? pos.above() : pos);
            return center.add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        }

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Face", face.getName());
            tag.putString("Kind", kind.name());
            return tag;
        }

        public static Port load(CompoundTag tag) {
            var face = Direction.byName(tag.getString("Face"));
            if (face == null) throw new IllegalArgumentException("Invalid pneumatic port face");
            return new Port(BlockPos.of(tag.getLong("Pos")), face, PneumaticStationBlock.Kind.valueOf(tag.getString("Kind")));
        }
    }

    public PneumaticSpline(Port from, Port to) {
        this(from.center(), to.center(), from.face(), to.face(), from.pos().compareTo(to.pos()) > 0);
    }

    private PneumaticSpline(Vec3 origin, Vec3 destination, @Nullable Direction from, @Nullable Direction to, boolean reverse) {
        this.origin = origin;
        Vec3 end = destination.subtract(origin);
        double chord = end.length();
        if (!Double.isFinite(chord) || chord < MIN_DISTANCE)
            throw new IllegalArgumentException("Pneumatic endpoints overlap");

        int samples = Math.max(16, (int) Math.ceil(Math.min(chord, MAX_SPAN) * 8));
        // Use the same handle selection in both travel directions, including on save/load.
        Cubic curve = from == null || to == null ? null : reverse
                ? chooseCurve(end.scale(-1), chord, to, from, samples)
                : chooseCurve(end, chord, from, to, samples);
        aligned = curve != null;
        cubic = aligned ? (reverse ? curve.reversed() : curve) : new Cubic(end.scale(1d / 3), end.scale(2d / 3), end);
        distances = new double[samples + 1];
        var previous = Vec3.ZERO;
        AABB box = new AABB(origin, origin);
        for (int i = 1; i <= samples; i++) {
            Vec3 point = cubic.point((double) i / samples);
            distances[i] = distances[i - 1] + previous.distanceTo(point);
            Vec3 world = origin.add(point);
            box = box.minmax(new AABB(world, world));
            previous = point;
        }
        tooLong = chord > MAX_SPAN || length() > MAX_LENGTH;
        bounds = box.inflate(RADIUS);
        // Even a vastly over-length preview has bounded work and never builds a chunk index.
        chunks = valid() ? findChunks(samples) : Set.of();
    }

    @Nullable
    public static PneumaticSpline preview(Vec3 from, Vec3 to) {
        double distance = from.distanceToSqr(to);
        return Double.isFinite(distance) && distance >= MIN_DISTANCE * MIN_DISTANCE
                ? new PneumaticSpline(from, to, null, null, false) : null;
    }

    private Set<ChunkPos> findChunks(int samples) {
        var touched = new HashSet<ChunkPos>();
        Vec3 previous = origin;
        for (int i = 0; i <= samples; i++) {
            Vec3 world = origin.add(cubic.point((double) i / samples));
            AABB piece = new AABB(previous, world).inflate(RADIUS + .01);
            for (int x = floorChunk(piece.minX); x <= floorChunk(piece.maxX); x++)
                for (int z = floorChunk(piece.minZ); z <= floorChunk(piece.maxZ); z++) touched.add(new ChunkPos(x, z));
            previous = world;
        }
        return Set.copyOf(touched);
    }

    @Nullable
    private static Cubic chooseCurve(Vec3 end, double chord, Direction from, Direction to, int samples) {
        var forward = Vec3.atLowerCornerOf(from.getNormal());
        var backward = Vec3.atLowerCornerOf(to.getNormal());
        double handle = Math.min(chord, MAX_SPAN) / 3;
        Cubic original = controls(end, forward, backward, handle, handle);
        double bestRadius = original.radiusScore(from.getAxis(), chord);
        if (bestRadius >= PROFILE_CORNER || chord > MAX_SPAN && bestRadius >= 0) return original;

        Cubic best = bestRadius >= 0 ? original : null;
        for (double scale : HANDLE_SCALES) {
            double a = handle * scale;
            double b = a;
            // A tangent directed past its endpoint needs less reach while tightening a turn.
            if (scale < 1) {
                if (forward.dot(end) < 0) a *= .5;
                if (backward.dot(end) > 0) b *= .5;
            }
            Cubic candidate = controls(end, forward, backward, a, b);
            double score = candidate.radiusScore(from.getAxis(), chord);
            if (score <= bestRadius + 1e-8 || candidate.sampleLength(samples) > MAX_LENGTH) continue;
            best = candidate;
            bestRadius = score;
            if (score >= PROFILE_CORNER) break;
        }
        return best;
    }

    private static Cubic controls(Vec3 end, Vec3 forward, Vec3 backward, double a, double b) {
        return new Cubic(forward.scale(a), end.add(backward.scale(b)), end);
    }

    private record Cubic(Vec3 a, Vec3 b, Vec3 end) {
        Vec3 point(double t) {
            double s = 1 - t;
            return a.scale(3 * s * s * t).add(b.scale(3 * s * t * t)).add(end.scale(t * t * t));
        }

        Vec3 derivative(double t) {
            double s = 1 - t;
            return a.scale(3 * s * s).add(b.subtract(a).scale(6 * s * t))
                    .add(end.subtract(b).scale(3 * t * t));
        }

        Cubic reversed() {
            return new Cubic(b.subtract(end), a.subtract(end), end.scale(-1));
        }

        double sampleLength(int samples) {
            Vec3 last = Vec3.ZERO;
            double length = 0;
            for (int i = 1; i <= samples; i++) {
                Vec3 point = point((double) i / samples);
                length += point.distanceTo(last);
                last = point;
            }
            return length;
        }

        double radiusScore(Direction.Axis axis, double chord) {
            // Power-basis coefficients for checking stationary points and centerline loops.
            Vec3 third = end.subtract(b.scale(3)).add(a.scale(3));
            Vec3 second = b.scale(3).subtract(a.scale(6));
            Vec3 first = a.scale(3);
            double qa = 3 * axis.choose(third.x, third.y, third.z);
            double qb = 2 * axis.choose(second.x, second.y, second.z);
            double qc = axis.choose(first.x, first.y, first.z);
            double epsilon = Math.max(1e-12, chord * 1e-10);
            if (stationary(qa, qb, qc, epsilon) || loops(third, second, first, epsilon)) return -1;

            double radius = Double.POSITIVE_INFINITY;
            for (int i = 0; i <= 32; i++) {
                double t = i / 32d;
                Vec3 velocity = derivative(t);
                Vec3 acceleration = third.scale(6 * t).add(second.scale(2));
                double speed = velocity.length();
                double bend = velocity.cross(acceleration).length();
                if (bend > 0) radius = Math.min(radius, speed * speed * speed / bend);
            }
            return radius;
        }

        private boolean stationary(double a, double b, double c, double epsilon) {
            if (Math.abs(a) < epsilon) {
                return Math.abs(b) >= epsilon && stopsAt(-c / b, epsilon);
            }
            double discriminant = b * b - 4 * a * c;
            if (discriminant < 0) return false;
            double q = -.5 * (b + Math.copySign(Math.sqrt(discriminant), b));
            return q == 0 ? stopsAt(-b / (2 * a), epsilon)
                    : stopsAt(q / a, epsilon) || stopsAt(c / q, epsilon);
        }

        private boolean stopsAt(double t, double epsilon) {
            return t > 0 && t < 1 && derivative(t).lengthSqr() <= epsilon * epsilon;
        }

        private static boolean loops(Vec3 a, Vec3 b, Vec3 c, double epsilon) {
            // Remove (t-s) from P(t)-P(s), then solve for t+s and t^2+ts+s^2.
            Vec3 plane = a.cross(b);
            double area = plane.lengthSqr();
            if (area <= epsilon * epsilon) return false;
            double sum = -a.cross(c).dot(plane) / area;
            Vec3 remainder = b.scale(sum).add(c);
            double squares = -remainder.dot(a) / a.lengthSqr();
            if (remainder.add(a.scale(squares)).lengthSqr() > epsilon * epsilon) return false;
            double discriminant = 4 * squares - 3 * sum * sum;
            if (discriminant <= 0) return false;
            double root = Math.sqrt(discriminant);
            return sum >= root && sum + root <= 2;
        }
    }

    private static int floorChunk(double coordinate) {
        return (int) Math.floor(coordinate / 16);
    }

    public boolean valid() { return aligned && !tooLong; }

    public boolean tooLong() { return tooLong; }

    public double length() {
        return distances[distances.length - 1];
    }

    public int cost() {
        return Math.max(1, (int) Math.ceil(length() - 1e-8));
    }

    public Vec3 at(double distance) {
        return origin.add(cubic.point(parameter(distance)));
    }

    public Vec3 tangent(double distance) {
        Vec3 derivative = cubic.derivative(parameter(distance));
        return derivative.scale(1 / derivative.length());
    }

    private double parameter(double distance) {
        if (distance <= 0) return 0;
        if (distance >= length()) return 1;
        int index = Arrays.binarySearch(distances, distance);
        if (index >= 0) return (double) index / (distances.length - 1);
        int next = -index - 1;
        double fraction = (distance - distances[next - 1]) / (distances[next] - distances[next - 1]);
        return (next - 1 + fraction) / (distances.length - 1);
    }
}
