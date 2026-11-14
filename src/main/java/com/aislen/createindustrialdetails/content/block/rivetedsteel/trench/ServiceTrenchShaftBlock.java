package com.aislen.createindustrialdetails.content.block.rivetedsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import static com.aislen.createindustrialdetails.content.block.rivetedsteel.trench.ServiceTrenchBlock.*;

public final class ServiceTrenchShaftBlock extends EncasedShaftBlock implements SimpleWaterloggedBlock {
    public ServiceTrenchShaftBlock(Properties properties) {
        super(properties, () -> ModBlocks.SERVICE_TRENCH.get());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SERVICE_TRENCH_SHAFT.get();
    }

    @Override
    public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack stack, Player player,
                               InteractionHand hand, BlockHitResult ray) {
        if (level.isClientSide) return;
        Direction facing = player.getDirection();
        Direction.Axis axis = state.getValue(AXIS);
        if (axis.isHorizontal() && facing.getAxis() != axis)
            facing = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        KineticBlockEntity.switchToBlockState(level, pos, defaultBlockState().setValue(AXIS, state.getValue(AXIS))
                .setValue(FACING, facing).setValue(WATERLOGGED, state.getValue(WATERLOGGED)));
        if (!player.isCreative()) stack.shrink(1);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return ServiceTrenchWrench.rotate(state, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return ServiceTrenchWrench.remove(state, context, AllBlocks.SHAFT.getDefaultState()
                .setValue(AXIS, state.getValue(AXIS)).setValue(WATERLOGGED, state.getValue(WATERLOGGED)), true);
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity be) {
        return super.getRequiredItems(state, be).union(ItemRequirement.of(ModBlocks.SERVICE_TRENCH.get().defaultBlockState(), null));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModBlocks.SERVICE_TRENCH.get());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(state, rotation).setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
}
