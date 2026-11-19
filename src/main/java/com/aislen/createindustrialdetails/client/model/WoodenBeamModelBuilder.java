package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamGeometry;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.cap.CapAccumulator;
import com.cake.struts.content.geometry.StrutGeometry;
import com.cake.struts.content.geometry.StrutVertex;
import com.cake.struts.content.mesh.StrutMeshQuad;
import com.cake.struts.content.mesh.StrutPlaneClipper;
import com.cake.struts.content.mesh.StrutSegmentMesh;
import com.cake.struts.content.shape.SurfaceClippingHelper.SurfacePlane;
import com.cake.struts.internal.util.BakedQuadHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cake.struts.content.shape.SurfaceClippingHelper.signedDistance;

public final class WoodenBeamModelBuilder {
    private static final Map<StrutModelType, StrutSegmentMesh> SEGMENT_MESHES = new HashMap<>();
    private static final float CLIP_NORMAL_SCALE = 256;

    private WoodenBeamModelBuilder() {
    }

    public static List<BakedQuad> buildConnectionQuads(WoodenBeamBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 blockOrigin = Vec3.atLowerCornerOf(pos);
        StrutModelType type = blockEntity.getModelType();
        List<BakedQuad> quads = new ArrayList<>();

        for (var connection : blockEntity.getConnectionsCopy()) {
            BlockPos peer = connection.absoluteFrom(pos);
            boolean first = pos.compareTo(peer) < 0;
            var endpoints = blockEntity.getEndpoints(connection);
            var beam = first
                    ? new WoodenBeamGeometry(blockEntity.getLevel(), pos, peer, endpoints)
                    : new WoodenBeamGeometry(blockEntity.getLevel(), peer, pos, endpoints.reversed());
            if (beam.faces().isEmpty()) {
                continue;
            }
            TextureAtlasSprite capSprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(type.capTexture());
            bakeHalf(beam, first, blockOrigin, type, getSegmentMesh(type), capSprite, quads);
        }
        return List.copyOf(quads);
    }

    static void bakeHalf(
            WoodenBeamGeometry beam, boolean first, Vec3 blockOrigin, StrutModelType type,
            StrutSegmentMesh mesh, TextureAtlasSprite capSprite, List<BakedQuad> quads
    ) {
        Vec3 direction = beam.direction();
        double middle = beam.length() * 0.5;
        for (var section : beam.sections()) {
            double start = first ? section.start() : Math.max(section.start(), middle);
            double end = first ? Math.min(section.end(), middle) : section.end();
            bakeSides(beam, start, end, section.planes(), blockOrigin, type, mesh, quads);
        }

        // Both anchors render the outer faces of the same solid.
        Vec3 midpoint = beam.origin().add(direction.scale(middle));
        SurfacePlane split = new SurfacePlane(midpoint, first ? direction.reverse() : direction);
        for (var face : beam.faces()) {
            if (face.end()) {
                emitCap(beam, face.normal(), WoodenBeamGeometry.clipPolygon(face.vertices(), split),
                        blockOrigin, capSprite, quads);
            }
        }
    }

    private static void bakeSides(
            WoodenBeamGeometry beam, double start, double end, List<SurfacePlane> planes, Vec3 blockOrigin,
            StrutModelType type, StrutSegmentMesh mesh, List<BakedQuad> quads
    ) {
        if (end - start <= StrutGeometry.EPSILON) {
            return;
        }

        Vec3 direction = beam.direction();
        Vec3 startPoint = beam.origin().add(direction.scale(start));
        Vec3 endPoint = beam.origin().add(direction.scale(end));
        Vec3 meshOrigin = startPoint.add(direction.scale(0.5)).subtract(blockOrigin);
        double horizontal = Math.hypot(direction.x, direction.z);
        PoseStack pose = new PoseStack();
        pose.translate(meshOrigin.x, meshOrigin.y, meshOrigin.z);
        float yaw = horizontal == 0 ? 0 : (float) Math.atan2(direction.x, direction.z);
        pose.mulPose(new Quaternionf().rotationY(yaw));
        pose.mulPose(new Quaternionf().rotationX(-(float) Math.atan2(direction.y, horizontal)));
        float widthPixels = (float) (WoodenBeamGeometry.HALF_WIDTH * 32);
        pose.scale(widthPixels / type.shapeSizeXPixels(), widthPixels / type.shapeSizeYPixels(), 1);
        pose.translate(-0.5F, -0.5F, -0.5F);

        var cuts = planes.stream()
                .filter(plane -> reachesPlane(beam, startPoint, endPoint, plane))
                .map(plane -> new MeshPlane(toVector(plane.point().subtract(blockOrigin)), clipNormal(plane)))
                .toList();
        MeshPlane firstCut = cuts.isEmpty() ? new MeshPlane(new Vector3f(), new Vector3f()) : cuts.getFirst();
        List<MeshPlane> extraCuts = cuts.size() > 1 ? cuts.subList(1, cuts.size()) : List.of();
        CapAccumulator intersections = new CapAccumulator(type.capTexture());
        List<BakedQuad> sides = extraCuts.isEmpty() ? quads : new ArrayList<>();
        for (StrutMeshQuad side : mesh.forLength((float) (end - start), (float) (start + 0.5))) {
            side.transformAndEmit(pose.last().pose(), pose.last().normal(), firstCut.point(), firstCut.normal(),
                    intersections, sides);
        }
        if (!extraCuts.isEmpty()) {
            for (BakedQuad quad : sides) {
                var polygon = unpack(quad);
                for (MeshPlane cut : extraCuts) {
                    polygon = StrutPlaneClipper.clip(polygon, cut.point(), cut.normal()).polygon();
                    if (polygon.size() < 3) {
                        break;
                    }
                }
                if (polygon.size() >= 3) {
                    StrutGeometry.emitPolygon(polygon, quad.getSprite(), quad.getDirection(),
                            quad.getTintIndex(), quad.isShade(), quads);
                }
            }
        }
    }

