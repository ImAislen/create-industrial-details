package com.aislen.createindustrialdetails.content.block.woodenbeam.structure;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlock;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
public final class WoodenBeamStructureRestoreHandler {

    private WoodenBeamStructureRestoreHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState broken = event.getState();
        if (broken.getBlock() instanceof WoodenBeamBlock || broken.is(ModBlocks.WOODEN_BEAM_STRUCTURE.get())) {
            return;
        }
        if (WoodenBeamStructureShapes.hasPositionData(level, event.getPos())) {
            WoodenBeamStructureShapes.queueRestore(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WoodenBeamStructureShapes.flushRestores(level);
        }
    }
}
