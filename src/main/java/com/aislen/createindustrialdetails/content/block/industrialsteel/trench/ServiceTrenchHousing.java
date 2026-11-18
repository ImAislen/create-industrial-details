package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.Tags;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.*;

final class ServiceTrenchHousing {
    private ServiceTrenchHousing() {}

    static void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, CAP_NEGATIVE, CAP_POSITIVE);
    }

    static BlockState defaults(BlockState state) {
        boolean empty = state.getBlock() instanceof ServiceTrenchBlock;
        return state.setValue(OPEN, false).setValue(CAP_NEGATIVE, empty).setValue(CAP_POSITIVE, empty);
    }

    static boolean isTrench(BlockState state) {
        return state.hasProperty(CAP_NEGATIVE) && state.hasProperty(CAP_POSITIVE);
    }

    static BlockState orient(BlockState state, Direction facing) {
        boolean reverseEnds = state.getValue(FACING).getAxisDirection() != facing.getAxisDirection();
        if (reverseEnds) {
            state = state.setValue(CAP_NEGATIVE, state.getValue(CAP_POSITIVE))
                    .setValue(CAP_POSITIVE, state.getValue(CAP_NEGATIVE));
        }
        return state.setValue(FACING, facing);
    }

    static BlockState withCaps(BlockState state, LevelAccessor level, BlockPos pos) {
        if (!(state.getBlock() instanceof ServiceTrenchBlock)) return withoutCaps(state);
        Direction negative = Direction.get(Direction.AxisDirection.NEGATIVE, state.getValue(FACING).getAxis());
        return withEnd(withEnd(state, level, pos, negative), level, pos, negative.getOpposite());
    }

    private static BlockState withEnd(BlockState state, LevelAccessor level, BlockPos pos, Direction end) {
        BlockPos neighbor = pos.relative(end);
        return level.hasChunkAt(neighbor) ? updateEnd(state, end, level.getBlockState(neighbor)) : state;
    }

    static BlockState updateEnd(BlockState state, Direction side, BlockState neighbor) {
        if (!(state.getBlock() instanceof ServiceTrenchBlock)) return withoutCaps(state);
        if (side.getAxis() != state.getValue(FACING).getAxis()) return state;
        boolean continuation = isTrench(neighbor) && neighbor.getValue(FACING).getAxis() == side.getAxis();
        return state.setValue(side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? CAP_NEGATIVE : CAP_POSITIVE,
                !continuation);
    }

    private static BlockState withoutCaps(BlockState state) {
        return state.setValue(CAP_NEGATIVE, false).setValue(CAP_POSITIVE, false);
    }

    static void stateChanged(LevelReader reader, BlockPos pos, BlockState oldState, BlockState state) {
        if (reader instanceof Level level && !level.isClientSide
                && (!sameUtility(oldState, state) || oldState.getValue(FACING).getAxis() != state.getValue(FACING).getAxis()))
            refresh(level, pos, state);
    }

    static boolean sameUtility(BlockState oldState, BlockState newState) {
        if (oldState.getBlock() != newState.getBlock()) return false;
        if (oldState.getValue(WATERLOGGED) != newState.getValue(WATERLOGGED)) return false;
        if (oldState.hasProperty(BlockStateProperties.AXIS))
            return oldState.getValue(BlockStateProperties.AXIS) == newState.getValue(BlockStateProperties.AXIS);
        if (oldState.getBlock() instanceof ServiceTrenchPipeBlock) {
            for (var side : FluidPipeBlock.PROPERTY_BY_DIRECTION.values()) {
                if (!oldState.getValue(side).equals(newState.getValue(side))) return false;
            }
        }
        return true;
    }

    static void refresh(Level level, BlockPos pos, BlockState state) {
        BlockState updated = withCaps(state, level, pos);
        if (updated != state && level.getBlockState(pos) == state)
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    static InteractionResult toggle(BlockState state, Level level, BlockPos pos, Player player) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        var offhand = player.getOffhandItem();
        if (AllItems.WRENCH.isIn(offhand) || offhand.is(Tags.Items.TOOLS_WRENCH)
                || offhand.is(ModBlocks.SERVICE_TRENCH.asItem()) || ServiceTrenchUtilities.isUtilityItem(offhand))
            return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player, pos))
            return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState toggled = state.cycle(OPEN);
        if (!toggled.getValue(OPEN) && !level.isUnobstructed(toggled, pos, CollisionContext.empty()))
            return InteractionResult.FAIL;
        if (!level.setBlock(pos, toggled, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE))
            return InteractionResult.FAIL;

        SoundType sound = state.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.6F,
                toggled.getValue(OPEN) ? 0.8F : 1.1F);
        return InteractionResult.CONSUME;
    }

    static boolean passesSkylight(BlockState state) {
        return state.getFluidState().isEmpty();
    }
}
