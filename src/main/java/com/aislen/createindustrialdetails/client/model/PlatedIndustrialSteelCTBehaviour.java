package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumMap;
import java.util.Map;

public final class PlatedIndustrialSteelCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final Map<Direction, CTSpriteShiftEntry> SHIFTS = createShifts();

    private static Map<Direction, CTSpriteShiftEntry> createShifts() {
        var shifts = new EnumMap<Direction, CTSpriteShiftEntry>(Direction.class);
        for (Direction face : Direction.values()) {
            String path = "block/riveted_steel/block_of_plated_industrial_steel/" + face.getName();
            shifts.put(face, CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
                    ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, path),
                    ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, path + "_connected")));
        }
        return shifts;
    }

    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction face, TextureAtlasSprite sprite) {
        return SHIFTS.get(face);
    }
}
