package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableColor;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ControlBoxPorts {
    private ControlBoxPorts() {}
    public static int encode(Direction side, Direction facing) { return 1 + side.ordinal() + 6 * facing.get2DDataValue(); }
    public static Direction side(int code) { return Direction.values()[(code - 1) % 6]; }
    public static Direction facing(int code) { return Direction.from2DDataValue((code - 1) / 6); }
    public static boolean valid(BlockState state, int code) {
        return code > 0 && code <= 24 && state.getBlock() instanceof ControlBoxBlock && facing(code) == state.getValue(ControlBoxBlock.FACING)
                && ControlBoxBlock.isCablePort(state, side(code));
    }
    public static Vec3 point(BlockPos pos, int code, IndividualCableColor color) {
        Direction side = side(code);
        // Main Body UVs: the black openings are centered two model pixels from the back/bottom edge.
        Vec3 model = new Vec3((color.minX + .75) / 16, side == Direction.UP ? 1 : side == Direction.DOWN ? 0 : 2.0 / 16,
                side.getAxis() == Direction.Axis.Y ? 2.0 / 16 : 0);
        return ControlBoxGeometry.fromModel(facing(code), model).add(Vec3.atLowerCornerOf(pos));
    }
    public static int target(BlockState state, BlockPos pos, Direction side, Vec3 hit, IndividualCableColor color) {
        if (!(state.getBlock() instanceof ControlBoxBlock) || !ControlBoxBlock.isCablePort(state, side)) return 0;
        int code = encode(side, state.getValue(ControlBoxBlock.FACING));
        var delta = hit.subtract(point(pos, code, color));
        double axial = side.getAxis().choose(delta.x, delta.y, delta.z);
        return delta.lengthSqr() - axial * axial <= Math.pow(.85 / 16, 2) && Math.abs(axial) < .6 / 16 ? code : 0;
    }
}
