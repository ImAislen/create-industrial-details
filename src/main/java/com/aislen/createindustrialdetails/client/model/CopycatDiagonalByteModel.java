package com.aislen.createindustrialdetails.client.model;

import com.mojang.math.Transformation;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class CopycatDiagonalByteModel extends CopycatModel {
    private static final CropPiece[][] FACE_PIECES = createFacePieces();
    private static final Matrix4f ROTATION = new Matrix4f().translation(.5f, .25f, .5f)
            .rotateY((float) -Math.PI / 4).translate(-.5f, -.25f, -.5f);
    private static final IQuadTransformer ROTATE = QuadTransformers.applying(new Transformation(ROTATION));
    private final BakedModel unfilledModel;

    public CopycatDiagonalByteModel(BakedModel originalModel) {
        super(new BakedModelWrapper<>(originalModel) {
            @Override
            public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
                return List.of();
            }
        });
        unfilledModel = originalModel;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        BlockState material = getMaterial(data);
        return AllBlocks.COPYCAT_BASE.has(material) ? unfilledModel.getRenderTypes(state, random, data)
                : ChunkRenderTypeSet.all();
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState material = getMaterial(data);
        return AllBlocks.COPYCAT_BASE.has(material) ? unfilledModel.useAmbientOcclusion(state, data, renderType)
                : getModelOf(material).useAmbientOcclusion(material, data, renderType);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return unfilledModel.getQuads(state, side, random);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
        if (renderType != null && !getRenderTypes(state, random, data).contains(renderType))
            return List.of();
        if (AllBlocks.COPYCAT_BASE.has(getMaterial(data)))
            return unfilledModel.getQuads(state, side, random, data, renderType);
        return super.getQuads(state, side, random, data, renderType);
    }

    @Override
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource random,
                                             BlockState material, ModelData wrappedData, RenderType renderType) {
        return cropAndRotate(getModelOf(material).getQuads(material, side, random, wrappedData, renderType));
    }

    static List<BakedQuad> cropAndRotate(List<BakedQuad> source) {
        if (source.isEmpty()) return List.of();
        List<BakedQuad> quads = new ArrayList<>(source.size() * 4);
        for (BakedQuad quad : source)
            for (CropPiece piece : FACE_PIECES[quad.getDirection().get3DDataValue()])
                quads.add(cropAndRotate(quad, piece));
        return quads;
    }

    private static BakedQuad cropAndRotate(BakedQuad source, CropPiece piece) {
        int[] vertices = BakedModelHelper.cropAndMove(source.getVertices(), source.getSprite(), piece.crop, piece.offset);
        preserveUVs(source.getVertices(), vertices, piece.offset);
        BakedQuad result = new BakedQuad(vertices, source.getTintIndex(), Direction.rotate(ROTATION, source.getDirection()),
                source.getSprite(), source.isShade(), source.hasAmbientOcclusion());
        ROTATE.processInPlace(result);
        return result;
    }

    private static void preserveUVs(int[] source, int[] cropped, Vec3 offset) {
        Vec3 origin = BakedQuadHelper.getXYZ(source, 0);
        Vec3 edge1 = BakedQuadHelper.getXYZ(source, 1).subtract(origin);
        Vec3 edge3 = BakedQuadHelper.getXYZ(source, 3).subtract(origin);
        double length1 = edge1.lengthSqr();
        double length3 = edge3.lengthSqr();
        double cross = edge1.dot(edge3);
        double determinant = length1 * length3 - cross * cross;
        if (Math.abs(determinant) < 1e-12) return;
        float u0 = BakedQuadHelper.getU(source, 0);
        float v0 = BakedQuadHelper.getV(source, 0);
        float u1 = BakedQuadHelper.getU(source, 1) - u0;
        float v1 = BakedQuadHelper.getV(source, 1) - v0;
        float u3 = BakedQuadHelper.getU(source, 3) - u0;
        float v3 = BakedQuadHelper.getV(source, 3) - v0;
        for (int vertex = 0; vertex < 4; vertex++) {
            Vec3 delta = BakedQuadHelper.getXYZ(cropped, vertex).subtract(offset).subtract(origin);
            double dot1 = delta.dot(edge1);
            double dot3 = delta.dot(edge3);
            double along1 = (dot1 * length3 - dot3 * cross) / determinant;
            double along3 = (dot3 * length1 - dot1 * cross) / determinant;
            BakedQuadHelper.setU(cropped, vertex, (float) (u0 + along1 * u1 + along3 * u3));
            BakedQuadHelper.setV(cropped, vertex, (float) (v0 + along1 * v1 + along3 * v3));
        }
    }

    private static CropPiece[][] createFacePieces() {
        CropPiece[][] pieces = new CropPiece[6][4];
        for (Direction face : Direction.values()) {
            int index = 0;
            for (int corner = 0; corner < 8; corner++) {
                int x = corner & 1;
                int y = (corner >> 1) & 1;
                int z = (corner >> 2) & 1;
                if (face.getAxis().choose(x, y, z) != (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0))
                    continue;
                AABB crop = new AABB(x * .75, y * .75, z * .75,
                        x * .75 + .25, y * .75 + .25, z * .75 + .25);
                Vec3 offset = new Vec3(.25 - x * .5, -y * .5, .25 - z * .5);
                pieces[face.get3DDataValue()][index++] = new CropPiece(crop, offset);
            }
        }
        return pieces;
    }

    private record CropPiece(AABB crop, Vec3 offset) {
    }
}
