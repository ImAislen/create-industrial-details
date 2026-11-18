package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableTrayGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void resourcesAndRotatedShapes(GameTestHelper helper) throws IOException {
        String assets = "/assets/create_industrial_details/";
        var model = json(assets + "models/block/cable_tray/cable_tray.json");
        String texture = "create_industrial_details:block/cable_tray/cable_tray";
        helper.assertTrue(model.getAsJsonObject("textures").get("0").getAsString().equals(texture)
                && model.getAsJsonObject("textures").get("particle").getAsString().equals(texture), "Canonical model resolves the supplied texture directly");
        helper.assertTrue(json(assets + "models/item/cable_tray.json").get("parent").getAsString()
                .equals("create_industrial_details:block/cable_tray/cable_tray"), "Inventory and dropped item use the canonical model");
        var variants = json(assets + "blockstates/cable_tray.json").getAsJsonObject("variants");
        helper.assertTrue(variants.size() == 12, "Six distinct appearances share one model");
        var shapes = new HashSet<>();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        for (BlockState state : ModBlocks.CABLE_TRAY.get().getStateDefinition().getPossibleStates()) {
            var axis = state.getValue(CableTrayBlock.AXIS);
            String key = "axis=" + axis.getSerializedName();
            key += ",facing=" + state.getValue(CableTrayBlock.FACING).getSerializedName();
            var variant = variants.getAsJsonObject(key);
            helper.assertTrue(variant.get("model").getAsString().equals("create_industrial_details:block/cable_tray/cable_tray"), "All blockstates reference the original model");
            float x = variant.has("x") ? variant.get("x").getAsFloat() : 0;
            float y = variant.has("y") ? variant.get("y").getAsFloat() : 0;
            var transform = new Matrix4f().translate(.5f, .5f, .5f).rotateY((float) Math.toRadians(-y))
                    .rotateX((float) Math.toRadians(-x)).translate(-.5f, -.5f, -.5f);
            var front = transform.transformDirection(new Vector3f(0, 1, 0));
            var length = transform.transformDirection(new Vector3f(0, 0, 1));
            if (axis.isVertical()) {
                Direction face = state.getValue(CableTrayBlock.FACING);
                helper.assertTrue(front.distance(face.getStepX(), 0, face.getStepZ()) < .00001 && Math.abs(length.y) > .9999,
                        "Model rotations point exposed colored cables toward facing and run the tray vertically");
            } else helper.assertTrue(front.distance(0, 1, 0) < .00001 && Math.abs(axis == Direction.Axis.X ? length.x : length.z) > .9999,
                    "Existing horizontal model rotations remain unchanged");
            var expected = Shapes.empty();
            for (var element : model.getAsJsonArray("elements")) {
                var box = element.getAsJsonObject();
                var from = box.getAsJsonArray("from");
                var to = box.getAsJsonArray("to");
                float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
                float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
                for (int corner = 0; corner < 8; corner++) {
                    var point = new Vector3f();
                    for (int d = 0; d < 3; d++) point.setComponent(d, ((corner & (1 << d)) == 0 ? from : to).get(d).getAsFloat() / 16);
                    transform.transformPosition(point);
                    for (int d = 0; d < 3; d++) {
                        min[d] = Math.min(min[d], point.get(d)); max[d] = Math.max(max[d], point.get(d));
                    }
                }
                expected = Shapes.or(expected, Shapes.create(round(min[0]), round(min[1]), round(min[2]), round(max[0]), round(max[1]), round(max[2])));
            }
            var actual = state.getShape(helper.getLevel(), pos);
            helper.assertTrue(!Shapes.joinIsNotEmpty(actual, expected, BooleanOp.NOT_SAME), "Cached shape follows every rotated cuboid of the supplied model");
            helper.assertTrue(actual == state.getShape(helper.getLevel(), pos) && actual == state.getCollisionShape(helper.getLevel(), pos),
                    "Collision and selection reuse cached shapes");
            helper.assertTrue(!state.hasBlockEntity() && state.canSurvive(helper.getLevel(), pos), "Decorative tray needs no block entity or support");
            shapes.add(actual);
        }
        helper.assertTrue(shapes.size() == 6, "Only six shape instances serve all states");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void normalPlacementAndRuns(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var level = helper.getLevel();
        var tray = ModBlocks.CABLE_TRAY.get();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction look : Direction.Plane.HORIZONTAL) {
            player.setYRot(look.toYRot());
            for (Direction clicked : new Direction[]{Direction.UP, Direction.DOWN}) {
                BlockState state = tray.getStateForPlacement(placement(helper, player, pos, clicked));
                helper.assertTrue(state.getValue(CableTrayBlock.AXIS) == look.getAxis(), "Top and bottom placement retains player-aligned horizontal runs");
            }
            BlockState vertical = tray.getStateForPlacement(placement(helper, player, pos, look));
            helper.assertTrue(vertical.getValue(CableTrayBlock.AXIS) == Direction.Axis.Y && vertical.getValue(CableTrayBlock.FACING) == look,
                    "Wall placement exposes the cables away from the clicked wall");
            level.setBlockAndUpdate(pos, vertical);
            for (Direction extension : new Direction[]{Direction.UP, Direction.DOWN}) {
                var state = tray.getStateForPlacement(placement(helper, player, pos.relative(extension), extension));
                helper.assertTrue(state.getValue(CableTrayBlock.AXIS) == Direction.Axis.Y && state.getValue(CableTrayBlock.FACING) == look,
                        "Vertical runs extend upward and downward with the same cable-facing direction");
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void controlBoxPlacementAndUpdates(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var tray = ModBlocks.CABLE_TRAY.get();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var box = ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, facing);
            level.setBlockAndUpdate(pos, box);
            for (Direction side : new Direction[]{Direction.UP, Direction.DOWN}) {
                BlockPos target = pos.relative(side);
                BlockState state = tray.getStateForPlacement(placement(helper, player, target, facing.getOpposite()));
                helper.assertTrue(state.getValue(CableTrayBlock.AXIS) == Direction.Axis.Y && state.getValue(CableTrayBlock.FACING) == facing,
                        "Box orientation overrides normal placement above and below for every facing");
                level.setBlockAndUpdate(target, state);
            }
            BlockPos unrelated = pos.east();
            level.setBlockAndUpdate(unrelated, tray.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            AllItems.WRENCH.get().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            Direction rotated = level.getBlockState(pos).getValue(ControlBoxBlock.FACING);
            helper.assertTrue(rotated != facing, "Existing Control Box wrench rotation executes normally");
            for (Direction side : new Direction[]{Direction.UP, Direction.DOWN})
                helper.assertTrue(level.getBlockState(pos.relative(side)).getValue(CableTrayBlock.FACING) == rotated,
                        "Box wrench rotation updates only directly attached vertical trays");
            helper.assertTrue(level.getBlockState(unrelated) == tray.defaultBlockState(), "Side-adjacent tray remains player-controlled");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            for (Direction side : new Direction[]{Direction.UP, Direction.DOWN})
                helper.assertTrue(level.getBlockState(pos.relative(side)).is(tray), "Removing box never breaks an attached tray");
            level.setBlockAndUpdate(pos.above(), tray.defaultBlockState().setValue(CableTrayBlock.AXIS, Direction.Axis.X));
            level.setBlockAndUpdate(pos.below(), tray.defaultBlockState());
            level.setBlockAndUpdate(pos, box);
            for (Direction side : new Direction[]{Direction.UP, Direction.DOWN}) {
                var aligned = level.getBlockState(pos.relative(side));
                helper.assertTrue(aligned.getValue(CableTrayBlock.AXIS) == Direction.Axis.Y && aligned.getValue(CableTrayBlock.FACING) == facing,
                        "Placing the box after the trays also aligns them");
                level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(unrelated, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void persistenceAndWrench(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var tray = ModBlocks.CABLE_TRAY.get();
        for (String axis : new String[]{"x", "z"}) {
            var tag = new CompoundTag();
            tag.putString("Name", "create_industrial_details:cable_tray");
            var properties = new CompoundTag(); properties.putString("axis", axis); tag.put("Properties", properties);
            var loaded = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), tag);
            helper.assertTrue(loaded.is(tray) && loaded.getValue(CableTrayBlock.AXIS).getSerializedName().equals(axis),
                    "Legacy axis-only states retain their original horizontal orientation");
        }
        for (BlockState state : tray.getStateDefinition().getPossibleStates())
            helper.assertTrue(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(state)) == state,
                    "Every orientation survives state serialization");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        level.setBlockAndUpdate(pos, tray.defaultBlockState());
        AllItems.WRENCH.get().useOn(wrench(helper, player, pos, Direction.UP));
        helper.assertTrue(level.getBlockState(pos).getValue(CableTrayBlock.AXIS) == Direction.Axis.X, "Top wrench preserves horizontal rotation");
        AllItems.WRENCH.get().useOn(wrench(helper, player, pos, Direction.NORTH));
        helper.assertTrue(level.getBlockState(pos).getValue(CableTrayBlock.AXIS) == Direction.Axis.Y, "Side wrench tips a horizontal run vertically");
        var vertical = level.getBlockState(pos);
        AllItems.WRENCH.get().useOn(wrench(helper, player, pos, Direction.UP));
        helper.assertTrue(level.getBlockState(pos).getValue(CableTrayBlock.FACING) == vertical.getValue(CableTrayBlock.FACING).getClockWise(),
                "Top wrench turns the visible cable face around the vertical axis");
        helper.assertTrue(vertical.rotate(Rotation.CLOCKWISE_90).getValue(CableTrayBlock.FACING) == vertical.getValue(CableTrayBlock.FACING).getClockWise()
                && vertical.mirror(Mirror.LEFT_RIGHT).getValue(CableTrayBlock.FACING) == Direction.SOUTH, "Structure rotations and mirrors preserve vertical orientation");
        var drops = Block.getDrops(vertical, level, pos, null);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(tray.asItem()), "Original loot drops one tray");
        player.setShiftKeyDown(true);
        AllItems.WRENCH.get().useOn(wrench(helper, player, pos, Direction.UP));
        helper.assertTrue(level.getBlockState(pos).isAir() && player.getInventory().countItem(tray.asItem()) == 1,
                "Shift-wrench returns exactly one tray");
        helper.succeed();
    }

    private static double round(float coordinate) {
        return Math.round(coordinate * 4096) / 4096.0;
    }

    private static JsonObject json(String path) throws IOException {
        var stream = CableTrayGameTests.class.getResourceAsStream(path);
        if (stream == null) throw new IOException("Missing resource: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static BlockPlaceContext placement(GameTestHelper helper, net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face) {
        return new BlockPlaceContext(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.CABLE_TRAY.get()),
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
    }

    private static UseOnContext wrench(GameTestHelper helper, net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face) {
        return new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
}
