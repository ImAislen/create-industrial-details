package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchCableBlock;
import com.aislen.createindustrialdetails.content.block.cable.CablePhysicalConnections;
import net.minecraft.core.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public final class CableConnectionGeometry {
    private CableConnectionGeometry() {}
    public static Vec3 offset(CableRoute route) {
        return route.transform(route.vertical() ? new Vec3(0, 0, 1.5 / 16) : new Vec3(0, 1.5 / 16, 0)).subtract(route.transform(Vec3.ZERO));
    }
    public static VoxelShape elevatedPart(IndividualCableColor color, CableRoute route) {
        var shift = offset(route);
        return IndividualCableGeometry.part(color, route).move(shift.x, shift.y, shift.z);
    }
    public static boolean elevated(LevelReader level, BlockPos pos, IndividualCableColor color, CableRoute route) {
        if (level.getBlockState(pos).getBlock() instanceof ServiceTrenchCableBlock) return true;
        for (var contact : CablePhysicalConnections.contacts(level, pos, level.getBlockState(pos), color, route))
            if (contact.cable().add(offset(route)).distanceToSqr(contact.other()) < 1e-10) return true;
        return false;
    }
    public static VoxelShape part(LevelReader level, BlockPos pos, IndividualCableColor color, CableRoute route) {
        return elevated(level, pos, color, route) ? elevatedPart(color, route) : IndividualCableGeometry.part(color, route);
    }
    public static Vec3 endpoint(LevelReader level, BlockPos pos, IndividualCableColor color, CableRoute route, BlockPos peer) {
        var end = IndividualCableGeometry.attachment(pos, color, route, peer);
        return elevated(level, pos, color, route) ? end.add(offset(route)) : end;
    }
    public static List<AABB> steps(LevelReader level, BlockPos pos, IndividualCableColor color, CableRoute route) {
        if (elevated(level, pos, color, route)) return List.of();
        var result = new ArrayList<AABB>();
        for (var side : Direction.values()) {
            if (side.getAxis() != route.axis()) continue;
            var peer = pos.relative(side); if (!level.hasChunkAt(peer)) continue;
            if (!(level.getBlockEntity(peer) instanceof IndividualCableBlockEntity host) || !host.contains(color, route)
                    || !elevated(level, peer, color, route)) continue;
            var own = IndividualCableGeometry.part(color, route).move(pos.getX(), pos.getY(), pos.getZ());
            var other = elevatedPart(color, route).move(peer.getX(), peer.getY(), peer.getZ());
            var point = IndividualCableGeometry.end(pos, color, route, side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
            var hull = own.bounds().minmax(other.bounds());
            double half = 1.0 / 32;
            var bridge = new AABB(route.axis() == Direction.Axis.X ? point.x - half : hull.minX,
                    route.axis() == Direction.Axis.Y ? point.y - half : hull.minY,
                    route.axis() == Direction.Axis.Z ? point.z - half : hull.minZ,
                    route.axis() == Direction.Axis.X ? point.x + half : hull.maxX,
                    route.axis() == Direction.Axis.Y ? point.y + half : hull.maxY,
                    route.axis() == Direction.Axis.Z ? point.z + half : hull.maxZ);
            result.addAll(Shapes.join(Shapes.create(bridge), Shapes.or(own, other), BooleanOp.ONLY_FIRST).toAabbs());
        }
        return List.copyOf(result);
    }
    public static VoxelShape shape(LevelReader level, BlockPos pos, IndividualCableColor color, CableRoute route) {
        var shape = part(level, pos, color, route);
        for (var step : steps(level, pos, color, route)) shape = Shapes.or(shape, Shapes.create(step.move(Vec3.atLowerCornerOf(pos).scale(-1))));
        return shape.optimize();
    }
}
