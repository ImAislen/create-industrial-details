package com.aislen.createindustrialdetails.content.block.cable.individual;

import com.aislen.createindustrialdetails.content.block.cable.ControlChannel;
import com.aislen.createindustrialdetails.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public enum IndividualCableColor {
    RED(ControlChannel.RED, 3.5), YELLOW(ControlChannel.YELLOW, 6),
    GREEN(ControlChannel.GREEN, 8.5), BLUE(ControlChannel.BLUE, 11);

    public final ControlChannel channel;
    public final double minX;
    public final EnumProperty<CableRoute> property;

    IndividualCableColor(ControlChannel channel, double minX) {
        this.channel = channel;
        this.minX = minX;
        property = EnumProperty.create(channel.name().toLowerCase(java.util.Locale.ROOT), CableRoute.class);
    }

    public Item item() {
        return switch (this) {
            case RED -> ModItems.RED_CONTROL_CABLE.get();
            case YELLOW -> ModItems.YELLOW_CONTROL_CABLE.get();
            case GREEN -> ModItems.GREEN_CONTROL_CABLE.get();
            case BLUE -> ModItems.BLUE_CONTROL_CABLE.get();
        };
    }
    public String modelPath() { return "block/cable_tray/cable_" + property.getName(); }
}
