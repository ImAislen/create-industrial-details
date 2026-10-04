package com.aislen.createindustrialdetails.content.block.airduct;

import com.mojang.serialization.MapCodec;
import com.aislen.createindustrialdetails.registry.ModParticles;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AirVentBlock extends Block implements IWrenchable {
    public static final MapCodec<AirVentBlock> CODEC = simpleCodec(AirVentBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty AIRFLOW = BooleanProperty.create("airflow");
    // Nearby random display sampling averages about one call per six client ticks.
    private static final int PARTICLE_ATTEMPTS = 6;

    // FACING points out through the grille; the connector touches the opposite block boundary.
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(1, 1, 13, 15, 15, 14.5), Block.box(2, 2, 14.5, 14, 14, 16));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(1, 1, 1.5, 15, 15, 3), Block.box(2, 2, 0, 14, 14, 1.5));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(1.5, 1, 1, 3, 15, 15), Block.box(0, 2, 2, 1.5, 14, 14));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(13, 1, 1, 14.5, 15, 15), Block.box(14.5, 2, 2, 16, 14, 14));
    private static final VoxelShape UP_SHAPE = Shapes.or(
            Block.box(1, 1.5, 1, 15, 3, 15), Block.box(2, 0, 2, 14, 1.5, 14));
    private static final VoxelShape DOWN_SHAPE = Shapes.or(
            Block.box(1, 13, 1, 15, 14.5, 15), Block.box(2, 14.5, 2, 14, 16, 14));

    public AirVentBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(AIRFLOW, false));
    }

    @Override
    public MapCodec<AirVentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AIRFLOW);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            level.setBlock(context.getClickedPos(), state.cycle(AIRFLOW), Block.UPDATE_CLIENTS);
            IWrenchable.playRotateSound(level, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.isClientSide || !state.getValue(AIRFLOW)) {
            return;
        }
        double density = AllConfigs.client().fanParticleDensity.get();
        if (density <= 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        // The grille lies three pixels inward from the rear mounting boundary.
        double offset = -5.0 / 16.0 + 1.0 / 64.0;
        double x = pos.getX() + .5 + facing.getStepX() * offset;
        double y = pos.getY() + .5 + facing.getStepY() * offset;
        double z = pos.getZ() + .5 + facing.getStepZ() * offset;
        for (int i = 0; i < PARTICLE_ATTEMPTS; i++) {
            if (random.nextFloat() < density) {
                level.addParticle(ModParticles.VENT_AIRFLOW.get(), x, y, z,
                        facing.getStepX(), facing.getStepY(), facing.getStepZ());
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
