package com.aislen.createindustrialdetails.block;

import com.simibubi.create.content.decoration.girder.GirderBlock;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RivetedSteelBeamBlock extends GirderBlock {

    // Properties

    public static final BooleanProperty VERTICAL_ROTATED =
            BooleanProperty.create("vertical_rotated");


    // Placement

    private static final int PLACEMENT_HELPER_ID =
            PlacementHelpers.register(new RivetedSteelBeamPlacementHelper());


    // Shapes

    private static final VoxelShape SHAPE_Z = Shapes.or(
            Block.box(1.5, 0, 0, 14.5, 3, 16),
            Block.box(3.5, 3, 0, 12.5, 13, 16),
            Block.box(1.5, 13, 0, 14.5, 16, 16)
    );

    private static final VoxelShape SHAPE_X = Shapes.or(
            Block.box(0, 0, 1.5, 16, 3, 14.5),
            Block.box(0, 3, 3.5, 16, 13, 12.5),
            Block.box(0, 13, 1.5, 16, 16, 14.5)
    );

    private static final VoxelShape SHAPE_Y = Shapes.or(
            Block.box(1.5, 0, 0, 14.5, 16, 3),
            Block.box(3.5, 0, 3, 12.5, 16, 13),
            Block.box(1.5, 0, 13, 14.5, 16, 16)
    );

    private static final VoxelShape SHAPE_Y_ROTATED = Shapes.or(
            Block.box(0, 0, 1.5, 3, 16, 14.5),
            Block.box(3, 0, 3.5, 13, 16, 12.5),
            Block.box(13, 0, 1.5, 16, 16, 14.5)
    );


    // Setup

    public RivetedSteelBeamBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                defaultBlockState()
                        .setValue(VERTICAL_ROTATED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
        builder.add(VERTICAL_ROTATED);
    }


    // Placement Assistance

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        ItemInteractionResult result = super.useItemOn(
                stack,
                state,
                level,
                pos,
                player,
                hand,
                hitResult
        );

        if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return result;
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return result;
        }

        IPlacementHelper helper =
                PlacementHelpers.get(PLACEMENT_HELPER_ID);

        if (!helper.matchesItem(stack)) {
            return result;
        }

        return helper
                .getOffset(
                        player,
                        level,
                        state,
                        pos,
                        hitResult
                )
                .placeInWorld(
                        level,
                        blockItem,
                        player,
                        hand,
                        hitResult
                );
    }


    // Placement

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);

        if (state == null) {
            return null;
        }

        if (state.getValue(AXIS) == Direction.Axis.Y) {
            state = state.setValue(
                    VERTICAL_ROTATED,
                    context.getHorizontalDirection().getAxis()
                            == Direction.Axis.X
            );
        }

        return state;
    }


    // Shapes

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeForState(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeForState(state);
    }

    private static VoxelShape shapeForState(BlockState state) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            case Y -> state.getValue(VERTICAL_ROTATED)
                    ? SHAPE_Y_ROTATED
                    : SHAPE_Y;
        };
    }


    // Rotation

    @Override
    public BlockState rotate(
            BlockState state,
            Rotation rotation
    ) {
        boolean oldX = state.getValue(X);
        boolean oldZ = state.getValue(Z);

        BlockState rotated = super.rotate(state, rotation);

        boolean quarterTurn =
                rotation == Rotation.CLOCKWISE_90
                        || rotation == Rotation.COUNTERCLOCKWISE_90;

        if (!quarterTurn) {
            return rotated;
        }

        rotated = rotated
                .setValue(X, oldZ)
                .setValue(Z, oldX);

        if (state.getValue(AXIS) == Direction.Axis.Y) {
            rotated = rotated.setValue(
                    VERTICAL_ROTATED,
                    !state.getValue(VERTICAL_ROTATED)
            );
        }

        return rotated;
    }
}