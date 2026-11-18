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
public class IndividualCableUpAndOverGameTests {
    private static InteractionResult click(FakePlayer player, BlockPos pos, Direction side) {
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false));
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void completeRoutesInAllCardinalDirections(GameTestHelper h) {
        var level = h.getLevel(); var center = h.absolutePos(new BlockPos(2, 3, 2));
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "up-and-over"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        for (var direction : Direction.Plane.HORIZONTAL) {
            for (var pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 1, 1))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
            var entrance = center.relative(direction.getOpposite()); var exit = center.relative(direction); var top = center.above();
            player.setYRot(direction.toYRot()); var floor = CableRoute.floor(direction);
            for (var color : IndividualCableColor.values()) {
                var stack = new ItemStack(color.item(), 16); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(click(player, entrance, Direction.UP).consumesAction() && click(player, exit, Direction.UP).consumesAction()
                        && click(player, center, Direction.UP).consumesAction(), "Ground and top sections place through normal dispatch");
                h.assertTrue(click(player, center, direction.getOpposite()).consumesAction(), "Upward transition keeps its valid normal orientation");
                var up = (IndividualCableBlockEntity) level.getBlockEntity(entrance);
                h.assertTrue(up.secondary(color) == CableRoute.wall(direction.getOpposite()), "Valid original wall orientation is preserved");
                var down = (IndividualCableBlockEntity) level.getBlockEntity(exit);
                var normal = CableRoute.wall(direction);
                h.assertTrue(!down.canAdd(color, normal), "Opposite wall default reverses the floor lane and is rejected");
                var selected = IndividualCablePlacement.choose(level, exit, down.getBlockState(), color, normal);
                h.assertTrue(selected.reversedWall() && down.canAdd(color, selected), "A same-plane X=90 wall variant resolves the conflict");
                h.assertTrue(click(player, center, direction).consumesAction() && stack.getCount() == 11, "Downward transition succeeds and pays exactly once");
                h.assertTrue(down.secondary(color) == selected && down.getBlockState().getValue(color.property) == floor
                        && up.getBlockState().getValue(color.property) == floor && level.getBlockState(top).getValue(color.property) == floor,
                        "Only the new downward component changes orientation");
                var oldPlane = IndividualCableGeometry.part(color, normal).bounds(); var newPlane = IndividualCableGeometry.part(color, selected).bounds();
                h.assertTrue(direction.getAxis() == Direction.Axis.X ? oldPlane.minX == newPlane.minX && oldPlane.maxX == newPlane.maxX
                        : oldPlane.minZ == newPlane.minZ && oldPlane.maxZ == newPlane.maxZ, "Corrected cable remains against the clicked wall");
                h.assertTrue(IndividualCableBends.find(level, entrance, up.getBlockState()).stream().anyMatch(b -> b.color() == color && b.peer().equals(top))
                        && IndividualCableBends.find(level, exit, down.getBlockState()).stream().anyMatch(b -> b.color() == color && b.peer().equals(top)),
                        "Both actual top endpoint corners connect continuously");
                h.assertTrue(IndividualCableTopology.connectedWithin(up, color, floor, up.secondary(color))
                        && IndividualCableTopology.connectedWithin(down, color, floor, selected), "Both ground transitions connect their same-colored components");
                var roundTrip = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(exit, down.getBlockState(), down.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                h.assertTrue(roundTrip instanceof IndividualCableBlockEntity saved && saved.secondary(color) == selected, "New wall variants survive saved host data");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void newWallModelsAndLegacyIds(GameTestHelper h) throws Exception {
        h.assertTrue(CableRoute.X.ordinal() == 1 && CableRoute.Y.ordinal() == 2 && CableRoute.Z.ordinal() == 3 && CableRoute.WALL_WEST.ordinal() == 8,
                "Existing saved route ordinals are unchanged");
        try (var stream = IndividualCableUpAndOverGameTests.class.getResourceAsStream("/assets/create_industrial_details/blockstates/individual_control_cable.json")) {
            var parts = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject().getAsJsonArray("multipart");
            for (var color : IndividualCableColor.values()) for (var route : CableRoute.values()) {
                if (!route.reversedWall()) continue;
                var entry = parts.asList().stream().map(p -> p.getAsJsonObject())
                        .filter(p -> p.getAsJsonObject("when").has(color.property.getName()) && p.getAsJsonObject("when").get(color.property.getName()).getAsString().equals(route.getSerializedName()))
                        .findFirst().orElseThrow().getAsJsonObject("apply");
                h.assertTrue(entry.get("x").getAsInt() == 90 && (entry.has("y") ? entry.get("y").getAsInt() : 0) == route.turns() * 90
                        && entry.get("model").getAsString().equals("create_industrial_details:" + color.modelPath()), "New wall state reuses the canonical model with correct rotations");
            }
        }
        h.succeed();
    }
}
