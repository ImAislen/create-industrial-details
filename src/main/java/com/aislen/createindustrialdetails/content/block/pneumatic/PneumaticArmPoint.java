package com.aislen.createindustrialdetails.content.block.pneumatic;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class PneumaticArmPoint extends ArmInteractionPoint {
    private final boolean receiver;

    private PneumaticArmPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        super(type, level, pos, state);
        this.receiver = ((PneumaticStationBlock) state.getBlock()).kind == PneumaticStationBlock.Kind.RECEIVER;
        this.mode = receiver ? Mode.TAKE : Mode.DEPOSIT;
    }

    public static void register(RegisterEvent event) {
        event.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.key(),
                ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "pneumatic_station"), () -> new ArmInteractionPointType() {
                    @Override
                    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
                        return state.getBlock() instanceof PneumaticStationBlock station
                                && (station.kind == PneumaticStationBlock.Kind.LAUNCHER
                                || station.kind == PneumaticStationBlock.Kind.RECEIVER);
                    }

                    @Override
                    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
                        return new PneumaticArmPoint(this, level, pos, state);
                    }
                });
    }

    @Override public void cycleMode() {}

    @Override
    protected Vec3 getInteractionPositionVector() {
        return Vec3.atBottomCenterOf(PneumaticStationBlock.base(cachedState, pos)).add(0, 1.5, -.5);
    }

    @Override
    protected net.minecraft.core.Direction getInteractionDirection() { return net.minecraft.core.Direction.SOUTH; }

    @Override
    public ItemStack insert(ArmBlockEntity arm, ItemStack stack, boolean simulate) {
        if (receiver || !(level instanceof ServerLevel server) || !isValid()) return stack;
        return PneumaticNetwork.get(server).insertPackage(server, PneumaticStationBlock.base(cachedState, pos), stack, simulate);
    }

    @Override
    public ItemStack extract(ArmBlockEntity arm, int slot, int amount, boolean simulate) {
        if (!receiver || slot != 0 || amount <= 0 || !(level instanceof ServerLevel server) || !isValid()) return ItemStack.EMPTY;
        return PneumaticNetwork.get(server).extractPackage(server, PneumaticStationBlock.base(cachedState, pos), simulate);
    }

    @Override
    public int getSlotCount(ArmBlockEntity arm) { return receiver ? 1 : 0; }
}
