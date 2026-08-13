package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(
                    Registries.CREATIVE_MODE_TAB,
                    CreateIndustrialDetails.MOD_ID
            );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab>
            INDUSTRIAL_DETAILS_TAB =
            CREATIVE_TABS.register(
                    "industrial_details",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable(
                                    "itemGroup.create_industrial_details"
                            ))
                            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                            .icon(() ->
                                    ModBlocks.CAST_IRON_BLOCK
                                            .get()
                                            .asItem()
                                            .getDefaultInstance()
                            )
                            .displayItems((parameters, output) -> {
                                output.accept(ModBlocks.CAST_IRON_BLOCK.get());
                                output.accept(ModBlocks.MOORING_BOLLARD.get());
                                output.accept(ModBlocks.RIVETED_STEEL_PANEL.get());
                                output.accept(ModBlocks.RIVETED_STEEL_GRATE.get());
                                output.accept(ModBlocks.RIVETED_STEEL_HATCH.get());
                                output.accept(ModBlocks.RIVETED_STEEL_BEAM.get());
                                output.accept(ModBlocks.RIVETED_STEEL_CAGED_LAMP.get());
                                output.accept(ModBlocks.OAK_WOODEN_BEAM.get());


                                output.accept(ModBlocks.ACACIA_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.BAMBOO_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.BIRCH_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.CHERRY_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.CRIMSON_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.DARKOAK_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.JUNGLE_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.MANGROVE_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.OAK_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.SPRUCE_PLANKED_PLANKS.get());
                                output.accept(ModBlocks.WARPED_PLANKED_PLANKS.get());


                            })
                            .build()
            );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
