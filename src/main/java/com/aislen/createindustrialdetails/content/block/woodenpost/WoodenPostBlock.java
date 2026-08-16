package com.aislen.createindustrialdetails.content.block.woodenpost;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WoodenPostBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<WoodenPostBlock> CODEC = simpleCodec(WoodenPostBlock::new);
    public static final EnumProperty<WoodenPostPosition> POST_POSITION =
            EnumProperty.create("post_position", WoodenPostPosition.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new WoodenPostPlacementHelper());

    private final int fireSpreadSpeed;
    private final int flammability;

    public WoodenPostBlock(Properties properties) {
        this(properties, 0, 0);
    }

    public WoodenPostBlock(Properties properties, int fireSpreadSpeed, int flammability) {
        super(properties);
        this.fireSpreadSpeed = fireSpreadSpeed;
        this.flammability = flammability;
        registerDefaultState(stateDefinition.any()
                .setValue(POST_POSITION, WoodenPostPosition.CENTER)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                || hitResult.getDirection().getAxis() == Direction.Axis.Y
                || !(stack.getItem() instanceof BlockItem blockItem)) {
            return result;
        }

        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!helper.matchesItem(stack)) {
            return result;
        }

        return helper.getOffset(player, level, state, pos, hitResult)
                .placeInWorld(level, blockItem, player, hand, hitResult);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos placementPos = context.getClickedPos();
        WoodenPostPosition position = snappedPosition(context, placementPos);

        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            BlockState supportState = context.getLevel().getBlockState(
                    placementPos.relative(clickedFace.getOpposite())
            );
            if (supportState.getBlock() instanceof WoodenPostBlock) {
                position = supportState.getValue(POST_POSITION);
            }
        }

        FluidState fluidState = context.getLevel().getFluidState(placementPos);
        return defaultBlockState()
                .setValue(POST_POSITION, position)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    private static WoodenPostPosition snappedPosition(BlockPlaceContext context, BlockPos placementPos) {
        Vec3 hit = context.getClickLocation();
        return WoodenPostPosition.nearest(
                hit.x - placementPos.getX(),
                hit.z - placementPos.getZ()
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(POST_POSITION).shape();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return state.getValue(POST_POSITION).shape();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POST_POSITION, WATERLOGGED);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireSpreadSpeed;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammability;
    }
}
