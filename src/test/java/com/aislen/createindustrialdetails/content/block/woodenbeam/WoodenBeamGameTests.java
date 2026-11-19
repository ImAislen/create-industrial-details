package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.woodenbeam.structure.WoodenBeamLineGeometry;
import com.aislen.createindustrialdetails.content.block.woodenpost.WoodenPostArrangement;
import com.aislen.createindustrialdetails.content.block.woodenpost.WoodenPostBlock;
import com.aislen.createindustrialdetails.content.block.woodenpost.WoodenPostPosition;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WoodenBeamGameTests {
    @GameTest(template = "empty")
    public static void steepBeamItemPlacementUsesClippedWorldCollision(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos supportA = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos supportB = helper.absolutePos(new BlockPos(4, 9, 1));
        BlockPos from = supportA.east();
        BlockPos to = supportB.west();
        level.setBlockAndUpdate(supportA, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(supportB, Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        ItemStack beamItem = new ItemStack(ModBlocks.OAK_WOODEN_BEAM.get());
        click(helper, player, beamItem, supportA, Direction.EAST, new Vec3(1, 0.75, 0.75));
        click(helper, player, beamItem, supportB, Direction.WEST, new Vec3(0, 0.75, 0.25));

        var anchor = (WoodenBeamBlockEntity) level.getBlockEntity(from);
        if (anchor == null || !anchor.hasConnectionTo(to)) {
            helper.fail("Both item clicks must place and connect the anchors");
            return;
        }
        var endpoints = anchor.getEndpoints(anchor.getConnectionsCopy().iterator().next());
        helper.assertTrue(endpoints.localSnap() == WoodenBeamSnapPoint.fromGrid(-1, 1)
                && endpoints.peerSnap() == WoodenBeamSnapPoint.fromGrid(-1, 1), "Both wall snaps are retained");
        var beam = new WoodenBeamGeometry(level, from, to, endpoints);
        var structureBlocks = new ArrayList<BlockPos>();
        for (var entry : WoodenBeamLineGeometry.voxelize(beam, anchor.getModelType().voxelShapeResolutionPixels())
                .entrySet()) {
            BlockPos pos = entry.getKey();
            var state = level.getBlockState(pos);
            helper.assertTrue(!state.isAir(), "Every voxel in the open span has a structure block at " + pos);
            if (state.is(ModBlocks.WOODEN_BEAM_STRUCTURE.get())) {
                structureBlocks.add(pos);
                var actual = state.getCollisionShape(level, pos);
                helper.assertTrue(!actual.isEmpty() && actual.bounds().equals(entry.getValue().bounds()),
                        "The structure block supplies the cached clipped shape");
            }
        }
        helper.assertTrue(!structureBlocks.isEmpty(), "The steep span places collision between its anchors");
        level.removeBlock(from, false);
        helper.assertTrue(structureBlocks.stream().noneMatch(pos -> level.getBlockState(pos).is(ModBlocks.WOODEN_BEAM_STRUCTURE.get())),
                "Removing the beam clears its structure");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steepBambooBeamStopsAtStoneBrickWallSnap(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos wallTop = floor.offset(-2, 8, 0);
        level.setBlockAndUpdate(floor, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int depth = 0; depth < 8; depth++) {
            level.setBlockAndUpdate(wallTop.below(depth), Blocks.STONE_BRICKS.defaultBlockState());
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        ItemStack bamboo = new ItemStack(ModBlocks.WOODEN_BEAMS.get("bamboo_wooden_beam").get());
        click(helper, player, bamboo, floor, Direction.UP, new Vec3(0.5, 1, 0.5));
        click(helper, player, bamboo, wallTop, Direction.EAST, new Vec3(1, 0.75, 0.5));

        BlockPos from = floor.above();
        BlockPos to = wallTop.east();
        if (!(level.getBlockEntity(from) instanceof WoodenBeamBlockEntity anchor) || !anchor.hasConnectionTo(to)) {
            helper.fail("The bamboo item must place the steep wall connection");
            return;
        }
        var endpoints = anchor.getEndpoints(anchor.getConnectionsCopy().iterator().next());
        helper.assertTrue(endpoints.peerSnap() == WoodenBeamSnapPoint.TOP_CENTER, "Keep the wall's upper snap");
        var beam = new WoodenBeamGeometry(level, from, to, endpoints);
        var peak = beam.faces().stream().flatMap(face -> face.vertices().stream()).mapToDouble(point -> point.y).max();
        helper.assertTrue(peak.isPresent() && Math.abs(peak.getAsDouble() - wallTop.getY() - 1.0) < 1.0e-8,
                "The bounded bevel reaches the wall top without passing it");
        level.removeBlock(from, false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steepPostBeamMeetsPostSurface(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(support, ModBlocks.OAK_WOODEN_POST.get().defaultBlockState());
        BlockPos from = support.east();
        var endpoints = new WoodenBeamConnectionEndpoints(Direction.EAST, WoodenBeamSnapPoint.fromGrid(0, 1),
                Direction.WEST, WoodenBeamSnapPoint.CENTER);
        var beam = new WoodenBeamGeometry(level, from, from.offset(1, 8, 0), endpoints);
        helper.assertTrue(beam.fromPlane().normal().equals(new Vec3(0, 1, 0))
                        && Math.abs(beam.fromPlane().point().y - support.getY() - 1) < 1.0e-9,
                "A steep beam aimed into a post cuts flush to the post top it meets");
        for (WoodenPostPosition position : WoodenPostPosition.values()) {
            level.setBlockAndUpdate(support, ModBlocks.OAK_WOODEN_POST.get().defaultBlockState()
                    .setValue(WoodenPostBlock.POST_POSITION, WoodenPostArrangement.singleton(position)));
            var shallow = new WoodenBeamGeometry(level, from, from.east(4), endpoints);
            helper.assertTrue(shallow.fromPlane().normal().equals(new Vec3(1, 0, 0))
                            && Math.abs(shallow.fromPlane().point().x - support.getX() - position.bounds().maxX) < 1.0e-9,
                    "A shallow beam cuts flush to the side of the post at " + position);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allWoodenBeamMaterialsUseTheirEndTextures(GameTestHelper helper) throws IOException {
        for (var variant : WoodenBeamMaterial.allVariants()) {
            var modelType = ModBlocks.WOODEN_BEAMS.get(variant.registryName()).get().getModelType();
            String path = "/assets/create_industrial_details/models/" + modelType.segmentModelLocation().getPath() + ".json";
            try (var input = WoodenBeamGameTests.class.getResourceAsStream(path)) {
                if (input == null) {
                    helper.fail("Missing beam model " + path);
                    return;
                }
                var model = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                var textures = model.getAsJsonObject("textures");
                helper.assertTrue(model.get("parent").getAsString().endsWith("/wooden_beam"), "Shared timber profile");
                helper.assertTrue(textures.get("side").getAsString().equals(variant.sideTexture().toString()), "Wood sides");
                helper.assertTrue(textures.get("end").getAsString().equals(modelType.capTexture().toString()), "Wood end grain");
            }
        }
        helper.succeed();
    }

    private static void click(GameTestHelper helper, Player player, ItemStack stack, BlockPos pos, Direction face, Vec3 offset) {
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(offset), face, pos, false);
        var context = new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit);
        helper.assertTrue(stack.getItem().useOn(context).consumesAction(), "Beam endpoint use succeeds on " + face);
    }
}
