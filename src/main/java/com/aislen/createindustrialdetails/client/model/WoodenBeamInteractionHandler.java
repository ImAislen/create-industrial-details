package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlock;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamConnectionEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamEndpoints;
import com.cake.struts.content.connection.GirderConnectionNode;
import com.cake.struts.content.shape.DefaultStrutConnectionShape;
import com.cake.struts.content.shape.StrutConnectionShape;
import com.cake.struts.content.shape.StrutInteractionHandler;
import com.cake.struts.content.structure.ConnectionKey;
import com.cake.struts.internal.util.LevelSafeStorage;
import com.cake.struts.network.BreakStrutPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Supplies endpoint-aware selection shapes to Struts' existing breaking flow. */
@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class WoodenBeamInteractionHandler {

    private static final LevelSafeStorage<Map<ConnectionKey, StrutConnectionShape>> SHAPES =
            new LevelSafeStorage<>((Supplier<Map<ConnectionKey, StrutConnectionShape>>) HashMap::new);
    private static ConnectionKey fallbackBreakingKey;
    private static BlockPos fallbackBreakPos;
    private static float fallbackBreakProgress;
    private static int fallbackBreakTicks;

    private WoodenBeamInteractionHandler() {
    }

    public static void update(Level level, WoodenBeamBlockEntity beam) {
        Map<ConnectionKey, StrutConnectionShape> shapes = SHAPES.getForLevel(level);
        BlockPos pos = beam.getBlockPos();
        shapes.keySet().removeIf(key -> key.a().equals(pos) || key.b().equals(pos));

        double halfWidth = beam.getModelType().shapeSizeXPixels() / 32.0;
        double halfHeight = beam.getModelType().shapeSizeYPixels() / 32.0;
        for (GirderConnectionNode connection : beam.getConnectionsCopy()) {
            BlockPos peer = connection.absoluteFrom(pos);
            WoodenBeamConnectionEndpoints endpoints = beam.getEndpoints(connection);
            WoodenBeamEndpoints.GeometrySpan geometrySpan = WoodenBeamEndpoints.geometrySpan(
                    level,
                    pos,
                    endpoints.localFace(),
                    endpoints.localSnap(),
                    peer,
                    endpoints.peerFace(),
                    endpoints.peerSnap()
            );
            shapes.put(new ConnectionKey(pos, peer), new DefaultStrutConnectionShape(
                    geometrySpan.from(),
                    geometrySpan.to(),
                    halfWidth,
                    halfHeight,
                    pos,
                    endpoints.localFace(),
                    peer,
                    endpoints.peerFace()
            ));
        }
    }

    public static void remove(Level level, BlockPos pos) {
        SHAPES.getForLevel(level).keySet().removeIf(key -> key.a().equals(pos) || key.b().equals(pos));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ClientLevel level = minecraft.level;
        Vec3 eye = minecraft.player.getEyePosition();
        double range = minecraft.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
        Vec3 target = eye.add(minecraft.player.getLookAngle().scale(range));

        ConnectionKey bestKey = null;
        StrutConnectionShape bestShape = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Map.Entry<ConnectionKey, StrutConnectionShape> entry : SHAPES.getForLevel(level).entrySet()) {
            Vec3 hit = entry.getValue().intersect(eye, target);
            if (hit == null) {
                continue;
            }
            double distance = eye.distanceToSqr(hit);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestKey = entry.getKey();
                bestShape = entry.getValue();
            }
        }

        ConnectionKey stockSelectedKey = StrutInteractionHandler.selectedKey;
        StrutConnectionShape stockSelectedShape = StrutInteractionHandler.selectedShape;
        if (bestKey != null && !isWoodenBeamSelection(level, stockSelectedKey) && stockSelectedShape != null) {
            Vec3 stockHit = stockSelectedShape.intersect(eye, target);
            if (stockHit != null && eye.distanceToSqr(stockHit) < bestDistance) {
                bestKey = null;
                bestShape = null;
            }
        }
        if (bestKey != null) {
            StrutInteractionHandler.selectedKey = bestKey;
            StrutInteractionHandler.selectedShape = bestShape;
            if (!bestKey.equals(stockSelectedKey)) {
                tickFallbackBreaking(minecraft, level, bestKey);
            } else {
                resetFallbackBreaking(level, minecraft);
            }
        } else if (isWoodenBeamSelection(level, StrutInteractionHandler.selectedKey)) {
            StrutInteractionHandler.selectedKey = null;
            StrutInteractionHandler.selectedShape = null;
            resetFallbackBreaking(level, minecraft);
        } else {
            resetFallbackBreaking(level, minecraft);
        }
    }

    private static void tickFallbackBreaking(Minecraft minecraft, ClientLevel level, ConnectionKey key) {
        if (!minecraft.options.keyAttack.isDown() || !minecraft.player.getAbilities().mayBuild) {
            resetFallbackBreaking(level, minecraft);
            return;
        }
        BlockPos breakPos = level.getBlockState(key.a()).getBlock() instanceof WoodenBeamBlock ? key.a() : key.b();
        if (!key.equals(fallbackBreakingKey) || !breakPos.equals(fallbackBreakPos)) {
            resetFallbackBreaking(level, minecraft);
            fallbackBreakingKey = key;
            fallbackBreakPos = breakPos;
        }

        BlockState state = level.getBlockState(breakPos);
        if (!(state.getBlock() instanceof WoodenBeamBlock)) {
            resetFallbackBreaking(level, minecraft);
            return;
        }
        if (fallbackBreakTicks % 4 == 0) {
            SoundType sound = state.getSoundType(level, breakPos, minecraft.player);
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                    sound.getHitSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 8.0F,
                    sound.getPitch() * 0.5F,
                    level.random,
                    breakPos
            ));
        }
        fallbackBreakTicks++;
        fallbackBreakProgress += minecraft.player.getAbilities().instabuild
                ? 1.0F
                : state.getDestroyProgress(minecraft.player, level, breakPos);
        int stage = Math.max(0, Math.min(9, (int) (fallbackBreakProgress * 10.0F) - 1));
        level.destroyBlockProgress(minecraft.player.getId(), breakPos, stage);
        if (fallbackBreakProgress >= 1.0F) {
            PacketDistributor.sendToServer(new BreakStrutPacket(key, false));
            resetFallbackBreaking(level, minecraft);
        }
    }

    private static void resetFallbackBreaking(ClientLevel level, Minecraft minecraft) {
        if (fallbackBreakPos != null && minecraft.player != null) {
            level.destroyBlockProgress(minecraft.player.getId(), fallbackBreakPos, -1);
        }
        fallbackBreakingKey = null;
        fallbackBreakPos = null;
        fallbackBreakProgress = 0.0F;
        fallbackBreakTicks = 0;
    }

    private static boolean isWoodenBeamSelection(ClientLevel level, ConnectionKey key) {
        return key != null
                && (level.getBlockState(key.a()).getBlock() instanceof WoodenBeamBlock
                || level.getBlockState(key.b()).getBlock() instanceof WoodenBeamBlock);
    }
}
