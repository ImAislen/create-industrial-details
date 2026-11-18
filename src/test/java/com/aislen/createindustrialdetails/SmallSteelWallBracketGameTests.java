package com.aislen.createindustrialdetails;

import com.aislen.createindustrialdetails.content.block.supports.SmallSteelWallBracketBlock;
import com.aislen.createindustrialdetails.content.block.supports.SmallSteelWallBracketBlockItem;
import com.aislen.createindustrialdetails.content.block.supports.SmallSteelWallBracketStyle;
import com.aislen.createindustrialdetails.content.variant.SelectItemVariant;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.aislen.createindustrialdetails.content.block.supports.SmallSteelWallBracketBlock.*;
import static com.aislen.createindustrialdetails.content.block.supports.SmallSteelWallBracketStyle.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmallSteelWallBracketGameTests {
    @GameTest(template = "empty")
    public static void bracketVariantSelectionAndPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "variant-test"),
                ClientInformation.createDefault());
        var inventory = player.getInventory();
        var item = ModBlocks.SMALL_STEEL_WALL_BRACKET.asItem();
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        var standard = new ItemStack(item, 3);
        var chosen = new ItemStack(item, 4);
        inventory.setItem(0, chosen);
        inventory.setItem(1, standard);

        helper.assertTrue(!new SelectItemVariant(1, itemId, "standard", "straight").apply(player),
                "Reject a request for a slot other than the active hotbar slot");
        helper.assertTrue(!new SelectItemVariant(0, BuiltInRegistries.ITEM.getKey(Items.STONE), "standard", "straight").apply(player),
                "Reject a request for a different item");
        helper.assertTrue(!new SelectItemVariant(0, itemId, "standard", "missing").apply(player),
                "Reject an unknown variant");
        helper.assertTrue(SmallSteelWallBracketBlockItem.style(chosen) == STANDARD, "Invalid requests leave the item alone");

        var request = new SelectItemVariant(0, itemId, "standard", "straight");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            SelectItemVariant.CODEC.encode(buffer, request);
            var decoded = SelectItemVariant.CODEC.decode(buffer);
            helper.assertTrue(decoded.equals(request) && decoded.apply(player), "Decoded request selects the style");
            helper.assertTrue(!request.apply(player), "Reject a stale request with the wrong previous selection");
            buffer.clear();
            ItemStack.STREAM_CODEC.encode(buffer, chosen);
            var synced = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertTrue(synced.getCount() == 4 && SmallSteelWallBracketBlockItem.style(synced) == STRAIGHT,
                    "Inventory packet encoding preserves the style and count");
        } finally {
            buffer.release();
        }

        inventory.selected = 1;
        helper.assertTrue(SmallSteelWallBracketBlockItem.style(player.getMainHandItem()) == STANDARD,
                "A separate hotbar stack retains its default style");
        inventory.setItem(9, inventory.removeItemNoUpdate(0));
        var reloaded = ItemStack.parse(level.registryAccess(), inventory.getItem(9).save(level.registryAccess())).orElseThrow();
        helper.assertTrue(reloaded.getCount() == 4 && SmallSteelWallBracketBlockItem.style(reloaded) == STRAIGHT,
                "An inventory move and save-load round trip preserve the selection");
        inventory.setItem(1, reloaded);
        helper.assertTrue(new SelectItemVariant(1, itemId, "straight", "standard").apply(player)
                && ItemStack.isSameItemSameComponents(standard, reloaded), "Returning to Standard clears the style component");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bracketVariantPlacementAndRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.SMALL_STEEL_WALL_BRACKET.get();
        var item = (SmallSteelWallBracketBlockItem) block.asItem();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var standard = new ItemStack(item, 2);
        var straight = new ItemStack(item, 3);
        item.selectVariant(straight, "straight");
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var support = pos.north();
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());

        for (var hand : new ItemStack[]{standard, straight}) {
            var style = SmallSteelWallBracketBlockItem.style(hand);
            var other = new ItemStack(item);
            item.selectVariant(other, (style == STANDARD ? STRAIGHT : STANDARD).getSerializedName());
            var placement = use(player, hand, support, Direction.SOUTH, 0.75);
            var stacked = use(player, hand, pos, Direction.SOUTH, 0.9);
            helper.assertTrue(item.useOn(placement).consumesAction(), "Place a wall bracket from its selected stack");
            var single = level.getBlockState(pos);
            helper.assertTrue(single.getValue(STYLE) == style && single.getValue(HALF) == Half.TOP,
                    "Wall placement keeps the selected style and upper orientation");
            int otherCount = other.getCount();
            var incompatible = use(player, other, pos, Direction.SOUTH, 0.9);
            helper.assertTrue(!item.useOn(incompatible).consumesAction() && other.getCount() == otherCount
                    && level.getBlockState(pos) == single, "A different style cannot merge or consume an item");
            helper.assertTrue(item.useOn(stacked).consumesAction(), "Same style can be stacked");
            var doubled = level.getBlockState(pos);
            helper.assertTrue(doubled.getValue(STYLE) == style && doubled.getValue(QUANTITY) == 2, "Double retains the style");
            var drops = Block.getDrops(doubled, level, pos, null);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getCount() == 2
                    && SmallSteelWallBracketBlockItem.style(drops.getFirst()) == style, "Double loot preserves its style and count");

            level.removeBlock(support, false);
            helper.assertTrue(level.getBlockState(pos) == doubled, "Removing the support leaves the bracket in place");
            int before = countBrackets(player, item, style);
            var wrench = use(player, AllItems.WRENCH.asStack(), pos, Direction.SOUTH, 0.9);
            block.onSneakWrenched(doubled, wrench);
            helper.assertTrue(level.getBlockState(pos) == single && countBrackets(player, item, style) == before + 1,
                    "First wrench action removes one piece and preserves its style on both sides");
            block.onSneakWrenched(single, wrench);
            helper.assertTrue(level.getBlockState(pos).isAir() && countBrackets(player, item, style) == before + 2,
                    "Second wrench action removes the last piece and returns it with the right style");
            var creative = helper.makeMockPlayer(GameType.CREATIVE);
            level.setBlockAndUpdate(pos, doubled);
            var creativeWrench = use(creative, AllItems.WRENCH.asStack(), pos, Direction.SOUTH, 0.9);
            block.onSneakWrenched(doubled, creativeWrench);
            block.onSneakWrenched(single, creativeWrench);
            helper.assertTrue(level.getBlockState(pos).isAir() && countBrackets(creative, item, style) == 0,
                    "Creative removes both pieces without returning items");
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bracketVariantOrientationsAndLegacyState(GameTestHelper helper) {
        var block = ModBlocks.SMALL_STEEL_WALL_BRACKET.get();
        var item = (SmallSteelWallBracketBlockItem) block.asItem();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (var style : SmallSteelWallBracketStyle.values()) {
            var stack = new ItemStack(item);
            item.selectVariant(stack, style.getSerializedName());
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (var half : Half.values()) {
                    var placement = block.getStateForPlacement(new BlockPlaceContext(
                            use(player, stack, pos, facing, half == Half.TOP ? 0.75 : 0.25)));
                    helper.assertTrue(placement != null && placement.getValue(FACING) == facing
                            && placement.getValue(HALF) == half && placement.getValue(STYLE) == style,
                            "Wall placement supports every facing, half, and style");
                }
                player.setYRot(facing.getOpposite().toYRot());
                var floor = block.getStateForPlacement(new BlockPlaceContext(use(player, stack, pos, Direction.UP, 1)));
                helper.assertTrue(floor != null && floor.getValue(FACING) == facing && floor.getValue(ATTACH_FACE) == AttachFace.FLOOR
                        && floor.getValue(STYLE) == style, "Floor placement follows player facing and style");
            }
        }
        for (var state : block.getStateDefinition().getPossibleStates()) {
            var outline = state.getShape(helper.getLevel(), pos);
            helper.assertTrue(!outline.isEmpty() && outline == state.getCollisionShape(helper.getLevel(), pos),
                    "Every state uses a populated shared selection and collision shape");
            if (state.getValue(STYLE) == STANDARD) {
                var legacy = NbtUtils.writeBlockState(state);
                legacy.getCompound("Properties").remove("style");
                BlockState loaded = NbtUtils.readBlockState(helper.getLevel().holderLookup(Registries.BLOCK), legacy);
                helper.assertTrue(loaded == state, "Old world bracket states load as Standard with their original properties");
            }
        }
        var straight = block.defaultBlockState().setValue(STYLE, STRAIGHT).getShape(helper.getLevel(), pos);
        helper.assertTrue(straight.min(Direction.Axis.Y) == 13.5 / 16 && straight.max(Direction.Axis.Z) == 7.0 / 16,
                "Straight wall shape stops at the mounting plate and the end of the cantilever");
        helper.succeed();
    }

    private static int countBrackets(Player player, SmallSteelWallBracketBlockItem item, SmallSteelWallBracketStyle style) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item))
                .filter(stack -> SmallSteelWallBracketBlockItem.style(stack) == style).mapToInt(ItemStack::getCount).sum();
    }

    private static UseOnContext use(Player player, ItemStack stack, BlockPos pos, Direction face, double height) {
        var point = Vec3.atBottomCenterOf(pos).add(0, height, 0);
        return new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(point, face, pos, false));
    }
}
