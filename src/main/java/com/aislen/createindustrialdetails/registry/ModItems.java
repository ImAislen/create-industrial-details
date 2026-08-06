package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateIndustrialDetails.MOD_ID);

    private ModItems() {
    }

    /**
     * Registers the inventory item corresponding to a block.
     */
    static <T extends Block> DeferredItem<BlockItem> registerBlockItem(
            String name,
            DeferredBlock<T> block
    ) {
        return ITEMS.registerSimpleBlockItem(name, block);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}