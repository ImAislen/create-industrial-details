package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.*;
import static com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableColor.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class ServiceTrenchCableGameTests {
    @GameTest(template = "empty")
    public static void installAndMaintainCableTrench(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 3, 2));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var empty = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, Direction.EAST);
        level.setBlockAndUpdate(pos, empty);
        var first = new ItemStack(RED.item(), 2);
        helper.assertTrue(place(player, pos, first) == InteractionResult.FAIL && first.getCount() == 2,
                "A closed grille protects the lanes");
        level.setBlockAndUpdate(pos, empty.setValue(OPEN, true));
        for (var color : IndividualCableColor.values()) {
            var stack = color == RED ? first : new ItemStack(color.item(), 2);
            helper.assertTrue(place(player, pos, stack).consumesAction() && stack.getCount() == 1,
                    "Each cable color consumes one item and shares the housing");
        }
        helper.assertTrue(place(player, pos, first) == InteractionResult.FAIL && first.getCount() == 1,
                "Duplicates cannot occupy the same lane");
        var state = level.getBlockState(pos);
        var host = (IndividualCableBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(state.is(ModBlocks.SERVICE_TRENCH_CABLE.get()) && state.getValue(OPEN)
                        && state.getValue(FACING) == Direction.EAST
                        && !state.getValue(CAP_NEGATIVE) && !state.getValue(CAP_POSITIVE),
                "Installation preserves orientation and grille while leaving cable ends clear");
        var saved = host.saveWithFullMetadata(level.registryAccess());
        var reloaded = (IndividualCableBlockEntity) BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
        for (var color : IndividualCableColor.values())
            helper.assertTrue(reloaded.contains(color, CableRoute.X) && reloaded.token(color) == host.token(color),
                    "Existing cable block entity persists each lane identity");
        var drops = Block.getDrops(state, level, pos, host);
        helper.assertTrue(drops.size() == 5 && drops.stream().anyMatch(stack -> stack.is(ModBlocks.SERVICE_TRENCH.asItem())),
                "Breaking occupied housing drops the casing and each of its four lanes once");

        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.SHAFT.asStack());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, hit));
        helper.assertTrue(event.isCanceled() && event.getCancellationResult() == InteractionResult.FAIL
                && level.getBlockState(pos) == state, "A shaft cannot replace installed cables");

        player.setShiftKeyDown(true);
        for (var color : IndividualCableColor.values()) {
            var point = IndividualCableGeometry.anchor(pos, color, CableRoute.X);
            player.setPos(point.x, pos.getY() + 1, point.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            var cableHit = new BlockHitResult(point, Direction.UP, pos, false);
            helper.assertTrue(AllItems.WRENCH.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, cableHit)).consumesAction(),
                    "Open-grille wrench targets one cable");
            helper.assertTrue(player.getInventory().countItem(color.item()) == 1, "Wrench returns that color only");
            if (color != BLUE) {
                var remaining = (IndividualCableBlockEntity) level.getBlockEntity(pos);
                helper.assertTrue(remaining.routes(color).isEmpty() && remaining.contains(BLUE, CableRoute.X),
                        "Remaining cables retain their routes");
            }
        }
        helper.assertTrue(level.getBlockState(pos).is(empty.getBlock()) && level.getBlockState(pos).getValue(OPEN)
                        && level.getBlockState(pos).getValue(CAP_NEGATIVE) && level.getBlockEntity(pos) == null,
                "Final cable leaves the original open, capped trench without a block entity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trenchCableContactsAndCasingRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var trench = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, true);
        for (var target : new BlockPos[]{pos, pos.south()}) {
            level.setBlockAndUpdate(target, trench);
            helper.assertTrue(place(player, target, new ItemStack(RED.item())).consumesAction(), "Red lane is installed");
        }
        var ordinary = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState()
                .setValue(RED.property, CableRoute.Z).setValue(BLUE.property, CableRoute.Z);
        level.setBlockAndUpdate(pos.north(), ordinary);
        helper.assertTrue(!CableSpans.get(level).connect(RED, pos, pos.north(), 1),
                "The straight enclosed lanes do not serve as sagging-span anchors");
        var boxPos = pos.south(2);
        level.setBlockAndUpdate(boxPos, ModBlocks.CONTROL_BOX.get().defaultBlockState()
                .setValue(ControlBoxBlock.FACING, Direction.SOUTH));
        var box = (ControlBoxBlockEntity) level.getBlockEntity(boxPos);
        box.setSignal(RED.channel, 13);
        box.setSignal(BLUE.channel, 7);
        var manager = CableNetworks.get(level);
        for (int i = 0; i < 128; i++) manager.processPending();
        helper.assertTrue(manager.read(pos.north()).signal(RED.channel) == 13
                        && manager.read(pos.north()).signal(BLUE.channel) == 0,
                "Native Control Box contact drives red through two trenches and an ordinary cable; channels stay isolated");
        helper.assertTrue(!CableConnectionGeometry.steps(level, pos.north(), RED, CableRoute.Z).isEmpty(),
                "The existing step bridges a regular cable to the raised trench lane");

        level.setBlockAndUpdate(boxPos, ModBlocks.CABLE_TRAY.get().defaultBlockState()
                .setValue(CableTrayBlock.AXIS, Direction.Axis.Z).setValue(CableTrayBlock.FACING, Direction.NORTH));
        var contacts = CablePhysicalConnections.contacts(level, pos.south(), level.getBlockState(pos.south()), RED, CableRoute.Z);
        helper.assertTrue(contacts.stream().anyMatch(contact -> contact.peer().equals(boxPos)
                        && contact.cable().equals(contact.other())),
                "Aligned tray meets the raised lane at the native tray height");
        for (int i = 0; i < 128; i++) manager.processPending();
        helper.assertTrue(manager.inspect(pos.north(), RED).hasTray() && !manager.inspect(pos.north(), BLUE).hasTray(),
                "The trench-to-tray contact joins the existing red network only");

        var closed = level.getBlockState(pos).setValue(ServiceTrenchBlock.OPEN, false);
        level.setBlockAndUpdate(pos, closed);
        long token = ((IndividualCableBlockEntity) level.getBlockEntity(pos)).token(RED);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        helper.assertTrue(AllItems.WRENCH.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
                        && level.getBlockState(pos).is(ModBlocks.INDIVIDUAL_CONTROL_CABLE.get()),
                "Closed grille wrench removes only the casing");
        var exposed = (IndividualCableBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(exposed.contains(RED, CableRoute.Z) && exposed.token(RED) == token
                        && player.getInventory().countItem(ModBlocks.SERVICE_TRENCH.asItem()) == 1
                        && player.getInventory().countItem(RED.item()) == 0,
                "Decasing preserves the cable identity and returns only the trench item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trenchCableChunkLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var seed = helper.absolutePos(new BlockPos(2, 4, 2));
        var boundary = new BlockPos((seed.getX() & ~15) + 15, seed.getY(), seed.getZ());
        var next = boundary.east();
        level.getChunkAt(next);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var housing = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, Direction.EAST).setValue(OPEN, true);
        for (var pos : new BlockPos[]{boundary, next}) {
            level.setBlockAndUpdate(pos, housing);
            helper.assertTrue(place(player, pos, new ItemStack(RED.item())).consumesAction(), "Install raised X lanes across chunk boundary");
        }
        var source = boundary.west();
        level.setBlockAndUpdate(source, ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, Direction.WEST));
        ((ControlBoxBlockEntity) level.getBlockEntity(source)).setSignal(RED.channel, 12);
        var manager = CableNetworks.get(level);
        for (int i = 0; i < 128; i++) manager.processPending();
        helper.assertTrue(manager.read(next, RED).signal(RED.channel) == 12, "Trench cable conducts across the chunk boundary");
        var chunk = level.getChunkAt(boundary);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
        for (int i = 0; i < 128; i++) manager.processPending();
        helper.assertTrue(manager.read(next, RED).signal(RED.channel) == 0, "Chunk unload disconnects the trench cable network");
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false));
        for (int i = 0; i < 128; i++) manager.processPending();
        helper.assertTrue(manager.read(next, RED).signal(RED.channel) == 12, "Chunk load rebuilds connectivity from occupied lanes");
        var host = (IndividualCableBlockEntity) level.getBlockEntity(boundary);
        host.removeComponent(RED, CableRoute.X);
        for (int i = 0; i < 128; i++) manager.processPending();
        helper.assertTrue(manager.read(next, RED).signal(RED.channel) == 0, "Removing a lane disconnects the downstream trench");
        level.removeBlock(source, false);
        level.removeBlock(boundary, false);
        level.removeBlock(next, false);
        helper.succeed();
    }

    private static InteractionResult place(Player player, BlockPos pos, ItemStack stack) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return stack.getItem().useOn(new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, stack, hit));
    }
}
