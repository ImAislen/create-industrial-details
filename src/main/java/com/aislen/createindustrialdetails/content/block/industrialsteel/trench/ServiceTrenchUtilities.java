package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.FACING;
import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.WATERLOGGED;
import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.OPEN;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
public final class ServiceTrenchUtilities {
    private ServiceTrenchUtilities() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!ServiceTrenchHousing.isTrench(state)) return;

        var context = new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec());
        InteractionResult result = installOrReplace(state, context);
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    private static InteractionResult installOrReplace(BlockState state, UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Utility requested = Utility.fromStack(stack);
        if (requested == null) return InteractionResult.PASS;

        Utility existing = Utility.in(state);
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        if (existing != null && !player.isShiftKeyDown()) return InteractionResult.PASS;

        Direction facing = state.getValue(FACING);
        Direction.Axis pipeAxis = existing == Utility.PIPE ? straightAxis(state)
                : existing == Utility.GLASS ? state.getValue(BlockStateProperties.AXIS) : facing.getAxis();
        if (existing == requested) {
            // Create supplies no glass pipe item. Fit a spare copper pipe sideways into a straight run to glaze it.
            if (requested != Utility.PIPE || hasGlassItem() || pipeAxis == null
                    || context.getClickedFace().getAxis() == pipeAxis) return InteractionResult.FAIL;
            requested = Utility.GLASS;
        }
        if (requested == Utility.GLASS && pipeAxis == null) return InteractionResult.FAIL;

        for (Direction side : Direction.values()) {
            if (!level.hasChunkAt(pos.relative(side))) return InteractionResult.FAIL;
        }

        BlockState placed = switch (requested) {
            case PIPE -> {
                var copper = AllBlocks.FLUID_PIPE.get();
                BlockState pipe = existing == Utility.GLASS ? copper.getAxisState(pipeAxis)
                        : copper.updateBlockState(copper.getAxisState(facing.getAxis()), facing, null, level, pos);
                yield EncasedPipeBlock.transferSixWayProperties(pipe, ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState());
            }
            case GLASS -> ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, pipeAxis);
            case SHAFT -> ModBlocks.SERVICE_TRENCH_SHAFT.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, facing.getAxis());
        };
        placed = ServiceTrenchHousing.withCaps(placed.setValue(FACING, facing)
                .setValue(WATERLOGGED, state.getValue(WATERLOGGED)).setValue(OPEN, state.getValue(OPEN)), level, pos);
        if (!level.isUnobstructed(placed, pos, CollisionContext.empty())) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (existing != null) {
            if (!safeToRemove(level, pos, existing)) {
                player.displayClientMessage(Component.translatable("create_industrial_details.service_trench.utility_in_use"), true);
                return InteractionResult.FAIL;
            }
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()
                    || level.getBlockState(pos) != state) return InteractionResult.FAIL;
        }
        if (CatnipServices.HOOKS.playerPlaceSingleBlock(player, level, pos, placed)
                || !level.getBlockState(pos).is(placed.getBlock())) return InteractionResult.FAIL;

        BlockState installed = level.getBlockState(pos);
        installed.getBlock().setPlacedBy(level, pos, installed, player, stack);
        var sound = requested.placementState().getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (!player.isCreative()) {
            stack.shrink(1);
            if (existing != null) player.getInventory().placeItemBackInInventory(existing.asStack());
        }
        return InteractionResult.CONSUME;
    }

    static Direction.Axis straightAxis(BlockState state) {
        BlockState copper = EncasedPipeBlock.transferSixWayProperties(state, AllBlocks.FLUID_PIPE.getDefaultState());
        return FluidPropagator.getStraightPipeAxis(copper);
    }

    private static boolean safeToRemove(Level level, BlockPos pos, Utility existing) {
        if (existing == Utility.SHAFT) return true;
        var bracket = BlockEntityBehaviour.get(level, pos, BracketedBlockEntityBehaviour.TYPE);
        if (bracket != null && bracket.isBracketPresent()) return false;
        var transport = FluidPropagator.getPipe(level, pos);
        if (transport == null || transport.hasAnyPressure()) return false;
        for (Direction face : Direction.values()) {
            var flow = transport.getFlow(face);
            if (flow != null && !flow.fluid.isEmpty()) return false;
        }
        return true;
    }

    static boolean isUtilityItem(ItemStack stack) {
        return Utility.fromStack(stack) != null;
    }

    private static boolean hasGlassItem() {
        return AllBlocks.GLASS_FLUID_PIPE.get().asItem() != Items.AIR;
    }

    private enum Utility {
        PIPE, GLASS, SHAFT;

        static Utility fromStack(ItemStack stack) {
            if (stack.isEmpty()) return null;
            if (AllBlocks.FLUID_PIPE.isIn(stack)) return PIPE;
            if (AllBlocks.SHAFT.isIn(stack)) return SHAFT;
            return hasGlassItem() && AllBlocks.GLASS_FLUID_PIPE.isIn(stack) ? GLASS : null;
        }

        static Utility in(BlockState state) {
            if (state.getBlock() instanceof ServiceTrenchPipeBlock) return PIPE;
            if (state.getBlock() instanceof ServiceTrenchGlassPipeBlock) return GLASS;
            return state.getBlock() instanceof ServiceTrenchShaftBlock ? SHAFT : null;
        }

        BlockState placementState() {
            return switch (this) {
                case PIPE -> AllBlocks.FLUID_PIPE.getDefaultState();
                case GLASS -> AllBlocks.GLASS_FLUID_PIPE.getDefaultState();
                case SHAFT -> AllBlocks.SHAFT.getDefaultState();
            };
        }

        ItemStack asStack() {
            return switch (this) {
                case PIPE -> AllBlocks.FLUID_PIPE.asStack();
                case GLASS -> hasGlassItem() ? AllBlocks.GLASS_FLUID_PIPE.asStack() : AllBlocks.FLUID_PIPE.asStack();
                case SHAFT -> AllBlocks.SHAFT.asStack();
            };
        }
    }
}
