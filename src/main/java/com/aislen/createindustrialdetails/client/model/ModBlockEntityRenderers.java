package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.cake.struts.compat.flywheel.StrutsFlywheelCompatLoader;
import com.cake.struts.content.block.StrutBlockEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = CreateIndustrialDetails.MOD_ID,
        value = Dist.CLIENT
)
public final class ModBlockEntityRenderers {

    private ModBlockEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.WOODEN_BEAM.get(),
                StrutBlockEntityRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerFlywheelVisual(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                StrutsFlywheelCompatLoader.registerStrutVisual(
                        ModBlockEntities.WOODEN_BEAM.get()
                )
        );
    }
}
