package com.aislen.createindustrialdetails.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.bracket.BracketBlock;
import com.simibubi.create.content.decoration.bracket.BracketBlock.BracketType;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RivetedSteelPanelBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public static final BooleanProperty CONNECTED_TOP =
        BooleanProperty.create("connected_top");
    public static final BooleanProperty CONNECTED_RIGHT =
        BooleanProperty.create("connected_right");
    public static final BooleanProperty CONNECTED_BOTTOM =
        BooleanProperty.create("connected_bottom");
    public static final BooleanProperty CONNECTED_LEFT =
        BooleanProperty.create("connected_left");

    /*
     * These are TRUE only for concave/interior corners:
     *
     * - both adjoining side-neighbours exist
     * - but the diagonal panel that would fill the 2x2 junction does not
     *
     * Example for INNER_TOP_RIGHT:
     *
     *     [ current ][ top ]
     *     [ right   ][ EMPTY ]  <- missing diagonal creates inner corner
     */
    public static final BooleanProperty INNER_TOP_LEFT =
        BooleanProperty.create("inner_top_left");
    public static final BooleanProperty INNER_TOP_RIGHT =
        BooleanProperty.create("inner_top_right");
    public static final BooleanProperty INNER_BOTTOM_LEFT =
        BooleanProperty.create("inner_bottom_left");
    public static final BooleanProperty INNER_BOTTOM_RIGHT =
        BooleanProperty.create("inner_bottom_right");

    private static final int TOP_BIT = 1;
    private static final int RIGHT_BIT = 2;
    private static final int BOTTOM_BIT = 4;
    private static final int LEFT_BIT = 8;

    private static final int INNER_TOP_LEFT_BIT = 1;
    private static final int INNER_TOP_RIGHT_BIT = 2;
    private static final int INNER_BOTTOM_LEFT_BIT = 4;
    private static final int INNER_BOTTOM_RIGHT_BIT = 8;

    /*
     * 6 facings x 16 cardinal-connection masks x 16 inner-corner masks.
     *
     * Most theoretical combinations can never occur naturally, but caching
     * the small table keeps getShape/getCollisionShape to simple array access.
     * Shape construction happens once when this class loads; never per tick,
     * render frame, selection check, or entity collision.
     */
    private static final VoxelShape[][][] SHAPES = buildShapeCache();

    public RivetedSteelPanelBlock(Properties properties) {
        super(properties);

        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED_TOP, false)
                .setValue(CONNECTED_RIGHT, false)
                .setValue(CONNECTED_BOTTOM, false)
                .setValue(CONNECTED_LEFT, false)
                .setValue(INNER_TOP_LEFT, false)
                .setValue(INNER_TOP_RIGHT, false)
                .setValue(INNER_BOTTOM_LEFT, false)
                .setValue(INNER_BOTTOM_RIGHT, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(
        StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(
            FACING,
            CONNECTED_TOP,
            CONNECTED_RIGHT,
            CONNECTED_BOTTOM,
            CONNECTED_LEFT,
            INNER_TOP_LEFT,
            INNER_TOP_RIGHT,
            INNER_BOTTOM_LEFT,
            INNER_BOTTOM_RIGHT
        );
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
            .setValue(FACING, context.getClickedFace());

        return updateAllConnections(
            state,
            context.getLevel(),
            context.getClickedPos()
        );
    }


    /**
     * Inverse shaft-penetration interaction:
     *
     * Right-click an already placed Riveted Steel Panel with a standard
     * Create Shaft. The panel's blockspace becomes the real Create shaft,
     * while the visual panel-with-hole is transferred into the shaft's
     * existing bracket slot.
     */
    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (!(stack.getItem() instanceof BlockItem shaftItem)
            || shaftItem.getBlock() != AllBlocks.SHAFT.get()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        Direction panelFacing = state.getValue(FACING);
        BlockState originalPanelState = state;
        int originalStackCount = stack.getCount();

        /*
         * Remove the panel temporarily, then let Create's actual Shaft BlockItem
         * perform a genuine placement into this exact block position.
         *
         * This preserves Create/Minecraft's normal placement lifecycle instead
         * of manually creating the shaft with Level#setBlock.
         */
        level.removeBlock(pos, false);

        DirectionalPlaceContext placementContext =
            new DirectionalPlaceContext(
                level,
                pos,
                panelFacing,
                stack,
                panelFacing
            );

        InteractionResult placementResult =
            shaftItem.place(placementContext);

        BlockState placedState = level.getBlockState(pos);

        if (!placementResult.consumesAction()
            || !ShaftBlock.isShaft(placedState)) {

            level.setBlock(pos, originalPanelState, Block.UPDATE_ALL);

            // BlockItem#place may have modified the stack before a later
            // validation failed; restore exactly what the player started with.
            if (!player.isCreative()) {
                stack.setCount(originalStackCount);
            }

            return ItemInteractionResult.FAIL;
        }

        /*
         * Force the shaft to pass perpendicular to the panel. Create's normal
         * placement heuristics can prefer a nearby kinetic axis; for a panel
         * penetration the panel normal must win.
         */
        Direction.Axis desiredAxis = panelFacing.getAxis();

        if (placedState.getValue(RotatedPillarKineticBlock.AXIS) != desiredAxis) {
            BlockState correctedState =
                placedState.setValue(
                    RotatedPillarKineticBlock.AXIS,
                    desiredAxis
                );

            KineticBlockEntity.switchToBlockState(
                level,
                pos,
                correctedState
            );

            placedState = level.getBlockState(pos);
        }

        BracketedBlockEntityBehaviour behaviour = BlockEntityBehaviour.get(
            level,
            pos,
            BracketedBlockEntityBehaviour.TYPE
        );

        if (behaviour == null || !behaviour.canHaveBracket()) {
            level.setBlock(pos, originalPanelState, Block.UPDATE_ALL);

            if (!player.isCreative()) {
                stack.setCount(originalStackCount);
            }

            return ItemInteractionResult.FAIL;
        }

        /*
         * This path began with an already-placed panel, so retain its exact
         * facing. That leaves the penetration on the same edge of the
         * blockspace where the original panel was.
         */
        BlockState penetration =
            ModBlocks.RIVETED_STEEL_PANEL_SHAFT_PENETRATION
                .get()
                .defaultBlockState()
                .setValue(BracketBlock.FACING, panelFacing)
                .setValue(BracketBlock.TYPE, BracketType.SHAFT)
                .setValue(
                    BracketBlock.AXIS_ALONG_FIRST_COORDINATE,
                    false
                );

        behaviour.applyBracket(penetration);

        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        Direction direction,
        BlockState neighborState,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighborPos
    ) {
        /*
         * Re-evaluate all four edges and diagonals whenever a direct neighbour
         * changes. A diagonal panel is itself adjacent to one of the cardinal
         * panels around a concave corner, so its placement/removal causes that
         * cardinal panel to update, which in turn notifies this panel.
         *
         * This remains entirely neighbour-event-driven; there is no ticking.
         */
        return updateAllConnections(state, level, pos);
    }

    private BlockState updateAllConnections(
        BlockState state,
        BlockGetter level,
        BlockPos pos
    ) {
        Direction facing = state.getValue(FACING);

        Direction topDir = localTop(facing);
        Direction rightDir = localRight(facing);
        Direction bottomDir = localBottom(facing);
        Direction leftDir = localLeft(facing);

        boolean top = sameFacingPanel(level, pos.relative(topDir), facing);
        boolean right = sameFacingPanel(level, pos.relative(rightDir), facing);
        boolean bottom = sameFacingPanel(level, pos.relative(bottomDir), facing);
        boolean left = sameFacingPanel(level, pos.relative(leftDir), facing);

        boolean diagonalTopLeft =
            sameFacingPanel(level, pos.relative(topDir).relative(leftDir), facing);
        boolean diagonalTopRight =
            sameFacingPanel(level, pos.relative(topDir).relative(rightDir), facing);
        boolean diagonalBottomLeft =
            sameFacingPanel(level, pos.relative(bottomDir).relative(leftDir), facing);
        boolean diagonalBottomRight =
            sameFacingPanel(level, pos.relative(bottomDir).relative(rightDir), facing);

        /*
         * A concave corner exists only when BOTH side panels are present but
         * the panel diagonally across their junction is absent.
         */
        boolean innerTopLeft = top && left && !diagonalTopLeft;
        boolean innerTopRight = top && right && !diagonalTopRight;
        boolean innerBottomLeft = bottom && left && !diagonalBottomLeft;
        boolean innerBottomRight = bottom && right && !diagonalBottomRight;

        return state
            .setValue(CONNECTED_TOP, top)
            .setValue(CONNECTED_RIGHT, right)
            .setValue(CONNECTED_BOTTOM, bottom)
            .setValue(CONNECTED_LEFT, left)
            .setValue(INNER_TOP_LEFT, innerTopLeft)
            .setValue(INNER_TOP_RIGHT, innerTopRight)
            .setValue(INNER_BOTTOM_LEFT, innerBottomLeft)
            .setValue(INNER_BOTTOM_RIGHT, innerBottomRight);
    }

    private boolean sameFacingPanel(
        BlockGetter level,
        BlockPos pos,
        Direction facing
    ) {
        BlockState neighborState = level.getBlockState(pos);

        return neighborState.is(this)
            && neighborState.getValue(FACING) == facing;
    }

    private static Direction localTop(Direction facing) {
        return switch (facing) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }

    private static Direction localBottom(Direction facing) {
        return switch (facing) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.DOWN;
        };
    }

    private static Direction localRight(Direction facing) {
        return switch (facing) {
            case UP, DOWN -> Direction.EAST;
            default -> facing.getClockWise();
        };
    }

    private static Direction localLeft(Direction facing) {
        return switch (facing) {
            case UP, DOWN -> Direction.WEST;
            default -> facing.getCounterClockWise();
        };
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return cachedShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return cachedShape(state);
    }

    private static VoxelShape cachedShape(BlockState state) {
        return SHAPES[
            state.getValue(FACING).ordinal()
        ][
            connectionMask(state)
        ][
            innerCornerMask(state)
        ];
    }

    private static int connectionMask(BlockState state) {
        int mask = 0;

        if (state.getValue(CONNECTED_TOP)) {
            mask |= TOP_BIT;
        }
        if (state.getValue(CONNECTED_RIGHT)) {
            mask |= RIGHT_BIT;
        }
        if (state.getValue(CONNECTED_BOTTOM)) {
            mask |= BOTTOM_BIT;
        }
        if (state.getValue(CONNECTED_LEFT)) {
            mask |= LEFT_BIT;
        }

        return mask;
    }

    private static int innerCornerMask(BlockState state) {
        int mask = 0;

        if (state.getValue(INNER_TOP_LEFT)) {
            mask |= INNER_TOP_LEFT_BIT;
        }
        if (state.getValue(INNER_TOP_RIGHT)) {
            mask |= INNER_TOP_RIGHT_BIT;
        }
        if (state.getValue(INNER_BOTTOM_LEFT)) {
            mask |= INNER_BOTTOM_LEFT_BIT;
        }
        if (state.getValue(INNER_BOTTOM_RIGHT)) {
            mask |= INNER_BOTTOM_RIGHT_BIT;
        }

        return mask;
    }

    private static VoxelShape[][][] buildShapeCache() {
        VoxelShape[][][] result =
            new VoxelShape[Direction.values().length][16][16];

        for (Direction facing : Direction.values()) {
            for (int connections = 0; connections < 16; connections++) {
                for (int innerCorners = 0; innerCorners < 16; innerCorners++) {
                    result[facing.ordinal()][connections][innerCorners] =
                        buildShape(facing, connections, innerCorners);
                }
            }
        }

        return result;
    }

    private static VoxelShape buildShape(
        Direction facing,
        int connectionMask,
        int innerMask
    ) {
        boolean top = (connectionMask & TOP_BIT) != 0;
        boolean right = (connectionMask & RIGHT_BIT) != 0;
        boolean bottom = (connectionMask & BOTTOM_BIT) != 0;
        boolean left = (connectionMask & LEFT_BIT) != 0;

        boolean innerTopLeft =
            (innerMask & INNER_TOP_LEFT_BIT) != 0;
        boolean innerTopRight =
            (innerMask & INNER_TOP_RIGHT_BIT) != 0;
        boolean innerBottomLeft =
            (innerMask & INNER_BOTTOM_LEFT_BIT) != 0;
        boolean innerBottomRight =
            (innerMask & INNER_BOTTOM_RIGHT_BIT) != 0;

        List<BoxDef> boxes = new ArrayList<>(9);

        // Recessed centre is always one pixel deep.
        boxes.add(new BoxDef(2, 2, 15, 14, 14, 16));

        // Edge middle sections.
        boxes.add(depthBox(2, 14, 14, 16, top));
        boxes.add(depthBox(14, 2, 16, 14, right));
        boxes.add(depthBox(2, 0, 14, 2, bottom));
        boxes.add(depthBox(0, 2, 2, 14, left));

        /*
         * Corners are flat only when:
         * - both adjoining edges connect, AND
         * - there is no concave/interior corner there.
         *
         * Otherwise the 2x2 corner remains at full two-pixel depth.
         */
        boolean flatTopLeft = top && left && !innerTopLeft;
        boolean flatTopRight = top && right && !innerTopRight;
        boolean flatBottomLeft = bottom && left && !innerBottomLeft;
        boolean flatBottomRight = bottom && right && !innerBottomRight;

        boxes.add(depthBox(0, 14, 2, 16, flatTopLeft));
        boxes.add(depthBox(14, 14, 16, 16, flatTopRight));
        boxes.add(depthBox(0, 0, 2, 2, flatBottomLeft));
        boxes.add(depthBox(14, 0, 16, 2, flatBottomRight));

        VoxelShape shape = Shapes.empty();

        for (BoxDef box : boxes) {
            BoxDef transformed = transform(box, facing);

            shape = Shapes.or(
                shape,
                Block.box(
                    transformed.x1,
                    transformed.y1,
                    transformed.z1,
                    transformed.x2,
                    transformed.y2,
                    transformed.z2
                )
            );
        }

        return shape;
    }

    /*
     * Defines a rectangle on the north-authored panel plane.
     *
     * flat=true  -> Z 15..16, same depth as recessed sheet
     * flat=false -> Z 14..16, same depth as raised frame
     */
    private static BoxDef depthBox(
        double x1,
        double y1,
        double x2,
        double y2,
        boolean flat
    ) {
        return new BoxDef(
            x1,
            y1,
            flat ? 15 : 14,
            x2,
            y2,
            16
        );
    }

    /*
     * Same mapping already confirmed working in-game:
     *
     * NORTH = none
     * EAST  = Y 270
     * SOUTH = Y 180
     * WEST  = Y 90
     * UP    = X 270
     * DOWN  = X 90
     */
    private static BoxDef transform(BoxDef box, Direction facing) {
        Point a = transformPoint(box.x1, box.y1, box.z1, facing);
        Point b = transformPoint(box.x2, box.y2, box.z2, facing);

        return new BoxDef(
            Math.min(a.x, b.x),
            Math.min(a.y, b.y),
            Math.min(a.z, b.z),
            Math.max(a.x, b.x),
            Math.max(a.y, b.y),
            Math.max(a.z, b.z)
        );
    }

    private static Point transformPoint(
        double x,
        double y,
        double z,
        Direction facing
    ) {
        return switch (facing) {
            case NORTH -> new Point(x, y, z);
            case EAST -> new Point(16 - z, y, x);
            case SOUTH -> new Point(16 - x, y, 16 - z);
            case WEST -> new Point(z, y, 16 - x);
            case UP -> new Point(x, 16 - z, y);
            case DOWN -> new Point(x, z, 16 - y);
        };
    }

    private record BoxDef(
        double x1,
        double y1,
        double z1,
        double x2,
        double y2,
        double z2
    ) {}

    private record Point(double x, double y, double z) {}
}
