package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.cake.struts.content.shape.SurfaceClippingHelper.SurfacePlane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.cake.struts.content.shape.SurfaceClippingHelper.clipPolygonToPlane;
import static com.cake.struts.content.shape.SurfaceClippingHelper.signedDistance;

/** The same square prism and support cuts are used for the mesh, selection and collision. */
public final class WoodenBeamGeometry {
    public static final double HALF_WIDTH = 7.99 / 32.0;
    private static final double EPSILON = 1.0e-8;

    private final Vec3 origin;
    private final Vec3 direction;
    private final Vec3 crossX;
    private final Vec3 crossY;
    private final double length;
    private final SurfacePlane fromPlane;
    private final SurfacePlane toPlane;
    private final List<Section> sections;
    private final List<Face> faces;

    public WoodenBeamGeometry(
            @Nullable BlockGetter level, BlockPos fromPos, BlockPos toPos, WoodenBeamConnectionEndpoints endpoints
    ) {
        var from = WoodenBeamEndpoints.geometryAttachment(level, fromPos, endpoints.localFace(), endpoints.localSnap());
        var to = WoodenBeamEndpoints.geometryAttachment(level, toPos, endpoints.peerFace(), endpoints.peerSnap());
        Vec3 span = to.center().subtract(from.center());
        double aimLength = span.length();
        direction = aimLength > EPSILON ? span.scale(1.0 / aimLength) : new Vec3(0, 0, 1);
        double horizontal = Math.hypot(direction.x, direction.z);
        crossX = horizontal > EPSILON ? new Vec3(direction.z / horizontal, 0, -direction.x / horizontal)
                : new Vec3(1, 0, 0);
        crossY = direction.cross(crossX);
        fromPlane = supportPlane(from, direction);
        toPlane = supportPlane(to, direction.reverse());
        origin = fromPlane.point();
        length = toPlane.point().subtract(origin).dot(direction);
        sections = aimLength > EPSILON && length > EPSILON
                ? buildSections(fromPos.relative(endpoints.localFace().getOpposite()),
                        toPos.relative(endpoints.peerFace().getOpposite()))
                : List.of();
        faces = buildExterior();
    }

    private static SurfacePlane supportPlane(WoodenBeamEndpoints.Attachment attachment, Vec3 towardBeam) {
        AABB post = attachment.postBounds();
        if (post == null) {
            return attachment.blockFace();
        }

        // The beam is aimed into the post. Use the actual face the centerline
        // exits through, which can be its top on a steep side attachment.
        SurfacePlane surface = attachment.blockFace();
        double distance = Double.POSITIVE_INFINITY;
        for (Direction face : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
            double speed = towardBeam.dot(normal);
            if (speed <= EPSILON) {
                continue;
            }
            double coordinate = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? post.max(face.getAxis()) : -post.min(face.getAxis());
            double exit = (coordinate - attachment.center().dot(normal)) / speed;
            if (exit < distance || (exit == distance && normal.equals(attachment.blockFace().normal()))) {
                distance = exit;
                surface = new SurfacePlane(attachment.center().add(towardBeam.scale(exit)), normal);
            }
        }
        return surface;
    }

    private List<Section> buildSections(BlockPos fromSupport, BlockPos toSupport) {
        List<Section> result = new ArrayList<>(3);
        addExtension(result, 0, fromPlane, fromSupport, direction.reverse());
        result.add(section(0, length, List.of(fromPlane, toPlane)));
        addExtension(result, length, toPlane, toSupport, direction);
        return List.copyOf(result);
    }

