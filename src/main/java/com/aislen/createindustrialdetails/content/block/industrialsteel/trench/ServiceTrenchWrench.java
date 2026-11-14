package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

final class ServiceTrenchWrench {
    private ServiceTrenchWrench() {}

    static InteractionResult rotate(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            var facing = state.getValue(ServiceTrenchBlock.FACING).getAxis() == net.minecraft.core.Direction.Axis.X
                    ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.EAST;
            context.getLevel().setBlock(context.getClickedPos(), state.setValue(ServiceTrenchBlock.FACING, facing), Block.UPDATE_ALL);
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    static InteractionResult remove(BlockState state, UseOnContext context, BlockState restored, boolean kinetic) {
        if (!(context.getLevel() instanceof ServerLevel level))
            return InteractionResult.SUCCESS;
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        var event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled())
            return InteractionResult.SUCCESS;
        if (kinetic) {
            KineticBlockEntity.switchToBlockState(level, pos, restored);
        } else {
            FluidTransportBehaviour.cacheFlows(level, pos);
            level.setBlockAndUpdate(pos, restored);
            FluidTransportBehaviour.loadFlows(level, pos);
        }
        if (player != null && !player.isCreative())
            player.getInventory().placeItemBackInInventory(new ItemStack(ModBlocks.SERVICE_TRENCH.get()));
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }
}
