package com.aislen.createindustrialdetails.content.block.cable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class CableConnections {
    private CableConnections() {
    }

    public static boolean canConnect(BlockState first, BlockState second, Direction towardSecond) {
        if (!(first.getBlock() instanceof CableTrayBlock)) {
            return second.getBlock() instanceof CableTrayBlock && sourcePort(first, second, towardSecond);
        }
        if (first.getValue(CableTrayBlock.AXIS) != towardSecond.getAxis()) return false;
        if (second.getBlock() instanceof CableTrayBlock) {
            return first.getValue(CableTrayBlock.AXIS) == second.getValue(CableTrayBlock.AXIS)
                    && (towardSecond.getAxis() != Direction.Axis.Y
                    || first.getValue(CableTrayBlock.FACING) == second.getValue(CableTrayBlock.FACING));
        }
        return sourcePort(second, first, towardSecond.getOpposite());
    }

    private static boolean sourcePort(BlockState box, BlockState tray, Direction fromBox) {
        if (!(box.getBlock() instanceof ControlBoxBlock)) return false;
        if (!ControlBoxBlock.isCablePort(box, fromBox)) return false;
        Direction front = box.getValue(ControlBoxBlock.FACING);
        if (tray.getValue(CableTrayBlock.AXIS) != fromBox.getAxis()) return false;
        return fromBox.getAxis() != Direction.Axis.Y || tray.getValue(CableTrayBlock.FACING) == front;
    }
}
