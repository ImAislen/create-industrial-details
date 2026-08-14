package com.aislen.createindustrialdetails.content.block.woodenbeam.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Wooden-Beam endpoint adaptation of Struts' BlockyStrutLineGeometry.
 *
 * <p>Adapted from Strut Your Stuff 1.2.1 (MIT): the stock class only accepts
 * centered BlockPos/Direction attachments. This version keeps the same cached,
 * sliced VoxelShape strategy but accepts the two authoritative endpoint Vec3s.</p>
 */
public final class WoodenBeamLineGeometry {

    private static final double EPSILON = 1.0e-6;

    private final Vec3 fromAttachment;
    private final Vec3 toAttachment;
    private final double halfWidth;
    private final double halfHeight;
    private final int resolutionPixels;
    private final Vec3 tangent;
    private final Vec3 localX;
    private final Vec3 localY;
    private final double length;
    private final BlockPos[] positions;

    public WoodenBeamLineGeometry(
            Vec3 fromAttachment,
            Vec3 toAttachment,
            int widthPixels,
            int heightPixels,
            int resolutionPixels
    ) {
        this.fromAttachment = fromAttachment;
        this.toAttachment = toAttachment;
        this.halfWidth = widthPixels / 32.0;
        this.halfHeight = heightPixels / 32.0;
        this.resolutionPixels = Math.max(1, resolutionPixels);

        Vec3 delta = toAttachment.subtract(fromAttachment);
        this.length = delta.length();
        this.tangent = length > EPSILON ? delta.scale(1.0 / length) : new Vec3(1, 0, 0);
        Vec3 helper = Math.abs(tangent.y) > 0.999 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        this.localX = tangent.cross(helper).normalize();
        this.localY = tangent.cross(localX).normalize();
        this.positions = calculatePositions();
    }

    public BlockPos[] getPositions() {
        return positions;
    }

    public VoxelShape getShapeForPosition(BlockPos pos) {
        if (length <= EPSILON) {
            return Shapes.empty();
        }

        double extentX = axisExtent(localX.x, localY.x);
        double extentY = axisExtent(localX.y, localY.y);
        double extentZ = axisExtent(localX.z, localY.z);
        AABB expandedBlock = new AABB(pos).inflate(extentX, extentY, extentZ);
        double[] range = intersectRayAabb(fromAttachment, tangent, expandedBlock);
        if (range == null) {
            return Shapes.empty();
        }

        double startT = Math.max(0.0, range[0]);
        double endT = Math.min(length, range[1]);
        if (startT > endT + EPSILON) {
            return Shapes.empty();
        }

        double dominant = Math.max(Math.abs(tangent.x), Math.max(Math.abs(tangent.y), Math.abs(tangent.z)));
        double step = Math.max((resolutionPixels / 16.0) / Math.max(dominant, EPSILON), EPSILON);
        VoxelShape result = Shapes.empty();

        for (double t = startT; t <= endT + EPSILON; ) {
            double next = Math.min(t + step, endT);
            Vec3 a = fromAttachment.add(tangent.scale(t));
            Vec3 b = fromAttachment.add(tangent.scale(next));

            double minX = Math.max(0, Math.min(a.x, b.x) - pos.getX() - extentX);
            double minY = Math.max(0, Math.min(a.y, b.y) - pos.getY() - extentY);
            double minZ = Math.max(0, Math.min(a.z, b.z) - pos.getZ() - extentZ);
            double maxX = Math.min(1, Math.max(a.x, b.x) - pos.getX() + extentX);
            double maxY = Math.min(1, Math.max(a.y, b.y) - pos.getY() + extentY);
            double maxZ = Math.min(1, Math.max(a.z, b.z) - pos.getZ() + extentZ);

            if (minX < maxX && minY < maxY && minZ < maxZ) {
                result = Shapes.or(result, Shapes.create(minX, minY, minZ, maxX, maxY, maxZ));
            }
            if (next >= endT - EPSILON) {
                break;
            }
            t = next;
        }
        return result.isEmpty() ? result : result.optimize();
    }

    private BlockPos[] calculatePositions() {
        if (length <= EPSILON) {
            return new BlockPos[0];
        }
        double extentX = axisExtent(localX.x, localY.x);
        double extentY = axisExtent(localX.y, localY.y);
        double extentZ = axisExtent(localX.z, localY.z);

        int minX = Mth.floor(Math.min(fromAttachment.x, toAttachment.x) - extentX);
        int minY = Mth.floor(Math.min(fromAttachment.y, toAttachment.y) - extentY);
        int minZ = Mth.floor(Math.min(fromAttachment.z, toAttachment.z) - extentZ);
        int maxX = Mth.floor(Math.max(fromAttachment.x, toAttachment.x) + extentX);
        int maxY = Mth.floor(Math.max(fromAttachment.y, toAttachment.y) + extentY);
        int maxZ = Mth.floor(Math.max(fromAttachment.z, toAttachment.z) + extentZ);

        List<BlockPos> touched = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            AABB expanded = new AABB(pos).inflate(extentX, extentY, extentZ);
            double[] range = intersectRayAabb(fromAttachment, tangent, expanded);
            if (range != null && range[1] >= 0 && range[0] <= length) {
                touched.add(pos.immutable());
            }
        }
        return touched.toArray(BlockPos[]::new);
    }

    private double axisExtent(double localXComponent, double localYComponent) {
        return Math.abs(halfWidth * localXComponent) + Math.abs(halfHeight * localYComponent);
    }

    private static double[] intersectRayAabb(Vec3 origin, Vec3 direction, AABB box) {
        double[] range = {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        if (!clipAxis(origin.x, direction.x, box.minX, box.maxX, range)
                || !clipAxis(origin.y, direction.y, box.minY, box.maxY, range)
                || !clipAxis(origin.z, direction.z, box.minZ, box.maxZ, range)) {
            return null;
        }
        return range;
    }

    private static boolean clipAxis(double origin, double direction, double min, double max, double[] range) {
        if (Math.abs(direction) <= EPSILON) {
            return origin >= min && origin <= max;
        }
        double a = (min - origin) / direction;
        double b = (max - origin) / direction;
        if (a > b) {
            double swap = a;
            a = b;
            b = swap;
        }
        range[0] = Math.max(range[0], a);
        range[1] = Math.min(range[1], b);
        return range[0] <= range[1];
    }
}
