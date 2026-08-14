package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.aislen.createindustrialdetails.registry.ModDataComponents;
import com.cake.struts.content.block.StrutBlock;
import com.cake.struts.registry.StrutDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Endpoint-aware adaptation of Struts 1.2.1 StrutBlockItem (MIT).
 * Stock placement helpers are private, so this isolated item retains the
 * established two-click/item-cost flow while adding one snap byte.
 */
public final class WoodenBeamBlockItem extends BlockItem {

    private static final double MIN_DOT_THRESHOLD = Math.cos(Math.toRadians(90));

    public WoodenBeamBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();

        if (context.isSecondaryUseActive()) {
            if (hasPlacementState(stack)) {
                clearPlacementState(stack);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }

        BlockHitResult hit = new BlockHitResult(
                context.getClickLocation(),
                context.getClickedFace(),
                context.getClickedPos(),
                context.isInside()
        );
        EndpointSelection selected = resolveSelection(level, hit, getBlock());
        if (!stack.has(StrutDataComponents.GIRDER_STRUT_FROM)) {
            if (selected == null) {
                return InteractionResult.FAIL;
            }
            stack.set(StrutDataComponents.GIRDER_STRUT_FROM, selected.anchorPos());
            stack.set(StrutDataComponents.GIRDER_STRUT_FROM_FACE, selected.supportFace());
            stack.set(ModDataComponents.WOODEN_BEAM_FROM_SNAP.get(), selected.snap().id());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos fromPos = stack.get(StrutDataComponents.GIRDER_STRUT_FROM);
        Direction fromFace = stack.get(StrutDataComponents.GIRDER_STRUT_FROM_FACE);
        Byte storedSnap = stack.get(ModDataComponents.WOODEN_BEAM_FROM_SNAP.get());
        WoodenBeamSnapPoint fromSnap = storedSnap == null
                ? WoodenBeamSnapPoint.CENTER
                : WoodenBeamSnapPoint.byId(storedSnap);

        if (fromPos == null) {
            clearPlacementState(stack);
            return InteractionResult.FAIL;
        }
        if (selected == null) {
            return InteractionResult.FAIL;
        }
        if (fromFace == null) {
            BlockState fromState = level.getBlockState(fromPos);
            fromFace = fromState.getBlock().equals(getBlock())
                    ? fromState.getValue(StrutBlock.FACING)
                    : selected.supportFace().getOpposite();
        }

        if (!level.isClientSide) {
            ConnectionResult result = tryConnect(
                    context,
                    new EndpointSelection(fromPos, fromFace, fromSnap),
                    selected
            );
            if (result != ConnectionResult.SUCCESS) {
                if (result == ConnectionResult.INVALID) {
                    clearPlacementState(stack);
                }
                return InteractionResult.FAIL;
            }
        }

        clearPlacementState(stack);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(StrutDataComponents.GIRDER_STRUT_FROM) || super.isFoil(stack);
    }

    public static @Nullable EndpointSelection resolveSelection(
            Level level,
            BlockHitResult hit,
            Block woodenBeamBlock
    ) {
        BlockPos clickedPos = hit.getBlockPos();
        Direction clickedFace = hit.getDirection();
        BlockState clickedState = level.getBlockState(clickedPos);

        BlockPos anchorPos;
        Direction supportFace;
        if (clickedState.getBlock().equals(woodenBeamBlock)) {
            anchorPos = clickedPos;
            // Clicking an existing anchor directly selects the physical face hit.
            supportFace = clickedFace.getOpposite();
        } else {
            anchorPos = clickedPos.relative(clickedFace);
            BlockState anchorState = level.getBlockState(anchorPos);
            if (!anchorState.canBeReplaced() && !anchorState.getBlock().equals(woodenBeamBlock)) {
                return null;
            }
            supportFace = clickedFace;
        }

        WoodenBeamSnapPoint snap = WoodenBeamEndpoints.nearest(anchorPos, supportFace, hit.getLocation());
        return new EndpointSelection(anchorPos.immutable(), supportFace, snap);
    }

    public static boolean isValidConnection(Level level, EndpointSelection from, EndpointSelection to) {
        if (from == null || to == null || from.anchorPos().equals(to.anchorPos())) {
            return false;
        }
        int dx = to.anchorPos().getX() - from.anchorPos().getX();
        int dy = to.anchorPos().getY() - from.anchorPos().getY();
        int dz = to.anchorPos().getZ() - from.anchorPos().getZ();
        int nonZeroAxes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (nonZeroAxes >= 3 || (dy != 0 && dx == 0 && dz == 0)) {
            return false;
        }

        Vec3 fromPoint = WoodenBeamEndpoints.attachment(from.anchorPos(), from.supportFace(), from.snap());
        Vec3 toPoint = WoodenBeamEndpoints.attachment(to.anchorPos(), to.supportFace(), to.snap());
        Vec3 connection = toPoint.subtract(fromPoint);
        if (connection.lengthSqr() > StrutBlock.MAX_SPAN * StrutBlock.MAX_SPAN) {
            return false;
        }
        return isWithinAngle(connection, from.supportFace())
                && isWithinAngle(connection.reverse(), to.supportFace());
    }

    private static boolean isWithinAngle(Vec3 vector, Direction face) {
        if (vector.lengthSqr() < 1.0e-8) {
            return false;
        }
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        return vector.normalize().dot(normal) >= MIN_DOT_THRESHOLD;
    }

    private ConnectionResult tryConnect(UseOnContext context, EndpointSelection from, EndpointSelection to) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (!isValidConnection(level, from, to)) {
            return ConnectionResult.INVALID;
        }

        BlockState fromState = level.getBlockState(from.anchorPos());
        BlockState toState = level.getBlockState(to.anchorPos());
        boolean placeFrom = !fromState.getBlock().equals(getBlock());
        boolean placeTo = !toState.getBlock().equals(getBlock());
        int requiredAnchors = (placeFrom ? 1 : 0) + (placeTo ? 1 : 0);

        if ((placeFrom && !canOccupy(level, from.anchorPos()))
                || (placeTo && !canOccupy(level, to.anchorPos()))) {
            return ConnectionResult.INVALID;
        }
        if (!placeFrom && level.getBlockEntity(from.anchorPos()) instanceof WoodenBeamBlockEntity fromBeam
                && fromBeam.hasConnectionTo(to.anchorPos())) {
            return ConnectionResult.INVALID;
        }
        if (player != null && !player.getAbilities().instabuild
                && !hasRequiredAnchors(player, stack, requiredAnchors)) {
            return ConnectionResult.MISSING_ITEMS;
        }

        int placed = 0;
        if (placeFrom) {
            if (!placeAnchor(level, from.anchorPos(), from.supportFace(), player, stack.copy())) {
                return ConnectionResult.INVALID;
            }
            placed++;
        }
        if (placeTo) {
            if (!placeAnchor(level, to.anchorPos(), to.supportFace(), player, stack.copy())) {
                if (placeFrom) {
                    level.removeBlock(from.anchorPos(), false);
                }
                return ConnectionResult.INVALID;
            }
            placed++;
        }

        if (!(level.getBlockEntity(from.anchorPos()) instanceof WoodenBeamBlockEntity fromBeam)
                || !(level.getBlockEntity(to.anchorPos()) instanceof WoodenBeamBlockEntity toBeam)) {
            return ConnectionResult.INVALID;
        }
        if (placed > 0) {
            consumeAnchors(player, stack, placed);
        }

        SoundType sound = getBlock().defaultBlockState().getSoundType(level, to.anchorPos(), player);
        level.playSound(null, to.anchorPos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);

        fromBeam.addBeamConnection(
                to.anchorPos(), from.supportFace(), from.snap(), to.supportFace(), to.snap()
        );
        toBeam.addBeamConnection(
                from.anchorPos(), to.supportFace(), to.snap(), from.supportFace(), from.snap()
        );
        return ConnectionResult.SUCCESS;
    }

