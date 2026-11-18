package com.aislen.createindustrialdetails.content.block.cable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

public class ControlBoxBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable {
    public static final MapCodec<ControlBoxBlock> CODEC = simpleCodec(ControlBoxBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final ThreadLocal<Boolean> CREATIVE_WRENCH = ThreadLocal.withInitial(() -> false);
    public ControlBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends ControlBoxBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) face = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, face);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControlBoxBlockEntity(pos, state);
    }

    public static boolean isCablePort(BlockState state, Direction side) {
        return side.getAxis() == Direction.Axis.Y || side == state.getValue(FACING).getOpposite();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ControlBoxBlockEntity box ? box.getComparatorOutput() : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (previous.is(this) && previous.getValue(FACING) != state.getValue(FACING)) detachSpans(level, pos, true);
        if (level instanceof ServerLevel server && (!previous.is(this) || previous.getValue(FACING) != state.getValue(FACING)))
            CableNetworks.get(server).topologyChanged(pos, previous);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this)) detachSpans(level, pos, !CREATIVE_WRENCH.get());
        super.onRemove(state, level, pos, replacement, moving);
        if (!level.isClientSide && !state.is(replacement.getBlock())) {
            level.updateNeighbourForOutputSignal(pos, this);
            if (level instanceof ServerLevel server) CableNetworks.get(server).topologyChanged(pos, state);
        }
    }

    private static void detachSpans(Level level, BlockPos pos, boolean refund) {
        if (!(level instanceof ServerLevel server)) return;
        var spans = com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server);
        for (var span : spans.at(pos)) if (spans.remove(span) && refund)
            com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableBlock.drop(server, pos, span.color(), span.cost());
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative()) detachSpans(level, pos, false);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public InteractionResult onSneakWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        // Create checks break protection before removal; suppress refunds only inside that successful removal path.
        boolean previous = CREATIVE_WRENCH.get();
        CREATIVE_WRENCH.set(context.getPlayer() != null && context.getPlayer().isCreative());
        try {
            return IWrenchable.super.onSneakWrenched(state, context);
        } finally {
            if (previous) CREATIVE_WRENCH.set(true); else CREATIVE_WRENCH.remove();
        }
    }
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ControlBoxGeometry.shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    static boolean hasWrench(Player player) {
        return isWrench(player.getMainHandItem()) || isWrench(player.getOffhandItem());
    }

    private static boolean isWrench(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem || stack.is(Tags.Items.TOOLS_WRENCH);
    }

    public static BlockHitResult traceDoor(BlockState state, BlockPos pos, Vec3 start, Vec3 end) {
        return ControlBoxGeometry.traceDoor(state, pos, start, end);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (hasWrench(player)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return interact(state, level, pos, player, hit) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hasWrench(player)) return InteractionResult.PASS;
        return interact(state, level, pos, player, hit) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    private boolean interact(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return false;
        if (state.getValue(OPEN)) {
            if (controlAt(state, pos, hit) != null) return true;
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(hit.getLocation().subtract(eye).normalize().scale(player.blockInteractionRange()));
            if (!ControlBoxGeometry.hitsDoor(state, pos, eye, end)) return false;
        }
        if (!level.isClientSide) {
            boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, .6f, 1);
        }
        return true;
    }

    static ControlChannel controlAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) return null;
        return ControlBoxGeometry.controlAt(state, hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
    }
}
