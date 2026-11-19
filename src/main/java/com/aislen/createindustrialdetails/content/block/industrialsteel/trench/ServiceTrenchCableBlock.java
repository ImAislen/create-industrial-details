package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.content.block.cable.CableNetworks;
import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

public final class ServiceTrenchCableBlock extends ServiceTrenchBlock implements EntityBlock {
    private static final BooleanProperty[] LANES = {
            BooleanProperty.create("red"), BooleanProperty.create("yellow"),
            BooleanProperty.create("green"), BooleanProperty.create("blue")
    };
    private static final IndividualCableColor[] COLORS = IndividualCableColor.values();

    public ServiceTrenchCableBlock(Properties properties) {
        super(properties);
        var state = defaultBlockState();
        for (var lane : LANES) state = state.setValue(lane, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends ServiceTrenchBlock> codec() {
        return simpleCodec(ServiceTrenchCableBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LANES);
    }

    public static CableRoute route(BlockState state) {
        return CableRoute.along(state.getValue(FACING).getAxis());
    }

    public static CableRoute lane(BlockState state, IndividualCableColor color) {
        return state.getValue(LANES[color.ordinal()]) ? route(state) : CableRoute.NONE;
    }

    public static BlockState withLane(BlockState state, IndividualCableColor color, boolean occupied) {
        return state.setValue(LANES[color.ordinal()], occupied);
    }

    public static BlockState housing(BlockState state, BlockState target) {
        return target.setValue(FACING, state.getValue(FACING)).setValue(OPEN, state.getValue(OPEN))
                .setValue(WATERLOGGED, state.getValue(WATERLOGGED));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndividualCableBlockEntity(pos, state);
    }

    @Override
    public String getDescriptionId() {
        return ModBlocks.SERVICE_TRENCH.get().getDescriptionId();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult hit,
                                      LevelReader level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return new ItemStack(ModBlocks.SERVICE_TRENCH.get());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var drops = new ArrayList<>(ModBlocks.SERVICE_TRENCH.get().defaultBlockState().getDrops(params));
        for (var color : COLORS)
            if (lane(state, color) != CableRoute.NONE) drops.add(new ItemStack(color.item()));
        return drops;
    }

    @Override
    public void onBlockStateChange(LevelReader reader, BlockPos pos, BlockState previous, BlockState state) {
        super.onBlockStateChange(reader, pos, previous, state);
        if (!(reader instanceof Level level)) return;
        for (var color : COLORS) {
            if (IndividualCableComponents.primary(previous, color) == lane(state, color)) continue;
            if (level instanceof ServerLevel server) CableNetworks.get(server).topologyChanged(pos, previous);
            if (level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) host.changed();
            IndividualCableBlock.refreshCorners(level, pos);
            return;
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this)) {
            if (level instanceof ServerLevel server) CableNetworks.get(server).topologyChanged(pos, state);
            IndividualCableBlock.refreshCorners(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof IndividualCableBlockEntity host) host.changed();
        if (level instanceof Level world) IndividualCableBlock.refreshCorners(world, pos);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (state.getValue(OPEN)) return IndividualCableBlock.removeSelected(state, context);
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        var player = context.getPlayer();
        var pos = context.getClickedPos();
        if (player == null || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()
                || level.getBlockState(pos) != state) return InteractionResult.FAIL;
        BlockState exposed = ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState();
        for (var color : COLORS) exposed = exposed.setValue(color.property, lane(state, color));
        var old = level.getBlockEntity(pos);
        if (!(old instanceof IndividualCableBlockEntity host)) return InteractionResult.FAIL;
        var data = host.saveCustomOnly(level.registryAccess());
        if (!level.setBlockAndUpdate(pos, exposed)) return InteractionResult.FAIL;
        if (level.getBlockEntity(pos) instanceof IndividualCableBlockEntity cable) {
            cable.loadCustomOnly(data, level.registryAccess());
            cable.changed();
        }
        if (!player.isCreative()) player.getInventory().placeItemBackInInventory(new ItemStack(ModBlocks.SERVICE_TRENCH.get()));
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }
}
