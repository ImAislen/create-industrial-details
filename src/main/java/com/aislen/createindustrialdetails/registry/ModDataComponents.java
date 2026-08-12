package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampFrequencies;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE,
                    CreateIndustrialDetails.MOD_ID
            );

    public static final DeferredHolder<
            DataComponentType<?>,
            DataComponentType<RivetedSteelCagedLampFrequencies>
            >
            RIVETED_STEEL_CAGED_LAMP_FREQUENCIES =
            DATA_COMPONENTS.registerComponentType(
                    "riveted_steel_caged_lamp_frequencies",
                    builder -> builder
                            .persistent(RivetedSteelCagedLampFrequencies.CODEC)
                            .networkSynchronized(
                                    RivetedSteelCagedLampFrequencies.STREAM_CODEC
                            )
            );

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
