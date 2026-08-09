package com.aislen.createindustrialdetails.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mooring bollard with a detailed selection/outline shape matching the
 * current Blockbench model and a cheaper physical collision shape.
 *
 * All shapes are static and constructed once when the class loads.
 */
public final class MooringBollardBlock extends Block {

    private static final VoxelShape OUTLINE_SHAPE = or(
            // Base plate
            Block.box(2.75, 0.0, 2.75, 13.25, 1.0, 13.25),

            // Four mounting bolts
            Block.box(4.0, 1.0, 4.0, 5.0, 1.5, 5.0),
            Block.box(11.0, 1.0, 4.0, 12.0, 1.5, 5.0),
            Block.box(4.0, 1.0, 11.0, 5.0, 1.5, 12.0),
            Block.box(11.0, 1.0, 11.0, 12.0, 1.5, 12.0),

            // Main octagonal post: y 1.0 -> 8.75
            Block.box(6.76, 1.0, 5.00, 9.24, 8.75, 5.88),
            Block.box(5.88, 1.0, 5.88, 10.12, 8.75, 6.76),
            Block.box(5.00, 1.0, 6.76, 11.00, 8.75, 9.24),
            Block.box(5.88, 1.0, 9.24, 10.12, 8.75, 10.12),
            Block.box(6.76, 1.0, 10.12, 9.24, 8.75, 11.00),

            // Top collar transition: y 8.75 -> 9.0
            Block.box(6.71, 8.75, 4.88, 9.29, 9.0, 5.79),
            Block.box(5.79, 8.75, 5.79, 10.21, 9.0, 6.71),
            Block.box(4.88, 8.75, 6.71, 11.12, 9.0, 9.29),
            Block.box(5.79, 8.75, 9.29, 10.21, 9.0, 10.21),
            Block.box(6.71, 8.75, 10.21, 9.29, 9.0, 11.12),

            // Top collar: y 9.0 -> 9.5
            Block.box(6.55, 9.0, 4.50, 9.45, 9.5, 5.53),
            Block.box(5.53, 9.0, 5.53, 10.47, 9.5, 6.55),
            Block.box(4.50, 9.0, 6.55, 11.50, 9.5, 9.45),
            Block.box(5.53, 9.0, 9.45, 10.47, 9.5, 10.47),
            Block.box(6.55, 9.0, 10.47, 9.45, 9.5, 11.50),

            // Upper post visible between collar and cap: y 9.5 -> 10.0
            Block.box(6.76, 9.5, 5.00, 9.24, 10.0, 5.88),
            Block.box(5.88, 9.5, 5.88, 10.12, 10.0, 6.76),
            Block.box(5.00, 9.5, 6.76, 11.00, 10.0, 9.24),
            Block.box(5.88, 9.5, 9.24, 10.12, 10.0, 10.12),
            Block.box(6.76, 9.5, 10.12, 9.24, 10.0, 11.00),

            // Top cap: y 10.0 -> 11.5
            Block.box(6.45, 10.0, 4.25, 9.55, 11.5, 5.35),
            Block.box(5.35, 10.0, 5.35, 10.65, 11.5, 6.45),
            Block.box(4.25, 10.0, 6.45, 11.75, 11.5, 9.55),
            Block.box(5.35, 10.0, 9.55, 10.65, 11.5, 10.65),
            Block.box(6.45, 10.0, 10.65, 9.55, 11.5, 11.75)
    );

    private static final VoxelShape COLLISION_SHAPE = or(
            Block.box(2.75, 0.0, 2.75, 13.25, 1.0, 13.25),

            // Main post
            Block.box(6.0, 1.0, 5.0, 10.0, 10.0, 11.0),
            Block.box(5.0, 1.0, 6.0, 11.0, 10.0, 10.0),

            // Collar
            Block.box(5.5, 8.75, 4.5, 10.5, 9.5, 11.5),
            Block.box(4.5, 8.75, 5.5, 11.5, 9.5, 10.5),

            // Cap
            Block.box(5.35, 10.0, 4.25, 10.65, 11.5, 11.75),
            Block.box(4.25, 10.0, 5.35, 11.75, 11.5, 10.65)
    );

    public MooringBollardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return OUTLINE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return COLLISION_SHAPE;
    }

    private static VoxelShape or(VoxelShape... shapes) {
        VoxelShape result = Shapes.empty();

        for (VoxelShape shape : shapes) {
            result = Shapes.or(result, shape);
        }

        return result;
    }
}
