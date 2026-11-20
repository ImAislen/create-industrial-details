package com.aislen.createindustrialdetails.content.block.airduct;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AirDuctBlock extends PipeBlock implements IWrenchable, TransformableBlock {

    public static final MapCodec<AirDuctBlock> CODEC = simpleCodec(AirDuctBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<AirDuctCasing> CASING = EnumProperty.create("casing", AirDuctCasing.class);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new AirDuctPlacementHelper());

    public AirDuctBlock(Properties properties) {
        super(6 / 16f, properties);

        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(CASING, AirDuctCasing.NONE)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                AXIS,
                CASING,
                NORTH,
                SOUTH,
                EAST,
                WEST,
                UP,
                DOWN
        );

        super.createBlockStateDefinition(builder);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        AirDuctCasing previous = state.getValue(CASING);
        if (previous == AirDuctCasing.NONE) return InteractionResult.PASS;
        if (context.getLevel() instanceof ServerLevel level) {
            var event = new BlockEvent.BreakEvent(level, context.getClickedPos(), state, context.getPlayer());
            NeoForge.EVENT_BUS.post(event);
            if (!event.isCanceled() && level.setBlock(context.getClickedPos(), state.setValue(CASING, AirDuctCasing.NONE), Block.UPDATE_ALL)) {
                returnMaterial(context.getPlayer(), previous);
                IWrenchable.playRemoveSound(level, context.getClickedPos());
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void returnMaterial(@Nullable Player player, AirDuctCasing casing) {
        if (player != null && !player.isCreative() && casing != AirDuctCasing.NONE)
            player.getInventory().placeItemBackInInventory(casing.asStack());
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
        AirDuctCasing casing = AirDuctCasing.fromItem(stack);
        if (casing != AirDuctCasing.NONE) {
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hitResult.getDirection(), stack))
                return ItemInteractionResult.FAIL;
            AirDuctCasing previous = state.getValue(CASING);
            if (previous != casing && !level.isClientSide
                    && level.setBlock(pos, state.setValue(CASING, casing), Block.UPDATE_ALL)) {
                if (!player.isCreative()) stack.shrink(1);
                returnMaterial(player, previous);
                var sound = casing.asStack().getItem() instanceof BlockItem item
                        ? item.getBlock().defaultBlockState().getSoundType() : state.getSoundType();
                level.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                        (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !helper.matchesItem(stack)
                || !helper.matchesState(state)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        PlacementOffset offset = helper.getOffset(player, level, state, pos, hitResult);
        if (!offset.isSuccessful()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return offset.placeInWorld(level, blockItem, player, hand, hitResult);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        LevelAccessor level = context.getLevel();

        BlockState state = defaultBlockState()
                .setValue(AXIS, context.getClickedFace().getAxis());

        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));

            state = state.setValue(
                    PROPERTY_BY_DIRECTION.get(direction),
                    canConnectTo(neighbour, direction)
            );
        }

        return state;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbourState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighbourPos
    ) {
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);

        return state.setValue(property, canConnectTo(neighbourState, direction));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(CASING) == AirDuctCasing.NONE ? super.getShape(state, level, pos, context) : Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(CASING) == AirDuctCasing.NONE ? Shapes.empty()
                : AirDuctCasingShapes.OCCLUSION[casingOpenings(state)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(CASING) != AirDuctCasing.NONE;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(CASING) == AirDuctCasing.NONE ? super.getLightBlock(state, level, pos) : level.getMaxLightLevel();
    }

    public static boolean hasCasingOpening(BlockState state, Direction face) {
        return (casingOpenings(state) & (1 << face.ordinal())) != 0;
    }

    @Override
    public BlockState getAppearance(BlockState state, net.minecraft.world.level.BlockAndTintGetter level, BlockPos pos,
                                    Direction side, @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        AirDuctCasing casing = state.getValue(CASING);
        return casing.isConnectedTexture() && !hasCasingOpening(state, side) ? casing.materialState() : state;
    }

    static int casingOpenings(BlockState state) {
        int mask = 0;
        for (Direction direction : DIRECTIONS) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) mask |= 1 << direction.ordinal();
        }
        if (mask == 0) {
            Direction end = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
            return (1 << end.ordinal()) | (1 << end.getOpposite().ordinal());
        }
        if (Integer.bitCount(mask) == 1) {
            Direction end = DIRECTIONS[Integer.numberOfTrailingZeros(mask)];
            return mask | (1 << end.getOpposite().ordinal());
        }
        return mask;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction direction : Direction.values())
            rotated = rotated.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(direction)), state.getValue(PROPERTY_BY_DIRECTION.get(direction)));
        return rotated.setValue(AXIS, rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS))).getAxis());
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction direction : Direction.values())
            mirrored = mirrored.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(direction)), state.getValue(PROPERTY_BY_DIRECTION.get(direction)));
        return mirrored;
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        BlockState mirrored = transform.mirror == null ? state : mirror(state, transform.mirror);
        BlockState rotated = mirrored;
        for (Direction direction : Direction.values())
            rotated = rotated.setValue(PROPERTY_BY_DIRECTION.get(transform.rotateFacing(direction)), mirrored.getValue(PROPERTY_BY_DIRECTION.get(direction)));
        return rotated.setValue(AXIS, transform.rotateFacing(Direction.get(Direction.AxisDirection.POSITIVE, mirrored.getValue(AXIS))).getAxis());
    }

    @Nullable
    static Direction.Axis placementAssistAxis(BlockState state) {
        Direction connectedDirection = null;
        int connections = 0;

        for (Direction direction : Direction.values()) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                continue;
            }
            connections++;
            if (connections > 2) {
                return null;
            }
            if (connectedDirection == null) {
                connectedDirection = direction;
            } else if (connectedDirection.getOpposite() != direction) {
                return null;
            }
        }

        return connectedDirection == null
                ? state.getValue(AXIS)
                : connectedDirection.getAxis();
    }

    private boolean canConnectTo(BlockState state, Direction direction) {
        return state.getBlock() instanceof AirDuctBlock
                || (state.getBlock() instanceof AirVentBlock && state.getValue(AirVentBlock.FACING) == direction);
    }
}
