package com.aislen.createindustrialdetails.content.block.pneumatic;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PneumaticStationBlock extends KineticBlock {
    public enum Kind {
        LAUNCHER(true), SUPPORT(false), BOOSTER(false), RECEIVER(true);
        final boolean tall;
        Kind(boolean tall) { this.tall = tall; }
    }

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");
    private static final VoxelShape SUPPORT_X = box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape SUPPORT_Y = box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape SUPPORT_Z = box(4, 4, 0, 12, 12, 16);
    public final Kind kind;

    public PneumaticStationBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y).setValue(UPPER, false).setValue(LOADED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, UPPER, LOADED);
    }

    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(UPPER) ? pos.below() : pos;
    }

    @Nullable
    public static PneumaticSpline.Port port(BlockState state, BlockPos pos, Direction face) {
        if (!(state.getBlock() instanceof PneumaticStationBlock station)) return null;
        if (station.kind.tall)
            return new PneumaticSpline.Port(base(state, pos), Direction.UP, station.kind);
        return state.getValue(AXIS) == face.getAxis() ? new PneumaticSpline.Port(pos, face, station.kind) : null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        Level level = context.getLevel();
        if (kind.tall && (level.isOutsideBuildHeight(above) || !level.getWorldBorder().isWithinBounds(above)
                || !level.getBlockState(above).canBeReplaced(context))) return null;
        return defaultBlockState().setValue(AXIS, kind.tall ? Direction.Axis.Y : context.getClickedFace().getAxis());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack item) {
        super.setPlacedBy(level, pos, state, placer, item);
        if (kind.tall && !level.isClientSide) level.setBlock(pos.above(), state.setValue(UPPER, true), UPDATE_ALL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (kind != Kind.SUPPORT) return super.getShape(state, level, pos, context);
        return switch (state.getValue(AXIS)) {
            case X -> SUPPORT_X;
            case Y -> SUPPORT_Y;
            case Z -> SUPPORT_Z;
        };
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return kind == Kind.LAUNCHER && !state.getValue(UPPER) && face == Direction.DOWN;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (kind.tall) return state;
        return state.setValue(AXIS, rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS))).getAxis());
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) { return state; }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!kind.tall || !state.getValue(UPPER)) return true;
        BlockState lower = level.getBlockState(pos.below());
        return lower.is(this) && !lower.getValue(UPPER);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && level instanceof ServerLevel server) {
            BlockPos owner = base(state, pos);
            PneumaticNetwork.get(server).removeStation(server, owner, kind);
            if (kind.tall) {
                BlockPos other = state.getValue(UPPER) ? owner : pos.above();
                BlockState counterpart = level.getBlockState(other);
                if (counterpart.is(this) && counterpart.getValue(UPPER) != state.getValue(UPPER))
                    level.removeBlock(other, false);
            }
        } else if (next.is(this) && state.getValue(AXIS) != next.getValue(AXIS) && level instanceof ServerLevel server) {
            PneumaticNetwork.get(server).disconnectStation(server, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.WIND_CHARGE) && (kind == Kind.LAUNCHER || kind == Kind.BOOSTER)) {
            if (level instanceof ServerLevel server) {
                int accepted = PneumaticNetwork.get(server).insertFuel(base(state, pos), stack.getCount(), false);
                if (!player.isCreative()) stack.shrink(accepted);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (kind.tall && player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            if (level instanceof ServerLevel server) {
                boolean recovered = PneumaticNetwork.get(server).recover(base(state, pos), player);
                player.displayClientMessage(Component.translatable(recovered
                        ? "message.create_industrial_details.pneumatic.recovered"
                        : "message.create_industrial_details.pneumatic.no_recovery"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        // Wrench an occupied port first. A second use removes the station.
        var port = port(state, context.getClickedPos(), context.getClickedFace());
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (context.getLevel() instanceof ServerLevel server) {
            if (port != null && PneumaticNetwork.get(server).removeSpan(server, port, context.getPlayer()))
                return InteractionResult.SUCCESS;
            BlockPos owner = base(state, context.getClickedPos());
            if (!levelHasOwner(server, owner)) return InteractionResult.PASS;
            var hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), owner, false);
            return super.onSneakWrenched(server.getBlockState(owner), new UseOnContext(
                    server, context.getPlayer(), context.getHand(), context.getItemInHand(), hit));
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    private boolean levelHasOwner(Level level, BlockPos owner) {
        BlockState state = level.getBlockState(owner);
        return state.is(this) && !state.getValue(UPPER);
    }
}
