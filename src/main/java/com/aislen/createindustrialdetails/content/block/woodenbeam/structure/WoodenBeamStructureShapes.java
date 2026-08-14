package com.aislen.createindustrialdetails.content.block.woodenbeam.structure;

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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Cached collision registry rebuilt only when Wooden Beam connections change. */
public final class WoodenBeamStructureShapes {

    private static final LevelSafeStorage<ShapeRegistry> STORAGE = new LevelSafeStorage<>(ShapeRegistry::new);

    private WoodenBeamStructureShapes() {
    }

    public static void registerConnection(
            Level level,
            BlockPos from,
            Vec3 fromAttachment,
            BlockPos to,
            Vec3 toAttachment,
            StrutModelType modelType
    ) {
        STORAGE.getForLevel(level).register(level, from, fromAttachment, to, toAttachment, modelType);
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
        private final Map<ConnectionKey, WoodenBeamLineGeometry> connections = new HashMap<>();
        private final Map<BlockPos, WoodenBeamPositionData> positions = new HashMap<>();
        private final Set<BlockPos> queuedRestores = new LinkedHashSet<>();

        void register(Level level, BlockPos from, Vec3 fromAttachment, BlockPos to, Vec3 toAttachment, StrutModelType type) {
            ConnectionKey key = new ConnectionKey(from, to);
            if (connections.containsKey(key)) {
                return;
            }
            WoodenBeamLineGeometry geometry = new WoodenBeamLineGeometry(
                    fromAttachment,
                    toAttachment,
                    type.shapeSizeXPixels(),
                    type.shapeSizeYPixels(),
                    type.voxelShapeResolutionPixels()
            );
            connections.put(key, geometry);
            for (BlockPos pos : geometry.getPositions()) {
                VoxelShape shape = geometry.getShapeForPosition(pos);
                if (shape.isEmpty()) {
                    continue;
                }
                positions.computeIfAbsent(pos, ignored -> new WoodenBeamPositionData()).add(key, shape);
                if (!pos.equals(from) && !pos.equals(to)) {
                    placeStructureBlock(level, pos);
                }
            }
        }

        void unregister(Level level, ConnectionKey key) {
            WoodenBeamLineGeometry geometry = connections.remove(key);
            if (geometry == null) {
                return;
            }
            for (BlockPos pos : geometry.getPositions()) {
                WoodenBeamPositionData data = positions.get(pos);
                if (data == null) {
                    continue;
                }
                data.remove(key);
                if (data.perConnection.isEmpty()) {
                    positions.remove(pos);
                    if (level.getBlockState(pos).is(ModBlocks.WOODEN_BEAM_STRUCTURE.get())) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }
}
