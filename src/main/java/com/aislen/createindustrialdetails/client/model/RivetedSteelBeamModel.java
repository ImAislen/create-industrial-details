package com.aislen.createindustrialdetails.client.model;

import java.util.ArrayList;
import java.util.List;

import com.aislen.createindustrialdetails.content.block.rivetedsteel.beam.RivetedSteelBeamBlock;
import com.simibubi.create.content.decoration.girder.GirderBlock;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class RivetedSteelBeamModel
        extends BakedModelWrapper<BakedModel> {

    // Visual State

    private static final ModelProperty<Integer> VISUAL_MASK =
            new ModelProperty<>();

    private static final int NEGATIVE_CONNECTED = 1;
    private static final int POSITIVE_CONNECTED = 1 << 1;
    private static final int TOP_X = 1 << 2;
    private static final int TOP_Z = 1 << 3;


    // Models

    private final Direction.Axis axis;

    private final BakedModel negativeEnd;
    private final BakedModel positiveEnd;
    private final BakedModel topX;
    private final BakedModel topZ;


    // Setup

    public RivetedSteelBeamModel(
            BakedModel originalModel,
            Direction.Axis axis,
            BakedModel negativeEnd,
            BakedModel positiveEnd,
            BakedModel topX,
            BakedModel topZ
    ) {
        super(originalModel);

        this.axis = axis;
        this.negativeEnd = negativeEnd;
        this.positiveEnd = positiveEnd;
        this.topX = topX;
        this.topZ = topZ;
    }


    // Model Data

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ModelData modelData
    ) {
        ModelData baseData =
                originalModel.getModelData(
                        level,
                        pos,
                        state,
                        modelData
                );

        Direction negativeDirection =
                Direction.fromAxisAndDirection(
                        axis,
                        Direction.AxisDirection.NEGATIVE
                );

        Direction positiveDirection =
                Direction.fromAxisAndDirection(
                        axis,
                        Direction.AxisDirection.POSITIVE
                );

        BlockState negativeState =
                level.getBlockState(
                        pos.relative(negativeDirection)
                );

        BlockState positiveState =
                level.getBlockState(
                        pos.relative(positiveDirection)
                );

        int mask = 0;

        if (connectsAlongAxis(negativeState, axis)) {
            mask |= NEGATIVE_CONNECTED;
        }

        if (connectsAlongAxis(positiveState, axis)) {
            mask |= POSITIVE_CONNECTED;
        }

        if (axis == Direction.Axis.Y
                && positiveState.getBlock()
                instanceof RivetedSteelBeamBlock
                && positiveState.getValue(GirderBlock.AXIS)
                != Direction.Axis.Y) {

            if (positiveState.getValue(GirderBlock.X)) {
                mask |= TOP_X;
            }

            if (positiveState.getValue(GirderBlock.Z)) {
                mask |= TOP_Z;
            }
        }

        return baseData
                .derive()
                .with(VISUAL_MASK, mask)
                .build();
    }

    private static boolean connectsAlongAxis(
            BlockState neighbour,
            Direction.Axis axis
    ) {
        if (!(neighbour.getBlock()
                instanceof RivetedSteelBeamBlock)) {
            return false;
        }

        return switch (axis) {
            case X -> neighbour.getValue(GirderBlock.X);
            case Z -> neighbour.getValue(GirderBlock.Z);
            case Y -> true;
        };
    }


    // Quads

    @Override
    public List<BakedQuad> getQuads(
            BlockState state,
            Direction side,
            RandomSource random,
            ModelData modelData,
            RenderType renderType
    ) {
        List<BakedQuad> baseQuads =
                originalModel.getQuads(
                        state,
                        side,
                        random,
                        modelData,
                        renderType
                );

        if (side != null) {
            return baseQuads;
        }

        Integer value = modelData.get(VISUAL_MASK);

        if (value == null) {
            return baseQuads;
        }

        int mask = value;

        boolean showNegative =
                (mask & NEGATIVE_CONNECTED) == 0;

        boolean showPositive =
                (mask & POSITIVE_CONNECTED) == 0;

        boolean showTopX =
                (mask & TOP_X) != 0;

        boolean showTopZ =
                (mask & TOP_Z) != 0;

        if (!showNegative
                && !showPositive
                && !showTopX
                && !showTopZ) {
            return baseQuads;
        }

        List<BakedQuad> quads =
                new ArrayList<>(baseQuads.size() + 32);

        quads.addAll(baseQuads);

        if (showNegative) {
            addModel(
                    quads,
                    negativeEnd,
                    state,
                    random,
                    renderType
            );
        }

        if (showPositive) {
            addModel(
                    quads,
                    positiveEnd,
                    state,
                    random,
                    renderType
            );
        }

        if (showTopX) {
            addModel(
                    quads,
                    topX,
                    state,
                    random,
                    renderType
            );
        }

        if (showTopZ) {
            addModel(
                    quads,
                    topZ,
                    state,
                    random,
                    renderType
            );
        }

        return quads;
    }

    private static void addModel(
            List<BakedQuad> output,
            BakedModel model,
            BlockState state,
            RandomSource random,
            RenderType renderType
    ) {
        output.addAll(
                model.getQuads(
                        state,
                        null,
                        random,
                        ModelData.EMPTY,
                        renderType
                )
        );
    }
}
