package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.*;

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

    static InteractionResult wrenchPipe(BlockState state, UseOnContext context) {
        if (!state.getValue(OPEN)) return rotate(state, context);

        Direction.Axis axis = null;
        if (state.getBlock() instanceof ServiceTrenchPipeBlock) {
            axis = ServiceTrenchUtilities.straightAxis(state);
            if (axis == null || axis == context.getClickedFace().getAxis()) return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        var pos = context.getClickedPos();
        BlockState replacement;
        if (state.getBlock() instanceof ServiceTrenchGlassPipeBlock glass) {
            replacement = EncasedPipeBlock.transferSixWayProperties(glass.toRegularPipe(level, pos, state),
                    ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState());
        } else {
            replacement = ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, axis);
        }
        FluidTransportBehaviour.cacheFlows(level, pos);
        boolean changed = level.setBlockAndUpdate(pos, retainHousing(state, replacement));
        FluidTransportBehaviour.loadFlows(level, pos);
        if (!changed) return InteractionResult.FAIL;

        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    private static BlockState retainHousing(BlockState from, BlockState to) {
        return to.setValue(FACING, from.getValue(FACING))
                .setValue(WATERLOGGED, from.getValue(WATERLOGGED))
                .setValue(OPEN, from.getValue(OPEN));
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
        if (state.getValue(OPEN)) {
            BlockState empty = retainHousing(state, ModBlocks.SERVICE_TRENCH.get().defaultBlockState());
            if (level.getBlockState(pos) != state
                    || !level.setBlockAndUpdate(pos, ServiceTrenchHousing.withCaps(empty, level, pos)))
                return InteractionResult.FAIL;
            if (player != null && !player.isCreative())
                player.getInventory().placeItemBackInInventory(kinetic ? AllBlocks.SHAFT.asStack() : AllBlocks.FLUID_PIPE.asStack());
            IWrenchable.playRemoveSound(level, pos);
            return InteractionResult.SUCCESS;
        }
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
