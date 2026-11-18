package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.registry.*;
import com.simibubi.create.AllItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndividualCableGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void segmentsCooccupancyAndDrops(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 3, 3));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (var first : java.util.List.of(IndividualCableColor.RED, IndividualCableColor.YELLOW)) {
            var second = first == IndividualCableColor.RED ? IndividualCableColor.YELLOW : IndividualCableColor.RED;
            var stack = new ItemStack(first.item(), 8); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            h.assertTrue(stack.getItem().useOn(context(player, pos, Direction.UP)).consumesAction() && stack.getCount() == 7, "First color places and costs one");
            var before = level.getBlockState(pos);
            h.assertTrue(stack.getItem().useOn(context(player, pos, Direction.UP)) == InteractionResult.FAIL && stack.getCount() == 7, "Duplicate color rejected without consumption");
            var other = new ItemStack(second.item(), 8); player.setItemInHand(InteractionHand.MAIN_HAND, other);
            h.assertTrue(other.getItem().useOn(context(player, pos, Direction.NORTH)).consumesAction() && other.getCount() == 7, "Second color shares the host in either order");
            var state = level.getBlockState(pos);
            h.assertTrue(state.getValue(first.property) == before.getValue(first.property)
                    && state.getValue(second.property) == CableRoute.WALL_NORTH, "Independent vertical orientation preserves first component");
            h.assertTrue(state.getCollisionShape(level, pos).isEmpty() && !state.getShape(level, pos).isEmpty(), "Thin selection without movement collision");
            var host = (IndividualCableBlockEntity) level.getBlockEntity(pos);
            h.assertTrue(ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().getTicker(level, state, host.getType()) == null, "No host ticker");
            var restored = BlockEntity.loadStatic(pos, state, host.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            h.assertTrue(restored instanceof IndividualCableBlockEntity loaded && loaded.token(first) == host.token(first), "Endpoint identity survives NBT reload");
            h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    net.minecraft.nbt.NbtUtils.writeBlockState(state)) == state, "Independent routes survive serialization");
            var drops = Block.getDrops(state, level, pos, host);
            h.assertTrue(drops.size() == 2 && drops.stream().anyMatch(s -> s.is(first.item())) && drops.stream().anyMatch(s -> s.is(second.item())), "Drops contain both colored pieces");
            level.destroyBlock(pos, false);
        }
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        var stack = new ItemStack(ModItems.RED_CONTROL_CABLE.get(), 8); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.assertTrue(stack.getItem().useOn(context(player, pos, Direction.UP)) == InteractionResult.FAIL && stack.getCount() == 8,
                "Cannot overwrite occupied solid positions");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void targetedWrenchRemoval(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 3, 3));
        level.setBlockAndUpdate(pos, both());
        var rotating = h.makeMockPlayer(GameType.SURVIVAL);
        rotating.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        wrenchAt(rotating, pos, IndividualCableColor.RED);
        h.assertTrue(level.getBlockState(pos).getValue(IndividualCableColor.RED.property) != CableRoute.Z
                && IndividualCableGeometry.permits(level.getBlockState(pos))
                && level.getBlockState(pos).getValue(IndividualCableColor.YELLOW.property) == CableRoute.Z,
                "Normal wrench rotates only the targeted color and skips intersecting floor routes");
        for (var removed : java.util.List.of(IndividualCableColor.RED, IndividualCableColor.YELLOW)) {
            level.setBlockAndUpdate(pos, both());
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack()); player.setShiftKeyDown(true);
            wrenchAt(player, pos, removed);
            var survivor = removed == IndividualCableColor.RED ? IndividualCableColor.YELLOW : IndividualCableColor.RED;
            h.assertTrue(level.getBlockState(pos).getValue(removed.property) == CableRoute.NONE
                    && level.getBlockState(pos).getValue(survivor.property) == CableRoute.Z, "Only targeted color removed");
            h.assertTrue(player.getInventory().countItem(removed.item()) == 1, "Removed piece returned once");
            wrenchAt(player, pos, survivor);
            h.assertTrue(level.getBlockState(pos).isAir() && player.getInventory().countItem(survivor.item()) == 1, "Final removal cleans host");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coloredSpansPersistenceAndRemoval(GameTestHelper h) {
        var level = h.getLevel(); var from = h.absolutePos(new BlockPos(2, 3, 2)); var to = from.east(5);
        level.setBlockAndUpdate(from, both()); level.setBlockAndUpdate(to, both());
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(true);
        for (var color : java.util.List.of(IndividualCableColor.RED, IndividualCableColor.YELLOW)) {
            var stack = new ItemStack(color.item(), 32); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            h.assertTrue(stack.getItem().useOn(context(player, from, Direction.UP)).consumesAction() && stack.getCount() == 32, "Selecting first anchor costs nothing");
            h.assertTrue(stack.getItem().useOn(context(player, to, Direction.UP)).consumesAction() && stack.getCount() == 27, "Span placement pays length-based cable cost");
            stack.getItem().useOn(context(player, from, Direction.UP));
            h.assertTrue(stack.getItem().useOn(context(player, to, Direction.UP)) == InteractionResult.FAIL && stack.getCount() == 27, "Duplicate same-color span rejected");
        }
        var ledger = CableSpans.get(level);
        h.assertTrue(ledger.at(from).size() == 2 && ledger.at(to).size() == 2, "Both colors connect identical endpoints");
        var loaded = CableSpans.load(ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(new java.util.HashSet<>(loaded.at(from)).equals(new java.util.HashSet<>(ledger.at(from))), "Span saved data round-trips");
        for (var color : java.util.List.of(IndividualCableColor.RED, IndividualCableColor.YELLOW)) h.assertTrue(IndividualCableTopology.links(level, from, color).stream()
                .filter(IndividualCableTopology.Link::span).count() == 1, "Topology retains per-color identity");
        var host = (IndividualCableBlockEntity) level.getBlockEntity(from);
        var client = new IndividualCableBlockEntity(from, both()); client.loadWithComponents(host.getUpdateTag(level.registryAccess()), level.registryAccess());
        h.assertTrue(client.spans().size() == 2, "Endpoint update packet data includes both spans");
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack()); wrenchAt(player, from, IndividualCableColor.RED);
        h.assertTrue(ledger.at(from).size() == 1 && ledger.at(from).getFirst().color() == IndividualCableColor.YELLOW
                && level.getBlockState(from) == both(), "Span removal preserves other color and anchors");
        h.assertTrue(player.getInventory().countItem(ModItems.RED_CONTROL_CABLE.get()) == 5, "Span cost refunded once");
        level.destroyBlock(from, true);
        h.assertTrue(ledger.at(to).isEmpty(), "Endpoint destruction removes incident span records");
        var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(from).inflate(2));
        h.assertTrue(items.stream().filter(e -> e.getItem().is(ModItems.YELLOW_CONTROL_CABLE.get())).mapToInt(e -> e.getItem().getCount()).sum() == 6,
                "Endpoint break drops one segment plus span material, without duplication");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancellationAndElectricalIsolation(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 3, 3));
        level.setBlockAndUpdate(pos, both()); level.setBlockAndUpdate(pos.south(), both());
        var ledger = CableSpans.get(level);
        h.assertTrue(!ledger.connect(IndividualCableColor.RED, pos, pos, 1) && !ledger.connect(IndividualCableColor.RED, pos, pos.east(31), 31), "Same endpoint and excessive span length rejected");
        var remote = new BlockPos(28_000_000, pos.getY(), 28_000_000);
        h.assertTrue(CableSpans.endpoint(level, remote, IndividualCableColor.RED) == null
                && level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "No forced chunk loads");
        h.assertTrue(IndividualCableTopology.links(level, pos, IndividualCableColor.RED).size() == 1
                && IndividualCableTopology.links(level, pos, IndividualCableColor.YELLOW).size() == 1, "Same-color straight topology remains independent");
        h.assertTrue(!CableNetworks.get(level).inspect(pos, IndividualCableColor.RED).hasTray(), "An isolated individual route does not touch a tray");
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.RED_CONTROL_CABLE.get(), 16); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().useOn(context(player, pos, Direction.UP)); level.destroyBlock(pos, false); level.setBlockAndUpdate(pos, both());
        h.assertTrue(stack.getItem().useOn(context(player, pos.south(), Direction.UP)) == InteractionResult.FAIL && stack.getCount() == 16, "Replacing starting anchor invalidates selection without consumption");
        stack.getItem().useOn(context(player, pos, Direction.UP));
        stack.getItem().inventoryTick(stack, level, player, 0, false);
        h.assertTrue(!stack.has(ModDataComponents.CABLE_FROM.get()), "Switching away cancels pending span");
        h.succeed();
    }

    private static BlockState both() { return ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState()
            .setValue(IndividualCableColor.RED.property, CableRoute.Z).setValue(IndividualCableColor.YELLOW.property, CableRoute.Z); }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void woodenBeamStrutRegression(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2)); var peer = pos.east(4);
        var block = ModBlocks.WOODEN_BEAMS.values().iterator().next().get();
        level.setBlockAndUpdate(pos, block.defaultBlockState()); level.setBlockAndUpdate(peer, block.defaultBlockState());
        var first = (com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity) level.getBlockEntity(pos);
        var second = (com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity) level.getBlockEntity(peer);
        first.addConnection(peer, Direction.UP); second.addConnection(pos, Direction.UP);
        h.assertTrue(first.hasConnectionTo(peer) && second.hasConnectionTo(pos), "Existing Wooden Beam Strut connections remain reciprocal");
        var saved = first.saveWithFullMetadata(level.registryAccess());
        var loaded = (com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamBlockEntity)
                BlockEntity.loadStatic(pos, first.getBlockState(), saved, level.registryAccess());
        h.assertTrue(loaded.hasConnectionTo(peer) && CableSpans.get(level).at(pos).isEmpty(), "Wooden Beam persistence stays separate from individual cable spans");
        first.removeConnection(peer); second.removeConnection(pos);
        h.assertTrue(first.connectionCount() == 0 && second.connectionCount() == 0, "Original Strut removal still works");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void endpointReloadAndPartialRemoval(GameTestHelper h) {
        var level = h.getLevel(); var from = h.absolutePos(new BlockPos(3, 3, 3)); var to = from.east(4);
        level.setBlockAndUpdate(from, both()); level.setBlockAndUpdate(to, both());
        var ledger = CableSpans.get(level);
        h.assertTrue(ledger.connect(IndividualCableColor.RED, from, to, 4)
                && ledger.connect(IndividualCableColor.YELLOW, from, to, 4), "Both spans created");
        var peer = (IndividualCableBlockEntity) level.getBlockEntity(to);
        var saved = peer.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(to);
        ledger.validate(from);
        h.assertTrue(ledger.at(from).size() == 2, "A still-loading peer block entity does not invalidate saved endpoint geometry");
        var reloaded = (IndividualCableBlockEntity) BlockEntity.loadStatic(to, both(), saved, level.registryAccess());
        level.setBlockEntity(reloaded); reloaded.onLoad();
        h.assertTrue(reloaded.spans().size() == 2, "Reloading endpoint restores its connections from saved ledger");
        level.removeBlockEntity(to);
        level.setBlockAndUpdate(from, both().setValue(IndividualCableColor.RED.property, CableRoute.NONE));
        h.assertTrue(ledger.at(to).size() == 1 && ledger.at(to).getFirst().color() == IndividualCableColor.YELLOW,
                "Removing one color cleans only its span while peer entity is unavailable");
        var again = (IndividualCableBlockEntity) BlockEntity.loadStatic(to, both(), saved, level.registryAccess());
        level.setBlockEntity(again); again.onLoad();
        h.assertTrue(again.spans().size() == 1 && again.spans().getFirst().color() == IndividualCableColor.YELLOW,
                "Reload never restores removed spans or duplicates refunds");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void severalSpansAndCreativeAccounting(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 3, 3));
        for (BlockPos end : java.util.List.of(pos, pos.east(4), pos.south(4))) level.setBlockAndUpdate(end, both());
        var ledger = CableSpans.get(level);
        h.assertTrue(ledger.connect(IndividualCableColor.RED, pos, pos.east(4), 4)
                && ledger.connect(IndividualCableColor.RED, pos, pos.south(4), 4)
                && ledger.connect(IndividualCableColor.YELLOW, pos, pos.east(4), 4), "One anchor holds several distinct spans and colors");
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        wrenchAt(player, pos, IndividualCableColor.RED);
        h.assertTrue(ledger.at(pos).size() == 2 && ledger.at(pos).stream().filter(s -> s.color() == IndividualCableColor.RED).count() == 1,
                "Removing one red span preserves adjacent red and yellow spans");
        h.assertTrue(player.getInventory().countItem(ModItems.RED_CONTROL_CABLE.get()) == 4, "Exactly one span cost is returned");
        var creative = h.makeMockPlayer(GameType.CREATIVE); creative.setShiftKeyDown(true);
        creative.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        wrenchAt(creative, pos, IndividualCableColor.RED);
        h.assertTrue(creative.getInventory().countItem(ModItems.RED_CONTROL_CABLE.get()) == 0, "Creative span removal creates no material refunds");
        var state = level.getBlockState(pos); state.getBlock().playerWillDestroy(level, pos, state, creative); level.destroyBlock(pos, false);
        h.assertTrue(ledger.at(pos).isEmpty() && level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(),
                "Creative endpoint destruction clears records without item duplication");
        var stack = new ItemStack(ModItems.RED_CONTROL_CABLE.get()); creative.setItemInHand(InteractionHand.MAIN_HAND, stack); creative.setShiftKeyDown(false);
        h.assertTrue(stack.getItem().useOn(context(creative, pos, Direction.UP)).consumesAction() && stack.getCount() == 1,
                "Creative straight placement costs nothing");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void resourcesGeometryAndInsufficientMaterials(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 3, 3));
        for (var color : java.util.List.of(IndividualCableColor.RED, IndividualCableColor.YELLOW)) {
            try (var stream = IndividualCableGameTests.class.getResourceAsStream("/assets/create_industrial_details/models/" + color.modelPath() + ".json")) {
                var model = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                h.assertTrue(model.getAsJsonArray("elements").size() == 1
                        && model.getAsJsonObject("textures").get("0").getAsString().equals("create_industrial_details:block/cable_tray/cable_tray"),
                        "Original single cuboid references the existing tray texture");
            }
            for (CableRoute route : java.util.List.of(CableRoute.X, CableRoute.Y, CableRoute.Z)) {
                var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(color.property, route);
                h.assertTrue(state.getShape(level, pos) == state.getShape(level, pos) && !state.getShape(level, pos).isEmpty(), "All three orientations use cached thin geometry");
            }
        }
        h.assertTrue(!IndividualCableGeometry.permits(both().setValue(IndividualCableColor.YELLOW.property, CableRoute.X)),
                "Supplied perpendicular floor cable profiles cannot be placed intersecting each other");
        level.setBlockAndUpdate(pos, both()); level.setBlockAndUpdate(pos.east(5), both());
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.YELLOW_CONTROL_CABLE.get(), 2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().useOn(context(player, pos, Direction.UP));
        h.assertTrue(stack.getItem().useOn(context(player, pos.east(5), Direction.UP)) == InteractionResult.FAIL
                && stack.getCount() == 2 && CableSpans.get(level).at(pos).isEmpty(), "Insufficient span material never consumes items or creates partial connections");
        h.succeed();
    }
    private static void wrenchAt(Player player, BlockPos pos, IndividualCableColor color) {
        var point = new Vec3(pos.getX() + (color.minX + .75) / 16, pos.getY() + 2.5 / 16, pos.getZ() + .5);
        player.setPos(point.x, point.y + 2 - player.getEyeHeight(), point.z);
        AllItems.WRENCH.get().useOn(new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), new BlockHitResult(point, Direction.UP, pos, false)));
    }
    private static UseOnContext context(Player p, BlockPos pos, Direction face) {
        return new UseOnContext(p.level(), p, InteractionHand.MAIN_HAND, p.getMainHandItem(), new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
}
