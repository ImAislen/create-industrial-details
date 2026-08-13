package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.world.level.block.Blocks;
import com.aislen.createindustrialdetails.content.block.MooringBollardBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.RivetedSteelGrateBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.RivetedSteelHatchBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.beam.RivetedSteelBeamBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.panel.RivetedSteelPanelBlock;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.panel.RivetedSteelPanelShaftPenetrationBlock;
import com.aislen.createindustrialdetails.content.item.rivetedsteel.RivetedSteelPanelItem;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampBlock;
import com.aislen.createindustrialdetails.content.block.plankedplanks.PlankedPlanksBlock;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlock;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.block.StrutBlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
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


    private static <T extends Block> DeferredBlock<T> registerBlockWithItem(
            String name,
            Supplier<? extends T> blockFactory,
            BiFunction<T, Item.Properties, ? extends BlockItem> itemFactory
    ) {
        DeferredBlock<T> block = BLOCKS.register(name, blockFactory);
        ModItems.registerBlockItem(name, block, itemFactory);
        return block;
    }


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
                                    .lightLevel(state -> state.getValue(RivetedSteelCagedLampBlock.LIT) ? 12 : 0)
                    )
            );

    public static final DeferredBlock<WoodenBeamBlock> OAK_WOODEN_BEAM =
            registerBlockWithItem(
                    "oak_wooden_beam",
                    () -> new WoodenBeamBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.WOOD)
                                    .instrument(NoteBlockInstrument.BASS)
                                    .strength(2.0F)
                                    .sound(SoundType.WOOD)
                                    .ignitedByLava()
                                    .noOcclusion(),
                            new StrutModelType(
                                    ResourceLocation.fromNamespaceAndPath(
                                            CreateIndustrialDetails.MOD_ID,
                                            "block/wooden_beam/wooden_beam"
                                    ),
                                    ResourceLocation.withDefaultNamespace(
                                            "block/oak_log_top"
                                    ),
                                    8,
                                    8
                            )
                    ),
                    StrutBlockItem::new
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



    //Planked Planks

    public static final DeferredBlock<PlankedPlanksBlock> ACACIA_PLANKED_PLANKS =
            registerBlockWithItem(
                    "acacia_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> BAMBOO_PLANKED_PLANKS =
            registerBlockWithItem(
                    "bamboo_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> BIRCH_PLANKED_PLANKS =
            registerBlockWithItem(
                    "birch_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> CHERRY_PLANKED_PLANKS =
            registerBlockWithItem(
                    "cherry_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> CRIMSON_PLANKED_PLANKS =
            registerBlockWithItem(
                    "crimson_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> DARKOAK_PLANKED_PLANKS =
            registerBlockWithItem(
                    "darkoak_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> JUNGLE_PLANKED_PLANKS =
            registerBlockWithItem(
                    "jungle_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> MANGROVE_PLANKED_PLANKS =
            registerBlockWithItem(
                    "mangrove_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> OAK_PLANKED_PLANKS =
            registerBlockWithItem(
                    "oak_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> SPRUCE_PLANKED_PLANKS =
            registerBlockWithItem(
                    "spruce_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()
                    ),
                    BlockItem::new
            );

    public static final DeferredBlock<PlankedPlanksBlock> WARPED_PLANKED_PLANKS =
            registerBlockWithItem(
                    "warped_planked_planks",
                    () -> new PlankedPlanksBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.OAK_PLANKS)
                                    .noOcclusion()

                    ),
                    BlockItem::new
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
