package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class ServiceTrenchShaftBlockEntity extends KineticBlockEntity {
    public ServiceTrenchShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVICE_TRENCH_SHAFT.get(), pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        return false;
    }
}
