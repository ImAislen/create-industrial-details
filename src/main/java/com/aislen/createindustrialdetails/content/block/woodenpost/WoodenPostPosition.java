package com.aislen.createindustrialdetails.content.block.woodenpost;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The nine discrete X/Z locations available to an 8-pixel-wide Wooden Post. */
public enum WoodenPostPosition implements StringRepresentable {
    NORTH_WEST("north_west", -1, -1),
    NORTH("north", 0, -1),
    NORTH_EAST("north_east", 1, -1),
    WEST("west", -1, 0),
    CENTER("center", 0, 0),
    EAST("east", 1, 0),
    SOUTH_WEST("south_west", -1, 1),
    SOUTH("south", 0, 1),
    SOUTH_EAST("south_east", 1, 1);

    private static final double LOWER_SNAP_THRESHOLD = 0.375D;
    private static final double UPPER_SNAP_THRESHOLD = 0.625D;
    private static final WoodenPostPosition[] VALUES = values();

    private final String serializedName;
    private final int xIndex;
    private final int zIndex;
    private final VoxelShape shape;

    WoodenPostPosition(String serializedName, int xIndex, int zIndex) {
        this.serializedName = serializedName;
        this.xIndex = xIndex;
        this.zIndex = zIndex;

        double centerX = 8.0D + xIndex * 4.0D;
        double centerZ = 8.0D + zIndex * 4.0D;
        this.shape = Block.box(centerX - 4.0D, 0.0D, centerZ - 4.0D,
                centerX + 4.0D, 16.0D, centerZ + 4.0D);
    }

    public static WoodenPostPosition nearest(double localX, double localZ) {
        return fromIndices(nearestIndex(localX), nearestIndex(localZ));
    }

    private static int nearestIndex(double coordinate) {
        if (coordinate < LOWER_SNAP_THRESHOLD) {
            return -1;
        }
        return coordinate > UPPER_SNAP_THRESHOLD ? 1 : 0;
    }

    private static WoodenPostPosition fromIndices(int xIndex, int zIndex) {
        for (WoodenPostPosition position : VALUES) {
            if (position.xIndex == xIndex && position.zIndex == zIndex) {
                return position;
            }
        }
        throw new IllegalArgumentException("Invalid Wooden Post grid indices: " + xIndex + ", " + zIndex);
    }

    public VoxelShape shape() {
        return shape;
    }

    public int gridX() {
        return xIndex;
    }

    public int gridZ() {
        return zIndex;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
