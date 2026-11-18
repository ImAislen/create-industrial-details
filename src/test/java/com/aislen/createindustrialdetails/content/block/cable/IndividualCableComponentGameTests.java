package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.registry.*;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndividualCableComponentGameTests {
    private static FakePlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "component-cable"));
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return p;
    }
    private static InteractionResult click(FakePlayer p, BlockPos pos, Direction face) {
        return p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
    private static BlockHitResult aim(FakePlayer player, BlockPos pos, IndividualCableColor color, CableRoute route) {
        var point = IndividualCableGeometry.anchor(pos, color, route);
        var box = IndividualCableGeometry.part(color, route).bounds();
        Vec3 offset = !route.vertical() ? new Vec3(0, 2, 0) : box.getXsize() < box.getZsize() ? new Vec3(box.minX < .5 ? 2 : -2, 0, 0) : new Vec3(0, 0, box.minZ < .5 ? 2 : -2);
        var eye = point.add(offset); player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        return new BlockHitResult(point, Direction.UP, pos, false);
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void perpendicularPlacementRemovalAndPersistence(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2)); var p = player(h);
        for (var wallFace : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            p.setYRot(wallFace.getOpposite().toYRot());
            for (var color : IndividualCableColor.values()) {
                var stack = new ItemStack(color.item(), 16); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(click(p, pos, Direction.UP).consumesAction(), "Primary horizontal component places");
                h.assertTrue(click(p, pos, wallFace).consumesAction() && stack.getCount() == 14, "Same-colored vertical component joins for one item");
                h.assertTrue(click(p, pos, wallFace) == InteractionResult.FAIL && click(p, pos, Direction.UP) == InteractionResult.FAIL && stack.getCount() == 14, "Identical orientations cannot be duplicated");
            }
            var host = (IndividualCableBlockEntity) level.getBlockEntity(pos);
            var saved = (IndividualCableBlockEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos), host.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            var legacyTag = host.saveWithFullMetadata(level.registryAccess());
            legacyTag.remove("SecondaryRoutes"); legacyTag.remove("SecondaryTokens");
            var legacy = (IndividualCableBlockEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos), legacyTag, level.registryAccess());
            for (var color : IndividualCableColor.values()) h.assertTrue(legacy.routes(color).size() == 1 && legacy.token(color) == host.token(color), "Old single-component saves load without fabricating extra pieces");
            var synced = new IndividualCableBlockEntity(pos, level.getBlockState(pos));
            synced.loadWithComponents(host.getUpdateTag(level.registryAccess()), level.registryAccess());
            for (var color : IndividualCableColor.values()) {
                h.assertTrue(saved.routes(color).equals(host.routes(color)) && synced.routes(color).equals(host.routes(color)), "Both component orientations persist and synchronize");
                h.assertTrue(saved.token(color, saved.secondary(color)) == host.token(color, host.secondary(color)), "Secondary identity survives reload");
                h.assertTrue(IndividualCableTopology.connectedWithin(host, color, CableRoute.floor(wallFace.getOpposite()), CableRoute.wall(wallFace)), "Same-color perpendicular route is electrically representable");
            }
            h.assertTrue(Block.getDrops(level.getBlockState(pos), level, pos, host).size() == 8, "Survival loot accounts for every physical piece");
            h.assertTrue(host.getType() == ModBlockEntities.INDIVIDUAL_CONTROL_CABLE.get()
                    && ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().getTicker(level, level.getBlockState(pos), host.getType()) == null, "One existing non-ticking host holds all components");
            for (var color : IndividualCableColor.values()) {
                for (var route : List.of(CableRoute.floor(wallFace.getOpposite()), CableRoute.wall(wallFace))) {
                    var hit = aim(p, pos, color, route);
                    var picked = level.getBlockState(pos).getBlock().getCloneItemStack(level.getBlockState(pos), hit, level, pos, p);
                    h.assertTrue(picked.is(color.item()), "Pick Block resolves either physical component");
                    p.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack()); p.setShiftKeyDown(true);
                    p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    if (!level.getBlockState(pos).isAir()) {
                        var current = (IndividualCableBlockEntity) level.getBlockEntity(pos);
                        h.assertTrue(!current.contains(color, route), "Only the targeted orientation was removed");
                    }
                }
                h.assertTrue(p.getInventory().countItem(color.item()) >= 2, "Both removed components return their items");
            }
            h.assertTrue(level.getBlockState(pos).isAir(), "Last component removes host");
            p.setShiftKeyDown(false);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void secondarySpanIdentityAndPromotion(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2)); var peer = pos.south(4);
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(IndividualCableColor.RED.property, CableRoute.Z);
        level.setBlockAndUpdate(pos, state); level.setBlockAndUpdate(peer, state);
        var a = (IndividualCableBlockEntity) level.getBlockEntity(pos); var b = (IndividualCableBlockEntity) level.getBlockEntity(peer);
        h.assertTrue(a.add(IndividualCableColor.RED, CableRoute.Y) && b.add(IndividualCableColor.RED, CableRoute.Y), "Both hosts have perpendicular Red components");
        var spans = CableSpans.get(level);
        h.assertTrue(spans.connect(IndividualCableColor.RED, pos, peer, CableRoute.Z, CableRoute.Z, 4)
                && spans.connect(IndividualCableColor.RED, pos, peer, CableRoute.Y, CableRoute.Y, 4), "Independent same-color component pairs support distinct span records");
        long identity = a.token(IndividualCableColor.RED, CableRoute.Y);
        a.removeComponent(IndividualCableColor.RED, CableRoute.Z);
        h.assertTrue(a.contains(IndividualCableColor.RED, CableRoute.Y) && a.token(IndividualCableColor.RED, CableRoute.Y) == identity, "Promoted surviving component retains its identity");
        h.assertTrue(spans.at(pos).size() == 1 && spans.at(pos).getFirst().fromRoute() == CableRoute.Y, "Only spans dependent on removed orientation are detached");
        spans.validate(pos);
        h.assertTrue(spans.at(pos).size() == 1, "Secondary and promoted routes validate correctly against saved spans");
        var saved = CableSpans.load(spans.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(saved.at(pos).equals(spans.at(pos)), "Existing span serialization retains component routes");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeSecondaryAnchorSelection(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2)); var peer = pos.south(4); var p = player(h);
        for (var color : IndividualCableColor.values()) {
            var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(color.property, CableRoute.Z);
            level.setBlockAndUpdate(pos, state); level.setBlockAndUpdate(peer, state);
            ((IndividualCableBlockEntity) level.getBlockEntity(pos)).add(color, CableRoute.Y);
            ((IndividualCableBlockEntity) level.getBlockEntity(peer)).add(color, CableRoute.Y);
            var stack = new ItemStack(color.item(), 16); p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.setShiftKeyDown(true);
            var first = aim(p, pos, color, CableRoute.Y);
            p.gameMode.useItemOn(p, level, stack, InteractionHand.MAIN_HAND, first);
            h.assertTrue(stack.getOrDefault(ModDataComponents.CABLE_FROM_ROUTE.get(), 0) == CableRoute.Y.ordinal(), "First click stores the actually targeted component route");
            p.setShiftKeyDown(false); var second = aim(p, peer, color, CableRoute.Y);
            h.assertTrue(p.gameMode.useItemOn(p, level, stack, InteractionHand.MAIN_HAND, second).consumesAction(), "Normal click completes span on the targeted secondary route");
            var record = CableSpans.get(level).at(pos).stream().filter(s -> s.color() == color).findFirst().orElseThrow();
            h.assertTrue(record.fromRoute() == CableRoute.Y && record.toRoute() == CableRoute.Y && stack.getCount() == 12, "Both route-specific endpoints and cost are correct");
            h.assertTrue(!stack.has(ModDataComponents.CABLE_FROM_ROUTE.get()), "Successful placement clears route selection");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cleanOuterCornerAndInvalidCombinations(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2));
        for (var wallFace : Direction.Plane.HORIZONTAL) {
            var peer = pos.above().relative(wallFace.getOpposite());
            var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState(); var other = state;
            for (var color : IndividualCableColor.values()) {
                state = state.setValue(color.property, CableRoute.wall(wallFace)); other = other.setValue(color.property, CableRoute.floor(wallFace.getOpposite()));
            }
            level.setBlockAndUpdate(pos, state); level.setBlockAndUpdate(peer, other);
            for (var bend : IndividualCableBends.find(level, pos, state)) if (bend.peer().equals(peer)) {
                var wall = IndividualCableGeometry.part(bend.color(), CableRoute.wall(wallFace)).bounds().move(pos);
                h.assertTrue(bend.needsInfill() && bend.boxes().size() == 1, "Only the single missing outside cube is filled");
                for (var box : bend.boxes()) {
                    h.assertTrue(Math.abs(box.minY - wall.maxY) < 1e-8 && Math.abs(box.maxY - wall.maxY - 1.0 / 16) < 1e-8, "Filler never protrudes down into inner corner");
                    if (wallFace.getAxis() == Direction.Axis.Z) h.assertTrue(box.minZ == wall.minZ && box.maxZ == wall.maxZ, "Filler stays within wall thickness");
                    else h.assertTrue(box.minX == wall.minX && box.maxX == wall.maxX, "Filler stays within wall thickness");
                }
            }
            level.destroyBlock(peer, false); level.destroyBlock(pos, false);
        }
        for (var color : IndividualCableColor.values()) {
            h.assertTrue(!IndividualCableComponents.validPair(color, CableRoute.Z, CableRoute.X), "Two horizontal routes cannot create a crossing");
            h.assertTrue(!IndividualCableComponents.validPair(color, CableRoute.Y, CableRoute.WALL_NORTH), "Two wall routes cannot create duplicate vertical occupancy");
            h.assertTrue(!IndividualCableComponents.validPair(color, CableRoute.X, CableRoute.Y), "Nonmeeting perpendicular lanes are rejected");
        }
        h.succeed();
    }
}
