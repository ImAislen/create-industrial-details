package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import com.cake.struts.content.block.StrutBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class IndividualCableItem extends Item {
    public final IndividualCableColor color;
    public IndividualCableItem(Properties properties, IndividualCableColor color) { super(properties); this.color = color; }
    public static void clear(ItemStack stack) { stack.remove(ModDataComponents.CABLE_FROM.get()); stack.remove(ModDataComponents.CABLE_FROM_TOKEN.get()); stack.remove(ModDataComponents.CABLE_FROM_ROUTE.get()); }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && stack.has(ModDataComponents.CABLE_FROM.get()) && entity instanceof Player p
                && (!selected && p.getOffhandItem() != stack || p.getMainHandItem().getItem() instanceof IndividualCableItem held && held.color != color)) {
            clear(stack); message(p, "cancelled");
        }
        if (level instanceof ServerLevel && stack.has(ModDataComponents.CABLE_FROM.get()) && entity instanceof Player player) {
            var endpoint = pendingEndpoint(stack, level);
            if (endpoint == null || !endpoint.exists(level, color) || endpoint.token(level, color) != stack.getOrDefault(ModDataComponents.CABLE_FROM_TOKEN.get(), 0L)) {
                clear(stack); message(player, "start_unavailable");
            }
        }
    }
    @Override public boolean isFoil(ItemStack stack) { return stack.has(ModDataComponents.CABLE_FROM.get()); }
    private static boolean boxWrenchInteraction(UseOnContext context) {
        var player = context.getPlayer();
        var block = context.getLevel().getBlockState(context.getClickedPos()).getBlock();
        return (block instanceof com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlock || block instanceof com.aislen.createindustrialdetails.content.block.cable.CableTrayBlock)
                && player != null && (CableSpanRemoval.isWrench(player.getMainHandItem()) || CableSpanRemoval.isWrench(player.getOffhandItem()));
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (boxWrenchInteraction(context)) return InteractionResult.PASS;
        boolean port = CableEndpoint.target(context.getLevel(), context.getClickedPos(), context.getClickedFace(), context.getClickLocation(), player.getEyePosition(), player.blockInteractionRange(), color) != null
                && !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof IndividualCableBlock);
        if (!port && !context.isSecondaryUseActive() && !stack.has(ModDataComponents.CABLE_FROM.get())) {
            for (var other : IndividualCableColor.values()) if (com.aislen.createindustrialdetails.content.block.cable.ControlBoxPorts.target(context.getLevel().getBlockState(context.getClickedPos()), context.getClickedPos(), context.getClickedFace(), context.getClickLocation(), other) != 0) {
                if (!context.getLevel().isClientSide) message(player, "invalid_anchor"); return InteractionResult.FAIL;
            }
        }
        return (port || context.isSecondaryUseActive() || stack.has(ModDataComponents.CABLE_FROM.get())) ? useOn(context) : InteractionResult.PASS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown() && !player.getItemInHand(hand).has(ModDataComponents.CABLE_FROM.get())) return super.use(level, player, hand);
        if (!level.isClientSide) message(player, "invalid_anchor");
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var player = context.getPlayer(); var stack = context.getItemInHand();
        if (boxWrenchInteraction(context)) return InteractionResult.PASS;
        if (player == null || !player.mayBuild() || player.isSpectator()) return InteractionResult.FAIL;
        if (player.isShiftKeyDown() || stack.has(ModDataComponents.CABLE_FROM.get())) return span(context);
        if (!level.isClientSide) clear(stack);
        BlockPos pos = context.getClickedPos(); BlockState state = level.getBlockState(pos);
        CableRoute route = context.getClickedFace().getAxis().isHorizontal() ? CableRoute.wall(context.getClickedFace()) : CableRoute.floor(player.getDirection());
        if (state.getBlock() instanceof IndividualCableBlock) {
            if (state.getValue(color.property) != CableRoute.NONE) return addSecondary(context, pos, route);
        } else {
            if (!state.canBeReplaced()) pos = pos.relative(context.getClickedFace());
            state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof IndividualCableBlock) && !state.canBeReplaced()) return InteractionResult.FAIL;
            if (!(state.getBlock() instanceof IndividualCableBlock)) state = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
            if (state.getValue(color.property) != CableRoute.NONE) return addSecondary(context, pos, route);
        }
        route = placementRoute(level, pos, state, route);
        BlockState placed = state.setValue(color.property, route);
        if (!IndividualCableGeometry.permits(placed) || level.getBlockEntity(pos) instanceof IndividualCableBlockEntity existing && !existing.clearOfOtherColors(color, route)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("create_industrial_details.cable.overlap"), true);
            return InteractionResult.FAIL;
        }
        if (!level.isInWorldBounds(pos) || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), stack)
                || !level.isUnobstructed(placed, pos, CollisionContext.of(player))) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
            level.setBlockAndUpdate(pos, placed);
            if (net.neoforged.neoforge.event.EventHooks.onBlockPlace(player, snapshot, context.getClickedFace())) {
                snapshot.restore(3); return InteractionResult.FAIL;
            }
            if (!player.isCreative()) stack.shrink(1);
            placementSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private InteractionResult addSecondary(UseOnContext context, BlockPos pos, CableRoute route) {
        var level = context.getLevel(); var player = context.getPlayer(); var stack = context.getItemInHand();
        route = placementRoute(level, pos, level.getBlockState(pos), route);
        if (!(level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) || !host.canAdd(color, route)) return reject(player, "overlap");
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        if (level instanceof ServerLevel) {
            var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
            if (net.neoforged.neoforge.event.EventHooks.onBlockPlace(player, snapshot, context.getClickedFace())) return InteractionResult.FAIL;
            if (!host.add(color, route)) return InteractionResult.FAIL;
            if (!player.isCreative()) stack.shrink(1);
            placementSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    public static CableRoute pendingRoute(ItemStack stack, IndividualCableBlockEntity host) {
        int id = stack.getOrDefault(ModDataComponents.CABLE_FROM_ROUTE.get(), -1);
        var color = ((IndividualCableItem) stack.getItem()).color;
        return id > 0 && id < CableRoute.values().length ? CableRoute.values()[id] : host.getBlockState().getValue(color.property);
    }
    private CableRoute placementRoute(Level level, BlockPos pos, BlockState state, CableRoute normal) {
        var continuation = IndividualCablePlacement.choose(level, pos, state, color, normal);
        if (continuation != normal && IndividualCablePlacement.valid(level, pos, state, color, continuation)) return continuation;
        return IndividualCablePlacement.choose(level, pos, state, color, portRoute(level, pos, normal));
    }
    private CableRoute portRoute(Level level, BlockPos pos, CableRoute normal) {
        for (var side : Direction.values()) {
            var boxPos = pos.relative(side); if (!level.hasChunkAt(boxPos)) continue;
            var state = level.getBlockState(boxPos); var fromBox = side.getOpposite();
            net.minecraft.world.phys.Vec3 point;
            if (state.getBlock() instanceof com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlock && com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlock.isCablePort(state, fromBox)) {
                int port = com.aislen.createindustrialdetails.content.block.cable.ControlBoxPorts.encode(fromBox, state.getValue(com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlock.FACING));
                point = com.aislen.createindustrialdetails.content.block.cable.ControlBoxPorts.point(boxPos, port, color);
            } else if (state.getBlock() instanceof com.aislen.createindustrialdetails.content.block.cable.CableTrayBlock && state.getValue(com.aislen.createindustrialdetails.content.block.cable.CableTrayBlock.AXIS) == side.getAxis())
                point = com.aislen.createindustrialdetails.content.block.cable.CablePhysicalConnections.trayEnd(boxPos, state, color, fromBox);
            else continue;
            for (var route : CableRoute.values()) if (route != CableRoute.NONE && route.axis() == side.getAxis()
                    && com.aislen.createindustrialdetails.content.block.cable.CablePhysicalConnections.compatible(IndividualCableGeometry.end(pos, color, route, side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0), point, route)) return route;
        }
        return normal;
    }
    public static CableEndpoint pendingEndpoint(ItemStack stack, Level level) {
        var from = stack.get(ModDataComponents.CABLE_FROM.get());
        if (from == null || !from.dimension().equals(level.dimension()) || !level.hasChunkAt(from.pos())) return null;
        int code = stack.getOrDefault(ModDataComponents.CABLE_FROM_ROUTE.get(), 0);
        if (code == 0 && level.getBlockEntity(from.pos()) instanceof IndividualCableBlockEntity host) code = host.getBlockState().getValue(((IndividualCableItem) stack.getItem()).color.property).ordinal();
        return CableEndpoint.selection(from.pos(), code);
    }
    private InteractionResult span(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        var stack = context.getItemInHand(); var player = context.getPlayer(); var pos = context.getClickedPos();
        var from = pendingEndpoint(stack, level);
        if (stack.has(ModDataComponents.CABLE_FROM.get()) && (from == null || !from.exists(level, color)
                || from.token(level, color) != stack.getOrDefault(ModDataComponents.CABLE_FROM_TOKEN.get(), 0L))) { clear(stack); return reject(player, "start_unavailable"); }
        var to = CableEndpoint.target(level, pos, context.getClickedFace(), context.getClickLocation(), player.getEyePosition(), player.blockInteractionRange(), color);
        if (to == null) return reject(player, "invalid_anchor");
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), stack)
                || from != null && (!level.mayInteract(player, from.pos()) || !player.mayUseItemAt(from.pos(), context.getClickedFace(), stack))) return reject(player, "protected_anchor");
        if (from == null) {
            stack.set(ModDataComponents.CABLE_FROM.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            stack.set(ModDataComponents.CABLE_FROM_ROUTE.get(), to.selection()); stack.set(ModDataComponents.CABLE_FROM_TOKEN.get(), to.token(level, color));
            message(player, "anchor_selected"); return InteractionResult.CONSUME;
        }
        if (from.pos().equals(pos)) { clear(stack); message(player, "cancelled"); return InteractionResult.CONSUME; }
        if (from.pos().distSqr(pos) > StrutBlock.MAX_SPAN * StrutBlock.MAX_SPAN) return reject(player, "too_far", StrutBlock.MAX_SPAN);
        int cost = Math.max(1, (int) Math.ceil(Math.sqrt(from.pos().distSqr(pos))));
        var spans = CableSpans.get(level); var proposed = CableSpans.Span.between(level, color, from, to, cost);
        if (!CableSpanGeometry.clearEnclosures(proposed)) return reject(player, "port_obstructed");
        if (spans.at(from.pos()).stream().anyMatch(s -> s.key().equals(proposed.key()))) return reject(player, "duplicate");
        if (!player.isCreative() && stack.getCount() < cost) return reject(player, "materials", cost);
        if (!spans.connect(color, from, to, cost)) return reject(player, "connection_failed");
        clear(stack); if (!player.isCreative()) stack.shrink(cost);
        placementSound(level, pos); message(player, "connected"); return InteractionResult.CONSUME;
    }
    public static void placementSound(Level level, BlockPos pos) {
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.METAL_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, .55f, 1.2f);
    }
    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("create_industrial_details.cable." + key, args), true);
    }
    private static InteractionResult reject(Player player, String key, Object... args) {
        message(player, key, args); return InteractionResult.FAIL;
    }
}
