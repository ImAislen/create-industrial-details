package com.aislen.createindustrialdetails.content.block.cable.individual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class IndividualCableGeometry {
    private static final IndividualCableColor[] COLORS = IndividualCableColor.values();
    private static final int ROUTE_COUNT = CableRoute.values().length;
    private static final VoxelShape[][] PARTS = new VoxelShape[COLORS.length][ROUTE_COUNT];
    private static final java.util.Map<Integer, VoxelShape> COMBINED = new java.util.concurrent.ConcurrentHashMap<>();
    static {
        var routes = CableRoute.values();
        for (var color : COLORS) for (var route : routes) {
            if (route == CableRoute.NONE) { PARTS[color.ordinal()][0] = Shapes.empty(); continue; }
            double x = color.minX / 16;
            var a = route.transform(new Vec3(x, 0, 0));
            var b = route.transform(route.vertical() ? new Vec3(x + 1.5 / 16, 1, 1.0 / 16) : new Vec3(x + 1.5 / 16, 1.0 / 16, 1));
            PARTS[color.ordinal()][route.ordinal()] = Shapes.create(new net.minecraft.world.phys.AABB(a, b));
        }
    }
    private IndividualCableGeometry() {}
    public static VoxelShape part(IndividualCableColor color, CableRoute route) { return PARTS[color.ordinal()][route.ordinal()]; }
    public static VoxelShape shape(BlockState state) {
        int key = 0;
        for (var color : COLORS) key = key * ROUTE_COUNT + state.getValue(color.property).ordinal();
        return COMBINED.computeIfAbsent(key, ignored -> {
            VoxelShape result = Shapes.empty();
            for (var color : COLORS) result = Shapes.or(result, part(color, state.getValue(color.property)));
            return result;
        });
    }
    public static boolean permits(BlockState state) {
        for (var a : COLORS) for (var b : COLORS) {
            if (a.ordinal() >= b.ordinal()) continue;
            if (Shapes.joinIsNotEmpty(part(a, state.getValue(a.property)), part(b, state.getValue(b.property)), net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        }
        return true;
    }
    public static Vec3 anchor(BlockPos pos, IndividualCableColor color, CableRoute route) {
        return end(pos, color, route, .5);
    }
    public static Vec3 attachment(BlockPos pos, IndividualCableColor color, CableRoute route, BlockPos peer) {
        double delta = route.axis().choose(peer.getX() - pos.getX(), peer.getY() - pos.getY(), peer.getZ() - pos.getZ());
        return end(pos, color, route, delta > 0 || delta == 0 && pos.compareTo(peer) < 0 ? 1 : 0);
    }
    public static Vec3 end(BlockPos pos, IndividualCableColor color, CableRoute route, double along) {
        var box = part(color, route).bounds();
        return new Vec3(route.axis() == net.minecraft.core.Direction.Axis.X ? box.minX + box.getXsize() * along : (box.minX + box.maxX) / 2,
                route.axis() == net.minecraft.core.Direction.Axis.Y ? along : (box.minY + box.maxY) / 2,
                route.axis() == net.minecraft.core.Direction.Axis.Z ? box.minZ + box.getZsize() * along : (box.minZ + box.maxZ) / 2).add(Vec3.atLowerCornerOf(pos));
    }
    public static IndividualCableColor target(BlockState state, BlockPos pos, Vec3 start, Vec3 end) {
        double closest = Double.POSITIVE_INFINITY; IndividualCableColor selected = null;
        for (var color : COLORS) {
            var hit = part(color, state.getValue(color.property)).clip(start, end, pos);
            if (hit != null && hit.getLocation().distanceToSqr(start) < closest) {
                selected = color; closest = hit.getLocation().distanceToSqr(start);
            }
        }
        return selected;
    }
}
