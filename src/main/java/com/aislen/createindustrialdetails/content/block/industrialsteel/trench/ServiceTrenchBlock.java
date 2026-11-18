package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ServiceTrenchBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, IWrenchable {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty CAP_NEGATIVE = BooleanProperty.create("cap_negative");
    public static final BooleanProperty CAP_POSITIVE = BooleanProperty.create("cap_positive");

    public ServiceTrenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(ServiceTrenchHousing.defaults(defaultBlockState()).setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<ServiceTrenchBlock> codec() {
        return simpleCodec(ServiceTrenchBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
        ServiceTrenchHousing.addProperties(builder);
    }

    @Override
    public void onBlockStateChange(LevelReader level, BlockPos pos, BlockState oldState, BlockState newState) {
        ServiceTrenchHousing.stateChanged(level, pos, oldState, newState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return ServiceTrenchHousing.toggle(state, level, pos, player);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return ServiceTrenchHousing.passesSkylight(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getAxis() == Direction.Axis.X ? Direction.EAST : Direction.NORTH)
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    public net.minecraft.world.InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        return ServiceTrenchWrench.rotate(state, context);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return ServiceTrenchHousing.orient(state, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ServiceTrenchShapes.empty(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED))
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return ServiceTrenchHousing.updateEnd(state, direction, neighbor);
    }
}
