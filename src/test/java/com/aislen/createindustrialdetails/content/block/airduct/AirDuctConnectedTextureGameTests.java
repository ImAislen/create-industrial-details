package com.aislen.createindustrialdetails.content.block.airduct;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.client.model.AirDuctCasingCTBehaviour;
import com.aislen.createindustrialdetails.client.model.PlatedIndustrialSteelCTBehaviour;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.decoration.encasing.CasingConnectivity;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class AirDuctConnectedTextureGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void materialsAndFaces(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var behavior = new AirDuctCasingCTBehaviour(new CasingConnectivity());
        var type = AllCTTypes.OMNIDIRECTIONAL;
        for (AirDuctCasing casing : AirDuctCasing.values()) {
            for (Direction face : Direction.values()) {
                var ductAxis = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
                var state = ModBlocks.AIR_DUCT.get().defaultBlockState().setValue(AirDuctBlock.AXIS, ductAxis)
                        .setValue(AirDuctBlock.CASING, casing);
                level.setBlockAndUpdate(pos, state);
                var vertical = face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
                var neighbor = pos.relative(vertical);
                helper.assertTrue((behavior.getShift(state, face, null) != null) == casing.isConnectedTexture(), "Per-material CT enabled only where intended");
                if (!casing.isConnectedTexture()) {
                    helper.assertTrue(behavior.getDataType(level, pos, state, face) == null, "Uncased and standard steel remain non-connected");
                    continue;
                }
                var appearance = state.getAppearance(level, pos, face, casing.materialState(), neighbor);
                helper.assertTrue(appearance == casing.materialState(), "Native full blocks see matching material face");
                helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) == 0, "Isolated face selects original tile");
                level.setBlockAndUpdate(neighbor, state);
                helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) != 0, "Identically encased ducts connect on every exterior face");
                for (AirDuctCasing other : AirDuctCasing.values()) {
                    if (other == casing) continue;
                    level.setBlockAndUpdate(neighbor, state.setValue(AirDuctBlock.CASING, other));
                    helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) == 0, "Different materials never connect, including material swaps");
                }
                level.setBlockAndUpdate(neighbor, casing.materialState());
                helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) != 0, "Duct connects to corresponding full block");
                var full = casing.materialState();
                helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, neighbor, full, face, type.getContextRequirement())) != 0, "Full material connects back to duct");
                if (casing == AirDuctCasing.PLATED_INDUSTRIAL_STEEL) {
                    var plated = new PlatedIndustrialSteelCTBehaviour();
                    helper.assertTrue(type.getTextureIndex(plated.buildContext(level, neighbor, full, face, type.getContextRequirement())) != 0,
                            "Existing plated-steel behavior connects reciprocally without replacement");
                }
                level.setBlockAndUpdate(neighbor, state.setValue(AirDuctBlock.PROPERTY_BY_DIRECTION.get(face), true));
                helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) == 0, "Neighbor opening does not impersonate a solid casing face");
                var open = level.getBlockState(neighbor);
                helper.assertTrue(behavior.getShift(open, face, null) == null
                        && open.getAppearance(level, neighbor, face, full, pos) == open, "Opening face retains its original geometry and material appearance");
                level.setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
                helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) == 0, "Removing neighbor restores border");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void industrialIronStaysUnconnected(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var behavior = new AirDuctCasingCTBehaviour(new CasingConnectivity());
        for (Direction face : Direction.values()) {
            var axis = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            var state = ModBlocks.AIR_DUCT.get().defaultBlockState().setValue(AirDuctBlock.AXIS, axis)
                    .setValue(AirDuctBlock.CASING, AirDuctCasing.INDUSTRIAL_IRON);
            var tangent = face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
            var neighbor = pos.relative(tangent);
            level.setBlockAndUpdate(pos, state);
            helper.assertTrue(behavior.getShift(state, face, null) == null
                    && behavior.getDataType(level, pos, state, face) == null, "Industrial Iron has no CT sprite or context");
            helper.assertTrue(state.getAppearance(level, pos, face, state, neighbor) == state,
                    "Industrial Iron does not advertise a connected material face");
            for (var adjacent : new net.minecraft.world.level.block.state.BlockState[]{state, AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState()}) {
                level.setBlockAndUpdate(neighbor, adjacent);
                helper.assertTrue(!behavior.connectsTo(state, level.getBlockState(neighbor), level, pos, neighbor, face),
                        "Industrial Iron cannot connect textures to encased ducts or full blocks");
                helper.assertTrue(behavior.getShift(AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), face, null) == null,
                        "Ordinary Industrial Iron has no custom CT shift");
            }
            level.setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sheetsAndChunkEdges(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var behavior = new AirDuctCasingCTBehaviour(new CasingConnectivity());
        var type = AllCTTypes.OMNIDIRECTIONAL;
        for (AirDuctCasing casing : AirDuctCasing.values()) {
            if (!casing.isConnectedTexture()) continue;
            for (Direction face : Direction.values()) {
                var vertical = face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
                var horizontal = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.WEST;
                var axis = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
                var state = ModBlocks.AIR_DUCT.get().defaultBlockState().setValue(AirDuctBlock.CASING, casing)
                        .setValue(AirDuctBlock.AXIS, axis);
                level.setBlockAndUpdate(pos, state);
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                    if (x != 0 || y != 0) level.setBlockAndUpdate(pos.relative(horizontal, x).relative(vertical, y), casing.materialState());
                }
                var full = behavior.buildContext(level, pos, state, face, type.getContextRequirement());
                helper.assertTrue(full.up && full.down && full.left && full.right && full.topLeft && full.topRight
                        && full.bottomLeft && full.bottomRight, "Complete mixed-material sheet joins all edges and corners");
                level.setBlockAndUpdate(pos.relative(horizontal).relative(vertical), Blocks.AIR.defaultBlockState());
                helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement()))
                        != type.getTextureIndex(full), "Missing diagonal retains concave border");
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                    if (x != 0 || y != 0) level.setBlockAndUpdate(pos.relative(horizontal, x).relative(vertical, y), Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            var boundary = new BlockPos((pos.getX() & ~15) + 15, pos.getY() + 10, pos.getZ());
            var state = ModBlocks.AIR_DUCT.get().defaultBlockState().setValue(AirDuctBlock.AXIS, Direction.Axis.Y)
                    .setValue(AirDuctBlock.CASING, casing);
            level.setBlockAndUpdate(boundary, state);
            level.setBlockAndUpdate(boundary.east(), state);
            helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, boundary, state, Direction.SOUTH,
                    type.getContextRequirement())) != 0, "Encased duct CT crosses chunk boundary");
            level.setBlockAndUpdate(boundary, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(boundary.east(), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void compatibleCreateCasings(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var neighbor = pos.east();
        var registry = new CasingConnectivity();
        registry.makeCasing(AllBlocks.ANDESITE_CASING.get(), AllSpriteShifts.ANDESITE_CASING);
        registry.make(AllBlocks.ANDESITE_ENCASED_SHAFT.get(), AllSpriteShifts.ANDESITE_CASING,
                (state, side) -> side.getAxis() != state.getValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS));
        var behavior = new AirDuctCasingCTBehaviour(registry);
        var state = ModBlocks.AIR_DUCT.get().defaultBlockState().setValue(AirDuctBlock.AXIS, Direction.Axis.X)
                .setValue(AirDuctBlock.CASING, AirDuctCasing.ANDESITE);
        level.setBlockAndUpdate(pos, state);
        var shaft = AllBlocks.ANDESITE_ENCASED_SHAFT.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        level.setBlockAndUpdate(neighbor, shaft);
        helper.assertTrue(behavior.connectsTo(state, shaft, level, pos, neighbor, Direction.NORTH), "Create casing connectivity exposes compatible shaft casing");
        level.setBlockAndUpdate(neighbor, shaft.setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
        helper.assertTrue(!behavior.connectsTo(state, level.getBlockState(neighbor), level, pos, neighbor, Direction.NORTH), "Create side predicate rejects exposed utility face");
        for (Direction face : Direction.values()) {
            var axis = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            var train = state.setValue(AirDuctBlock.AXIS, axis).setValue(AirDuctBlock.CASING, AirDuctCasing.RAILWAY);
            helper.assertTrue(behavior.getShift(train, face, null) == (face.getAxis().isVertical()
                    ? AllSpriteShifts.RAILWAY_CASING : AllSpriteShifts.RAILWAY_CASING_SIDE), "Railway top and side sprite shifts preserved");
        }
        helper.succeed();
    }
}
