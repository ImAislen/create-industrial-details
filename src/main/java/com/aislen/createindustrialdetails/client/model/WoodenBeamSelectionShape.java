package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamGeometry;
import com.cake.struts.content.shape.StrutConnectionShape;
import com.cake.struts.internal.microliner.MicrolinerCoordinateTransform;
import com.cake.struts.internal.microliner.MicrolinerOutline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class WoodenBeamSelectionShape implements StrutConnectionShape {
    private static final double EPSILON = 1.0e-9;
    private final List<WoodenBeamGeometry.Face> faces;
    private final List<Edge> edges = new ArrayList<>();
    private final AABB bounds;

    WoodenBeamSelectionShape(WoodenBeamGeometry beam) {
        faces = beam.faces();
        AABB box = null;
        for (var face : faces) {
            List<Vec3> vertices = face.vertices();
            for (int i = 0; i < vertices.size(); i++) {
                Vec3 point = vertices.get(i);
                AABB corner = new AABB(point, point);
                box = box == null ? corner : box.minmax(corner);
                Vec3 next = vertices.get((i + 1) % vertices.size());
                if (!samePoint(point, next) && edges.stream().noneMatch(edge -> edge.matches(point, next))) {
                    edges.add(new Edge(point, next));
                }
            }
        }
        bounds = box;
    }

    @Override
    public @Nullable Vec3 intersect(Vec3 rayFrom, Vec3 rayTo) {
        if (bounds == null || (!bounds.contains(rayFrom) && bounds.clip(rayFrom, rayTo).isEmpty())) {
            return null;
        }
        Vec3 ray = rayTo.subtract(rayFrom);
        double hit = Double.POSITIVE_INFINITY;
        for (var face : faces) {
            Vec3 normal = face.normal();
            double speed = ray.dot(normal);
            if (Math.abs(speed) <= EPSILON) {
                continue;
            }
            double t = face.vertices().getFirst().subtract(rayFrom).dot(normal) / speed;
            if (t >= 0 && t <= 1 && t < hit && contains(face, rayFrom.add(ray.scale(t)))) {
                hit = t;
            }
        }
        return hit <= 1 ? rayFrom.add(ray.scale(hit)) : null;
    }

    private static boolean contains(WoodenBeamGeometry.Face face, Vec3 point) {
        var vertices = face.vertices();
        for (int i = 0; i < vertices.size(); i++) {
            Vec3 a = vertices.get(i);
            Vec3 b = vertices.get((i + 1) % vertices.size());
            if (b.subtract(a).cross(point.subtract(a)).dot(face.normal()) < -EPSILON) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void drawOutline(PoseStack pose, VertexConsumer consumer, Vec3 camera, int color,
                            MicrolinerCoordinateTransform transform) {
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float alpha = (color >>> 24) / 255.0F;
        for (Edge edge : edges) {
            MicrolinerOutline.renderLine(pose, consumer, transform.transform(edge.from()).subtract(camera),
                    transform.transform(edge.to()).subtract(camera), red, green, blue, alpha);
        }
    }

    private static boolean samePoint(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) <= EPSILON * EPSILON;
    }

    private record Edge(Vec3 from, Vec3 to) {
        boolean matches(Vec3 a, Vec3 b) {
            return (samePoint(from, a) && samePoint(to, b)) || (samePoint(from, b) && samePoint(to, a));
        }
    }
}
