package com.aislen.createindustrialdetails.content.block.supports;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class SteelWallBracketBlockItem extends BlockItem {
    public SteelWallBracketBlockItem(SteelWallBracketBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(getBlock()) && state.getValue(SteelWallBracketBlock.QUANTITY) == 2) {
            // Stop BlockItem from placing an adjacent block when the clicked pair is already full.
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
}
