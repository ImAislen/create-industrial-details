package com.aislen.createindustrialdetails.content.block.rivetedsteel.trench;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class ServiceTrenchPipeFlanges {
    private ServiceTrenchPipeFlanges() {}

    public static int exposedEnds(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (!state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side))) continue;
            BlockPos neighborPos = pos.relative(side);
            BlockState neighbor = level.getBlockState(neighborPos);
            var pipe = FluidPropagator.getPipe(level, neighborPos);
            boolean connected = FluidPipeBlock.canConnectTo(level, neighborPos, neighbor, side)
                    && (pipe == null || pipe.canHaveFlowToward(neighbor, side.getOpposite()));
            if (!connected) mask |= 1 << side.ordinal();
        }
        return mask;
    }
}
