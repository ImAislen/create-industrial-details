package com.aislen.createindustrialdetails.content.block.cable.individual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class IndividualCableBends {
    private IndividualCableBends() {}
    public record Bend(IndividualCableColor color, BlockPos peer, List<Vec3> points, boolean needsInfill, List<net.minecraft.world.phys.AABB> boxes, CableRoute route) {}
    private static net.minecraft.world.phys.AABB extend(net.minecraft.world.phys.AABB box, net.minecraft.world.phys.AABB hull, Direction.Axis axis) {
        return new net.minecraft.world.phys.AABB(axis == Direction.Axis.X ? hull.minX : box.minX, axis == Direction.Axis.Y ? hull.minY : box.minY, axis == Direction.Axis.Z ? hull.minZ : box.minZ,
                axis == Direction.Axis.X ? hull.maxX : box.maxX, axis == Direction.Axis.Y ? hull.maxY : box.maxY, axis == Direction.Axis.Z ? hull.maxZ : box.maxZ);
    }
    public static List<net.minecraft.world.phys.AABB> infill(net.minecraft.world.phys.shapes.VoxelShape own,
            net.minecraft.world.phys.shapes.VoxelShape theirs, Direction.Axis a, Direction.Axis b) {
        var ownBox = own.bounds(); var otherBox = theirs.bounds(); var hull = ownBox.minmax(otherBox);
        var envelope = extend(ownBox, hull, a).intersect(extend(otherBox, hull, b));
        return net.minecraft.world.phys.shapes.Shapes.join(net.minecraft.world.phys.shapes.Shapes.create(envelope),
                net.minecraft.world.phys.shapes.Shapes.or(own, theirs), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST).toAabbs();
    }
    public static List<Bend> find(LevelReader level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof IndividualCableBlock)) return List.of();
        var result = new ArrayList<Bend>();
        for (var offset : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
            if (offset.equals(BlockPos.ZERO)) continue;
            var peer = pos.offset(offset);
            if (!level.hasChunkAt(peer)) continue;
            var other = level.getBlockState(peer);
            if (!(other.getBlock() instanceof IndividualCableBlock)) continue;
            for (var color : IndividualCableColor.values()) {
                for (var a : IndividualCableComponents.routes(level, pos, state, color)) for (var b : IndividualCableComponents.routes(level, peer, other, color)) {
                if (a == CableRoute.NONE || b == CableRoute.NONE || a.axis() == b.axis()) continue;
                Vec3 from = null, to = null; double distance = .1 * .1;
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
                    var endA = IndividualCableGeometry.end(pos, color, a, i); var endB = IndividualCableGeometry.end(peer, color, b, j);
                    double gap = endA.distanceToSqr(endB);
                    if (gap < distance && gap > 1e-10) { from = endA; to = endB; distance = gap; }
                }
                if (from != null) {
                    Vec3 elbow = a.axis() == Direction.Axis.Y ? new Vec3(from.x, to.y, from.z)
                            : a.axis() == Direction.Axis.X ? new Vec3(to.x, from.y, from.z) : new Vec3(from.x, from.y, to.z);
                    var own = IndividualCableGeometry.part(color, a).move(pos.getX(), pos.getY(), pos.getZ());
                    var theirs = IndividualCableGeometry.part(color, b).move(peer.getX(), peer.getY(), peer.getZ());
                    var boxes = infill(own, theirs, a.axis(), b.axis());
                    result.add(new Bend(color, peer.immutable(), List.of(from, elbow, to), !boxes.isEmpty(), boxes, a));
                }
                }
            }
        }
        return List.copyOf(result);
    }
}
