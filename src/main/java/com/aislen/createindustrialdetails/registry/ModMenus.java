package com.aislen.createindustrialdetails.registry;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(
                    Registries.MENU,
                    CreateIndustrialDetails.MOD_ID
            );

    public static final DeferredHolder<
            MenuType<?>,
            MenuType<RivetedSteelCagedLampMenu>
            > RIVETED_STEEL_CAGED_LAMP =
            MENUS.register(
                    "riveted_steel_caged_lamp",
                    () -> IMenuTypeExtension.create(
                            RivetedSteelCagedLampMenu::new
                    )
            );

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
