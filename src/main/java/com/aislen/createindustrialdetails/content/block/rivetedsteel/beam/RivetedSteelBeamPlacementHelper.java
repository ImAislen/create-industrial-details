package com.aislen.createindustrialdetails.content.block.rivetedsteel.beam;

import java.util.List;
import java.util.function.Predicate;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RivetedSteelBeamPlacementHelper implements IPlacementHelper {

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack ->
                stack.getItem() instanceof BlockItem blockItem
                        && blockItem.getBlock() instanceof RivetedSteelBeamBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state ->
                state.getBlock() instanceof RivetedSteelBeamBlock;
    }

    private boolean canExtendToward(BlockState state, Direction side) {
        if (!(state.getBlock() instanceof RivetedSteelBeamBlock)) {
            return false;
        }

        Axis axis = side.getAxis();
        boolean x = state.getValue(RivetedSteelBeamBlock.X);
        boolean z = state.getValue(RivetedSteelBeamBlock.Z);

        if (!x && !z) {
            return axis == Axis.Y;
        }

        if (x && z) {
            return true;
        }

        return axis == (x ? Axis.X : Axis.Z);
    }

    private int attachedBeams(
            Level level,
            BlockPos pos,
            Direction direction
    ) {
        BlockPos checkPos = pos.relative(direction);
        BlockState state = level.getBlockState(checkPos);
        int count = 0;

        while (canExtendToward(state, direction)) {
            count++;
            checkPos = checkPos.relative(direction);
            state = level.getBlockState(checkPos);
        }

        return count;
    }

    private BlockState withAxis(
            BlockState state,
            Axis axis,
            BlockState sourceState
    ) {
        state = state
                .setValue(RivetedSteelBeamBlock.X, axis == Axis.X)
                .setValue(RivetedSteelBeamBlock.Z, axis == Axis.Z)
                .setValue(RivetedSteelBeamBlock.AXIS, axis);

        if (axis == Axis.Y) {
            state = state.setValue(
                    RivetedSteelBeamBlock.VERTICAL_ROTATED,
                    sourceState.getValue(
                            RivetedSteelBeamBlock.VERTICAL_ROTATED
                    )
            );
        }

        return state;
    }

    @Override
    public PlacementOffset getOffset(
            Player player,
            Level level,
            BlockState state,
            BlockPos pos,
            BlockHitResult ray
    ) {
        List<Direction> directions =
                IPlacementHelper.orderedByDistance(
                        pos,
                        ray.getLocation(),
                        direction -> canExtendToward(state, direction)
                );

        for (Direction direction : directions) {
            int range =
                    AllConfigs.server()
                            .equipment
                            .placementAssistRange
                            .get();

            if (player != null) {
                AttributeInstance reach =
                        player.getAttribute(
                                Attributes.BLOCK_INTERACTION_RANGE
                        );

                if (
                        reach != null
                                && reach.hasModifier(
                                ExtendoGripItem
                                        .singleRangeAttributeModifier
                                        .id()
                        )
                ) {
                    range += 4;
                }
            }

            int beams =
                    attachedBeams(level, pos, direction);

            if (beams >= range) {
                continue;
            }

            BlockPos newPos =
                    pos.relative(direction, beams + 1);

            BlockState newState =
                    level.getBlockState(newPos);

            if (!newState.canBeReplaced()) {
                continue;
            }

            return PlacementOffset.success(
                    newPos,
                    blockState ->
                            Block.updateFromNeighbourShapes(
                                    withAxis(
                                            blockState,
                                            direction.getAxis(),
                                            state
                                    ),
                                    level,
                                    newPos
                            )
            );
        }

        return PlacementOffset.fail();
    }
}
