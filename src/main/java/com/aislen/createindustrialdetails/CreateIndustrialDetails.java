package com.aislen.createindustrialdetails;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModCreativeTabs;
import com.aislen.createindustrialdetails.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CreateIndustrialDetails.MOD_ID)
public final class CreateIndustrialDetails {
    public static final String MOD_ID = "create_industrial_details";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateIndustrialDetails(
            IEventBus modEventBus,
            ModContainer modContainer
    ) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        LOGGER.info("Create: Industrial Details initialized.");
    }
}