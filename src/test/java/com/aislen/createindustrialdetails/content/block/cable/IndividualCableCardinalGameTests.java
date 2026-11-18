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
public class IndividualCableCardinalGameTests {
    private static FakePlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "cardinal-cable"));
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return p;
    }
    private static InteractionResult click(FakePlayer p, BlockPos pos, Direction face) {
        return p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cardinalPlacementAndComponentPick(GameTestHelper h) {
        var level = h.getLevel(); var base = h.absolutePos(new BlockPos(2, 3, 2)); var p = player(h);
        for (var face : Direction.Plane.HORIZONTAL) {
            p.setYRot(face.toYRot());
            for (boolean wall : new boolean[]{false, true}) {
                level.setBlockAndUpdate(base, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                var position = wall ? base.relative(face) : base;
                if (wall) level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                for (var color : IndividualCableColor.values()) {
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(color.item(), 16));
                    h.assertTrue(click(p, wall ? base : position, wall ? face : Direction.UP).consumesAction(), "Native cardinal placement succeeds");
                    var state = level.getBlockState(position); var route = state.getValue(color.property);
                    h.assertTrue(route == (wall ? CableRoute.wall(face) : CableRoute.floor(face)), "Signed facing and wall face retained per color");
                    var box = IndividualCableGeometry.part(color, route).bounds();
                    if (wall) {
                        double surface = face == Direction.NORTH ? box.maxZ : face == Direction.SOUTH ? box.minZ : face == Direction.EAST ? box.minX : box.maxX;
                        h.assertTrue(Math.abs(surface - (face == Direction.NORTH || face == Direction.WEST ? 1 : 0)) < 1e-9, "Wall profile contacts the clicked surface inside the adjacent host");
                    } else {
                        var expected = route.transform(new Vec3((color.minX + .75) / 16, .5 / 16, .5));
                        h.assertTrue(expected.distanceToSqr(IndividualCableGeometry.anchor(BlockPos.ZERO, color, route)) < 1e-12, "Floor lanes rotate around block center without mirroring");
                    }
                }
                for (var color : IndividualCableColor.values()) {
                    var state = level.getBlockState(position); var route = state.getValue(color.property);
                    var center = IndividualCableGeometry.anchor(position, color, route);
                    var outside = wall ? center.add(Vec3.atLowerCornerOf(face.getNormal()).scale(2)) : center.add(0, 2, 0);
                    p.setPos(outside.x, outside.y - p.getEyeHeight(), outside.z);
                    var hit = new BlockHitResult(center, wall ? face : Direction.UP, position, false);
                    h.assertTrue(state.getBlock().getCloneItemStack(state, hit, level, position, p).is(color.item()), "Pick Block selects the targeted color from a four-color host");
                }
                level.destroyBlock(position, false); if (wall) level.destroyBlock(base, false);
            }
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void normalClickCompletesPendingSpan(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.south(4);
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
        for (var c : IndividualCableColor.values()) state = state.setValue(c.property, CableRoute.Z);
        level.setBlockAndUpdate(a, state); level.setBlockAndUpdate(b, state);
        var p = player(h);
        for (var c : IndividualCableColor.values()) {
            var stack = new ItemStack(c.item(), 16); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            p.setShiftKeyDown(true); click(p, a, Direction.UP);
            h.assertTrue(stack.has(ModDataComponents.CABLE_FROM.get()), "Shift click enters span mode");
            p.setShiftKeyDown(false);
            h.assertTrue(click(p, b, Direction.UP).consumesAction(), "Normal click completes pending span before block placement");
            h.assertTrue(stack.getCount() == 12 && !stack.has(ModDataComponents.CABLE_FROM.get()), "Span paid once and preview state cleared");
            h.assertTrue(level.getBlockState(a) == state && level.getBlockState(b) == state, "Normal completion creates no extra segment or orientation change");
        }
        h.assertTrue(CableSpans.get(level).at(a).size() == 4, "All four independent records completed");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void smallOutsideCornersInEveryDirection(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2));
        for (var face : Direction.Plane.HORIZONTAL) {
            var b = a.above().relative(face.getOpposite());
            var wall = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState(); var floor = wall;
            for (var c : IndividualCableColor.values()) {
                wall = wall.setValue(c.property, CableRoute.wall(face));
                floor = floor.setValue(c.property, CableRoute.floor(face.getOpposite()));
            }
            level.setBlockAndUpdate(a, wall);
            var host = (IndividualCableBlockEntity) level.getBlockEntity(a); int before = host.revision;
            level.setBlockAndUpdate(b, floor);
            h.assertTrue(host.revision > before, "Diagonal placement invalidates the existing corner render cache");
            var bends = IndividualCableBends.find(level, a, wall);
            for (var c : IndividualCableColor.values()) {
                var bend = bends.stream().filter(v -> v.color() == c && v.peer().equals(b)).findFirst().orElseThrow();
                h.assertTrue(bend.needsInfill() && !bend.boxes().isEmpty(), "Diagonal host corner gets a small matching infill");
                for (var box : bend.boxes()) h.assertTrue(box.getXsize() <= 1.5 / 16 + 1e-8 && box.getYsize() <= 1.0 / 16 + 1e-8 && box.getZsize() <= 1.5 / 16 + 1e-8, "Only pixel-scale missing corner space is filled");
            }
            level.destroyBlock(b, false);
            h.assertTrue(IndividualCableBends.find(level, a, wall).stream().noneMatch(v -> v.peer().equals(b)), "Corner disappears with its participating segment");
            level.destroyBlock(a, false);
        }
        h.succeed();
    }
}
