package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchCableBlock;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import net.minecraft.core.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class CablePhysicalConnections {
    private CablePhysicalConnections() {}
    public record Contact(BlockPos peer, Vec3 cable, Vec3 other, int port) {}
    public static Vec3 trayEnd(BlockPos pos, BlockState state, IndividualCableColor color, Direction side) {
        double x = (color.minX + .75) / 16;
        var axis = state.getValue(CableTrayBlock.AXIS);
        if (axis == Direction.Axis.Y) {
            var local = ControlBoxGeometry.fromModel(state.getValue(CableTrayBlock.FACING), new Vec3(x, side == Direction.UP ? 1 : 0, 2.0 / 16));
            return local.add(Vec3.atLowerCornerOf(pos));
        }
        Direction heading = CableTrayBlock.horizontalHeading(state);
        var route = CableRoute.floor(heading);
        var box = IndividualCableGeometry.part(color, route).bounds();
        return new Vec3(axis == Direction.Axis.X ? (side == Direction.EAST ? 1 : 0) : (box.minX + box.maxX) / 2,
                2.0 / 16, axis == Direction.Axis.Z ? (side == Direction.SOUTH ? 1 : 0) : (box.minZ + box.maxZ) / 2).add(Vec3.atLowerCornerOf(pos));
    }
    public static List<Contact> contacts(LevelReader level, BlockPos pos, BlockState state, IndividualCableColor color, CableRoute route) {
        var result = new ArrayList<Contact>();
        boolean housed = state.getBlock() instanceof ServiceTrenchCableBlock;
        for (var side : Direction.values()) {
            if (side.getAxis() != route.axis()) continue;
            var peer = pos.relative(side); if (!level.hasChunkAt(peer)) continue;
            var other = level.getBlockState(peer);
            if (other.getBlock() instanceof ControlBoxBlock) {
                Direction fromBox = side.getOpposite();
                if (!ControlBoxBlock.isCablePort(other, fromBox)) continue;
                int port = ControlBoxPorts.encode(fromBox, other.getValue(ControlBoxBlock.FACING));
                var cable = IndividualCableGeometry.end(pos, color, route, side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                var point = ControlBoxPorts.point(peer, port, color);
                if (housed) cable = cable.add(CableConnectionGeometry.offset(route));
                if (housed ? cable.distanceToSqr(point) < 1e-10 : compatible(cable, point, route)) result.add(new Contact(peer, cable, point, port));
            } else if (other.getBlock() instanceof CableTrayBlock && other.getValue(CableTrayBlock.AXIS) == side.getAxis()) {
                var cable = IndividualCableGeometry.end(pos, color, route, side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                var point = trayEnd(peer, other, color, side.getOpposite());
                if (housed) cable = cable.add(CableConnectionGeometry.offset(route));
                if (housed ? cable.distanceToSqr(point) < 1e-10 : compatible(cable, point, route))
                    result.add(new Contact(peer, cable, point, 0));
            }
        }
        return List.copyOf(result);
    }
    public static boolean compatible(Vec3 cable, Vec3 point, CableRoute route) {
        var delta = point.subtract(cable);
        double recess = 1.5 / 16;
        return delta.lengthSqr() < 1e-10 || Math.abs(delta.lengthSqr() - recess * recess) < 1e-10
                && (route.vertical() ? Math.abs(delta.x) < 1e-10 || Math.abs(delta.z) < 1e-10 : Math.abs(delta.x) < 1e-10 && Math.abs(delta.z) < 1e-10);
    }
}
