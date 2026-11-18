package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableNetworkGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void verticalColorAlignment(GameTestHelper helper) throws IOException {
        var variants = json("blockstates/cable_tray.json").getAsJsonObject("variants");
        var tray = json("models/block/cable_tray/cable_tray.json");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var variant = variants.getAsJsonObject("axis=y,facing=" + facing.getSerializedName());
            var matrix = new Matrix4f().translate(.5f, .5f, .5f)
                    .rotateY((float) Math.toRadians(-variant.get("y").getAsInt()))
                    .rotateX((float) Math.toRadians(-variant.get("x").getAsInt())).translate(-.5f, -.5f, -.5f);
            Vec3 right = ControlBoxGeometry.fromModel(facing, new Vec3(1, 0, 0)).subtract(ControlBoxGeometry.fromModel(facing, Vec3.ZERO));
            double last = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                var element = tray.getAsJsonArray("elements").get(3 + i).getAsJsonObject();
                var from = element.getAsJsonArray("from"); var to = element.getAsJsonArray("to");
                var center = new Vector3f();
                for (int d = 0; d < 3; d++) center.setComponent(d, (from.get(d).getAsFloat() + to.get(d).getAsFloat()) / 32);
                matrix.transformPosition(center);
                double across = new Vec3(center.x, center.y, center.z).dot(right);
                helper.assertTrue(across > last, "Red/yellow/green/blue positions increase in the same direction as Control Box controls for " + facing);
                last = across;
            }
            var front = matrix.transformDirection(new Vector3f(0, 1, 0));
            helper.assertTrue(front.distance(facing.getStepX(), 0, facing.getStepZ()) < .00001,
                    "Color correction keeps exposed cables facing the player");
        }
        helper.assertTrue(variants.getAsJsonObject("axis=z,facing=north").get("x") == null
                && variants.getAsJsonObject("axis=x,facing=north").get("y").getAsInt() == 90, "Horizontal appearances retain their original transformations");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void physicalConnections(GameTestHelper helper) {
        var z = tray(Direction.Axis.Z, Direction.NORTH);
        var x = tray(Direction.Axis.X, Direction.NORTH);
        for (Direction direction : Direction.values()) {
            helper.assertTrue(CableConnections.canConnect(z, z, direction) == (direction.getAxis() == Direction.Axis.Z), "Horizontal Z run exposes only its two ends");
            helper.assertTrue(CableConnections.canConnect(x, x, direction) == (direction.getAxis() == Direction.Axis.X), "Parallel adjacent trays never branch sideways");
            helper.assertTrue(!CableConnections.canConnect(z, x, direction), "Perpendicular trays do not create implicit elbows");
        }
        for (Direction front : Direction.Plane.HORIZONTAL) {
            var vertical = tray(Direction.Axis.Y, front);
            var box = ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, front);
            for (Direction side : Direction.values()) {
                helper.assertTrue(CableConnections.canConnect(vertical, vertical, side) == (side.getAxis() == Direction.Axis.Y), "Vertical runs expose only top and bottom");
                helper.assertTrue(!CableConnections.canConnect(vertical, tray(Direction.Axis.Y, front.getOpposite()), side), "Opposite cable faces do not line up");
                var connected = side.getAxis() == Direction.Axis.Y ? vertical : tray(side.getAxis(), Direction.NORTH);
                boolean expected = side.getAxis() == Direction.Axis.Y || side == front.getOpposite();
                helper.assertTrue(CableConnections.canConnect(box, connected, side) == expected
                        && CableConnections.canConnect(connected, box, side.getOpposite()) == expected, "Only top, bottom and rear permit compatible tray connections");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void highestSignalsSplitsAndMerges(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 3, 2));
        int length = 24;
        for (int i = 0; i <= length + 1; i++) level.getChunk(start.offset(0, 0, i));
        var first = source(helper, start, Direction.NORTH, 5, 12, 0, 7);
        var last = source(helper, start.offset(0, 0, length + 1), Direction.SOUTH, 10, 3, 15, 2);
        for (int i = 1; i <= length; i++) level.setBlockAndUpdate(start.offset(0, 0, i), tray(Direction.Axis.Z, Direction.NORTH));
        var manager = settle(helper);
        var values = manager.read(start.south());
        assertSignals(helper, values, 10, 12, 15, 7);
        helper.assertTrue(values.id() == manager.read(start.offset(0, 0, length)).id(), "Entire straight run shares a cached network without attenuation");
        var observations = new ArrayList<CableNetworkManager.NetworkState>();
        try (var subscription = manager.observe(start.south(), observations::add)) {
            int count = observations.size();
            last.setSignal(ControlChannel.RED, 4);
            assertSignals(helper, manager.read(start.south()), 5, 12, 15, 7);
            helper.assertTrue(observations.size() == count + 1, "Source value change updates subscribers immediately without discovery");
            first.setSignal(ControlChannel.GREEN, 5);
            helper.assertTrue(observations.size() == count + 1, "A source change below another source's maximum causes no signal notification");
            helper.assertTrue(first.getComparatorOutput() == 12 && last.getComparatorOutput() == 15, "Comparators remain local rather than reporting network aggregation");
            BlockPos cut = start.offset(0, 0, length / 2);
            level.destroyBlock(cut, false);
            settle(helper);
            var left = manager.read(start.south()); var right = manager.read(start.offset(0, 0, length));
            assertSignals(helper, left, 5, 12, 5, 7); assertSignals(helper, right, 4, 3, 15, 2);
            helper.assertTrue(left.id() != right.id(), "Removing a tray splits the network");
            level.setBlockAndUpdate(cut, tray(Direction.Axis.Z, Direction.NORTH));
            settle(helper);
            assertSignals(helper, manager.read(cut), 5, 12, 15, 7);
            level.destroyBlock(start.offset(0, 0, length + 1), false);
            settle(helper);
            assertSignals(helper, manager.read(start.south()), 5, 12, 5, 7);
            level.destroyBlock(start, false);
            settle(helper);
            assertSignals(helper, manager.read(start.south()), 0, 0, 0, 0);
            helper.assertTrue(manager.read(start.south()).connected(), "An undriven tray network remains connected with zero signals");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void threePortsDoNotBridge(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 4, 3));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            source(helper, pos, facing, 2, 4, 6, 8);
            for (int i = 1; i <= 3; i++) {
                level.setBlockAndUpdate(pos.above(i), tray(Direction.Axis.Y, facing));
                level.setBlockAndUpdate(pos.below(i), tray(Direction.Axis.Y, facing));
            }
            Direction back = facing.getOpposite();
            level.setBlockAndUpdate(pos.relative(back), tray(back.getAxis(), Direction.NORTH));
            var manager = settle(helper);
            var top = manager.read(pos.above()); var bottom = manager.read(pos.below()); var rear = manager.read(pos.relative(back));
            assertSignals(helper, top, 2, 4, 6, 8); assertSignals(helper, bottom, 2, 4, 6, 8); assertSignals(helper, rear, 2, 4, 6, 8);
            helper.assertTrue(top.id() != bottom.id() && top.id() != rear.id() && bottom.id() != rear.id(), "Three source ports drive separate networks");
            source(helper, pos.above(4), facing, 15, 0, 0, 0);
            settle(helper);
            assertSignals(helper, manager.read(pos.above(3)), 15, 4, 6, 8);
            assertSignals(helper, manager.read(pos.below(3)), 2, 4, 6, 8);
            assertSignals(helper, manager.read(pos.relative(back)), 2, 4, 6, 8);
            for (int i = -3; i <= 4; i++) level.setBlockAndUpdate(pos.offset(0, i, 0), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.relative(back), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void isolatedRunsRotationAndObservation(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        source(helper, pos, Direction.NORTH, 9, 8, 7, 6);
        for (int i = 1; i <= 4; i++) {
            level.setBlockAndUpdate(pos.south(i), tray(Direction.Axis.Z, Direction.NORTH));
            level.setBlockAndUpdate(pos.south(i).east(), tray(Direction.Axis.Z, Direction.NORTH));
        }
        var manager = settle(helper);
        assertSignals(helper, manager.read(pos.south(4)), 9, 8, 7, 6);
        assertSignals(helper, manager.read(pos.south(4).east()), 0, 0, 0, 0);
        long identity = manager.read(pos.south(4)).id();
        BlockState box = level.getBlockState(pos);
        level.setBlock(pos, box.setValue(ControlBoxBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        helper.assertTrue(manager.read(pos.south(4)).id() == identity, "Door changes do not invalidate cable topology");
        level.setBlockAndUpdate(pos, box.setValue(ControlBoxBlock.FACING, Direction.EAST));
        settle(helper);
        assertSignals(helper, manager.read(pos.south(4)), 0, 0, 0, 0);
        level.setBlockAndUpdate(pos, box);
        settle(helper);
        assertSignals(helper, manager.read(pos.south(4)), 9, 8, 7, 6);
        var updates = new ArrayList<CableNetworkManager.NetworkState>();
        var watch = manager.observe(pos.south(4), updates::add);
        level.setBlockAndUpdate(pos.south(2), tray(Direction.Axis.X, Direction.NORTH));
        settle(helper);
        assertSignals(helper, manager.read(pos.south(4)), 0, 0, 0, 0);
        helper.assertTrue(updates.size() > 1 && updates.getLast().connected(), "Orientation changes notify output observers of a different undriven network");
        watch.close();
        int count = updates.size();
        level.destroyBlock(pos.south(4), false);
        settle(helper);
        helper.assertTrue(updates.size() == count, "Disposed subscriptions receive no further callbacks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void boundedRebuildAndUnloadedReads(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var manager = settle(helper);
        var observations = new ArrayList<CableNetworkManager.NetworkState>();
        try (var watch = manager.observe(pos.south(8), observations::add)) {
            var source = source(helper, pos, Direction.NORTH, 3, 5, 7, 9);
            for (int i = 1; i <= 8; i++) level.setBlockAndUpdate(pos.south(i), tray(Direction.Axis.Z, Direction.NORTH));
            manager.processPending(1);
            helper.assertTrue(!observations.getLast().connected(), "An incomplete bounded rebuild does not publish a partial network");
            source.setSignal(ControlChannel.RED, 11);
            level.destroyBlock(pos.south(4), false);
            manager.processPending(1);
            settle(helper);
            assertSignals(helper, manager.read(pos.south(8)), 0, 0, 0, 0);
            level.setBlockAndUpdate(pos.south(4), tray(Direction.Axis.Z, Direction.NORTH));
            settle(helper);
            assertSignals(helper, manager.read(pos.south(8)), 11, 5, 7, 9);
            var snapshot = manager.read(pos.south(8));
            helper.assertTrue(snapshot == manager.read(pos.south(8)), "Stable reads reuse the cached immutable signal snapshot");
            level.setBlock(pos, level.getBlockState(pos).setValue(ControlBoxBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            new SettingsPacket(pos).apply(source);
            assertSignals(helper, manager.read(pos.south(8)), 11, 5, 7, 15);
            helper.assertTrue(observations.getLast().signal(ControlChannel.BLUE) == 15,
                    "Confirming a native Value Settings packet updates the connected network and its output observers");
        }
        BlockPos remote = new BlockPos(28_000_000, 80, 28_000_000);
        helper.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Remote test chunk starts unloaded");
        helper.assertTrue(!manager.read(remote).connected()
                && level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                "Reading an unloaded cable location never requests or loads its chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chunkLifecycleEvents(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos edge = new BlockPos((origin.getX() >> 4) * 16 + 15, origin.getY(), origin.getZ());
        level.getChunk(edge.east());
        source(helper, edge.west(2), Direction.WEST, 1, 5, 10, 15);
        for (int i = -1; i <= 3; i++) level.setBlockAndUpdate(edge.east(i), tray(Direction.Axis.X, Direction.NORTH));
        var manager = settle(helper);
        assertSignals(helper, manager.read(edge.east(3)), 1, 5, 10, 15);
        var sourceChunk = level.getChunkAt(edge);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(sourceChunk));
        settle(helper);
        assertSignals(helper, manager.read(edge.east(3)), 0, 0, 0, 0);
        helper.assertTrue(!manager.read(edge).connected(), "Unloaded chunk members are unavailable even during the unload callback");
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(sourceChunk, false));
        settle(helper);
        assertSignals(helper, manager.read(edge.east(3)), 1, 5, 10, 15);
        var destinationChunk = level.getChunkAt(edge.east());
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(destinationChunk));
        settle(helper);
        helper.assertTrue(!manager.read(edge.east(3)).connected(), "Unloaded receiving chunk has no phantom membership");
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(destinationChunk, false));
        settle(helper);
        assertSignals(helper, manager.read(edge.east(3)), 1, 5, 10, 15);
        helper.succeed();
    }

    private static CableNetworkManager settle(GameTestHelper helper) {
        var manager = CableNetworks.get(helper.getLevel());
        for (int i = 0; i < 128; i++) manager.processPending();
        return manager;
    }

    private static BlockState tray(Direction.Axis axis, Direction facing) {
        return ModBlocks.CABLE_TRAY.get().defaultBlockState().setValue(CableTrayBlock.AXIS, axis).setValue(CableTrayBlock.FACING, facing);
    }

    private static ControlBoxBlockEntity source(GameTestHelper helper, BlockPos pos, Direction facing, int... values) {
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, facing));
        var box = (ControlBoxBlockEntity) helper.getLevel().getBlockEntity(pos);
        for (ControlChannel channel : ControlChannel.values()) box.setSignal(channel, values[channel.ordinal()]);
        return box;
    }

    private static void assertSignals(GameTestHelper helper, CableNetworkManager.NetworkState network, int... expected) {
        for (ControlChannel channel : ControlChannel.values()) helper.assertTrue(network.signal(channel) == expected[channel.ordinal()],
                channel + " expected " + expected[channel.ordinal()] + " but got " + network.signal(channel));
    }

    private static JsonObject json(String path) throws IOException {
        try (var stream = CableNetworkGameTests.class.getResourceAsStream("/assets/create_industrial_details/" + path)) {
            if (stream == null) throw new IOException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static final class SettingsPacket extends ValueSettingsPacket {
        SettingsPacket(BlockPos pos) { super(pos, 0, 15, null, null, Direction.NORTH, false, ControlChannel.BLUE.ordinal()); }

        void apply(ControlBoxBlockEntity box) { super.applySettings(null, box); }
    }
}
