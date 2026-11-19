package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.aislen.createindustrialdetails.content.block.woodenpost.WoodenPostBlock;
import com.aislen.createindustrialdetails.content.block.woodenpost.WoodenPostPosition;
import com.cake.struts.content.shape.SurfaceClippingHelper.SurfacePlane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class WoodenBeamEndpoints {

    public static final double SNAP_SPACING = 4.0 / 16.0;

    private static final double ATTACHMENT_DEPTH = (6.0 / 16.0) + 1.0e-3;
    private static final double MARKER_SURFACE_OFFSET = 1.0 / 256.0;
    private static final double BEAM_HALF_SIZE = 4.0 / 16.0;

    private WoodenBeamEndpoints() {
    }

    public static Vec3 attachment(BlockPos anchorPos, Direction supportFace, WoodenBeamSnapPoint snap) {
        return snappedPoint(anchorPos, supportFace, snap, ATTACHMENT_DEPTH);
    }

    private static Vec3 snappedPoint(BlockPos anchorPos, Direction supportFace, WoodenBeamSnapPoint snap, double depth) {
        FaceBasis basis = basis(supportFace);
        return Vec3.atCenterOf(anchorPos)
                .relative(supportFace, -depth)
                .add(Vec3.atLowerCornerOf(basis.u()).scale(snap.gridU() * SNAP_SPACING))
                .add(Vec3.atLowerCornerOf(basis.v()).scale(snap.gridV() * SNAP_SPACING));
    }

    static Attachment geometryAttachment(
            @Nullable BlockGetter level,
            BlockPos anchorPos,
            Direction supportFace,
            WoodenBeamSnapPoint snap
    ) {
        Vec3 facePoint = snappedPoint(anchorPos, supportFace, snap, 0.5);
        Vec3 faceNormal = Vec3.atLowerCornerOf(supportFace.getNormal());
        SurfacePlane blockFace = new SurfacePlane(facePoint, faceNormal);
        BlockPos supportPos = anchorPos.relative(supportFace.getOpposite());
        var supportState = level == null ? null : level.getBlockState(supportPos);
        WoodenPostPosition postPosition = supportState != null && supportState.getBlock() instanceof WoodenPostBlock
                ? WoodenPostBlock.getSinglePosition(supportState) : null;
        if (postPosition == null) {
            return new Attachment(facePoint, blockFace, null);
        }
        AABB postBounds = postPosition.bounds().move(supportPos);
        double centreX = (postBounds.minX + postBounds.maxX) * 0.5;
        double centreZ = (postBounds.minZ + postBounds.maxZ) * 0.5;
        if (supportFace.getAxis().isHorizontal()) {
            return new Attachment(new Vec3(centreX, facePoint.y, centreZ), blockFace, postBounds);
        }

        double targetY = supportFace == Direction.UP
                ? postBounds.maxY - BEAM_HALF_SIZE
                : postBounds.minY + BEAM_HALF_SIZE;
        return new Attachment(new Vec3(centreX, targetY, centreZ), blockFace, postBounds);
    }

    public static Vec3 markerPoint(BlockPos anchorPos, Direction supportFace, WoodenBeamSnapPoint snap) {
        return snappedPoint(anchorPos, supportFace, snap, 0.5 - MARKER_SURFACE_OFFSET);
    }

    public static WoodenBeamSnapPoint nearest(BlockPos anchorPos, Direction supportFace, Vec3 hitLocation) {
        FaceBasis basis = basis(supportFace);
        Vec3 delta = hitLocation.subtract(Vec3.atCenterOf(anchorPos));
        double localU = delta.dot(Vec3.atLowerCornerOf(basis.u()));
        double localV = delta.dot(Vec3.atLowerCornerOf(basis.v()));
        return WoodenBeamSnapPoint.fromGrid(nearestGrid(localU), nearestGrid(localV));
    }

    public static WoodenBeamSnapPoint alignToPost(
            Direction supportFace,
            WoodenBeamSnapPoint selected,
            WoodenPostPosition postPosition
    ) {
        FaceBasis faceBasis = basis(supportFace);
        Vec3i selectedWorldOffset = add(
                scale(faceBasis.u(), selected.gridU()),
                scale(faceBasis.v(), selected.gridV())
        );
        Vec3i alignedWorldOffset = switch (supportFace.getAxis()) {
            case X -> new Vec3i(0, selectedWorldOffset.getY(), postPosition.gridZ());
            case Z -> new Vec3i(postPosition.gridX(), selectedWorldOffset.getY(), 0);
            case Y -> new Vec3i(postPosition.gridX(), 0, postPosition.gridZ());
        };
        return WoodenBeamSnapPoint.fromGrid(
                dot(alignedWorldOffset, faceBasis.u()),
                dot(alignedWorldOffset, faceBasis.v())
        );
    }

    private static int nearestGrid(double centeredCoordinate) {
        if (centeredCoordinate < -SNAP_SPACING / 2.0) {
            return -1;
        }
        if (centeredCoordinate > SNAP_SPACING / 2.0) {
            return 1;
        }
        return 0;
    }

    public static WoodenBeamSnapPoint transformSnap(
            Direction face,
            WoodenBeamSnapPoint snap,
            Rotation rotation,
            Mirror mirror
    ) {
        Vec3i worldOffset = add(
                scale(basis(face).u(), snap.gridU()),
                scale(basis(face).v(), snap.gridV())
        );
        Vec3i transformedOffset = transform(worldOffset, rotation, mirror);
        Direction transformedFace = mirror.mirror(rotation.rotate(face));
        FaceBasis transformedBasis = basis(transformedFace);
        int transformedU = dot(transformedOffset, transformedBasis.u());
        int transformedV = dot(transformedOffset, transformedBasis.v());
        return WoodenBeamSnapPoint.fromGrid(transformedU, transformedV);
    }

    public static Direction transformFace(Direction face, Rotation rotation, Mirror mirror) {
        return mirror.mirror(rotation.rotate(face));
    }

    public static FaceBasis basis(Direction face) {
        return switch (face) {
            case UP -> new FaceBasis(new Vec3i(1, 0, 0), new Vec3i(0, 0, -1));
            case DOWN -> new FaceBasis(new Vec3i(1, 0, 0), new Vec3i(0, 0, 1));
            case NORTH -> new FaceBasis(new Vec3i(-1, 0, 0), new Vec3i(0, 1, 0));
            case SOUTH -> new FaceBasis(new Vec3i(1, 0, 0), new Vec3i(0, 1, 0));
            case WEST -> new FaceBasis(new Vec3i(0, 0, 1), new Vec3i(0, 1, 0));
            case EAST -> new FaceBasis(new Vec3i(0, 0, -1), new Vec3i(0, 1, 0));
        };
    }

    private static Vec3i transform(Vec3i value, Rotation rotation, Mirror mirror) {
        int x = value.getX();
        int y = value.getY();
        int z = value.getZ();
        Vec3i rotated = switch (rotation) {
            case NONE -> new Vec3i(x, y, z);
            case CLOCKWISE_90 -> new Vec3i(-z, y, x);
            case CLOCKWISE_180 -> new Vec3i(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> new Vec3i(z, y, -x);
        };
        return switch (mirror) {
            case NONE -> rotated;
            case LEFT_RIGHT -> new Vec3i(rotated.getX(), rotated.getY(), -rotated.getZ());
            case FRONT_BACK -> new Vec3i(-rotated.getX(), rotated.getY(), rotated.getZ());
        };
    }

    private static Vec3i add(Vec3i a, Vec3i b) {
        return new Vec3i(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    private static Vec3i scale(Vec3i value, int scale) {
        return new Vec3i(value.getX() * scale, value.getY() * scale, value.getZ() * scale);
    }

    private static int dot(Vec3i a, Vec3i b) {
        return a.getX() * b.getX() + a.getY() * b.getY() + a.getZ() * b.getZ();
    }

    public record FaceBasis(Vec3i u, Vec3i v) {
    }

    record Attachment(Vec3 center, SurfacePlane blockFace, @Nullable AABB postBounds) {
    }
}
