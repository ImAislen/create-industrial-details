package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.client.ControlBoxGoggleInspection;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public class ControlBoxBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private int signals;
    private boolean portsRemoved;

    public ControlBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_BOX.get(), pos, state);
    }

    public int getSignal(ControlChannel channel) {
        return channel.read(signals);
    }

    public int getComparatorOutput() {
        return Math.max(Math.max(getSignal(ControlChannel.RED), getSignal(ControlChannel.YELLOW)),
                Math.max(getSignal(ControlChannel.GREEN), getSignal(ControlChannel.BLUE)));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        int previousSize = tooltip.size();
        CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> ControlBoxGoggleInspection.addInformation(this, tooltip));
        return tooltip.size() > previousSize;
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, Player player, BlockHitResult hit) {
        if (isRemoved() || player == null || !GogglesItem.isWearingGoggles(player)
                || !worldPosition.equals(hit.getBlockPos())) return false;
        ControlChannel channel = ControlBoxBlock.controlAt(getBlockState(), worldPosition, hit);
        if (channel == null) return false;
        tooltip.add(Component.translatable("create_industrial_details.control_box.value",
                Component.translatable(channel.translationKey()), getSignal(channel)));
        return true;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        for (ControlChannel channel : ControlChannel.values()) behaviours.add(new ControlBoxValueBehaviour(this, channel));
    }

    public ControlBoxValueBehaviour getControl(ControlChannel channel) {
        return getBehaviour(ControlBoxValueBehaviour.type(channel));
    }

    public boolean canConnectCable(Direction side) {
        return ControlBoxBlock.isCablePort(getBlockState(), side);
    }

    public int getSignal(ControlChannel channel, Direction side) {
        return !isRemoved() && canConnectCable(side) ? getSignal(channel) : 0;
    }

    public boolean setSignal(ControlChannel channel, int value) {
        if (isRemoved() || level != null && level.isClientSide) return false;
        int updated = channel.write(signals, value);
        if (updated == signals) return false;
        int previousOutput = getComparatorOutput();
        signals = updated;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            notifyCables();
            updateComparators(previousOutput);
        }
        return true;
    }

    private void notifyCables() {
        if (level instanceof ServerLevel server) CableNetworks.get(server).sourceChanged(worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).validate(worldPosition);
        else if (level != null) com.aislen.createindustrialdetails.content.block.cable.individual.CableSpanIndex.client(level).replace(worldPosition, visibleSpans);
        portsRemoved = false;
        initialize();
        if (level instanceof ServerLevel server)
            CableNetworks.get(server).topologyChanged(worldPosition, getBlockState());
        notifyCables();
        notifyComparators();
    }

    private void updateComparators(int previousOutput) {
        if (previousOutput != getComparatorOutput()) notifyComparators();
    }

    void notifyComparators() {
        if (level != null && !level.isClientSide)
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && level.isClientSide) com.aislen.createindustrialdetails.content.block.cable.individual.CableSpanIndex.client(level).forget(worldPosition);
        if (!portsRemoved) {
            portsRemoved = true;
            notifyCables();
        }
    }

    private List<com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.Span> visibleSpans = List.of();
    public int cableRevision;
    public List<com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.Span> portSpans() {
        return level instanceof ServerLevel server ? com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(server).at(worldPosition) : visibleSpans;
    }
    private long cableToken = java.util.UUID.randomUUID().getMostSignificantBits();
    public long cableToken() { return cableToken; }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Signals", signals);
        tag.putLong("CableToken", cableToken);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        int previousSignals = signals;
        int previousOutput = getComparatorOutput();
        super.read(tag, registries, clientPacket);
        if (tag.contains("VisibleSpans")) {
            var list = tag.getList("VisibleSpans", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                    .map(t -> com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.Span.read((CompoundTag) t)).filter(java.util.Objects::nonNull).toList();
            if (!list.equals(visibleSpans)) { visibleSpans = list; cableRevision++; }
            if (level != null && level.isClientSide) com.aislen.createindustrialdetails.content.block.cable.individual.CableSpanIndex.client(level).replace(worldPosition, visibleSpans);
        }
        if (tag.contains("CableToken")) cableToken = tag.getLong("CableToken");
        signals = Math.clamp(tag.getInt("Signals"), 0, 65535);
        updateComparators(previousOutput);
        if (previousSignals != signals) notifyCables();
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putInt("Signals", signals);
        tag.putLong("CableToken", cableToken);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveCustomOnly(registries); var spans = new net.minecraft.nbt.ListTag();
        for (var span : portSpans()) spans.add(span.write()); tag.put("VisibleSpans", spans); return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (signals != 0) builder.set(ModDataComponents.CONTROL_BOX_SIGNALS.get(), signals);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        int previousSignals = signals;
        int previousOutput = getComparatorOutput();
        super.applyImplicitComponents(input);
        signals = Math.clamp(input.getOrDefault(ModDataComponents.CONTROL_BOX_SIGNALS.get(), 0), 0, 65535);
        updateComparators(previousOutput);
        if (previousSignals != signals) notifyCables();
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Signals");
    }
}
