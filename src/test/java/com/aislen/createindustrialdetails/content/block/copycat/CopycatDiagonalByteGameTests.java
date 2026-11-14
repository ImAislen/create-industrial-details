package com.aislen.createindustrialdetails.content.block.copycat;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class CopycatDiagonalByteGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void materialAndWrenchInteractions(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.COPYCAT_DIAGONAL_BYTE.get();
        var state = block.defaultBlockState();
        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            Player player = helper.makeMockPlayer(mode);
            level.setBlockAndUpdate(pos, state);
            var entity = (CopycatBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(entity != null && entity.getType() == ModBlockEntities.COPYCAT_DIAGONAL_BYTE.get()
                    && entity.getType().isValid(state), "Byte has its own valid native copycat entity type");
            helper.assertTrue(block.getTicker(level, state, entity.getType()) == null, "No block entity ticking");
            ItemStack oak = new ItemStack(Items.OAK_PLANKS, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, oak);
            helper.assertTrue(state.useItemOn(oak, level, player, InteractionHand.MAIN_HAND, hit(pos)).consumesAction(), "Native material application succeeds");
            helper.assertTrue(entity.getMaterial().is(Blocks.OAK_PLANKS) && entity.getConsumedItem().getCount() == 1,
                    "Material state and exactly one consumed item stored");
            helper.assertTrue(oak.getCount() == (player.isCreative() ? 3 : 2), "Survival consumes exactly one; creative consumes none");
            ItemStack stone = new ItemStack(Items.STONE, 3);
            state.useItemOn(stone, level, player, InteractionHand.MAIN_HAND, hit(pos));
            helper.assertTrue(entity.getMaterial().is(Blocks.OAK_PLANKS) && stone.getCount() == 3, "Applied material cannot be replaced");
            helper.assertTrue(block.getAcceptedBlockState(level, pos, new ItemStack(block), Direction.UP) == null
                    && block.getAcceptedBlockState(level, pos, new ItemStack(Items.CHEST), Direction.UP) == null,
                    "Native rejection of copycats and block entities");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            block.onWrenched(state, context(player, pos));
            helper.assertTrue(!entity.hasCustomMaterial() && entity.getConsumedItem().isEmpty(), "Normal wrench clears material only");
            int expected = player.isCreative() ? 0 : 1;
            helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == expected, "Material returned once in survival only");
            block.onWrenched(state, context(player, pos));
            helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == expected, "Repeated wrench cannot duplicate material");
            state.useItemOn(stone, level, player, InteractionHand.MAIN_HAND, hit(pos));
            block.onSneakWrenched(state, context(player, pos));
            helper.assertTrue(level.getBlockState(pos).isAir(), "Sneak wrench removes byte");
            helper.assertTrue(player.getInventory().countItem(Items.STONE) == expected
                    && player.getInventory().countItem(block.asItem()) == expected, "Sneak wrench returns byte and material exactly once in survival only");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placementWaterloggingAndShapes(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.COPYCAT_DIAGONAL_BYTE.get();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS, 2));
        ItemStack byteItem = new ItemStack(block, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, byteItem);
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        BlockPlaceContext placement = new BlockPlaceContext(new UseOnContext(level, player, InteractionHand.MAIN_HAND, byteItem, hit(pos)));
        helper.assertTrue(((BlockItem) block.asItem()).place(placement).consumesAction(), "Normal block item placement succeeds");
        var state = level.getBlockState(pos);
        helper.assertTrue(state.is(block) && state.getValue(CopycatDiagonalByteBlock.WATERLOGGED)
                && state.getFluidState().isSource(), "Placement preserves source water via native waterlogging");
        helper.assertTrue(((CopycatBlockEntity) level.getBlockEntity(pos)).getMaterial().is(Blocks.OAK_PLANKS)
                && player.getOffhandItem().getCount() == 1 && byteItem.getCount() == 1, "Native offhand application consumes one material and byte");
        helper.assertTrue(block.getStateDefinition().getProperties().size() == 1, "Waterlogging is the only state property");
        var shape = state.getCollisionShape(level, pos);
        helper.assertTrue(shape == state.getCollisionShape(level, pos), "Collision is cached");
        var expected = new AABB(.25, 0, .25, .75, .5, .75);
        helper.assertTrue(shape.toAabbs().size() == 1 && shape.bounds().equals(expected),
                "Collision is exactly one axis-aligned 8x8x8 cube at (4,0,4)");
        helper.assertTrue(state.getShape(level, pos).toAabbs().equals(shape.toAabbs()),
                "Selection and collision use the same cube");
        helper.assertTrue(!state.canOcclude() && !state.isCollisionShapeFullBlock(level, pos), "Byte does not occlude a full block");
        for (Direction side : Direction.values()) {
            helper.assertTrue(block.shouldFaceAlwaysRender(state, side) && !block.canFaceBeOccluded(state, side), "Diagonal material faces cannot be culled as full cardinal faces");
            helper.assertTrue(!block.canConnectTexturesToward(level, pos, pos.relative(side), state), "Separated byte surfaces never claim full-block texture continuity");
        }
        level.removeBlock(pos.below(), false);
        helper.assertTrue(state.canSurvive(level, pos), "No supporting block required");
        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(BlockTags.MINEABLE_WITH_AXE), "Native copycat mining tags present");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void materialPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var state = ModBlocks.COPYCAT_DIAGONAL_BYTE.get().defaultBlockState().setValue(CopycatDiagonalByteBlock.WATERLOGGED, true);
        level.setBlockAndUpdate(pos, state);
        var entity = (CopycatBlockEntity) level.getBlockEntity(pos);
        entity.setMaterial(Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
        entity.setConsumedItem(new ItemStack(Items.OAK_LOG));
        var saved = entity.saveWithFullMetadata(level.registryAccess());
        var restoredState = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(state));
        var loaded = BlockEntity.loadStatic(pos, restoredState, saved, level.registryAccess());
        helper.assertTrue(loaded instanceof CopycatBlockEntity copy && copy.getType() == ModBlockEntities.COPYCAT_DIAGONAL_BYTE.get()
                && copy.getMaterial() == entity.getMaterial() && copy.getConsumedItem().is(Items.OAK_LOG)
                && copy.getConsumedItem().getCount() == 1 && loaded.getBlockState() == state,
                "Block state, native material orientation, entity ID and consumed item survive NBT reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingDrops(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.COPYCAT_DIAGONAL_BYTE.get();
        var state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var entity = (CopycatBlockEntity) level.getBlockEntity(pos);
        entity.setMaterial(Blocks.STONE.defaultBlockState());
        entity.setConsumedItem(new ItemStack(Items.STONE));
        var loot = Block.getDrops(state, level, pos, entity);
        helper.assertTrue(loot.size() == 1 && loot.getFirst().is(block.asItem()) && loot.getFirst().getCount() == 1,
                "Loot returns byte only; native entity removal owns material refund");
        level.destroyBlock(pos, true);
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        helper.assertTrue(drops.stream().filter(drop -> drop.getItem().is(Items.STONE)).mapToInt(drop -> drop.getItem().getCount()).sum() == 1
                && drops.stream().filter(drop -> drop.getItem().is(block.asItem())).mapToInt(drop -> drop.getItem().getCount()).sum() == 1,
                "Survival destruction produces one byte and one material without duplication");
        drops.forEach(ItemEntity::discard);
        level.setBlockAndUpdate(pos, state);
        entity = (CopycatBlockEntity) level.getBlockEntity(pos);
        entity.setMaterial(Blocks.STONE.defaultBlockState());
        entity.setConsumedItem(new ItemStack(Items.STONE));
        block.playerWillDestroy(level, pos, state, helper.makeMockPlayer(GameType.CREATIVE));
        level.destroyBlock(pos, false);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Creative destruction drops neither byte nor material");
        helper.succeed();
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static UseOnContext context(Player player, BlockPos pos) {
        return new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack(), hit(pos));
    }
}
