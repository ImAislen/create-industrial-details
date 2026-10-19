package com.aislen.createindustrialdetails.content.block.supports;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class SmallSteelWallBracketBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final MapCodec<SmallSteelWallBracketBlock> CODEC = simpleCodec(SmallSteelWallBracketBlock::new);

    public static final EnumProperty<AttachFace> ATTACH_FACE = BlockStateProperties.ATTACH_FACE;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final IntegerProperty QUANTITY = IntegerProperty.create("quantity", 1, 2);

    private static final VoxelShape[][][] SHAPES = createShapes();

    public SmallSteelWallBracketBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.SOUTH)
                .setValue(ATTACH_FACE, AttachFace.WALL)
                .setValue(HALF, Half.TOP)
                .setValue(QUANTITY, 1));
    }

    @Override
    public MapCodec<SmallSteelWallBracketBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ATTACH_FACE, HALF, QUANTITY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) {
            return existing.getValue(QUANTITY) == 1 ? existing.setValue(QUANTITY, 2) : null;
        }
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.DOWN) {
            return null;
        }
        boolean floor = clickedFace == Direction.UP;
        BlockState state = defaultBlockState()
                .setValue(FACING, floor ? context.getHorizontalDirection().getOpposite() : clickedFace)
                .setValue(ATTACH_FACE, floor ? AttachFace.FLOOR : AttachFace.WALL)
                .setValue(HALF, !floor && context.getClickLocation().y - context.getClickedPos().getY() >= 0.5
                        ? Half.TOP : Half.BOTTOM);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (state.getValue(QUANTITY) == 1) {
            return IWrenchable.super.onSneakWrenched(state, context);
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return InteractionResult.SUCCESS;
        }
        if (!level.setBlock(pos, state.setValue(QUANTITY, 1), Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }
        // Partial pickup returns one physical bracket without invoking the double state's loot.
        if (player != null && !player.isCreative()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(this));
        }
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return state.getValue(QUANTITY) == 1 && context.getItemInHand().is(asItem());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(ATTACH_FACE) == AttachFace.CEILING) {
            return false;
        }
        BlockPos supportPos = pos.relative(supportDirection(state));
        BlockState support = level.getBlockState(supportPos);
        // Partial structural blocks can support the bracket without a full sturdy face.
        return !support.canBeReplaced() && !support.getCollisionShape(level, supportPos).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == supportDirection(state) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int layout = state.getValue(ATTACH_FACE) == AttachFace.FLOOR ? 2 : state.getValue(HALF) == Half.TOP ? 0 : 1;
        return SHAPES[layout][state.getValue(QUANTITY) - 1][state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static Direction supportDirection(BlockState state) {
        return state.getValue(ATTACH_FACE) == AttachFace.FLOOR ? Direction.DOWN : state.getValue(FACING).getOpposite();
    }

    private static VoxelShape[][][] createShapes() {
        // South-facing wall top, wall bottom, and floor layouts; the diagonal remains visual only.
        VoxelShape[] singles = {
                Shapes.or(Block.box(7, 8, 0, 9, 16, 1), Block.box(7, 15, 1, 9, 16, 8)),
                Shapes.or(Block.box(7, 0, 0, 9, 8, 1), Block.box(7, 0, 1, 9, 1, 8)),
                Shapes.or(Block.box(7, 0, 0, 9, 1, 8), Block.box(7, 1, 0, 9, 8, 1))
        };
        VoxelShape[][][] shapes = new VoxelShape[3][2][4];
        for (int layout = 0; layout < singles.length; layout++) {
            for (int quantity = 1; quantity <= 2; quantity++) {
                VoxelShape shape = quantity == 1 ? singles[layout]
                        : Shapes.or(singles[layout].move(-1.0 / 6.0, 0, 0), singles[layout].move(1.0 / 6.0, 0, 0));
                // Direction's horizontal indices run south, west, north, east, matching model Y rotations.
                for (int facing = 0; facing < 4; facing++) {
                    shapes[layout][quantity - 1][facing] = shape;
                    shape = rotateClockwise(shape);
                }
            }
        }
        return shapes;
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                rotated[0] = Shapes.or(rotated[0], Shapes.box(
                        1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
        return rotated[0];
    }
}
