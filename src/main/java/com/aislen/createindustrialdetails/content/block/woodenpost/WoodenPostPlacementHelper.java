package com.aislen.createindustrialdetails.content.block.woodenpost;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/** Create placement assistance for extending one aligned Wooden Post member vertically. */
public final class WoodenPostPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> heldPostBlock(stack) != null;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof WoodenPostBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                     BlockHitResult ray) {
        PlacementPlan plan = findPlacement(
                player,
                level,
                state,
                pos,
                ray,
                heldPostBlock(player)
        );
        return plan == null ? PlacementOffset.fail() : plan.offset();
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                     BlockHitResult ray, ItemStack heldItem) {
        Block heldBlock = heldPostBlock(heldItem);
        PlacementPlan plan = findPlacement(player, level, state, pos, ray, heldBlock);
        if (plan == null || heldBlock == null) {
            return PlacementOffset.fail();
        }

        BlockState ghostState = plan.merge()
                ? level.getBlockState(plan.destination())
                : heldBlock.defaultBlockState();
        return plan.offset().withGhostState(ghostState);
    }

    public ItemInteractionResult placeInWorld(
            Level level,
            BlockItem blockItem,
            Player player,
            InteractionHand hand,
            BlockState state,
            BlockPos pos,
            BlockHitResult ray
    ) {
        Block heldBlock = blockItem.getBlock();
        PlacementPlan plan = findPlacement(player, level, state, pos, ray, heldBlock);
        if (plan == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!plan.merge()) {
            return plan.offset().placeInWorld(level, blockItem, player, hand, ray);
        }

        ItemStack stack = player.getItemInHand(hand);
        boolean added = WoodenPostBlockItem.tryAddMember(
                level,
                plan.destination(),
                plan.position(),
                heldBlock,
                player,
                stack,
                ray.getDirection()
        );
        return added
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.FAIL;
    }

    @Nullable
    private static PlacementPlan findPlacement(
            Player player,
            Level level,
            BlockState state,
            BlockPos pos,
            BlockHitResult ray,
            @Nullable Block heldBlock
    ) {
        if (ray.getDirection().getAxis() == Direction.Axis.Y
                || player != null && player.isSecondaryUseActive()) {
            return null;
        }

        WoodenPostPosition position = WoodenPostBlock.targetedMember(
                state,
                pos,
                ray,
                player == null ? null : player.getEyePosition()
        );
        if (position == null) {
            return null;
        }

        List<Direction> directions = IPlacementHelper.orderedByDistance(
                pos,
                ray.getLocation(),
                direction -> direction.getAxis() == Direction.Axis.Y
        );
        int range = placementAssistRange(player);
        for (Direction direction : directions) {
            for (int distance = 1; distance <= range; distance++) {
                BlockPos destination = pos.relative(direction, distance);
                BlockState destinationState = level.getBlockState(destination);

                if (destinationState.getBlock() instanceof WoodenPostBlock) {
                    WoodenPostArrangement arrangement = WoodenPostBlock.getArrangement(destinationState);
                    if (arrangement.contains(position)) {
                        continue;
                    }
                    if (destinationState.getBlock() == heldBlock && arrangement.canAdd(position)) {
                        WoodenPostArrangement merged = arrangement.add(position);
                        PlacementOffset offset = PlacementOffset.success(
                                destination,
                                blockState -> transformedState(blockState, merged, level, destination)
                        );
                        return new PlacementPlan(offset, destination, position, true);
                    }
                    break;
                }

                if (destinationState.canBeReplaced()) {
                    WoodenPostArrangement singleton = WoodenPostArrangement.singleton(position);
                    PlacementOffset offset = PlacementOffset.success(
                            destination,
                            blockState -> transformedState(blockState, singleton, level, destination)
                    );
                    return new PlacementPlan(offset, destination, position, false);
                }
                break;
            }
        }
        return null;
    }

    private static BlockState transformedState(
            BlockState state,
            WoodenPostArrangement arrangement,
            Level level,
            BlockPos pos
    ) {
        return Block.updateFromNeighbourShapes(
                state.setValue(WoodenPostBlock.POST_POSITION, arrangement),
                level,
                pos
        );
    }

    @Nullable
    private static Block heldPostBlock(Player player) {
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            Block block = heldPostBlock(player.getItemInHand(hand));
            if (block != null) {
                return block;
            }
        }
        return null;
    }

    @Nullable
    private static Block heldPostBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof WoodenPostBlock) {
            return item.getBlock();
        }
        return null;
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

    private record PlacementPlan(
            PlacementOffset offset,
            BlockPos destination,
            WoodenPostPosition position,
            boolean merge
    ) {
    }
}
