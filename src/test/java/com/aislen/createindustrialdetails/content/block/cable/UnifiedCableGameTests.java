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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class UnifiedCableGameTests {
    private static CableNetworkManager settle(GameTestHelper h) { var m = CableNetworks.get(h.getLevel()); for (int i = 0; i < 256; i++) m.processPending(); return m; }
    private static ControlBoxBlockEntity box(GameTestHelper h, BlockPos pos, Direction facing, int... values) {
        h.getLevel().setBlockAndUpdate(pos, ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, facing));
        var box = (ControlBoxBlockEntity) h.getLevel().getBlockEntity(pos);
        for (var color : IndividualCableColor.values()) box.setSignal(color.channel, values[color.ordinal()]); return box;
    }
    private static BlockState host(CableRoute route, IndividualCableColor... colors) {
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState(); for (var color : colors) state = state.setValue(color.property, route); return state;
    }
    private static BlockState tray() { return ModBlocks.CABLE_TRAY.get().defaultBlockState(); }
    private static FakePlayer player(GameTestHelper h) { var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "unified-cables")); p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return p; }
    private static InteractionResult click(FakePlayer p, BlockPos pos, Direction side, Vec3 hit) {
        return p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, side, pos, false));
    }
    private static CableRoute aligned(BlockPos pos, Vec3 point, Direction direction, IndividualCableColor color) {
        for (var route : CableRoute.values()) {
            if (route == CableRoute.NONE || route.axis() != direction.getAxis()) continue;
            var delta = IndividualCableGeometry.anchor(pos, color, route).subtract(point);
            double axial = direction.getAxis().choose(delta.x, delta.y, delta.z);
            double lateral = delta.lengthSqr() - axial * axial;
            if (lateral < 1e-10 || Math.abs(lateral - Math.pow(1.5 / 16, 2)) < 1e-10) return route;
        }
        throw new AssertionError("No physically aligned route");
    }
    private static void signals(GameTestHelper h, CableNetworkManager.NetworkState state, int... expected) {
        for (var channel : ControlChannel.values()) h.assertTrue(state.signal(channel) == expected[channel.ordinal()], "Expected " + Arrays.toString(expected) + " got " + state);
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twelvePortsAndNativeStraightAttachment(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 4, 3)); var p = player(h);
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (var side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
            var box = box(h, pos, facing, 12, 4, 9, 15);
            int ports = 0;
            for (var side : Direction.values()) for (var color : IndividualCableColor.values()) {
                int code = ControlBoxPorts.encode(side, facing);
                boolean valid = side.getAxis() == Direction.Axis.Y || side == facing.getOpposite();
                h.assertTrue(ControlBoxPorts.valid(box.getBlockState(), code) == valid, "Only top, bottom and back expose four ports");
                if (!valid) continue; ports++;
                var point = ControlBoxPorts.point(pos, code, color);
                h.assertTrue(ControlBoxPorts.target(box.getBlockState(), pos, side, point, color) == code, "Port hit region maps face and color");
                for (var wrong : IndividualCableColor.values()) if (wrong != color)
                    h.assertTrue(ControlBoxPorts.target(box.getBlockState(), pos, side, point, wrong) == 0, "Another color cannot target this port");
                var local = ControlBoxGeometry.toModel(facing, point.subtract(Vec3.atLowerCornerOf(pos)));
                h.assertTrue(Math.abs(local.x - (color.minX + .75) / 16) < 1e-10, "Rotations preserve the physical color lane");
                var wrongStack = new ItemStack(IndividualCableColor.values()[(color.ordinal() + 1) % 4].item(), 8);
                p.setItemInHand(InteractionHand.MAIN_HAND, wrongStack);
                h.assertTrue(click(p, pos, side, point) == InteractionResult.FAIL && wrongStack.getCount() == 8
                        && !box.getBlockState().getValue(ControlBoxBlock.OPEN), "Wrong-color port interaction is rejected without placement or door changes");
                var stack = new ItemStack(color.item(), 8); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(click(p, pos, side, point).consumesAction() && stack.getCount() == 7, "Normal item interaction places one adjacent segment at its port");
                var peer = pos.relative(side); var state = level.getBlockState(peer); var route = state.getValue(color.property);
                h.assertTrue(CablePhysicalConnections.contacts(level, peer, state, color, route).stream().anyMatch(c -> c.port() == code && c.peer().equals(pos)), "Straight contact is geometrically exact");
                var manager = settle(h);
                h.assertTrue(manager.read(peer, color).signal(color.channel) == box.getSignal(color.channel), "Matching source channel enters individual wire");
                h.assertTrue(!box.getBlockState().getValue(ControlBoxBlock.OPEN), "Port clicks never open the closed door");
            }
            h.assertTrue(ports == 12, "All twelve physical ports tested");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativePortSpansBothDirectionsAndPersistence(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 4, 3)); var p = player(h);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var box = box(h, pos, facing, 12, 4, 9, 15);
            for (var side : List.of(Direction.UP, Direction.DOWN, facing.getOpposite())) {
                var peer = pos.relative(side, 5); level.setBlockAndUpdate(peer, Blocks.AIR.defaultBlockState());
                for (var color : IndividualCableColor.values()) {
                    int port = ControlBoxPorts.encode(side, facing); var point = ControlBoxPorts.point(pos, port, color);
                    var route = aligned(peer, point, side, color);
                    var state = level.getBlockState(peer); if (!(state.getBlock() instanceof IndividualCableBlock)) state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
                    level.setBlockAndUpdate(peer, state.setValue(color.property, route));
                    for (boolean portFirst : new boolean[]{true, false}) {
                        var stack = new ItemStack(color.item(), 32); p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.setShiftKeyDown(true);
                        var anchor = IndividualCableGeometry.anchor(peer, color, route);
                        p.setPos(anchor.x + 1, anchor.y - p.getEyeHeight(), anchor.z);
                        h.assertTrue(click(p, portFirst ? pos : peer, portFirst ? side : Direction.UP, portFirst ? point : anchor).consumesAction(), "Both endpoint types can be selected first");
                        p.setShiftKeyDown(false);
                        h.assertTrue(click(p, portFirst ? peer : pos, portFirst ? Direction.UP : side, portFirst ? anchor : point).consumesAction(), "Normal second click completes a typed port span");
                        h.assertTrue(stack.getCount() == 27 && !stack.has(ModDataComponents.CABLE_FROM.get()), "Span cost is paid once and preview state clears");
                        var ledger = CableSpans.get(level); var span = ledger.at(peer).stream().filter(s -> s.color() == color).findFirst().orElseThrow();
                        var end = span.start().isPort() ? span.start() : span.end();
                        h.assertTrue(end.pos().equals(pos) && end.port() == port && end.point(color, peer).equals(point), "Port identity and actual geometry are stored explicitly");
                        h.assertTrue(CableSpans.Span.read(span.write()).equals(span), "Extended records round trip without altering legacy fields");
                        h.assertTrue(settle(h).read(peer, color).signal(color.channel) == box.getSignal(color.channel), "Port span transmits its single channel");
                        var update = box.getUpdateTag(level.registryAccess()); h.assertTrue(update.getList("VisibleSpans", 10).size() == 1, "Port-side renderer receives the span through the existing block entity packet");
                        h.assertTrue(ledger.remove(span) && level.getBlockState(pos).getBlock() instanceof ControlBoxBlock, "Span removal preserves the source box");
                    }
                }
                level.destroyBlock(peer, false);
            }
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void redBridgeMergesOnlyRedTrayChannels(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 4, 2)); var b = a.east(6);
        var first = box(h, a, Direction.NORTH, 5, 12, 0, 7); var last = box(h, b, Direction.NORTH, 10, 3, 15, 2);
        level.setBlockAndUpdate(a.south(), tray()); level.setBlockAndUpdate(b.south(), tray());
        level.setBlockAndUpdate(a.south(2), host(CableRoute.Z, IndividualCableColor.RED)); level.setBlockAndUpdate(b.south(2), host(CableRoute.Z, IndividualCableColor.RED));
        var manager = settle(h); signals(h, manager.read(a.south()), 5, 12, 0, 7); signals(h, manager.read(b.south()), 10, 3, 15, 2);
        var ledger = CableSpans.get(level); h.assertTrue(ledger.connect(IndividualCableColor.RED, a.south(2), b.south(2), 6), "Individual Red span links two separate trays");
        settle(h); signals(h, manager.read(a.south()), 10, 12, 0, 7); signals(h, manager.read(b.south()), 10, 3, 15, 2);
        h.assertTrue(manager.read(a.south(), IndividualCableColor.RED).id() == manager.read(b.south(), IndividualCableColor.RED).id()
                && manager.read(a.south(), IndividualCableColor.YELLOW).id() != manager.read(b.south(), IndividualCableColor.YELLOW).id(), "Connectivity is channel-specific, not a whole-tray merge");
        var diagnostic = manager.inspect(a.south(2), IndividualCableColor.RED);
        h.assertTrue(diagnostic.hasSource() && diagnostic.hasTray() && diagnostic.hasControlBox()
                && CableNetworkInspection.describeChannel(IndividualCableColor.RED, diagnostic).getString().contains("source=true tray=true control_box_port=true"), "Diagnostics report actual cached source and tray membership");
        var host = (IndividualCableBlockEntity) level.getBlockEntity(a.south(2)); int revision = host.revision;
        last.setSignal(ControlChannel.RED, 4); signals(h, manager.read(a.south()), 5, 12, 0, 7); signals(h, manager.read(b.south()), 5, 3, 15, 2);
        h.assertTrue(host.revision == revision, "Dial changes do not rebuild span render geometry");
        h.assertTrue(first.getComparatorOutput() == 12 && last.getComparatorOutput() == 15, "Comparators remain local");
        level.destroyBlock(b, false); settle(h); signals(h, manager.read(b.south()), 5, 0, 0, 0);
        ledger.remove(ledger.at(a.south(2)).getFirst()); settle(h); signals(h, manager.read(b.south()), 0, 0, 0, 0);
        signals(h, manager.read(a.south()), 5, 12, 0, 7);
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sourcePortsNeverRelayReceivedSignals(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 4, 3));
        box(h, pos, Direction.SOUTH, 5, 0, 0, 0);
        level.setBlockAndUpdate(pos.above(), host(CableRoute.Y, IndividualCableColor.RED));
        level.setBlockAndUpdate(pos.below(), host(CableRoute.Y, IndividualCableColor.RED));
        level.setBlockAndUpdate(pos.north(), host(CableRoute.Z, IndividualCableColor.RED));
        var remote = pos.east(5); box(h, remote, Direction.SOUTH, 15, 0, 0, 0);
        var ledger = CableSpans.get(level);
        var port = new CableEndpoint(remote, CableRoute.NONE, ControlBoxPorts.encode(Direction.UP, Direction.SOUTH));
        int cost = (int) Math.ceil(Math.sqrt(remote.distSqr(pos.above())));
        h.assertTrue(ledger.connect(IndividualCableColor.RED, new CableEndpoint(pos.above(), CableRoute.Y, 0), port, cost), "Remote source drives one Control Box port's separate network");
        var manager = settle(h);
        h.assertTrue(manager.read(pos.above(), IndividualCableColor.RED).signal(ControlChannel.RED) == 15
                && manager.read(pos.below(), IndividualCableColor.RED).signal(ControlChannel.RED) == 5
                && manager.read(pos.north(), IndividualCableColor.RED).signal(ControlChannel.RED) == 5, "Source box supplies its values without bridging separate networks");
        level.destroyBlock(remote, false); settle(h);
        h.assertTrue(ledger.at(pos.above()).isEmpty() && manager.read(pos.above(), IndividualCableColor.RED).signal(ControlChannel.RED) == 5, "Removing a source box clears port spans and withdraws its contribution");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void portToPortLeadsRemovalAndRotation(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 4, 2)); var b = a.east(6);
        box(h, a, Direction.SOUTH, 0, 0, 7, 0); box(h, b, Direction.SOUTH, 0, 0, 13, 0);
        var color = IndividualCableColor.GREEN; int code = ControlBoxPorts.encode(Direction.UP, Direction.SOUTH);
        var first = new CableEndpoint(a, CableRoute.NONE, code); var last = new CableEndpoint(b, CableRoute.NONE, code);
        var p = player(h); var stack = new ItemStack(color.item(), 32); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.setShiftKeyDown(true); click(p, a, Direction.UP, first.point(color, b)); p.setShiftKeyDown(false);
        h.assertTrue(click(p, b, Direction.UP, last.point(color, a)).consumesAction(), "Two explicit box ports complete a native cable connection");
        var ledger = CableSpans.get(level); var span = ledger.at(a).getFirst(); var points = CableSpanGeometry.points(span);
        h.assertTrue(span.start().isPort() && span.end().isPort() && CableSpanGeometry.clearEnclosures(span), "Port-to-port curve stays outside both enclosures");
        h.assertTrue(points.get(1).y > points.getFirst().y && points.get(points.size() - 2).y > points.getLast().y, "Top ports have outward lead geometry");
        h.assertTrue(settle(h).read(span).signal(ControlChannel.GREEN) == 13, "Port-to-port network uses the highest source");
        var middle = points.get(points.size() / 2); p.setPos(middle.x, middle.y + 1.5 - p.getEyeHeight(), middle.z); p.setXRot(90); p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
        int before = p.getInventory().countItem(color.item());
        h.assertTrue(CableSpanRemoval.remove(p, CableSpanRemoval.Request.of(span)) && !CableSpanRemoval.remove(p, CableSpanRemoval.Request.of(span)), "Direct port-span removal is validated and replay safe");
        h.assertTrue(p.getInventory().countItem(color.item()) == before + 6 && level.getBlockState(a).is(ModBlocks.CONTROL_BOX.get()) && level.getBlockState(b).is(ModBlocks.CONTROL_BOX.get()), "One refund leaves both Control Boxes intact");
        h.assertTrue(ledger.connect(color, first, last, 6), "Port span reconnects");
        level.setBlockAndUpdate(b, level.getBlockState(b).setValue(ControlBoxBlock.FACING, Direction.NORTH));
        h.assertTrue(ledger.at(a).isEmpty(), "Box rotation invalidates old physical port attachments");
        box(h, b, Direction.SOUTH, 0, 0, 13, 0); p.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(color.item())); p.setShiftKeyDown(true); click(p, a, Direction.UP, first.point(color, b));
        p.setShiftKeyDown(false); click(p, b, Direction.UP, last.point(color, a));
        h.assertTrue(p.getMainHandItem().getCount() == 1 && ledger.at(a).size() == 1, "Creative port span does not consume held material");
        var state = level.getBlockState(b); state.getBlock().playerWillDestroy(level, b, state, p); level.destroyBlock(b, false);
        h.assertTrue(ledger.at(a).isEmpty(), "Creative source destruction clears its spans");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void individualCornersLoopsSplitsAndReconnection(GameTestHelper h) {
        var level = h.getLevel(); var root = h.absolutePos(new BlockPos(3, 4, 3));
        for (var facing : Direction.Plane.HORIZONTAL) for (var color : IndividualCableColor.values()) {
            var first = root.above(); var turn = root.above(2); var middle = turn.relative(facing.getOpposite()); var end = turn.relative(facing.getOpposite(), 2);
            for (var pos : BlockPos.betweenClosed(root.offset(-2, 1, -2), root.offset(2, 2, 2))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(root, Blocks.AIR.defaultBlockState());
            var box = box(h, root, facing, 11, 7, 3, 15); var vertical = CableRoute.wall(facing); var floor = CableRoute.floor(facing.getOpposite());
            level.setBlockAndUpdate(first, host(vertical, color)); level.setBlockAndUpdate(turn, host(vertical, color));
            h.assertTrue(((IndividualCableBlockEntity) level.getBlockEntity(turn)).add(color, floor), "Physical same-color L component joins the route");
            level.setBlockAndUpdate(middle, host(floor, color)); level.setBlockAndUpdate(end, host(floor, color)); var manager = settle(h);
            h.assertTrue(manager.read(end, color).signal(color.channel) == box.getSignal(color.channel), "Straight, vertical and paired corner transmission retains source strength");
            var ledger = CableSpans.get(level); int cost = (int) Math.ceil(Math.sqrt(first.distSqr(end)));
            h.assertTrue(ledger.connect(color, first, end, vertical, floor, cost), "Redundant sagging edge creates a supported loop");
            settle(h); level.destroyBlock(middle, false); settle(h);
            h.assertTrue(manager.read(end, color).signal(color.channel) == box.getSignal(color.channel), "Loop's remaining span preserves conductivity after the straight route is cut");
            ledger.remove(ledger.at(first).stream().filter(s -> s.color() == color).findFirst().orElseThrow()); settle(h);
            h.assertTrue(manager.read(end, color).signal(color.channel) == 0, "Removing final conductive path splits the channel");
            level.setBlockAndUpdate(middle, host(floor, color)); settle(h);
            h.assertTrue(manager.read(end, color).signal(color.channel) == box.getSignal(color.channel), "Restored components merge the correct channel");
            level.destroyBlock(root, false); settle(h); h.assertTrue(manager.read(end, color).signal(color.channel) == 0, "Source removal withdraws its contribution");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void portSpanChunkLifecycleAndMismatchedTrayGeometry(GameTestHelper h) {
        var level = h.getLevel(); var seed = h.absolutePos(new BlockPos(2, 4, 2));
        var root = new BlockPos((seed.getX() >> 4) * 16 + 15, seed.getY(), seed.getZ()); var peer = root.east(4);
        level.getChunk(peer); box(h, root, Direction.WEST, 14, 0, 0, 0); level.setBlockAndUpdate(peer, host(CableRoute.X, IndividualCableColor.RED));
        var ledger = CableSpans.get(level); var port = new CableEndpoint(root, CableRoute.NONE, ControlBoxPorts.encode(Direction.EAST, Direction.WEST));
        h.assertTrue(ledger.connect(IndividualCableColor.RED, port, new CableEndpoint(peer, CableRoute.X, 0), 4), "Port span crosses a loaded chunk boundary");
        var manager = settle(h); h.assertTrue(manager.read(peer, IndividualCableColor.RED).signal(ControlChannel.RED) == 14, "Cross-chunk port span transmits");
        var chunk = level.getChunkAt(root); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk)); settle(h);
        h.assertTrue(manager.read(peer, IndividualCableColor.RED).signal(ControlChannel.RED) == 0 && !manager.inspect(peer, IndividualCableColor.RED).hasSource(), "Unloaded source leaves no phantom signal or source flag");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false)); settle(h);
        h.assertTrue(manager.read(peer, IndividualCableColor.RED).signal(ControlChannel.RED) == 14, "Reload restores channel connectivity from existing records");
        var isolated = peer.south(3); level.setBlockAndUpdate(isolated, host(CableRoute.Y, IndividualCableColor.RED));
        level.setBlockAndUpdate(isolated.above(), ModBlocks.CABLE_TRAY.get().defaultBlockState().setValue(CableTrayBlock.AXIS, Direction.Axis.Y).setValue(CableTrayBlock.FACING, Direction.NORTH));
        h.assertTrue(CablePhysicalConnections.contacts(level, isolated, level.getBlockState(isolated), IndividualCableColor.RED, CableRoute.Y).isEmpty(), "Opposite visible tray/cable faces never electrically join by adjacency alone");
        var remote = new BlockPos(28_000_000, 80, 28_000_000);
        h.assertTrue(!manager.read(remote, IndividualCableColor.RED).connected() && level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Channel queries never load remote chunks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void protectedWrenchAndCableInteractionPriority(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 4, 2)); var b = a.east(5);
        box(h, a, Direction.SOUTH, 6, 0, 0, 0); box(h, b, Direction.SOUTH, 9, 0, 0, 0);
        int code = ControlBoxPorts.encode(Direction.UP, Direction.SOUTH);
        var first = new CableEndpoint(a, CableRoute.NONE, code); var last = new CableEndpoint(b, CableRoute.NONE, code);
        var ledger = CableSpans.get(level); h.assertTrue(ledger.connect(IndividualCableColor.RED, first, last, 5), "Test port span is connected");
        var p = player(h); p.gameMode.changeGameModeForPlayer(GameType.CREATIVE); p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> protection = event -> {
            if (event.getLevel() == level && event.getPos().equals(a)) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(protection);
        try {
            click(p, a, Direction.UP, first.point(IndividualCableColor.RED, b));
            h.assertTrue(level.getBlockState(a).is(ModBlocks.CONTROL_BOX.get()) && ledger.at(a).size() == 1,
                    "Cancelled creative wrench removal preserves the box and its attached span");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(protection); }
        int drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(a).inflate(1)).size();
        click(p, a, Direction.UP, first.point(IndividualCableColor.RED, b));
        h.assertTrue(level.isEmptyBlock(a) && ledger.at(b).isEmpty()
                && level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(a).inflate(1)).size() == drops,
                "Successful creative wrench removal cleans up spans without refunds");
        box(h, a, Direction.SOUTH, 6, 0, 0, 0); p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(IndividualCableColor.RED.item(), 8); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.setItemInHand(InteractionHand.OFF_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
        p.setShiftKeyDown(false); var point = first.point(IndividualCableColor.RED, b);
        h.assertTrue(click(p, a, Direction.UP, point) == InteractionResult.PASS && level.isEmptyBlock(a.above())
                && stack.getCount() == 8 && !level.getBlockState(a).getValue(ControlBoxBlock.OPEN),
                "Cable use yields to an offhand wrench through both item interaction phases");
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.assertTrue(click(p, a, Direction.UP, point).consumesAction() && stack.getCount() == 7,
                "Ordinary port placement remains available after releasing the wrench");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void individualToTrayAndReverseDrive(GameTestHelper h) {
        var level = h.getLevel(); var root = h.absolutePos(new BlockPos(3, 4, 3));
        var source = box(h, root, Direction.SOUTH, 12, 4, 9, 15);
        var first = root.north(); var second = root.north(2); var trayPos = root.north(3);
        level.setBlockAndUpdate(first, host(CableRoute.Z, IndividualCableColor.RED));
        level.setBlockAndUpdate(second, host(CableRoute.Z, IndividualCableColor.RED)); level.setBlockAndUpdate(trayPos, tray());
        var manager = settle(h); signals(h, manager.read(trayPos), 12, 0, 0, 0);
        level.setBlockAndUpdate(first, level.getBlockState(first).setValue(IndividualCableColor.YELLOW.property, CableRoute.Z));
        level.setBlockAndUpdate(second, level.getBlockState(second).setValue(IndividualCableColor.YELLOW.property, CableRoute.Z));
        settle(h); signals(h, manager.read(trayPos), 12, 4, 0, 0);
        box(h, root.north(4), Direction.NORTH, 3, 1, 15, 8); settle(h);
        source.setSignal(ControlChannel.RED, 0);
        h.assertTrue(manager.read(first, IndividualCableColor.RED).signal(ControlChannel.RED) == 3, "Tray source drives the individual cable in reverse without a diode");
        h.assertTrue(source.getSignal(ControlChannel.RED) == 0 && source.getComparatorOutput() == 15, "Received network values never overwrite local dials or comparator readings");
        long yellowIdentity = manager.read(trayPos, IndividualCableColor.YELLOW).id();
        ((IndividualCableBlockEntity) level.getBlockEntity(second)).removeComponent(IndividualCableColor.RED, CableRoute.Z); settle(h);
        h.assertTrue(manager.read(first, IndividualCableColor.RED).signal(ControlChannel.RED) == 0, "Individual route break disconnects the remote tray source");
        h.assertTrue(manager.read(trayPos, IndividualCableColor.YELLOW).id() == yellowIdentity
                && manager.read(trayPos, IndividualCableColor.YELLOW).signal(ControlChannel.YELLOW) == 4, "A Red component edit preserves the unrelated Yellow cached network");
        h.succeed();
    }

}
