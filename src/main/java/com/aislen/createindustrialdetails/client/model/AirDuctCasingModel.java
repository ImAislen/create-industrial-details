package com.aislen.createindustrialdetails.client.model;

import com.simibubi.create.foundation.block.connected.CTModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.List;

public final class AirDuctCasingModel extends CTModel {
    public AirDuctCasingModel(BakedModel original, AirDuctCasingCTBehaviour behavior) {
        super(original, behavior);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
        var original = originalModel.getQuads(state, side, random, data, renderType);
        var connected = super.getQuads(state, side, random, data, renderType);
        for (int i = 0; i < connected.size(); i++) {
            if (connected.get(i) != original.get(i) && !isExterior(original.get(i)))
                connected.set(i, original.get(i));
        }
        return connected;
    }

    private static boolean isExterior(BakedQuad quad) {
        Direction normal = quad.getDirection();
        int coordinate = normal.getAxis().ordinal();
        float plane = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            if (Float.intBitsToFloat(vertices[vertex * stride + coordinate]) != plane) return false;
        }
        return true;
    }
}
