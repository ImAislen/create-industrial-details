package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import com.simibubi.create.AllItems;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndividualCableInteractionGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rejectedSecondClickKeepsSelection(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.east(4);
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(IndividualCableColor.RED.property, CableRoute.Z);
        level.setBlockAndUpdate(a, state); level.setBlockAndUpdate(b, state);
        level.setBlockAndUpdate(a.south(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var player = new InteractingPlayer(h); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.RED_CONTROL_CABLE.get(), 16); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        click(player, a); player.feedback = Component.empty();
        h.assertTrue(click(player, a.south()) == InteractionResult.FAIL, "Invalid second target rejected");
        h.assertTrue(!player.feedback.getString().isEmpty(), "Rejected second click explains why no span was placed");
        h.assertTrue(stack.has(ModDataComponents.CABLE_FROM.get()), "Missing the thin cable must not silently discard the first anchor");
        h.assertTrue(click(player, b).consumesAction() && CableSpans.get(level).at(a).size() == 1, "A corrected second click still completes the span");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void consecutiveSneakClicksThroughGameMode(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.east(5);
        var player = new InteractingPlayer(h);
        level.setBlockAndUpdate(a, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
        for (var color : IndividualCableColor.values()) {
            var stack = new ItemStack(color.item(), 32); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setShiftKeyDown(false);
            var aBefore = level.getBlockState(a); var bBefore = level.getBlockState(b);
            var first = click(player, a); var second = click(player, b);
            h.assertTrue(first.consumesAction() && second.consumesAction(), "Both anchors placed through native item/block dispatch: "
                    + color + " " + first + "/" + second + " before=" + aBefore + "/" + bBefore + " feedback=" + player.feedback.getString());
            h.assertTrue(stack.getCount() == 30, "Straight segments consume only their own colored items");
            player.setShiftKeyDown(true);
            h.assertTrue(click(player, a).consumesAction(), "First native sneak interaction handled");
            h.assertTrue(stack.get(ModDataComponents.CABLE_FROM.get()) != null, "First endpoint survives native item dispatch");
            h.assertTrue(stack.getOrDefault(ModDataComponents.CABLE_FROM_TOKEN.get(), 0L)
                    == ((IndividualCableBlockEntity) level.getBlockEntity(a)).token(color), "Selection uses server endpoint identity");
            player.getInventory().tick();
            h.assertTrue(stack.get(ModDataComponents.CABLE_FROM.get()) != null, "Pending selection survives inventory ticking between clicks");
            h.assertTrue(click(player, b).consumesAction(), "Second native sneak interaction completes: " + player.feedback.getString());
            h.assertTrue(CableSpans.get(level).at(a).stream().anyMatch(s -> s.color() == color && s.peer(a).equals(b)), "Native clicks create color-keyed span");
            h.assertTrue(stack.getCount() == 25, "Native placement consumes span materials once");
            h.assertTrue(!stack.has(ModDataComponents.CABLE_FROM.get()) && !stack.has(ModDataComponents.CABLE_FROM_TOKEN.get()), "Native dispatch preserves selection cleanup");
            feedback(h, player, "connected");
            var host = (IndividualCableBlockEntity) level.getBlockEntity(a);
            var client = new IndividualCableBlockEntity(a, level.getBlockState(a));
            client.loadWithComponents(host.getUpdatePacket().getTag(), level.registryAccess());
            h.assertTrue(client.spans().stream().anyMatch(s -> s.color() == color && s.peer(a).equals(b)), "Server-created span reaches rendering through the existing endpoint packet");
        }
        h.assertTrue(CableSpans.get(level).at(a).size() == 4, "All four spans share real placed anchors without replacing each other");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rejectionFeedbackAndRetry(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.east(5); var far = a.above(31);
        var red = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(IndividualCableColor.RED.property, CableRoute.Y);
        level.setBlockAndUpdate(a, red); level.setBlockAndUpdate(b, red); level.setBlockAndUpdate(far, red);
        level.setBlockAndUpdate(a.south(), red.setValue(IndividualCableColor.RED.property, CableRoute.NONE)
                .setValue(IndividualCableColor.YELLOW.property, CableRoute.Z));
        var player = new InteractingPlayer(h); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.RED_CONTROL_CABLE.get(), 2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        click(player, a);
        h.assertTrue(click(player, a.south()) == InteractionResult.FAIL, "Wrong-color endpoint rejected"); feedback(h, player, "invalid_anchor");
        h.assertTrue(click(player, far) == InteractionResult.FAIL, "Overlong connection rejected"); feedback(h, player, "too_far");
        h.assertTrue(click(player, b) == InteractionResult.FAIL, "Insufficient held material rejected"); feedback(h, player, "materials");
        h.assertTrue(stack.has(ModDataComponents.CABLE_FROM.get()) && stack.getCount() == 2 && CableSpans.get(level).at(a).isEmpty(),
                "Recoverable rejections keep selection and consume no material");
        stack.grow(10);
        h.assertTrue(click(player, b).consumesAction() && stack.getCount() == 7, "Retry succeeds without reselecting the start");
        click(player, b);
        h.assertTrue(click(player, a) == InteractionResult.FAIL && stack.getCount() == 7, "Duplicate rejected in reverse endpoint order without consumption");
        feedback(h, player, "duplicate");
        h.assertTrue(CableSpans.get(level).at(a).size() == 1, "Rejected duplicate creates no extra span record");
        h.assertTrue(click(player, b).consumesAction() && !stack.has(ModDataComponents.CABLE_FROM.get()), "Clicking selected start cancels after rejection");
        feedback(h, player, "cancelled");
        level.destroyBlock(far, false);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void staleStartAndCancellationThroughGameMode(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.east(3);
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(IndividualCableColor.YELLOW.property, CableRoute.Z);
        level.setBlockAndUpdate(a, state); level.setBlockAndUpdate(b, state);
        var player = new InteractingPlayer(h); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.YELLOW_CONTROL_CABLE.get(), 16); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        click(player, a); level.destroyBlock(a, false); level.setBlockAndUpdate(a, state);
        h.assertTrue(click(player, b) == InteractionResult.FAIL && !stack.has(ModDataComponents.CABLE_FROM.get()), "Replaced anchor clears stale identity on the next interaction");
        feedback(h, player, "start_unavailable");
        click(player, a);
        h.assertTrue(click(player, a).consumesAction() && !stack.has(ModDataComponents.CABLE_FROM.get()), "Second click on the same anchor explicitly cancels");
        feedback(h, player, "cancelled");
        click(player, a); player.getInventory().selected = 1; player.getInventory().tick();
        h.assertTrue(!stack.has(ModDataComponents.CABLE_FROM.get()), "Changing held item cancels selection on server inventory tick");
        feedback(h, player, "cancelled");
        player.getInventory().selected = 0; click(player, a);
        h.assertTrue(player.gameMode.useItem(player, level, stack, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
                "Sneak click in air is handled without placing a straight segment");
        feedback(h, player, "invalid_anchor");
        h.assertTrue(stack.has(ModDataComponents.CABLE_FROM.get()), "A missed ray does not clear a valid selection");
        var unloaded = new BlockPos(28_000_000, a.getY(), 28_000_000);
        stack.set(ModDataComponents.CABLE_FROM.get(), GlobalPos.of(level.dimension(), unloaded));
        h.assertTrue(click(player, b) == InteractionResult.FAIL && !stack.has(ModDataComponents.CABLE_FROM.get())
                && level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null,
                "Unavailable start cancels without force-loading its chunk");
        feedback(h, player, "start_unavailable");
        h.assertTrue(stack.getCount() == 16 && CableSpans.get(level).at(a).isEmpty(), "Cancellation and stale selections never charge items");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sneakItemPriorityAndWrenchRegression(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.east(3);
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState()
                .setValue(IndividualCableColor.RED.property, CableRoute.Z).setValue(IndividualCableColor.YELLOW.property, CableRoute.Z);
        level.setBlockAndUpdate(a, state); level.setBlockAndUpdate(b, state);
        var player = new InteractingPlayer(h); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.RED_CONTROL_CABLE.get(), 16); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setItemInHand(InteractionHand.OFF_HAND, AllItems.WRENCH.asStack());
        var hit = new BlockHitResult(Vec3.atCenterOf(a), Direction.UP, a, false);
        h.assertTrue(stack.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                "Sneak cable item consumes the pre-block phase so neither block use nor normal placement runs");
        click(player, b);
        h.assertTrue(level.getBlockState(a) == state && level.getBlockState(b) == state && CableSpans.get(level).at(a).size() == 1,
                "Cable use does not invoke the wrench or add straight segments");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        var point = new Vec3(a.getX() + 4.25 / 16, a.getY() + 2.5 / 16, a.getZ() + .5);
        player.setPos(point.x, point.y + 2 - player.getEyeHeight(), point.z);
        var wrenchHit = new BlockHitResult(point, Direction.UP, a, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, wrenchHit);
        h.assertTrue(CableSpans.get(level).at(a).isEmpty() && level.getBlockState(a) == state, "Native Shift-wrench removes only the red span first");
        h.assertTrue(player.getInventory().countItem(ModItems.RED_CONTROL_CABLE.get()) == 3, "Native wrench refunds span exactly once");
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, wrenchHit);
        h.assertTrue(level.getBlockState(a).getValue(IndividualCableColor.RED.property) == CableRoute.NONE
                && level.getBlockState(a).getValue(IndividualCableColor.YELLOW.property) == CableRoute.Z, "Native targeted removal preserves the other cable color");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void creativeSpanThroughGameMode(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = a.above(5);
        var state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(IndividualCableColor.YELLOW.property, CableRoute.Y);
        level.setBlockAndUpdate(a, state); level.setBlockAndUpdate(b, state);
        var player = new InteractingPlayer(h); player.gameMode.changeGameModeForPlayer(GameType.CREATIVE); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.YELLOW_CONTROL_CABLE.get()); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.assertTrue(click(player, a).consumesAction() && click(player, b).consumesAction(), "Native Creative flow accepts a vertical Yellow span");
        h.assertTrue(stack.getCount() == 1 && !stack.has(ModDataComponents.CABLE_FROM.get()) && CableSpans.get(level).at(a).size() == 1,
                "Creative span costs nothing and leaves no pending selection");
        h.succeed();
    }

    private static void feedback(GameTestHelper h, InteractingPlayer player, String key) {
        h.assertTrue(player.feedback.getContents() instanceof TranslatableContents text
                && text.getKey().equals("create_industrial_details.cable." + key), "Expected action-bar feedback: " + key);
    }

    private static InteractionResult click(InteractingPlayer player, BlockPos pos) {
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    private static final class InteractingPlayer extends FakePlayer {
        private Component feedback = Component.empty();
        private InteractingPlayer(GameTestHelper h) {
            super(h.getLevel(), new GameProfile(UUID.randomUUID(), "cable-interaction-test"));
            gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        }
        @Override public void displayClientMessage(Component message, boolean actionBar) { feedback = message; }
    }
}
