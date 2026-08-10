package com.aislen.createindustrialdetails.content.block.lighting.cagedlamp;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RivetedSteelCagedLampBlock extends Block implements IWrenchable {

    // Properties

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty PERPENDICULAR = BooleanProperty.create("perpendicular");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");
    public static final EnumProperty<RivetedSteelCagedLampColor> COLOR =
            EnumProperty.create("color", RivetedSteelCagedLampColor.class);

    // Shapes

    private static final VoxelShape WALL_HANGING_SOUTH = combine(
            box(2.5, 2.5, 0, 13.5, 13.5, 1.75),
            box(7, 9.5, 1.75, 9, 11.5, 6.75),
            box(5.5, 1, 5.25, 10.5, 12, 10.25)
    );

    private static final VoxelShape WALL_HANGING_WEST = combine(
            box(14.25, 2.5, 2.5, 16, 13.5, 13.5),
            box(9.25, 9.5, 7, 14.25, 11.5, 9),
            box(5.75, 1, 5.5, 10.75, 12, 10.5)
    );

    private static final VoxelShape WALL_HANGING_NORTH = combine(
            box(2.5, 2.5, 14.25, 13.5, 13.5, 16),
            box(7, 9.5, 9.25, 9, 11.5, 14.25),
            box(5.5, 1, 5.75, 10.5, 12, 10.75)
    );

    private static final VoxelShape WALL_HANGING_EAST = combine(
            box(0, 2.5, 2.5, 1.75, 13.5, 13.5),
            box(1.75, 9.5, 7, 6.75, 11.5, 9),
            box(5.25, 1, 5.5, 10.25, 12, 10.5)
    );

    private static final VoxelShape WALL_PERPENDICULAR_SOUTH =
            box(5.5, 5.4375, 0.1875, 10.5, 10.4375, 10.6875);

    private static final VoxelShape WALL_PERPENDICULAR_WEST =
            box(5.3125, 5.4375, 5.5, 15.8125, 10.4375, 10.5);

    private static final VoxelShape WALL_PERPENDICULAR_NORTH =
            box(5.5, 5.4375, 5.3125, 10.5, 10.4375, 15.8125);

    private static final VoxelShape WALL_PERPENDICULAR_EAST =
            box(0.1875, 5.4375, 5.5, 10.6875, 10.4375, 10.5);

    private static final VoxelShape CEILING = combine(
            box(2.5, 14.225, 2.275, 13.5, 15.975, 13.275),
            box(7, 9.5, 6.75, 9, 14.975, 8.75),
            box(5.5, 1, 5.25, 10.5, 9.5, 10.25)
    );

    private static final VoxelShape FLOOR =
            box(5.5, 0, 5.25, 10.5, 11, 10.25);

    public RivetedSteelCagedLampBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                defaultBlockState()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(LIT, false)
                        .setValue(INVERTED, false)
                        .setValue(COLOR, RivetedSteelCagedLampColor.NATURAL)
        );

    }

    // State

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, PERPENDICULAR, LIT, INVERTED, COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();

        boolean perpendicular =
                facing.getAxis() != Direction.Axis.Y
                        && context.getPlayer() != null
                        && context.getPlayer().isShiftKeyDown();

        boolean lit = context.getLevel().hasNeighborSignal(context.getClickedPos());

        BlockState state = defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PERPENDICULAR, perpendicular)
                .setValue(INVERTED, false)
                .setValue(LIT, lit);

        return state.canSurvive(context.getLevel(), context.getClickedPos())
                ? state
                : null;
    }

    // Redstone

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

        if (!level.isClientSide)
            updateLitState(state, level, pos);
    }

    private static void updateLitState(BlockState state, Level level, BlockPos pos) {
        boolean powered = level.hasNeighborSignal(pos);
        boolean lit = powered ^ state.getValue(INVERTED);

        if (state.getValue(LIT) != lit)
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
    }

    // Wrench

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            boolean inverted = !state.getValue(INVERTED);
            boolean lit = level.hasNeighborSignal(pos) ^ inverted;

            level.setBlock(
                    pos,
                    state
                            .setValue(INVERTED, inverted)
                            .setValue(LIT, lit),
                    Block.UPDATE_CLIENTS
            );

            IWrenchable.playRotateSound(level, pos);
        }

        return InteractionResult.SUCCESS;
    }

    // Dye

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
        if (!(stack.getItem() instanceof DyeItem dyeItem))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        RivetedSteelCagedLampColor color =
                RivetedSteelCagedLampColor.fromDye(dyeItem.getDyeColor());

        if (state.getValue(COLOR) == color)
            return ItemInteractionResult.sidedSuccess(level.isClientSide);

        if (!level.isClientSide) {
            level.setBlockAndUpdate(
                    pos,
                    state.setValue(COLOR, color)
            );

            if (!player.hasInfiniteMaterials())
                stack.shrink(1);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    // Support

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());

        return Block.canSupportCenter(level, supportPos, facing);
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == state.getValue(FACING).getOpposite()
                && !state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // Shape

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getLampShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getLampShape(state);
    }

    private static VoxelShape getLampShape(BlockState state) {
        Direction facing = state.getValue(FACING);

        if (facing == Direction.UP)
            return FLOOR;

        if (facing == Direction.DOWN)
            return CEILING;

        if (state.getValue(PERPENDICULAR)) {
            return switch (facing) {
                case SOUTH -> WALL_PERPENDICULAR_SOUTH;
                case WEST -> WALL_PERPENDICULAR_WEST;
                case NORTH -> WALL_PERPENDICULAR_NORTH;
                case EAST -> WALL_PERPENDICULAR_EAST;
                default -> WALL_PERPENDICULAR_SOUTH;
            };
        }

        return switch (facing) {
            case SOUTH -> WALL_HANGING_SOUTH;
            case WEST -> WALL_HANGING_WEST;
            case NORTH -> WALL_HANGING_NORTH;
            case EAST -> WALL_HANGING_EAST;
            default -> WALL_HANGING_SOUTH;
        };
    }

    // Transform

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private static VoxelShape combine(VoxelShape first, VoxelShape second, VoxelShape third) {
        return Shapes.or(Shapes.or(first, second), third);
    }
}