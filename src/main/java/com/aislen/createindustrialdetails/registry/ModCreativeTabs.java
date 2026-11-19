package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateIndustrialDetails.MOD_ID);

    private static final List<WoodenBeamMaterial> TIMBER_WOOD_ORDER = List.of(
            WoodenBeamMaterial.OAK,
            WoodenBeamMaterial.SPRUCE,
            WoodenBeamMaterial.BIRCH,
            WoodenBeamMaterial.JUNGLE,
            WoodenBeamMaterial.ACACIA,
            WoodenBeamMaterial.DARK_OAK,
            WoodenBeamMaterial.MANGROVE,
            WoodenBeamMaterial.CHERRY,
            WoodenBeamMaterial.BAMBOO,
            WoodenBeamMaterial.CRIMSON,
            WoodenBeamMaterial.WARPED
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FOUNDATIONS_TAB =
            CREATIVE_TABS.register("industrial_details", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_industrial_details"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModBlocks.CAST_IRON_BLOCK.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> displayFoundations(output))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TIMBER_TAB =
            CREATIVE_TABS.register("timber", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_industrial_details.timber"))
                    .withTabsBefore(FOUNDATIONS_TAB.getKey())
                    .icon(() -> ModBlocks.OAK_WOODEN_BEAM.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> displayTimber(output))
                    .build());

    private ModCreativeTabs() {
    }

    private static void displayFoundations(CreativeModeTab.Output output) {
        output.accept(ModBlocks.CAST_IRON_BLOCK.get());
        output.accept(ModBlocks.BLOCK_OF_INDUSTRIAL_STEEL.get());
        output.accept(ModBlocks.BLOCK_OF_PLATED_INDUSTRIAL_STEEL.get());
        output.accept(ModBlocks.MOORING_BOLLARD.get());
        output.accept(ModBlocks.INDUSTRIAL_STEEL_PANEL.get());
        output.accept(ModBlocks.COPYCAT_DIAGONAL_BYTE.get());
        output.accept(ModBlocks.INDUSTRIAL_STEEL_GRATE.get());
        output.accept(ModBlocks.INDUSTRIAL_STEEL_HATCH.get());
        output.accept(ModBlocks.INDUSTRIAL_STEEL_BEAM.get());
        output.accept(ModBlocks.SERVICE_TRENCH.get());
        output.accept(ModBlocks.SMALL_STEEL_WALL_BRACKET.get());
        output.accept(ModBlocks.STEEL_WALL_BRACKET.get());
        output.accept(ModBlocks.STEEL_WALL_CORNER_BRACKET.get());
        output.accept(ModBlocks.INDUSTRIAL_STEEL_CAGED_LAMP.get());
        output.accept(ModBlocks.AIR_DUCT.get());
        output.accept(ModBlocks.AIR_VENT.get());
        output.accept(ModBlocks.AIR_INTAKE.get());
        output.accept(ModBlocks.CABLE_TRAY.get());
        output.accept(ModBlocks.CONTROL_BOX.get());
        output.accept(ModItems.RED_CONTROL_CABLE.get());
        output.accept(ModItems.YELLOW_CONTROL_CABLE.get());
        output.accept(ModItems.GREEN_CONTROL_CABLE.get());
        output.accept(ModItems.BLUE_CONTROL_CABLE.get());
    }

    private static void displayTimber(CreativeModeTab.Output output) {
        for (WoodenBeamMaterial wood : TIMBER_WOOD_ORDER) {
            for (WoodenBeamMaterial.Variant variant : wood.variants()) {
                output.accept(ModBlocks.WOODEN_BEAMS.get(variant.registryName()).get());
            }
        }

        for (WoodenBeamMaterial wood : TIMBER_WOOD_ORDER) {
            for (WoodenBeamMaterial.Variant variant : wood.variants()) {
                output.accept(ModBlocks.WOODEN_POSTS.get(variant.postRegistryName()).get());
            }
        }

        output.accept(ModBlocks.OAK_PLANKED_PLANKS.get());
        output.accept(ModBlocks.SPRUCE_PLANKED_PLANKS.get());
        output.accept(ModBlocks.BIRCH_PLANKED_PLANKS.get());
        output.accept(ModBlocks.JUNGLE_PLANKED_PLANKS.get());
        output.accept(ModBlocks.ACACIA_PLANKED_PLANKS.get());
        output.accept(ModBlocks.DARKOAK_PLANKED_PLANKS.get());
        output.accept(ModBlocks.MANGROVE_PLANKED_PLANKS.get());
        output.accept(ModBlocks.CHERRY_PLANKED_PLANKS.get());
        output.accept(ModBlocks.BAMBOO_PLANKED_PLANKS.get());
        output.accept(ModBlocks.CRIMSON_PLANKED_PLANKS.get());
        output.accept(ModBlocks.WARPED_PLANKED_PLANKS.get());

        output.accept(ModBlocks.OAK_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.SPRUCE_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.BIRCH_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.JUNGLE_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.ACACIA_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.DARKOAK_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.MANGROVE_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.CHERRY_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.BAMBOO_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.CRIMSON_PLANKED_PLANKS_PANEL.get());
        output.accept(ModBlocks.WARPED_PLANKED_PLANKS_PANEL.get());

        output.accept(ModBlocks.OAK_SINGLE_PLANK.get());
        output.accept(ModBlocks.SPRUCE_SINGLE_PLANK.get());
        output.accept(ModBlocks.BIRCH_SINGLE_PLANK.get());
        output.accept(ModBlocks.JUNGLE_SINGLE_PLANK.get());
        output.accept(ModBlocks.ACACIA_SINGLE_PLANK.get());
        output.accept(ModBlocks.DARKOAK_SINGLE_PLANK.get());
        output.accept(ModBlocks.MANGROVE_SINGLE_PLANK.get());
        output.accept(ModBlocks.CHERRY_SINGLE_PLANK.get());
        output.accept(ModBlocks.BAMBOO_SINGLE_PLANK.get());
        output.accept(ModBlocks.CRIMSON_SINGLE_PLANK.get());
        output.accept(ModBlocks.WARPED_SINGLE_PLANK.get());
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
