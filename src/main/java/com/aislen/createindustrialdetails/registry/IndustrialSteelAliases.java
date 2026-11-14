package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class IndustrialSteelAliases {
    private IndustrialSteelAliases() {
    }

    public static void register() {
        for (String suffix : List.of("panel", "grate", "hatch", "beam", "caged_lamp")) {
            addAlias(ModBlocks.BLOCKS, suffix);
            addAlias(ModItems.ITEMS, suffix);
        }
        addAlias(ModBlocks.BLOCKS, "panel_shaft_penetration");
        addAlias(ModBlockEntities.BLOCK_ENTITY_TYPES, "caged_lamp");
        addAlias(ModMenus.MENUS, "caged_lamp");
        addAlias(ModDataComponents.DATA_COMPONENTS, "caged_lamp_frequencies");
    }

    private static void addAlias(DeferredRegister<?> registry, String suffix) {
        registry.addAlias(
                ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "riveted_steel_" + suffix),
                ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "industrial_steel_" + suffix));
    }
}
