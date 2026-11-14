package com.aislen.createindustrialdetails.client;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.industrialsteel.lighting.cagedlamp.IndustrialSteelCagedLampBlock;
import com.aislen.createindustrialdetails.content.block.industrialsteel.lighting.cagedlamp.IndustrialSteelCagedLampColor;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlockItemStateProperties;

@EventBusSubscriber(
        modid = CreateIndustrialDetails.MOD_ID,
        value = Dist.CLIENT
)
public final class IndustrialSteelCagedLampColors {

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (tintIndex != 0) {
                        return 0xFFFFFFFF;
                    }

                    return getDisplayColor(
                            state.getValue(IndustrialSteelCagedLampBlock.COLOR),
                            state.getValue(IndustrialSteelCagedLampBlock.LIT)
                    );
                },
                ModBlocks.INDUSTRIAL_STEEL_CAGED_LAMP.get()
        );
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;

        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;

        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);

        return (r << 16) | (g << 8) | bl;
    }

    public static int getDisplayColor(
            IndustrialSteelCagedLampColor color,
            boolean lit
    ) {
        int tint = color.getTintColor();
        return lit ? tint : mix(tint, 0xD8D1BF, 0.35f);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> {
                    if (tintIndex != 0)
                        return 0xFFFFFFFF;

                    BlockItemStateProperties properties =
                            stack.getOrDefault(
                                    DataComponents.BLOCK_STATE,
                                    BlockItemStateProperties.EMPTY
                            );

                    IndustrialSteelCagedLampColor color =
                            properties.get(IndustrialSteelCagedLampBlock.COLOR);

                    if (color == null)
                        color = IndustrialSteelCagedLampColor.NATURAL;

                    return mix(color.getTintColor(), 0xD8D1BF, 0.35f);
                },
                ModBlocks.INDUSTRIAL_STEEL_CAGED_LAMP.get().asItem()
        );
    }
}
