package com.aislen.createindustrialdetails.content.block.cable.individual;

import net.minecraft.util.StringRepresentable;
import net.minecraft.core.Direction;

public enum CableRoute implements StringRepresentable {
    NONE, X, Y, Z, SOUTH, WEST, WALL_EAST, WALL_NORTH, WALL_WEST, WALL_NORTH_REVERSED, WALL_EAST_REVERSED, WALL_SOUTH_REVERSED, WALL_WEST_REVERSED;

    public boolean reversedWall() { return ordinal() >= WALL_NORTH_REVERSED.ordinal(); }
    public boolean vertical() { return this == Y || ordinal() >= 6; }
    public int turns() { return switch (this) { case X, WALL_WEST, WALL_EAST_REVERSED -> 1; case SOUTH, WALL_NORTH, WALL_SOUTH_REVERSED -> 2; case WEST, WALL_EAST, WALL_WEST_REVERSED -> 3; default -> 0; }; }
    public Direction.Axis axis() { return vertical() ? Direction.Axis.Y : (turns() % 2 == 0 ? Direction.Axis.Z : Direction.Axis.X); }
    public static CableRoute floor(Direction facing) { return switch (facing) { case EAST -> X; case SOUTH -> SOUTH; case WEST -> WEST; default -> Z; }; }
    public static CableRoute wall(Direction face) { return switch (face) { case NORTH -> WALL_NORTH; case EAST -> WALL_EAST; case WEST -> WALL_WEST; default -> Y; }; }
    public net.minecraft.world.phys.Vec3 transform(net.minecraft.world.phys.Vec3 point) {
        double x = point.x, z = reversedWall() ? 1 - point.z : point.z;
        for (int i = 0; i < turns(); i++) { double old = x; x = 1 - z; z = old; }
        return new net.minecraft.world.phys.Vec3(x, reversedWall() ? 1 - point.y : point.y, z);
    }

    public static CableRoute along(Direction.Axis axis) { return values()[axis.ordinal() + 1]; }
    public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
