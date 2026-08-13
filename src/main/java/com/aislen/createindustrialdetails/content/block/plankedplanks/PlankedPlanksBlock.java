package com.aislen.createindustrialdetails.content.block.plankedplanks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PlankedPlanksBlock extends Block {

    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    private static final VoxelShape BOTTOM_SHAPE = Block.box(
            0, 0, 0,
            16, 1, 16
    );

    private static final VoxelShape TOP_SHAPE = Block.box(
            0, 15, 0,
            16, 16, 16
    );

    public PlankedPlanksBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(HALF, Half.BOTTOM)
        );
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return state.getValue(HALF) == Half.TOP
                ? TOP_SHAPE
                : BOTTOM_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return state.getValue(HALF) == Half.TOP
                ? TOP_SHAPE
                : BOTTOM_SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();

        double localY =
                context.getClickLocation().y
                        - context.getClickedPos().getY();

        Half half =
                clickedFace == Direction.DOWN
                        || (clickedFace != Direction.UP && localY > 0.5)
                        ? Half.TOP
                        : Half.BOTTOM;

        return defaultBlockState()
                .setValue(HALF, half);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(HALF);
    }
}