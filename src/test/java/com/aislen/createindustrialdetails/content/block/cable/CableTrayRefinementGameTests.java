package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableTrayRefinementGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void horizontalOrderAllHeadings(GameTestHelper helper) throws Exception {
        try (var stream = CableTrayRefinementGameTests.class.getResourceAsStream("/assets/create_industrial_details/blockstates/cable_tray.json")) {
            var variants = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
            for (Direction heading : Direction.Plane.HORIZONTAL) {
                var variant = variants.getAsJsonObject("axis=" + heading.getAxis().getSerializedName() + ",facing=" + heading.getSerializedName());
                var rotation = new Matrix4f().rotateY((float) Math.toRadians(-variant.get("y").getAsInt()));
                var across = rotation.transformDirection(new Vector3f(1, 0, 0));
                Direction screenRight = heading.getClockWise();
                helper.assertTrue(across.distance(screenRight.getStepX(), 0, screenRight.getStepZ()) < .00001,
                        "Canonical red-to-blue model direction reads left-to-right from the normal floor placement viewpoint for " + heading);
                helper.assertTrue(!variant.has("x"), "Floor trays never inherit vertical X rotations");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void assistedPlacementAndBypass(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var tray = ModBlocks.CABLE_TRAY.get(); var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var assistance = new CableTrayPlacementHelper();
        for (var state : tray.getStateDefinition().getPossibleStates()) {
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() != state.getValue(CableTrayBlock.AXIS)) continue;
                level.setBlockAndUpdate(pos, state);
                var stack = new ItemStack(tray, 8); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(.49)), direction, pos, false);
                var offset = assistance.getOffset(player, level, state, pos, hit);
                helper.assertTrue(offset.isSuccessful() && offset.getBlockPos().equals(pos.relative(direction)), "Assistance selects a straight run endpoint");
                helper.assertTrue(offset.getTransform().apply(tray.defaultBlockState()) == state, "Assistance inherits axis and cable-facing orientation");
                state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(level.getBlockState(pos.relative(direction)) == state && stack.getCount() == 7,
                        "Native helper places aligned tray and consumes one item");
                player.setShiftKeyDown(true);
                helper.assertTrue(!assistance.getOffset(player, level, state, pos, hit).isSuccessful(), "Sneaking bypasses native assistance");
                player.setShiftKeyDown(false);
                for (Direction end : Direction.values()) if (end.getAxis() == state.getValue(CableTrayBlock.AXIS))
                    level.setBlockAndUpdate(pos.relative(end), Blocks.STONE.defaultBlockState());
                helper.assertTrue(!assistance.getOffset(player, level, state, pos, hit).isSuccessful(), "Occupied endpoints are never replaced");
                for (Direction end : Direction.values()) if (end.getAxis() == state.getValue(CableTrayBlock.AXIS))
                    level.setBlockAndUpdate(pos.relative(end), Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void assistedControlBoxRunAndDiagnostics(GameTestHelper helper) {
        var level = helper.getLevel(); var boxPos = helper.absolutePos(new BlockPos(3, 3, 3));
        var tray = ModBlocks.CABLE_TRAY.get(); var player = helper.makeMockPlayer(GameType.CREATIVE);
        var assistance = new CableTrayPlacementHelper();
        for (Direction front : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(boxPos, ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, front));
            var box = (ControlBoxBlockEntity) level.getBlockEntity(boxPos);
            box.setSignal(ControlChannel.RED, 4); box.setSignal(ControlChannel.YELLOW, 12); box.setSignal(ControlChannel.BLUE, 8);
            var state = tray.defaultBlockState().setValue(CableTrayBlock.AXIS, Direction.Axis.Y).setValue(CableTrayBlock.FACING, front);
            level.setBlockAndUpdate(boxPos.above(), state);
            var stack = new ItemStack(tray); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3.atCenterOf(boxPos.above()).add(0, .49, 0), Direction.UP, boxPos.above(), false);
            state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(level.getBlockState(boxPos.above(2)) == state && stack.getCount() == 1, "Box-aligned vertical extensions preserve facing in creative mode");
            var manager = CableNetworks.get(level);
            for (int i = 0; i < 128; i++) manager.processPending();
            var network = manager.read(boxPos.above(2));
            String text = CableNetworkInspection.describe(network).getString();
            helper.assertTrue(network.signal(ControlChannel.RED) == 4 && network.signal(ControlChannel.YELLOW) == 12
                    && network.signal(ControlChannel.GREEN) == 0 && network.signal(ControlChannel.BLUE) == 8
                    && text.contains("12") && text.contains("#" + network.id()), "Diagnostic output reads real cached network values and identity");
            level.destroyBlock(boxPos, false);
            for (int i = 0; i < 128; i++) manager.processPending();
            helper.assertTrue(manager.read(boxPos.above(2)).signals() == 0, "Inspection reflects source removal");
            level.destroyBlock(boxPos.above(), false); level.destroyBlock(boxPos.above(2), false);
        }
        helper.succeed();
    }
}
