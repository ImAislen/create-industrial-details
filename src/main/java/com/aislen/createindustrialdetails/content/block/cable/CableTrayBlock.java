package com.aislen.createindustrialdetails.content.block.cable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.math.VoxelShaper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CableTrayBlock extends Block implements IWrenchable {
    public static final MapCodec<CableTrayBlock> CODEC = simpleCodec(CableTrayBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final int PLACEMENT_HELPER = PlacementHelpers.register(new CableTrayPlacementHelper());
    private static final ThreadLocal<Boolean> CREATIVE_WRENCH = ThreadLocal.withInitial(() -> false);

    private static final double[][] MODEL_BOXES = {
            {3, 0, 0, 13, 1, 16}, {2, 0, 0, 3, 3.5, 16}, {13, 0, 0, 14, 3.5, 16},
            {3.5, 1.5, 0, 5, 2.5, 16}, {6, 1.5, 0, 7.5, 2.5, 16},
            {8.5, 1.5, 0, 10, 2.5, 16}, {11, 1.5, 0, 12.5, 2.5, 16},
            {4, 1, 7.75, 4.5, 1.5, 8.25}, {6.5, 1, 7.75, 7, 1.5, 8.25},
            {9, 1, 7.75, 9.5, 1.5, 8.25}, {11.5, 1, 7.75, 12, 1.5, 8.25}
    };
    private static final VoxelShape Z_SHAPE = createShape(false, false);
    private static final VoxelShape X_SHAPE = createShape(true, false);
    private static final VoxelShaper VERTICAL_SHAPES = VoxelShaper.forHorizontal(createShape(false, true), Direction.NORTH);

    private static VoxelShape createShape(boolean alongX, boolean vertical) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : MODEL_BOXES) {
            VoxelShape part = vertical ? Block.box(b[0], b[2], 16 - b[4], b[3], b[5], 16 - b[1])
                    : alongX ? Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3])
                    : Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
            shape = Shapes.or(shape, part);
        }
        return shape.optimize();
    }

    public CableTrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z).setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<CableTrayBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FACING);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (state != previous && level instanceof ServerLevel server)
            com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).validate(pos);
        if (state != previous && level instanceof ServerLevel server)
            CableNetworks.get(server).topologyChanged(pos, previous);
        if (level instanceof ServerLevel server) com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).validate(pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this)) detachSpans(level, pos, !CREATIVE_WRENCH.get());
        if (!replacement.is(this) && level instanceof ServerLevel server)
            for (var span : com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).at(pos))
                if (com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).remove(span))
                    com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableBlock.drop(server, pos, span.color(), span.cost());
        super.onRemove(state, level, pos, replacement, moving);
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server)
            CableNetworks.get(server).topologyChanged(pos, state);
    }

    private static void detachSpans(Level level, BlockPos pos, boolean refund) {
        if (!(level instanceof ServerLevel server)) return;
        var ledger = com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server);
        for (var span : ledger.at(pos)) if (ledger.remove(span) && refund)
            com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableBlock.drop(server, pos, span.color(), span.cost());
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative()) detachSpans(level, pos, false);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public net.minecraft.world.InteractionResult onSneakWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        boolean previous = CREATIVE_WRENCH.get(); CREATIVE_WRENCH.set(context.getPlayer() != null && context.getPlayer().isCreative());
        try { return IWrenchable.super.onSneakWrenched(state, context); }
        finally { if (previous) CREATIVE_WRENCH.set(true); else CREATIVE_WRENCH.remove(); }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState base = defaultBlockState();
        BlockState aligned = alignToControlBox(base, context.getLevel(), context.getClickedPos());
        if (aligned != base) return aligned;
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) return base.setValue(AXIS, Direction.Axis.Y).setValue(FACING, clicked);
        BlockState supporting = context.getLevel().getBlockState(context.getClickedPos().relative(clicked.getOpposite()));
        if (supporting.is(this) && supporting.getValue(AXIS) == Direction.Axis.Y)
            return base.setValue(AXIS, Direction.Axis.Y).setValue(FACING, supporting.getValue(FACING));
        return base.setValue(AXIS, context.getHorizontalDirection().getAxis()).setValue(FACING, context.getHorizontalDirection());
    }

    static BlockState alignToControlBox(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState box = level.getBlockState(pos.below());
        if (!(box.getBlock() instanceof ControlBoxBlock)) box = level.getBlockState(pos.above());
        if (box.getBlock() instanceof ControlBoxBlock)
            return state.setValue(AXIS, Direction.Axis.Y).setValue(FACING, box.getValue(ControlBoxBlock.FACING));
        return state;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.mayBuild() || player.isSpectator()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var helper = PlacementHelpers.get(PLACEMENT_HELPER);
        if (!(stack.getItem() instanceof BlockItem item) || !helper.matchesItem(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var offset = helper.getOffset(player, level, state, pos, hit);
        return offset.isSuccessful() ? offset.placeInWorld(level, item, player, hand, hit) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    static Direction horizontalHeading(BlockState state) {
        Direction face = state.getValue(FACING);
        Direction.Axis axis = state.getValue(AXIS);
        return face.getAxis() == axis ? face : axis == Direction.Axis.X ? Direction.EAST : Direction.NORTH;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level instanceof ServerLevel server) com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).validate(pos);
        if (level instanceof ServerLevel server)
            com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).validate(pos);
        return direction.getAxis().isVertical() ? alignToControlBox(state, level, pos) : state;
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        if (targetedFace.getAxis().isVertical()) return rotate(state, Rotation.CLOCKWISE_90);
        Direction.Axis axis = state.getValue(AXIS);
        if (axis == targetedFace.getAxis()) return state;
        if (axis == Direction.Axis.Y)
            return state.setValue(AXIS, targetedFace.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return state.setValue(AXIS, Direction.Axis.Y).setValue(FACING, targetedFace);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (state.getValue(AXIS) == Direction.Axis.Y) return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
        return state.setValue(AXIS, rotation.rotate(direction).getAxis()).setValue(FACING, rotation.rotate(horizontalHeading(state)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.getValue(AXIS) == Direction.Axis.Y ? state.setValue(FACING, mirror.mirror(state.getValue(FACING))) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(AXIS) == Direction.Axis.Y) return VERTICAL_SHAPES.get(state.getValue(FACING));
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
