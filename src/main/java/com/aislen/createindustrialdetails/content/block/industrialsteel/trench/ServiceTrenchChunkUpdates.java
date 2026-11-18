package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
final class ServiceTrenchChunkUpdates {
    private ServiceTrenchChunkUpdates() {}

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        var server = level.getServer();
        // Defer until this chunk and its block entities can be queried without waiting on chunk generation.
        server.tell(new TickTask(server.getTickCount(), () -> refreshLoadedChunk(level, chunk)));
    }

    private static void refreshLoadedChunk(ServerLevel level, LevelChunk chunk) {
        var pos = chunk.getPos();
        if (level.getChunkSource().getChunkNow(pos.x, pos.z) != chunk) return;
        refresh(level, chunk, 0, 15, 0, 15);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            LevelChunk neighbor = level.getChunkSource().getChunkNow(pos.x + side.getStepX(), pos.z + side.getStepZ());
            if (neighbor == null) continue;
            int minX = side == Direction.WEST ? 15 : 0;
            int maxX = side == Direction.EAST ? 0 : 15;
            int minZ = side == Direction.NORTH ? 15 : 0;
            int maxZ = side == Direction.SOUTH ? 0 : 15;
            refresh(level, neighbor, minX, maxX, minZ, maxZ);
        }
    }

    private static void refresh(ServerLevel level, LevelChunk chunk, int minX, int maxX, int minZ, int maxZ) {
        LevelChunkSection[] sections = chunk.getSections();
        var base = chunk.getPos();
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (!section.maybeHas(ServiceTrenchHousing::isTrench)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) * 16;
            for (int y = 0; y < 16; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        var state = section.getBlockState(x, y, z);
                        if (ServiceTrenchHousing.isTrench(state))
                            ServiceTrenchHousing.refresh(level, pos.set(base.getMinBlockX() + x, baseY + y,
                                    base.getMinBlockZ() + z), state);
                    }
                }
            }
        }
    }
}
