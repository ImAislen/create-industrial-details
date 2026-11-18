package com.aislen.createindustrialdetails;

import com.aislen.createindustrialdetails.registry.*;
import com.aislen.createindustrialdetails.content.block.industrialsteel.trench.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static com.aislen.createindustrialdetails.content.block.industrialsteel.trench.ServiceTrenchBlock.*;
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
        helper.assertTrue(!state.isCollisionShapeFullBlock(level, pos), "Empty trench collision is hollow");
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
            var entity = level.getBlockEntity(pos);
            helper.assertTrue(!level.getBlockState(pos).getValue(CAP_NEGATIVE)
                    && !level.getBlockState(pos).getValue(CAP_POSITIVE), "Driven shaft connections keep both ends open");
            helper.assertTrue(level.getBlockState(pos).useWithoutItem(level, player, hit(pos)).consumesAction(),
                    "Open the grille on the powered shaft");
            helper.assertTrue(level.getBlockEntity(pos) == entity, "Cover state retains the powered shaft block entity");
            ModBlocks.SERVICE_TRENCH_SHAFT.get().onWrenched(level.getBlockState(pos), context(player, pos));
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(end)).getSpeed() == 16, "Open grille and rotation retain power");
            helper.assertTrue(wrench(player, hit(pos), true).consumesAction() && level.getBlockEntity(pos) == null
                    && level.getBlockState(pos).is(ModBlocks.SERVICE_TRENCH.get())
                    && level.getBlockState(pos).getValue(OPEN), "Open Shift-wrench removes the powered shaft only");
        }).thenWaitUntil(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(end)).getSpeed() == 0, "Removing the shaft detaches power");
        }).thenExecute(() -> {
            var housing = level.getBlockState(pos);
            ModBlocks.SERVICE_TRENCH.get().onWrenched(housing, context(player, pos));
            var shaftStack = AllBlocks.SHAFT.asStack();
            helper.assertTrue(clickUtility(player, shaftStack, hit(pos)).consumesAction(), "Reinstall shaft into the open housing");
        }).thenWaitUntil(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(end)).getSpeed() == 16, "Reinstalled shaft transmits power");
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
            var entity = level.getBlockEntity(pos);
            var state = level.getBlockState(pos);
            helper.assertTrue(!state.getValue(CAP_NEGATIVE) && !state.getValue(CAP_POSITIVE), "Connected pump and tank keep both ends open");
            state.useWithoutItem(level, player, hit(pos));
            helper.assertTrue(level.getBlockState(pos).getValue(OPEN) && level.getBlockEntity(pos) == entity,
                    "Opening regular pipe housing retains its block entity");
            var stack = AllBlocks.SHAFT.asStack();
            player.setShiftKeyDown(true);
            helper.assertTrue(clickUtility(player, stack, hit(pos)) == InteractionResult.FAIL
                    && stack.getCount() == 1 && level.getBlockEntity(pos) == entity,
                    "A pressurized pipe rejects replacement without consuming the shaft");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
            before[0] = output.getTankInventory().getFluidAmount();
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Open regular pipe still transfers fluid");
            var flows = com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, pos).interfaces;
            helper.assertTrue(wrench(player, hit(pos), false).consumesAction()
                    && level.getBlockState(pos).is(ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get())
                    && com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, pos).interfaces == flows,
                    "Wrenching an active open pipe fits glass and retains its flow data");
            before[0] = output.getTankInventory().getFluidAmount();
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Glass conversion retains fluid transfer");
            helper.assertTrue(wrench(player, hit(pos), false).consumesAction()
                    && level.getBlockState(pos).is(ModBlocks.SERVICE_TRENCH_PIPE.get()), "Wrench converts the open glass pipe back");
            before[0] = output.getTankInventory().getFluidAmount();
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Copper conversion retains fluid transfer");
            before[0] = output.getTankInventory().getFluidAmount();
            level.getBlockState(pos).useWithoutItem(level, player, hit(pos));
            ModBlocks.SERVICE_TRENCH_PIPE.get().onSneakWrenched(level.getBlockState(pos), context(player, pos));
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Decased pipe continues transferring fluid");
            ((EncasableBlock) pipe.getBlock()).tryEncase(level.getBlockState(pos), level, pos,
                    new ItemStack(ModBlocks.SERVICE_TRENCH.get()), player, InteractionHand.MAIN_HAND, hit(pos));
            player.getInventory().selected = 1;
            helper.assertTrue(level.getBlockState(pos).useWithoutItem(level, player, hit(pos)).consumesAction(),
                    "Reopen the housing before maintenance");
            helper.assertTrue(wrench(player, hit(pos), true).consumesAction()
                    && level.getBlockState(pos).is(ModBlocks.SERVICE_TRENCH.get())
                    && level.getBlockEntity(pos) == null, "Open Shift-wrench removes the active pipe and keeps the housing");
            before[0] = output.getTankInventory().getFluidAmount();
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() == before[0],
                    "Removing the pipe stops transfer to the formerly connected tank");
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
            var entity = level.getBlockEntity(pos);
            level.getBlockState(pos).useWithoutItem(level, player, hit(pos));
            helper.assertTrue(level.getBlockState(pos).getValue(OPEN) && level.getBlockEntity(pos) == entity,
                    "Opening glass pipe housing retains its block entity");
            before[0] = output.getTankInventory().getFluidAmount();
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(output.getTankInventory().getFluidAmount() > before[0], "Open glass pipe still transfers fluid");
            before[0] = output.getTankInventory().getFluidAmount();
            level.getBlockState(pos).useWithoutItem(level, player, hit(pos));
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
                var properties = saved.getCompound("Properties");
                properties.remove("open");
                properties.remove("cap_negative");
                properties.remove("cap_positive");
                var loaded = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved);
                helper.assertTrue(loaded == state, "Old housing defaults to closed and capped while retaining utility properties");
                level.setBlockAndUpdate(pos, loaded);
                var entity = level.getBlockEntity(pos);
                Direction expected = initial.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
                for (int click = 0; click < 4; click++) {
                    ((IWrenchable)state.getBlock()).onWrenched(level.getBlockState(pos), context(player, pos));
                    var rotated = level.getBlockState(pos);
                    helper.assertTrue(rotated.setValue(CAP_NEGATIVE, state.getValue(CAP_NEGATIVE))
                                    .setValue(CAP_POSITIVE, state.getValue(CAP_POSITIVE))
                                    == state.setValue(ServiceTrenchBlock.FACING, expected),
                            "Wrench switches the casing axis while retaining water and the utility state");
                    helper.assertTrue(level.getBlockEntity(pos) == entity, "Rotation retains utility block entity");
                    expected = expected == Direction.NORTH ? Direction.EAST : Direction.NORTH;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void crawlingThroughEmptyTrenches(GameTestHelper helper) {
        var level = helper.getLevel();
        var start = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            boolean alongX = facing.getAxis() == Direction.Axis.X;
            var forward = alongX ? Direction.EAST : Direction.SOUTH;
            var state = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(ServiceTrenchBlock.FACING, facing);
            for (int i = 0; i < 3; i++) level.setBlockAndUpdate(start.relative(forward, i), state);
            var middle = start.relative(forward);
            var center = level.getBlockState(middle);
            helper.assertTrue(!center.getValue(CAP_NEGATIVE) && !center.getValue(CAP_POSITIVE),
                    "Aligned neighbors remove both interior caps");
            helper.assertTrue(level.getBlockState(start).getValue(CAP_NEGATIVE)
                    && level.getBlockState(start.relative(forward, 2)).getValue(CAP_POSITIVE),
                    "Outer ends are capped");
            var shape = center.getCollisionShape(level, middle);
            helper.assertTrue(shape == center.getCollisionShape(level, middle), "Collision shape is cached");
            helper.assertTrue(!state.isSuffocating(level, start) && !state.canOcclude(), "Hollow trench does not suffocate or occlude");
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            player.refreshDimensions();
            helper.assertTrue(player.getBoundingBox().getYsize() <= 14.0 / 16, "Vanilla crawling dimensions fit interior");
            player.setPos(start.getX() + 0.5, start.getY() + 1.0 / 16, start.getZ() + 0.5);
            for (int step = 0; step < 40; step++)
                player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(alongX ? 0.1 : 0, 0, alongX ? 0 : 0.1));
            double traveled = alongX ? player.getX() - start.getX() : player.getZ() - start.getZ();
            helper.assertTrue(traveled > 2.5 && traveled < 2.75, "Player crawls through the run and stops at the far cap");
            player.setPos(start.getX() + 0.5, start.getY() + 1.0 / 16, start.getZ() + 0.5);
            player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(alongX ? 0 : 2, 0, alongX ? 2 : 0));
            double lateral = alongX ? player.getZ() - start.getZ() : player.getX() - start.getX();
            helper.assertTrue(lateral < 0.65, "Side wall stops crawling player");
            player.setPos(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
            player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -1, 0));
            helper.assertTrue(Math.abs(player.getY() - start.getY() - 1.0 / 16) < 0.0001, "Floor supports crawling player");
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            player.refreshDimensions();
            player.setPos(start.getX() + 0.5, start.getY() + 1.1, start.getZ() + 0.5);
            player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.5, 0));
            helper.assertTrue(Math.abs(player.getY() - start.getY() - 1) < 0.0001, "Grille roof supports standing player");
            for (int step = 0; step < 20; step++) {
                player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(alongX ? 0.1 : 0, -0.05, alongX ? 0 : 0.1));
                helper.assertTrue(Math.abs(player.getY() - start.getY() - 1) < 0.0001, "Player walks continuously across grille gaps");
            }
            var saved = net.minecraft.nbt.NbtUtils.writeBlockState(center);
            var loaded = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved);
            helper.assertTrue(loaded.getCollisionShape(level, middle) == shape, "Reload retains oriented collision");
            level.removeBlock(start.relative(forward, 2), false);
            helper.assertTrue(level.getBlockState(middle).getValue(CAP_POSITIVE), "Removing a neighbor restores the cap");
            level.setBlockAndUpdate(start.relative(forward, 2), state.setValue(FACING, forward.getClockWise()));
            helper.assertTrue(level.getBlockState(middle).getValue(CAP_POSITIVE), "Perpendicular trenches do not join");
            for (int i = 0; i < 3; i++) level.setBlockAndUpdate(start.relative(forward, i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        for (var block : List.of(ModBlocks.SERVICE_TRENCH_PIPE.get(), ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get(), ModBlocks.SERVICE_TRENCH_SHAFT.get())) {
            var state = block.defaultBlockState();
            helper.assertTrue(state.isCollisionShapeFullBlock(level, start), "Encased utility collision stays full");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void installAndReplaceUtilities(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var base = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, Direction.EAST)
                .setValue(OPEN, true).setValue(WATERLOGGED, true);
        level.setBlockAndUpdate(pos, base);
        level.setBlockAndUpdate(pos.west(), AllBlocks.FLUID_PIPE.get().getAxisState(Direction.Axis.X));
        var copper = AllBlocks.FLUID_PIPE.asStack(2);
        helper.assertTrue(clickUtility(player, copper, hit(pos)) == InteractionResult.CONSUME && copper.getCount() == 1,
                "Install regular pipe and consume one pipe without consuming a trench");
        var installed = level.getBlockState(pos);
        helper.assertTrue(installed.is(ModBlocks.SERVICE_TRENCH_PIPE.get())
                && FluidPipeBlock.isOpenAt(installed, Direction.WEST) && FluidPipeBlock.isOpenAt(installed, Direction.EAST),
                "Regular pipe follows Create's native connection state");
        helper.assertTrue(installed.getValue(FACING) == Direction.EAST && installed.getValue(OPEN)
                && installed.getValue(WATERLOGGED) && !installed.getValue(CAP_NEGATIVE), "Installation preserves housing and opens the connected end");

        player.setShiftKeyDown(true);
        helper.assertTrue(clickUtility(player, copper, hit(pos)) == InteractionResult.CONSUME,
                "Sneak-use a spare copper pipe on the side to fit the straight glass variant");
        var glazed = level.getBlockState(pos);
        helper.assertTrue(glazed.is(ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get()) && glazed.getValue(BlockStateProperties.AXIS) == Direction.Axis.X
                && glazed.getValue(OPEN) && glazed.getValue(WATERLOGGED) && !glazed.getValue(CAP_NEGATIVE),
                "Glass conversion preserves the straight axis and housing");
        helper.assertTrue(pipeCount(player) == 1, "Glass conversion returns the removed regular pipe exactly once");
        player.getMainHandItem().grow(1);
        helper.assertTrue(clickUtility(player, player.getMainHandItem(), hit(pos)) == InteractionResult.CONSUME,
                "Replace glass utility with regular pipe");
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.SERVICE_TRENCH_PIPE.get())
                && pipeCount(player) == 2, "Glass removal returns Create's regular pipe item");
        var inventory = player.getInventory();
        inventory.setItem(1, inventory.removeItemNoUpdate(inventory.selected));

        var shaft = AllBlocks.SHAFT.asStack(2);
        helper.assertTrue(clickUtility(player, shaft, hit(pos)) == InteractionResult.CONSUME && shaft.getCount() == 1,
                "Replacing regular pipe with a shaft consumes one shaft");
        var shaftState = level.getBlockState(pos);
        helper.assertTrue(shaftState.is(ModBlocks.SERVICE_TRENCH_SHAFT.get()) && shaftState.getValue(BlockStateProperties.AXIS) == Direction.Axis.X
                && shaftState.getValue(FACING) == Direction.EAST && shaftState.getValue(OPEN) && shaftState.getValue(WATERLOGGED),
                "Shaft follows trench axis, retaining water and cover");
        helper.assertTrue(!shaftState.getValue(CAP_NEGATIVE) && !shaftState.getValue(CAP_POSITIVE) && pipeCount(player) == 3,
                "Shaft housing leaves both ends clear and the removed pipe is returned");
        inventory.selected = 1;
        helper.assertTrue(clickUtility(player, player.getMainHandItem(), hit(pos)) == InteractionResult.CONSUME,
                "Replace shaft with regular pipe");
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.SERVICE_TRENCH_PIPE.get()) && pipeCount(player) == 2
                && inventory.items.stream().filter(AllBlocks.SHAFT::isIn).mapToInt(ItemStack::getCount).sum() == 2,
                "Shaft replacement returns exactly one shaft");

        var creative = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        var second = pos.south();
        level.setBlockAndUpdate(second, base);
        var creativeShaft = AllBlocks.SHAFT.asStack();
        helper.assertTrue(clickUtility(creative, creativeShaft, hit(second)) == InteractionResult.CONSUME
                && creativeShaft.getCount() == 1 && level.getBlockState(second).is(ModBlocks.SERVICE_TRENCH_SHAFT.get()),
                "Creative can install directly into an empty trench without consumption");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void replacePoweredShaft(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        level.setBlockAndUpdate(pos.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        level.setBlockAndUpdate(pos.east(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        level.setBlockAndUpdate(pos, ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, Direction.EAST));
        helper.assertTrue(clickUtility(player, AllBlocks.SHAFT.asStack(), hit(pos)) == InteractionResult.CONSUME,
                "Install a shaft directly into empty housing between kinetic blocks");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(pos.east())).getSpeed() == 16,
                    "Newly installed shaft transmits native speed");
        }).thenExecute(() -> {
            player.setShiftKeyDown(true);
            helper.assertTrue(clickUtility(player, AllBlocks.FLUID_PIPE.asStack(), hit(pos)) == InteractionResult.CONSUME,
                    "Replace a running shaft with a regular pipe");
        }).thenWaitUntil(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(pos.east())).getSpeed() == 0,
                    "Replacing the running shaft detaches its network");
        }).thenExecute(() -> {
            helper.assertTrue(clickUtility(player, AllBlocks.SHAFT.asStack(), hit(pos)) == InteractionResult.CONSUME,
                    "Refit a shaft into the occupied trench");
        }).thenWaitUntil(() -> {
            helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(pos.east())).getSpeed() == 16,
                    "Refitted shaft reconnects the downstream shaft");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void openGrilleAndPlacementHelper(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (var facing : List.of(Direction.NORTH, Direction.EAST)) {
            var base = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, facing);
            level.setBlockAndUpdate(pos, base);
            helper.assertTrue(level.getBlockState(pos).getValue(CAP_NEGATIVE) && level.getBlockState(pos).getValue(CAP_POSITIVE),
                    "Isolated housing caps both ends");
            helper.assertTrue(base.useWithoutItem(level, player, hit(pos)).consumesAction(), "Empty hand opens the cover");
            var open = level.getBlockState(pos);
            helper.assertTrue(open.getValue(OPEN) && open.getLightBlock(level, pos) == 0 && !open.canOcclude(),
                    "Open dry trench uses vanilla light transmission and no full-cube occlusion");
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            player.refreshDimensions();
            player.setPos(pos.getX() + .5, pos.getY() + 1.1, pos.getZ() + .5);
            player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -1.2, 0));
            helper.assertTrue(Math.abs(player.getY() - pos.getY() - 1.0 / 16) < .0001, "Player enters from above and lands on the floor");
            helper.assertTrue(open.useWithoutItem(level, player, hit(pos)).consumesAction() && !level.getBlockState(pos).getValue(OPEN),
                    "A second empty-hand use closes the cover");

            var occupied = ModBlocks.SERVICE_TRENCH_SHAFT.get().defaultBlockState().setValue(FACING, facing)
                    .setValue(BlockStateProperties.AXIS, facing.getAxis()).setValue(OPEN, true);
            level.setBlockAndUpdate(pos, occupied);
            var utility = level.getBlockEntity(pos);
            var stack = new ItemStack(ModBlocks.SERVICE_TRENCH.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var target = pos.relative(facing);
            var endHit = new BlockHitResult(Vec3.atCenterOf(pos).relative(facing, 0.5), facing, pos, false);
            player.setShiftKeyDown(true);
            var createHelper = net.createmod.catnip.placement.PlacementHelpers.getHelpersView().stream()
                    .filter(h -> h.matchesItem(stack) && h.matchesState(occupied)).findFirst().orElseThrow();
            helper.assertTrue(!createHelper.getOffset(player, level, occupied, pos, endHit).isSuccessful(), "Sneak bypasses native placement assistance");
            player.setShiftKeyDown(false);
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState());
            helper.assertTrue(!createHelper.getOffset(player, level, occupied, pos, endHit).isSuccessful(), "Solid obstructions stop both helper directions");
            level.removeBlock(target, false);
            var preview = createHelper.getOffset(player, level, occupied, pos, endHit, stack);
            helper.assertTrue(preview.isSuccessful() && preview.getBlockPos().equals(target)
                    && preview.hasGhostState() && preview.getTransform().apply(preview.getGhostState()).is(base.getBlock()),
                    "Native helper preview targets an empty trench at the selected end");
            helper.assertTrue(stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, endHit)).consumesAction()
                    && stack.getCount() == 1, "Native placement consumes exactly one trench");
            var placed = level.getBlockState(target);
            helper.assertTrue(placed.is(base.getBlock()) && placed.getValue(FACING) == facing && !placed.getValue(OPEN)
                    && level.getBlockEntity(pos) == utility, "Helper extends the axis with an empty closed trench and preserves the utility");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.removeBlock(target, false);
            level.removeBlock(pos.relative(facing.getOpposite()), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wrenchThroughOpenGrille(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var empty = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().setValue(FACING, Direction.EAST);
        var copper = ModBlocks.SERVICE_TRENCH_PIPE.get().defaultBlockState()
                .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
        var glass = ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        var shaft = ModBlocks.SERVICE_TRENCH_SHAFT.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        level.setBlockAndUpdate(pos.west(), empty);
        for (var utility : List.of(copper, glass, shaft)) {
            for (var mode : List.of(net.minecraft.world.level.GameType.SURVIVAL, net.minecraft.world.level.GameType.CREATIVE)) {
                var player = helper.makeMockPlayer(mode);
                var closed = utility.setValue(FACING, Direction.EAST).setValue(WATERLOGGED, true);
                level.setBlockAndUpdate(pos, closed.setValue(CAP_NEGATIVE, true).setValue(CAP_POSITIVE, true));
                var placed = level.getBlockState(pos);
                helper.assertTrue(!placed.getValue(CAP_NEGATIVE) && !placed.getValue(CAP_POSITIVE),
                        "Occupied casing clears old cap flags even with a closed grille");
                var open = placed.setValue(OPEN, true);
                helper.assertTrue(open.getCollisionShape(level, pos) == open.setValue(CAP_POSITIVE, true)
                        .setValue(CAP_NEGATIVE, true).getCollisionShape(level, pos),
                        "Legacy cap flags cannot add collision to open occupied casing");
                level.setBlockAndUpdate(pos, open);
                helper.assertTrue(wrench(player, hit(pos), true).consumesAction(), "Shift-wrench handles open utility");
                var expected = empty.setValue(OPEN, true).setValue(WATERLOGGED, true).setValue(CAP_NEGATIVE, false);
                helper.assertTrue(level.getBlockState(pos) == expected && level.getBlockEntity(pos) == null,
                        "Utility removal preserves open housing, waterlogging and facing and recomputes empty end caps");
                var saved = net.minecraft.nbt.NbtUtils.writeBlockState(level.getBlockState(pos));
                helper.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(
                        level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved) == expected,
                        "Open empty housing and its caps survive a save/load round trip");
                var removed = utility == shaft ? AllBlocks.SHAFT.asStack() : AllBlocks.FLUID_PIPE.asStack();
                helper.assertTrue(player.getInventory().countItem(removed.getItem()) == (player.isCreative() ? 0 : 1)
                        && player.getInventory().countItem(ModBlocks.SERVICE_TRENCH.asItem()) == 0,
                        "Only the original utility item is returned in Survival");
            }
        }

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var straight = copper.setValue(FACING, Direction.EAST).setValue(OPEN, true);
        level.setBlockAndUpdate(pos, straight);
        var end = new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false);
        helper.assertTrue(wrench(player, end, false) == InteractionResult.PASS && level.getBlockState(pos) == straight,
                "An axial wrench click leaves a straight pipe unchanged");
        var elbow = straight.setValue(BlockStateProperties.WEST, false).setValue(BlockStateProperties.NORTH, true);
        var junction = straight.setValue(BlockStateProperties.NORTH, true);
        for (var invalid : List.of(elbow, junction)) {
            level.setBlockAndUpdate(pos, invalid);
            helper.assertTrue(wrench(player, hit(pos), false) == InteractionResult.PASS && level.getBlockState(pos) == invalid,
                    "Elbows and junctions refuse glass conversion and casing rotation");
        }
        helper.succeed();
    }

    private static InteractionResult wrench(net.minecraft.world.entity.player.Player player, BlockHitResult hit, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        var result = AllItems.WRENCH.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(false);
        return result;
    }

    private static InteractionResult clickUtility(net.minecraft.world.entity.player.Player player, ItemStack stack, BlockHitResult hit) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, hit.getBlockPos(), hit));
        return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
    }

    private static int pipeCount(net.minecraft.world.entity.player.Player player) {
        return player.getInventory().items.stream().filter(AllBlocks.FLUID_PIPE::isIn).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void platedSteelConnections(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var state = ModBlocks.BLOCK_OF_PLATED_INDUSTRIAL_STEEL.get().defaultBlockState();
        var behavior = new com.aislen.createindustrialdetails.client.model.PlatedIndustrialSteelCTBehaviour();
        var type = com.simibubi.create.foundation.block.connected.AllCTTypes.OMNIDIRECTIONAL;
        level.setBlockAndUpdate(pos, state);
        for (Direction face : Direction.values()) {
            var empty = behavior.buildContext(level, pos, state, face, type.getContextRequirement());
            helper.assertTrue(type.getTextureIndex(empty) == 0, "Isolated plated face uses exact original tile");
            var normal = face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
            var neighbor = pos.relative(normal);
            level.setBlockAndUpdate(neighbor, ModBlocks.BLOCK_OF_INDUSTRIAL_STEEL.get().defaultBlockState());
            helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) == 0,
                    "Regular steel never connects to plated steel");
            level.setBlockAndUpdate(neighbor, state);
            helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) != 0,
                    "Each face connects to plated neighbors");
            level.setBlockAndUpdate(neighbor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement())) == 0,
                    "Neighbor removal restores border");
        }
        for (Direction face : Direction.values()) {
            var vertical = face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
            var horizontal = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.WEST;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
                level.setBlockAndUpdate(pos.relative(horizontal, x).relative(vertical, y), state);
            var full = behavior.buildContext(level, pos, state, face, type.getContextRequirement());
            helper.assertTrue(full.up && full.down && full.left && full.right && full.topLeft && full.topRight
                    && full.bottomLeft && full.bottomRight, "Complete wall/floor sheet connects in all directions");
            level.setBlockAndUpdate(pos.relative(horizontal).relative(vertical), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, pos, state, face, type.getContextRequirement()))
                    != type.getTextureIndex(full), "Concave corner selects distinct border tile");
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                if (x != 0 || y != 0) level.setBlockAndUpdate(pos.relative(horizontal, x).relative(vertical, y),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        var boundary = new BlockPos((pos.getX() & ~15) + 15, pos.getY() + 10, pos.getZ());
        level.setBlockAndUpdate(boundary, state);
        level.setBlockAndUpdate(boundary.east(), state);
        helper.assertTrue(type.getTextureIndex(behavior.buildContext(level, boundary, state, Direction.SOUTH,
                type.getContextRequirement())) != 0, "Connection crosses chunk boundary");
        level.setBlockAndUpdate(boundary, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(boundary.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(state.isCollisionShapeFullBlock(level, pos) && !state.hasBlockEntity(), "Static solid decorative block");
        helper.assertTrue(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                && state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL), "Matching mining requirements");
        var drops = Block.getDrops(state, level, pos, null);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.BLOCK_OF_PLATED_INDUSTRIAL_STEEL.get().asItem()), "Plated block drops itself");
        helper.succeed();
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static UseOnContext context(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        return new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit(pos));
    }
}
