package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlock;
import com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlockEntity;
import com.aislen.createindustrialdetails.content.block.cable.ControlChannel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class ControlBoxRenderer extends SafeBlockEntityRenderer<ControlBoxBlockEntity> {
    private static final ControlChannel[] CHANNELS = ControlChannel.values();
    private static final PartialModel[] DIALS = {
            dial("red"), dial("yellow"), dial("green"), dial("blue")
    };

    public ControlBoxRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static PartialModel dial(String channel) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID,
                "block/cable_tray/control_box_dial_" + channel));
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (PartialModel dial : DIALS) event.register(ModelResourceLocation.standalone(dial.modelLocation()));
    }

    @Override
    protected void renderSafe(ControlBoxBlockEntity box, float partialTicks, PoseStack poses,
                              MultiBufferSource buffers, int light, int overlay) {
        IndividualCableRenderer.renderPorts(box, poses, buffers, light, overlay);
        var state = box.getBlockState();
        if (!state.getValue(ControlBoxBlock.OPEN)) return;
        var facing = state.getValue(ControlBoxBlock.FACING);
        var vertices = buffers.getBuffer(RenderType.cutoutMipped());
        for (ControlChannel channel : CHANNELS) {
            int value = box.getSignal(channel);
            poses.pushPose();
            poses.last().pose().mul(ControlBoxDialPoses.pose(facing, channel, value));
            poses.last().normal().mul(ControlBoxDialPoses.normal(facing, channel, value));
            CachedBuffers.partial(DIALS[channel.ordinal()], state).light(light).overlay(overlay).renderInto(poses, vertices);
            poses.popPose();
        }
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(ControlBoxBlockEntity box) { return new net.minecraft.world.phys.AABB(box.getBlockPos()).inflate(31); }

}
