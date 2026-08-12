package com.aislen.createindustrialdetails;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModCreativeTabs;
import com.aislen.createindustrialdetails.registry.ModItems;
import com.aislen.createindustrialdetails.registry.ModMenus;
import com.aislen.createindustrialdetails.registry.ModTooltips;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(
                CreateIndustrialDetails::commonSetup
        );

        LOGGER.info("Create: Industrial Details initialized.");
    }
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModTooltips::register);
    }
}
