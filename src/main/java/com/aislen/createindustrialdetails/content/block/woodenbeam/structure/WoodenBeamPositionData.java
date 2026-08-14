package com.aislen.createindustrialdetails.content.block.woodenbeam.structure;

import com.cake.struts.content.structure.ConnectionKey;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

final class WoodenBeamPositionData {
    final Map<ConnectionKey, VoxelShape> perConnection = new HashMap<>();
    VoxelShape merged = Shapes.empty();

    void add(ConnectionKey key, VoxelShape shape) {
        perConnection.put(key, shape);
        recompute();
    }

    void remove(ConnectionKey key) {
        perConnection.remove(key);
        recompute();
    }

    private void recompute() {
        VoxelShape result = Shapes.empty();
        for (VoxelShape shape : perConnection.values()) {
            result = Shapes.or(result, shape);
        }
        merged = result.isEmpty() ? result : result.optimize();
    }
}
