package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockItem;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamSnapPoint;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import com.cake.struts.content.StrutPreviewRenderTransforms;
import com.cake.struts.content.block.StrutBlock;
import com.cake.struts.content.shape.DefaultStrutConnectionShape;
import com.cake.struts.content.shape.StrutConnectionShape;
import com.cake.struts.internal.microliner.Microliner;
import com.cake.struts.internal.microliner.MicrolinerCoordinateTransform;
import com.cake.struts.internal.microliner.MicrolinerOutline;
import com.cake.struts.internal.microliner.MicrolinerParams;
import com.cake.struts.registry.StrutDataComponents;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Endpoint-aware counterpart to Struts' stock centered placement outline. */
@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class WoodenBeamPlacementPreview {

    private WoodenBeamPlacementPreview() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        ItemStack held = heldBeam(minecraft.player);
        if (held == null) {
            return;
        }
        display(minecraft, minecraft.level, held);
    }

    private static ItemStack heldBeam(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof WoodenBeamBlockItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof WoodenBeamBlockItem) {
            return player.getOffhandItem();
        }
        return null;
    }

    private static void display(Minecraft minecraft, ClientLevel level, ItemStack held) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(held.getItem() instanceof WoodenBeamBlockItem item)) {
            return;
        }

        WoodenBeamBlockItem.EndpointSelection to = WoodenBeamBlockItem.resolveSelection(level, hit, item.getBlock());
        if (to == null) {
            return;
        }

        StrutBlock block = (StrutBlock) item.getBlock();
        double halfWidth = block.getModelType().shapeSizeXPixels() / 32.0;
        double halfHeight = block.getModelType().shapeSizeYPixels() / 32.0;
        BlockPos fromPos = held.get(StrutDataComponents.GIRDER_STRUT_FROM);
        Direction fromFace = held.get(StrutDataComponents.GIRDER_STRUT_FROM_FACE);
        Byte fromSnapId = held.get(ModDataComponents.WOODEN_BEAM_FROM_SNAP.get());
        if (fromPos == null || fromFace == null) {
            showEndpointMarker(
                    "wooden_beam_preview_target",
                    to,
                    halfWidth,
                    halfHeight,
                    new Vector3f(.45f, .8f, .65f),
                    MicrolinerCoordinateTransform.IDENTITY
            );
            return;
        }
        WoodenBeamBlockItem.EndpointSelection from = new WoodenBeamBlockItem.EndpointSelection(
                fromPos,
                fromFace,
                fromSnapId == null ? WoodenBeamSnapPoint.CENTER : WoodenBeamSnapPoint.byId(fromSnapId)
        );
        Vec3 fromPoint = WoodenBeamEndpoints.attachment(from.anchorPos(), from.supportFace(), from.snap());
        Vec3 toPoint = WoodenBeamEndpoints.attachment(to.anchorPos(), to.supportFace(), to.snap());
        if (fromPoint.distanceToSqr(toPoint) < 1.0e-6) {
            return;
        }

        boolean valid = WoodenBeamBlockItem.isValidConnection(level, from, to);
        Vector3f color = valid ? new Vector3f(.35f, .85f, .55f) : new Vector3f(.85f, .35f, .55f);
        StrutConnectionShape shape = new DefaultStrutConnectionShape(
                fromPoint,
                toPoint,
                halfWidth,
                halfHeight,
                from.anchorPos(),
                from.supportFace(),
                to.anchorPos(),
                to.supportFace()
        );
        StrutPreviewRenderTransforms transforms = StrutPreviewRenderTransforms.resolve(
                level,
                from.anchorPos(),
                to.anchorPos()
        );
        int packedColor = packColor(color.x, color.y, color.z);
        Microliner.get().showOutline(
                "wooden_beam_preview_shape",
                (poseStack, buffer, camera, transform, params) -> {
                    VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
                    shape.drawOutline(poseStack, consumer, camera, packedColor, transform);
                },
                transforms.connection(),
                new MicrolinerParams(1 / 16f, color.x, color.y, color.z, 1f, 2)
        );
        showEndpointMarker(
                "wooden_beam_preview_from",
                from,
                halfWidth,
                halfHeight,
                color,
                transforms.from()
        );
        showEndpointMarker(
                "wooden_beam_preview_target",
                to,
                halfWidth,
                halfHeight,
                color,
                transforms.to()
        );
    }

    private static void showEndpointMarker(
            String id,
            WoodenBeamBlockItem.EndpointSelection endpoint,
            double halfWidth,
            double halfHeight,
            Vector3f color,
            MicrolinerCoordinateTransform coordinateTransform
    ) {
        WoodenBeamEndpoints.FaceBasis basis = WoodenBeamEndpoints.basis(endpoint.supportFace());
        Vec3 center = WoodenBeamEndpoints.markerPoint(
                endpoint.anchorPos(),
                endpoint.supportFace(),
                endpoint.snap()
        );
        Vec3 u = Vec3.atLowerCornerOf(basis.u()).scale(halfWidth);
        Vec3 v = Vec3.atLowerCornerOf(basis.v()).scale(halfHeight);
        Vec3[] corners = {
                center.subtract(u).subtract(v),
                center.add(u).subtract(v),
                center.add(u).add(v),
                center.subtract(u).add(v)
        };

        Microliner.get().showOutline(
                id,
                (poseStack, buffer, camera, transform, params) -> {
                    VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
                    for (int i = 0; i < corners.length; i++) {
                        Vec3 from = transform.transform(corners[i]).subtract(camera);
                        Vec3 to = transform.transform(corners[(i + 1) % corners.length]).subtract(camera);
                        MicrolinerOutline.renderLine(
                                poseStack,
                                consumer,
                                from,
                                to,
                                params.r(),
                                params.g(),
                                params.b(),
                                params.a()
                        );
                    }
                },
                coordinateTransform,
                new MicrolinerParams(1 / 16f, color.x, color.y, color.z, .7f, 2)
        );
    }

    private static int packColor(float r, float g, float b) {
        return 0xFF000000
                | ((int) (r * 255.0F) << 16)
                | ((int) (g * 255.0F) << 8)
                | (int) (b * 255.0F);
    }
}
