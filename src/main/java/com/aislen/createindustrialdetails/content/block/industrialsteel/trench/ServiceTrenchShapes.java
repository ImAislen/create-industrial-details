package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.*;

final class ServiceTrenchShapes {
    private static final Direction[] FACES = Direction.values();
    private static final Direction.Axis[] AXES = Direction.Axis.values();
    private static final VoxelShape[][][] SHELLS = createShells();

    private ServiceTrenchShapes() {}

    static VoxelShape empty(BlockState state) {
        return SHELLS[orientation(state)][state.getValue(OPEN) ? 1 : 0][caps(state)];
    }

    static VoxelShape occupied(BlockState state) {
        if (!state.getValue(OPEN)) return Shapes.block();
        int orientation = orientation(state);
        if (state.getBlock() instanceof ServiceTrenchPipeBlock) {
            int connections = 0;
            for (Direction face : FACES)
                if (FluidPipeBlock.isOpenAt(state, face)) connections |= 1 << face.ordinal();
            return OpenShapes.PIPES[orientation][connections];
        }
        int axis = state.getValue(BlockStateProperties.AXIS).ordinal();
        return (state.getBlock() instanceof ServiceTrenchShaftBlock ? OpenShapes.SHAFTS : OpenShapes.GLASS)
                [orientation][axis];
    }

    private static int orientation(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? 1 : 0;
    }

    private static int caps(BlockState state) {
        return (state.getValue(CAP_NEGATIVE) ? 1 : 0) | (state.getValue(CAP_POSITIVE) ? 2 : 0);
    }

    private static VoxelShape[][][] createShells() {
        VoxelShape[][][] shapes = new VoxelShape[2][2][4];
        for (int axis = 0; axis < shapes.length; axis++) {
            VoxelShape body = Shapes.or(box(axis, 0, 0, 0, 16, 1, 16),
                    box(axis, 0, 1, 0, 1, 15, 16), box(axis, 15, 1, 0, 16, 15, 16),
                    box(axis, 0, 15, 0, 3, 16, 16), box(axis, 13, 15, 0, 16, 16, 16));
            VoxelShape grille = Shapes.or(box(axis, 3, 15, 0, 13, 16, 2),
                    box(axis, 3, 15, 4.75, 13, 16, 6.75), box(axis, 3, 15, 9.25, 13, 16, 11.25),
                    box(axis, 3, 15, 14, 13, 16, 16));
            VoxelShape negative = box(axis, 1, 1, 0, 15, 15, 1);
            VoxelShape positive = box(axis, 1, 1, 15, 15, 15, 16);
            for (int open = 0; open <= 1; open++) {
                VoxelShape shell = open == 0 ? Shapes.or(body, grille) : body;
                shapes[axis][open][0] = shell;
                shapes[axis][open][1] = Shapes.or(shell, negative);
                shapes[axis][open][2] = Shapes.or(shell, positive);
                shapes[axis][open][3] = Shapes.or(shell, negative, positive);
            }
        }
        return shapes;
    }

    private static VoxelShape box(int axis, double x1, double y1, double z1, double x2, double y2, double z2) {
        return axis == 0 ? Block.box(x1, y1, z1, x2, y2, z2) : Block.box(z1, y1, x1, z2, y2, x2);
    }

    private static VoxelShape[][] withUtility(VoxelShape[] utilityShapes) {
        VoxelShape[][] shapes = new VoxelShape[2][utilityShapes.length];
        for (int axis = 0; axis < shapes.length; axis++)
            for (int utility = 0; utility < utilityShapes.length; utility++)
                shapes[axis][utility] = Shapes.or(SHELLS[axis][1][0], utilityShapes[utility]);
        return shapes;
    }

    private static VoxelShape[] pipeShapes() {
        VoxelShape[] shapes = new VoxelShape[1 << FACES.length];
        for (int mask = 0; mask < shapes.length; mask++) {
            BlockState pipe = AllBlocks.FLUID_PIPE.getDefaultState();
            for (Direction side : FACES)
                pipe = pipe.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), (mask & (1 << side.ordinal())) != 0);
            shapes[mask] = pipe.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        }
        return shapes;
    }

    private static VoxelShape[] axisShapes(net.createmod.catnip.math.VoxelShaper utility) {
        VoxelShape[] shapes = new VoxelShape[AXES.length];
        for (Direction.Axis axis : AXES)
            shapes[axis.ordinal()] = utility.get(axis);
        return shapes;
    }

    private static final class OpenShapes {
        static final VoxelShape[][] PIPES = withUtility(pipeShapes());
        static final VoxelShape[][] GLASS = withUtility(axisShapes(AllShapes.EIGHT_VOXEL_POLE));
        static final VoxelShape[][] SHAFTS = withUtility(axisShapes(AllShapes.SIX_VOXEL_POLE));
    }
}
