package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class ServiceTrenchGlassPipeBlock extends GlassFluidPipeBlock {
    public ServiceTrenchGlassPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(ServiceTrenchHousing.defaults(defaultBlockState()).setValue(ServiceTrenchBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ServiceTrenchBlock.FACING);
        ServiceTrenchHousing.addProperties(builder);
    }

    public void encase(BlockState state, Level level, BlockPos pos, Player player, ItemStack stack) {
        if (level.isClientSide) return;
        Direction facing = player.getDirection();
        Direction.Axis axis = state.getValue(AXIS);
        if (axis.isHorizontal() && facing.getAxis() != axis)
            facing = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        BlockState encased = defaultBlockState().setValue(AXIS, axis).setValue(ALT, state.getValue(ALT))
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED))
                .setValue(ServiceTrenchBlock.FACING, facing);
        FluidTransportBehaviour.cacheFlows(level, pos);
        level.setBlockAndUpdate(pos, encased);
        FluidTransportBehaviour.loadFlows(level, pos);
        if (!player.isCreative()) stack.shrink(1);
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
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!ServiceTrenchHousing.sameUtility(oldState, state)) super.onPlace(state, level, pos, oldState, moved);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!ServiceTrenchHousing.sameUtility(state, newState)) super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SERVICE_TRENCH_GLASS_PIPE.get();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return ServiceTrenchWrench.wrenchPipe(state, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        BlockState restored = AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(AXIS, state.getValue(AXIS))
                .setValue(ALT, state.getValue(ALT))
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
        return ServiceTrenchWrench.remove(state, context, restored, false);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity be) {
        return super.getRequiredItems(state, be).union(ItemRequirement.of(ModBlocks.SERVICE_TRENCH.get().defaultBlockState(), null));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult hit, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModBlocks.SERVICE_TRENCH.get());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ServiceTrenchShapes.occupied(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ServiceTrenchShapes.occupied(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED))
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return ServiceTrenchHousing.updateEnd(super.updateShape(state, direction, neighbor, level, pos, neighborPos),
                direction, neighbor);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return ServiceTrenchHousing.orient(super.rotate(state, rotation),
                rotation.rotate(state.getValue(ServiceTrenchBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return ServiceTrenchHousing.orient(state, mirror.mirror(state.getValue(ServiceTrenchBlock.FACING)));
    }
}
