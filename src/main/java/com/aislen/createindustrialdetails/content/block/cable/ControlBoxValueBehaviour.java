package com.aislen.createindustrialdetails.content.block.cable;

import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

public final class ControlBoxValueBehaviour extends BlockEntityBehaviour implements ValueSettingsBehaviour {
    private static final List<BehaviourType<ControlBoxValueBehaviour>> TYPES = List.of(
            new BehaviourType<>("ControlRed"), new BehaviourType<>("ControlYellow"),
            new BehaviourType<>("ControlGreen"), new BehaviourType<>("ControlBlue"));
    private final ControlBoxBlockEntity owner;
    private final ControlChannel channel;
    private final ValueBoxTransform slot;

    ControlBoxValueBehaviour(ControlBoxBlockEntity owner, ControlChannel channel) {
        super(owner);
        this.owner = owner;
        this.channel = channel;
        slot = new Slot(owner, channel);
    }

    static BehaviourType<ControlBoxValueBehaviour> type(ControlChannel channel) {
        return TYPES.get(channel.ordinal());
    }

    @Override
    public BehaviourType<?> getType() {
        return type(channel);
    }

    @Override
    public int netId() {
        return channel.ordinal();
    }

    @Override
    public String getClipboardKey() {
        return getType().getName();
    }

    @Override
    public boolean isActive() {
        return !owner.isRemoved() && owner.getBlockState().getValue(ControlBoxBlock.OPEN);
    }

    @Override
    public boolean mayInteract(Player player) {
        return player.mayBuild() && !player.isSpectator() && !ControlBoxBlock.hasWrench(player);
    }

    @Override
    public boolean bypassesInput(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem || stack.is(Tags.Items.TOOLS_WRENCH);
    }

    @Override
    public boolean testHit(Vec3 hit) {
        return isActive() && ControlBoxGeometry.controlAt(owner.getBlockState(), hit.subtract(Vec3.atLowerCornerOf(getPos()))) == channel;
    }

    public int getValue() {
        return owner.getSignal(channel);
    }

    public void setValue(int value) {
        owner.setSignal(channel, value);
    }

    @Override
    public ValueBoxTransform getSlotPositioning() {
        return slot;
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, getValue());
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        if (!isActive() || settings.row() != 0 || player != null && !mayInteract(player)
                || settings.equals(getValueSettings())) return;
        setValue(settings.value());
        playFeedbackSound(this);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
        return new ValueSettingsBoard(Component.translatable(channel.translationKey()), 15, 5,
                List.of(Component.translatable("create_industrial_details.control_box.signal_strength")),
                new ValueSettingsFormatter(ValueSettings::format));
    }

    private static class Slot extends ValueBoxTransform.Sided {
        private final ControlBoxBlockEntity owner;
        private final Vec3 location;
        private final Vec3[] offsets = new Vec3[4];
        private final ControlChannel channel;

        private Slot(ControlBoxBlockEntity owner, ControlChannel channel) {
            this.owner = owner;
            this.channel = channel;
            location = new Vec3((5.125 + 2 * channel.ordinal()) / 16, 8.375 / 16, 4.51 / 16);
            for (Direction face : Direction.Plane.HORIZONTAL)
                offsets[face.get2DDataValue()] = ControlBoxGeometry.fromModel(face, location);
        }

        @Override
        protected Vec3 getSouthLocation() {
            return location;
        }

        @Override
        public Direction getSide() {
            return owner.getBlockState().getValue(ControlBoxBlock.FACING);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return offsets[state.getValue(ControlBoxBlock.FACING).get2DDataValue()];
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            return state.getValue(ControlBoxBlock.OPEN) && side == state.getValue(ControlBoxBlock.FACING);
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 hit) {
            return ControlBoxGeometry.controlAt(state, hit) == channel;
        }

        @Override
        public float getScale() {
            return .075f;
        }

        @Override
        public float getFontScale() {
            return 1 / 16f;
        }
    }
}
