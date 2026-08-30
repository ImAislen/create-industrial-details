package com.aislen.createindustrialdetails.content.block.airduct;

import com.mojang.serialization.MapCodec;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AirDuctBlock extends PipeBlock {

    public static final MapCodec<AirDuctBlock> CODEC = simpleCodec(AirDuctBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new AirDuctPlacementHelper());

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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !helper.matchesItem(stack)
                || !helper.matchesState(state)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        PlacementOffset offset = helper.getOffset(player, level, state, pos, hitResult);
        if (!offset.isSuccessful()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return offset.placeInWorld(level, blockItem, player, hand, hitResult);
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

    @Nullable
    static Direction.Axis placementAssistAxis(BlockState state) {
        Direction connectedDirection = null;
        int connections = 0;

        for (Direction direction : Direction.values()) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                continue;
            }
            connections++;
            if (connections > 2) {
                return null;
            }
            if (connectedDirection == null) {
                connectedDirection = direction;
            } else if (connectedDirection.getOpposite() != direction) {
                return null;
            }
        }

        return connectedDirection == null
                ? state.getValue(AXIS)
                : connectedDirection.getAxis();
    }

    private boolean canConnectTo(BlockState state) {
        return state.getBlock() instanceof AirDuctBlock;
    }
}
