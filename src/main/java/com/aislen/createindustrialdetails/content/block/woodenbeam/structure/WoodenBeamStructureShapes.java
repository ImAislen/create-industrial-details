package com.aislen.createindustrialdetails.content.block.woodenbeam.structure;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamConnectionEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamGeometry;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.structure.ConnectionKey;
import com.cake.struts.internal.util.LevelSafeStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class WoodenBeamStructureShapes {

    private static final LevelSafeStorage<ShapeRegistry> STORAGE = new LevelSafeStorage<>(ShapeRegistry::new);

    private WoodenBeamStructureShapes() {
    }

    public static void registerConnection(
            Level level,
            BlockPos from,
            BlockPos to,
            WoodenBeamConnectionEndpoints endpoints,
            StrutModelType modelType
    ) {
        STORAGE.getForLevel(level).register(level, from, to, endpoints, modelType);
    }

    public static void unregisterConnection(Level level, BlockPos from, BlockPos to) {
        STORAGE.getForLevel(level).unregister(level, new ConnectionKey(from, to));
    }

    public static VoxelShape getShape(Level level, BlockPos pos) {
        WoodenBeamPositionData data = STORAGE.getForLevel(level).positions.get(pos);
        return data == null ? Shapes.empty() : data.merged;
    }

    public static Set<ConnectionKey> getConnectionsAt(Level level, BlockPos pos) {
        WoodenBeamPositionData data = STORAGE.getForLevel(level).positions.get(pos);
        return data == null ? Set.of() : Set.copyOf(data.perConnection.keySet());
    }

    public static boolean hasPositionData(Level level, BlockPos pos) {
        return STORAGE.getForLevel(level).positions.containsKey(pos);
    }

    public static void queueRestore(Level level, BlockPos pos) {
        STORAGE.getForLevel(level).queuedRestores.add(pos.immutable());
    }

    public static void flushRestores(Level level) {
        ShapeRegistry registry = STORAGE.getForLevel(level);
        if (registry.queuedRestores.isEmpty()) {
            return;
        }
        Set<BlockPos> queued = Set.copyOf(registry.queuedRestores);
        registry.queuedRestores.clear();
        for (BlockPos pos : queued) {
            if (registry.positions.containsKey(pos)) {
                placeStructureBlock(level, pos);
            }
        }
    }

    private static void placeStructureBlock(Level level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.is(ModBlocks.WOODEN_BEAM_STRUCTURE.get()) || !current.canBeReplaced()) {
            return;
        }
        BlockState structure = ModBlocks.WOODEN_BEAM_STRUCTURE.get().defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        level.setBlock(pos, structure, Block.UPDATE_ALL);
    }

    private static final class ShapeRegistry {
        private final Map<ConnectionKey, Set<BlockPos>> connections = new HashMap<>();
        private final Map<BlockPos, WoodenBeamPositionData> positions = new HashMap<>();
        private final Set<BlockPos> queuedRestores = new LinkedHashSet<>();

        void register(Level level, BlockPos from, BlockPos to, WoodenBeamConnectionEndpoints endpoints,
                      StrutModelType type) {
            ConnectionKey key = new ConnectionKey(from, to);
            Set<BlockPos> existing = connections.get(key);
            if (existing != null) {
                // Struts briefly registers its own structure while loading
                // the second anchor. Restore any proxies it just replaced.
                if (!level.isClientSide) {
                    placeConnectionBlocks(level, from, to, existing);
                }
                return;
            }
            var beam = from.compareTo(to) < 0
                    ? new WoodenBeamGeometry(level, from, to, endpoints)
                    : new WoodenBeamGeometry(level, to, from, endpoints.reversed());
            var shapes = WoodenBeamLineGeometry.voxelize(beam, type.voxelShapeResolutionPixels());
            Set<BlockPos> blocks = Set.copyOf(shapes.keySet());
            connections.put(key, blocks);
            for (var entry : shapes.entrySet()) {
                positions.computeIfAbsent(entry.getKey(), ignored -> new WoodenBeamPositionData())
                        .add(key, entry.getValue());
            }
            if (!level.isClientSide) {
                placeConnectionBlocks(level, from, to, blocks);
            }
        }

        private void placeConnectionBlocks(Level level, BlockPos from, BlockPos to, Set<BlockPos> blocks) {
            for (BlockPos pos : blocks) {
                if (!pos.equals(from) && !pos.equals(to)) {
                    placeStructureBlock(level, pos);
                }
            }
        }

        void unregister(Level level, ConnectionKey key) {
            Set<BlockPos> blocks = connections.remove(key);
            if (blocks == null) {
                return;
            }
            for (BlockPos pos : blocks) {
                WoodenBeamPositionData data = positions.get(pos);
                if (data == null) {
                    continue;
                }
                data.remove(key);
                if (data.perConnection.isEmpty()) {
                    positions.remove(pos);
                    if (!level.isClientSide && level.getBlockState(pos).is(ModBlocks.WOODEN_BEAM_STRUCTURE.get())) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }
}
