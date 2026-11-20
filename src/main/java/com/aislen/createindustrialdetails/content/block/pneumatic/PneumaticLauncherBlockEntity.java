package com.aislen.createindustrialdetails.content.block.pneumatic;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class PneumaticLauncherBlockEntity extends KineticBlockEntity {
    private float cooldown;
    private long retryAt;

    public PneumaticLauncherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PNEUMATIC_LAUNCHER.get(), pos, state);
    }

    // Create arms advance by RPM / 1024 per tick. One launcher cycle takes one arm move.
    public static float workPerTick(float rpm) {
        return Math.min(256, Math.abs(rpm)) / 1024f;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level instanceof ServerLevel server) PneumaticNetwork.get(server).refreshLauncherDisplay(server, worldPosition);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server)) return;
        float work = workPerTick(getSpeed());
        if (cooldown > 0 && work > 0) {
            cooldown = Math.max(0, cooldown - work);
            setChanged();
        }
        if (work == 0 || cooldown > 0 || !getBlockState().getValue(PneumaticStationBlock.LOADED)
                || server.getGameTime() < retryAt) return;
        if (PneumaticNetwork.get(server).dispatch(server, worldPosition)) {
            cooldown = 1;
            setChanged();
        } else {
            retryAt = server.getGameTime() + 8;
        }
    }

    public void packageLoaded() { retryAt = 0; }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        cooldown = tag.getFloat("DispatchCooldown");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (cooldown > 0) tag.putFloat("DispatchCooldown", cooldown);
    }
}
