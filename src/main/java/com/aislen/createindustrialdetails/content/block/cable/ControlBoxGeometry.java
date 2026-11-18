package com.aislen.createindustrialdetails.content.block.cable;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ControlBoxGeometry {
    private static final double COS = Math.cos(Math.PI / 8);
    private static final double SIN = Math.sin(Math.PI / 8);
    private static final AABB DOOR = new AABB(17.625, 0, 3.75, 18.125, 16, 16.75);
    private static final AABB HANDLE = new AABB(18.125, 6, 15.25, 18.375, 10, 16);
    private static final VoxelShape CABINET = Shapes.or(Block.box(2, 0, 0, 14, 16, 4), Block.box(2, 0, 4, 3, 16, 5),
            Block.box(13, 0, 4, 14, 16, 5), Block.box(3, 0, 4, 13, 1, 5), Block.box(3, 15, 4, 13, 16, 5),
            Block.box(3, 6.5, 4, 13, 13.25, 4.5));
    private static final VoxelShaper CABINET_SHAPES = VoxelShaper.forHorizontal(CABINET, Direction.SOUTH);
    private static final VoxelShaper CLOSED = VoxelShaper.forHorizontal(Shapes.or(Block.box(2, 0, 0, 14, 16, 5),
            Block.box(1.5, 0, 5, 14.5, 16, 5.5), Block.box(2.25, 6, 5.5, 3, 10, 5.75)), Direction.SOUTH);
    private static final VoxelShaper DOORS = VoxelShaper.forHorizontal(doorShape(), Direction.SOUTH);
    private static final VoxelShaper OPEN = VoxelShaper.forHorizontal(Shapes.or(CABINET, DOORS.get(Direction.SOUTH)), Direction.SOUTH);

    private ControlBoxGeometry() {
    }

    static VoxelShape shape(BlockState state) {
        return (state.getValue(ControlBoxBlock.OPEN) ? OPEN : CLOSED).get(state.getValue(ControlBoxBlock.FACING));
    }

    static VoxelShape doorShape(Direction facing) {
        return DOORS.get(facing);
    }

    static Vec3 toModel(Direction facing, Vec3 point) {
        return switch (facing) {
            case SOUTH -> point;
            case NORTH -> new Vec3(1 - point.x, point.y, 1 - point.z);
            case EAST -> new Vec3(1 - point.z, point.y, point.x);
            case WEST -> new Vec3(point.z, point.y, 1 - point.x);
            default -> throw new IllegalArgumentException();
        };
    }

    public static Vec3 fromModel(Direction facing, Vec3 point) {
        return switch (facing) {
            case SOUTH -> point;
            case NORTH -> new Vec3(1 - point.x, point.y, 1 - point.z);
            case EAST -> new Vec3(point.z, point.y, 1 - point.x);
            case WEST -> new Vec3(1 - point.z, point.y, point.x);
            default -> throw new IllegalArgumentException();
        };
    }

    static ControlChannel controlAt(BlockState state, Vec3 localHit) {
        if (!state.getValue(ControlBoxBlock.OPEN)) return null;
        Vec3 point = toModel(state.getValue(ControlBoxBlock.FACING), localHit).scale(16);
        if (point.y < 7.9 || point.y > 8.85 || point.z < 3.9 || point.z > 4.6) return null;
        for (ControlChannel channel : ControlChannel.values())
            if (Math.abs(point.x - (5.125 + channel.ordinal() * 2)) <= .475) return channel;
        return null;
    }

    static boolean hitsDoor(BlockState state, BlockPos pos, Vec3 start, Vec3 end) {
        return traceDoor(state, pos, start, end) != null;
    }

    static BlockHitResult traceDoor(BlockState state, BlockPos pos, Vec3 start, Vec3 end) {
        if (!state.getValue(ControlBoxBlock.OPEN)) return null;
        Direction facing = state.getValue(ControlBoxBlock.FACING);
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        Vec3 a = inverseDoor(toModel(facing, start.subtract(origin)).scale(16));
        Vec3 b = inverseDoor(toModel(facing, end.subtract(origin)).scale(16));
        Vec3 doorHit = DOOR.clip(a, b).orElse(null);
        Vec3 handleHit = HANDLE.clip(a, b).orElse(null);
        boolean handle = handleHit != null && (doorHit == null || a.distanceToSqr(handleHit) < a.distanceToSqr(doorHit));
        Vec3 point = handle ? handleHit : doorHit;
        if (point == null) return null;
        double doorDistance = a.distanceToSqr(point);
        BlockHitResult cabinetHit = CABINET_SHAPES.get(facing).clip(start, end, pos);
        if (cabinetHit != null && doorDistance > start.distanceToSqr(cabinetHit.getLocation()) * 256 + 1e-6) return null;
        AABB bounds = handle ? HANDLE : DOOR;
        Vec3 normal = Math.abs(point.x - bounds.minX) < 1e-6 ? new Vec3(-1, 0, 0)
                : Math.abs(point.x - bounds.maxX) < 1e-6 ? new Vec3(1, 0, 0)
                : Math.abs(point.y - bounds.minY) < 1e-6 ? new Vec3(0, -1, 0)
                : Math.abs(point.y - bounds.maxY) < 1e-6 ? new Vec3(0, 1, 0)
                : Math.abs(point.z - bounds.minZ) < 1e-6 ? new Vec3(0, 0, -1) : new Vec3(0, 0, 1);
        normal = new Vec3(COS * normal.x + SIN * normal.z, normal.y, -SIN * normal.x + COS * normal.z);
        normal = fromModel(facing, normal).subtract(fromModel(facing, Vec3.ZERO));
        Vec3 location = fromModel(facing, rotateDoor(point).scale(1 / 16.0)).add(origin);
        return new BlockHitResult(location, Direction.getNearest(normal.x, normal.y, normal.z), pos.immutable(), false);
    }

    private static Vec3 rotateDoor(Vec3 point) {
        double x = point.x - 18.0625, z = point.z - 12.9375;
        return new Vec3(18.0625 + COS * x + SIN * z, point.y, 12.9375 - SIN * x + COS * z);
    }

    private static Vec3 inverseDoor(Vec3 point) {
        double x = point.x - 18.0625, z = point.z - 12.9375;
        return new Vec3(18.0625 + COS * x - SIN * z, point.y, 12.9375 + SIN * x + COS * z);
    }

    private static VoxelShape doorShape() {
        VoxelShape shape = Shapes.empty();
        for (int strip = 0; strip < 8; strip++) {
            double z1 = 3.75 + 13.0 * strip / 8;
            double z2 = 3.75 + 13.0 * (strip + 1) / 8;
            shape = Shapes.or(shape, rotatedBounds(new AABB(DOOR.minX, DOOR.minY, z1,
                    DOOR.maxX, DOOR.maxY, z2)));
        }
        return Shapes.or(shape, Block.box(19.0052, 6, 14.9543, 19.5232, 10, 15.743)).optimize();
    }

    private static VoxelShape rotatedBounds(AABB box) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{box.minX, box.maxX})
            for (double z : new double[]{box.minZ, box.maxZ}) {
                Vec3 point = rotateDoor(new Vec3(x, 0, z));
                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
            }
        return Block.box(minX, box.minY, minZ, maxX, box.maxY, maxZ);
    }
}
