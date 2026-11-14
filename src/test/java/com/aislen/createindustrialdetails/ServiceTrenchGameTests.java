package com.aislen.createindustrialdetails;

import com.aislen.createindustrialdetails.registry.*;
import com.aislen.createindustrialdetails.content.block.rivetedsteel.trench.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasableBlock;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class ServiceTrenchGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void casingAndDrops(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var state = ModBlocks.SERVICE_TRENCH.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        helper.assertTrue(state.getCollisionShape(level, pos).equals(net.minecraft.world.phys.shapes.Shapes.block()), "Full collision");
        for (int i = 0; i < 2; i++) {
            ((IWrenchable) state.getBlock()).onWrenched(level.getBlockState(pos), context(player, pos));
        }
        helper.assertTrue(level.getBlockState(pos) == state, "Two casing rotations return to original state");
        var drops = Block.getDrops(state, level, pos, null);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.SERVICE_TRENCH.get().asItem()), "Empty casing drop");
        ((IWrenchable) state.getBlock()).onSneakWrenched(state, context(player, pos));
        helper.assertTrue(level.getBlockState(pos).isAir(), "Empty casing wrench removal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void pipeConversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var state = AllBlocks.FLUID_PIPE.get().getAxisState(Direction.Axis.Z)
                .setValue(BlockStateProperties.WATERLOGGED, true);
        level.setBlockAndUpdate(pos, state);
        var transport = ((com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity)level.getBlockEntity(pos)).getBehaviour(FluidTransportBehaviour.TYPE);
        transport.getFlow(Direction.NORTH);
        var originalInterfaces = transport.interfaces;
        var connection = transport.interfaces.get(Direction.NORTH);
        var flowData = new net.minecraft.nbt.CompoundTag();
        flowData.put("Fluid", new FluidStack(Fluids.WATER, 1).saveOptional(level.registryAccess()));
        flowData.putBoolean("In", true);
        var connectionData = new net.minecraft.nbt.CompoundTag();
        connectionData.put("Flow", flowData);
        var data = new net.minecraft.nbt.CompoundTag();
        data.put("north", connectionData);
        connection.deserializeNBT(data, level.registryAccess(), pos, true);
        var stack = new ItemStack(ModBlocks.SERVICE_TRENCH.get(), 2);
        ((EncasableBlock) state.getBlock()).tryEncase(state, level, pos, stack, player, InteractionHand.MAIN_HAND, hit(pos));
        var encased = level.getBlockState(pos);
        helper.assertTrue(encased.is(ModBlocks.SERVICE_TRENCH_PIPE.get()), "Pipe encasing registry");
        helper.assertTrue(stack.getCount() == 1, "Exactly one casing consumed");
        for (Direction direction : Direction.values())
            helper.assertTrue(encased.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction)) == state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction)), "Pipe connections preserved");
        var be = (ServiceTrenchPipeBlockEntity)level.getBlockEntity(pos);
        helper.assertTrue(be.getBehaviour(FluidTransportBehaviour.TYPE).interfaces == originalInterfaces, "Live flow objects preserved");
        var inspectedFlow = be.getBehaviour(FluidTransportBehaviour.TYPE).getFlow(Direction.NORTH);
        helper.assertTrue(inspectedFlow != null && inspectedFlow.fluid.getFluid() == Fluids.WATER,
                "Known flow fluid available for inspection");
        var saved = be.saveWithFullMetadata(level.registryAccess());
        var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, encased, saved, level.registryAccess());
        helper.assertTrue(loaded instanceof ServiceTrenchPipeBlockEntity, "Pipe reload type");
        var drops = Block.getDrops(encased, level, pos, be);
        helper.assertTrue(drops.size() == 2, "Pipe and casing both drop");
        ((IWrenchable)encased.getBlock()).onWrenched(encased, context(player, pos));
        helper.assertTrue(be == level.getBlockEntity(pos), "Casing rotation preserves pipe entity");
        ModBlocks.SERVICE_TRENCH_PIPE.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
        var restored = level.getBlockState(pos);
        helper.assertTrue(AllBlocks.FLUID_PIPE.has(restored) && restored.getValue(BlockStateProperties.WATERLOGGED), "Waterlogged pipe restored");
        var restoredBe = (com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity)level.getBlockEntity(pos);
        helper.assertTrue(restoredBe.getBehaviour(FluidTransportBehaviour.TYPE).interfaces == originalInterfaces, "Decasing retains live flow objects");
        helper.assertTrue(player.getInventory().countItem(ModBlocks.SERVICE_TRENCH.get().asItem()) == 1, "Exactly one casing refunded");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void shaftConversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (Direction.Axis axis : Direction.Axis.values()) {
            var state = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, axis)
                    .setValue(BlockStateProperties.WATERLOGGED, true);
            level.setBlockAndUpdate(pos, state);
            var stack = new ItemStack(ModBlocks.SERVICE_TRENCH.get(), 2);
            ((EncasableBlock)state.getBlock()).tryEncase(state, level, pos, stack, player, InteractionHand.MAIN_HAND, hit(pos));
            var encased = level.getBlockState(pos);
            helper.assertTrue(encased.is(ModBlocks.SERVICE_TRENCH_SHAFT.get()) && stack.getCount() == 1, "Shaft encasing");
            var be = (KineticBlockEntity)level.getBlockEntity(pos);
            helper.assertTrue(be instanceof ServiceTrenchShaftBlockEntity, "Correct kinetic entity");
            helper.assertTrue(!be.addToGoggleTooltip(new ArrayList<>(), false), "No shaft measurements");
            ModBlocks.SERVICE_TRENCH_SHAFT.get().onWrenched(encased, context(player, pos));
            helper.assertTrue(level.getBlockState(pos).getValue(RotatedPillarKineticBlock.AXIS) == axis, "Casing rotation preserves shaft axis");
            helper.assertTrue(level.getBlockEntity(pos) == be, "Casing rotation preserves kinetic entity");
            helper.assertTrue(Block.getDrops(encased, level, pos, be).size() == 2, "Shaft and casing both drop");
            var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, encased, be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            helper.assertTrue(loaded instanceof ServiceTrenchShaftBlockEntity, "Shaft reload type");
            ModBlocks.SERVICE_TRENCH_SHAFT.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
            var restored = level.getBlockState(pos);
            helper.assertTrue(AllBlocks.SHAFT.has(restored) && restored.getValue(RotatedPillarKineticBlock.AXIS) == axis
                    && restored.getValue(BlockStateProperties.WATERLOGGED), "Original waterlogged shaft restored");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void poweredShaft(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var end = pos.east();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        level.setBlockAndUpdate(pos.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
        var shaft = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
        level.setBlockAndUpdate(pos, shaft);
        level.setBlockAndUpdate(end, shaft);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(end)).getSpeed() != 0, "Initial shaft transmits power");
        }).thenExecute(() -> {
            ((EncasableBlock)shaft.getBlock()).tryEncase(shaft, level, pos, new ItemStack(ModBlocks.SERVICE_TRENCH.get()),
                    player, InteractionHand.MAIN_HAND, hit(pos));
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(pos)).getSpeed() == 16, "Encased shaft powered");
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(end)).getSpeed() == 16, "Downstream power retained");
            ModBlocks.SERVICE_TRENCH_SHAFT.get().onWrenched(level.getBlockState(pos), context(player, pos));
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(end)).getSpeed() == 16, "Casing rotation retains power");
            ModBlocks.SERVICE_TRENCH_SHAFT.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(end)).getSpeed() == 16, "Decased shaft retains power");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void pumpingThroughTrench(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(0, 2, 2));
        var target = source.east(4);
        var pos = source.east(3);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        level.setBlockAndUpdate(source, AllBlocks.FLUID_TANK.getDefaultState());
        level.setBlockAndUpdate(target, AllBlocks.FLUID_TANK.getDefaultState());
        var pipe = AllBlocks.FLUID_PIPE.get().getAxisState(Direction.Axis.X);
        level.setBlockAndUpdate(source.east(), pipe);
        level.setBlockAndUpdate(pos, pipe);
        level.setBlockAndUpdate(source.east(2), AllBlocks.MECHANICAL_PUMP.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        level.setBlockAndUpdate(source.east(2).above(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        level.setBlockAndUpdate(source.east().above(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        var input = (com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(source);
        var output = (com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(target);
        input.getTankInventory().fill(new FluidStack(Fluids.WATER, 8000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        int[] before = {0};
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > 0, "Ordinary pipe transfers fluid");
        }).thenExecute(() -> {
            before[0] = output.getTankInventory().getFluidAmount();
            ((EncasableBlock)pipe.getBlock()).tryEncase(level.getBlockState(pos), level, pos,
                    new ItemStack(ModBlocks.SERVICE_TRENCH.get()), player, InteractionHand.MAIN_HAND, hit(pos));
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Trench continues transferring fluid");
            before[0] = output.getTankInventory().getFluidAmount();
            ModBlocks.SERVICE_TRENCH_PIPE.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Decased pipe continues transferring fluid");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void creativeDecasing(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        var shaft = AllBlocks.SHAFT.getDefaultState();
        level.setBlockAndUpdate(pos, shaft);
        var stack = new ItemStack(ModBlocks.SERVICE_TRENCH.get());
        ((EncasableBlock)shaft.getBlock()).tryEncase(shaft, level, pos, stack, player, InteractionHand.MAIN_HAND, hit(pos));
        helper.assertTrue(stack.getCount() == 1, "Creative encasing does not consume item");
        ModBlocks.SERVICE_TRENCH_SHAFT.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
        helper.assertTrue(AllBlocks.SHAFT.has(level.getBlockState(pos)), "Creative decasing restores shaft");
        helper.assertTrue(player.getInventory().countItem(ModBlocks.SERVICE_TRENCH.get().asItem()) == 0, "Creative decasing does not duplicate casing");
        helper.assertTrue(ModBlocks.SERVICE_TRENCH_PIPE.get().asItem() == net.minecraft.world.item.Items.AIR
                && ModBlocks.SERVICE_TRENCH_SHAFT.get().asItem() == net.minecraft.world.item.Items.AIR, "No hidden variant items");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glassPipeConversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (var gameType : new net.minecraft.world.level.GameType[]{net.minecraft.world.level.GameType.SURVIVAL,
                net.minecraft.world.level.GameType.CREATIVE}) {
            var player = helper.makeMockPlayer(gameType);
            for (Direction.Axis axis : Direction.Axis.values()) {
                var glass = AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
                        .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, axis)
                        .setValue(com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock.ALT, true)
                        .setValue(BlockStateProperties.WATERLOGGED, true);
                level.setBlockAndUpdate(pos, glass);
                var original = (com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity)level.getBlockEntity(pos);
                var direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
                original.getBehaviour(FluidTransportBehaviour.TYPE).getFlow(direction);
                var interfaces = original.getBehaviour(FluidTransportBehaviour.TYPE).interfaces;
                var stack = new ItemStack(ModBlocks.SERVICE_TRENCH.get(), 2);
                var use = new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit(pos));
                helper.assertTrue(stack.getItem().useOn(use).consumesAction(), "Glass pipe item interaction");
                var state = level.getBlockState(pos);
                helper.assertTrue(state.is(ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get()), "Glass remains a distinct encased variant");
                helper.assertTrue(stack.getCount() == (player.isCreative() ? 2 : 1), "Correct casing consumption");
                var be = (ServiceTrenchGlassPipeBlockEntity)level.getBlockEntity(pos);
                var transport = be.getBehaviour(FluidTransportBehaviour.TYPE);
                helper.assertTrue(transport.interfaces == interfaces, "Glass flows retained");
                for (Direction side : Direction.values())
                    helper.assertTrue(transport.canHaveFlowToward(state, side) == (side.getAxis() == axis), "Straight-only transport");
                var saved = be.saveWithFullMetadata(level.registryAccess());
                var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
                helper.assertTrue(loaded instanceof ServiceTrenchGlassPipeBlockEntity, "Glass block entity reload");
                helper.assertTrue(loaded.getBlockState().getValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS) == axis,
                        "Reloaded glass orientation");
                var loadedTransport = ((ServiceTrenchGlassPipeBlockEntity)loaded).getBehaviour(FluidTransportBehaviour.TYPE);
                for (Direction side : Direction.values())
                    helper.assertTrue(loadedTransport.canHaveFlowToward(state, side) == (side.getAxis() == axis), "Reloaded straight-only behavior");
                var drops = Block.getDrops(state, level, pos, be);
                helper.assertTrue(drops.size() == 2 && drops.stream().mapToInt(ItemStack::getCount).sum() == 2,
                        "Exactly one pipe and casing drop");
                ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().onWrenched(state, context(player, pos));
                helper.assertTrue(level.getBlockEntity(pos) == be
                        && level.getBlockState(pos).getValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS) == axis,
                        "Wrench only rotates casing");
                int before = player.getInventory().countItem(ModBlocks.SERVICE_TRENCH.get().asItem());
                ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
                helper.assertTrue(level.getBlockState(pos) == glass, "Exact glass state restored, including alt and waterlogging");
                helper.assertTrue(player.getInventory().countItem(ModBlocks.SERVICE_TRENCH.get().asItem()) == before + (player.isCreative() ? 0 : 1),
                        "No duplication during glass decasing");
                var restored = (com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity)level.getBlockEntity(pos);
                helper.assertTrue(restored.getBehaviour(FluidTransportBehaviour.TYPE).interfaces == interfaces, "Glass decasing retains flows");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void pumpingThroughGlassTrench(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(0, 2, 2));
        var target = source.east(4);
        var pos = source.east(3);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        level.setBlockAndUpdate(source, AllBlocks.FLUID_TANK.getDefaultState());
        level.setBlockAndUpdate(target, AllBlocks.FLUID_TANK.getDefaultState());
        var pipe = AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
                .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, Direction.Axis.X);
        level.setBlockAndUpdate(source.east(), pipe);
        level.setBlockAndUpdate(pos, pipe);
        level.setBlockAndUpdate(source.east(2), AllBlocks.MECHANICAL_PUMP.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        level.setBlockAndUpdate(source.east(2).above(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        level.setBlockAndUpdate(source.east().above(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        var input = (com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(source);
        var output = (com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(target);
        input.getTankInventory().fill(new FluidStack(Fluids.WATER, 8000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        int[] before = {0};
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > 0, "Ordinary pipe transfers fluid");
        }).thenExecute(() -> {
            before[0] = output.getTankInventory().getFluidAmount();
            var stack = new ItemStack(ModBlocks.SERVICE_TRENCH.get());
            stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit(pos)));
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Trench continues transferring fluid");
            before[0] = output.getTankInventory().getFluidAmount();
            ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Decased pipe continues transferring fluid");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glassFlangeSelection(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        for (Direction.Axis axis : Direction.Axis.values()) {
            var side = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            var encased = ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().defaultBlockState()
                    .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, axis);
            var exposed = AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
                    .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, axis);
            level.setBlockAndUpdate(pos, encased);
            helper.assertTrue(glassFlanges(level, pos) == 2, "Isolated glass pipe has two flanges");
            level.setBlockAndUpdate(pos.relative(side), encased);
            helper.assertTrue(glassFlanges(level, pos) == 1 && glassFlanges(level, pos.relative(side)) == 1,
                    "Two encased glass pipes have only outer flanges");
            level.setBlockAndUpdate(pos.relative(side.getOpposite()), encased);
            helper.assertTrue(glassFlanges(level, pos) == 0, "Three glass pipes have no middle flanges");
            level.setBlockAndUpdate(pos.relative(side), exposed);
            helper.assertTrue(glassFlanges(level, pos) == 0 && glassFlanges(level, pos.relative(side)) == 1,
                    "Mixed glass pipes have native outer flanges");
            level.setBlockAndUpdate(pos.relative(side), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.assertTrue(glassFlanges(level, pos) == 1, "Removed neighbor exposes flange");
            var wrongAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            level.setBlockAndUpdate(pos.relative(side), exposed
                    .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, wrongAxis));
            helper.assertTrue(glassFlanges(level, pos) == 1, "Unaligned glass does not hide flange");
            level.setBlockAndUpdate(pos.relative(side), AllBlocks.FLUID_TANK.getDefaultState());
            var behavior = ((ServiceTrenchGlassPipeBlockEntity)level.getBlockEntity(pos)).getBehaviour(FluidTransportBehaviour.TYPE);
            var nativeBehavior = new com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity.StraightPipeFluidTransportBehaviour(
                    (ServiceTrenchGlassPipeBlockEntity)level.getBlockEntity(pos));
            helper.assertTrue(behavior.getRenderedRimAttachment(level, pos, encased, side)
                    == nativeBehavior.getRenderedRimAttachment(level, pos, exposed, side), "Fluid component attachment follows native glass rules");
            var be = level.getBlockEntity(pos);
            var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, encased,
                    be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            var loadedBehavior = ((ServiceTrenchGlassPipeBlockEntity)loaded).getBehaviour(FluidTransportBehaviour.TYPE);
            helper.assertTrue(loadedBehavior.getRenderedRimAttachment(level, pos, encased, side.getOpposite())
                    == FluidTransportBehaviour.AttachmentTypes.NONE, "Reload keeps connected end unflanged");
            level.setBlockAndUpdate(pos.relative(side), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.relative(side.getOpposite()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    private static int glassFlanges(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var be = (com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity)level.getBlockEntity(pos);
        var transport = be.getBehaviour(FluidTransportBehaviour.TYPE);
        int count = 0;
        for (Direction side : Direction.values()) {
            var attachment = transport.getRenderedRimAttachment(level, pos, level.getBlockState(pos), side);
            if (java.util.Arrays.asList(attachment.partials).contains(FluidTransportBehaviour.AttachmentTypes.ComponentPartials.RIM)) count++;
        }
        return count;
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void copperFlangeSelection(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        for (int mask = 0; mask < 64; mask++) {
            var state = ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState();
            for (Direction side : Direction.values())
                state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), (mask & (1 << side.ordinal())) != 0);
            level.setBlockAndUpdate(pos, state);
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == mask, "All exposed branches have flanges");
            for (Direction side : Direction.values()) {
                if ((mask & (1 << side.ordinal())) == 0) continue;
                var neighbor = ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState()
                        .setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite()), true);
                level.setBlockAndUpdate(pos.relative(side), neighbor);
            }
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == 0, "Connected branches have no internal flanges");
            for (Direction side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == mask, "Removing branches restores flanges");
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            var side = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            var state = ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState();
            for (Direction d : Direction.values()) state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(d), d.getAxis() == axis);
            level.setBlockAndUpdate(pos, state);
            var neighbor = AllBlocks.FLUID_PIPE.get().getAxisState(axis);
            level.setBlockAndUpdate(pos.relative(side), neighbor);
            int outer = 1 << side.getOpposite().ordinal();
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == outer, "Mixed ordinary copper transition");
            level.setBlockAndUpdate(pos.relative(side.getOpposite()), ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState()
                    .setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), true));
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == 0, "Middle of three pipes has no flanges");
            level.setBlockAndUpdate(pos.relative(side), AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
                    .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, axis));
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == 0, "Glass transition remains connected");
            level.setBlockAndUpdate(pos.relative(side), AllBlocks.FLUID_TANK.getDefaultState());
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == 0, "Fluid capability suppresses flange");
            level.setBlockAndUpdate(pos.relative(side), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, state) == (1 << side.ordinal()), "Unrelated solid block is not a fluid connection");
            var be = level.getBlockEntity(pos);
            var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state,
                    be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            helper.assertTrue(ServiceTrenchPipeFlanges.exposedEnds(level, pos, loaded.getBlockState()) == (1 << side.ordinal()), "Reloaded flange selection");
            for (Direction d : Direction.values()) level.setBlockAndUpdate(pos.relative(d), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void twoOrientationWrench(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var samples = List.of(
                ModBlocks.SERVICE_TRENCH.get().defaultBlockState(),
                ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState()
                        .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.EAST, true)
                        .setValue(BlockStateProperties.UP, true),
                ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().defaultBlockState()
                        .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, Direction.Axis.X)
                        .setValue(com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock.ALT, true),
                ModBlocks.SERVICE_TRENCH_SHAFT.get().defaultBlockState()
                        .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y));
        for (var sample : samples) {
            for (Direction initial : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                var state = sample.setValue(ServiceTrenchBlock.FACING, initial).setValue(BlockStateProperties.WATERLOGGED, true);
                var saved = net.minecraft.nbt.NbtUtils.writeBlockState(state);
                var loaded = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved);
                helper.assertTrue(loaded == state, "Existing facing values round-trip without migration");
                level.setBlockAndUpdate(pos, loaded);
                var entity = level.getBlockEntity(pos);
                Direction expected = initial.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
                for (int click = 0; click < 4; click++) {
                    ((IWrenchable)state.getBlock()).onWrenched(level.getBlockState(pos), context(player, pos));
                    helper.assertTrue(level.getBlockState(pos) == state.setValue(ServiceTrenchBlock.FACING, expected),
                            "Wrench changes only casing facing to the other axis");
                    helper.assertTrue(level.getBlockEntity(pos) == entity, "Rotation retains utility block entity");
                    expected = expected == Direction.NORTH ? Direction.EAST : Direction.NORTH;
                }
            }
        }
        helper.succeed();
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static UseOnContext context(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        return new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit(pos));
    }
}
