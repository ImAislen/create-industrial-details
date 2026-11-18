package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import net.minecraft.core.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class CableTrayPorts {
    private CableTrayPorts() {}
    // Codes 1..24 remain the existing Control Box endpoint identities.
    public static int encode(BlockState state, Direction side) {
        return 25 + state.getValue(CableTrayBlock.AXIS).ordinal() * 8
                + state.getValue(CableTrayBlock.FACING).get2DDataValue() * 2
                + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
    }
    public static boolean isTray(int code) { return code >= 25 && code <= 48; }
    public static Direction side(int code) {
        return Direction.get((code - 25) % 2 == 1 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE,
                Direction.Axis.values()[(code - 25) / 8]);
    }
    public static BlockState state(int code) {
        return com.aislen.createindustrialdetails.registry.ModBlocks.CABLE_TRAY.get().defaultBlockState()
                .setValue(CableTrayBlock.AXIS, side(code).getAxis())
                .setValue(CableTrayBlock.FACING, Direction.from2DDataValue((code - 25) % 8 / 2));
    }
    public static Vec3 point(BlockPos pos, int code, IndividualCableColor color) {
        return CablePhysicalConnections.trayEnd(pos, state(code), color, side(code));
    }
    public static boolean exposed(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        if (state.getValue(CableTrayBlock.AXIS) != side.getAxis()) return false;
        var peer = pos.relative(side);
        if (!level.hasChunkAt(peer)) return false;
        var adjacent = level.getBlockState(peer);
        if (adjacent.getBlock() instanceof IndividualCableBlock) return false;
        if (CableConnections.canConnect(state, adjacent, side)) return false;
        return adjacent.getCollisionShape(level, peer).isEmpty();
    }
    public static boolean valid(LevelReader level, BlockPos pos, int code) {
        if (!isTray(code) || !level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof CableTrayBlock && encode(state, side(code)) == code
                && exposed(level, pos, state, side(code));
    }
    public static int target(LevelReader level, BlockPos pos, Direction face, Vec3 hit, IndividualCableColor color) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CableTrayBlock) || face.getAxis() != state.getValue(CableTrayBlock.AXIS)) return 0;
        int code = encode(state, face);
        if (!valid(level, pos, code)) return 0;
        var delta = hit.subtract(point(pos, code, color));
        double axial = face.getAxis().choose(delta.x, delta.y, delta.z);
        return Math.abs(axial) < .6 / 16 && delta.lengthSqr() - axial * axial < Math.pow(.85 / 16, 2) ? code : 0;
    }
}
