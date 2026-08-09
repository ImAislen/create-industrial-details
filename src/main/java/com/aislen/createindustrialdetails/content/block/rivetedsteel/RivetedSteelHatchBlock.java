package com.aislen.createindustrialdetails.content.block.rivetedsteel;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RivetedSteelHatchBlock extends TrapDoorBlock {

    public static final MapCodec<RivetedSteelHatchBlock> CODEC =
            simpleCodec(RivetedSteelHatchBlock::new);


    private static final VoxelShape TOP_FRAME = Shapes.or(
            // North edge
            Block.box(0, 13, 0, 16, 16, 2),

            // South edge
            Block.box(0, 13, 14, 16, 16, 16),

            // West edge
            Block.box(0, 13, 2, 2, 16, 14),

            // East edge
            Block.box(14, 13, 2, 16, 16, 14)
    );


    private static final VoxelShape TOP_LID_NORTH =
            Block.box(2, 16, 2, 14, 28, 4);

    private static final VoxelShape TOP_LID_SOUTH =
            Block.box(2, 16, 12, 14, 28, 14);

    private static final VoxelShape TOP_LID_WEST =
            Block.box(2, 16, 2, 4, 28, 14);

    private static final VoxelShape TOP_LID_EAST =
            Block.box(12, 16, 2, 14, 28, 14);


    private static final VoxelShape TOP_OPEN_NORTH =
            Shapes.or(TOP_FRAME, TOP_LID_NORTH);

    private static final VoxelShape TOP_OPEN_SOUTH =
            Shapes.or(TOP_FRAME, TOP_LID_SOUTH);

    private static final VoxelShape TOP_OPEN_WEST =
            Shapes.or(TOP_FRAME, TOP_LID_WEST);

    private static final VoxelShape TOP_OPEN_EAST =
            Shapes.or(TOP_FRAME, TOP_LID_EAST);

    public RivetedSteelHatchBlock(Properties properties) {

        super(BlockSetType.COPPER, properties);
    }

    @Override
    public MapCodec<RivetedSteelHatchBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {

        if (state.getValue(OPEN)
                && state.getValue(HALF) == Half.TOP) {


            Direction lidSide =
                    state.getValue(FACING).getOpposite();

            return switch (lidSide) {
                case NORTH -> TOP_OPEN_NORTH;
                case SOUTH -> TOP_OPEN_SOUTH;
                case WEST -> TOP_OPEN_WEST;
                case EAST -> TOP_OPEN_EAST;


                default -> TOP_OPEN_NORTH;
            };
        }

        return super.getShape(
                state,
                level,
                pos,
                context
        );
    }
}
