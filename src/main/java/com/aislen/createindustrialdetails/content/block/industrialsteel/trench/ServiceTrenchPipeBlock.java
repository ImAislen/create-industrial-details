package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
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
import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.*;

public final class ServiceTrenchPipeBlock extends EncasedPipeBlock implements SimpleWaterloggedBlock {
    public ServiceTrenchPipeBlock(Properties properties) {
        super(properties, () -> ModBlocks.SERVICE_TRENCH.get());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SERVICE_TRENCH_PIPE.get();
    }

    @Override
    public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack stack, Player player,
                               InteractionHand hand, BlockHitResult ray) {
        if (level.isClientSide) return;
        Direction facing = player.getDirection();
        Direction.Axis axis = com.simibubi.create.content.fluids.FluidPropagator.getStraightPipeAxis(state);
        if (axis != null && axis.isHorizontal() && facing.getAxis() != axis)
            facing = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        BlockState encased = transferSixWayProperties(state, defaultBlockState())
                .setValue(FACING, facing).setValue(WATERLOGGED, state.getValue(WATERLOGGED));
        FluidTransportBehaviour.cacheFlows(level, pos);
        level.setBlockAndUpdate(pos, encased);
        FluidTransportBehaviour.loadFlows(level, pos);
        if (!player.isCreative()) stack.shrink(1);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return ServiceTrenchWrench.rotate(state, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return ServiceTrenchWrench.remove(state, context,
                transferSixWayProperties(state, AllBlocks.FLUID_PIPE.getDefaultState())
                        .setValue(WATERLOGGED, state.getValue(WATERLOGGED)), false);
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
        return super.mirror(state, mirror).setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public BlockState transform(BlockState state, com.simibubi.create.content.contraptions.StructureTransform transform) {
        Direction facing = state.getValue(FACING);
        if (transform.mirror != null) facing = transform.mirror.mirror(facing);
        facing = transform.rotateFacing(facing);
        return super.transform(state, transform).setValue(FACING,
                facing.getAxis().isHorizontal() ? facing : state.getValue(FACING));
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
