package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.cake.struts.content.block.StrutBlockEntity;
import com.cake.struts.content.block.StrutBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.jetbrains.annotations.NotNull;

/** Reuses Struts' renderer after supplying the endpoint-aware cached quad list. */
public final class WoodenBeamBlockEntityRenderer extends StrutBlockEntityRenderer {

    public WoodenBeamBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            StrutBlockEntity blockEntity,
            float partialTick,
            @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        if (blockEntity instanceof WoodenBeamBlockEntity beam
                && beam.getLevel() != null
                && beam.connectionQuadCache == null) {
            beam.connectionQuadCache = WoodenBeamModelBuilder.buildConnectionQuads(beam);
        }
        super.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
    }
}
