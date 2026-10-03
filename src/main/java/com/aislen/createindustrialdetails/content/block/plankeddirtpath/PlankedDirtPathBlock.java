package com.aislen.createindustrialdetails.content.block.plankeddirtpath;

import com.aislen.createindustrialdetails.content.block.singleplank.SinglePlankBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PlankedDirtPathBlock extends DirtPathBlock {
    public static final EnumProperty<Direction.Axis> AXIS = SinglePlankBlock.AXIS;
    public static final BooleanProperty LEFT = SinglePlankBlock.LEFT;
    public static final BooleanProperty CENTER = SinglePlankBlock.CENTER;
    public static final BooleanProperty RIGHT = SinglePlankBlock.RIGHT;

    private static final VoxelShape[][] PLANK_SHAPES = new VoxelShape[2][8];
    private static final VoxelShape[][] PATH_SHAPES = new VoxelShape[2][8];

    static {
        for (Direction.Axis axis : AXIS.getPossibleValues()) {
            int row = axis == Direction.Axis.X ? 0 : 1;
            for (int mask = 0; mask < 8; mask++) {
                VoxelShape planks = SinglePlankBlock.shapeForMask(axis, mask).move(0, 15.0 / 16.0, 0);
                PLANK_SHAPES[row][mask] = planks;
                PATH_SHAPES[row][mask] = Shapes.or(SHAPE, planks);
            }
        }
    }

    private final SinglePlankBlock plank;

    public PlankedDirtPathBlock(SinglePlankBlock plank, BlockBehaviour.Properties properties) {
        super(properties);
        this.plank = plank;
        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(LEFT, false)
                .setValue(CENTER, true)
                .setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, LEFT, CENTER, RIGHT);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return SinglePlankBlock.rotatePlanks(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return SinglePlankBlock.mirrorPlanks(state, mirror);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(plank);
    }

    private static int axisIndex(BlockState state) {
        return state.getValue(AXIS) == Direction.Axis.X ? 0 : 1;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PATH_SHAPES[axisIndex(state)][SinglePlankBlock.occupancyMask(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    public InteractionResult embedPlank(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || context.getClickedFace() != Direction.UP || !level.getBlockState(pos).is(Blocks.DIRT_PATH)) {
            return InteractionResult.PASS;
        }
        // Copy the standalone placement convention, including its facing axis and click thirds.
        BlockState state = withPropertiesOf(plank.getStateForPlacement(new BlockPlaceContext(context)));
        if (!state.canSurvive(level, pos)) {
            return InteractionResult.FAIL;
        }
        return placePlank(context.getItemInHand(), state, level, pos, player, context.getClickedFace()).result();
    }

    public ItemInteractionResult tryAddPlank(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, Direction clickedFace, Vec3 hitLocation) {
        if (!state.is(this) || !(stack.getItem() instanceof BlockItem item) || item.getBlock() != plank) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BooleanProperty slot = SinglePlankBlock.availableSlotFromHit(state, hitLocation, pos);
        if (slot == null) {
            return ItemInteractionResult.CONSUME;
        }
        return placePlank(stack, state.setValue(slot, true), level, pos, player, clickedFace);
    }

    private ItemInteractionResult placePlank(ItemStack stack, BlockState updatedState, Level level, BlockPos pos,
                                           Player player, Direction clickedFace) {
        if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() != plank
                || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, clickedFace, stack)) {
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            if (!level.setBlock(pos, updatedState, Block.UPDATE_ALL)) {
                return ItemInteractionResult.CONSUME;
            }
            SoundType sound = plank.defaultBlockState().getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, updatedState));
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return tryAddPlank(stack, state, level, pos, player, hitResult.getDirection(), hitResult.getLocation());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        BooleanProperty slot = SinglePlankBlock.slotFromHit(state, hitResult.getLocation(), pos);
        if (!state.getValue(slot)) {
            return InteractionResult.PASS;
        }
        int bit = slot == LEFT ? 1 : slot == CENTER ? 2 : 4;
        VoxelShape clickedPlank = PLANK_SHAPES[axisIndex(state)][bit];
        if (!clickedPlank.bounds().inflate(1.0E-7).contains(
                hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()))) {
            return InteractionResult.PASS;
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            BlockState remaining = state.setValue(slot, false);
            BlockState replacement = SinglePlankBlock.occupancyMask(remaining) == 0
                    ? Blocks.DIRT_PATH.defaultBlockState() : remaining;
            if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
                return InteractionResult.FAIL;
            }
            SoundType sound = plank.defaultBlockState().getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
            if (!player.hasInfiniteMaterials()) {
                ItemStack returned = new ItemStack(plank);
                if (!player.getInventory().add(returned)) {
                    Block.popResource(level, pos, returned);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        // DirtPathBlock schedules this once on obstruction; it is not a repeating or random tick.
        int count = Integer.bitCount(SinglePlankBlock.occupancyMask(state));
        if (count > 0) {
            Block.popResource(level, pos, new ItemStack(plank, count));
        }
        super.tick(state, level, pos, random);
    }
}
