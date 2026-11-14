package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.aislen.createindustrialdetails.content.block.industrialsteel.trench.*;
import com.aislen.createindustrialdetails.content.block.industrialsteel.lighting.cagedlamp.IndustrialSteelCagedLampBlockEntity;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
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
            BlockEntityType<IndustrialSteelCagedLampBlockEntity>
            > INDUSTRIAL_STEEL_CAGED_LAMP =
            BLOCK_ENTITY_TYPES.register(
                    "industrial_steel_caged_lamp",
                    () -> BlockEntityType.Builder.of(
                            IndustrialSteelCagedLampBlockEntity::new,
                            ModBlocks.INDUSTRIAL_STEEL_CAGED_LAMP.get()
                    ).build(null)
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<WoodenBeamBlockEntity>
            > WOODEN_BEAM =
            BLOCK_ENTITY_TYPES.register(
                    "wooden_beam",
                    () -> BlockEntityType.Builder.of(
                            WoodenBeamBlockEntity::new,
                            ModBlocks.WOODEN_BEAMS.values().stream()
                                    .map(DeferredHolder::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServiceTrenchPipeBlockEntity>> SERVICE_TRENCH_PIPE =
            BLOCK_ENTITY_TYPES.register("service_trench_pipe", () -> BlockEntityType.Builder.of(
                    ServiceTrenchPipeBlockEntity::new, ModBlocks.SERVICE_TRENCH_PIPE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServiceTrenchShaftBlockEntity>> SERVICE_TRENCH_SHAFT =
            BLOCK_ENTITY_TYPES.register("service_trench_shaft", () -> BlockEntityType.Builder.of(
                    ServiceTrenchShaftBlockEntity::new, ModBlocks.SERVICE_TRENCH_SHAFT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServiceTrenchGlassPipeBlockEntity>> SERVICE_TRENCH_GLASS_PIPE =
            BLOCK_ENTITY_TYPES.register("service_trench_glass_pipe", () -> BlockEntityType.Builder.of(
                    ServiceTrenchGlassPipeBlockEntity::new, ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopycatBlockEntity>> COPYCAT_DIAGONAL_BYTE =
            BLOCK_ENTITY_TYPES.register("copycat_diagonal_byte", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CopycatBlockEntity(ModBlockEntities.COPYCAT_DIAGONAL_BYTE.get(), pos, state),
                    ModBlocks.COPYCAT_DIAGONAL_BYTE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
