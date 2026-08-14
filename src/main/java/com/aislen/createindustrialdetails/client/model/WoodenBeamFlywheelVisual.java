package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.cake.struts.compat.flywheel.StrutFlywheelVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import org.jetbrains.annotations.NotNull;

/** Reuses Struts' Flywheel visual with the same endpoint-aware quad cache. */
public final class WoodenBeamFlywheelVisual extends StrutFlywheelVisual {

    public WoodenBeamFlywheelVisual(
            @NotNull VisualizationContext context,
            @NotNull WoodenBeamBlockEntity blockEntity,
            float partialTick
    ) {
        super(context, prepare(blockEntity), partialTick);
    }

    @Override
    public void update(float partialTick) {
        prepare((WoodenBeamBlockEntity) blockEntity);
        super.update(partialTick);
    }

    @Override
    public void updateLight(float partialTick) {
        prepare((WoodenBeamBlockEntity) blockEntity);
        super.updateLight(partialTick);
    }

    private static WoodenBeamBlockEntity prepare(WoodenBeamBlockEntity beam) {
        if (beam.getLevel() != null && beam.connectionQuadCache == null) {
            beam.connectionQuadCache = WoodenBeamModelBuilder.buildConnectionQuads(beam);
        }
        return beam;
    }
}
