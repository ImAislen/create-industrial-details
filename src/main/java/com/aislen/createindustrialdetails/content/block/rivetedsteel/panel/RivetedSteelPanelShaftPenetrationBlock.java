package com.aislen.createindustrialdetails.content.block.rivetedsteel.panel;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.content.decoration.bracket.BracketBlock;
import com.simibubi.create.content.decoration.bracket.BracketBlock.BracketType;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Hidden block stored inside Create's existing bracket slot.
 *
 * It is never placed directly in the world and has no BlockItem.
 */
public final class RivetedSteelPanelShaftPenetrationBlock extends BracketBlock {

    public RivetedSteelPanelShaftPenetrationBlock(Properties properties) {
        super(properties);
    }

    /**
     * Unlike Create's normal decorative brackets, our "bracket" represents
     * a panel pierced by the shaft, so it is valid when the clicked face IS
     * one of the shaft's end faces.
     *
     * Important:
     * The panel's FACING convention points OUTWARD from its supporting face.
     * A panel physically occupying the clicked end of the shaft therefore
     * needs the OPPOSITE stored facing.
     */
    @Override
    public Optional<BlockState> getSuitableBracket(
        BlockState targetState,
        Direction clickedFace
    ) {
        if (!ShaftBlock.isShaft(targetState)) {
            return Optional.empty();
        }

        Direction.Axis shaftAxis =
            targetState.getValue(RotatedPillarKineticBlock.AXIS);

        if (clickedFace.getAxis() != shaftAxis) {
            return Optional.empty();
        }

        return Optional.of(
            defaultBlockState()
                .setValue(TYPE, BracketType.SHAFT)
                .setValue(FACING, clickedFace.getOpposite())
                .setValue(AXIS_ALONG_FIRST_COORDINATE, false)
        );
    }

    @Override
    public Item asItem() {
        return ModBlocks.RIVETED_STEEL_PANEL.get().asItem();
    }
}
