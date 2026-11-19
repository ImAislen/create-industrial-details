package com.aislen.createindustrialdetails.client.model;

import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CopycatDiagonalByteModelChecks {
    public static void main(String[] args) {
        IndividualCableModelChecks.run();
        for (Direction face : Direction.values())
            for (boolean atlas : new boolean[]{false, true})
                for (boolean rotatedUV : new boolean[]{false, true})
                    checkFace(face, atlas ? .375f : 0, atlas ? .25f : 0, atlas ? .125f : 1, rotatedUV);
        checkFallback();
        checkTrenchInteriorLighting();
        System.out.println("Copycat Diagonal Byte: six-face coverage, original texture borders, CT atlas UVs, normals, metadata and empty-layer fallback checks passed.");
    }

    private static void checkFace(Direction face, float originU, float originV, float span, boolean rotatedUV) {
        BakedQuad source = sourceQuad(face, originU, originV, span, rotatedUV);
        int[] original = source.getVertices().clone();
        List<BakedQuad> pieces = CopycatDiagonalByteModel.cropAndRotate(List.of(source));
        require(pieces.size() == 4, "Each face uses four exterior corner patches");
        require(Arrays.equals(original, source.getVertices()), "Source model must remain immutable");
        Set<Integer> uvCorners = new HashSet<>();
        Set<Integer> geometryCorners = new HashSet<>();
        for (BakedQuad result : pieces) {
            require(result.getSprite() == source.getSprite() && result.getTintIndex() == source.getTintIndex()
                    && result.isShade() == source.isShade() && result.hasAmbientOcclusion() == source.hasAmbientOcclusion(),
                    "Sprite, tint, shade and AO metadata preserved");
            Vec3[] vertices = new Vec3[4];
            double minX = 1, minY = 1, minZ = 1;
            for (int i = 0; i < 4; i++) {
                vertices[i] = unrotate(BakedQuadHelper.getXYZ(result.getVertices(), i));
                minX = Math.min(minX, vertices[i].x);
                minY = Math.min(minY, vertices[i].y);
                minZ = Math.min(minZ, vertices[i].z);
            }
            int cornerX = minX >= .5 - .00001 ? 1 : 0;
            int cornerY = minY >= .25 - .00001 ? 1 : 0;
            int cornerZ = minZ >= .5 - .00001 ? 1 : 0;
            require(geometryCorners.add(cornerX | cornerY << 1 | cornerZ << 2), "Each spatial corner is used once without overlapping patches");
            Vec3 offset = new Vec3(.25 - cornerX * .5, -cornerY * .5, .25 - cornerZ * .5);
            double minU = 1, minV = 1, maxU = 0, maxV = 0;
            double area = 0;
            Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 rotatedNormal = new Vec3((normal.x - normal.z) / Math.sqrt(2), normal.y, (normal.x + normal.z) / Math.sqrt(2));
            for (int i = 0; i < 4; i++) {
                Vec3 vertex = vertices[i];
                require(vertex.x >= .25 - .00001 && vertex.x <= .75 + .00001
                        && vertex.z >= .25 - .00001 && vertex.z <= .75 + .00001
                        && vertex.y >= -.00001 && vertex.y <= .5 + .00001, "All vertices retain byte bounds and rotation");
                double faceCoordinate = face.getAxis().choose(vertex.x, vertex.y, vertex.z);
                double expected = face.getAxis() == Direction.Axis.Y ? (face == Direction.UP ? .5 : 0)
                        : (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? .75 : .25);
                require(Math.abs(faceCoordinate - expected) < .00001, "Only exterior faces are emitted");
                int packedNormal = result.getVertices()[i * BakedQuadHelper.VERTEX_STRIDE + BakedQuadHelper.NORMAL_OFFSET];
                require(Math.abs((byte) packedNormal / 127.0 - rotatedNormal.x) < .01
                        && Math.abs((byte) (packedNormal >> 8) / 127.0 - rotatedNormal.y) < .01
                        && Math.abs((byte) (packedNormal >> 16) / 127.0 - rotatedNormal.z) < .01, "Packed normals rotate with geometry");
                double u = (BakedQuadHelper.getU(result.getVertices(), i) - originU) / span;
                double v = (BakedQuadHelper.getV(result.getVertices(), i) - originV) / span;
                minU = Math.min(minU, u); maxU = Math.max(maxU, u);
                minV = Math.min(minV, v); maxV = Math.max(maxV, v);
                Vec3 sourceVertex = vertex.subtract(offset);
                Vec3 uAxis = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 vAxis = normal.cross(uAxis);
                Vec3 center = new Vec3(.5, .5, .5).add(normal.scale(.5));
                double a = sourceVertex.subtract(center).dot(uAxis) + .5;
                double b = sourceVertex.subtract(center).dot(vAxis) + .5;
                require(Math.abs(u - (rotatedUV ? b : a)) < .00001
                        && Math.abs(v - (rotatedUV ? 1 - a : b)) < .00001,
                        "Original UV orientation and texel scale: face=" + face + ", span=" + span + ", rotatedUV=" + rotatedUV
                                + ", vertex=" + i + ", actual=" + u + "," + v
                                + ", expected=" + (rotatedUV ? b : a) + "," + (rotatedUV ? 1 - a : b));
                require(result.getVertices()[i * BakedQuadHelper.VERTEX_STRIDE + BakedQuadHelper.COLOR_OFFSET] == 0xFFFFFFFF
                        && result.getVertices()[i * BakedQuadHelper.VERTEX_STRIDE + BakedQuadHelper.LIGHT_OFFSET] == 0x00A000A0,
                        "Per-vertex color and light metadata preserved");
                area += vertex.cross(vertices[(i + 1) % 4]).dot(normal) / 2;
            }
            require(Math.abs(Math.abs(area) - .0625) < .00001, "Each corner is a complete 4x4-pixel patch");
            require(Math.abs(maxU - minU - .25) < .00001 && Math.abs(maxV - minV - .25) < .00001,
                    "Each patch retains original texel density without stretching");
            require((Math.abs(minU) < .00001 || Math.abs(minU - .75) < .00001)
                    && (Math.abs(minV) < .00001 || Math.abs(minV - .75) < .00001), "Original texture borders are retained instead of center-only crops");
            uvCorners.add((minU > .5 ? 1 : 0) | (minV > .5 ? 2 : 0));
        }
        require(uvCorners.size() == 4 && geometryCorners.size() == 4, "All four texture corners completely cover the face");
    }

    private static void checkTrenchInteriorLighting() {
        for (Direction normal : Direction.Plane.HORIZONTAL) {
            BakedQuad source = sourceQuad(normal, .25F, .25F, .25F, false);
            Direction.Axis across = normal.getAxis();
            Direction.Axis along = across == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            boolean positive = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            int[] vertices = source.getVertices();
            for (int plane : new int[]{0, 1, 3, 13, 15, 16}) {
                for (int vertex = 0; vertex < 4; vertex++)
                    vertices[vertex * BakedQuadHelper.VERTEX_STRIDE + across.ordinal()] =
                            Float.floatToIntBits(plane / 16F);
                boolean interior = positive ? plane == 1 || plane == 3 : plane == 13 || plane == 15;
                require(ServiceTrenchCasingModel.isInteriorWall(source, along) == interior
                                && !ServiceTrenchCasingModel.isInteriorWall(source, across),
                        "Only inward wall and rail faces are selected on either trench axis");
            }
            int light = 0x00600020;
            BakedQuad lit = ServiceTrenchCasingModel.lightInteriorQuad(source, light);
            require(!lit.hasAmbientOcclusion() && lit.isShade() == source.isShade()
                            && lit.getSprite() == source.getSprite() && source.hasAmbientOcclusion(),
                    "Trench interior keeps directional shading and its sprite while applying flat lighting");
            for (int i = 0; i < vertices.length; i++) {
                int offset = i % BakedQuadHelper.VERTEX_STRIDE;
                require(lit.getVertices()[i] == (offset == BakedQuadHelper.LIGHT_OFFSET ? light : vertices[i]),
                        "Interior lighting preserves geometry, CT UVs and other vertex data");
            }
        }
        for (Direction normal : new Direction[]{Direction.UP, Direction.DOWN}) {
            BakedQuad source = sourceQuad(normal, .25F, .25F, .25F, false);
            for (float height : new float[]{0, 1 / 16f, 15 / 16f, 1}) {
                for (int vertex = 0; vertex < 4; vertex++)
                    source.getVertices()[vertex * BakedQuadHelper.VERTEX_STRIDE + Direction.Axis.Y.ordinal()] =
                            Float.floatToIntBits(height);
                require(ServiceTrenchCasingModel.isInteriorFloor(source) == (normal == Direction.UP && height == 1 / 16f),
                        "Only the floor's upper face bypasses smooth lighting");
            }
            BakedQuad lit = ServiceTrenchCasingModel.lightInteriorQuad(source, 0);
            require(!lit.hasAmbientOcclusion() && lit.isShade() == source.isShade()
                            && Arrays.equals(lit.getVertices(), source.getVertices()),
                    "A flat-lit floor keeps the world light sample and connected UVs");
        }
        for (String texture : List.of("block/pipes", "block/pipes_connected", "block/glass_fluid_pipe", "block/shaft")) {
            var sprite = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", texture);
            require(!ServiceTrenchCasingModel.usesFlatPipeLighting(false, sprite),
                    "Closing the grille preserves the original pipe lighting");
            require(ServiceTrenchCasingModel.usesFlatPipeLighting(true, sprite) == !texture.equals("block/shaft"),
                    "Only exposed copper and glass pipe sprites opt into flat lighting");
        }
        checkStraightTrenchPipePartials();
        System.out.println("Service Trench: casing and open pipe lighting flags passed.");
    }

    private static void checkStraightTrenchPipePartials() {
        for (Direction move : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            var center = createPipeModel(move == Direction.EAST ? "lr_y" : "ud_y");
            String inheritedCore = center.get("parent").getAsString().substring("create:block/fluid_pipe/".length());
            var core = createPipeModel(inheritedCore);
            var arms = List.of(createPipeModel("connection/" + move.getOpposite().getName()),
                    createPipeModel("connection/" + move.getName()));
            int axis = move.getAxis().ordinal();
            checkPipeTopPartial(core, center, axis, 4, 12);
            checkPipeTopPartial(arms.getFirst(), arms.getFirst(), axis, 0, 4);
            checkPipeTopPartial(arms.getLast(), arms.getLast(), axis, 12, 16);
        }
    }

    private static void checkPipeTopPartial(com.google.gson.JsonObject material, com.google.gson.JsonObject model,
                                            int axis, int from, int to) {
        var element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
        require(element.getAsJsonArray("from").get(axis).getAsInt() == from
                        && element.getAsJsonArray("to").get(axis).getAsInt() == to
                        && element.getAsJsonObject("faces").has("up"),
                "Create's straight pipe top is split into two quarter-length arms and a half-length center");
        String texture = element.getAsJsonObject("faces").getAsJsonObject("up").get("texture").getAsString();
        var sprite = net.minecraft.resources.ResourceLocation.parse(
                material.getAsJsonObject("textures").get(texture.substring(1)).getAsString());
        require(ServiceTrenchCasingModel.usesFlatPipeLighting(true, sprite)
                        && !ServiceTrenchCasingModel.usesFlatPipeLighting(false, sprite),
                "Core and arms must use the same lighting mode for each grille state: " + sprite);
    }

    private static com.google.gson.JsonObject createPipeModel(String path) {
        try (var stream = CopycatDiagonalByteModelChecks.class.getResourceAsStream(
                "/assets/create/models/block/fluid_pipe/" + path + ".json")) {
            if (stream == null) throw new AssertionError("Missing Create pipe model " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                    stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read Create pipe model " + path, exception);
        }
    }

    private static void checkFallback() {
        var faces = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction side : Direction.values()) faces.put(side, List.of());
        List<BakedQuad> quads = List.of(sourceQuad(Direction.UP, 0, 0, 1, false));
        BakedModel original = new SimpleBakedModel(quads, faces, true, true, true,
                UnitTextureAtlasSprite.INSTANCE, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
        var model = new InspectableModel(original);
        require(model.getQuads(null, null, RandomSource.create()) == quads, "Unfilled geometry delegates directly to the original static model");
        require(model.materialFallback(null).isEmpty(), "Material fallback cannot leak default copycat faces");
    }

    private static Vec3 unrotate(Vec3 vertex) {
        double x = vertex.x - .5, z = vertex.z - .5;
        return new Vec3((x + z) / Math.sqrt(2) + .5, vertex.y, (-x + z) / Math.sqrt(2) + .5);
    }

    private static BakedQuad sourceQuad(Direction face, float originU, float originV, float span, boolean rotatedUV) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 v = normal.cross(u);
        Vec3 center = new Vec3(.5, .5, .5).add(normal.scale(.5));
        var builder = new QuadBakingVertexConsumer();
        builder.setDirection(face);
        builder.setSprite(UnitTextureAtlasSprite.INSTANCE);
        builder.setTintIndex(2);
        builder.setShade(!rotatedUV);
        builder.setHasAmbientOcclusion(!rotatedUV);
        for (int i = 0; i < 4; i++) {
            double a = i < 2 ? -.5 : .5;
            double b = i == 0 || i == 3 ? -.5 : .5;
            Vec3 vertex = center.add(u.scale(a)).add(v.scale(b));
            float textureU = (float) (rotatedUV ? b + .5 : a + .5);
            float textureV = (float) (rotatedUV ? .5 - a : b + .5);
            builder.addVertex((float) vertex.x, (float) vertex.y, (float) vertex.z)
                    .setColor(255, 255, 255, 255).setUv(originU + span * textureU, originV + span * textureV)
                    .setUv2(160, 160).setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        }
        return builder.bakeQuad();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static class InspectableModel extends CopycatDiagonalByteModel {
        private InspectableModel(BakedModel original) {
            super(original);
        }

        private List<BakedQuad> materialFallback(RenderType layer) {
            return originalModel.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, layer);
        }
    }
}
