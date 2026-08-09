package com.aislen.createindustrialdetails.client.model;

import java.util.Map;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.beam.RivetedSteelBeamBlock;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(
        modid = CreateIndustrialDetails.MOD_ID,
        value = Dist.CLIENT
)
public final class ModModelEvents {

    // Models

    private static final ResourceLocation END_A =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_a");

    private static final ResourceLocation END_B =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_b");

    private static final ResourceLocation END_A_X =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_a_x");

    private static final ResourceLocation END_B_X =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_b_x");

    private static final ResourceLocation END_A_Y =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_a_y");

    private static final ResourceLocation END_B_Y =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_b_y");

    private static final ResourceLocation END_A_Y_ROTATED =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_a_y_rotated");

    private static final ResourceLocation END_B_Y_ROTATED =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_end_b_y_rotated");

    private static final ResourceLocation VERTICAL_CROSS =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_vertical_cross");

    private static final ResourceLocation VERTICAL_CROSS_ROTATED =
            id("block/riveted_steel/riveted_steel_beam/riveted_steel_beam_vertical_cross_rotated");


    // Registration

    @SubscribeEvent
    public static void registerAdditional(
            ModelEvent.RegisterAdditional event
    ) {
        event.register(ModelResourceLocation.standalone(END_A));
        event.register(ModelResourceLocation.standalone(END_B));

        event.register(ModelResourceLocation.standalone(END_A_X));
        event.register(ModelResourceLocation.standalone(END_B_X));

        event.register(ModelResourceLocation.standalone(END_A_Y));
        event.register(ModelResourceLocation.standalone(END_B_Y));

        event.register(
                ModelResourceLocation.standalone(
                        END_A_Y_ROTATED
                )
        );

        event.register(
                ModelResourceLocation.standalone(
                        END_B_Y_ROTATED
                )
        );

        event.register(
                ModelResourceLocation.standalone(
                        VERTICAL_CROSS
                )
        );

        event.register(
                ModelResourceLocation.standalone(
                        VERTICAL_CROSS_ROTATED
                )
        );
    }


    // Baking

    @SubscribeEvent
    public static void modifyBakingResult(
            ModelEvent.ModifyBakingResult event
    ) {
        Map<ModelResourceLocation, BakedModel> models =
                event.getModels();

        BakedModel endA =
                getModel(models, END_A);

        BakedModel endB =
                getModel(models, END_B);

        BakedModel endAX =
                getModel(models, END_A_X);

        BakedModel endBX =
                getModel(models, END_B_X);

        BakedModel endAY =
                getModel(models, END_A_Y);

        BakedModel endBY =
                getModel(models, END_B_Y);

        BakedModel endAYRotated =
                getModel(models, END_A_Y_ROTATED);

        BakedModel endBYRotated =
                getModel(models, END_B_Y_ROTATED);

        BakedModel verticalCross =
                getModel(models, VERTICAL_CROSS);

        BakedModel verticalCrossRotated =
                getModel(models, VERTICAL_CROSS_ROTATED);

        for (BlockState state :
                ModBlocks.RIVETED_STEEL_BEAM
                        .get()
                        .getStateDefinition()
                        .getPossibleStates()) {

            Direction.Axis axis =
                    state.getValue(GirderBlock.AXIS);

            boolean verticalRotated =
                    state.getValue(
                            RivetedSteelBeamBlock.VERTICAL_ROTATED
                    );

            BakedModel negativeEnd;
            BakedModel positiveEnd;

            switch (axis) {
                case Z -> {
                    negativeEnd = endA;
                    positiveEnd = endB;
                }

                case X -> {
                    negativeEnd = endAX;
                    positiveEnd = endBX;
                }

                case Y -> {
                    if (verticalRotated) {
                        negativeEnd = endBYRotated;
                        positiveEnd = endAYRotated;
                    } else {
                        negativeEnd = endBY;
                        positiveEnd = endAY;
                    }
                }

                default -> throw new IllegalStateException();
            }

            ModelResourceLocation location =
                    BlockModelShaper.stateToModelLocation(state);

            models.computeIfPresent(
                    location,
                    (key, original) ->
                            new RivetedSteelBeamModel(
                                    original,
                                    axis,
                                    negativeEnd,
                                    positiveEnd,
                                    verticalCrossRotated,
                                    verticalCross
                            )
            );
        }
    }


    // Helpers

    private static BakedModel getModel(
            Map<ModelResourceLocation, BakedModel> models,
            ResourceLocation location
    ) {
        BakedModel model = models.get(
                ModelResourceLocation.standalone(location)
        );

        if (model == null) {
            throw new IllegalStateException(
                    "Missing beam model: " + location
            );
        }

        return model;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                CreateIndustrialDetails.MOD_ID,
                path
        );
    }
}
