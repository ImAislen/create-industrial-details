package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.*;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class TrayCableSpans {
    private static final Map<Level, Map<Long, Set<BlockPos>>> OWNERS = new WeakHashMap<>();
    private TrayCableSpans() {}
    public static void receive(CableSpanSync.Snapshot packet) {
        var level = Minecraft.getInstance().level; if (level == null) return;
        var chunks = OWNERS.computeIfAbsent(level, key -> new HashMap<>());
        var index = CableSpanIndex.client(level); var old = chunks.remove(packet.chunk().toLong());
        if (old != null) for (var owner : old) index.forget(owner);
        var grouped = new HashMap<BlockPos, List<CableSpans.Span>>();
        for (var span : packet.spans()) for (var endpoint : List.of(span.start(), span.end()))
            if (endpoint.isTray() && new net.minecraft.world.level.ChunkPos(endpoint.pos()).equals(packet.chunk()))
                grouped.computeIfAbsent(endpoint.pos(), key -> new ArrayList<>()).add(span);
        grouped.forEach(index::replace);
        if (!grouped.isEmpty()) chunks.put(packet.chunk().toLong(), Set.copyOf(grouped.keySet()));
        IndividualCableRenderer.pruneTrayCache(level);
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level) || !level.isClientSide) return;
        var chunks = OWNERS.get(level); if (chunks == null) return;
        var old = chunks.remove(event.getChunk().getPos().toLong());
        if (old != null) for (var owner : old) CableSpanIndex.client(level).forget(owner);
        IndividualCableRenderer.pruneTrayCache(level);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var level = Minecraft.getInstance().level; if (level == null) return;
        var camera = event.getCamera().getPosition(); var index = CableSpanIndex.client(level);
        boolean drew = false;
        for (var span : index.nearby(camera, 64)) if (span.start().isTray() && event.getFrustum().isVisible(index.bounds(span))) {
            IndividualCableRenderer.renderTraySpan(level, span, event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), camera);
            drew = true;
        }
        if (drew) Minecraft.getInstance().renderBuffers().bufferSource().endBatch(net.minecraft.client.renderer.RenderType.cutoutMipped());
    }
}
