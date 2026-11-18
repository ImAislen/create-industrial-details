package com.aislen.createindustrialdetails.content.block.cable;

import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.function.Predicate;

final class CableTrayPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CableTrayBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof CableTrayBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hit) {
        if (player == null || player.isShiftKeyDown() || !player.mayBuild() || player.isSpectator() || !matchesState(state)) return PlacementOffset.fail();
        var axis = state.getValue(CableTrayBlock.AXIS);
        for (var direction : IPlacementHelper.orderedByDistanceOnlyAxis(pos, hit.getLocation(), axis)) {
            for (int distance = 1; distance <= AllConfigs.server().equipment.placementAssistRange.get(); distance++) {
                BlockPos target = pos.relative(direction, distance);
                if (!level.isInWorldBounds(target) || !level.hasChunkAt(target)) break;
                BlockState next = level.getBlockState(target);
                if (next.is(state.getBlock())) {
                    if (next.getValue(CableTrayBlock.AXIS) == axis && next.getValue(CableTrayBlock.FACING) == state.getValue(CableTrayBlock.FACING)) continue;
                    break;
                }
                if (!next.canBeReplaced()) break;
                BlockState placed = CableTrayBlock.alignToControlBox(state, level, target);
                var stack = matchesItem(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
                if (!level.mayInteract(player, target) || !player.mayUseItemAt(target, direction, stack)
                        || !placed.canSurvive(level, target) || !level.isUnobstructed(placed, target, net.minecraft.world.phys.shapes.CollisionContext.of(player))) break;
                return PlacementOffset.success(target, ignored -> placed);
            }
        }
        return PlacementOffset.fail();
    }
}
