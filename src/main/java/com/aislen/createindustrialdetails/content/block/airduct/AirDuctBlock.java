package com.aislen.createindustrialdetails.content.block.airduct;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class AirDuctBlock extends PipeBlock {

    public static final MapCodec<AirDuctBlock> CODEC = simpleCodec(AirDuctBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public AirDuctBlock(Properties properties) {
        super(6 / 16f, properties);

        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                AXIS,
                NORTH,
                SOUTH,
                EAST,
                WEST,
                UP,
                DOWN
        );

        super.createBlockStateDefinition(builder);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        LevelAccessor level = context.getLevel();

        BlockState state = defaultBlockState()
                .setValue(AXIS, context.getClickedFace().getAxis());

        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));

            state = state.setValue(
                    PROPERTY_BY_DIRECTION.get(direction),
                    canConnectTo(neighbour)
            );
        }

        return state;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbourState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighbourPos
    ) {
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);

        return state.setValue(property, canConnectTo(neighbourState));
    }

    private boolean canConnectTo(BlockState state) {
        return state.getBlock() instanceof AirDuctBlock;
    }
}