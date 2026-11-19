package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchCableBlock;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.UUID;

public final class IndividualCableBlockEntity extends BlockEntity {
    private final long[] tokens = java.util.stream.LongStream.generate(() -> UUID.randomUUID().getMostSignificantBits())
            .limit(IndividualCableColor.values().length).toArray();
    private final CableRoute[] secondary = new CableRoute[IndividualCableColor.values().length];
    private final long[] secondaryTokens = new long[IndividualCableColor.values().length];
    private net.minecraft.world.phys.shapes.VoxelShape shape;
    public CableRoute secondary(IndividualCableColor color) { return secondary[color.ordinal()] == null ? CableRoute.NONE : secondary[color.ordinal()]; }
    public CableRoute primary(IndividualCableColor color) {
        return IndividualCableComponents.primary(getBlockState(), color);
    }

    public List<CableRoute> routes(IndividualCableColor color) {
        var primary = primary(color); var extra = secondary(color);
        return primary == CableRoute.NONE ? List.of() : extra == CableRoute.NONE ? List.of(primary) : List.of(primary, extra);
    }
    public boolean contains(IndividualCableColor color, CableRoute route) { return routes(color).contains(route); }
    public long token(IndividualCableColor color, CableRoute route) { return secondary(color) == route ? secondaryTokens[color.ordinal()] : token(color); }
    public boolean clearOfOtherColors(IndividualCableColor color, CableRoute route) {
        for (var other : IndividualCableColor.values()) if (other != color) for (var occupied : routes(other))
            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(IndividualCableGeometry.part(color, route), IndividualCableGeometry.part(other, occupied), net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        return true;
    }
    public boolean canAdd(IndividualCableColor color, CableRoute route) {
        var primary = primary(color);
        if (getBlockState().getBlock() instanceof ServiceTrenchCableBlock || secondary(color) != CableRoute.NONE || !IndividualCableComponents.validPair(color, primary, route)) return false;
        for (var other : IndividualCableColor.values()) if (other != color) for (var occupied : routes(other))
            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(IndividualCableGeometry.part(color, route), IndividualCableGeometry.part(other, occupied), net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        return true;
    }
    public boolean add(IndividualCableColor color, CableRoute route) {
        if (!canAdd(color, route)) return false;
        secondary[color.ordinal()] = route; secondaryTokens[color.ordinal()] = UUID.randomUUID().getMostSignificantBits();
        changed(); topologyChanged(color); if (level != null) IndividualCableBlock.refreshCorners(level, worldPosition); return true;
    }
    public boolean canRotate(IndividualCableColor color, CableRoute old, CableRoute replacement) {
        if (getBlockState().getBlock() instanceof ServiceTrenchCableBlock || !contains(color, old) || replacement == CableRoute.NONE || old == replacement) return false;
        var sibling = secondary(color) == old ? primary(color) : secondary(color);
        if (sibling != CableRoute.NONE && !IndividualCableComponents.validPair(color, sibling, replacement)) return false;
        for (var other : IndividualCableColor.values()) if (other != color) for (var occupied : routes(other))
            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(IndividualCableGeometry.part(color, replacement), IndividualCableGeometry.part(other, occupied), net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        return true;
    }
    public void rotateComponent(IndividualCableColor color, CableRoute old, CableRoute replacement) {
        if (!canRotate(color, old, replacement)) return;
        if (secondary(color) == old) { secondary[color.ordinal()] = replacement; secondaryTokens[color.ordinal()] = UUID.randomUUID().getMostSignificantBits(); }
        else { tokens[color.ordinal()] = UUID.randomUUID().getMostSignificantBits(); level.setBlockAndUpdate(worldPosition, getBlockState().setValue(color.property, replacement)); }
        changed(); topologyChanged(color); IndividualCableBlock.refreshCorners(level, worldPosition);
    }
    public void removeComponent(IndividualCableColor color, CableRoute route) {
        if (!contains(color, route) || level == null) return;
        if (level instanceof ServerLevel server) for (var span : CableSpans.get(server).detach(worldPosition, color, route))
            IndividualCableBlock.drop(server, worldPosition, color, span.cost());
        if (getBlockState().getBlock() instanceof ServiceTrenchCableBlock) {
            BlockState state = ServiceTrenchCableBlock.withLane(getBlockState(), color, false);
            boolean empty = java.util.Arrays.stream(IndividualCableColor.values()).allMatch(c -> ServiceTrenchCableBlock.lane(state, c) == CableRoute.NONE);
            level.setBlockAndUpdate(worldPosition, empty ? ServiceTrenchCableBlock.housing(state,
                    com.aislen.createindustrialdetails.registry.ModBlocks.SERVICE_TRENCH.get().defaultBlockState()) : state);
            if (!empty) renew(color);
            return;
        }
        if (secondary(color) == route) secondary[color.ordinal()] = CableRoute.NONE;
        else {
            var replacement = secondary(color); long identity = secondaryTokens[color.ordinal()];
            secondary[color.ordinal()] = CableRoute.NONE;
            var state = getBlockState().setValue(color.property, replacement);
            boolean empty = java.util.Arrays.stream(IndividualCableColor.values()).allMatch(c -> state.getValue(c.property) == CableRoute.NONE);
            level.setBlockAndUpdate(worldPosition, empty ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state);
            if (replacement != CableRoute.NONE) tokens[color.ordinal()] = identity;
        }
        changed(); topologyChanged(color); IndividualCableBlock.refreshCorners(level, worldPosition);
    }
    public net.minecraft.world.phys.shapes.VoxelShape componentShape() {
        if (shape == null) {
            var result = net.minecraft.world.phys.shapes.Shapes.empty();
            for (var color : IndividualCableColor.values()) for (var route : routes(color))
                result = net.minecraft.world.phys.shapes.Shapes.or(result, level == null ? IndividualCableGeometry.part(color, route) : CableConnectionGeometry.shape(level, worldPosition, color, route));
            shape = result.optimize();
        }
        return shape;
    }
    private List<CableSpans.Span> clientSpans = List.of();
    public int revision;

    public IndividualCableBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.INDIVIDUAL_CONTROL_CABLE.get(), pos, state); }
    public long token(IndividualCableColor color) { return tokens[color.ordinal()]; }
    public void renew(IndividualCableColor color) { tokens[color.ordinal()] = UUID.randomUUID().getMostSignificantBits(); changed(); }
    public List<CableSpans.Span> spans() {
        return level instanceof ServerLevel server ? CableSpans.get(server).at(worldPosition) : clientSpans;
    }
    public void invalidateGeometry() {
        shape = null; revision++;
        if (level != null && level.isClientSide) { requestModelDataUpdate(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }
    }
    private void topologyChanged() { topologyChanged(null); }
    private void topologyChanged(IndividualCableColor color) {
        if (level instanceof ServerLevel server) {
            var manager = com.aislen.createindustrialdetails.content.block.cable.CableNetworks.get(server);
            if (color == null) manager.topologyChanged(worldPosition, getBlockState()); else manager.topologyChanged(worldPosition, getBlockState(), color);
        }
    }
    public void changed() {
        invalidateGeometry(); setChanged();
        requestModelDataUpdate();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) { CableSpans.get(server).validate(worldPosition); topologyChanged(); }
        else if (level != null) {
            CableSpanIndex.client(level).replace(worldPosition, clientSpans);
            IndividualCableBlock.refreshCorners(level, worldPosition);
        }
    }
    @Override public void setRemoved() {
        if (level != null && level.isClientSide) {
            CableSpanIndex.client(level).forget(worldPosition);
            IndividualCableBlock.refreshCorners(level, worldPosition);
        }
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putLongArray("Tokens", tokens);
        byte[] routes = new byte[secondary.length]; for (var color : IndividualCableColor.values()) routes[color.ordinal()] = (byte) secondary(color).ordinal();
        tag.putByteArray("SecondaryRoutes", routes); tag.putLongArray("SecondaryTokens", secondaryTokens);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); shape = null;
        long[] saved = tag.getLongArray("Tokens"); System.arraycopy(saved, 0, tokens, 0, Math.min(saved.length, tokens.length));
        byte[] routes = tag.getByteArray("SecondaryRoutes");
        java.util.Arrays.fill(secondary, CableRoute.NONE);
        for (int i = 0; i < Math.min(routes.length, secondary.length); i++) {
            int id = routes[i]; if (id > 0 && id < CableRoute.values().length && IndividualCableComponents.validPair(IndividualCableColor.values()[i], primary(IndividualCableColor.values()[i]), CableRoute.values()[id])) secondary[i] = CableRoute.values()[id];
        }
        long[] identities = tag.getLongArray("SecondaryTokens"); System.arraycopy(identities, 0, secondaryTokens, 0, Math.min(identities.length, secondaryTokens.length)); shape = null;
        ListTag list = tag.getList("VisibleSpans", Tag.TAG_COMPOUND);
        clientSpans = list.stream().map(t -> CableSpans.Span.read((CompoundTag) t)).filter(java.util.Objects::nonNull).toList(); revision++;
        requestModelDataUpdate();
        if (level != null && level.isClientSide) {
            CableSpanIndex.client(level).replace(worldPosition, clientSpans);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            IndividualCableBlock.refreshCorners(level, worldPosition);
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries); ListTag list = new ListTag();
        for (var span : spans()) list.add(span.write()); tag.put("VisibleSpans", list); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
