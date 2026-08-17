package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamConnectionEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamSnapPoint;
import com.aislen.createindustrialdetails.content.block.woodenpost.WoodenPostBlock;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.cap.CapAccumulator;
import com.cake.struts.content.connection.GirderConnectionNode;
import com.cake.struts.content.geometry.StrutGeometry;
import com.cake.struts.content.geometry.StrutVertex;
import com.cake.struts.content.mesh.StrutMeshQuad;
import com.cake.struts.content.mesh.StrutSegmentMesh;
import com.cake.struts.internal.util.BakedQuadHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint-aware adaptation of the small, non-extensible portion of Struts
 * 1.2.1's StrutModelBuilder/StrutModelManipulator (MIT). Ordinary supports use
 * Struts clipping/caps directly; Post ends use the isolated MIT-attributed
 * local-Z crop/direct-transform adaptation below.
 */
public final class WoodenBeamModelBuilder {

    private static final Map<StrutModelType, StrutSegmentMesh> SEGMENT_MESHES = new HashMap<>();
    private static final Map<StrutModelType, DirectSegmentMesh> DIRECT_SEGMENT_MESHES = new HashMap<>();
    private static final double POST_SUPPORT_OVERLAP = 1.0 / 256.0;
    private static final double RENDER_BEAM_WIDTH_PIXELS = 7.99;
    private static final double RENDER_BEAM_HALF_EXTENT = RENDER_BEAM_WIDTH_PIXELS / 32.0;

    private WoodenBeamModelBuilder() {
    }

    public static List<BakedQuad> buildConnectionQuads(WoodenBeamBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 blockOrigin = Vec3.atLowerCornerOf(pos);
        StrutModelType modelType = blockEntity.getModelType();
        List<BakedQuad> quads = new ArrayList<>();
        var connections = blockEntity.getConnectionsCopy();

        for (GirderConnectionNode connection : connections) {
            BlockPos peerPos = connection.absoluteFrom(pos);
            WoodenBeamConnectionEndpoints endpoints = blockEntity.getEndpoints(connection);
            Vec3 localEndpoint = WoodenBeamEndpoints.physicalAttachment(
                    blockEntity.getLevel(),
                    pos,
                    endpoints.localFace(),
                    endpoints.localSnap()
            );
            Vec3 peerEndpoint = WoodenBeamEndpoints.physicalAttachment(
                    blockEntity.getLevel(),
                    peerPos,
                    endpoints.peerFace(),
                    endpoints.peerSnap()
            );

            boolean localIsCanonicalStart = pos.compareTo(peerPos) < 0;
            Vec3 canonicalStart = localIsCanonicalStart ? localEndpoint : peerEndpoint;
            Vec3 canonicalEnd = localIsCanonicalStart ? peerEndpoint : localEndpoint;
            Vec3 span = canonicalEnd.subtract(canonicalStart);
            if (span.lengthSqr() < 1.0e-4) {
                continue;
            }
            double totalLength = span.length();
            double halfLength = totalLength * 0.5;
            Vec3 direction = span.scale(1.0 / totalLength);
            Vec3 midpoint = canonicalStart.add(span.scale(0.5));
            PlaneSelection planeSelection = renderClippingPlane(
                    blockEntity,
                    pos,
                    endpoints.localFace(),
                    endpoints.localSnap(),
                    localEndpoint,
                    peerEndpoint,
                    modelType.shapeSizeXPixels() / 32.0,
                    modelType.shapeSizeYPixels() / 32.0
            );
            EndpointPlane localPlane = planeSelection.plane();
            Vec3 directionFromLocalEndpoint = localIsCanonicalStart ? direction : direction.reverse();

            if (planeSelection.path() == PlanePath.POST_PERPENDICULAR) {
                double capScalar = localPlane.point().subtract(canonicalStart).dot(direction);
                double startScalar = localIsCanonicalStart ? capScalar : halfLength;
                double endScalar = localIsCanonicalStart ? halfLength : capScalar;
                double renderLength = endScalar - startScalar;
                if (renderLength <= StrutGeometry.EPSILON) {
                    continue;
                }
                Vec3 renderedStart = canonicalStart.add(direction.scale(startScalar));
                Vec3 meshOrigin = renderedStart.add(direction.scale(0.5));
                quads.addAll(bakePostConnection(
                        meshOrigin.subtract(blockOrigin),
                        direction,
                        renderLength,
                        (float) (startScalar + 0.5),
                        localPlane.point().subtract(blockOrigin),
                        localPlane.normal(),
                        modelType
                ));
                continue;
            }

            double endpointExtension = requiredEndpointExtension(
                    localEndpoint,
                    localPlane.point(),
                    localPlane.normal(),
                    directionFromLocalEndpoint,
                    direction,
                    modelType.shapeSizeXPixels() / 32.0,
                    modelType.shapeSizeYPixels() / 32.0
            );

            // Both block entities render in the same canonical direction. The first
            // half physically ends at the midpoint; the reciprocal half begins there.
            // Each support-side end is extended only far enough for its own clipping
            // plane to cross all four longitudinal faces and produce one closed cap.
            Vec3 meshOrigin = localIsCanonicalStart
                    ? canonicalStart.add(direction.scale(0.5 - endpointExtension))
                    : midpoint.add(direction.scale(0.5));
            float textureStartOffset = localIsCanonicalStart
                    ? (float) (0.5 - endpointExtension)
                    : (float) (halfLength + 0.5);
            quads.addAll(bakeConnection(
                    meshOrigin.subtract(blockOrigin),
                    direction,
                    halfLength + endpointExtension,
                    textureStartOffset,
                    localPlane.point().subtract(blockOrigin),
                    localPlane.normal(),
                    modelType
            ));
        }
        return List.copyOf(quads);
    }

