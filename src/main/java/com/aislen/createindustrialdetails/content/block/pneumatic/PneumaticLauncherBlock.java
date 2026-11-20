package com.aislen.createindustrialdetails.content.block.pneumatic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

public final class PneumaticLauncherBlock extends PneumaticStationBlock implements EntityBlock {
    public PneumaticLauncherBlock(Properties properties) { super(properties, Kind.LAUNCHER); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(UPPER) ? null : new PneumaticLauncherBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(UPPER) ? null : (world, pos, block, entity) -> {
            if (entity instanceof PneumaticLauncherBlockEntity launcher) launcher.tick();
        };
    }
}
