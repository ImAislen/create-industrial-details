package com.aislen.createindustrialdetails.content.block.pneumatic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import static com.aislen.createindustrialdetails.content.block.pneumatic.PneumaticPlacement.Problem;

public final class PneumaticTubingItem extends Item {
    public PneumaticTubingItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && PneumaticTubingSelection.clear(player, hand))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        var pos = context.getClickedPos();
        var target = PneumaticStationBlock.port(level.getBlockState(pos), pos, context.getClickedFace());
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        var origin = player.isShiftKeyDown() ? null : PneumaticTubingSelection.port(player, context.getHand());
        if (target == null && origin == null) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!mayAim(level, player, pos, context.getClickedFace(), stack))
            return tell(player, Problem.INVALID_ENDPOINT.message(0));

        var network = PneumaticNetwork.get(server);
        if (origin == null) {
            if (!PneumaticNetwork.validPort(server, target)) return tell(player, Problem.INVALID_ENDPOINT.message(0));
            if (network.occupied(target)) return tell(player, Problem.OCCUPIED.message(0));
            PneumaticTubingSelection.select(player, context.getHand(), target);
            return tell(player, Component.translatable("message.create_industrial_details.pneumatic.selected"));
        }
        var curve = target == null ? null : PneumaticPlacement.candidate(origin, target, target.center());
        Problem problem = PneumaticPlacement.validate(origin, target, curve,
                port -> level.mayInteract(player, port.pos()) && PneumaticNetwork.validPort(server, port),
                network::connectedTo, available(player, this));
        if (problem != null) return tell(player, problem.message(curve == null ? 0 : curve.cost()));
        int cost = player.isCreative() ? 0 : curve.cost();
        if (!network.connect(server, origin, target, curve, cost))
            return tell(player, Problem.INVALID_ENDPOINT.message(0));
        PneumaticTubingSelection.clear(player, context.getHand());
        if (!player.isCreative()) {
            int remaining = cost;
            for (ItemStack entry : player.getInventory().items) {
                if (!entry.is(this)) continue;
                int spent = Math.min(remaining, entry.getCount());
                entry.shrink(spent);
                remaining -= spent;
                if (remaining == 0) break;
            }
            if (remaining > 0) player.getOffhandItem().shrink(remaining);
        }
        return tell(player, Component.translatable("message.create_industrial_details.pneumatic.connected", curve.cost()));
    }

    public static boolean mayAim(Level level, Player player, BlockPos pos, Direction face, ItemStack stack) {
        return level.mayInteract(player, pos) && player.mayUseItemAt(pos, face, stack);
    }

    public static int available(Player player, Item tubing) {
        if (player.isCreative()) return Integer.MAX_VALUE;
        int count = player.getOffhandItem().is(tubing) ? player.getOffhandItem().getCount() : 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(tubing)) count += stack.getCount();
        return count;
    }

    private static InteractionResult tell(Player player, Component message) {
        player.displayClientMessage(message, true);
        return InteractionResult.CONSUME;
    }
}
