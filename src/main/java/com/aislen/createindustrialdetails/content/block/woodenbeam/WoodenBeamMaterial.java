package com.aislen.createindustrialdetails.content.block.woodenbeam;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;
import java.util.List;

/** Central material definitions for the shared Wooden Beam implementation. */
public enum WoodenBeamMaterial {
    OAK("oak", "Oak", MapColor.WOOD, SoundType.WOOD, SoundType.WOOD,
            "oak_log", "oak_log_top", "stripped_oak_log", "stripped_oak_log_top", "oak_planks", true),
    SPRUCE("spruce", "Spruce", MapColor.PODZOL, SoundType.WOOD, SoundType.WOOD,
            "spruce_log", "spruce_log_top", "stripped_spruce_log", "stripped_spruce_log_top", "spruce_planks", true),
    BIRCH("birch", "Birch", MapColor.SAND, SoundType.WOOD, SoundType.WOOD,
            "birch_log", "birch_log_top", "stripped_birch_log", "stripped_birch_log_top", "birch_planks", true),
    JUNGLE("jungle", "Jungle", MapColor.DIRT, SoundType.WOOD, SoundType.WOOD,
            "jungle_log", "jungle_log_top", "stripped_jungle_log", "stripped_jungle_log_top", "jungle_planks", true),
    ACACIA("acacia", "Acacia", MapColor.COLOR_ORANGE, SoundType.WOOD, SoundType.WOOD,
            "acacia_log", "acacia_log_top", "stripped_acacia_log", "stripped_acacia_log_top", "acacia_planks", true),
    DARK_OAK("dark_oak", "Dark Oak", MapColor.COLOR_BROWN, SoundType.WOOD, SoundType.WOOD,
            "dark_oak_log", "dark_oak_log_top", "stripped_dark_oak_log", "stripped_dark_oak_log_top", "dark_oak_planks", true),
    MANGROVE("mangrove", "Mangrove", MapColor.COLOR_RED, SoundType.WOOD, SoundType.WOOD,
            "mangrove_log", "mangrove_log_top", "stripped_mangrove_log", "stripped_mangrove_log_top", "mangrove_planks", true),
    CHERRY("cherry", "Cherry", MapColor.TERRACOTTA_WHITE, SoundType.CHERRY_WOOD, SoundType.CHERRY_WOOD,
            "cherry_log", "cherry_log_top", "stripped_cherry_log", "stripped_cherry_log_top", "cherry_planks", true),
    CRIMSON("crimson", "Crimson", MapColor.CRIMSON_STEM, SoundType.STEM, SoundType.NETHER_WOOD,
            "crimson_stem", "crimson_stem_top", "stripped_crimson_stem", "stripped_crimson_stem_top", "crimson_planks", false),
    WARPED("warped", "Warped", MapColor.WARPED_STEM, SoundType.STEM, SoundType.NETHER_WOOD,
            "warped_stem", "warped_stem_top", "stripped_warped_stem", "stripped_warped_stem_top", "warped_planks", false),
    BAMBOO("bamboo", "Bamboo", MapColor.COLOR_YELLOW, SoundType.BAMBOO_WOOD, SoundType.BAMBOO_WOOD,
            "bamboo_block", "bamboo_block_top", "stripped_bamboo_block", "stripped_bamboo_block_top", "bamboo_planks", true);

    private static final int WOOD_FIRE_SPREAD_SPEED = 5;
    private static final int LOG_FLAMMABILITY = 5;
    private static final int PLANK_FLAMMABILITY = 20;

    private static final List<Variant> ALL_VARIANTS = Arrays.stream(values())
            .flatMap(material -> material.variants.stream())
            .toList();

    private final List<Variant> variants;

    WoodenBeamMaterial(
            String id,
            String displayName,
            MapColor mapColor,
            SoundType rawSound,
            SoundType plankSound,
            String rawSide,
            String rawCap,
            String strippedSide,
            String strippedCap,
            String plankTexture,
            boolean flammable
    ) {
        int fireSpreadSpeed = flammable ? WOOD_FIRE_SPREAD_SPEED : 0;
        int logFlammability = flammable ? LOG_FLAMMABILITY : 0;
        int plankFlammability = flammable ? PLANK_FLAMMABILITY : 0;
        String rawDisplayName = id.equals("bamboo") ? "Bamboo Beam" : displayName + " Wooden Beam";
        String strippedDisplayName = id.equals("bamboo")
                ? "Stripped Bamboo Beam"
                : "Stripped " + displayName + " Wooden Beam";

        variants = List.of(
                new Variant(
                        id + "_wooden_beam",
                        rawDisplayName,
                        texture(rawSide),
                        texture(rawCap),
                        mapColor,
                        rawSound,
                        2.0F,
                        2.0F,
                        flammable,
                        fireSpreadSpeed,
                        logFlammability
                ),
                new Variant(
                        "stripped_" + id + "_wooden_beam",
                        strippedDisplayName,
                        texture(strippedSide),
                        texture(strippedCap),
                        mapColor,
                        rawSound,
                        2.0F,
                        2.0F,
                        flammable,
                        fireSpreadSpeed,
                        logFlammability
                ),
                new Variant(
                        id + "_plank_beam",
                        displayName + " Plank Beam",
                        texture(plankTexture),
                        texture(plankTexture),
                        mapColor,
                        plankSound,
                        2.0F,
                        3.0F,
                        flammable,
                        fireSpreadSpeed,
                        plankFlammability
                )
        );
    }

    public List<Variant> variants() {
        return variants;
    }

    public static List<Variant> allVariants() {
        return ALL_VARIANTS;
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.withDefaultNamespace("block/" + path);
    }

    public record Variant(
            String registryName,
            String displayName,
            ResourceLocation sideTexture,
            ResourceLocation capTexture,
            MapColor mapColor,
            SoundType sound,
            float destroyTime,
            float explosionResistance,
            boolean ignitedByLava,
            int fireSpreadSpeed,
            int flammability
    ) {
        public String segmentModelPath() {
            return "block/wooden_beam/" + registryName;
        }

        public String attachmentModelPath() {
            return segmentModelPath() + "_attachment";
        }
    }
}