    private void addExtension(List<Section> sections, double seam, SurfacePlane support, BlockPos pos, Vec3 outward) {
        if (outward.dot(support.normal()) > EPSILON) {
            return;
        }
        AABB bounds = new AABB(pos);
        List<SurfacePlane> planes = new ArrayList<>(List.of(fromPlane, toPlane));
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (Math.abs(support.normal().get(axis)) < EPSILON) {
                Vec3 normal = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axis,
                        Direction.AxisDirection.POSITIVE).getNormal());
                planes.add(new SurfacePlane(normal.scale(bounds.min(axis)), normal));
                planes.add(new SurfacePlane(normal.scale(bounds.max(axis)), normal.reverse()));
            }
        }

        Vec3 endpoint = origin.add(direction.scale(seam));
        double reach = Double.POSITIVE_INFINITY;
        for (SurfacePlane plane : planes) {
            double speed = -outward.dot(plane.normal());
            if (speed > EPSILON) {
                // Furthest possible intersection of a longitudinal corner with this plane.
                double distance = signedDistance(endpoint, plane) + crossExtent(plane.normal());
                reach = Math.min(reach, distance / speed);
            }
        }
        if (!Double.isFinite(reach) || reach <= EPSILON) {
            return;
        }
        boolean first = seam == 0;
        Section extension = section(first ? -reach : seam, first ? seam : seam + reach, List.copyOf(planes));
        if (extension.faces().stream().flatMap(face -> face.vertices().stream())
                .anyMatch(vertex -> vertex.subtract(endpoint).dot(outward) > EPSILON)) {
            sections.add(extension);
        }
    }

    private Section section(double start, double end, List<SurfacePlane> planes) {
        List<Vec3> a = corners(origin.add(direction.scale(start)));
        List<Vec3> b = corners(origin.add(direction.scale(end)));
        List<Face> faces = List.of(
                new Face(List.of(a.get(3), a.get(2), a.get(1), a.get(0)), direction.reverse(), true),
                new Face(b, direction, true),
                new Face(List.of(a.get(0), a.get(1), b.get(1), b.get(0)), crossY.reverse(), false),
                new Face(List.of(a.get(1), a.get(2), b.get(2), b.get(1)), crossX, false),
                new Face(List.of(a.get(2), a.get(3), b.get(3), b.get(2)), crossY, false),
                new Face(List.of(a.get(3), a.get(0), b.get(0), b.get(3)), crossX.reverse(), false)
        );
        for (SurfacePlane plane : planes) {
            faces = cut(faces, plane);
        }
        return new Section(start, end, planes, faces.stream().filter(WoodenBeamGeometry::hasArea).toList());
    }

    private List<Face> buildExterior() {
        if (sections.isEmpty()) {
            return List.of();
        }
        Section first = sections.getFirst();
        Section last = sections.getLast();
        List<Face> result = new ArrayList<>();
        for (Section section : sections) {
            for (Face face : section.faces()) {
                if (section.start() < 0 || section.end() > length) {
                    double seam = section.start() < 0 ? 0 : length;
                    if (!isAt(face, seam)) {
                        result.add(face);
                    }
                } else if (first.start() < 0 && isAt(face, 0)) {
                    addExposedCap(result, face, first);
                } else if (last.end() > length && isAt(face, length)) {
                    addExposedCap(result, face, last);
                } else {
                    result.add(face);
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean isAt(Face face, double along) {
        return face.end() && face.vertices().stream()
                .allMatch(vertex -> Math.abs(vertex.subtract(origin).dot(direction) - along) <= EPSILON);
    }

    private static void addExposedCap(List<Face> result, Face face, Section extension) {
        // Partition the old end face into the strips outside the new joint.
        // The part shared with the extension is internal and gets no quad.
        List<Vec3> opening = face.vertices();
        for (SurfacePlane plane : extension.planes().subList(2, extension.planes().size())) {
            if (opening.stream().anyMatch(point -> signedDistance(point, plane) < -EPSILON)) {
                var strip = clipPolygon(opening, new SurfacePlane(plane.point(), plane.normal().reverse()));
                Face exposed = new Face(strip, face.normal(), true);
                if (hasArea(exposed)) {
                    result.add(exposed);
                }
                opening = clipPolygon(opening, plane);
            }
        }
    }

    private static boolean hasArea(Face face) {
        List<Vec3> points = face.vertices();
        for (int i = 1; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i).subtract(points.getFirst());
            Vec3 b = points.get(i + 1).subtract(points.getFirst());
            if (a.cross(b).lengthSqr() > EPSILON * EPSILON) {
                return true;
            }
        }
        return false;
    }

    private List<Vec3> corners(Vec3 center) {
        Vec3 x = crossX.scale(HALF_WIDTH);
        Vec3 y = crossY.scale(HALF_WIDTH);
        return List.of(center.subtract(x).subtract(y), center.add(x).subtract(y),
                center.add(x).add(y), center.subtract(x).add(y));
    }

    private static List<Face> cut(List<Face> source, SurfacePlane plane) {
        List<Face> clipped = new ArrayList<>(source.size() + 1);
        List<Vec3> cap = new ArrayList<>(4);
        boolean removedStock = false;
        for (Face face : source) {
            for (Vec3 point : face.vertices()) {
                removedStock |= signedDistance(point, plane) < -EPSILON;
            }
            List<Vec3> polygon = clipPolygon(face.vertices(), plane);
            if (polygon.size() >= 3) {
                clipped.add(new Face(polygon, face.normal(), face.end()));
            }
            for (Vec3 point : polygon) {
                if (Math.abs(signedDistance(point, plane)) <= EPSILON
                        && cap.stream().noneMatch(other -> other.distanceToSqr(point) <= EPSILON * EPSILON)) {
                    cap.add(point);
                }
            }
        }
        if (removedStock && cap.size() >= 3) {
            Vec3 center = Vec3.ZERO;
            for (Vec3 point : cap) {
                center = center.add(point);
            }
            Vec3 capCenter = center.scale(1.0 / cap.size());
            Vec3 normal = plane.normal().reverse();
            Vec3 u = cap.getFirst().subtract(capCenter);
            Vec3 v = normal.cross(u);
            cap.sort(Comparator.comparingDouble(point -> {
                Vec3 offset = point.subtract(capCenter);
                return Math.atan2(offset.dot(v), offset.dot(u));
            }));
            clipped.add(new Face(List.copyOf(cap), normal, true));
        }
        return clipped;
    }

    public static List<Vec3> clipPolygon(List<Vec3> polygon, SurfacePlane plane) {
        return clipPolygonToPlane(polygon, plane, EPSILON);
    }

    public double crossExtent(Vec3 axis) {
        return HALF_WIDTH * (Math.abs(axis.dot(crossX)) + Math.abs(axis.dot(crossY)));
    }

    public Vec3 origin() { return origin; }
    public Vec3 direction() { return direction; }
    public Vec3 crossX() { return crossX; }
    public Vec3 crossY() { return crossY; }
    public double length() { return length; }
    public SurfacePlane fromPlane() { return fromPlane; }
    public SurfacePlane toPlane() { return toPlane; }
    public List<Face> faces() { return faces; }
    public List<Section> sections() { return sections; }

    public record Face(List<Vec3> vertices, Vec3 normal, boolean end) {
    }

    public record Section(double start, double end, List<SurfacePlane> planes, List<Face> faces) {
    }
}
