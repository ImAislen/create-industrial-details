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
