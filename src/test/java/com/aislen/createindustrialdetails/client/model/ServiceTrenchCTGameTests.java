package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.FACING;
import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.OPEN;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServiceTrenchCTGameTests {
    @GameTest(template = "empty")
    public static void interiorConnectionsBehindExteriorBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var behaviour = new ServiceTrenchCTBehaviour();
        var type = AllCTTypes.HORIZONTAL;
        var solid = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var empty = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, facing);
            level.setBlockAndUpdate(pos.relative(facing), empty);
            level.setBlockAndUpdate(pos.relative(facing.getOpposite()), empty);
            Direction left = facing.getCounterClockWise();
            Direction right = facing.getClockWise();
            for (var trench : new Block[]{empty.getBlock(), ModBlocks.SERVICE_TRENCH_PIPE.get(),
                    ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get(), ModBlocks.SERVICE_TRENCH_SHAFT.get()}) {
                for (boolean open : new boolean[]{false, true}) {
                    level.setBlockAndUpdate(pos, trench.defaultBlockState().setValue(FACING, facing).setValue(OPEN, open));
                    for (int sides : new int[]{0, 1, 2, 3, 0}) {
                        level.setBlockAndUpdate(pos.relative(left), (sides & 1) != 0 ? solid : air);
                        level.setBlockAndUpdate(pos.relative(right), (sides & 2) != 0 ? solid : air);
                        var state = level.getBlockState(pos);
                        for (Direction wall : new Direction[]{left, right}) {
                            helper.assertTrue(behaviour.buildContextForOccludedDirections()
                                            || Block.shouldRenderFace(state, level, pos, wall, pos.relative(wall)),
                                    "Create must build CT context even when the exterior face is occluded");
                            var context = behaviour.buildContext(level, pos, state, wall, type.getContextRequirement());
                            helper.assertTrue(type.getTextureIndex(context) == 3,
                                    "Both recessed walls keep the tile for two connected ends");
                        }
                    }
                }
            }
            level.removeBlock(pos.relative(facing), false);
            level.removeBlock(pos.relative(facing.getOpposite()), false);
        }
        helper.succeed();
    }
}
