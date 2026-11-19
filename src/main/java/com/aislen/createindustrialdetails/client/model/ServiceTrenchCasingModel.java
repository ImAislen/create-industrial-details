package com.aislen.createindustrialdetails.client.model;

import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.FACING;
import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.OPEN;

final class ServiceTrenchCasingModel extends CTModel {
    private static final ResourceLocation COPPER_PIPE = ResourceLocation.fromNamespaceAndPath("create", "block/pipes");
    private static final ResourceLocation COPPER_PIPE_CORE = ResourceLocation.fromNamespaceAndPath("create", "block/pipes_connected");
    private static final ResourceLocation GLASS_PIPE = ResourceLocation.fromNamespaceAndPath("create", "block/glass_fluid_pipe");
    private static final ModelProperty<Integer> INTERIOR_LIGHT = new ModelProperty<>();

    ServiceTrenchCasingModel(BakedModel original, ServiceTrenchCTBehaviour behaviour) {
        super(original, behaviour);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter level, BlockPos pos,
                                               BlockState state, ModelData blockEntityData) {
        super.gatherModelData(builder, level, pos, state, blockEntityData);
        // Full collision makes flat lighting sample the side neighbor even for recessed faces.
        int light = state.isCollisionShapeFullBlock(level, pos) ? LevelRenderer.getLightColor(level, state, pos) : 0;
        return builder.with(INTERIOR_LIGHT, light);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data,
                                  RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, random, data, renderType);
        if (state == null || !data.has(INTERIOR_LIGHT)) return quads;

        Direction.Axis trenchAxis = state.getValue(FACING).getAxis();
        int light = data.get(INTERIOR_LIGHT);
        boolean open = state.getValue(OPEN);
        List<BakedQuad> result = quads;
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            ResourceLocation sprite = quad.getSprite().contents().name();
            boolean casing = sprite.equals(ServiceTrenchCTBehaviour.CASING_SPRITE);
            boolean wall = casing && isInteriorWall(quad, trenchAxis);
            if (!wall && !(casing ? isInteriorFloor(quad) : usesFlatPipeLighting(open, sprite))) continue;
            if (result == quads) result = new ArrayList<>(quads);
            result.set(i, lightInteriorQuad(quad, wall ? light : 0));
        }
        return result;
    }

    static boolean usesFlatPipeLighting(boolean open, ResourceLocation sprite) {
        // Create's copper core and connection arms use different sprites.
        return open && (COPPER_PIPE.equals(sprite) || COPPER_PIPE_CORE.equals(sprite) || GLASS_PIPE.equals(sprite));
    }

    static boolean isInteriorWall(BakedQuad quad, Direction.Axis trenchAxis) {
        Direction normal = quad.getDirection();
        if (!normal.getAxis().isHorizontal() || normal.getAxis() == trenchAxis) return false;

        boolean positive = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float wall = (positive ? 1 : 15) / 16f;
        float rail = (positive ? 3 : 13) / 16f;
        return isOnPlane(quad, wall) || isOnPlane(quad, rail);
    }

    static boolean isInteriorFloor(BakedQuad quad) {
        return quad.getDirection() == Direction.UP && isOnPlane(quad, 1 / 16f);
    }

    private static boolean isOnPlane(BakedQuad quad, float plane) {
        int[] vertices = quad.getVertices();
        int coordinate = quad.getDirection().getAxis().ordinal();
        for (int vertex = 0; vertex < 4; vertex++)
            if (Math.abs(Float.intBitsToFloat(vertices[vertex * BakedQuadHelper.VERTEX_STRIDE + coordinate]) - plane)
                    > 1e-4f) return false;
        return true;
    }

    static BakedQuad lightInteriorQuad(BakedQuad quad, int interiorLight) {
        int[] vertices = quad.getVertices();
        if (interiorLight != 0) {
            vertices = vertices.clone();
            for (int vertex = 0; vertex < 4; vertex++)
                vertices[vertex * BakedQuadHelper.VERTEX_STRIDE + BakedQuadHelper.LIGHT_OFFSET] = interiorLight;
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), false);
    }
}
