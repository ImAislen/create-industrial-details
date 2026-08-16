package com.aislen.createindustrialdetails.content.block.woodenpost;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.Predicate;

/** Create placement assistance for extending aligned Wooden Post runs vertically. */
public final class WoodenPostPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof WoodenPostBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof WoodenPostBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                     BlockHitResult ray) {
        if (ray.getDirection().getAxis() == Direction.Axis.Y) {
            return PlacementOffset.fail();
        }

        List<Direction> directions = IPlacementHelper.orderedByDistance(
                pos,
                ray.getLocation(),
                direction -> direction.getAxis() == Direction.Axis.Y
        );
        WoodenPostPosition position = state.getValue(WoodenPostBlock.POST_POSITION);

        for (Direction direction : directions) {
            int range = placementAssistRange(player);
            int attached = attachedPosts(level, pos, direction, position);
            if (attached >= range) {
                continue;
            }

            BlockPos newPos = pos.relative(direction, attached + 1);
            if (!level.getBlockState(newPos).canBeReplaced()) {
                continue;
            }

            return PlacementOffset.success(
                    newPos,
                    blockState -> Block.updateFromNeighbourShapes(
                            blockState.setValue(WoodenPostBlock.POST_POSITION, position),
                            level,
                            newPos
                    )
            );
        }

        return PlacementOffset.fail();
    }

    private static int attachedPosts(Level level, BlockPos pos, Direction direction,
                                     WoodenPostPosition position) {
        int count = 0;
        BlockPos checkPos = pos.relative(direction);
        BlockState checkState = level.getBlockState(checkPos);

        while (checkState.getBlock() instanceof WoodenPostBlock
                && checkState.getValue(WoodenPostBlock.POST_POSITION) == position) {
            count++;
            checkPos = checkPos.relative(direction);
            checkState = level.getBlockState(checkPos);
        }
        return count;
    }

    private static int placementAssistRange(Player player) {
        int range = AllConfigs.server().equipment.placementAssistRange.get();
        if (player == null) {
            return range;
        }

        AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) {
            range += 4;
        }
        return range;
    }
}