    private static boolean reachesPlane(WoodenBeamGeometry beam, Vec3 start, Vec3 end, SurfacePlane plane) {
        double distance = Math.min(signedDistance(start, plane), signedDistance(end, plane));
        return distance - beam.crossExtent(plane.normal()) < -StrutGeometry.EPSILON / CLIP_NORMAL_SCALE;
    }

    private static Vector3f clipNormal(SurfacePlane plane) {
        // Struts uses a fixed dot-product tolerance. A scaled normal tightens
        // that tolerance without moving the plane or its intersections.
        return toVector(plane.normal()).mul(CLIP_NORMAL_SCALE);
    }

    private static void emitCap(
            WoodenBeamGeometry beam, Vec3 normal, List<Vec3> polygon,
            Vec3 blockOrigin, TextureAtlasSprite sprite, List<BakedQuad> quads
    ) {
        if (polygon.size() < 3) {
            return;
        }
        List<StrutVertex> vertices = new ArrayList<>(polygon.size());
        double width = WoodenBeamGeometry.HALF_WIDTH * 2;
        for (Vec3 point : polygon) {
            Vec3 offset = point.subtract(beam.origin());
            float u = Mth.clamp((float) (0.5 + offset.dot(beam.crossX()) / width), 0, 1);
            float v = Mth.clamp((float) (0.5 - offset.dot(beam.crossY()) / width), 0, 1);
            vertices.add(new StrutVertex(toVector(point.subtract(blockOrigin)), toVector(normal),
                    Mth.lerp(u, sprite.getU0(), sprite.getU1()), Mth.lerp(v, sprite.getV0(), sprite.getV1()),
                    StrutGeometry.DEFAULT_COLOR, 0));
        }
        StrutGeometry.emitPolygon(vertices, sprite, Direction.getNearest(normal.x, normal.y, normal.z),
                -1, true, quads);
    }

    public static void invalidateMeshes() {
        SEGMENT_MESHES.clear();
    }

    private static StrutSegmentMesh getSegmentMesh(StrutModelType type) {
        return SEGMENT_MESHES.computeIfAbsent(type, key -> {
            ModelResourceLocation location = ModelResourceLocation.standalone(key.segmentModelLocation());
            BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
            return new StrutSegmentMesh(model.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null)
                    .stream()
                    .filter(quad -> quad.getDirection().getAxis() != Direction.Axis.Z)
                    .toList());
        });
    }

    private static List<StrutVertex> unpack(BakedQuad quad) {
        int[] data = quad.getVertices();
        List<StrutVertex> vertices = new ArrayList<>(4);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * BakedQuadHelper.VERTEX_STRIDE;
            vertices.add(new StrutVertex(
                    toVector(BakedQuadHelper.getXYZ(data, vertex)),
                    toVector(BakedQuadHelper.getNormalXYZ(data, vertex)),
                    BakedQuadHelper.getU(data, vertex),
                    BakedQuadHelper.getV(data, vertex),
                    data[offset + BakedQuadHelper.COLOR_OFFSET],
                    data[offset + BakedQuadHelper.LIGHT_OFFSET]
            ));
        }
        return vertices;
    }

    private static Vector3f toVector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    private record MeshPlane(Vector3f point, Vector3f normal) {
    }
}
