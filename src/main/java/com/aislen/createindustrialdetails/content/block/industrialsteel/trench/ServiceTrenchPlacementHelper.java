package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.function.Predicate;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.FACING;
import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.WATERLOGGED;

final class ServiceTrenchPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.is(ModBlocks.SERVICE_TRENCH.asItem());
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return ServiceTrenchHousing::isTrench;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hit) {
        if (player == null || player.isShiftKeyDown() || !player.mayBuild() || player.isSpectator() || !matchesState(state))
            return PlacementOffset.fail();
        Direction facing = state.getValue(FACING);
        var stack = matchesItem(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
        if (!matchesItem(stack)) return PlacementOffset.fail();
        int range = AllConfigs.server().equipment.placementAssistRange.get();
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) range += 4;

        for (Direction end : IPlacementHelper.orderedByDistanceOnlyAxis(pos, hit.getLocation(), facing.getAxis())) {
            for (int distance = 1; distance <= range; distance++) {
                BlockPos next = pos.relative(end, distance);
                if (!level.isInWorldBounds(next) || !level.getWorldBorder().isWithinBounds(next) || !level.hasChunkAt(next))
                    break;
                BlockState neighbor = level.getBlockState(next);
                if (matchesState(neighbor) && neighbor.getValue(FACING).getAxis() == facing.getAxis()) continue;
                if (!neighbor.canBeReplaced() || !level.mayInteract(player, next)
                        || !player.mayUseItemAt(next, end, stack)) break;
                BlockState placed = ServiceTrenchHousing.withCaps(ModBlocks.SERVICE_TRENCH.get().defaultBlockState()
                        .setValue(FACING, facing).setValue(WATERLOGGED, neighbor.getFluidState().getType() == Fluids.WATER),
                        level, next);
                if (!placed.canSurvive(level, next) || !level.isUnobstructed(placed, next, CollisionContext.empty())) break;
                return PlacementOffset.success(next, ignored -> placed);
            }
        }
        return PlacementOffset.fail();
    }
}
