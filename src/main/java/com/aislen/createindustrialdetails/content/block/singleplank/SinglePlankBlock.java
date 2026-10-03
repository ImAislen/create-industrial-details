package com.aislen.createindustrialdetails.content.block.singleplank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SinglePlankBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty CENTER = BooleanProperty.create("center");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    private static final VoxelShape LEFT_SHAPE = Block.box(
            0, 0, 0.5,
            16, 1, 4.8333
    );

    private static final VoxelShape CENTER_SHAPE = Block.box(
            0, 0, 5.833,
            16, 1, 10.1667
    );

    private static final VoxelShape RIGHT_SHAPE = Block.box(
            0, 0, 11.1667,
            16, 1, 15.5
    );

    private static final VoxelShape[][] SHAPES = createShapes();

    public SinglePlankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(LEFT, false)
                .setValue(CENTER, true)
                .setValue(RIGHT, false));
    }

    private static VoxelShape[][] createShapes() {
        VoxelShape[][] slots = {
                {LEFT_SHAPE, CENTER_SHAPE, RIGHT_SHAPE},
                {rotateToZ(LEFT_SHAPE), rotateToZ(CENTER_SHAPE), rotateToZ(RIGHT_SHAPE)}
        };
        VoxelShape[][] shapes = new VoxelShape[2][8];
        // X/Z rows share the same left/center/right mask. Build all 16 unions only once.
        for (int axis = 0; axis < shapes.length; axis++) {
            for (int mask = 0; mask < shapes[axis].length; mask++) {
                VoxelShape shape = Shapes.empty();
                for (int slot = 0; slot < slots[axis].length; slot++) {
                    if ((mask & (1 << slot)) != 0) {
                        shape = Shapes.or(shape, slots[axis][slot]);
                    }
                }
                shapes[axis][mask] = shape;
            }
        }
        return shapes;
    }

    private static VoxelShape rotateToZ(VoxelShape shape) {
        AABB bounds = shape.bounds();
        // Matches model y=270: low Z becomes low X, and each plank still spans the full length.
        return Shapes.box(bounds.minZ, bounds.minY, bounds.minX,
                bounds.maxZ, bounds.maxY, bounds.maxX);
    }

    public static VoxelShape shapeForMask(Direction.Axis axis, int mask) {
        return SHAPES[axis == Direction.Axis.X ? 0 : 1][mask];
    }

    public static int occupancyMask(BlockState state) {
        return (state.getValue(LEFT) ? 1 : 0)
                | (state.getValue(CENTER) ? 2 : 0)
                | (state.getValue(RIGHT) ? 4 : 0);
    }

    private static VoxelShape shapeForState(BlockState state) {
        return shapeForMask(state.getValue(AXIS), occupancyMask(state));
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeForState(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeForState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, LEFT, CENTER, RIGHT);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return rotatePlanks(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return mirrorPlanks(state, mirror);
    }

    public static BlockState rotatePlanks(BlockState state, Rotation rotation) {
        return transformLanes(state, rotation.rotate(lateralDirection(state)));
    }

    public static BlockState mirrorPlanks(BlockState state, Mirror mirror) {
        return transformLanes(state, mirror.mirror(lateralDirection(state)));
    }

    private static Direction lateralDirection(BlockState state) {
        // RIGHT is the positive lateral lane: +Z for X planks, +X for Z planks.
        return state.getValue(AXIS) == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
    }

    private static BlockState transformLanes(BlockState state, Direction lateral) {
        BlockState transformed = state.setValue(AXIS,
                lateral.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
        if (lateral.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            transformed = transformed.setValue(LEFT, state.getValue(RIGHT))
                    .setValue(RIGHT, state.getValue(LEFT));
        }
        return transformed;
    }

    private static double lateralPosition(BlockState state, Vec3 hitLocation, BlockPos pos) {
        return state.getValue(AXIS) == Direction.Axis.X
                ? hitLocation.z - pos.getZ()
                : hitLocation.x - pos.getX();
    }

    private static BooleanProperty getPreferredSlot(double localPosition) {
        if (localPosition < 1.0 / 3.0) {
            return LEFT;
        }
        if (localPosition > 2.0 / 3.0) {
            return RIGHT;
        }
        return CENTER;
    }

    public static BooleanProperty slotFromHit(BlockState state, Vec3 hitLocation, BlockPos pos) {
        return getPreferredSlot(lateralPosition(state, hitLocation, pos));
    }

    @Nullable
    public static BooleanProperty availableSlotFromHit(BlockState state, Vec3 hitLocation, BlockPos pos) {
        return getAvailableSlot(state, lateralPosition(state, hitLocation, pos));
    }

    @Nullable
    private static BooleanProperty getAvailableSlot(BlockState state, double localPosition) {
        BooleanProperty preferred = getPreferredSlot(localPosition);
        if (!state.getValue(preferred)) {
            return preferred;
        }
        // Center is the nearest alternative to either outer lane.
        if (!state.getValue(CENTER)) {
            return CENTER;
        }
        BooleanProperty side = localPosition < 0.5 ? LEFT : RIGHT;
        if (!state.getValue(side)) {
            return side;
        }
        BooleanProperty other = side == LEFT ? RIGHT : LEFT;
        return state.getValue(other) ? null : other;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(AXIS, context.getHorizontalDirection().getAxis())
                .setValue(LEFT, false)
                .setValue(CENTER, false)
                .setValue(RIGHT, false);
        Vec3 hitLocation = context.getClickLocation();
        double localPosition = lateralPosition(state, hitLocation, BlockPos.containing(hitLocation));
        return state.setValue(getPreferredSlot(localPosition), true);
    }

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
        return tryAddPlank(stack, state, level, pos, player, hitResult.getDirection(),
                hitResult.getLocation());
    }

    ItemInteractionResult tryAddPlank(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            Direction clickedFace,
            Vec3 hitLocation
    ) {
        if (!state.is(this) || !(stack.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() != this) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, clickedFace, stack)) {
            return ItemInteractionResult.FAIL;
        }

        BooleanProperty slot = availableSlotFromHit(state, hitLocation, pos);
        if (slot == null) {
            // Consume the interaction, not the item, so BlockItem cannot place a fourth plank beside it.
            return ItemInteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            BlockState updatedState = state.setValue(slot, true);
            if (!level.setBlock(pos, updatedState, Block.UPDATE_ALL)) {
                return ItemInteractionResult.CONSUME;
            }
            SoundType sound = updatedState.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, updatedState));
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        BooleanProperty slot = slotFromHit(state, hitResult.getLocation(), pos);
        if (!state.getValue(slot)) {
            return InteractionResult.PASS;
        }
        VoxelShape clickedShape = shapeForMask(state.getValue(AXIS), slot == LEFT ? 1 : slot == CENTER ? 2 : 4);
        // Include the surface itself while rejecting clicks in the gaps around an occupied lane.
        if (!clickedShape.bounds().inflate(1.0E-7).contains(
                hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()))) {
            return InteractionResult.PASS;
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            BlockState updatedState = state.setValue(slot, false);
            boolean empty = !updatedState.getValue(LEFT)
                    && !updatedState.getValue(CENTER)
                    && !updatedState.getValue(RIGHT);
            if (!level.setBlock(pos, empty ? Blocks.AIR.defaultBlockState() : updatedState, Block.UPDATE_ALL)) {
                return InteractionResult.FAIL;
            }
            SoundType sound = state.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS,
                    sound.getVolume(), sound.getPitch());
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
            if (!player.hasInfiniteMaterials()) {
                ItemStack returned = new ItemStack(this);
                if (!player.getInventory().add(returned)) {
                    Block.popResource(level, pos, returned);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
