package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public final class IndividualCableBlock extends Block implements EntityBlock, IWrenchable {
    public IndividualCableBlock(Properties properties) {
        super(properties); var state = defaultBlockState();
        for (var color : IndividualCableColor.values()) state = state.setValue(color.property, CableRoute.NONE);
        registerDefaultState(state);
    }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(IndividualCableBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (var color : IndividualCableColor.values()) builder.add(color.property);
    }
    @Override public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target,
                                                 LevelReader level, BlockPos pos, Player player) {
        var from = player.getEyePosition();
        var component = IndividualCableComponents.target(level, pos, state, from, from.add(target.getLocation().subtract(from).normalize().scale(player.blockInteractionRange())));
        return component == null ? ItemStack.EMPTY : new ItemStack(component.color().item());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IndividualCableBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host ? host.componentShape() : IndividualCableGeometry.shape(state); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        for (var color : IndividualCableColor.values()) if (state.getValue(color.property) != CableRoute.NONE) drops.add(new ItemStack(color.item()));
        var host = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (host instanceof IndividualCableBlockEntity cable) for (var color : IndividualCableColor.values())
            if (cable.secondary(color) != CableRoute.NONE) drops.add(new ItemStack(color.item()));
        return drops;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        refreshCorners(level, pos);
        if (level instanceof ServerLevel serverLevel) com.aislen.createindustrialdetails.content.block.cable.CableNetworks.get(serverLevel).topologyChanged(pos, previous);
        if (previous.is(this) && level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host)
            for (var color : IndividualCableColor.values()) if (previous.getValue(color.property) == CableRoute.NONE && state.getValue(color.property) != CableRoute.NONE) host.renew(color);
        if (level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) host.changed();
    }
    @Override protected BlockState updateShape(BlockState state, net.minecraft.core.Direction side, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) host.changed();
        if (level instanceof ServerLevel server) CableSpans.get(server).refreshGeometry(pos);
        if (level instanceof Level world) refreshCorners(world, pos);
        return state;
    }
    public static void refreshCorners(Level level, BlockPos pos) {
        for (var offset : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
            if (offset.equals(BlockPos.ZERO)) continue;
            var peer = pos.offset(offset);
            if (level.hasChunkAt(peer) && level.getBlockEntity(peer) instanceof IndividualCableBlockEntity host) {
                if (level.isClientSide) host.invalidateGeometry(); else host.changed();
            }
        }
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        refreshCorners(level, pos);
        if (level instanceof ServerLevel serverLevel) com.aislen.createindustrialdetails.content.block.cable.CableNetworks.get(serverLevel).topologyChanged(pos, state);
        if (level instanceof ServerLevel server) for (var color : IndividualCableColor.values()) {
            if (state.getValue(color.property) != CableRoute.NONE && (!replacement.is(this) || replacement.getValue(color.property) != state.getValue(color.property)))
                for (var span : replacement.is(this) ? CableSpans.get(server).detach(pos, color, state.getValue(color.property)) : CableSpans.get(server).detach(pos, color)) drop(server, pos, color, span.cost());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative() && level instanceof ServerLevel server)
            for (var color : IndividualCableColor.values()) CableSpans.get(server).detach(pos, color);
        return super.playerWillDestroy(level, pos, state, player);
    }
    public static void drop(ServerLevel level, BlockPos pos, IndividualCableColor color, int amount) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) return;
        while (amount > 0) { int count = Math.min(amount, 64); Block.popResource(level, pos, new ItemStack(color.item(), count)); amount -= count; }
    }
    private static IndividualCableComponents.Component selected(BlockState state, UseOnContext context) {
        Player player = context.getPlayer(); if (player == null) return null;
        Vec3 start = player.getEyePosition(); Vec3 end = start.add(context.getClickLocation().subtract(start).normalize().scale(player.blockInteractionRange()));
        return IndividualCableComponents.target(context.getLevel(), context.getClickedPos(), state, start, end);
    }
    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return removeSelected(state, context);
    }
    public static InteractionResult removeSelected(BlockState state, UseOnContext context) {
        var component = selected(state, context); if (component == null) return InteractionResult.PASS;
        var color = component.color(); var route = component.route();
        if (context.getLevel() instanceof ServerLevel server) {
            var player = context.getPlayer(); var pos = context.getClickedPos();
            if (!server.mayInteract(player, pos) || !player.mayBuild()) return InteractionResult.FAIL;
            var event = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(server, pos, state, player);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event); if (event.isCanceled()) return InteractionResult.FAIL;
            var spans = CableSpans.get(server).at(pos).stream().filter(s -> s.color() == color && (s.from().equals(pos) ? s.fromRoute() : s.toRoute()) == route).toList();
            if (!spans.isEmpty()) {
                var span = selectedSpan(spans, pos, player);
                if (CableSpans.get(server).remove(span) && !player.isCreative()) returnItems(player, color, span.cost());
                IWrenchable.playRemoveSound(server, pos); return InteractionResult.SUCCESS;
            }
            if (server.getBlockState(pos) != state
                    || !(server.getBlockEntity(pos) instanceof IndividualCableBlockEntity host)
                    || !host.contains(color, route)) return InteractionResult.FAIL;
            host.removeComponent(color, route);
            if (server.getBlockEntity(pos) instanceof IndividualCableBlockEntity remaining && remaining.contains(color, route))
                return InteractionResult.FAIL;
            if (!player.isCreative()) returnItems(player, color, 1);
            IWrenchable.playRemoveSound(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var component = selected(state, context); if (component == null) return InteractionResult.PASS;
        var color = component.color(); var route = component.route();
        if (context.getLevel() instanceof ServerLevel server) {
            var player = context.getPlayer(); var pos = context.getClickedPos();
            if (!server.mayInteract(player, pos) || !player.mayBuild()) return InteractionResult.FAIL;
            var spans = CableSpans.get(server).at(pos).stream().filter(s -> s.color() == color && (s.from().equals(pos) ? s.fromRoute() : s.toRoute()) == route).toList();
            if (!spans.isEmpty()) return InteractionResult.PASS;
            var host = (IndividualCableBlockEntity) server.getBlockEntity(pos);
            var candidate = route;
            boolean found = false;
            for (int i = 0; i < CableRoute.values().length - 1; i++) {
                candidate = CableRoute.values()[candidate.ordinal() % (CableRoute.values().length - 1) + 1];
                if (host.canRotate(color, route, candidate)) { found = true; break; }
            }
            if (!found) return InteractionResult.PASS;
            host.rotateComponent(color, route, candidate);
            IWrenchable.playRotateSound(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
    private static CableSpans.Span selectedSpan(List<CableSpans.Span> spans, BlockPos pos, Player player) {
        return spans.stream().max(Comparator.<CableSpans.Span>comparingDouble(s -> Vec3.atCenterOf(s.peer(pos)).subtract(Vec3.atCenterOf(pos)).normalize().dot(player.getLookAngle()))
                .thenComparing(s -> s.peer(pos))).orElseThrow();
    }
    public static void returnItems(Player player, IndividualCableColor color, int count) {
        while (count > 0) { int size = Math.min(count, 64); player.getInventory().placeItemBackInInventory(new ItemStack(color.item(), size)); count -= size; }
    }
}
