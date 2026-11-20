package com.aislen.createindustrialdetails.content.block.pneumatic;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.aislen.createindustrialdetails.content.block.pneumatic.PneumaticStationBlock.Kind;
import static com.aislen.createindustrialdetails.content.block.pneumatic.PneumaticSpline.Port;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PneumaticGameTests {
    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void pneumaticArmAt16(GameTestHelper h) { armFeed(h, 16); }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void pneumaticArmAt64(GameTestHelper h) { armFeed(h, 64); }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pneumaticArmAt128(GameTestHelper h) { armFeed(h, 128); }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pneumaticArmAt256(GameTestHelper h) { armFeed(h, 256); }

    private static void armFeed(GameTestHelper h, int rpm) {
        var level = h.getLevel();
        BlockPos launcher = h.absolutePos(new BlockPos(2, 3, 2)).above(40 + rpm / 8);
        BlockPos receiver = launcher.east(16);
        place(level, launcher, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        var network = PneumaticNetwork.get(level);
        link(h, port(launcher, Direction.UP, Kind.LAUNCHER), port(receiver, Direction.UP, Kind.RECEIVER));
        BlockPos armPos = launcher.west(2);
        BlockPos depotPos = armPos.west();
        level.setBlockAndUpdate(depotPos, AllBlocks.DEPOT.getDefaultState());
        level.setBlockAndUpdate(armPos, AllBlocks.MECHANICAL_ARM.getDefaultState());
        level.setBlockAndUpdate(armPos.south(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        motor(level, armPos.south().below(), rpm);
        motor(level, launcher.below(), rpm);
        var arm = (ArmBlockEntity) level.getBlockEntity(armPos);
        var machine = (PneumaticLauncherBlockEntity) level.getBlockEntity(launcher);
        var depot = (DepotBlockEntity) level.getBlockEntity(depotPos);
        var input = ArmInteractionPoint.create(level, depotPos, level.getBlockState(depotPos));
        var output = ArmInteractionPoint.create(level, launcher, level.getBlockState(launcher));
        var take = ArmInteractionPoint.create(level, receiver, level.getBlockState(receiver));
        h.assertTrue(com.simibubi.create.api.stress.BlockStressValues.getImpact(ModBlocks.PNEUMATIC_LAUNCHER.get()) == 4,
                "Launcher advertises its stress impact through Create");
        h.assertTrue(input != null && output instanceof PneumaticArmPoint && take instanceof PneumaticArmPoint,
                "Native Create selection recognizes Depot, launcher and receiver");
        h.assertTrue(output.getMode() == ArmInteractionPoint.Mode.DEPOSIT && take.getMode() == ArmInteractionPoint.Mode.TAKE,
                "Station points select their supported mode");
        input.cycleMode();
        var points = new ListTag();
        points.add(input.serialize(armPos));
        points.add(output.serialize(armPos));
        configureArm(level, arm, points);
        var fuel = level.getCapability(Capabilities.ItemHandler.BLOCK, launcher, Direction.WEST);
        var cargo = cargo();
        h.assertTrue(fuel != null && fuel.insertItem(0, cargo, false) == cargo && output.insert(arm, new ItemStack(Items.COBBLESTONE), true).is(Items.COBBLESTONE),
                "Automation sees a fuel-only slot and the arm refuses ordinary items");
        h.assertTrue(fuel.insertItem(0, new ItemStack(Items.WIND_CHARGE, 3), false).isEmpty(), "Automated fuel insertion");
        depot.setHeldItem(cargo.copy());
        int[] supplied = {1};
        int[] delivered = {0};
        long start = level.getGameTime();
        int[] queuedTicks = {0};
        int[] maxQueued = {0};
        h.startSequence().thenWaitUntil(() -> {
            if (depot.getHeldItem().isEmpty() && supplied[0] < 3) {
                depot.setHeldItem(cargo.copy());
                supplied[0]++;
            }
            if (!take.extract(null, 0, 1, true).isEmpty()) {
                var received = take.extract(null, 0, 1, false);
                h.assertTrue(ItemStack.matches(received, cargo), "Address, contents and package style survive both native arm targets");
                delivered[0]++;
            }
            queuedTicks[0] = network.peek(launcher).isEmpty() ? 0 : queuedTicks[0] + 1;
            maxQueued[0] = Math.max(maxQueued[0], queuedTicks[0]);
            h.assertTrue(delivered[0] == 3, "Waiting for sustained native arm feed at " + rpm + " RPM, delivered " + delivered[0]
                    + ", supplied " + supplied[0] + ", arm speed " + arm.getSpeed() + ", launcher speed " + machine.getSpeed());
            h.assertTrue(Math.abs(arm.getSpeed()) == rpm && Math.abs(machine.getSpeed()) == rpm,
                    "Both machines get the configured RPM through actual Create kinetics");
            h.assertTrue(maxQueued[0] <= 2 && level.getGameTime() - start <= 12L * (1024 / rpm) + 50,
                    "Launcher can keep pace with same-RPM arm");
            h.assertTrue(network.fuel(launcher) == 0, "One charge per delivery");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void pneumaticBoostedDeliveryToSecondArm(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos receiver = h.absolutePos(new BlockPos(2, 4, 2)).above(110);
        BlockPos launcher = receiver.west(75);
        BlockPos support = launcher.east(25).above(4);
        BlockPos booster = launcher.east(42).above(4);
        place(level, launcher, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        var axis = PneumaticStationBlock.AXIS;
        level.setBlockAndUpdate(support, ModBlocks.PNEUMATIC_TUBE_SUPPORT.get().defaultBlockState().setValue(axis, Direction.Axis.X));
        level.setBlockAndUpdate(booster, ModBlocks.PNEUMATIC_BOOSTER.get().defaultBlockState().setValue(axis, Direction.Axis.X));
        h.assertTrue(!level.getBlockState(support).hasBlockEntity() && !level.getBlockState(booster).hasBlockEntity(),
                "Inline stations do not allocate block entities");
        var first = port(launcher, Direction.UP, Kind.LAUNCHER);
        var supportIn = port(support, Direction.WEST, Kind.SUPPORT);
        var boosterIn = port(booster, Direction.WEST, Kind.BOOSTER);
        link(h, first, supportIn);
        link(h, opposite(supportIn), boosterIn);
        link(h, opposite(boosterIn), port(receiver, Direction.UP, Kind.RECEIVER));

        BlockPos armPos = receiver.west(2);
        BlockPos depotPos = armPos.west();
        level.setBlockAndUpdate(armPos, AllBlocks.MECHANICAL_ARM.getDefaultState());
        level.setBlockAndUpdate(depotPos, AllBlocks.DEPOT.getDefaultState());
        level.setBlockAndUpdate(armPos.south(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        motor(level, armPos.south().below(), 256);
        var arm = (ArmBlockEntity) level.getBlockEntity(armPos);
        var depot = (DepotBlockEntity) level.getBlockEntity(depotPos);
        var points = new ListTag();
        points.add(ArmInteractionPoint.create(level, receiver.above(), level.getBlockState(receiver.above())).serialize(armPos));
        points.add(ArmInteractionPoint.create(level, depotPos, level.getBlockState(depotPos)).serialize(armPos));
        configureArm(level, arm, points);

        var network = PneumaticNetwork.get(level);
        var cargo = cargo();
        network.insertFuel(launcher, 1, false);
        network.insertFuel(booster, 1, false);
        network.insertPackage(level, launcher, cargo, false);
        h.assertTrue(network.dispatch(level, launcher), "Boosted route commits");
        h.assertTrue(network.removeSpan(level, first, null) && network.occupied(opposite(supportIn)),
                "Removing one support span preserves its other link");
        h.succeedWhen(() -> {
            h.assertTrue(ItemStack.matches(depot.getHeldItem(), cargo), "The second actual Create arm delivers the original package to a Depot"
                    + ", arm speed " + arm.getSpeed() + ", receiver loaded " + !network.peek(receiver).isEmpty());
            h.assertTrue(network.fuel(launcher) == 0 && network.fuel(booster) == 0 && network.peek(receiver).isEmpty(),
                    "A disconnected in-flight route uses only reserved fuel and yields the package once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void pneumaticClippingAndSafeInvalidDispatch(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos launcher = h.absolutePos(new BlockPos(2, 3, 2)).above(36);
        BlockPos receiver = launcher.east(18);
        place(level, launcher, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        var origin = port(launcher, Direction.UP, Kind.LAUNCHER);
        var target = port(receiver, Direction.UP, Kind.RECEIVER);
        var curve = new PneumaticSpline(origin, target);
        ensureLoaded(level, curve);
        BlockPos obstruction = BlockPos.containing(curve.at(curve.length() * .5));
        level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        var network = PneumaticNetwork.get(level);
        var mechanical = ArmInteractionPoint.create(level, launcher, level.getBlockState(launcher));
        var cargo = cargo();
        h.assertTrue(mechanical.insert(null, cargo, false).isEmpty(), "Accept addressed package from arm");
        h.assertTrue(!mechanical.insert(null, cargo, true).isEmpty(), "Reject second package");
        network.insertFuel(launcher, 1, false);
        h.assertTrue(!network.dispatch(level, launcher) && network.fuel(launcher) == 1 && ItemStack.matches(network.peek(launcher), cargo),
                "Failed route spends neither package nor fuel");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(launcher));
        var tubing = new ItemStack(ModItems.PNEUMATIC_TUBING.get(), 64);
        player.setItemInHand(InteractionHand.MAIN_HAND, tubing);
        ModItems.PNEUMATIC_TUBING.get().useOn(context(level, player, launcher.above(), Direction.UP));
        ModItems.PNEUMATIC_TUBING.get().useOn(context(level, player, receiver.above(), Direction.UP));
        h.assertTrue(tubing.getCount() == 64 - curve.cost() && network.occupied(origin) && network.occupied(target),
                "Two item clicks spend the actual curved length");
        h.assertTrue(level.getBlockState(obstruction).is(Blocks.STONE) && curve.length() > 18, "Tube clips through a solid block along its arc");
        h.runAfterDelay(8, () -> {
            h.assertTrue(network.fuel(launcher) == 1 && ItemStack.matches(network.peek(launcher), cargo), "Zero RPM waits even with a connected receiver");
            player.setItemInHand(InteractionHand.OFF_HAND, tubing);
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            ModBlocks.PNEUMATIC_RECEIVER.get().onSneakWrenched(level.getBlockState(receiver.above()),
                    context(level, player, receiver.above(), Direction.UP));
            h.assertTrue(!network.occupied(origin) && !network.occupied(target) && level.getBlockState(receiver).is(ModBlocks.PNEUMATIC_RECEIVER.get()),
                    "Wrench removes only the occupied span");
            h.assertTrue(PneumaticTubingItem.available(player, ModItems.PNEUMATIC_TUBING.get()) == 64, "Wrench returns exact construction materials");
            level.destroyBlock(launcher.above(), true);
            h.assertTrue(level.getBlockState(launcher).isAir() && network.peek(launcher).isEmpty() && network.fuel(launcher) == 0,
                    "Breaking upper block removes owner storage once");
            place(level, launcher, ModBlocks.PNEUMATIC_LAUNCHER.get());
            h.assertTrue(network.recover(launcher, player) && !network.recover(launcher, player)
                            && player.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, cargo)),
                    "After multiblock breakup the original cargo is recoverable exactly once");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void pneumaticBlocksWhenRouteChunksAreAbsent(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos remote = h.absolutePos(new BlockPos(1600, 62, 1600));
        BlockPos launcher = new BlockPos((remote.getX() & ~15) + 8, remote.getY(), (remote.getZ() & ~15) + 8);
        BlockPos receiver = launcher.offset(34, 0, 34);
        place(level, launcher, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        var first = port(launcher, Direction.UP, Kind.LAUNCHER);
        var last = port(receiver, Direction.UP, Kind.RECEIVER);
        var curve = new PneumaticSpline(first, last);
        var network = PneumaticNetwork.get(level);
        h.assertTrue(curve.length() < 64 && network.connect(level, first, last, curve, 0), "Build a span without loading its interior");
        var missing = curve.chunks.stream().filter(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null).toList();
        h.assertTrue(!missing.isEmpty(), "Test must start with missing interior chunks");
        var cargo = cargo();
        network.insertPackage(level, launcher, cargo, false);
        network.insertFuel(launcher, 1, false);
        h.assertTrue(!network.dispatch(level, launcher) && network.fuel(launcher) == 1 && ItemStack.matches(network.peek(launcher), cargo),
                "Unavailable chunks block dispatch without consuming cargo or fuel");
        h.assertTrue(missing.stream().allMatch(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null),
                "Preflight leaves the missing chunks unloaded");
        ensureLoaded(level, curve);
        h.assertTrue(network.dispatch(level, launcher), "The same cached route becomes usable after loading the chunks");
        level.destroyBlock(receiver, false);
        level.destroyBlock(launcher, false);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pneumaticArcRangeAndSupports(GameTestHelper h) {
        var from = port(new BlockPos(0, 0, 0), Direction.EAST, Kind.LAUNCHER);
        var exact = port(new BlockPos(65, 0, 0), Direction.WEST, Kind.RECEIVER);
        h.assertTrue(span(from, exact).curve().length() == 64 && PneumaticNetwork.makeRoute(List.of(span(from, exact))) != null,
                "An exact 64-block propulsion leg is accepted");
        var distant = port(new BlockPos(29_999_800, 0, 29_999_800), Direction.EAST, Kind.LAUNCHER);
        var distantEnd = port(distant.pos().east(65), Direction.WEST, Kind.RECEIVER);
        h.assertTrue(span(distant, distantEnd).curve().length() == 64,
                "Large world coordinates do not perturb the exact propulsion boundary");
        var beyond = port(new BlockPos(66, 0, 0), Direction.WEST, Kind.RECEIVER);
        h.assertTrue(PneumaticNetwork.makeRoute(List.of(span(from, beyond))) == null, "A 65-block leg rejects one impulse");
        var support = port(new BlockPos(35, 1, 0), Direction.WEST, Kind.SUPPORT);
        var booster = port(new BlockPos(60, 1, 0), Direction.WEST, Kind.BOOSTER);
        var far = port(new BlockPos(120, 1, 0), Direction.WEST, Kind.RECEIVER);
        var supportRoute = List.of(span(from, support), span(opposite(support), far));
        h.assertTrue(PneumaticNetwork.makeRoute(supportRoute) == null, "Supports do not reset propulsion");
        var boosted = PneumaticNetwork.makeRoute(List.of(span(from, booster), span(opposite(booster), far)));
        h.assertTrue(boosted != null && boosted.boosters().size() == 1 && boosted.impulses().getFirst() < 64,
                "A centered booster extends the usable range");
        var early = port(new BlockPos(10, 1, 0), Direction.WEST, Kind.BOOSTER);
        var earlyRoute = List.of(span(from, early), span(opposite(early), support), span(opposite(support), far));
        h.assertTrue(PneumaticNetwork.makeRoute(earlyRoute) == null, "An early booster cannot cover a later overlength leg");
        var curved = span(from, port(support.pos().above(), support.face(), support.kind())).curve();
        h.assertTrue(curved.valid() && curved.at(0).y != curved.at(curved.length()).y, "Curved span supports elevation changes");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void pneumaticReservationsAndPersistence(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos launcher = h.absolutePos(new BlockPos(2, 4, 2)).above(92);
        BlockPos receiver = launcher.east(72);
        BlockPos booster = launcher.east(35).above(4);
        place(level, launcher, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        level.setBlockAndUpdate(booster, ModBlocks.PNEUMATIC_BOOSTER.get().defaultBlockState()
                .setValue(PneumaticStationBlock.AXIS, Direction.Axis.X));
        var network = PneumaticNetwork.get(level);
        link(h, port(launcher, Direction.UP, Kind.LAUNCHER), port(booster, Direction.WEST, Kind.BOOSTER));
        link(h, port(booster, Direction.EAST, Kind.BOOSTER), port(receiver, Direction.UP, Kind.RECEIVER));
        var cargo = cargo();
        network.insertFuel(launcher, 16, false);
        network.insertPackage(level, launcher, cargo, false);
        h.assertTrue(!network.dispatch(level, launcher) && network.fuel(launcher) == 16, "An empty required booster blocks launch");
        network.insertFuel(booster, 1, false);
        h.assertTrue(network.dispatch(level, launcher) && network.fuel(booster) == 0, "Preflight debits the booster exactly once");
        network.insertPackage(level, launcher, cargo, false);
        h.assertTrue(!network.dispatch(level, launcher), "A second dispatch cannot spend the first reserved charge");
        network.insertFuel(booster, 15, false);
        for (int i = 1; i < PneumaticNetwork.RECEIVER_CAPACITY; i++) {
            h.assertTrue(network.dispatch(level, launcher), "A distinct receiver slot is reserved per simultaneous flight");
            network.insertPackage(level, launcher, cargo, false);
        }
        h.assertTrue(!network.dispatch(level, launcher), "Reservations account for the full receiver before any arrival");
        var saved = network.save(new CompoundTag(), level.registryAccess());
        var loaded = PneumaticNetwork.load(saved, level.registryAccess());
        var reencoded = loaded.save(new CompoundTag(), level.registryAccess());
        var flights = flightsFrom(reencoded, launcher);
        h.assertTrue(flights.size() == PneumaticNetwork.RECEIVER_CAPACITY &&
                        ItemStack.matches(ItemStack.parseOptional(level.registryAccess(), flights.getFirst().getCompound("Cargo")), cargo),
                "Active routes and complete package components round-trip without loss");
        level.destroyBlock(receiver.above(), false);
        h.assertTrue(flightsFrom(network.save(new CompoundTag(), level.registryAccess()), launcher).isEmpty(),
                "Broken receiver ends its reservations immediately");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < PneumaticNetwork.RECEIVER_CAPACITY; i++) network.recover(launcher, player);
        long recovered = player.getInventory().items.stream().filter(stack -> ItemStack.matches(stack, cargo)).count();
        h.assertTrue(recovered == PneumaticNetwork.RECEIVER_CAPACITY && ItemStack.matches(network.peek(launcher), cargo),
                "Every accepted flight returns once without touching queued cargo");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pneumaticFlexibleSplineGeometry(GameTestHelper h) {
        Port east = port(BlockPos.ZERO, Direction.EAST, Kind.SUPPORT);
        Port up = port(BlockPos.ZERO, Direction.UP, Kind.SUPPORT);

        var straight = checkCurve(h, east, port(new BlockPos(3, 0, 0), Direction.WEST, Kind.SUPPORT));
        h.assertTrue(straight.length() == 2 && straight.cost() == 2, "Straight connections retain exact arc length and cost");
        checkCurve(h, east, port(new BlockPos(2, 0, 2), Direction.NORTH, Kind.SUPPORT));
        checkCurve(h, east, port(new BlockPos(2, 1, 4), Direction.WEST, Kind.SUPPORT));
        var tight = checkCurve(h, up, port(new BlockPos(1, 0, 0), Direction.UP, Kind.SUPPORT));
        h.assertTrue(tight.length() < 3 && tight.tangent(0).dot(tight.tangent(tight.length())) < -.99,
                "Adjacent top ports form a compact U with opposite travel tangents");
        checkCurve(h, up, port(new BlockPos(1, 30, 1), Direction.DOWN, Kind.SUPPORT));
        var shortBend = checkCurve(h, east, port(new BlockPos(1, 1, 0), Direction.DOWN, Kind.SUPPORT));
        h.assertTrue(shortBend.length() < 1 && shortBend.cost() == 1, "A sub-block perpendicular span costs one tubing");
        checkCurve(h, east, port(new BlockPos(6, 3, 5), Direction.WEST, Kind.SUPPORT));

        Port ordinary = port(new BlockPos(16, 0, 0), Direction.UP, Kind.SUPPORT);
        var ordinaryCurve = checkCurve(h, up, ordinary);
        double legacyLength = legacyArc(up, ordinary);
        h.assertTrue(Math.abs(ordinaryCurve.length() - legacyLength) < 1e-10,
                "Existing ordinary curves keep their chord-third handles and arc length");

        Port touching = port(new BlockPos(1, 0, 0), Direction.WEST, Kind.SUPPORT);
        h.assertTrue(PneumaticPlacement.candidate(east, touching, touching.center()) == null,
                "Overlapping connection faces cannot produce a nonzero span");
        Port away = port(new BlockPos(3, 0, 0), Direction.EAST, Kind.SUPPORT);
        Port west = port(BlockPos.ZERO, Direction.WEST, Kind.SUPPORT);
        var fallback = PneumaticPlacement.candidate(west, away, away.center());
        h.assertTrue(!fallback.valid() && !fallback.tooLong() && fallback.length() > 0
                        && fallback.tangent(fallback.length() / 2).lengthSqr() > .99,
                "A collinear reversal remains invalid and has a finite approximate preview");
        h.assertTrue(PneumaticPlacement.validate(west, away, fallback, port -> true, port -> null, 99)
                        == PneumaticPlacement.Problem.INVALID_CURVE,
                "Server and preview identify the mathematical failure");

        Port far = port(new BlockPos(192, 0, 0), Direction.WEST, Kind.SUPPORT);
        var longPreview = PneumaticPlacement.candidate(east, far, far.center());
        h.assertTrue(longPreview.tooLong() && longPreview.length() > 96 && longPreview.chunks.isEmpty(),
                "An overlength proposal still draws and allocates no chunk index");
        h.assertTrue(PneumaticPlacement.validate(east, far, longPreview, port -> true, port -> null, 999)
                        == PneumaticPlacement.Problem.TOO_LONG,
                "Server and preview identify the span limit");
        h.assertTrue(PneumaticPlacement.validate(east, null, PneumaticPlacement.candidate(east, null, far.center()),
                        port -> true, port -> null, 99) == PneumaticPlacement.Problem.INVALID_ENDPOINT,
                "Invalid endpoint has a safe approximation and a specific rejection");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pneumaticTightPlacementAndDelivery(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos launcher = h.absolutePos(new BlockPos(2, 4, 2)).above(124);
        BlockPos receiver = launcher.east();
        BlockPos other = launcher.west(2);
        place(level, launcher, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        level.setBlockAndUpdate(other, ModBlocks.PNEUMATIC_TUBE_SUPPORT.get().defaultBlockState());
        Port source = port(launcher, Direction.UP, Kind.LAUNCHER);
        Port target = port(receiver, Direction.UP, Kind.RECEIVER);
        Port spare = port(other, Direction.UP, Kind.SUPPORT);
        var curve = new PneumaticSpline(source, target);
        ensureLoaded(level, curve);
        var network = PneumaticNetwork.get(level);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(launcher));
        var tubing = new ItemStack(ModItems.PNEUMATIC_TUBING.get(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, tubing);
        ModItems.PNEUMATIC_TUBING.get().useOn(context(level, player, launcher.above(), Direction.UP));
        ModItems.PNEUMATIC_TUBING.get().useOn(context(level, player, receiver.above(), Direction.UP));
        h.assertTrue(curve.cost() > 1 && tubing.getCount() == 1 && !network.occupied(source)
                        && PneumaticTubingSelection.port(player, InteractionHand.MAIN_HAND).equals(source),
                "Insufficient materials leave the selection, inventory and graph intact");
        tubing.setCount(16);
        ModItems.PNEUMATIC_TUBING.get().useOn(context(level, player, receiver.above(), Direction.UP));
        h.assertTrue(source.equals(network.connectedTo(target)) && tubing.getCount() == 16 - curve.cost(),
                "Both placement clicks build a tight adjacent-station U at the preview cost");
        h.assertTrue(PneumaticPlacement.validate(source, target, curve, port -> true, network::connectedTo, 16)
                        == PneumaticPlacement.Problem.DUPLICATE,
                "Exact duplicate reports already connected");
        var alternateCurve = new PneumaticSpline(spare, target);
        h.assertTrue(PneumaticPlacement.validate(spare, target, alternateCurve, port -> true, network::connectedTo, 16)
                        == PneumaticPlacement.Problem.OCCUPIED,
                "A different span reports the occupied port");
        int beforeOccupied = tubing.getCount();
        ModItems.PNEUMATIC_TUBING.get().useOn(context(level, player, other, Direction.UP));
        ModItems.PNEUMATIC_TUBING.get().useOn(context(level, player, receiver.above(), Direction.UP));
        h.assertTrue(tubing.getCount() == beforeOccupied && !network.occupied(spare) && source.equals(network.connectedTo(target)),
                "A second click on an occupied port consumes nothing and preserves the original link");

        var cargo = cargo();
        var arm = ArmInteractionPoint.create(level, launcher, level.getBlockState(launcher));
        arm.insert(null, cargo, false);
        network.insertFuel(launcher, 1, false);
        motor(level, launcher.below(), 256);
        h.succeedWhen(() -> h.assertTrue(ItemStack.matches(network.peek(receiver), cargo) && network.fuel(launcher) == 0,
                "A powered launcher delivers the unchanged package around the tight U with one charge"));
    }

    private static PneumaticSpline checkCurve(GameTestHelper h, Port from, Port to) {
        var curve = new PneumaticSpline(from, to);
        h.assertTrue(curve.valid() && curve.length() + 1e-8 >= from.center().distanceTo(to.center()) && curve.cost() > 0,
                "A regular curve has finite arc length and cost: " + from + " to " + to);
        h.assertTrue(curve.at(0).distanceToSqr(from.center()) < 1e-14
                        && curve.at(curve.length()).distanceToSqr(to.center()) < 1e-14,
                "Spline endpoints remain at the ports");
        h.assertTrue(curve.tangent(0).dot(Vec3.atLowerCornerOf(from.face().getNormal())) > .999999
                        && curve.tangent(curve.length()).dot(Vec3.atLowerCornerOf(to.face().getOpposite().getNormal())) > .999999,
                "Spline tangents remain aligned with the ports");
        var reverse = new PneumaticSpline(to, from);
        h.assertTrue(reverse.valid() && Math.abs(curve.length() - reverse.length()) < 1e-8,
                "The same arc length applies in either travel direction");
        for (int i = 0; i <= 64; i++) {
            double distance = curve.length() * i / 64;
            Vec3 pos = curve.at(distance);
            Vec3 tangent = curve.tangent(distance);
            h.assertTrue(Double.isFinite(pos.lengthSqr()) && Math.abs(tangent.lengthSqr() - 1) < 1e-8
                            && pos.distanceToSqr(reverse.at(reverse.length() - distance)) < 1e-12,
                    "Arc traversal gives finite positions and matching directions of travel along the same curve");
        }
        return curve;
    }

    private static double legacyArc(Port from, Port to) {
        Vec3 end = to.center().subtract(from.center());
        double handle = end.length() / 3;
        Vec3 a = Vec3.atLowerCornerOf(from.face().getNormal()).scale(handle);
        Vec3 b = end.add(Vec3.atLowerCornerOf(to.face().getNormal()).scale(handle));
        int samples = Math.max(16, (int) Math.ceil(end.length() * 8));
        Vec3 previous = Vec3.ZERO;
        double length = 0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            double s = 1 - t;
            Vec3 point = a.scale(3 * s * s * t).add(b.scale(3 * s * t * t)).add(end.scale(t * t * t));
            length += point.distanceTo(previous);
            previous = point;
        }
        return length;
    }

    @GameTest(template = "empty")
    public static void pneumaticSelectionCancellation(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos first = h.absolutePos(new BlockPos(2, 4, 2)).above(137);
        BlockPos second = first.east();
        place(level, first, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, second, ModBlocks.PNEUMATIC_RECEIVER.get());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(first));
        var item = ModItems.PNEUMATIC_TUBING.get();
        var tubing = new ItemStack(item, 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, tubing);
        item.useOn(context(level, player, first.above(), Direction.UP));
        PneumaticTubingSelection.cancel(player, java.util.UUID.randomUUID());
        h.assertTrue(PneumaticTubingSelection.port(player, InteractionHand.MAIN_HAND) != null,
                "A stale cancellation token cannot clear the current endpoint");
        player.setShiftKeyDown(true);
        player.setPos(Vec3.atCenterOf(first.east(500)));
        h.assertTrue(item.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction()
                && PneumaticTubingSelection.port(player, InteractionHand.MAIN_HAND) == null && tubing.getCount() == 16,
                "Sneak use clears the endpoint from any distance without spending tubing");
        player.setShiftKeyDown(false);
        player.setPos(Vec3.atCenterOf(first));
        item.useOn(context(level, player, first.above(), Direction.UP));
        player.getInventory().setItem(1, new ItemStack(Items.STICK));
        player.getInventory().selected = 1;
        PneumaticTubingSelection.heldItemChanged(new PlayerTickEvent.Post(player));
        player.getInventory().selected = 0;
        h.assertTrue(PneumaticTubingSelection.port(player, InteractionHand.MAIN_HAND) == null,
                "Changing hotbar slot and switching back clears the old endpoint");
        item.useOn(context(level, player, first.above(), Direction.UP));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, tubing);
        PneumaticTubingSelection.heldItemChanged(new PlayerTickEvent.Post(player));
        h.assertTrue(PneumaticTubingSelection.port(player, InteractionHand.OFF_HAND) == null, "An endpoint does not follow a hand swap");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, tubing);
        item.useOn(context(level, player, first.above(), Direction.UP));
        item.useOn(context(level, player, second.above(), Direction.UP));
        var port = port(first, Direction.UP, Kind.LAUNCHER);
        h.assertTrue(PneumaticNetwork.get(level).occupied(port) && tubing.getCount() < 16
                        && PneumaticTubingSelection.port(player, InteractionHand.MAIN_HAND) == null,
                "A fresh endpoint builds normally and clears after construction");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pneumaticLaunchSound(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos source = h.absolutePos(new BlockPos(2, 4, 2)).above(141);
        BlockPos receiver = source.east();
        place(level, source, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(level, receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        var network = PneumaticNetwork.get(level);
        var sounds = new LaunchSounds();
        NeoForge.EVENT_BUS.register(sounds);
        try {
            network.insertPackage(level, source, cargo(), false);
            network.insertFuel(source, 1, false);
            h.assertTrue(!network.dispatch(level, source) && sounds.fwoomp == 0 && sounds.clunk == 1,
                    "Insertion clunks while failed dispatch has no launch sound");
            link(h, port(source, Direction.UP, Kind.LAUNCHER), port(receiver, Direction.UP, Kind.RECEIVER));
            h.assertTrue(network.dispatch(level, source) && sounds.fwoomp == 1 && sounds.clunk == 1,
                    "Exactly one FWOOMP plays when dispatch commits");
        } finally {
            NeoForge.EVENT_BUS.unregister(sounds);
        }
        h.succeed();
    }

    public static final class LaunchSounds {
        int fwoomp;
        int clunk;

        @SubscribeEvent
        public void played(PlayLevelSoundEvent.AtPosition event) {
            var key = BuiltInRegistries.SOUND_EVENT.getKey(event.getSound().value());
            if (key == null) return;
            if (key.toString().equals("create:fwoomp")) fwoomp++;
            if (key.toString().equals("minecraft:block.iron_trapdoor.close")) clunk++;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pneumaticReceiverSimulatesAndRecovers(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos source = h.absolutePos(new BlockPos(2, 4, 2)).above(145);
        BlockPos receiver = receiverSetup(h, source);
        var network = PneumaticNetwork.get(level);
        var empty = PackageItem.containing(List.of());
        var named = new ItemStack(Items.IRON_INGOT, 32);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Pneumatic test cargo"));
        var mixed = PackageItem.containing(List.of(named, new ItemStack(Items.COPPER_INGOT, 16)));
        var spare = cargo();
        ship(h, source, empty);
        ship(h, source, mixed);
        for (int i = 2; i < PneumaticNetwork.RECEIVER_CAPACITY; i++) ship(h, source, spare);
        h.runAfterDelay(5, () -> {
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, receiver, Direction.DOWN);
            h.assertTrue(handler != null && level.getCapability(Capabilities.ItemHandler.BLOCK, receiver.above(), Direction.DOWN) == null
                            && level.getCapability(Capabilities.ItemHandler.BLOCK, receiver, Direction.UP) == null,
                    "Only lower faces provide unpacking access");
            var before = network.save(new CompoundTag(), level.registryAccess());
            for (int i = 0; i < 4; i++)
                h.assertTrue(ItemStack.matches(handler.extractItem(0, 5, true), named.copyWithCount(5)),
                        "Simulation skips an empty package and returns an exact component-preserving copy");
            h.assertTrue(before.equals(network.save(new CompoundTag(), level.registryAccess())),
                    "Simulated extraction changes no saved inventory");
            h.assertTrue(ItemStack.matches(handler.extractItem(0, 5, false), named.copyWithCount(5))
                            && ItemStack.matches(handler.getStackInSlot(0), named.copyWithCount(27)),
                    "Real extraction opens one package and preserves its remaining stack");
            network.insertPackage(level, source, spare, false);
            network.insertFuel(source, 1, false);
            h.assertTrue(!network.dispatch(level, source) && network.fuel(source) == 1,
                    "Partially unpacked output still occupies a reserved receiver slot");
            var take = ArmInteractionPoint.create(level, receiver, level.getBlockState(receiver));
            h.assertTrue(ItemStack.matches(take.extract(null, 0, 1, false), empty), "The arm can still remove unopened packages");
            h.assertTrue(ItemStack.matches(take.extract(null, 0, 1, true), spare), "The arm cannot extract the already unpacked package");
            var saved = network.save(new CompoundTag(), level.registryAccess());
            var restored = PneumaticNetwork.load(saved, level.registryAccess());
            h.assertTrue(ItemStack.matches(restored.receiverOutput(level, receiver, 0, 64, true), named.copyWithCount(27))
                            && restored.receiverOutput(level, receiver, 1, 64, true).getCount() == 16,
                    "Saved output retains all components and distinct buffer slots");
            level.destroyBlock(receiver.above(), false);
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            int recovered = 0;
            while (recovered < 9 && network.recover(receiver, player)) recovered++;
            h.assertTrue(recovered == 8 && !network.recover(receiver, player)
                            && player.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, named.copyWithCount(27)))
                            && player.getInventory().countItem(Items.COPPER_INGOT) == 16,
                    "Breaking the upper half recovers all unopened packages and only the remaining loose contents");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void pneumaticReceiverFeedsCreateChute(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos source = h.absolutePos(new BlockPos(2, 4, 2)).above(149);
        BlockPos receiver = receiverSetup(h, source);
        level.setBlockAndUpdate(receiver.below(2), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(receiver.below(), AllBlocks.CHUTE.getDefaultState());
        ship(h, source, PackageItem.containing(List.of(new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.COPPER_INGOT, 16))));
        h.succeedWhen(() -> {
            var barrel = (net.minecraft.world.Container) level.getBlockEntity(receiver.below(2));
            h.assertTrue(barrel.countItem(Items.IRON_INGOT) == 32 && barrel.countItem(Items.COPPER_INGOT) == 16,
                    "The actual Create Chute carries both loose item types into the barrel");
            for (int i = 0; i < barrel.getContainerSize(); i++)
                h.assertTrue(!PackageItem.isPackage(barrel.getItem(i)), "The Chute never outputs a wrapper");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void pneumaticReceiverFeedsCreateFunnel(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos source = h.absolutePos(new BlockPos(2, 4, 2)).above(153);
        BlockPos receiver = receiverSetup(h, source);
        BlockPos funnel = receiver.south();
        level.setBlockAndUpdate(funnel.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(funnel.south().below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(funnel, AllBlocks.ANDESITE_FUNNEL.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH).setValue(FunnelBlock.EXTRACTING, true));
        ship(h, source, PackageItem.containing(List.of(new ItemStack(Items.IRON_INGOT, 1))));
        h.succeedWhen(() -> h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(funnel).inflate(1.5)).stream()
                .anyMatch(item -> item.getItem().is(Items.IRON_INGOT)),
                "The actual Create Funnel pulls loose contents from a horizontal lower face"));
    }

    private static BlockPos receiverSetup(GameTestHelper h, BlockPos source) {
        BlockPos receiver = source.east();
        place(h.getLevel(), source, ModBlocks.PNEUMATIC_LAUNCHER.get());
        place(h.getLevel(), receiver, ModBlocks.PNEUMATIC_RECEIVER.get());
        link(h, port(source, Direction.UP, Kind.LAUNCHER), port(receiver, Direction.UP, Kind.RECEIVER));
        return receiver;
    }

    private static void ship(GameTestHelper h, BlockPos source, ItemStack stack) {
        var level = h.getLevel();
        var network = PneumaticNetwork.get(level);
        h.assertTrue(network.insertPackage(level, source, stack, false).isEmpty(), "Package loads");
        network.insertFuel(source, 1, false);
        h.assertTrue(network.dispatch(level, source), "Package dispatches");
    }

    private static List<CompoundTag> flightsFrom(CompoundTag snapshot, BlockPos launcher) {
        return snapshot.getList("Transit", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast).filter(flight -> flight.getList("Legs", Tag.TAG_COMPOUND)
                        .getCompound(0).getCompound("From").getLong("Pos") == launcher.asLong()).toList();
    }

    private static void configureArm(ServerLevel level, ArmBlockEntity arm, ListTag points) {
        var tag = arm.saveWithoutMetadata(level.registryAccess());
        tag.put("InteractionPoints", points);
        arm.loadWithComponents(tag, level.registryAccess());
    }

    private static void place(ServerLevel level, BlockPos pos, PneumaticStationBlock block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), block.defaultBlockState().setValue(PneumaticStationBlock.UPPER, true));
    }

    private static void motor(ServerLevel level, BlockPos pos, int rpm) {
        level.setBlockAndUpdate(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        ((CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(rpm);
    }

    private static ItemStack cargo() {
        ItemStack cargo = PackageItem.containing(List.of(new ItemStack(Items.IRON_INGOT, 17)));
        PackageItem.addAddress(cargo, "Dispatch Test");
        return cargo;
    }

    private static Port port(BlockPos pos, Direction face, Kind kind) { return new Port(pos, face, kind); }
    private static Port opposite(Port port) { return new Port(port.pos(), port.face().getOpposite(), port.kind()); }
    private static PneumaticNetwork.Span span(Port from, Port to) {
        return new PneumaticNetwork.Span(from, to, 0, new PneumaticSpline(from, to));
    }

    private static void link(GameTestHelper h, Port from, Port to) {
        var curve = new PneumaticSpline(from, to);
        ensureLoaded(h.getLevel(), curve);
        h.assertTrue(PneumaticNetwork.get(h.getLevel()).connect(h.getLevel(), from, to, curve, 0), "Construct test spline");
    }

    private static void ensureLoaded(ServerLevel level, PneumaticSpline curve) {
        // Test fixtures prepare terrain; the dispatch implementation never requests chunks.
        for (ChunkPos chunk : curve.chunks) level.getChunk(chunk.x, chunk.z);
    }

    private static UseOnContext context(ServerLevel level, net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face) {
        return new UseOnContext(level, player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
}
