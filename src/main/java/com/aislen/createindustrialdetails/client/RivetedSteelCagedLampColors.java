package com.aislen.createindustrialdetails.client;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampBlock;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(
        modid = CreateIndustrialDetails.MOD_ID,
        value = Dist.CLIENT
)
public final class RivetedSteelCagedLampColors {

    private static final int OFF_COLOR = 0xFFB9B39F;
    private static final int LIT_COLOR = 0xFFFFE2A3;

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (tintIndex != 0)
                        return 0xFFFFFFFF;

                    return state.getValue(RivetedSteelCagedLampBlock.LIT)
                            ? LIT_COLOR
                            : OFF_COLOR;
                },
                ModBlocks.RIVETED_STEEL_CAGED_LAMP.get()
        );
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> tintIndex == 0
                        ? OFF_COLOR
                        : 0xFFFFFFFF,
                ModBlocks.RIVETED_STEEL_CAGED_LAMP.get().asItem()
        );
    }
}