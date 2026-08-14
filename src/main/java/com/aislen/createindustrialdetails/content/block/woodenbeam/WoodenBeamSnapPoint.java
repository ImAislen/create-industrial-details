package com.aislen.createindustrialdetails.content.block.woodenbeam;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * One of the nine discrete attachment points on a Wooden Beam support face.
 * Grid coordinates are stored as -1, 0, or 1 and map to 4, 8, or 12 pixels.
 */
public enum WoodenBeamSnapPoint {
    BOTTOM_LEFT(0, -1, -1),
    BOTTOM_CENTER(1, 0, -1),
    BOTTOM_RIGHT(2, 1, -1),
    MIDDLE_LEFT(3, -1, 0),
    CENTER(4, 0, 0),
    MIDDLE_RIGHT(5, 1, 0),
    TOP_LEFT(6, -1, 1),
    TOP_CENTER(7, 0, 1),
    TOP_RIGHT(8, 1, 1);

    private static final WoodenBeamSnapPoint[] BY_ID = values();

    private final byte id;
    private final int gridU;
    private final int gridV;

    WoodenBeamSnapPoint(int id, int gridU, int gridV) {
        this.id = (byte) id;
        this.gridU = gridU;
        this.gridV = gridV;
    }

    public byte id() {
        return id;
    }

    public int gridU() {
        return gridU;
    }

    public int gridV() {
        return gridV;
    }

    public double u() {
        return 0.5 + gridU * WoodenBeamEndpoints.SNAP_SPACING;
    }

    public double v() {
        return 0.5 + gridV * WoodenBeamEndpoints.SNAP_SPACING;
    }

    public static WoodenBeamSnapPoint byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : CENTER;
    }

    public static WoodenBeamSnapPoint fromGrid(int gridU, int gridV) {
        for (WoodenBeamSnapPoint point : BY_ID) {
            if (point.gridU == gridU && point.gridV == gridV) {
                return point;
            }
        }
        return CENTER;
    }

    public WoodenBeamSnapPoint rotate(Direction face, Rotation rotation) {
        return WoodenBeamEndpoints.transformSnap(face, this, rotation, Mirror.NONE);
    }

    public WoodenBeamSnapPoint mirror(Direction face, Mirror mirror) {
        return WoodenBeamEndpoints.transformSnap(face, this, Rotation.NONE, mirror);
    }
}
