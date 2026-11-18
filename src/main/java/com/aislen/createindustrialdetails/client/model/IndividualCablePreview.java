package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.cake.struts.content.block.StrutBlock;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class IndividualCablePreview {
    private record Key(IndividualCableColor color, Vec3 from, Vec3 to, int fromPort, int toPort, CableSpans.Span span) {}
    private static Key key;
    private static Vec3 origin = Vec3.ZERO;
    private static List<BakedQuad> quads = List.of();
    private static boolean valid;
    private static CableSpans.Span snapped;
    public static void reload() { key = null; quads = List.of(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) { reload(); return; }
        var stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof IndividualCableItem)) stack = mc.player.getOffhandItem();
        if (!(stack.getItem() instanceof IndividualCableItem item)) { reload(); return; }
        var selection = stack.get(ModDataComponents.CABLE_FROM.get());
        if (selection == null || !selection.dimension().equals(mc.level.dimension()) || !mc.level.hasChunkAt(selection.pos())) { reload(); return; }
        var start = IndividualCableItem.pendingEndpoint(stack, mc.level);
        if (start == null || !start.exists(mc.level, item.color) || start.token(mc.level, item.color) != stack.getOrDefault(ModDataComponents.CABLE_FROM_TOKEN.get(), 0L)) { reload(); return; }
        Vec3 target = mc.hitResult == null ? mc.player.getEyePosition().add(mc.player.getViewVector(1).scale(mc.player.blockInteractionRange())) : mc.hitResult.getLocation();
        BlockPos pos = mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : BlockPos.containing(target);
        var from = start.point(mc.level, item.color, pos);
        var to = new Vec3(Math.rint(target.x * 32) / 32, Math.rint(target.y * 32) / 32, Math.rint(target.z * 32) / 32);
        valid = false; snapped = null; int fromPort = start.port(), toPort = 0;
        if (mc.hitResult instanceof BlockHitResult blockHit) {
            var end = CableEndpoint.target(mc.level, pos, blockHit.getDirection(), target, mc.player.getEyePosition(), mc.player.blockInteractionRange(), item.color);
            if (end != null) {
                int cost = Math.max(1, (int) Math.ceil(Math.sqrt(pos.distSqr(start.pos()))));
                var span = CableSpans.Span.between(mc.level, item.color, start, end, cost);
                valid = !start.pos().equals(pos) && start.pos().distSqr(pos) <= StrutBlock.MAX_SPAN * StrutBlock.MAX_SPAN
                        && CableSpanGeometry.clearEnclosures(span) && !CableSpanIndex.client(mc.level).contains(span) && (mc.player.isCreative() || stack.getCount() >= cost)
                        && mc.player.mayBuild() && mc.level.mayInteract(mc.player, pos) && mc.player.mayUseItemAt(pos, blockHit.getDirection(), stack);
                from = CableSpanGeometry.from(span); to = CableSpanGeometry.to(span); fromPort = span.fromPort(); toPort = span.toPort();
                snapped = span;
            }
        }
        if (from.distanceToSqr(to) > (StrutBlock.MAX_SPAN + 2) * (StrutBlock.MAX_SPAN + 2)) to = from.add(to.subtract(from).normalize().scale(StrutBlock.MAX_SPAN + 2));
        if (from.distanceToSqr(to) < 1e-8) { reload(); return; }
        var next = new Key(item.color, from, to, fromPort, toPort, snapped);
        if (!next.equals(key)) {
            key = next; origin = Vec3.atLowerCornerOf(BlockPos.containing(from));
            quads = snapped == null ? IndividualCableRenderer.ghost(item.color, CableSpanGeometry.preview(mc.level, start, item.color, pos, to).stream().map(p -> p.subtract(origin)).toList()) : IndividualCableRenderer.ghost(snapped, origin);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || key == null || quads.isEmpty()) return;
        var mc = Minecraft.getInstance(); if (mc.level == null) return;
        var poses = event.getPoseStack(); var camera = event.getCamera().getPosition();
        poses.pushPose(); poses.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        var buffers = mc.renderBuffers().bufferSource(); var vertices = buffers.getBuffer(RenderType.translucent());
        var lightPos = BlockPos.containing(key.from);
        int light = mc.level.hasChunkAt(lightPos) ? LevelRenderer.getLightColor(mc.level, lightPos) : LightTexture.FULL_BRIGHT;
        for (var quad : quads) vertices.putBulkData(poses.last(), quad, 1, valid ? 1 : .25f, valid ? 1 : .25f, .4f, light, OverlayTexture.NO_OVERLAY, true);
        poses.popPose(); buffers.endBatch(RenderType.translucent());
    }
}
