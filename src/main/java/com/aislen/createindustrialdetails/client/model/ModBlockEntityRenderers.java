package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.cake.struts.compat.flywheel.StrutsFlywheelCompatLoader;
import com.cake.struts.compat.sable.SableCompat;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

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
        WoodenBeamBlockEntity.CLIENT_ENDPOINT_UPDATE_LISTENER = WoodenBeamInteractionHandler::update;
        WoodenBeamBlockEntity.CLIENT_ENDPOINT_REMOVE_LISTENER = WoodenBeamInteractionHandler::remove;
        event.registerBlockEntityRenderer(
                ModBlockEntities.WOODEN_BEAM.get(),
                WoodenBeamBlockEntityRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerFlywheelVisual(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            StrutsFlywheelCompatLoader.init();
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.WOODEN_BEAM.get())
                    .factory(WoodenBeamFlywheelVisual::new)
                    .skipVanillaRender(blockEntity ->
                            StrutsFlywheelCompatLoader.supportsVisualization(blockEntity.getLevel())
                                    && !SableCompat.isInSubLevel(
                                            blockEntity.getLevel(),
                                            blockEntity.getBlockPos()
                                    )
                    )
                    .apply();
        });
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)
                resourceManager -> WoodenBeamModelBuilder.invalidateMeshes());
    }
}
