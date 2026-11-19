package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableColor;
import java.util.Map;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.industrialsteel.beam.IndustrialSteelBeamBlock;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamMaterial;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.foundation.block.connected.CTModel;

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
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(
        modid = CreateIndustrialDetails.MOD_ID,
        value = Dist.CLIENT
)
public final class ModModelEvents {
    private static final PlatedIndustrialSteelCTBehaviour PLATED_STEEL_CT = new PlatedIndustrialSteelCTBehaviour();
    private static final ServiceTrenchCTBehaviour TRENCH_CT = new ServiceTrenchCTBehaviour();
    private static final AirDuctCasingCTBehaviour AIR_DUCT_CT = new AirDuctCasingCTBehaviour(
            com.simibubi.create.CreateClient.CASING_CONNECTIVITY);


    private static final ResourceLocation END_A =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_a");

    private static final ResourceLocation END_B =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_b");

    private static final ResourceLocation END_A_X =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_a_x");

    private static final ResourceLocation END_B_X =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_b_x");

    private static final ResourceLocation END_A_Y =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_a_y");

    private static final ResourceLocation END_B_Y =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_b_y");

    private static final ResourceLocation END_A_Y_ROTATED =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_a_y_rotated");

    private static final ResourceLocation END_B_Y_ROTATED =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_end_b_y_rotated");

    private static final ResourceLocation VERTICAL_CROSS =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_vertical_cross");

    private static final ResourceLocation VERTICAL_CROSS_ROTATED =
            id("block/industrial_steel/industrial_steel_beam/industrial_steel_beam_vertical_cross_rotated");


    @SubscribeEvent
    public static void registerCopycatColors(RegisterColorHandlersEvent.Block event) {
        event.register(com.simibubi.create.content.decoration.copycat.CopycatBlock.wrappedColor(),
                ModBlocks.COPYCAT_DIAGONAL_BYTE.get());
    }

    @SubscribeEvent
    public static void registerAdditional(
            ModelEvent.RegisterAdditional event
    ) {
        ControlBoxRenderer.registerModels(event);
        for (var color : IndividualCableColor.values()) {
            event.register(ModelResourceLocation.standalone(id(color.modelPath())));
            event.register(ModelResourceLocation.standalone(id(color.modelPath() + "_elevated")));
        }
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

        for (WoodenBeamMaterial.Variant variant : WoodenBeamMaterial.allVariants()) {
            event.register(ModelResourceLocation.standalone(id(variant.segmentModelPath())));
        }
    }



    @SubscribeEvent
    public static void modifyBakingResult(
            ModelEvent.ModifyBakingResult event
    ) {
        Map<ModelResourceLocation, BakedModel> models =
                event.getModels();

        var cableModels = new java.util.IdentityHashMap<BakedModel, BakedModel>();
        for (BlockState state : ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().getStateDefinition().getPossibleStates())
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, original) -> cableModels.computeIfAbsent(original, IndividualCableConnectionModel::new));

        for (BlockState state : ModBlocks.COPYCAT_DIAGONAL_BYTE.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, original) -> new CopycatDiagonalByteModel(original));
        }

        var trenchModels = new java.util.IdentityHashMap<BakedModel, BakedModel>();
        for (var trench : java.util.List.of(ModBlocks.SERVICE_TRENCH, ModBlocks.SERVICE_TRENCH_PIPE,
                ModBlocks.SERVICE_TRENCH_GLASS_PIPE, ModBlocks.SERVICE_TRENCH_SHAFT, ModBlocks.SERVICE_TRENCH_CABLE)) {
            for (BlockState state : trench.get().getStateDefinition().getPossibleStates()) {
                models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                        (key, original) -> trenchModels.computeIfAbsent(original, model -> new ServiceTrenchCasingModel(model, TRENCH_CT)));
            }
        }

        var cableTrenchModels = new java.util.IdentityHashMap<BakedModel, BakedModel>();
        for (BlockState state : ModBlocks.SERVICE_TRENCH_CABLE.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, original) -> cableTrenchModels.computeIfAbsent(original, IndividualCableConnectionModel::new));
        }

        var glassTrenchModels = new java.util.IdentityHashMap<BakedModel, BakedModel>();
        for (BlockState state : ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, original) -> glassTrenchModels.computeIfAbsent(original,
                            com.simibubi.create.content.fluids.PipeAttachmentModel::withAO));
        }

        var copperTrenchModels = new java.util.IdentityHashMap<BakedModel, BakedModel>();
        for (BlockState state : ModBlocks.SERVICE_TRENCH_PIPE.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, original) -> copperTrenchModels.computeIfAbsent(original, ServiceTrenchPipeModel::new));
        }

        var platedState = ModBlocks.BLOCK_OF_PLATED_INDUSTRIAL_STEEL.get().defaultBlockState();
        models.computeIfPresent(BlockModelShaper.stateToModelLocation(platedState),
                (key, original) -> new CTModel(
                        original, PLATED_STEEL_CT));

        for (BlockState state : ModBlocks.AIR_DUCT.get().getStateDefinition().getPossibleStates()) {
            if (!state.getValue(com.aislen.createindustrialdetails.content.block.airduct.AirDuctBlock.CASING).isConnectedTexture()) continue;
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, original) -> new AirDuctCasingModel(original, AIR_DUCT_CT));
        }

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
                ModBlocks.INDUSTRIAL_STEEL_BEAM
                        .get()
                        .getStateDefinition()
                        .getPossibleStates()) {

            Direction.Axis axis =
                    state.getValue(GirderBlock.AXIS);

            boolean verticalRotated =
                    state.getValue(
                            IndustrialSteelBeamBlock.VERTICAL_ROTATED
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
                            new IndustrialSteelBeamModel(
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
