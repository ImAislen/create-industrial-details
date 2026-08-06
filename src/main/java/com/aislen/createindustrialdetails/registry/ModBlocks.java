package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

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
                                    .sound(SoundType.METAL)
                    )
            );
    public static final DeferredBlock<Block> MOORING_BOLLARD =
            registerBlockWithItem(
                    "mooring_bollard",
                    () -> new Block(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .strength(5.0F, 6.0F)
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.METAL)
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