package com.aislen.createindustrialdetails.content.block.singleplank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class SinglePlankBlockItem extends BlockItem {
    public SinglePlankBlockItem(SinglePlankBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Handle compatible merges before an interactive support block can consume the click.
        return tryAddPlank(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = tryAddPlank(context);
        return result == InteractionResult.PASS ? super.useOn(context) : result;
    }

    private InteractionResult tryAddPlank(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !(getBlock() instanceof SinglePlankBlock block)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(pos);
        if (!state.is(block)) {
            if (context.getClickedFace() != Direction.UP) {
                return InteractionResult.PASS;
            }
            pos = pos.above();
            state = context.getLevel().getBlockState(pos);
            if (!state.is(block)) {
                return InteractionResult.PASS;
            }
        }
        return block.tryAddPlank(context.getItemInHand(), state, context.getLevel(), pos, player,
                context.getClickedFace(), context.getClickLocation().z - pos.getZ()).result();
    }
}
