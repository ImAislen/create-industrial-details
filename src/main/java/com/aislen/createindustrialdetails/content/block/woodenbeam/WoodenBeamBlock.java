package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.block.StrutBlock;
import com.cake.struts.content.block.StrutBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class WoodenBeamBlock extends StrutBlock {

    public WoodenBeamBlock(Properties properties, StrutModelType modelType) {
        super(properties, modelType);
    }

    @Override
    protected BlockEntityType<? extends StrutBlockEntity>
    getStrutBlockEntityType() {
        return ModBlockEntities.WOODEN_BEAM.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WoodenBeamBlockEntity(pos, state);
    }
}
