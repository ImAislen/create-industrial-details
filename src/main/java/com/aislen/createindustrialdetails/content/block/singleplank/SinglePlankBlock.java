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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SinglePlankBlock extends Block {

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

    private static final VoxelShape[] SHAPES = createShapes();

    public SinglePlankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(LEFT, false)
                .setValue(CENTER, true)
                .setValue(RIGHT, false));
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[8];
        // Bits 0, 1, and 2 represent left, center, and right. Unions are built only once.
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = Shapes.empty();
            if ((mask & 1) != 0) {
                shape = Shapes.or(shape, LEFT_SHAPE);
            }
            if ((mask & 2) != 0) {
                shape = Shapes.or(shape, CENTER_SHAPE);
            }
            if ((mask & 4) != 0) {
                shape = Shapes.or(shape, RIGHT_SHAPE);
            }
            shapes[mask] = shape;
        }
        return shapes;
    }

    private static VoxelShape shapeForState(BlockState state) {
        int mask = (state.getValue(LEFT) ? 1 : 0)
                | (state.getValue(CENTER) ? 2 : 0)
                | (state.getValue(RIGHT) ? 4 : 0);
        return SHAPES[mask];
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
        builder.add(LEFT, CENTER, RIGHT);
    }

    private static BooleanProperty getPreferredSlot(double localZ) {
        if (localZ < 1.0 / 3.0) {
            return LEFT;
        }
        if (localZ > 2.0 / 3.0) {
            return RIGHT;
        }
        return CENTER;
    }

    @Nullable
    private static BooleanProperty getAvailableSlot(BlockState state, double localZ) {
        BooleanProperty preferred = getPreferredSlot(localZ);
        if (!state.getValue(preferred)) {
            return preferred;
        }
        // Center is the nearest alternative to either outer lane.
        if (!state.getValue(CENTER)) {
            return CENTER;
        }
        BooleanProperty side = localZ < 0.5 ? LEFT : RIGHT;
        if (!state.getValue(side)) {
            return side;
        }
        BooleanProperty other = side == LEFT ? RIGHT : LEFT;
        return state.getValue(other) ? null : other;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        double hitZ = context.getClickLocation().z;
        double localZ = hitZ - Math.floor(hitZ);
        return defaultBlockState()
                .setValue(LEFT, false)
                .setValue(CENTER, false)
                .setValue(RIGHT, false)
                .setValue(getPreferredSlot(localZ), true);
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
                hitResult.getLocation().z - pos.getZ());
    }

    ItemInteractionResult tryAddPlank(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            Direction clickedFace,
            double localZ
    ) {
        if (!state.is(this) || !(stack.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() != this) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, clickedFace, stack)) {
            return ItemInteractionResult.FAIL;
        }

        BooleanProperty slot = getAvailableSlot(state, localZ);
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
        BooleanProperty slot = getPreferredSlot(hitResult.getLocation().z - pos.getZ());
        if (!state.getValue(slot)) {
            return InteractionResult.PASS;
        }
        VoxelShape clickedShape = slot == LEFT ? LEFT_SHAPE : slot == CENTER ? CENTER_SHAPE : RIGHT_SHAPE;
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
