package com.aislen.createindustrialdetails.registry;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;

public final class ModTooltips {

    private ModTooltips() {
    }

    public static void register() {
        Item grateItem =
                ModBlocks.RIVETED_STEEL_GRATE.get().asItem();

        TooltipModifier.REGISTRY.register(
                grateItem,
                new ItemDescription.Modifier(
                        grateItem,
                        FontHelper.Palette.STANDARD_CREATE
                )
        );
    }
}