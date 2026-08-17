package com.aislen.createindustrialdetails.content.block.woodenpost;

import com.mojang.serialization.MapCodec;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WoodenPostBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<WoodenPostBlock> CODEC = simpleCodec(WoodenPostBlock::new);
    public static final EnumProperty<WoodenPostArrangement> POST_POSITION =
            EnumProperty.create("post_position", WoodenPostArrangement.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new WoodenPostPlacementHelper());
    private static final double HIT_TOLERANCE = 1.0D / 1024.0D;
    private static final double RAY_DISTANCE_TOLERANCE = 1.0E-10D;
    private static final double RAY_DIRECTION_TOLERANCE = 1.0E-8D;

    private final int fireSpreadSpeed;
    private final int flammability;

    public WoodenPostBlock(Properties properties) {
        this(properties, 0, 0);
    }

    public WoodenPostBlock(Properties properties, int fireSpreadSpeed, int flammability) {
        super(properties);
        this.fireSpreadSpeed = fireSpreadSpeed;
        this.flammability = flammability;
        registerDefaultState(stateDefinition.any()
                .setValue(POST_POSITION, WoodenPostArrangement.CENTER)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        Direction clickedFace = hitResult.getDirection();
        ItemInteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                || clickedFace.getAxis() == Direction.Axis.Y
                || player.isSecondaryUseActive()
                || !(stack.getItem() instanceof BlockItem blockItem)) {
            return result;
        }

        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!helper.matchesItem(stack)) {
            return result;
        }

        if (helper instanceof WoodenPostPlacementHelper postHelper) {
            return postHelper.placeInWorld(level, blockItem, player, hand, state, pos, hitResult);
        }
        return helper.getOffset(player, level, state, pos, hitResult)
                .placeInWorld(level, blockItem, player, hand, hitResult);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos placementPos = context.getClickedPos();
        WoodenPostPosition position = placementPosition(context);
        FluidState fluidState = context.getLevel().getFluidState(placementPos);
        return defaultBlockState()
                .setValue(POST_POSITION, WoodenPostArrangement.singleton(position))
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    public static WoodenPostPosition placementPosition(BlockPlaceContext context) {
        BlockPos placementPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            BlockPos supportPos = placementPos.relative(clickedFace.getOpposite());
            BlockState supportState = context.getLevel().getBlockState(supportPos);
            if (supportState.getBlock() instanceof WoodenPostBlock) {
                WoodenPostPosition targeted = targetedMember(
                        supportState,
                        supportPos,
                        context.getClickLocation(),
                        clickedFace
                );
                if (targeted != null) {
                    return targeted;
                }
            }
        }
        return snappedPosition(context, placementPos);
    }

    private static WoodenPostPosition snappedPosition(BlockPlaceContext context, BlockPos placementPos) {
        Vec3 hit = context.getClickLocation();
        return WoodenPostPosition.nearest(
                hit.x - placementPos.getX(),
                hit.z - placementPos.getZ()
        );
    }

    public static WoodenPostArrangement getArrangement(BlockState state) {
        return state.getValue(POST_POSITION);
    }

    @Nullable
    public static WoodenPostPosition getSinglePosition(BlockState state) {
        return getArrangement(state).singlePosition();
    }

    @Nullable
    public static WoodenPostPosition targetedMember(BlockState state, BlockPos pos, BlockHitResult hitResult) {
        return targetedMember(state, pos, hitResult.getLocation(), hitResult.getDirection());
    }

    @Nullable
    public static WoodenPostPosition targetedMember(
            BlockState state,
            BlockPos pos,
            BlockHitResult hitResult,
            @Nullable Vec3 rayOrigin
    ) {
        if (rayOrigin != null && hitResult.getDirection().getAxis().isHorizontal()) {
            WoodenPostPosition rayHit = rayHitMember(state, pos, hitResult, rayOrigin);
            if (rayHit != null) {
                return rayHit;
            }
        }
        return targetedMember(state, pos, hitResult);
    }

    @Nullable
    public static WoodenPostPosition resolveSameBlockInsertion(
            BlockState state,
            BlockPos pos,
            BlockHitResult hitResult
    ) {
        if (!(state.getBlock() instanceof WoodenPostBlock)
                || hitResult.getDirection().getAxis() == Direction.Axis.Y) {
            return null;
        }

        WoodenPostPosition clickedMember = targetedMember(state, pos, hitResult);
        if (clickedMember == null) {
            return null;
        }

        WoodenPostPosition addedMember = clickedMember.offsetByPostWidth(hitResult.getDirection());
        return addedMember != null && getArrangement(state).canAdd(addedMember) ? addedMember : null;
    }

    @Nullable
    public static WoodenPostPosition targetedMember(
            BlockState state,
            BlockPos pos,
            Vec3 hitLocation,
            Direction clickedFace
    ) {
        if (!(state.getBlock() instanceof WoodenPostBlock)) {
            return null;
        }
        Vec3 localHit = hitLocation.subtract(Vec3.atLowerCornerOf(pos));
        WoodenPostPosition exact = nearestMember(getArrangement(state), localHit, clickedFace, true);
        return exact != null ? exact : nearestMember(getArrangement(state), localHit, clickedFace, false);
    }

    @Nullable
    private static WoodenPostPosition nearestMember(
            WoodenPostArrangement arrangement,
            Vec3 localHit,
            Direction clickedFace,
            boolean requireFaceBoundary
    ) {
        WoodenPostPosition best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (WoodenPostPosition member : arrangement.members()) {
            AABB bounds = member.bounds();
            if (!containsFaceProjection(bounds, localHit, clickedFace)) {
                continue;
            }
            double faceDistance = faceDistance(bounds, localHit, clickedFace);
            if (requireFaceBoundary && faceDistance > HIT_TOLERANCE) {
                continue;
            }
            double distance = faceDistance * faceDistance + lateralDistance(bounds, localHit, clickedFace);
            if (distance < bestDistance) {
                best = member;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Nullable
    private static WoodenPostPosition rayHitMember(
            BlockState state,
            BlockPos pos,
            BlockHitResult hitResult,
            Vec3 rayOrigin
    ) {
        if (!(state.getBlock() instanceof WoodenPostBlock)) {
            return null;
        }

        Vec3 rayDelta = hitResult.getLocation().subtract(rayOrigin);
        double rayLength = rayDelta.length();
        if (rayLength <= RAY_DIRECTION_TOLERANCE) {
            return null;
        }

        Vec3 rayDirection = rayDelta.scale(1.0D / rayLength);
        Vec3 rayEnd = hitResult.getLocation().add(rayDirection.scale(HIT_TOLERANCE));
        WoodenPostPosition best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (WoodenPostPosition member : getArrangement(state).members()) {
            var intersection = member.bounds().move(pos).clip(rayOrigin, rayEnd);
            if (intersection.isEmpty()) {
                continue;
            }

            double distance = rayOrigin.distanceToSqr(intersection.get());
            if (distance < bestDistance - RAY_DISTANCE_TOLERANCE
                    || (Math.abs(distance - bestDistance) <= RAY_DISTANCE_TOLERANCE
                    && preferRayTie(member, best, hitResult.getDirection(), rayDirection))) {
                best = member;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean preferRayTie(
            WoodenPostPosition candidate,
            @Nullable WoodenPostPosition current,
            Direction clickedFace,
            Vec3 rayDirection
    ) {
        if (current == null) {
            return true;
        }

        int candidateCoordinate;
        int currentCoordinate;
        double lateralRayDirection;
        if (clickedFace.getAxis() == Direction.Axis.X) {
            candidateCoordinate = candidate.gridZ();
            currentCoordinate = current.gridZ();
            lateralRayDirection = rayDirection.z;
        } else {
            candidateCoordinate = candidate.gridX();
            currentCoordinate = current.gridX();
            lateralRayDirection = rayDirection.x;
        }

        if (Math.abs(lateralRayDirection) > RAY_DIRECTION_TOLERANCE) {
            return candidateCoordinate * lateralRayDirection > currentCoordinate * lateralRayDirection;
        }

        int faceSign = clickedFace.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        return candidateCoordinate * faceSign > currentCoordinate * faceSign;
    }

    private static boolean containsFaceProjection(AABB bounds, Vec3 hit, Direction face) {
        return switch (face.getAxis()) {
            case X -> contains(bounds.minY, bounds.maxY, hit.y)
                    && contains(bounds.minZ, bounds.maxZ, hit.z);
            case Y -> contains(bounds.minX, bounds.maxX, hit.x)
                    && contains(bounds.minZ, bounds.maxZ, hit.z);
            case Z -> contains(bounds.minX, bounds.maxX, hit.x)
                    && contains(bounds.minY, bounds.maxY, hit.y);
        };
    }

    private static boolean contains(double min, double max, double value) {
        return value >= min - HIT_TOLERANCE && value <= max + HIT_TOLERANCE;
    }

    private static double faceDistance(AABB bounds, Vec3 hit, Direction face) {
        double faceCoordinate = switch (face.getAxis()) {
            case X -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxX : bounds.minX;
            case Y -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxY : bounds.minY;
            case Z -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxZ : bounds.minZ;
        };
        double hitCoordinate = switch (face.getAxis()) {
            case X -> hit.x;
            case Y -> hit.y;
            case Z -> hit.z;
        };
        return Math.abs(hitCoordinate - faceCoordinate);
    }

    private static double lateralDistance(AABB bounds, Vec3 hit, Direction face) {
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerY = (bounds.minY + bounds.maxY) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        return switch (face.getAxis()) {
            case X -> squared(hit.y - centerY) + squared(hit.z - centerZ);
            case Y -> squared(hit.x - centerX) + squared(hit.z - centerZ);
            case Z -> squared(hit.x - centerX) + squared(hit.y - centerY);
        };
    }

    private static double squared(double value) {
        return value * value;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getArrangement(state).shape();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return getArrangement(state).shape();
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        int memberCount = getArrangement(state).memberCount();
        if (memberCount == 1 || drops.isEmpty()) {
            return drops;
        }

        List<ItemStack> multiplied = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            if (drop.getItem() == asItem()) {
                multiplied.add(drop.copyWithCount(drop.getCount() * memberCount));
            } else {
                multiplied.add(drop);
            }
        }
        return multiplied;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POST_POSITION, WATERLOGGED);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireSpreadSpeed;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammability;
    }
}
