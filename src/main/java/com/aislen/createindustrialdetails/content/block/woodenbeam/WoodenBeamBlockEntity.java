package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.cake.struts.content.block.StrutBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class WoodenBeamBlockEntity extends StrutBlockEntity {

    public WoodenBeamBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WOODEN_BEAM.get(), pos, state);
    }
}
