package com.aislen.createindustrialdetails.content.block.cable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CableTrayBlock extends Block implements IWrenchable {
    public static final MapCodec<CableTrayBlock> CODEC = simpleCodec(CableTrayBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private static final VoxelShape Z_SHAPE = Shapes.or(
            Block.box(3, 0, 0, 13, 2.5, 16),
            Block.box(2, 0, 0, 3, 3.5, 16),
            Block.box(13, 0, 0, 14, 3.5, 16)
    );
    private static final VoxelShape X_SHAPE = Shapes.or(
            Block.box(0, 0, 3, 16, 2.5, 13),
            Block.box(0, 0, 2, 16, 3.5, 3),
            Block.box(0, 0, 13, 16, 3.5, 14)
    );

    public CableTrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    public MapCodec<CableTrayBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
        return state.setValue(AXIS, rotation.rotate(direction).getAxis());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
