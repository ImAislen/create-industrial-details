package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableAttachmentGameTests {
    private static TestPlayer player(GameTestHelper h) {
        var p = new TestPlayer(h);
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return p;
    }
    private static final class TestPlayer extends FakePlayer {
        private String feedback = "";
        TestPlayer(GameTestHelper h) { super(h.getLevel(), new GameProfile(UUID.randomUUID(), "attachment-tests")); }
        @Override public void displayClientMessage(net.minecraft.network.chat.Component text, boolean actionBar) { feedback = text.getString(); }
    }
    private static InteractionResult click(FakePlayer p, CableEndpoint endpoint, BlockPos peer, IndividualCableColor color) {
        var point = endpoint.point(color, peer);
        var side = endpoint.port() == 0 ? Direction.UP : endpoint.outward(peer);
        p.setPos(point.x + 1, point.y + 1 - p.getEyeHeight(), point.z + 1);
        return p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(point, side, endpoint.pos(), false));
    }
    private static CableNetworkManager settle(GameTestHelper h) {
        var manager = CableNetworks.get(h.getLevel()); for (int i = 0; i < 256; i++) manager.processPending(); return manager;
    }
    private static CableEndpoint anchor(GameTestHelper h, BlockPos pos, int kind, boolean first, IndividualCableColor color) {
        var level = h.getLevel(); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        if (kind == 0) {
            level.setBlockAndUpdate(pos, ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(color.property, CableRoute.Z));
            return new CableEndpoint(pos, CableRoute.Z, 0);
        }
        if (kind == 1) {
            level.setBlockAndUpdate(pos, ModBlocks.CONTROL_BOX.get().defaultBlockState());
            ((ControlBoxBlockEntity) level.getBlockEntity(pos)).setSignal(color.channel, first ? 5 : 12);
            return new CableEndpoint(pos, CableRoute.NONE, ControlBoxPorts.encode(Direction.UP, Direction.SOUTH));
        }
        var state = ModBlocks.CABLE_TRAY.get().defaultBlockState().setValue(CableTrayBlock.AXIS, Direction.Axis.X).setValue(CableTrayBlock.FACING, Direction.EAST);
        level.setBlockAndUpdate(pos, state);
        return new CableEndpoint(pos, CableRoute.NONE, CableTrayPorts.encode(state, first ? Direction.EAST : Direction.WEST));
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void physicalPortCoordinatesAndAutomaticModels(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 20, 2)); var p = player(h);
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (var side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, facing));
            for (var side : List.of(Direction.UP, Direction.DOWN, facing.getOpposite())) for (var color : IndividualCableColor.values()) {
                int code = ControlBoxPorts.encode(side, facing); var endpoint = new CableEndpoint(pos, CableRoute.NONE, code);
                var model = ControlBoxGeometry.toModel(facing, endpoint.point(color, pos).subtract(Vec3.atLowerCornerOf(pos))).scale(16);
                h.assertTrue(Math.abs(model.x - (color.minX + .75)) < 1e-9 && (side.getAxis() == Direction.Axis.Y ? model.z == 2 : model.y == 2), "Actual black-square UV centers define every port");
                var stack = new ItemStack(color.item(), 8); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(click(p, endpoint, pos.relative(side), color).consumesAction() && stack.getCount() == 7, "Native port placement consumes exactly one cable");
                var peer = pos.relative(side); var host = (IndividualCableBlockEntity) level.getBlockEntity(peer); var route = host.getBlockState().getValue(color.property);
                h.assertTrue(CableConnectionGeometry.elevated(level, peer, color, route)
                        && CableConnectionGeometry.endpoint(level, peer, color, route, pos).distanceToSqr(endpoint.point(color, peer)) < 1e-12,
                        "Supplied elevated profile enters the actual port opening");
                h.assertTrue(host.componentShape().bounds().getSize() < 1, "Adjusted selection remains small");
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            for (var side : Direction.values()) if (level.getBlockEntity(pos.relative(side)) instanceof IndividualCableBlockEntity host)
                for (var color : IndividualCableColor.values()) for (var route : host.routes(color))
                    h.assertTrue(!CableConnectionGeometry.elevated(level, host.getBlockPos(), color, route), "Removing the enclosure restores normal cable geometry without orientation changes");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void trayTransitionsInEveryOrientation(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 20, 2)); var p = player(h);
        for (var facing : Direction.Plane.HORIZONTAL) for (var axis : Direction.Axis.values()) {
            for (var side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
            var state = ModBlocks.CABLE_TRAY.get().defaultBlockState().setValue(CableTrayBlock.AXIS, axis).setValue(CableTrayBlock.FACING, facing);
            level.setBlockAndUpdate(pos, state);
            for (var sign : Direction.AxisDirection.values()) for (var color : IndividualCableColor.values()) {
                var side = Direction.get(sign, axis); int code = CableTrayPorts.encode(state, side);
                var endpoint = new CableEndpoint(pos, CableRoute.NONE, code); var stack = new ItemStack(color.item(), 8); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(click(p, endpoint, pos.relative(side), color).consumesAction(), "Native tray-end placement chooses a compatible run orientation");
                var peer = pos.relative(side); var host = (IndividualCableBlockEntity) level.getBlockEntity(peer); var route = host.getBlockState().getValue(color.property);
                h.assertTrue(route.axis() == axis && CableConnectionGeometry.elevated(level, peer, color, route)
                        && CableConnectionGeometry.endpoint(level, peer, color, route, pos).distanceToSqr(endpoint.point(color, peer)) < 1e-12,
                        "Elevated model aligns exactly with each actual tray lane");
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            for (var side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeUniversalEndpointCombinations(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 20, 2)); var b = a.east(6); var p = player(h);
        for (var color : IndividualCableColor.values()) for (int kindA = 0; kindA < 3; kindA++) for (int kindB = 0; kindB < 3; kindB++) {
            var first = anchor(h, a, kindA, true, color); var last = anchor(h, b, kindB, false, color);
            var stack = new ItemStack(color.item(), 32); p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.setShiftKeyDown(true);
            h.assertTrue(click(p, first, b, color).consumesAction() && IndividualCableItem.pendingEndpoint(stack, level).equals(first), "Shift-first selects the explicit component type, with the box closed");
            p.setShiftKeyDown(false);
            h.assertTrue(click(p, last, a, color).consumesAction(), "Normal second click completes " + color + " " + kindA + " -> " + kindB + ": " + p.feedback);
            var ledger = CableSpans.get(level); var span = ledger.at(a).getFirst();
            h.assertTrue(stack.getCount() == 26 && !stack.has(ModDataComponents.CABLE_FROM.get()), "One charge and pending cleanup, with no unwanted straight placement");
            h.assertTrue(span.start().equals(first) && span.end().equals(last) && CableSpans.Span.read(span.write()).equals(span), "Explicit endpoint identities persist without ambiguity");
            var points = CableSpanGeometry.points(span);
            h.assertTrue(points.getFirst().equals(first.point(color, b)) && points.getLast().equals(last.point(color, a)), "Preview/render/target path retains exact physical ends");
            h.assertTrue(points.get(1).subtract(points.getFirst()).normalize().dot(Vec3.atLowerCornerOf(first.outward(b).getNormal())) > .99999, "First mesh section meets its anchor squarely");
            int expected = kindB == 1 ? 12 : kindA == 1 ? 5 : 0;
            h.assertTrue(settle(h).read(span).signal(color.channel) == expected, "Every endpoint type participates in the same existing signal graph");
            if (kindA == 2 || kindB == 2) h.assertTrue(ledger.traySpans(new net.minecraft.world.level.ChunkPos(kindA == 2 ? a : b)).contains(span), "Tray chunk synchronization indexes the actual record");
            ledger.remove(span); h.assertTrue(ledger.at(a).isEmpty(), "Removing one span preserves both anchor blocks");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exposedTrayLanesAndSnapshotCodec(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 20, 2)); var p = player(h);
        var endpoint = anchor(h, pos, 2, true, IndividualCableColor.RED); var peer = pos.east(6); var other = anchor(h, peer, 2, false, IndividualCableColor.RED);
        var point = endpoint.point(IndividualCableColor.RED, peer);
        h.assertTrue(CableTrayPorts.target(level, pos, Direction.EAST, point, IndividualCableColor.YELLOW) == 0
                && CableTrayPorts.target(level, pos, Direction.UP, point, IndividualCableColor.RED) == 0, "Wrong colors and metal casing faces are never lane endpoints");
        var ledger = CableSpans.get(level); h.assertTrue(ledger.connect(IndividualCableColor.RED, endpoint, other, 6), "Exposed tray ends support spans");
        var span = ledger.at(pos).getFirst(); var chunk = new net.minecraft.world.level.ChunkPos(pos);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            var packet = new CableSpanSync.Snapshot(chunk, ledger.traySpans(chunk)); CableSpanSync.Snapshot.CODEC.encode(buffer, packet);
            h.assertTrue(CableSpanSync.Snapshot.CODEC.decode(buffer).equals(packet), "Chunk snapshot wire format preserves typed endpoints and color");
        } finally { buffer.release(); }
        level.setBlockAndUpdate(pos.east(), ModBlocks.CABLE_TRAY.get().defaultBlockState().setValue(CableTrayBlock.AXIS, Direction.Axis.X));
        h.assertTrue(!endpoint.exists(level, IndividualCableColor.RED) && ledger.at(pos).isEmpty(), "Extending a tray closes its old exposed end and removes the dependent span");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void traySpanChannelIsolationAndDirectRemoval(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 20, 2)); var b = a.east(6);
        for (var pos : List.of(a, b)) {
            level.setBlockAndUpdate(pos, ModBlocks.CABLE_TRAY.get().defaultBlockState());
            level.setBlockAndUpdate(pos.north(), ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, Direction.NORTH));
        }
        var first = (ControlBoxBlockEntity) level.getBlockEntity(a.north()); var last = (ControlBoxBlockEntity) level.getBlockEntity(b.north());
        for (var channel : ControlChannel.values()) { first.setSignal(channel, channel == ControlChannel.RED ? 5 : 12); last.setSignal(channel, channel == ControlChannel.RED ? 10 : 3); }
        var from = new CableEndpoint(a, CableRoute.NONE, CableTrayPorts.encode(level.getBlockState(a), Direction.SOUTH));
        var to = new CableEndpoint(b, CableRoute.NONE, CableTrayPorts.encode(level.getBlockState(b), Direction.SOUTH));
        var ledger = CableSpans.get(level); h.assertTrue(ledger.connect(IndividualCableColor.RED, from, to, 6), "Tray-to-tray Red span connects directly");
        var manager = settle(h);
        h.assertTrue(manager.read(a).signal(ControlChannel.RED) == 10 && manager.read(a).signal(ControlChannel.YELLOW) == 12
                && manager.read(b).signal(ControlChannel.YELLOW) == 3, "Only the matching channel merges; no other tray channels cross the span");
        last.setSignal(ControlChannel.RED, 4); h.assertTrue(manager.read(a).signal(ControlChannel.RED) == 5 && first.getComparatorOutput() == 12, "Source changes refresh cached graph values while comparators remain local");
        var span = ledger.at(a).getFirst(); var points = CableSpanGeometry.points(span); var middle = points.get(points.size() / 2); var p = player(h);
        p.setPos(middle.x, middle.y + 1.5 - p.getEyeHeight(), middle.z); p.setXRot(90); p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
        int before = p.getInventory().countItem(IndividualCableColor.RED.item());
        h.assertTrue(CableSpanRemoval.remove(p, CableSpanRemoval.Request.of(span)) && !CableSpanRemoval.remove(p, CableSpanRemoval.Request.of(span)), "Direct tray-span removal is ray-validated and replay safe");
        h.assertTrue(p.getInventory().countItem(IndividualCableColor.RED.item()) == before + 6 && level.getBlockState(a).is(ModBlocks.CABLE_TRAY.get())
                && level.getBlockState(b).is(ModBlocks.CABLE_TRAY.get()), "One refund leaves both trays intact");
        settle(h); h.assertTrue(manager.read(a).signal(ControlChannel.RED) == 5 && manager.read(b).signal(ControlChannel.RED) == 4, "Span removal splits only its channel");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sharedTrayLanesChunkLifecycleAndRotation(GameTestHelper h) {
        var level = h.getLevel(); var seed = h.absolutePos(new BlockPos(2, 25, 2));
        var a = new BlockPos((seed.getX() >> 4) * 16 + 15, seed.getY(), seed.getZ()); var b = a.east(6);
        level.getChunkAt(b);
        for (var pos : List.of(a, b)) {
            level.setBlockAndUpdate(pos, ModBlocks.CABLE_TRAY.get().defaultBlockState());
            level.setBlockAndUpdate(pos.north(), ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, Direction.NORTH));
            var box = (ControlBoxBlockEntity) level.getBlockEntity(pos.north());
            for (var color : IndividualCableColor.values()) box.setSignal(color.channel, pos.equals(a) ? 5 : 14);
        }
        var from = new CableEndpoint(a, CableRoute.NONE, CableTrayPorts.encode(level.getBlockState(a), Direction.SOUTH));
        var to = new CableEndpoint(b, CableRoute.NONE, CableTrayPorts.encode(level.getBlockState(b), Direction.SOUTH));
        var ledger = CableSpans.get(level);
        for (var color : IndividualCableColor.values()) h.assertTrue(ledger.connect(color, from, to, 6), "Each color independently shares the same two tray anchors");
        var manager = settle(h);
        for (var color : IndividualCableColor.values()) h.assertTrue(manager.read(a, color).signal(color.channel) == 14, "Cross-chunk tray span transmits its own source maximum");
        var chunk = level.getChunkAt(b);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk)); settle(h);
        for (var color : IndividualCableColor.values()) h.assertTrue(manager.read(a, color).signal(color.channel) == 5, "Unloaded peer contributes no stale signal");
        h.assertTrue(ledger.at(a).size() == 4, "Unloading a peer preserves saved span records");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false)); settle(h);
        for (var color : IndividualCableColor.values()) h.assertTrue(manager.read(a, color).signal(color.channel) == 14, "Reload restores each existing span's channel");
        var red = ledger.at(a).stream().filter(s -> s.color() == IndividualCableColor.RED).findFirst().orElseThrow();
        ledger.remove(red); settle(h);
        h.assertTrue(ledger.at(a).size() == 3 && manager.read(a, IndividualCableColor.RED).signal(ControlChannel.RED) == 5
                && manager.read(a, IndividualCableColor.YELLOW).signal(ControlChannel.YELLOW) == 14, "Removing Red preserves other colored spans and signals");
        level.setBlockAndUpdate(b, level.getBlockState(b).setValue(CableTrayBlock.AXIS, Direction.Axis.X));
        h.assertTrue(ledger.at(a).isEmpty() && ledger.traySpans(new net.minecraft.world.level.ChunkPos(a)).isEmpty(), "Rotation withdraws old physical endpoints and their client snapshot records");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void adjustedStraightSpanEndRemainsSharedAndPersistent(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 30, 2)); var b = a.south(6);
        for (var color : IndividualCableColor.values()) {
            var first = anchor(h, a, 0, true, color); var last = anchor(h, b, 0, false, color);
            var ledger = CableSpans.get(level); h.assertTrue(ledger.connect(color, first, last, 6), "Normal straight endpoints create their existing span");
            var before = ledger.at(a).getFirst();
            level.setBlockAndUpdate(a.south(), ModBlocks.CABLE_TRAY.get().defaultBlockState());
            var adjusted = ledger.at(a).getFirst();
            h.assertTrue(adjusted.key().equals(before.key()) && adjusted.fromOffset().equals(new Vec3(0, 1.5 / 16, 0)), "Connection elevation changes geometry, never the stable endpoint identity");
            h.assertTrue(CableSpanGeometry.from(adjusted).equals(first.point(level, color, b))
                    && CableSpanGeometry.from(adjusted).equals(CableConnectionGeometry.endpoint(level, a, color, CableRoute.Z, b)),
                    "Straight transition and span use the exact same adjusted attachment");
            h.assertTrue(CableSpans.Span.read(adjusted.write()).equals(adjusted) && !ledger.connect(color, first, last, 6), "Offsets persist and cannot bypass duplicate rejection");
            var index = new CableSpanIndex(); index.replace(a, List.of(before)); index.replace(b, List.of(before));
            index.replace(a, List.of(adjusted)); index.replace(b, List.of(adjusted));
            h.assertTrue(index.contains(before) && index.containsRecord(adjusted) && !index.containsRecord(before), "Endpoint packet owners replace old geometry without losing stable-key lookup");
            level.setBlockAndUpdate(a.south(), Blocks.AIR.defaultBlockState());
            h.assertTrue(ledger.at(a).getFirst().equals(before), "Removing the destination restores the original height and saved attachment");
            ledger.remove(ledger.at(a).getFirst());
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void elevatedVariantsAndCompactSteps(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 35, 2));
        for (var facing : Direction.Plane.HORIZONTAL) for (var color : IndividualCableColor.values()) {
            var route = CableRoute.floor(facing); var axis = route.axis();
            var toward = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            var regular = pos.relative(toward.getOpposite()); var tray = pos.relative(toward);
            level.setBlockAndUpdate(tray, Blocks.AIR.defaultBlockState());
            var hostState = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(color.property, route);
            var other = IndividualCableColor.values()[(color.ordinal() + 1) % 4];
            level.setBlockAndUpdate(pos, hostState.setValue(other.property, CableRoute.floor(facing.getOpposite())));
            level.setBlockAndUpdate(regular, hostState);
            h.assertTrue(!CableConnectionGeometry.elevated(level, pos, color, route), "Ordinary installation stays regular");
            level.setBlockAndUpdate(tray, ModBlocks.CABLE_TRAY.get().defaultBlockState().setValue(CableTrayBlock.AXIS, axis).setValue(CableTrayBlock.FACING, facing));
            h.assertTrue(CableConnectionGeometry.elevated(level, pos, color, route)
                    && !CableConnectionGeometry.elevated(level, pos, other, CableRoute.floor(facing.getOpposite()))
                    && !CableConnectionGeometry.elevated(level, regular, color, route), "Variants are independently derived per component, without neighbor feedback");
            var pieces = CableConnectionGeometry.steps(level, regular, color, route);
            h.assertTrue(!pieces.isEmpty() && pieces.size() <= 3, "Height difference uses only compact rectangular infill");
            var own = IndividualCableGeometry.part(color, route).bounds().move(regular);
            var raised = CableConnectionGeometry.elevatedPart(color, route).bounds().move(pos);
            for (var box : pieces) {
                double along = axis == Direction.Axis.X ? box.getXsize() : box.getZsize();
                h.assertTrue(along <= 1.0 / 16 + 1e-9 && box.getYsize() <= 2.5 / 16 + 1e-9, "Connector occupies one pixel across the joint, with no curved ramp");
                h.assertTrue(!box.intersects(own) && !box.intersects(raised), "Existing component volumes are subtracted from infill");
            }
            var manager = settle(h);
            h.assertTrue(manager.read(pos, color).id() == manager.read(regular, color).id(), "Model variants remain the same electrical route");
            level.setBlockAndUpdate(tray, Blocks.AIR.defaultBlockState());
            h.assertTrue(!CableConnectionGeometry.elevated(level, pos, color, route) && CableConnectionGeometry.steps(level, regular, color, route).isEmpty(), "Removing the connection clears the variant and its step");
            level.setBlockAndUpdate(regular, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

}
