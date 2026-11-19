package com.aislen.createindustrialdetails.content.block.woodenbeam.structure;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamGeometry;
import com.cake.struts.content.shape.SurfaceClippingHelper.SurfacePlane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the cached voxel approximation from the beam's clipped faces. */
public final class WoodenBeamLineGeometry {
    private WoodenBeamLineGeometry() {
    }

    public static Map<BlockPos, VoxelShape> voxelize(WoodenBeamGeometry beam, int resolutionPixels) {
        if (beam.faces().isEmpty()) {
            return Map.of();
        }

        Direction dominant = Direction.getNearest(beam.direction().x, beam.direction().y, beam.direction().z);
        Direction.Axis axis = dominant.getAxis();
        Vec3 normal = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)
                .getNormal());
        Map<BlockPos, VoxelShape> shapes = new HashMap<>();
        double step = Math.max(1, resolutionPixels) / 16.0;
        for (var section : beam.sections()) {
            addSection(section.faces(), normal, step, shapes);
        }
        shapes.replaceAll((pos, shape) -> shape.optimize());
        return shapes;
    }

    private static void addSection(
            List<WoodenBeamGeometry.Face> faces, Vec3 normal, double step, Map<BlockPos, VoxelShape> shapes
    ) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (var face : faces) {
            for (Vec3 vertex : face.vertices()) {
                double coordinate = vertex.dot(normal);
                min = Math.min(min, coordinate);
                max = Math.max(max, coordinate);
            }
        }

        for (double start = min; start < max; start += step) {
            var lower = new SurfacePlane(normal.scale(start), normal);
            var upper = new SurfacePlane(normal.scale(Math.min(start + step, max)), normal.reverse());
            addSlice(faces, lower, upper, shapes);
        }
    }

    private static void addSlice(
            List<WoodenBeamGeometry.Face> faces, SurfacePlane lower, SurfacePlane upper, Map<BlockPos, VoxelShape> shapes
    ) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (var face : faces) {
            var polygon = WoodenBeamGeometry.clipPolygon(face.vertices(), lower);
            for (Vec3 point : WoodenBeamGeometry.clipPolygon(polygon, upper)) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
                maxZ = Math.max(maxZ, point.z);
            }
        }
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return;
        }

        // Split each slice across its handful of cells. Collision queries use
        // the cached local shapes and never repeat the clipping.
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(minX), Mth.floor(minY), Mth.floor(minZ),
                Mth.ceil(maxX) - 1, Mth.ceil(maxY) - 1, Mth.ceil(maxZ) - 1)) {
            VoxelShape shape = Shapes.create(
                    Math.max(0, minX - pos.getX()), Math.max(0, minY - pos.getY()), Math.max(0, minZ - pos.getZ()),
                    Math.min(1, maxX - pos.getX()), Math.min(1, maxY - pos.getY()), Math.min(1, maxZ - pos.getZ())
            );
            if (!shape.isEmpty()) {
                shapes.merge(pos.immutable(), shape, Shapes::or);
            }
        }
    }
}
