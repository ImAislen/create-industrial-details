package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.rivetedsteel.trench.ServiceTrenchPipeFlanges;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import java.util.ArrayList;
import java.util.List;

public final class ServiceTrenchPipeModel extends BakedModelWrapperWithData {
    private static final ModelProperty<Integer> EXPOSED_ENDS = new ModelProperty<>();

    public ServiceTrenchPipeModel(BakedModel original) {
        super(original);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter level, BlockPos pos,
                                               BlockState state, ModelData blockEntityData) {
        return builder.with(EXPOSED_ENDS, ServiceTrenchPipeFlanges.exposedEnds(level, pos, state));
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        var types = new ArrayList<ChunkRenderTypeSet>();
        types.add(super.getRenderTypes(state, random, data));
        int mask = data.has(EXPOSED_ENDS) ? data.get(EXPOSED_ENDS) : 0;
        for (Direction end : Direction.values()) {
            if ((mask & (1 << end.ordinal())) != 0)
                types.add(rim(end).getRenderTypes(state, random, data));
        }
        return ChunkRenderTypeSet.union(types);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
        var quads = new ArrayList<>(super.getQuads(state, side, random, data, renderType));
        int mask = data.has(EXPOSED_ENDS) ? data.get(EXPOSED_ENDS) : 0;
        quads.removeIf(quad -> (mask & (1 << quad.getDirection().ordinal())) != 0 && isTubeEndCap(quad));
        for (Direction end : Direction.values()) {
            if ((mask & (1 << end.ordinal())) != 0)
                quads.addAll(rim(end).getQuads(state, side, random, data, renderType));
        }
        return quads;
    }

    private static boolean isTubeEndCap(BakedQuad quad) {
        if (!quad.getSprite().contents().name().equals(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/pipes"))) return false;
        Direction direction = quad.getDirection();
        int axis = direction.getAxis().ordinal();
        float plane = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        float[] minimum = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] maximum = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                float value = Float.intBitsToFloat(vertices[vertex * stride + coordinate]);
                minimum[coordinate] = Math.min(minimum[coordinate], value);
                maximum[coordinate] = Math.max(maximum[coordinate], value);
            }
        }
        for (int coordinate = 0; coordinate < 3; coordinate++) {
            if (minimum[coordinate] != (coordinate == axis ? plane : 0.25F)
                    || maximum[coordinate] != (coordinate == axis ? plane : 0.75F)) return false;
        }
        return true;
    }

    private static BakedModel rim(Direction side) {
        return AllPartialModels.PIPE_ATTACHMENTS.get(ComponentPartials.RIM).get(side).get();
    }
}
