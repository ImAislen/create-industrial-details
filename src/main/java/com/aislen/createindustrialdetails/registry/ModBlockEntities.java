package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampBlockEntity;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.cake.struts.content.block.StrutBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    CreateIndustrialDetails.MOD_ID
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<RivetedSteelCagedLampBlockEntity>
            > RIVETED_STEEL_CAGED_LAMP =
            BLOCK_ENTITY_TYPES.register(
                    "riveted_steel_caged_lamp",
                    () -> BlockEntityType.Builder.of(
                            RivetedSteelCagedLampBlockEntity::new,
                            ModBlocks.RIVETED_STEEL_CAGED_LAMP.get()
                    ).build(null)
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<StrutBlockEntity>
            > WOODEN_BEAM =
            BLOCK_ENTITY_TYPES.register(
                    "wooden_beam",
                    () -> BlockEntityType.Builder.<StrutBlockEntity>of(
                            WoodenBeamBlockEntity::new,
                            ModBlocks.OAK_WOODEN_BEAM.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
