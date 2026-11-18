package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.cable.ControlChannel;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ControlBoxDialPoses {
    private static final Matrix4f[][][] POSES = new Matrix4f[4][4][16];
    private static final Matrix3f[][][] NORMALS = new Matrix3f[4][4][16];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            float yaw = switch (facing) {
                case SOUTH -> 0;
                case WEST -> -(float) Math.PI / 2;
                case NORTH -> (float) Math.PI;
                default -> (float) Math.PI / 2;
            };
            for (ControlChannel channel : ControlChannel.values()) {
                float x = (5.125f + 2 * channel.ordinal()) / 16;
                for (int value = 0; value < 16; value++) {
                    Matrix4f pose = new Matrix4f().translation(.5f, 0, .5f).rotateY(yaw).translate(-.5f, 0, -.5f)
                            .translate(x, 8.375f / 16, 4.25f / 16)
                            .rotateZ((float) Math.toRadians(-angle(value)))
                            .translate(-x, -8.375f / 16, -4.25f / 16);
                    POSES[facing.get2DDataValue()][channel.ordinal()][value] = pose;
                    NORMALS[facing.get2DDataValue()][channel.ordinal()][value] = new Matrix3f(pose);
                }
            }
        }
    }

    private ControlBoxDialPoses() {
    }

    public static int angle(int value) {
        return -135 + value * 18;
    }

    public static Matrix4fc pose(Direction facing, ControlChannel channel, int value) {
        return POSES[facing.get2DDataValue()][channel.ordinal()][value];
    }

    public static Matrix3fc normal(Direction facing, ControlChannel channel, int value) {
        return NORMALS[facing.get2DDataValue()][channel.ordinal()][value];
    }
}
