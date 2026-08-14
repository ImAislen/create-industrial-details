package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamConnectionEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamEndpoints;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.cap.CapAccumulator;
import com.cake.struts.content.connection.GirderConnectionNode;
import com.cake.struts.content.geometry.StrutGeometry;
import com.cake.struts.content.mesh.StrutMeshQuad;
import com.cake.struts.content.mesh.StrutSegmentMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
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
 * 1.2.1's StrutModelBuilder/StrutModelManipulator (MIT). Geometry primitives,
 * clipping, caps, and segment meshes continue to come directly from Struts.
 */
public final class WoodenBeamModelBuilder {

    private static final Map<StrutModelType, StrutSegmentMesh> SEGMENT_MESHES = new HashMap<>();

    private WoodenBeamModelBuilder() {
    }

    public static List<BakedQuad> buildConnectionQuads(WoodenBeamBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 blockOrigin = Vec3.atLowerCornerOf(pos);
        StrutModelType modelType = blockEntity.getModelType();
        List<BakedQuad> quads = new ArrayList<>();

        for (GirderConnectionNode connection : blockEntity.getConnectionsCopy()) {
            BlockPos peerPos = connection.absoluteFrom(pos);
            WoodenBeamConnectionEndpoints endpoints = blockEntity.getEndpoints(connection);
            Vec3 localEndpoint = WoodenBeamEndpoints.attachment(
                    pos,
                    endpoints.localFace(),
                    endpoints.localSnap()
            );
            Vec3 peerEndpoint = WoodenBeamEndpoints.attachment(
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
            Vec3 localPlanePoint = WoodenBeamEndpoints.clippingPlanePoint(
                    pos,
                    endpoints.localFace(),
                    endpoints.localSnap()
            );
            Vec3 localPlaneNormal = Vec3.atLowerCornerOf(endpoints.localFace().getNormal());
            Vec3 directionFromLocalEndpoint = localIsCanonicalStart ? direction : direction.reverse();
            double endpointExtension = requiredEndpointExtension(
                    localEndpoint,
                    localPlanePoint,
                    localPlaneNormal,
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
                    localPlanePoint.subtract(blockOrigin),
                    localPlaneNormal,
                    modelType
            ));
        }
        return List.copyOf(quads);
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
        double horizontal = Math.sqrt(
                canonicalDirection.x * canonicalDirection.x
                        + canonicalDirection.z * canonicalDirection.z
        );
        Vec3 crossAxisX = horizontal > StrutGeometry.EPSILON
                ? new Vec3(canonicalDirection.z / horizontal, 0.0, -canonicalDirection.x / horizontal)
                : new Vec3(1.0, 0.0, 0.0);
        Vec3 crossAxisY = canonicalDirection.cross(crossAxisX).normalize();

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

    public static void invalidateMeshes() {
        SEGMENT_MESHES.clear();
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

    private static Vector3f toVector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }
}
