package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ControlBoxAliases {
    private ControlBoxAliases() {
    }

    public static void register() {
        addAlias(ModBlocks.BLOCKS, "industrial_control_box", "control_box");
        addAlias(ModItems.ITEMS, "industrial_control_box", "control_box");
        addAlias(ModBlockEntities.BLOCK_ENTITY_TYPES, "industrial_control_box", "control_box");
        addAlias(ModDataComponents.DATA_COMPONENTS, "industrial_control_box_signals", "control_box_signals");
    }

    private static void addAlias(DeferredRegister<?> registry, String previous, String current) {
        registry.addAlias(ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, previous),
                ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, current));
    }
}
