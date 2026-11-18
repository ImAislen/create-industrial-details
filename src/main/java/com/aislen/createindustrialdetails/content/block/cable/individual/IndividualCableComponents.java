package com.aislen.createindustrialdetails.content.block.cable.individual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public final class IndividualCableComponents {
    private IndividualCableComponents() {}
    public record Component(IndividualCableColor color, CableRoute route) {}
    public static List<CableRoute> routes(BlockGetter level, BlockPos pos, BlockState state, IndividualCableColor color) {
        if (level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) return host.routes(color);
        return state.getValue(color.property) == CableRoute.NONE ? List.of() : List.of(state.getValue(color.property));
    }
    public static boolean validPair(IndividualCableColor color, CableRoute a, CableRoute b) {
        if (a == CableRoute.NONE || b == CableRoute.NONE || a.vertical() == b.vertical()) return false;
        double closest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) closest = Math.min(closest,
                IndividualCableGeometry.end(BlockPos.ZERO, color, a, i).distanceToSqr(IndividualCableGeometry.end(BlockPos.ZERO, color, b, j)));
        return closest < .01;
    }
    public static Component target(BlockGetter level, BlockPos pos, BlockState state, Vec3 from, Vec3 to) {
        Component result = null; double nearest = Double.POSITIVE_INFINITY;
        for (var color : IndividualCableColor.values()) for (var route : routes(level, pos, state, color)) {
            var shape = level instanceof net.minecraft.world.level.LevelReader reader ? CableConnectionGeometry.shape(reader, pos, color, route) : IndividualCableGeometry.part(color, route);
            var hit = shape.clip(from, to, pos);
            if (hit != null && hit.getLocation().distanceToSqr(from) < nearest) {
                nearest = hit.getLocation().distanceToSqr(from); result = new Component(color, route);
            }
        }
        return result;
    }
    public static CableRoute selectedRoute(IndividualCableBlockEntity host, IndividualCableColor color, Vec3 from, Vec3 to) {
        CableRoute result = host.getBlockState().getValue(color.property); double nearest = Double.POSITIVE_INFINITY;
        for (var route : host.routes(color)) {
            var hit = CableConnectionGeometry.shape(host.getLevel(), host.getBlockPos(), color, route).clip(from, to, host.getBlockPos());
            if (hit != null && hit.getLocation().distanceToSqr(from) < nearest) { nearest = hit.getLocation().distanceToSqr(from); result = route; }
        }
        return result;
    }
}
