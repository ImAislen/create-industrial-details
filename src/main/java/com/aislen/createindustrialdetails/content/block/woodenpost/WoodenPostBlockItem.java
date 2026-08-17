package com.aislen.createindustrialdetails.content.block.woodenpost;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/** Routes occupied-block Post merges before vanilla BlockItem placement rejects the target. */
public final class WoodenPostBlockItem extends BlockItem {
    public WoodenPostBlockItem(WoodenPostBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return super.useOn(context);
        }

        Direction clickedFace = context.getClickedFace();
        if (context.isSecondaryUseActive() && clickedFace.getAxis() != Direction.Axis.Y) {
            BlockHitResult hitResult = new BlockHitResult(
                    context.getClickLocation(),
                    clickedFace,
                    context.getClickedPos(),
                    context.isInside()
            );
            InteractionResult insertion = trySameBlockInsertion(context, hitResult);
            if (insertion != InteractionResult.PASS) {
                return insertion;
            }
        }

        if (clickedFace.getAxis() == Direction.Axis.Y) {
            InteractionResult merge = tryStackedMerge(context);
            if (merge != InteractionResult.PASS) {
                return merge;
            }
        }

        return super.useOn(context);
    }

    private InteractionResult trySameBlockInsertion(UseOnContext context, BlockHitResult hitResult) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != getBlock()) {
            return InteractionResult.PASS;
        }

        WoodenPostPosition addedMember = WoodenPostBlock.resolveSameBlockInsertion(state, pos, hitResult);
        if (addedMember == null) {
            return InteractionResult.FAIL;
        }

        return placeAdditionalMember(context, pos, addedMember);
    }

    private InteractionResult tryStackedMerge(UseOnContext context) {
        BlockPlaceContext placementContext = new BlockPlaceContext(context);
        BlockPos targetPos = placementContext.getClickedPos();
        BlockState targetState = context.getLevel().getBlockState(targetPos);
        if (targetState.getBlock() != getBlock()) {
            return InteractionResult.PASS;
        }

        WoodenPostPosition position = WoodenPostBlock.placementPosition(placementContext);
        return placeAdditionalMember(context, targetPos, position);
    }

    private InteractionResult placeAdditionalMember(
            UseOnContext context,
            BlockPos pos,
            WoodenPostPosition position
    ) {
        boolean added = tryAddMember(
                context.getLevel(),
                pos,
                position,
                getBlock(),
                context.getPlayer(),
                context.getItemInHand(),
                context.getClickedFace()
        );
        return added
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide)
                : InteractionResult.FAIL;
    }

    static boolean tryAddMember(
            Level level,
            BlockPos pos,
            WoodenPostPosition position,
            Block heldBlock,
            Player player,
            ItemStack stack,
            Direction clickedFace
    ) {
        BlockState state = level.getBlockState(pos);
        if (player == null
                || state.getBlock() != heldBlock
                || !(state.getBlock() instanceof WoodenPostBlock)
                || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, clickedFace, stack)) {
            return false;
        }

        WoodenPostArrangement arrangement = WoodenPostBlock.getArrangement(state);
        if (!arrangement.canAdd(position)) {
            return false;
        }

        BlockState updatedState = state.setValue(
                WoodenPostBlock.POST_POSITION,
                arrangement.add(position)
        );
        if (!level.isClientSide) {
            if (!level.setBlock(pos, updatedState, Block.UPDATE_ALL_IMMEDIATE)) {
                return false;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
            }

            SoundType sound = updatedState.getSoundType(level, pos, player);
            level.playSound(
                    null,
                    pos,
                    sound.getPlaceSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F,
                    sound.getPitch() * 0.8F
            );
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, updatedState));
            stack.consume(1, player);
        }
        return true;
    }
}
