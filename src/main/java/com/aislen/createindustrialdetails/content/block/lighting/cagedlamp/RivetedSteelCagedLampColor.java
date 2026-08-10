package com.aislen.createindustrialdetails.content.block.lighting.cagedlamp;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

public enum RivetedSteelCagedLampColor implements StringRepresentable {
    NATURAL("natural", null),
    WHITE("white", DyeColor.WHITE),
    ORANGE("orange", DyeColor.ORANGE),
    MAGENTA("magenta", DyeColor.MAGENTA),
    LIGHT_BLUE("light_blue", DyeColor.LIGHT_BLUE),
    YELLOW("yellow", DyeColor.YELLOW),
    LIME("lime", DyeColor.LIME),
    PINK("pink", DyeColor.PINK),
    GRAY("gray", DyeColor.GRAY),
    LIGHT_GRAY("light_gray", DyeColor.LIGHT_GRAY),
    CYAN("cyan", DyeColor.CYAN),
    PURPLE("purple", DyeColor.PURPLE),
    BLUE("blue", DyeColor.BLUE),
    BROWN("brown", DyeColor.BROWN),
    GREEN("green", DyeColor.GREEN),
    RED("red", DyeColor.RED),
    BLACK("black", DyeColor.BLACK);

    private final String name;
    private final DyeColor dyeColor;

    RivetedSteelCagedLampColor(String name, DyeColor dyeColor) {
        this.name = name;
        this.dyeColor = dyeColor;
    }

    public int getTintColor() {
        return dyeColor == null
                ? 0xFFFFFFFF
                : dyeColor.getTextureDiffuseColor();
    }

    public static RivetedSteelCagedLampColor fromDye(DyeColor dyeColor) {
        for (RivetedSteelCagedLampColor color : values()) {
            if (color.dyeColor == dyeColor) {
                return color;
            }
        }

        return NATURAL;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}