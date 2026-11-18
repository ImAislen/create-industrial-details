package com.aislen.createindustrialdetails.content.block.supports;

import net.minecraft.util.StringRepresentable;

public enum SmallSteelWallBracketStyle implements StringRepresentable {
    STANDARD("standard"),
    STRAIGHT("straight");

    private final String id;

    SmallSteelWallBracketStyle(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
