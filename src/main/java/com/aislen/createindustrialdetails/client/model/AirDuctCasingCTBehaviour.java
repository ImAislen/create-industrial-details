package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.airduct.AirDuctBlock;
import com.aislen.createindustrialdetails.content.block.airduct.AirDuctCasing;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.decoration.encasing.CasingConnectivity;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class AirDuctCasingCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final PlatedIndustrialSteelCTBehaviour PLATED = new PlatedIndustrialSteelCTBehaviour();
    private final CasingConnectivity connectivity;

    public AirDuctCasingCTBehaviour(CasingConnectivity connectivity) {
        this.connectivity = connectivity;
    }

    private static boolean hasMaterialFace(BlockState state, Direction face) {
        return !(state.getBlock() instanceof AirDuctBlock) || !AirDuctBlock.hasCasingOpening(state, face);
    }

    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction face, TextureAtlasSprite sprite) {
        if (!hasMaterialFace(state, face)) return null;
        return switch (AirDuctCasing.fromState(state)) {
            case ANDESITE -> AllSpriteShifts.ANDESITE_CASING;
            case BRASS -> AllSpriteShifts.BRASS_CASING;
            case COPPER -> AllSpriteShifts.COPPER_CASING;
            case RAILWAY -> face.getAxis().isVertical() ? AllSpriteShifts.RAILWAY_CASING : AllSpriteShifts.RAILWAY_CASING_SIDE;
            case PLATED_INDUSTRIAL_STEEL -> PLATED.getShift(state, face, sprite);
            default -> null;
        };
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter level, BlockPos pos,
                              BlockPos otherPos, Direction face) {
        AirDuctCasing casing = AirDuctCasing.fromState(state);
        if (!casing.isConnectedTexture() || !hasMaterialFace(state, face)
                || !hasMaterialFace(level.getBlockState(otherPos), face)
                || isBeingBlocked(state, level, pos, otherPos, face)) return false;
        AirDuctCasing otherCasing = AirDuctCasing.fromState(other);
        if (otherCasing != AirDuctCasing.NONE) return casing == otherCasing;
        var source = connectivity.get(casing.materialState());
        var target = connectivity.get(other);
        return source != null && target != null && target.isSideValid(other, face)
                && source.isSideValid(casing.materialState(), face) && source.getCasing() == target.getCasing();
    }
}
