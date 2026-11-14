package com.aislen.createindustrialdetails.content.block.airduct;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class AirDuctCasingShapes {
    static final VoxelShape[] OCCLUSION = createShapes();

    private AirDuctCasingShapes() {}

    private static VoxelShape[] createShapes() {
        VoxelShape[] openings = new VoxelShape[6];
        openings[Direction.NORTH.ordinal()] = Block.box(1.5, 1.5, 0, 14.5, 14.5, 2);
        openings[Direction.SOUTH.ordinal()] = Block.box(1.5, 1.5, 14, 14.5, 14.5, 16);
        openings[Direction.WEST.ordinal()] = Block.box(0, 1.5, 1.5, 2, 14.5, 14.5);
        openings[Direction.EAST.ordinal()] = Block.box(14, 1.5, 1.5, 16, 14.5, 14.5);
        openings[Direction.DOWN.ordinal()] = Block.box(1.5, 0, 1.5, 14.5, 2, 14.5);
        openings[Direction.UP.ordinal()] = Block.box(1.5, 14, 1.5, 14.5, 16, 14.5);
        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = Shapes.block();
            for (Direction direction : Direction.values()) {
                if ((mask & (1 << direction.ordinal())) != 0)
                    shape = Shapes.join(shape, openings[direction.ordinal()], BooleanOp.ONLY_FIRST);
            }
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }
}
