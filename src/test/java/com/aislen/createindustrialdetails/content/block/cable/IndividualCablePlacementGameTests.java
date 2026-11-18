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
import java.util.UUID;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndividualCablePlacementGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void validDefaultAndDuplicateRemainUnchanged(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2));
        var empty = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        for (var color : IndividualCableColor.values()) for (var route : CableRoute.values()) {
            if (route == CableRoute.NONE) continue;
            h.assertTrue(IndividualCablePlacement.choose(level, pos, empty, color, route) == route, "Valid default never changes without a conflict");
            var occupied = empty.setValue(color.property, route); level.setBlockAndUpdate(pos, occupied);
            h.assertTrue(IndividualCablePlacement.choose(level, pos, occupied, color, route) == route
                    && !IndividualCablePlacement.valid(level, pos, occupied, color, route), "Duplicate is rejected rather than converted into another piece");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void connectedFallbackPreservesComponentsAndCost(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2));
        var p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "fallback-placement"));
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        for (var direction : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            var empty = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
            var floor = CableRoute.floor(direction);
            var wrong = CableRoute.floor(direction.getClockWise());
            for (var color : IndividualCableColor.values()) {
                var other = IndividualCableColor.values()[(color.ordinal() + 1) % 4];
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                var state = empty.setValue(other.property, floor);
                level.setBlockAndUpdate(pos, state);
                level.setBlockAndUpdate(pos.below(), empty.setValue(color.property, CableRoute.wall(direction.getOpposite())));
                h.assertTrue(!IndividualCablePlacement.valid(level, pos, state, color, wrong), "Perpendicular floor crossing is invalid");
                h.assertTrue(IndividualCablePlacement.choose(level, pos, state, color, wrong) == floor, "Matching physical endpoint selects the connected floor direction");
                p.setYRot(direction.getClockWise().toYRot());
                var stack = new ItemStack(color.item(), 8); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                h.assertTrue(p.gameMode.useItemOn(p, level, stack, InteractionHand.MAIN_HAND, hit).consumesAction() && stack.getCount() == 7, "Only successful placement consumes an item");
                h.assertTrue(level.getBlockState(pos).getValue(other.property) == floor && level.getBlockState(pos).getValue(color.property) == floor, "Existing orientation remains intact: expected " + floor + " actual " + level.getBlockState(pos));
                var host = (IndividualCableBlockEntity) level.getBlockEntity(pos);
                h.assertTrue(IndividualCablePlacement.choose(level, pos, host.getBlockState(), color, CableRoute.wall(direction)).reversedWall(), "Fallback never substitutes a wall plane that would float away from the clicked support");
                level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unrelatedIntersectionsDoNotForceAnAlternative(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2));
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(IndividualCableColor.YELLOW.property, CableRoute.Z);
        level.setBlockAndUpdate(pos, state);
        for (int i = 0; i < 8; i++) h.assertTrue(IndividualCablePlacement.choose(level, pos, state, IndividualCableColor.RED, CableRoute.X) == CableRoute.X,
                "An unrelated other-color cable cannot justify rotating a rejected crossing");
        h.assertTrue(!IndividualCablePlacement.valid(level, pos, state, IndividualCableColor.RED, CableRoute.X), "The genuine crossing remains invalid");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void multiplePhysicalAlternativesAreDeterministic(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var empty = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
        var state = empty.setValue(IndividualCableColor.YELLOW.property, CableRoute.Z);
        level.setBlockAndUpdate(pos, state);
        level.setBlockAndUpdate(pos.below(), empty.setValue(IndividualCableColor.RED.property, CableRoute.Y));
        level.setBlockAndUpdate(pos.north(), empty.setValue(IndividualCableColor.RED.property, CableRoute.WALL_NORTH));
        for (int i = 0; i < 10; i++) h.assertTrue(IndividualCablePlacement.choose(level, pos, state, IndividualCableColor.RED, CableRoute.X) == CableRoute.Z,
                "Equal connected alternatives use stable turn-distance then route order");
        h.assertTrue(level.getBlockState(pos) == state, "Candidate evaluation is read-only");
        h.succeed();
    }
}
