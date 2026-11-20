package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.content.block.pneumatic.PneumaticTubingItem;
import com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableColor;
import com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableItem;
import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.BiFunction;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateIndustrialDetails.MOD_ID);

    public static final DeferredItem<PneumaticTubingItem> PNEUMATIC_TUBING =
            ITEMS.register("pneumatic_tubing", () -> new PneumaticTubingItem(new Item.Properties()));

    public static final DeferredItem<IndividualCableItem> RED_CONTROL_CABLE =
            ITEMS.register("red_control_cable", () -> new IndividualCableItem(new Item.Properties(),
                    IndividualCableColor.RED));
    public static final DeferredItem<IndividualCableItem> YELLOW_CONTROL_CABLE =
            ITEMS.register("yellow_control_cable", () -> new IndividualCableItem(new Item.Properties(),
                    IndividualCableColor.YELLOW));

    public static final DeferredItem<IndividualCableItem> GREEN_CONTROL_CABLE =
            ITEMS.register("green_control_cable", () -> new IndividualCableItem(new Item.Properties(),
                    IndividualCableColor.GREEN));
    public static final DeferredItem<IndividualCableItem> BLUE_CONTROL_CABLE =
            ITEMS.register("blue_control_cable", () -> new IndividualCableItem(new Item.Properties(),
                    IndividualCableColor.BLUE));

    private ModItems() {
    }


    static <T extends Block> DeferredItem<BlockItem> registerBlockItem(
            String name,
            DeferredBlock<T> block
    ) {
        return ITEMS.registerSimpleBlockItem(name, block);
    }
    static <T extends Block> void registerBlockItem(
            String name,
            java.util.function.Supplier<? extends T> block,
            BiFunction<T, Item.Properties, ? extends BlockItem> itemFactory
    ) {
        ITEMS.register(
                name,
                () -> itemFactory.apply(block.get(), new Item.Properties())
        );
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
