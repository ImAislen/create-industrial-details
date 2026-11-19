package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.CAP_NEGATIVE;
import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.FACING;

final class ServiceTrenchCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final String SPRITE = "block/industrial_steel/service_trench";
    static final ResourceLocation CASING_SPRITE = ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, SPRITE);
    private static final CTSpriteShiftEntry SHIFT = CTSpriteShifter.getCT(AllCTTypes.HORIZONTAL,
            CASING_SPRITE,
            ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, SPRITE + "_connected"));

    @Override
    public boolean buildContextForOccludedDirections() {
        // An exterior neighbor can hide the block face while the recessed wall still renders.
        return true;
    }

    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction face, TextureAtlasSprite sprite) {
        return state != null && face.getAxis() != state.getValue(FACING).getAxis() ? SHIFT : null;
    }

    @Override
    protected Direction getRightDirection(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        // The atlas's right connection is the model's south end. Undo Create's face-dependent reversal.
        boolean reversed = face.getAxisDirection() == Direction.AxisDirection.POSITIVE || face == Direction.DOWN;
        return reversed ? facing : facing.getOpposite();
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter level, BlockPos pos,
                              BlockPos otherPos, Direction face) {
        return other.hasProperty(CAP_NEGATIVE) && other.getValue(FACING).getAxis() == state.getValue(FACING).getAxis();
    }
}
