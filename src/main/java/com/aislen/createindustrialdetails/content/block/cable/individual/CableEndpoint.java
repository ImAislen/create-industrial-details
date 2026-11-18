package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.aislen.createindustrialdetails.content.block.cable.*;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

public record CableEndpoint(BlockPos pos, CableRoute route, int port) {
    public CableEndpoint { pos = pos.immutable(); }
    public boolean isPort() { return port > 0 && port <= 24; }
    public boolean isTray() { return CableTrayPorts.isTray(port); }
    public int selection() { return port != 0 ? -port : route.ordinal(); }
    public static CableEndpoint selection(BlockPos pos, int code) {
        if (code < 0 && code >= -48) return new CableEndpoint(pos, CableRoute.NONE, -code);
        return code > 0 && code < CableRoute.values().length ? new CableEndpoint(pos, CableRoute.values()[code], 0) : null;
    }
    public boolean exists(LevelReader level, IndividualCableColor color) {
        if (!level.hasChunkAt(pos)) return false;
        if (isPort()) return ControlBoxPorts.valid(level.getBlockState(pos), port);
        if (isTray()) return CableTrayPorts.valid(level, pos, port);
        return level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host && host.contains(color, route);
    }
    public long token(BlockGetter level, IndividualCableColor color) {
        if (isPort()) return level.getBlockEntity(pos) instanceof ControlBoxBlockEntity box ? box.cableToken() : 0;
        if (isTray()) return port;
        return level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host ? host.token(color, route) : 0;
    }
    public Vec3 point(IndividualCableColor color, BlockPos peer) {
        return isPort() ? ControlBoxPorts.point(pos, port, color) : isTray() ? CableTrayPorts.point(pos, port, color) : IndividualCableGeometry.attachment(pos, color, route, peer);
    }
    public Vec3 point(LevelReader level, IndividualCableColor color, BlockPos peer) {
        return port == 0 && level.hasChunkAt(pos) ? CableConnectionGeometry.endpoint(level, pos, color, route, peer) : point(color, peer);
    }
    public Direction outward(BlockPos peer) {
        if (isPort()) return ControlBoxPorts.side(port);
        if (isTray()) return CableTrayPorts.side(port);
        double along = route.axis().choose(peer.getX() - pos.getX(), peer.getY() - pos.getY(), peer.getZ() - pos.getZ());
        return Direction.get(along > 0 || along == 0 && pos.compareTo(peer) < 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, route.axis());
    }
    public double roll() {
        if (isPort()) return ControlBoxPorts.side(port).getAxis() == Direction.Axis.Y && ControlBoxPorts.facing(port).getAxis() == Direction.Axis.X ? Math.PI / 2 : 0;
        if (isTray()) return CableTrayPorts.side(port).getAxis() == Direction.Axis.Y && CableTrayPorts.state(port).getValue(CableTrayBlock.FACING).getAxis() == Direction.Axis.X ? Math.PI / 2 : 0;
        return route.vertical() && route.turns() % 2 != 0 ? Math.PI / 2 : 0;
    }
    public static CableEndpoint target(LevelReader level, BlockPos pos, Direction face, Vec3 hit, Vec3 eye, double reach, IndividualCableColor color) {
        if (!level.hasChunkAt(pos)) return null;
        int port = ControlBoxPorts.target(level.getBlockState(pos), pos, face, hit, color);
        if (port == 0) port = CableTrayPorts.target(level, pos, face, hit, color);
        if (port != 0) return new CableEndpoint(pos, CableRoute.NONE, port);
        if (!(level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) || host.routes(color).isEmpty()) return null;
        return new CableEndpoint(pos, IndividualCableComponents.selectedRoute(host, color, eye, eye.add(hit.subtract(eye).normalize().scale(reach))), 0);
    }
}
