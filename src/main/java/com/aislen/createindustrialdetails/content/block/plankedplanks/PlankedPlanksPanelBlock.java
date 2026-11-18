package com.aislen.createindustrialdetails.content.block.plankedplanks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PlankedPlanksPanelBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final MapCodec<PlankedPlanksPanelBlock> CODEC = simpleCodec(PlankedPlanksPanelBlock::new);

    private static final VoxelShape SOUTH_SHAPE = panelShape(0, 0, 16, 1);
    private static final VoxelShape WEST_SHAPE = panelShape(15, 0, 16, 16);
    private static final VoxelShape NORTH_SHAPE = panelShape(0, 15, 16, 16);
    private static final VoxelShape EAST_SHAPE = panelShape(0, 0, 1, 16);

    public PlankedPlanksPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<PlankedPlanksPanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction facing = clickedFace.getAxis().isHorizontal()
                ? clickedFace
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return rotate(state, Rotation.CLOCKWISE_90);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    private static VoxelShape panelShape(double minX, double minZ, double maxX, double maxZ) {
        return Shapes.or(
                box(minX, 0.00835, minZ, maxX, 4.34165, maxZ),
                box(minX, 5.33335, minZ, maxX, 9.66665, maxZ),
                box(minX, 10.65835, minZ, maxX, 14.99165, maxZ)
        );
    }
}