    private boolean placeAnchor(Level level, BlockPos pos, Direction face, Player player, ItemStack snapshot) {
        BlockState state = getBlock().defaultBlockState()
                .setValue(StrutBlock.FACING, face)
                .setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        state.getBlock().setPlacedBy(level, pos, state, player, snapshot);
        SoundType sound = state.getSoundType();
        level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        return true;
    }

    private boolean canOccupy(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() || state.getBlock().equals(getBlock());
    }

    private boolean hasRequiredAnchors(Player player, ItemStack held, int required) {
        if (required <= 0) {
            return true;
        }
        int available = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack candidate = inventory.getItem(i);
            if (!candidate.isEmpty() && candidate.getItem() == held.getItem()) {
                available += candidate.getCount();
            }
        }
        if (available >= required) {
            return true;
        }
        Component message = Component.translatable(
                "message.bits_n_bobs.girder_strut.missing_anchors",
                required - available
        ).withStyle(ChatFormatting.RED);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(message, true);
        } else {
            player.displayClientMessage(message, true);
        }
        return false;
    }

    private void consumeAnchors(Player player, ItemStack held, int amount) {
        if (amount <= 0 || player == null || player.getAbilities().instabuild) {
            return;
        }
        int remaining = drain(held, amount);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (candidate == held || candidate.isEmpty() || candidate.getItem() != held.getItem()) {
                continue;
            }
            remaining = drain(candidate, remaining);
        }
    }

    private static int drain(ItemStack stack, int amount) {
        int removed = Math.min(stack.getCount(), amount);
        stack.shrink(removed);
        return amount - removed;
    }

    private static boolean hasPlacementState(ItemStack stack) {
        return stack.has(StrutDataComponents.GIRDER_STRUT_FROM)
                || stack.has(StrutDataComponents.GIRDER_STRUT_FROM_FACE)
                || stack.has(ModDataComponents.WOODEN_BEAM_FROM_SNAP.get());
    }

    private static void clearPlacementState(ItemStack stack) {
        stack.remove(StrutDataComponents.GIRDER_STRUT_FROM);
        stack.remove(StrutDataComponents.GIRDER_STRUT_FROM_FACE);
        stack.remove(ModDataComponents.WOODEN_BEAM_FROM_SNAP.get());
    }

    public record EndpointSelection(BlockPos anchorPos, Direction supportFace, WoodenBeamSnapPoint snap) {
    }

    private enum ConnectionResult {
        SUCCESS,
        INVALID,
        MISSING_ITEMS
    }
}
