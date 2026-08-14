package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.content.block.woodenbeam.structure.WoodenBeamStructureShapes;
import com.cake.struts.content.block.StrutBlockEntity;
import com.cake.struts.content.connection.GirderConnectionNode;
import com.cake.struts.content.structure.GirderStrutStructureShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class WoodenBeamBlockEntity extends StrutBlockEntity {

    public static BiConsumer<net.minecraft.world.level.Level, WoodenBeamBlockEntity> CLIENT_ENDPOINT_UPDATE_LISTENER;
    public static BiConsumer<net.minecraft.world.level.Level, BlockPos> CLIENT_ENDPOINT_REMOVE_LISTENER;

    private static final String ENDPOINTS_TAG = "WoodenBeamEndpoints";
    private final Map<BlockPos, WoodenBeamConnectionEndpoints> endpointsByPeer = new HashMap<>();

    public WoodenBeamBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WOODEN_BEAM.get(), pos, state);
    }

    public boolean hasConnectionTo(BlockPos peerPos) {
        BlockPos relative = peerPos.subtract(getBlockPos());
        return getConnectionsCopy().stream().anyMatch(connection -> connection.relativeOffset().equals(relative));
    }

    public void addBeamConnection(
            BlockPos peerPos,
            Direction localFace,
            WoodenBeamSnapPoint localSnap,
            Direction peerFace,
            WoodenBeamSnapPoint peerSnap
    ) {
        BlockPos relative = peerPos.subtract(getBlockPos()).immutable();
        if (peerPos.equals(getBlockPos()) || hasConnectionTo(peerPos)) {
            return;
        }
        endpointsByPeer.put(relative, new WoodenBeamConnectionEndpoints(localFace, localSnap, peerFace, peerSnap));
        super.addConnection(peerPos, peerFace);

        if (level != null && !level.isClientSide) {
            // super.addConnection has no opt-out for its centered shape registration.
            // Replace that cached shape immediately with the endpoint-aware one.
            GirderStrutStructureShapes.unregisterConnection(level, getBlockPos(), peerPos);
            registerEndpointAwareShape(peerPos, endpointsByPeer.get(relative));
        }
    }

    @Override
    public void addConnection(BlockPos other, Direction otherFacing) {
        BlockPos relative = other.subtract(getBlockPos()).immutable();
        endpointsByPeer.putIfAbsent(relative, legacyEndpoints(otherFacing));
        super.addConnection(other, otherFacing);
        if (level != null && !level.isClientSide) {
            GirderStrutStructureShapes.unregisterConnection(level, getBlockPos(), other);
            registerEndpointAwareShape(other, endpointsByPeer.get(relative));
        }
    }

    @Override
    public void removeConnection(BlockPos pos) {
        if (level != null && !level.isClientSide) {
            WoodenBeamStructureShapes.unregisterConnection(level, getBlockPos(), pos);
        }
        endpointsByPeer.remove(pos.subtract(getBlockPos()));
        super.removeConnection(pos);
    }

    public WoodenBeamConnectionEndpoints getEndpoints(GirderConnectionNode connection) {
        return endpointsByPeer.getOrDefault(connection.relativeOffset(), legacyEndpoints(connection.peerFacing()));
    }

    @Override
    public int getConnectionHash() {
        return 31 * super.getConnectionHash() + endpointsByPeer.hashCode();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) {
            return;
        }
        for (GirderConnectionNode connection : getConnectionsCopy()) {
            BlockPos peer = connection.absoluteFrom(getBlockPos());
            GirderStrutStructureShapes.unregisterConnection(level, getBlockPos(), peer);
            registerEndpointAwareShape(peer, getEndpoints(connection));
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            for (GirderConnectionNode connection : getConnectionsCopy()) {
                WoodenBeamStructureShapes.unregisterConnection(
                        level,
                        getBlockPos(),
                        connection.absoluteFrom(getBlockPos())
                );
            }
        }
        if (level != null && level.isClientSide && CLIENT_ENDPOINT_REMOVE_LISTENER != null) {
            CLIENT_ENDPOINT_REMOVE_LISTENER.accept(level, getBlockPos());
        }
        super.setRemoved();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (level != null && level.isClientSide && CLIENT_ENDPOINT_UPDATE_LISTENER != null) {
            CLIENT_ENDPOINT_UPDATE_LISTENER.accept(level, this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag endpointList = new ListTag();
        for (GirderConnectionNode connection : getConnectionsCopy()) {
            WoodenBeamConnectionEndpoints endpoints = getEndpoints(connection);
            CompoundTag endpointTag = new CompoundTag();
            endpointTag.putInt("X", connection.relativeOffset().getX());
            endpointTag.putInt("Y", connection.relativeOffset().getY());
            endpointTag.putInt("Z", connection.relativeOffset().getZ());
            endpointTag.putByte("LocalFace", (byte) endpoints.localFace().get3DDataValue());
            endpointTag.putByte("LocalSnap", endpoints.localSnap().id());
            endpointTag.putByte("PeerFace", (byte) endpoints.peerFace().get3DDataValue());
            endpointTag.putByte("PeerSnap", endpoints.peerSnap().id());
            endpointList.add(endpointTag);
        }
        tag.put(ENDPOINTS_TAG, endpointList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        endpointsByPeer.clear();
        if (tag.contains(ENDPOINTS_TAG, Tag.TAG_LIST)) {
            ListTag endpointList = tag.getList(ENDPOINTS_TAG, Tag.TAG_COMPOUND);
            for (Tag entry : endpointList) {
                if (!(entry instanceof CompoundTag endpointTag)) {
                    continue;
                }
                BlockPos relative = new BlockPos(
                        endpointTag.getInt("X"),
                        endpointTag.getInt("Y"),
                        endpointTag.getInt("Z")
                );
                Direction localFace = Direction.from3DDataValue(endpointTag.getByte("LocalFace"));
                Direction peerFace = Direction.from3DDataValue(endpointTag.getByte("PeerFace"));
                endpointsByPeer.put(relative, new WoodenBeamConnectionEndpoints(
                        localFace,
                        WoodenBeamSnapPoint.byId(endpointTag.getByte("LocalSnap")),
                        peerFace,
                        WoodenBeamSnapPoint.byId(endpointTag.getByte("PeerSnap"))
                ));
            }
        }

        // Prototype saves contain only stock Connections. They remain intact and
        // acquire deterministic center metadata on their next save.
        for (GirderConnectionNode connection : getConnectionsCopy()) {
            endpointsByPeer.putIfAbsent(connection.relativeOffset(), legacyEndpoints(connection.peerFacing()));
        }
    }

    private WoodenBeamConnectionEndpoints legacyEndpoints(Direction peerFacing) {
        return new WoodenBeamConnectionEndpoints(
                getAttachmentDirection(),
                WoodenBeamSnapPoint.CENTER,
                peerFacing,
                WoodenBeamSnapPoint.CENTER
        );
    }

    private void registerEndpointAwareShape(BlockPos peerPos, WoodenBeamConnectionEndpoints endpoints) {
        if (level == null) {
            return;
        }
        WoodenBeamStructureShapes.registerConnection(
                level,
                getBlockPos(),
                WoodenBeamEndpoints.attachment(getBlockPos(), endpoints.localFace(), endpoints.localSnap()),
                peerPos,
                WoodenBeamEndpoints.attachment(peerPos, endpoints.peerFace(), endpoints.peerSnap()),
                getModelType()
        );
    }
}
