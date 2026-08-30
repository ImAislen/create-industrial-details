package com.aislen.createindustrialdetails.content.block.airduct;

import com.aislen.createindustrialdetails.registry.ModBlocks;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.Predicate;

final class AirDuctPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item
                && item.getBlock() == ModBlocks.AIR_DUCT.get();
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() == ModBlocks.AIR_DUCT.get()
                && AirDuctBlock.placementAssistAxis(state) != null;
    }

    @Override
    public PlacementOffset getOffset(
            Player player,
            Level level,
            BlockState state,
            BlockPos pos,
            BlockHitResult ray
    ) {
        Direction.Axis axis = AirDuctBlock.placementAssistAxis(state);
        if (axis == null) {
            return PlacementOffset.fail();
        }

        List<Direction> directions = IPlacementHelper.orderedByDistanceOnlyAxis(
                pos,
                ray.getLocation(),
                axis
        );
        int range = placementAssistRange(player);
        for (Direction direction : directions) {
            for (int distance = 1; distance <= range; distance++) {
                BlockPos destination = pos.relative(direction, distance);
                BlockState destinationState = level.getBlockState(destination);

                if (destinationState.getBlock() == state.getBlock()) {
                    if (AirDuctBlock.placementAssistAxis(destinationState) == axis) {
                        continue;
                    }
                    break;
                }

                if (destinationState.canBeReplaced()) {
                    return PlacementOffset.success(
                            destination,
                            placedState -> placedState.setValue(AirDuctBlock.AXIS, axis)
                    );
                }
                break;
            }
        }
        return PlacementOffset.fail();
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
