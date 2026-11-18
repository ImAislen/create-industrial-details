package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.registry.*;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndividualCableRefinementGameTests {
    private static BlockState all(CableRoute route) {
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
        for (var color : IndividualCableColor.values()) state = state.setValue(color.property, route);
        return state;
    }
    private static FakePlayer player(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "cable-refinement"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return player;
    }
    private static InteractionResult click(FakePlayer player, BlockPos pos) {
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fourColorsAndOriginalModels(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2));
        h.assertTrue(IndividualCableColor.values().length == 4 && IndividualCableColor.RED.ordinal() == 0
                && IndividualCableColor.YELLOW.ordinal() == 1, "Existing serialized color IDs are preserved");
        level.setBlockAndUpdate(pos, all(CableRoute.Z));
        h.assertTrue(ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().getStateDefinition().getPossibleStates().size() == (int) Math.pow(CableRoute.values().length, 4), "One host represents four independent routes");
        for (var color : IndividualCableColor.values()) {
            try (var stream = IndividualCableRefinementGameTests.class.getResourceAsStream("/assets/create_industrial_details/models/" + color.modelPath() + ".json")) {
                var model = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
                var element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
                h.assertTrue(element.getAsJsonArray("from").get(0).getAsDouble() == color.minX
                        && element.getAsJsonArray("from").get(1).getAsDouble() == 0
                        && element.getAsJsonArray("to").get(1).getAsDouble() == 1, "Canonical lowered geometry and color lane used");
                h.assertTrue(model.getAsJsonObject("textures").get("0").getAsString().equals("create_industrial_details:block/cable_tray/cable_tray"), "Supplied UVs resolve the existing texture");
            }
            h.assertTrue(new ItemStack(color.item()).getDescriptionId().endsWith(color.property.getName() + "_control_cable"), "Four obtainable colored items");
            var box = IndividualCableGeometry.part(color, CableRoute.Z).bounds();
            h.assertTrue(box.minY == 0 && box.maxY == 1.0 / 16 && box.minX == color.minX / 16, "Selection follows lowered source geometry");
        }
        var drops = net.minecraft.world.level.block.Block.getDrops(all(CableRoute.Z), level, pos, level.getBlockEntity(pos));
        h.assertTrue(drops.size() == 4 && drops.stream().map(ItemStack::getItem).distinct().count() == 4, "Host drops each color once");
        var player = player(h); player.setShiftKeyDown(true); player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        for (var color : IndividualCableColor.values()) {
            var point = IndividualCableGeometry.anchor(pos, color, CableRoute.Z).add(0, .5 / 16, 0);
            player.setPos(point.x, point.y + 2 - player.getEyeHeight(), point.z);
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(point, Direction.UP, pos, false));
            h.assertTrue(player.getInventory().countItem(color.item()) == 1, "Targeted native wrench removes only its colored segment");
            for (var remaining : IndividualCableColor.values()) if (remaining.ordinal() > color.ordinal())
                h.assertTrue(level.getBlockState(pos).getValue(remaining.property) == CableRoute.Z, "Other color routes survive removal");
        }
        h.assertTrue(level.getBlockState(pos).isAir(), "Last color cleans up shared host");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void endAttachmentsAndCurveTargeting(GameTestHelper h) {
        var a = h.absolutePos(new BlockPos(2, 3, 2));
        for (var color : IndividualCableColor.values()) for (var route : List.of(CableRoute.X, CableRoute.Y, CableRoute.Z)) {
            var b = route == CableRoute.X ? a.east(5).above(2) : route == CableRoute.Y ? a.above(5).east(2) : a.south(5).above(2);
            var span = new CableSpans.Span(color, a, b, route, route, 6);
            var from = CableSpanGeometry.from(span); var to = CableSpanGeometry.to(span);
            h.assertTrue(from.equals(IndividualCableGeometry.end(a, color, route, 1))
                    && to.equals(IndividualCableGeometry.end(b, color, route, 0)), "Appropriate positive and negative cable ends are used");
            var bounds = IndividualCableGeometry.part(color, route).bounds().move(a).inflate(1e-8);
            h.assertTrue(bounds.contains(from), "Attachment is on the real cable profile");
            var index = new CableSpanIndex(); index.add(span);
            var points = CableSpanGeometry.points(span); var middle = points.get(points.size() / 2);
            var right = route == CableRoute.Y ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            var hit = index.trace(middle.add(right), middle.subtract(right));
            h.assertTrue(hit != null && hit.span().equals(span), "Actual sagging curve is targetable far from either anchor");
            index.remove(span);
            h.assertTrue(index.trace(middle.add(right), middle.subtract(right)) == null, "Removing record clears spatial lookup");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spatialIndexOwnershipAndNearestHit(GameTestHelper h) {
        var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.south(4);
        var lower = new CableSpans.Span(IndividualCableColor.RED, a, b, CableRoute.Z, CableRoute.Z, 4);
        var higher = new CableSpans.Span(IndividualCableColor.RED, a.above(), b.above(), CableRoute.Z, CableRoute.Z, 4);
        var index = new CableSpanIndex(); index.add(lower); index.add(higher);
        var points = CableSpanGeometry.points(lower); var point = points.get(points.size() / 2);
        h.assertTrue(index.trace(point.add(0, 2, 0), point.subtract(0, 2, 0)).span().equals(higher), "Ray selects nearest actual curve when several spans are on its path");
        var clientIndex = new CableSpanIndex(); clientIndex.replace(a, List.of(lower)); clientIndex.replace(b, List.of(lower));
        clientIndex.forget(a);
        h.assertTrue(clientIndex.contains(lower) && clientIndex.trace(point.add(0, 1, 0), point.subtract(0, 1, 0)) != null, "One loaded endpoint retains span targeting when its peer unloads");
        clientIndex.forget(b);
        h.assertTrue(!clientIndex.contains(lower) && clientIndex.trace(point.add(0, 1, 0), point.subtract(0, 1, 0)) == null, "Unloading last owner clears client spatial state");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nearestSpanAndSafeDirectRemoval(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 4, 2)); var b = a.south(5);
        level.setBlockAndUpdate(a, all(CableRoute.Z)); level.setBlockAndUpdate(b, all(CableRoute.Z));
        var ledger = CableSpans.get(level);
        for (var color : IndividualCableColor.values()) h.assertTrue(ledger.connect(color, a, b, 5), "All colors span shared endpoints");
        var player = player(h); player.setShiftKeyDown(true); player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        for (var color : IndividualCableColor.values()) {
            var span = ledger.at(a).stream().filter(s -> s.color() == color).findFirst().orElseThrow();
            var points = CableSpanGeometry.points(span); var middle = points.get(points.size() / 2);
            player.setPos(middle.x, middle.y + 1.5 - player.getEyeHeight(), middle.z); player.setXRot(90); player.setYRot(0);
            var rayStart = player.getEyePosition(); var rayEnd = rayStart.add(0, -player.blockInteractionRange(), 0);
            var hit = ledger.spatial().trace(rayStart, rayEnd);
            h.assertTrue(hit != null && hit.span().color() == color, "Nearest curve hit keeps color identity");
            var request = CableSpanRemoval.Request.of(span);
            h.assertTrue(!CableSpanRemoval.remove(player, new CableSpanRemoval.Request(a.east(100), b, color.ordinal())), "Forged remote endpoint request rejected");
            var wall = BlockPos.containing(middle.x, middle.y + .7, middle.z);
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            h.assertTrue(!CableSpanRemoval.remove(player, request), "Solid obstruction prevents through-wall wrench removal");
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            h.assertTrue(CableSpanRemoval.remove(player, request), "Server validates and removes the directly targeted span");
            h.assertTrue(!CableSpanRemoval.remove(player, request) && player.getInventory().countItem(color.item()) == 5, "Replay cannot refund a span twice");
            h.assertTrue(level.getBlockState(a) == all(CableRoute.Z) && level.getBlockState(b) == all(CableRoute.Z), "Both anchor hosts remain intact");
        }
        h.assertTrue(ledger.at(a).isEmpty(), "Each span removed independently");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placementSoundsOnceAndOnlyOnSuccess(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.south(4);
        level.setBlockAndUpdate(a, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
        var player = player(h); int[] sounds = {0};
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == level && event.getSound() != null && event.getSound().value() == SoundEvents.METAL_PLACE
                    && (BlockPos.containing(event.getPosition()).equals(a) || BlockPos.containing(event.getPosition()).equals(b))) sounds[0]++;
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for (var color : IndividualCableColor.values()) {
                player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(color.item(), 16));
                int before = sounds[0];
                h.assertTrue(click(player, a).consumesAction() && sounds[0] == before + 1, "Each color addition plays one placement sound");
                h.assertTrue(click(player, a) == InteractionResult.FAIL && sounds[0] == before + 1, "Rejected duplicate segment is silent");
                click(player, b); player.setShiftKeyDown(true); before = sounds[0];
                click(player, a); h.assertTrue(sounds[0] == before, "Selecting first endpoint is silent");
                h.assertTrue(click(player, b).consumesAction() && sounds[0] == before + 1, "Successful span plays one sound");
                click(player, a); h.assertTrue(click(player, b) == InteractionResult.FAIL && sounds[0] == before + 1, "Rejected span is silent");
                click(player, a); h.assertTrue(sounds[0] == before + 1, "Cancellation is silent");
            }
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void automaticBendsFollowNeighbors(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.above();
        level.setBlockAndUpdate(a, all(CableRoute.Y)); level.setBlockAndUpdate(b, all(CableRoute.Z));
        var bends = IndividualCableBends.find(level, a, all(CableRoute.Y));
        h.assertTrue(bends.stream().filter(bend -> bend.peer().equals(b)).count() == 4, "Each color gets a minimal floor-to-wall bend");
        for (var color : IndividualCableColor.values()) {
            var bend = bends.stream().filter(c -> c.color() == color && c.peer().equals(b)).findFirst().orElseThrow();
            h.assertTrue(!bend.needsInfill(), "Touching straight profiles need no filler on the inner corner");
            h.assertTrue(bend.points().getFirst().equals(IndividualCableGeometry.end(a, color, CableRoute.Y, 1))
                    && bend.points().getLast().equals(IndividualCableGeometry.end(b, color, CableRoute.Z, 0)), "Bend connects physical ends in its own lane");
            h.assertTrue(IndividualCableTopology.links(level, a, color).stream().anyMatch(link -> link.peer().equals(b) && !link.span()), "Future same-color topology can reuse bend detection");
        }
        var host = (IndividualCableBlockEntity) level.getBlockEntity(a); int revision = host.revision;
        level.setBlockAndUpdate(b, all(CableRoute.Z).setValue(IndividualCableColor.BLUE.property, CableRoute.NONE));
        h.assertTrue(host.revision > revision && IndividualCableBends.find(level, a, all(CableRoute.Y)).stream().noneMatch(bend -> bend.peer().equals(b) && bend.color() == IndividualCableColor.BLUE),
                "Neighbor state change invalidates cached bend rendering for removed color");
        level.setBlockAndUpdate(b, all(CableRoute.X));
        h.assertTrue(IndividualCableBends.find(level, a, all(CableRoute.Y)).stream().noneMatch(bend -> bend.peer().equals(b)), "Unrelated perpendicular paths do not gain connectors");
        level.destroyBlock(b, false);
        h.assertTrue(IndividualCableBends.find(level, a, all(CableRoute.Y)).stream().noneMatch(bend -> bend.peer().equals(b)), "Removing neighbor removes dependent bends");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyRecordsAndPendingState(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.south(4);
        level.setBlockAndUpdate(a, all(CableRoute.Z)); level.setBlockAndUpdate(b, all(CableRoute.Z));
        var ledger = CableSpans.get(level);
        for (var color : IndividualCableColor.values()) ledger.connect(color, a, b, 4);
        var restored = CableSpans.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(new HashSet<>(restored.at(a)).equals(new HashSet<>(ledger.at(a))), "Four-color saved records preserve routes, IDs and cost");
        var host = (IndividualCableBlockEntity) level.getBlockEntity(a);
        var oldTag = host.saveWithFullMetadata(level.registryAccess()); oldTag.putLongArray("Tokens", new long[]{host.token(IndividualCableColor.RED), host.token(IndividualCableColor.YELLOW)});
        var oldHost = (IndividualCableBlockEntity) BlockEntity.loadStatic(a, all(CableRoute.Z), oldTag, level.registryAccess());
        h.assertTrue(oldHost.token(IndividualCableColor.RED) == host.token(IndividualCableColor.RED)
                && oldHost.token(IndividualCableColor.YELLOW) == host.token(IndividualCableColor.YELLOW), "Legacy two-color endpoint tokens survive extension");
        var oldStateTag = net.minecraft.nbt.NbtUtils.writeBlockState(all(CableRoute.Z));
        oldStateTag.getCompound("Properties").remove("green"); oldStateTag.getCompound("Properties").remove("blue");
        var oldState = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), oldStateTag);
        h.assertTrue(oldState.getValue(IndividualCableColor.RED.property) == CableRoute.Z && oldState.getValue(IndividualCableColor.YELLOW.property) == CableRoute.Z
                && oldState.getValue(IndividualCableColor.GREEN.property) == CableRoute.NONE && oldState.getValue(IndividualCableColor.BLUE.property) == CableRoute.NONE,
                "Legacy two-property world states load with new colors absent");
        for (var color : List.of(IndividualCableColor.RED, IndividualCableColor.YELLOW)) {
            var span = ledger.at(a).stream().filter(s -> s.color() == color).findFirst().orElseThrow();
            h.assertTrue(CableSpans.Span.read(span.write()).equals(span), "Legacy color-keyed span records still read without migration");
        }
        var player = player(h); player.setShiftKeyDown(true);
        for (var color : IndividualCableColor.values()) {
            ledger.detach(a, color);
            var stack = new ItemStack(color.item(), 16); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            click(player, a); h.assertTrue(stack.has(ModDataComponents.CABLE_FROM.get()), "First native click activates preview data for this color");
            click(player, b); h.assertTrue(!stack.has(ModDataComponents.CABLE_FROM.get()), "Completion clears preview state");
            click(player, a); click(player, a); h.assertTrue(!stack.has(ModDataComponents.CABLE_FROM.get()), "Explicit cancellation clears preview state");
            click(player, a); level.destroyBlock(a, false); player.getInventory().tick();
            h.assertTrue(!stack.has(ModDataComponents.CABLE_FROM.get()), "Removing starting anchor clears pending placement on inventory update");
            level.setBlockAndUpdate(a, all(CableRoute.Z));
        }
        h.succeed();
    }
}
