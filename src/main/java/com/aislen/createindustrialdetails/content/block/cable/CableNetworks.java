package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
public final class CableNetworks {
    private static final Map<ServerLevel, CableNetworkManager> LEVELS = new IdentityHashMap<>();

    private CableNetworks() {
    }

    public static CableNetworkManager get(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, CableNetworkManager::new);
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            level.getServer().execute(() -> get(level).queueChunkLoad(chunk));
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            get(level).unloadChunk(chunk);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            CableNetworkManager manager = LEVELS.get(level);
            if (manager != null) manager.processPending();
        }
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        LEVELS.clear();
    }
}
