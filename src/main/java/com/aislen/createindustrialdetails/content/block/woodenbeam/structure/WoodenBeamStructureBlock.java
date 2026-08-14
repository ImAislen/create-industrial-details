package com.aislen.createindustrialdetails.content.block.woodenbeam.structure;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.cake.struts.content.structure.ConnectionKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

/** Invisible cached-collision carrier used only between Wooden Beam anchors. */
public final class WoodenBeamStructureBlock extends Block implements SimpleWaterloggedBlock {

    public WoodenBeamStructureBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected @NotNull VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level instanceof Level world ? WoodenBeamStructureShapes.getShape(world, pos) : Shapes.empty();
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        for (ConnectionKey key : WoodenBeamStructureShapes.getConnectionsAt(level, pos)) {
            BlockState anchor = level.getBlockState(key.a());
            level.levelEvent(player, 2001, pos, getId(anchor));
            return;
        }
    }

    @Override
    protected @NotNull BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbourState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighbourPos
    ) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    protected @NotNull FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER
        );
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && newState.isAir()) {
            for (ConnectionKey key : Set.copyOf(WoodenBeamStructureShapes.getConnectionsAt(level, pos))) {
                WoodenBeamStructureShapes.unregisterConnection(level, key.a(), key.b());
                removeFromAnchor(level, key.a(), key.b());
                removeFromAnchor(level, key.b(), key.a());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void removeFromAnchor(Level level, BlockPos anchorPos, BlockPos peerPos) {
        if (level.getBlockEntity(anchorPos) instanceof WoodenBeamBlockEntity anchor) {
            anchor.removeConnection(peerPos);
            if (anchor.connectionCount() == 0) {
                level.destroyBlock(anchorPos, true);
            }
        }
    }

    @Override
    protected @NotNull RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
