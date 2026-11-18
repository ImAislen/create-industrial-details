package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;
import java.util.*;

public final class IndividualCableConnectionModel extends BakedModelWrapperWithData {
    private static final ModelProperty<List<BakedQuad>> GEOMETRY = new ModelProperty<>();
    private record Snapshot(int revision, List<BakedQuad> quads) {}
    private final Map<IndividualCableBlockEntity, Snapshot> cache = Collections.synchronizedMap(new WeakHashMap<>());
    public IndividualCableConnectionModel(BakedModel original) { super(original); }
    @Override protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        if (!(level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) || host.getLevel() == null) return builder;
        var snapshot = cache.get(host);
        if (snapshot == null || snapshot.revision != host.revision) {
            var quads = new ArrayList<BakedQuad>();
            for (var color : IndividualCableColor.values()) {
                var route = state.getValue(color.property); if (route == CableRoute.NONE) continue;
                quads.addAll(IndividualCableRenderer.connectionComponent(host, color, route));
            }
            snapshot = new Snapshot(host.revision, List.copyOf(quads)); cache.put(host, snapshot);
        }
        return builder.with(GEOMETRY, snapshot.quads);
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        if (data.has(GEOMETRY)) return side == null ? data.get(GEOMETRY) : List.of();
        return super.getQuads(state, side, random, data, type);
    }
}
