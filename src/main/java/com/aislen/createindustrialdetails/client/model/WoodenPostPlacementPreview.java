package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.woodenpost.WoodenPostBlock;
import com.cake.struts.internal.microliner.Microliner;
import com.cake.struts.internal.microliner.MicrolinerCoordinateTransform;
import com.cake.struts.internal.microliner.MicrolinerOutline;
import com.cake.struts.internal.microliner.MicrolinerParams;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Lightweight held-item outline for the exact Wooden Post state that vanilla placement will use. */
@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class WoodenPostPlacementPreview {
    private static final String OUTLINE_ID = "wooden_post_placement_preview";
    private static final double OUTLINE_OFFSET = 1.0D / 1024.0D;
    private static final MicrolinerParams OUTLINE_STYLE =
            new MicrolinerParams(1 / 16f, .45f, .8f, .65f, .7f, 2);

    private WoodenPostPlacementPreview() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.isPaused() || player == null || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || hit.getDirection().getAxis() != Direction.Axis.Y) {
            return;
        }

        InteractionHand hand = heldPostHand(player);
        if (hand == null) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(player, hand, hit));
        if (!context.canPlace()
                || !(held.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof WoodenPostBlock post)) {
            return;
        }

        BlockState placementState = post.getStateForPlacement(context);
        BlockPos placementPos = context.getClickedPos();
        if (placementState == null || !placementState.canSurvive(minecraft.level, placementPos)) {
            return;
        }

        AABB outline = placementState.getShape(minecraft.level, placementPos)
                .bounds()
                .move(placementPos)
                .inflate(OUTLINE_OFFSET);
        showOutline(outline);
    }

    private static InteractionHand heldPostHand(LocalPlayer player) {
        if (isPost(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        return isPost(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }

    private static boolean isPost(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof WoodenPostBlock;
    }

    private static void showOutline(AABB box) {
        Microliner.get().showOutline(
                OUTLINE_ID,
                (poseStack, buffer, camera, transform, params) -> {
                    VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
                    forEachEdge(box, (from, to) -> MicrolinerOutline.renderLine(
                            poseStack,
                            consumer,
                            transform.transform(from).subtract(camera),
                            transform.transform(to).subtract(camera),
                            params.r(),
                            params.g(),
                            params.b(),
                            params.a()
                    ));
                },
                MicrolinerCoordinateTransform.IDENTITY,
                OUTLINE_STYLE
        );
    }

    private static void forEachEdge(AABB box, EdgeConsumer consumer) {
        Vec3 nwd = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 ned = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 swd = new Vec3(box.minX, box.minY, box.maxZ);
        Vec3 sed = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 nwu = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 neu = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 swu = new Vec3(box.minX, box.maxY, box.maxZ);
        Vec3 seu = new Vec3(box.maxX, box.maxY, box.maxZ);

        consumer.accept(nwd, ned);
        consumer.accept(ned, sed);
        consumer.accept(sed, swd);
        consumer.accept(swd, nwd);
        consumer.accept(nwu, neu);
        consumer.accept(neu, seu);
        consumer.accept(seu, swu);
        consumer.accept(swu, nwu);
        consumer.accept(nwd, nwu);
        consumer.accept(ned, neu);
        consumer.accept(swd, swu);
        consumer.accept(sed, seu);
    }

    @FunctionalInterface
    private interface EdgeConsumer {
        void accept(Vec3 from, Vec3 to);
    }
}
