package com.aislen.createindustrialdetails.client;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class ControlBoxDoorTargeting {
    private ControlBoxDoorTargeting() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void targetDoor(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.hitResult == null) return;
        Vec3 start = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange();
        Vec3 end = start.add(mc.player.getViewVector(1).scale(reach));
        double obstruction = mc.hitResult.getType() == HitResult.Type.MISS ? reach * reach : start.distanceToSqr(mc.hitResult.getLocation());
        BlockPos existing = mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
        BlockHitResult nearest = null;
        double distance = reach * reach;
        BlockPos min = BlockPos.containing(Math.min(start.x, end.x) - 1, Math.min(start.y, end.y), Math.min(start.z, end.z) - 1);
        BlockPos max = BlockPos.containing(Math.max(start.x, end.x) + 1, Math.max(start.y, end.y), Math.max(start.z, end.z) + 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!mc.level.hasChunkAt(pos)) continue;
            var state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof ControlBoxBlock) || !state.getValue(ControlBoxBlock.OPEN)) continue;
            BlockHitResult hit = ControlBoxBlock.traceDoor(state, pos, start, end);
            if (hit == null) continue;
            double hitDistance = start.distanceToSqr(hit.getLocation());
            if (hitDistance > distance || !pos.equals(existing) && hitDistance > obstruction + 1e-6) continue;
            nearest = hit;
            distance = hitDistance;
        }
        if (nearest != null) mc.hitResult = nearest;
    }
}
