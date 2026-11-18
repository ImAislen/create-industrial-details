package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.cake.struts.content.shape.CableStrutConnectionShape;
import com.cake.struts.internal.microliner.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class IndividualCableTargeting {
    private static CableSpans.Span outlined;
    private static java.util.List<CableStrutConnectionShape> outline;
    private static CableSpanIndex.Hit target() { return target(true); }
    private static CableSpanIndex.Hit target(boolean wrench) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || wrench && !CableSpanRemoval.isWrench(mc.player.getMainHandItem())) return null;
        var from = mc.player.getEyePosition(); var to = from.add(mc.player.getViewVector(1).scale(mc.player.blockInteractionRange()));
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS && from.distanceToSqr(mc.hitResult.getLocation()) < from.distanceToSqr(to)) to = mc.hitResult.getLocation();
        return CableSpanIndex.client(mc.level).trace(from, to);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock()) return;
        var hit = target(false); if (hit == null) return;
        var mc = Minecraft.getInstance(); var stack = new net.minecraft.world.item.ItemStack(hit.span().color().item());
        var inventory = mc.player.getInventory(); int slot = inventory.findSlotMatchingItem(stack);
        if (mc.player.isCreative()) {
            inventory.setPickedItem(stack);
            mc.gameMode.handleCreativeModeItemAdd(mc.player.getMainHandItem(), 36 + inventory.selected);
        } else if (slot != -1) {
            if (net.minecraft.world.entity.player.Inventory.isHotbarSlot(slot)) inventory.selected = slot;
            else mc.gameMode.handlePickItem(slot);
        }
        event.setCanceled(true); event.setSwingHand(false);
    }
    @SubscribeEvent public static void highlight(ClientTickEvent.Post event) {
        var hit = target();
        if (hit == null) { outlined = null; outline = null; return; }
        if (!hit.span().equals(outlined)) {
            outlined = hit.span();
            var a = CableSpanGeometry.from(outlined); var b = CableSpanGeometry.to(outlined);
            var points = CableSpanGeometry.points(outlined);
            int first = 1, last = points.size() - 2;
            var pieces = new java.util.ArrayList<CableStrutConnectionShape>();
            if (first != 0) pieces.add(new CableStrutConnectionShape(a, points.get(first), .75 / 16 + .006, .5 / 16 + .006, new com.cake.struts.content.CableStrutInfo(0)));
            pieces.add(new CableStrutConnectionShape(points.get(first), points.get(last), .75 / 16 + .006, .5 / 16 + .006, CableSpanGeometry.sag(outlined.color(), points.get(first).distanceTo(points.get(last)))));
            if (last != points.size() - 1) pieces.add(new CableStrutConnectionShape(points.get(last), b, .75 / 16 + .006, .5 / 16 + .006, new com.cake.struts.content.CableStrutInfo(0)));
            outline = java.util.List.copyOf(pieces);
        }
        var shape = outline;
        Microliner.get().showOutline("cid_cable_target", (poses, buffers, camera, transform, params) ->
                { for (var piece : shape) piece.drawOutline(poses, buffers.getBuffer(RenderType.lines()), camera, 0xe0e0e0, transform); },
                new MicrolinerParams(1 / 64f, .15f, .85f, .8f, .85f, 1));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void remove(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || !mc.player.isShiftKeyDown()) return;
        var hit = target(); if (hit == null) return;
        PacketDistributor.sendToServer(CableSpanRemoval.Request.of(hit.span()));
        event.setCanceled(true); event.setSwingHand(true);
    }
}
