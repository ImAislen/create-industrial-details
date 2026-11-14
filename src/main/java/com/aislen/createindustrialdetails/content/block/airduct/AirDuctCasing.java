package com.aislen.createindustrialdetails.content.block.airduct;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.function.Supplier;

public enum AirDuctCasing implements StringRepresentable {
    NONE("none", () -> Blocks.AIR),
    ANDESITE("andesite", () -> AllBlocks.ANDESITE_CASING.get()),
    BRASS("brass", () -> AllBlocks.BRASS_CASING.get()),
    COPPER("copper", () -> AllBlocks.COPPER_CASING.get()),
    RAILWAY("railway", () -> AllBlocks.RAILWAY_CASING.get()),
    INDUSTRIAL_IRON("industrial_iron", () -> AllBlocks.INDUSTRIAL_IRON_BLOCK.get()),
    INDUSTRIAL_STEEL("industrial_steel", () -> ModBlocks.BLOCK_OF_INDUSTRIAL_STEEL.get()),
    PLATED_INDUSTRIAL_STEEL("plated_industrial_steel", () -> ModBlocks.BLOCK_OF_PLATED_INDUSTRIAL_STEEL.get());

    private static final AirDuctCasing[] ALL = values();

    private final String name;
    private final Supplier<Block> material;

    AirDuctCasing(String name, Supplier<Block> material) {
        this.name = name;
        this.material = material;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public ItemStack asStack() {
        return this == NONE ? ItemStack.EMPTY : new ItemStack(material.get());
    }

    public boolean isConnectedTexture() {
        return this != NONE && this != INDUSTRIAL_STEEL && this != INDUSTRIAL_IRON;
    }

    public net.minecraft.world.level.block.state.BlockState materialState() {
        return material.get().defaultBlockState();
    }

    public static AirDuctCasing fromState(net.minecraft.world.level.block.state.BlockState state) {
        if (state.getBlock() instanceof AirDuctBlock) return state.getValue(AirDuctBlock.CASING);
        for (AirDuctCasing casing : ALL) {
            if (casing != NONE && state.is(casing.material.get())) return casing;
        }
        return NONE;
    }

    static AirDuctCasing fromItem(ItemStack stack) {
        for (AirDuctCasing casing : ALL) {
            if (casing != NONE && stack.is(casing.material.get().asItem())) return casing;
        }
        return NONE;
    }
}
