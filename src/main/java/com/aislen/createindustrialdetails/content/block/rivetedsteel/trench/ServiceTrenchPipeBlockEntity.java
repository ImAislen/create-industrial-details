package com.aislen.createindustrialdetails.content.block.rivetedsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class ServiceTrenchPipeBlockEntity extends FluidPipeBlockEntity implements IHaveGoggleInformation {
    public ServiceTrenchPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVICE_TRENCH_PIPE.get(), pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        return ServiceTrenchFluidInspection.addToTooltip(this, tooltip);
    }
}
