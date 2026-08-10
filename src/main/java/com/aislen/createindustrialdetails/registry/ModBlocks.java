package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.MooringBollardBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.RivetedSteelGrateBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.RivetedSteelHatchBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.beam.RivetedSteelBeamBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.panel.RivetedSteelPanelBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.panel.RivetedSteelPanelShaftPenetrationBlock;
import com.aislen.createindustrialdetails.content.item.rivetedsteel.RivetedSteelPanelItem;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.BiFunction;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateIndustrialDetails.MOD_ID);

    public static final DeferredBlock<Block> CAST_IRON_BLOCK =
            registerBlockWithItem(
                    "cast_iron_block",
                    () -> new Block(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .strength(5.0F, 6.0F)
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.NETHERITE_BLOCK)
                    )
            );


    public static final DeferredBlock<RivetedSteelCagedLampBlock> RIVETED_STEEL_CAGED_LAMP =
            registerBlockWithItem(
                    "riveted_steel_caged_lamp",
                    () -> new RivetedSteelCagedLampBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5F, 6.0F)
                                    .sound(SoundType.NETHERITE_BLOCK)
                                    .noOcclusion()
                                    .lightLevel(state -> state.getValue(RivetedSteelCagedLampBlock.LIT) ? 15 : 0)
                    )
            );

    public static final DeferredBlock<RivetedSteelBeamBlock>
            RIVETED_STEEL_BEAM =
            registerBlockWithItem(
                    "riveted_steel_beam",
                    () -> new RivetedSteelBeamBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.5F, 6.0F)
                                    .sound(SoundType.NETHERITE_BLOCK)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()
                    )
            );

    public static final DeferredBlock<RivetedSteelHatchBlock>
            RIVETED_STEEL_HATCH =
            registerBlockWithItem(
                    "riveted_steel_hatch",
                    () -> new RivetedSteelHatchBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.5F, 6.0F)
                                    .sound(SoundType.NETHERITE_BLOCK)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()
                    )
            );

    public static final DeferredBlock<RivetedSteelGrateBlock>
            RIVETED_STEEL_GRATE =
            registerBlockWithItem(
                    "riveted_steel_grate",
                    () -> new RivetedSteelGrateBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.5F, 6.0F)
                                    .sound(SoundType.NETHERITE_BLOCK)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()
                    )

            );


    private static <T extends Block> DeferredBlock<T> registerBlockWithItem(
            String name,
            Supplier<? extends T> blockFactory,
            BiFunction<T, Item.Properties, ? extends BlockItem> itemFactory
    ) {
        DeferredBlock<T> block = BLOCKS.register(name, blockFactory);
        ModItems.registerBlockItem(name, block, itemFactory);
        return block;
    }
    public static final DeferredBlock<MooringBollardBlock> MOORING_BOLLARD =
            registerBlockWithItem(
                    "mooring_bollard",
                    () -> new MooringBollardBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .strength(5.0F, 6.0F)
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.METAL)
                                    .noOcclusion()
                    )
            );

    public static final DeferredBlock<RivetedSteelPanelBlock> RIVETED_STEEL_PANEL =
            registerBlockWithItem(
                    "riveted_steel_panel",
                    () -> new RivetedSteelPanelBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.5F, 6.0F)
                                    .sound(SoundType.METAL)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()
                    ),
                    RivetedSteelPanelItem::new
            );
    public static final DeferredBlock<RivetedSteelPanelShaftPenetrationBlock>
            RIVETED_STEEL_PANEL_SHAFT_PENETRATION =
            BLOCKS.register(
                    "riveted_steel_panel_shaft_penetration",
                    () -> new RivetedSteelPanelShaftPenetrationBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.5F, 6.0F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()
                    )
            );

    private ModBlocks() {
    }


    private static <T extends Block> DeferredBlock<T> registerBlockWithItem(
            String name,
            Supplier<? extends T> blockFactory
    ) {
        DeferredBlock<T> block = BLOCKS.register(name, blockFactory);
        ModItems.registerBlockItem(name, block);

        return block;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
