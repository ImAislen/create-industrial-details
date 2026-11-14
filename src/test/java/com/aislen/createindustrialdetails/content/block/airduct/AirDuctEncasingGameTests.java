package com.aislen.createindustrialdetails.content.block.airduct;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class AirDuctEncasingGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void materialInteractions(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var duct = ModBlocks.AIR_DUCT.get();
        for (var gameType : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            Player player = helper.makeMockPlayer(gameType);
            var initial = duct.defaultBlockState().setValue(AirDuctBlock.AXIS, Direction.Axis.Y);
            level.setBlockAndUpdate(pos, initial);
            for (AirDuctCasing casing : AirDuctCasing.values()) {
                if (casing == AirDuctCasing.NONE) continue;
                var previous = level.getBlockState(pos).getValue(AirDuctBlock.CASING);
                int oldCount = previous == AirDuctCasing.NONE ? 0 : player.getInventory().countItem(previous.asStack().getItem());
                ItemStack material = casing.asStack().copyWithCount(2);
                var result = duct.useItemOn(material, level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, hit(pos));
                helper.assertTrue(result.consumesAction(), "Material use consumes interaction rather than placing adjacent block");
                helper.assertTrue(level.getBlockState(pos) == initial.setValue(AirDuctBlock.CASING, casing), "Only casing changes");
                helper.assertTrue(material.getCount() == (player.isCreative() ? 2 : 1), "Correct material consumption");
                if (previous != AirDuctCasing.NONE)
                    helper.assertTrue(player.getInventory().countItem(previous.asStack().getItem()) == oldCount + (player.isCreative() ? 0 : 1), "Swap refunds previous casing once");
                int unchanged = material.getCount();
                duct.useItemOn(material, level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, hit(pos));
                helper.assertTrue(material.getCount() == unchanged, "Reapplying same casing costs nothing");
                var state = level.getBlockState(pos);
                var restored = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(state));
                helper.assertTrue(restored == state, "Casing serializes and reloads exactly");
                var drops = Block.getDrops(state, level, pos, null);
                helper.assertTrue(drops.size() == 2 && drops.stream().mapToInt(ItemStack::getCount).sum() == 2
                        && drops.stream().anyMatch(stack -> stack.is(casing.asStack().getItem())), "Survival loot contains one duct and one casing");
            }
            var state = level.getBlockState(pos);
            var casing = state.getValue(AirDuctBlock.CASING);
            int before = player.getInventory().countItem(casing.asStack().getItem());
            duct.onWrenched(state, context(player, pos));
            helper.assertTrue(level.getBlockState(pos) == initial, "Normal wrench removes only casing");
            helper.assertTrue(player.getInventory().countItem(casing.asStack().getItem()) == before + (player.isCreative() ? 0 : 1), "Wrench refund is not duplicated");
            helper.assertTrue(duct.onWrenched(initial, context(player, pos)) == net.minecraft.world.InteractionResult.PASS, "Uncased wrench behavior unchanged");
            for (AirDuctCasing material : AirDuctCasing.values()) {
                if (material == AirDuctCasing.NONE) continue;
                level.setBlockAndUpdate(pos, initial.setValue(AirDuctBlock.CASING, material));
                before = player.getInventory().countItem(material.asStack().getItem());
                int ducts = player.getInventory().countItem(duct.asItem());
                duct.onSneakWrenched(level.getBlockState(pos), context(player, pos));
                helper.assertTrue(level.getBlockState(pos).isAir(), "Sneak wrench removes whole duct");
                helper.assertTrue(player.getInventory().countItem(material.asStack().getItem()) == before + (player.isCreative() ? 0 : 1)
                        && player.getInventory().countItem(duct.asItem()) == ducts + (player.isCreative() ? 0 : 1), "Whole removal returns exactly both pieces in survival only");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void connectionsAndShapes(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var duct = ModBlocks.AIR_DUCT.get();
        var plain = duct.defaultBlockState();
        var originalBox = Block.box(2, 2, 2, 14, 14, 14);
        helper.assertTrue(!Shapes.joinIsNotEmpty(plain.getCollisionShape(level, pos), originalBox, BooleanOp.NOT_SAME), "Uncased collision stays original");
        helper.assertTrue(plain.getOcclusionShape(level, pos).isEmpty() && plain.getLightBlock(level, pos) == 1, "Uncased occlusion/light behavior preserved");
        var oldData = NbtUtils.writeBlockState(plain);
        oldData.getCompound("Properties").remove("casing");
        helper.assertTrue(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), oldData).getValue(AirDuctBlock.CASING) == AirDuctCasing.NONE, "Old worlds load with no casing");
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (int mask = 0; mask < 64; mask++) {
                var state = plain.setValue(AirDuctBlock.AXIS, axis);
                for (Direction side : Direction.values())
                    state = state.setValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(side), (mask & (1 << side.ordinal())) != 0);
                for (AirDuctCasing casing : AirDuctCasing.values()) {
                    if (casing == AirDuctCasing.NONE) continue;
                    var encased = state.setValue(AirDuctBlock.CASING, casing);
                    helper.assertTrue(encased.isCollisionShapeFullBlock(level, pos) && !encased.hasBlockEntity(), "Static full collision in all casing states");
                    helper.assertTrue(encased.getLightBlock(level, pos) == 15, "Casing blocks light");
                    var shape = encased.getOcclusionShape(level, pos);
                    helper.assertTrue(shape == encased.getOcclusionShape(level, pos), "Occlusion shapes cached");
                    int openings = AirDuctBlock.casingOpenings(encased);
                    for (Direction side : Direction.values())
                        helper.assertTrue(Block.isFaceFull(shape, side) == ((openings & (1 << side.ordinal())) == 0), "Only closed faces fully occlude");
                    var rotated = encased.rotate(Rotation.CLOCKWISE_90);
                    helper.assertTrue(rotated.getValue(AirDuctBlock.CASING) == casing, "Rotation keeps casing");
                    for (Direction side : Direction.values())
                        helper.assertTrue(rotated.getValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(Rotation.CLOCKWISE_90.rotate(side)))
                                == encased.getValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(side)), "Rotation maps connections");
                    helper.assertTrue(encased.mirror(Mirror.LEFT_RIGHT).getValue(AirDuctBlock.CASING) == casing, "Mirror keeps casing");
                    for (Direction.Axis rotationAxis : Direction.Axis.values()) {
                        var transform = new StructureTransform(BlockPos.ZERO, rotationAxis, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
                        var transformed = transform.apply(encased);
                        helper.assertTrue(transformed.getValue(AirDuctBlock.CASING) == casing, "Create transform keeps casing");
                        for (Direction side : Direction.values()) {
                            var destination = transform.rotateFacing(Mirror.LEFT_RIGHT.mirror(side));
                            helper.assertTrue(transformed.getValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(destination))
                                    == encased.getValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(side)), "Create transform preserves connection orientation");
                        }
                    }
                }
            }
        }
        var encased = plain.setValue(AirDuctBlock.CASING, AirDuctCasing.BRASS);
        level.setBlockAndUpdate(pos, encased);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction side : Direction.values()) {
            level.setBlockAndUpdate(pos.relative(side), plain);
            helper.assertTrue(level.getBlockState(pos).getValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(side))
                    && level.getBlockState(pos).getValue(AirDuctBlock.CASING) == AirDuctCasing.BRASS, "Neighbor addition connects and preserves casing");
            duct.onWrenched(level.getBlockState(pos), context(player, pos));
            helper.assertTrue(level.getBlockState(pos).getValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(side)), "Uncasing preserves live connection");
            duct.useItemOn(AirDuctCasing.BRASS.asStack(), level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, hit(pos));
            level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
            helper.assertTrue(!level.getBlockState(pos).getValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(side))
                    && level.getBlockState(pos).getValue(AirDuctBlock.CASING) == AirDuctCasing.BRASS, "Neighbor removal preserves casing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placementHelperOnEncasedDucts(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var duct = ModBlocks.AIR_DUCT.get();
        var encased = duct.defaultBlockState().setValue(AirDuctBlock.CASING, AirDuctCasing.COPPER);
        level.setBlockAndUpdate(pos, encased);
        level.setBlockAndUpdate(pos.north(), encased);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(duct, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var ray = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, -0.5), Direction.NORTH, pos, false);
        var result = duct.useItemOn(stack, level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND, ray);
        helper.assertTrue(result.consumesAction(), "Placement helper works on encased run");
        helper.assertTrue(level.getBlockState(pos.north(2)).is(duct)
                && level.getBlockState(pos.north(2)).getValue(AirDuctBlock.CASING) == AirDuctCasing.NONE, "New helper-placed duct starts uncased");
        helper.assertTrue(level.getBlockState(pos).getValue(AirDuctBlock.CASING) == AirDuctCasing.COPPER
                && level.getBlockState(pos.north()).getValue(AirDuctBlock.CASING) == AirDuctCasing.COPPER, "Helper does not reset existing casing");
        helper.assertTrue(stack.getCount() == 1, "Helper consumes one duct");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingEncasedDucts(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        for (AirDuctCasing casing : AirDuctCasing.values()) {
            if (casing == AirDuctCasing.NONE) continue;
            level.setBlockAndUpdate(pos, ModBlocks.AIR_DUCT.get().defaultBlockState().setValue(AirDuctBlock.CASING, casing));
            level.destroyBlock(pos, true);
            var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(1));
            int materials = items.stream().filter(item -> item.getItem().is(casing.asStack().getItem()))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            int ducts = items.stream().filter(item -> item.getItem().is(ModBlocks.AIR_DUCT.get().asItem()))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(materials == 1 && ducts == 1, "Actual block destruction drops exactly one duct and one material");
            items.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static UseOnContext context(Player player, BlockPos pos) {
        return new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack(), hit(pos));
    }
}
