package com.aislen.createindustrialdetails.content.block.lighting.cagedlamp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record RivetedSteelCagedLampFrequencies(
        ItemStack first,
        ItemStack second
) {

    public static final Codec<RivetedSteelCagedLampFrequencies> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ItemStack.OPTIONAL_CODEC
                            .fieldOf("first")
                            .forGetter(frequencies -> frequencies.first),
                    ItemStack.OPTIONAL_CODEC
                            .fieldOf("second")
                            .forGetter(frequencies -> frequencies.second)
            ).apply(instance, RivetedSteelCagedLampFrequencies::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RivetedSteelCagedLampFrequencies
            > STREAM_CODEC = StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    frequencies -> frequencies.first,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    frequencies -> frequencies.second,
                    RivetedSteelCagedLampFrequencies::new
            );

    public RivetedSteelCagedLampFrequencies {
        first = normalize(first);
        second = normalize(second);
    }

    @Override
    public ItemStack first() {
        return first.copy();
    }

    @Override
    public ItemStack second() {
        return second.copy();
    }

    public boolean isEmpty() {
        return first.isEmpty() && second.isEmpty();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof RivetedSteelCagedLampFrequencies other))
            return false;
        return ItemStack.isSameItemSameComponents(first, other.first)
                && ItemStack.isSameItemSameComponents(second, other.second);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(first)
                + ItemStack.hashItemAndComponents(second);
    }

    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return ItemStack.EMPTY;
        return stack.copyWithCount(1);
    }
}