    /**
     * Beam attachment coordinates intentionally remain based on the anchor block.
     * A Post can sit inward from its block boundary, so prefer a square Beam end
     * buried inside its actual bounds. The prior surface-aligned plane remains the
     * safe fallback for saved connections whose centreline cannot contain that cap.
     */
    private static PlaneSelection renderClippingPlane(
            WoodenBeamBlockEntity blockEntity,
            BlockPos anchorPos,
            Direction supportFace,
            WoodenBeamSnapPoint snap,
            Vec3 localEndpoint,
            Vec3 peerEndpoint,
            double halfWidth,
            double halfHeight
    ) {
        Vec3 planePoint = WoodenBeamEndpoints.clippingPlanePoint(anchorPos, supportFace, snap);
        Vec3 planeNormal = Vec3.atLowerCornerOf(supportFace.getNormal());
        BlockPos supportPos = anchorPos.relative(supportFace.getOpposite());
        if (blockEntity.getLevel() == null) {
            return new PlaneSelection(
                    new EndpointPlane(planePoint, planeNormal),
                    PlanePath.NORMAL_SUPPORT
            );
        }

        var supportState = blockEntity.getLevel().getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof WoodenPostBlock)) {
            return new PlaneSelection(
                    new EndpointPlane(planePoint, planeNormal),
                    PlanePath.NORMAL_SUPPORT
            );
        }

        var postPosition = WoodenPostBlock.getSinglePosition(supportState);
        if (postPosition == null) {
            return new PlaneSelection(
                    new EndpointPlane(planePoint, planeNormal),
                    PlanePath.NORMAL_SUPPORT
            );
        }
        AABB postBounds = postPosition
                .shape()
                .bounds()
                .move(supportPos);
        EndpointPlane perpendicularPlane = perpendicularPostPlane(
                peerEndpoint,
                localEndpoint,
                postBounds,
                halfWidth,
                halfHeight
        );
        if (perpendicularPlane != null) {
            return new PlaneSelection(
                    perpendicularPlane,
                    PlanePath.POST_PERPENDICULAR
            );
        }

        Vec3 towardVisibleBeam = peerEndpoint.subtract(localEndpoint);
        if (towardVisibleBeam.lengthSqr() <= StrutGeometry.EPSILON) {
            return new PlaneSelection(
                    new EndpointPlane(planePoint, planeNormal),
                    PlanePath.NORMAL_SUPPORT
            );
        }
        // A saved/misaligned Post connection that misses the eroded bounds still
        // terminates deterministically at its physical Post-centre endpoint.
        return new PlaneSelection(
                new EndpointPlane(localEndpoint, towardVisibleBeam.normalize()),
                PlanePath.POST_PERPENDICULAR
        );
    }

    /**
     * Places a square, Beam-perpendicular end inside a Post. Eroding the Post
     * bounds by the projected cap half-extents turns the containment problem
     * into a centreline/AABB intersection.
     */
    private static EndpointPlane perpendicularPostPlane(
            Vec3 peerEndpoint,
            Vec3 localEndpoint,
            AABB postBounds,
            double halfWidth,
            double halfHeight
    ) {
        Vec3 towardVisibleBeam = peerEndpoint.subtract(localEndpoint);
        double endpointDistance = towardVisibleBeam.length();
        if (endpointDistance <= StrutGeometry.EPSILON) {
            return null;
        }
        towardVisibleBeam = towardVisibleBeam.scale(1.0 / endpointDistance);

        CrossSectionAxes crossSection = crossSectionAxes(towardVisibleBeam);
        Vec3 capExtents = new Vec3(
                Math.abs(crossSection.crossAxisX().x) * halfWidth
                        + Math.abs(crossSection.crossAxisY().x) * halfHeight,
                Math.abs(crossSection.crossAxisX().y) * halfWidth
                        + Math.abs(crossSection.crossAxisY().y) * halfHeight,
                Math.abs(crossSection.crossAxisX().z) * halfWidth
                        + Math.abs(crossSection.crossAxisY().z) * halfHeight
        );
        AABB capCentreBounds = erode(postBounds, capExtents);
        if (capCentreBounds == null) {
            return null;
        }

        Vec3 towardPost = towardVisibleBeam.reverse();
        RayInterval interval = intersectBounds(
                peerEndpoint,
                towardPost,
                capCentreBounds
        );
        if (interval == null) {
            return null;
        }

        double intervalLength = Math.max(0.0, interval.exitDistance() - interval.entryDistance());
        double burialDistance = Math.min(POST_SUPPORT_OVERLAP, intervalLength * 0.5);
        Vec3 capCentre = peerEndpoint.add(towardPost.scale(interval.entryDistance() + burialDistance));
        return new EndpointPlane(capCentre, towardVisibleBeam);
    }

    private static AABB erode(AABB bounds, Vec3 extents) {
        double minX = bounds.minX + extents.x;
        double maxX = bounds.maxX - extents.x;
        double minY = bounds.minY + extents.y;
        double maxY = bounds.maxY - extents.y;
        double minZ = bounds.minZ + extents.z;
        double maxZ = bounds.maxZ - extents.z;

        double[] x = normalizeErodedInterval(minX, maxX);
        double[] y = normalizeErodedInterval(minY, maxY);
        double[] z = normalizeErodedInterval(minZ, maxZ);
        if (x == null || y == null || z == null) {
            return null;
        }
        return new AABB(x[0], y[0], z[0], x[1], y[1], z[1]);
    }

    /** Zero-width intervals are valid when the Beam exactly matches the Post. */
    private static double[] normalizeErodedInterval(double minimum, double maximum) {
        if (minimum <= maximum) {
            return new double[]{minimum, maximum};
        }
        if (minimum - maximum > StrutGeometry.EPSILON) {
            return null;
        }
        double centre = (minimum + maximum) * 0.5;
        return new double[]{centre, centre};
    }

    private static RayInterval intersectBounds(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            AABB bounds
    ) {
        double entryDistance = Double.NEGATIVE_INFINITY;
        double exitDistance = Double.POSITIVE_INFINITY;

        for (Direction.Axis axis : Direction.Axis.values()) {
            double origin = coordinate(rayOrigin, axis);
            double direction = coordinate(rayDirection, axis);
            double minimum = minimum(bounds, axis);
            double maximum = maximum(bounds, axis);
            if (Math.abs(direction) <= StrutGeometry.EPSILON) {
                    if (origin < minimum - StrutGeometry.EPSILON
                        || origin > maximum + StrutGeometry.EPSILON) {
                    return null;
                }
                continue;
            }

            double first = (minimum - origin) / direction;
            double second = (maximum - origin) / direction;
            entryDistance = Math.max(entryDistance, Math.min(first, second));
            exitDistance = Math.min(exitDistance, Math.max(first, second));
            if (entryDistance > exitDistance + StrutGeometry.EPSILON) {
                return null;
            }
        }

        if (exitDistance < 0.0 || entryDistance < 0.0) {
            return null;
        }
        if (entryDistance > exitDistance) {
            double centre = (entryDistance + exitDistance) * 0.5;
            return new RayInterval(centre, centre);
        }
        return new RayInterval(entryDistance, exitDistance);
    }

    private static double coordinate(Vec3 point, Direction.Axis axis) {
        return switch (axis) {
            case X -> point.x;
            case Y -> point.y;
            case Z -> point.z;
        };
    }

    private static double minimum(AABB bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.minX;
            case Y -> bounds.minY;
            case Z -> bounds.minZ;
        };
    }

    private static double maximum(AABB bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.maxX;
            case Y -> bounds.maxY;
            case Z -> bounds.maxZ;
        };
    }

    private static double requiredEndpointExtension(
            Vec3 endpoint,
            Vec3 planePoint,
            Vec3 planeNormal,
            Vec3 directionFromEndpoint,
            Vec3 canonicalDirection,
            double halfWidth,
            double halfHeight
    ) {
        CrossSectionAxes crossSection = crossSectionAxes(canonicalDirection);
        Vec3 crossAxisX = crossSection.crossAxisX();
        Vec3 crossAxisY = crossSection.crossAxisY();

        double crossSectionProjection = Math.abs(planeNormal.dot(crossAxisX)) * halfWidth
                + Math.abs(planeNormal.dot(crossAxisY)) * halfHeight;
        double clippingDepth = endpoint.subtract(planePoint).dot(planeNormal);
        double directionProjection = directionFromEndpoint.dot(planeNormal);
        if (directionProjection <= StrutGeometry.EPSILON) {
            return 0.5;
        }

        double required = (clippingDepth + crossSectionProjection + StrutGeometry.EPSILON)
                / directionProjection;
        return Math.max(0.5, required);
    }

    private static CrossSectionAxes crossSectionAxes(Vec3 direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        Vec3 crossAxisX = horizontal > StrutGeometry.EPSILON
                ? new Vec3(direction.z / horizontal, 0.0, -direction.x / horizontal)
                : new Vec3(1.0, 0.0, 0.0);
        return new CrossSectionAxes(crossAxisX, direction.cross(crossAxisX).normalize());
    }

    public static void invalidateMeshes() {
        SEGMENT_MESHES.clear();
        DIRECT_SEGMENT_MESHES.clear();
    }

    private static List<BakedQuad> bakeConnection(
            Vec3 meshOrigin,
            Vec3 direction,
            double renderLength,
            float textureStartOffset,
            Vec3 clippingPlanePoint,
            Vec3 clippingPlaneNormal,
            StrutModelType modelType
    ) {
        if (renderLength <= StrutGeometry.EPSILON) {
            return List.of();
        }

        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yRotation = horizontal == 0 ? 0 : (float) Math.atan2(direction.x, direction.z);
        float xRotation = (float) Math.atan2(direction.y, horizontal);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(meshOrigin.x, meshOrigin.y, meshOrigin.z);
        poseStack.mulPose(new Quaternionf().rotationY(yRotation));
        poseStack.mulPose(new Quaternionf().rotationX(-xRotation));
        applyRenderCrossSectionScale(poseStack, modelType);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        Matrix3f normalMatrix = new Matrix3f(poseStack.last().normal());
        Vector3f planePoint = toVector(clippingPlanePoint);
        Vector3f planeNormal = toVector(clippingPlaneNormal);
        if (planeNormal.lengthSquared() > StrutGeometry.EPSILON) {
            planeNormal.normalize();
        }

        List<BakedQuad> result = new ArrayList<>();
        CapAccumulator caps = new CapAccumulator(modelType.capTexture());
        for (StrutMeshQuad quad : getSegmentMesh(modelType).forLength(
                (float) renderLength,
                textureStartOffset
        )) {
            quad.transformAndEmit(pose, normalMatrix, planePoint, planeNormal, caps, result);
        }
        caps.emitCaps(planePoint, planeNormal, result);
        return result;
    }

    /**
     * Post-only deterministic termination adapted from the transform portion of
     * Struts 1.2.1's StrutMeshQuad (MIT). The mesh is cropped in local Z first;
     * no StrutPlaneClipper or CapAccumulator is involved.
     */
    private static List<BakedQuad> bakePostConnection(
            Vec3 meshOrigin,
            Vec3 direction,
            double renderLength,
            float textureStartOffset,
            Vec3 capCenter,
            Vec3 planeNormal,
            StrutModelType modelType
    ) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yRotation = horizontal == 0.0 ? 0.0F : (float) Math.atan2(direction.x, direction.z);
        float xRotation = (float) Math.atan2(direction.y, horizontal);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(meshOrigin.x, meshOrigin.y, meshOrigin.z);
        poseStack.mulPose(new Quaternionf().rotationY(yRotation));
        poseStack.mulPose(new Quaternionf().rotationX(-xRotation));
        applyRenderCrossSectionScale(poseStack, modelType);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        Matrix3f normalMatrix = new Matrix3f(poseStack.last().normal());
        List<BakedQuad> result = new ArrayList<>();
        for (DirectMeshQuad quad : getDirectSegmentMesh(modelType).forLength(
                (float) renderLength,
                textureStartOffset
        )) {
            quad.transformAndEmit(pose, normalMatrix, result);
        }
        emitPostCap(capCenter, planeNormal, direction, modelType, result);
        return result;
    }

    private static void applyRenderCrossSectionScale(PoseStack poseStack, StrutModelType modelType) {
        poseStack.scale(
                (float) (RENDER_BEAM_WIDTH_PIXELS / modelType.shapeSizeXPixels()),
                (float) (RENDER_BEAM_WIDTH_PIXELS / modelType.shapeSizeYPixels()),
                1.0F
        );
    }

    private static void emitPostCap(
            Vec3 capCenter,
            Vec3 planeNormal,
            Vec3 canonicalDirection,
            StrutModelType modelType,
            List<BakedQuad> result
    ) {
        CrossSectionAxes axes = crossSectionAxes(canonicalDirection);
        Vec3 x = axes.crossAxisX().scale(RENDER_BEAM_HALF_EXTENT);
        Vec3 y = axes.crossAxisY().scale(RENDER_BEAM_HALF_EXTENT);
        Vec3[] corners = {
                capCenter.subtract(x).subtract(y),
                capCenter.add(x).subtract(y),
                capCenter.add(x).add(y),
                capCenter.subtract(x).add(y)
        };

        Vec3 faceNormal = planeNormal.normalize().reverse();
        int[] order = faceNormal.dot(canonicalDirection) >= 0.0
                ? new int[]{0, 1, 2, 3}
                : new int[]{0, 3, 2, 1};
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(modelType.capTexture());
        float[] u = {sprite.getU0(), sprite.getU1(), sprite.getU1(), sprite.getU0()};
        float[] v = {sprite.getV1(), sprite.getV1(), sprite.getV0(), sprite.getV0()};
        List<StrutVertex> vertices = new ArrayList<>(4);
        for (int index : order) {
            vertices.add(new StrutVertex(
                    toVector(corners[index]),
                    toVector(faceNormal),
                    u[index],
                    v[index],
                    StrutGeometry.DEFAULT_COLOR,
                    0
            ));
        }
        StrutGeometry.emitPolygon(
                vertices,
                sprite,
                Direction.getNearest(faceNormal.x, faceNormal.y, faceNormal.z),
                -1,
                true,
                result
        );
    }

    private static List<StrutVertex> unpack(BakedQuad quad) {
        int[] data = quad.getVertices();
        List<StrutVertex> vertices = new ArrayList<>(4);
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            Vec3 position = BakedQuadHelper.getXYZ(data, vertexIndex);
            Vec3 normal = BakedQuadHelper.getNormalXYZ(data, vertexIndex);
            int baseIndex = BakedQuadHelper.VERTEX_STRIDE * vertexIndex;
            int color = data.length > baseIndex + BakedQuadHelper.COLOR_OFFSET
                    ? data[baseIndex + BakedQuadHelper.COLOR_OFFSET]
                    : StrutGeometry.DEFAULT_COLOR;
            int light = data.length > baseIndex + BakedQuadHelper.LIGHT_OFFSET
                    ? data[baseIndex + BakedQuadHelper.LIGHT_OFFSET]
                    : StrutGeometry.DEFAULT_COLOR;
            vertices.add(new StrutVertex(
                    toVector(position),
                    toVector(normal),
                    BakedQuadHelper.getU(data, vertexIndex),
                    BakedQuadHelper.getV(data, vertexIndex),
                    color,
                    light
            ));
        }
        return vertices;
    }

    private static StrutSegmentMesh getSegmentMesh(StrutModelType modelType) {
        return SEGMENT_MESHES.computeIfAbsent(modelType, type -> {
            ModelResourceLocation location = ModelResourceLocation.standalone(type.segmentModelLocation());
            BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
            List<BakedQuad> quads = model.getQuads(
                    null,
                    null,
                    RandomSource.create(),
                    ModelData.EMPTY,
                    null
            );
            // Segment end faces would be duplicated at every tile and at the
            // reciprocal midpoint. Supporting-surface caps are generated by
            // CapAccumulator after clipping, so the dynamic mesh only needs the
            // four longitudinal faces.
            List<BakedQuad> longitudinalQuads = quads.stream()
                    .filter(quad -> quad.getDirection() != Direction.NORTH
                            && quad.getDirection() != Direction.SOUTH)
                    .toList();
            return new StrutSegmentMesh(longitudinalQuads);
        });
    }

    private static DirectSegmentMesh getDirectSegmentMesh(StrutModelType modelType) {
        return DIRECT_SEGMENT_MESHES.computeIfAbsent(modelType, type -> {
            ModelResourceLocation location = ModelResourceLocation.standalone(type.segmentModelLocation());
            BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
            List<BakedQuad> quads = model.getQuads(
                    null,
                    null,
                    RandomSource.create(),
                    ModelData.EMPTY,
                    null
            );
            List<BakedQuad> longitudinalQuads = quads.stream()
                    .filter(quad -> quad.getDirection() != Direction.NORTH
                            && quad.getDirection() != Direction.SOUTH)
                    .toList();
            return new DirectSegmentMesh(longitudinalQuads);
        });
    }

    /** Minimal local-Z tiler/cropper adapted from Struts 1.2.1 (MIT). */
    private static final class DirectSegmentMesh {

        private final List<DirectMeshQuad> baseQuads;

        private DirectSegmentMesh(List<BakedQuad> quads) {
            baseQuads = quads.stream().map(DirectMeshQuad::from).toList();
        }

        private List<DirectMeshQuad> forLength(float length, float startOffset) {
            if (length <= StrutGeometry.EPSILON) {
                return List.of();
            }

            float wrappedOffset = startOffset - Mth.floor(startOffset);
            if (wrappedOffset <= StrutGeometry.EPSILON) {
                return forLength(length);
            }

            List<DirectMeshQuad> expanded = forLength(length + wrappedOffset);
            List<DirectMeshQuad> result = new ArrayList<>(expanded.size());
            for (DirectMeshQuad quad : expanded) {
                DirectMeshQuad clipped = quad.clipMinZ(wrappedOffset);
                if (clipped != null) {
                    result.add(clipped.translate(0.0F, 0.0F, -wrappedOffset));
                }
            }
            return result;
        }

        private List<DirectMeshQuad> forLength(float length) {
            int fullSegments = Mth.floor(length + StrutGeometry.EPSILON);
            float partial = length - fullSegments;
            List<DirectMeshQuad> result = new ArrayList<>(baseQuads.size() * (fullSegments + 1));

            for (int segment = 0; segment < fullSegments; segment++) {
                for (DirectMeshQuad quad : baseQuads) {
                    result.add(quad.translate(0.0F, 0.0F, segment));
                }
            }
            if (partial > StrutGeometry.EPSILON) {
                for (DirectMeshQuad quad : baseQuads) {
                    DirectMeshQuad clipped = quad.clipZ(partial);
                    if (clipped != null) {
                        result.add(clipped.translate(0.0F, 0.0F, fullSegments));
                    }
                }
            }
            if (result.isEmpty()) {
                for (DirectMeshQuad quad : baseQuads) {
                    DirectMeshQuad fallback = quad.clipZ(Math.max(partial, StrutGeometry.EPSILON));
                    if (fallback != null) {
                        result.add(fallback);
                    }
                }
            }
            return result;
        }
    }

    /** Minimal direct-transform quad adapted from Struts 1.2.1 (MIT). */
    private record DirectMeshQuad(
            List<StrutVertex> vertices,
            TextureAtlasSprite sprite,
            Direction nominalFace,
            int tintIndex,
            boolean shade
    ) {
        private static DirectMeshQuad from(BakedQuad quad) {
            return new DirectMeshQuad(
                    unpack(quad),
                    quad.getSprite(),
                    quad.getDirection(),
                    quad.getTintIndex(),
                    quad.isShade()
            );
        }

        private DirectMeshQuad translate(float x, float y, float z) {
            List<StrutVertex> translated = new ArrayList<>(vertices.size());
            for (StrutVertex vertex : vertices) {
                translated.add(new StrutVertex(
                        new Vector3f(vertex.position()).add(x, y, z),
                        new Vector3f(vertex.normal()),
                        vertex.u(),
                        vertex.v(),
                        vertex.color(),
                        vertex.light()
                ));
            }
            return new DirectMeshQuad(translated, sprite, nominalFace, tintIndex, shade);
        }

        private DirectMeshQuad clipZ(float maximumZ) {
            float minimumOriginalZ = Float.POSITIVE_INFINITY;
            float maximumOriginalZ = Float.NEGATIVE_INFINITY;
            for (StrutVertex vertex : vertices) {
                minimumOriginalZ = Math.min(minimumOriginalZ, vertex.position().z);
                maximumOriginalZ = Math.max(maximumOriginalZ, vertex.position().z);
            }
            if (maximumZ >= maximumOriginalZ - StrutGeometry.EPSILON) {
                return this;
            }
            if (maximumZ <= minimumOriginalZ + StrutGeometry.EPSILON) {
                return translate(0.0F, 0.0F, maximumZ - maximumOriginalZ);
            }
            return clipLocalZ(maximumZ, true);
        }

        private DirectMeshQuad clipMinZ(float minimumZ) {
            float minimumOriginalZ = Float.POSITIVE_INFINITY;
            float maximumOriginalZ = Float.NEGATIVE_INFINITY;
            for (StrutVertex vertex : vertices) {
                minimumOriginalZ = Math.min(minimumOriginalZ, vertex.position().z);
                maximumOriginalZ = Math.max(maximumOriginalZ, vertex.position().z);
            }
            if (minimumZ <= minimumOriginalZ + StrutGeometry.EPSILON) {
                return this;
            }
            if (minimumZ >= maximumOriginalZ - StrutGeometry.EPSILON) {
                return null;
            }
            return clipLocalZ(minimumZ, false);
        }

        private DirectMeshQuad clipLocalZ(float boundary, boolean retainBelow) {
            List<StrutVertex> clipped = new ArrayList<>(vertices.size() + 2);
            for (int index = 0; index < vertices.size(); index++) {
                StrutVertex current = vertices.get(index);
                StrutVertex next = vertices.get((index + 1) % vertices.size());
                boolean currentInside = retainBelow
                        ? current.position().z <= boundary + StrutGeometry.EPSILON
                        : current.position().z >= boundary - StrutGeometry.EPSILON;
                boolean nextInside = retainBelow
                        ? next.position().z <= boundary + StrutGeometry.EPSILON
                        : next.position().z >= boundary - StrutGeometry.EPSILON;

                if (currentInside && nextInside) {
                    clipped.add(next);
                } else if (currentInside) {
                    clipped.add(interpolateAtZ(current, next, boundary));
                } else if (nextInside) {
                    clipped.add(interpolateAtZ(current, next, boundary));
                    clipped.add(next);
                }
            }
            if (clipped.size() < 3) {
                return null;
            }
            return new DirectMeshQuad(clipped, sprite, nominalFace, tintIndex, shade);
        }

        private static StrutVertex interpolateAtZ(StrutVertex start, StrutVertex end, float z) {
            float delta = end.position().z - start.position().z;
            float interpolation = Math.abs(delta) < StrutGeometry.EPSILON
                    ? 0.0F
                    : (z - start.position().z) / delta;
            return StrutGeometry.interpolate(start, end, interpolation);
        }

        private void transformAndEmit(Matrix4f pose, Matrix3f normalMatrix, List<BakedQuad> result) {
            List<StrutVertex> transformed = new ArrayList<>(vertices.size());
            for (StrutVertex vertex : vertices) {
                Vector3f position = new Vector3f(vertex.position());
                pose.transformPosition(position);
                Vector3f normal = new Vector3f(vertex.normal());
                normalMatrix.transform(normal);
                if (normal.lengthSquared() > StrutGeometry.EPSILON) {
                    normal.normalize();
                }
                transformed.add(new StrutVertex(
                        position,
                        normal,
                        vertex.u(),
                        vertex.v(),
                        vertex.color(),
                        vertex.light()
                ));
            }
            StrutGeometry.emitPolygon(transformed, sprite, nominalFace, tintIndex, shade, result);
        }
    }

    private static Vector3f toVector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    private record EndpointPlane(Vec3 point, Vec3 normal) {
    }

    private record PlaneSelection(EndpointPlane plane, PlanePath path) {
    }

    private record RayInterval(double entryDistance, double exitDistance) {
    }

    private record CrossSectionAxes(Vec3 crossAxisX, Vec3 crossAxisY) {
    }

    private enum PlanePath {
        POST_PERPENDICULAR,
        NORMAL_SUPPORT
    }

}
