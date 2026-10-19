package com.aislen.createindustrialdetails.content.block.supports;

import com.mojang.serialization.MapCodec;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SteelWallCornerBracketBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SteelWallCornerBracketBlock> CODEC = simpleCodec(SteelWallCornerBracketBlock::new);
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    private static final VoxelShaper TOP_SHAPES = VoxelShaper.forHorizontal(Shapes.or(
            Block.box(13.5, 4, 0, 16, 16, 2.5),
            Block.box(13.5, 15, 2.5, 16, 16, 12),
            Block.box(4, 15, 0, 13.5, 16, 2.5)
    ), Direction.SOUTH);
    private static final VoxelShaper BOTTOM_SHAPES = VoxelShaper.forHorizontal(Shapes.or(
            Block.box(13.5, 0, 0, 16, 12, 2.5),
            Block.box(13.5, 0, 2.5, 16, 1, 12),
            Block.box(4, 0, 0, 13.5, 1, 2.5)
    ), Direction.SOUTH);

    public SteelWallCornerBracketBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(HALF, Half.TOP));
    }

    @Override
    public MapCodec<SteelWallCornerBracketBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isHorizontal()) {
            return null;
        }
        BlockPos pos = context.getClickedPos();
        Direction side = clickedFace.getCounterClockWise();
        double sideHit = (context.getClickLocation().x - pos.getX() - 0.5) * side.getStepX()
                + (context.getClickLocation().z - pos.getZ() - 0.5) * side.getStepZ();
        Direction facing = sideHit >= 0 ? clickedFace : side;
        BlockState state = defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HALF, context.getClickLocation().y - pos.getY() >= 0.5 ? Half.TOP : Half.BOTTOM);
        return state;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)).getCounterClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == Half.TOP ? TOP_SHAPES : BOTTOM_SHAPES).get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
