package com.aislen.createindustrialdetails.registry;

import net.minecraft.core.GlobalPos;
import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.industrialsteel.lighting.cagedlamp.IndustrialSteelCagedLampFrequencies;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE,
                    CreateIndustrialDetails.MOD_ID
            );

    public static final DeferredHolder<
            DataComponentType<?>,
            DataComponentType<IndustrialSteelCagedLampFrequencies>
            >
            INDUSTRIAL_STEEL_CAGED_LAMP_FREQUENCIES =
            DATA_COMPONENTS.registerComponentType(
                    "industrial_steel_caged_lamp_frequencies",
                    builder -> builder
                            .persistent(IndustrialSteelCagedLampFrequencies.CODEC)
                            .networkSynchronized(
                                    IndustrialSteelCagedLampFrequencies.STREAM_CODEC
                            )
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Byte>>
            WOODEN_BEAM_FROM_SNAP =
            DATA_COMPONENTS.registerComponentType(
                    "wooden_beam_from_snap",
                    builder -> builder
                            .persistent(Codec.BYTE)
                            .networkSynchronized(ByteBufCodecs.BYTE)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CONTROL_BOX_SIGNALS =
            DATA_COMPONENTS.registerComponentType("control_box_signals", builder -> builder
                    .persistent(Codec.intRange(0, 65535)).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> CABLE_FROM =
            DATA_COMPONENTS.registerComponentType("cable_from", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> CABLE_FROM_TOKEN =
            DATA_COMPONENTS.registerComponentType("cable_from_token", b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CABLE_FROM_ROUTE =
            DATA_COMPONENTS.registerComponentType("cable_from_route", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ITEM_VARIANT =
            DATA_COMPONENTS.registerComponentType("item_variant", builder -> builder
                    .persistent(Codec.sizeLimitedString(64)).networkSynchronized(ByteBufCodecs.stringUtf8(64)));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
