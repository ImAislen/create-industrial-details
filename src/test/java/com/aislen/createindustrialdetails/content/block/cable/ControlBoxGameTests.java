package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import com.aislen.createindustrialdetails.client.model.ControlBoxDialPoses;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.joml.Vector3f;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class ControlBoxGameTests {
    private static final VoxelShaper ORIGINAL_OPEN_SHAPES = VoxelShaper.forHorizontal(originalOpenShape(), Direction.SOUTH);

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dialModelSeparation(GameTestHelper helper) throws IOException {
        String path = "/assets/create_industrial_details/models/block/cable_tray/";
        var open = resourceJson(path + "control_box_open.json");
        var elements = open.getAsJsonArray("elements");
        helper.assertTrue(elements.size() == 26, "Only the twenty dial cuboids are removed from the static open model");
        for (var element : elements)
            helper.assertTrue(!element.getAsJsonObject().get("name").getAsString().equals("octagon"), "No stationary dial geometry remains");
        String[] channels = {"red", "yellow", "green", "blue"};
        for (int i = 0; i < channels.length; i++) {
            var partial = resourceJson(path + "control_box_dial_" + channels[i] + ".json");
            helper.assertTrue(partial.get("textures").equals(open.get("textures")), "Dial partial uses the original shared texture");
            helper.assertTrue(partial.getAsJsonArray("elements").size() == 5, "Each partial contains exactly its original five dial cuboids");
            for (var element : partial.getAsJsonArray("elements")) {
                var cube = element.getAsJsonObject();
                var origin = cube.getAsJsonObject("rotation").getAsJsonArray("origin");
                helper.assertTrue(origin.get(0).getAsDouble() == 5.125 + 2 * i
                        && origin.get(1).getAsDouble() == 8.375 && origin.get(2).getAsDouble() == 4.25,
                        "Each dial retains its model-defined center");
                helper.assertTrue(cube.get("name").getAsString().equals("octagon") && cube.getAsJsonObject("faces").size() == 6,
                        "Dial cuboids retain all six textured faces");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dialRotationPoses(GameTestHelper helper) {
        helper.assertTrue(ControlBoxDialPoses.angle(0) == -135 && ControlBoxDialPoses.angle(5) == -45
                && ControlBoxDialPoses.angle(10) == 45 && ControlBoxDialPoses.angle(15) == 135, "Required 270-degree endpoints and intermediate values");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (ControlChannel channel : ControlChannel.values()) {
                float x = (5.125f + 2 * channel.ordinal()) / 16;
                Vec3 pivot = new Vec3(x, 8.375 / 16, 4.25 / 16);
                var unique = new java.util.HashSet<String>();
                for (int value = 0; value < 16; value++) {
                    var pose = ControlBoxDialPoses.pose(facing, channel, value);
                    helper.assertTrue(pose == ControlBoxDialPoses.pose(facing, channel, value), "All dial transforms are cached");
                    var fixed = pose.transformPosition(new Vector3f((float) pivot.x, (float) pivot.y, (float) pivot.z));
                    Vec3 expectedPivot = ControlBoxGeometry.fromModel(facing, pivot);
                    helper.assertTrue(new Vec3(fixed.x, fixed.y, fixed.z).distanceTo(expectedPivot) < 1e-6, "Rotation leaves each dial center fixed in all facings");
                    var marker = pose.transformPosition(new Vector3f(x, 8.625f / 16, 4.5f / 16));
                    double angle = Math.toRadians(-135 + value * 18);
                    Vec3 expected = ControlBoxGeometry.fromModel(facing,
                            new Vec3(x + Math.sin(angle) * .25 / 16, (8.375 + Math.cos(angle) * .25) / 16, 4.5 / 16));
                    helper.assertTrue(new Vec3(marker.x, marker.y, marker.z).distanceTo(expected) < 1e-6,
                            "Increasing values move the original top marker clockwise as seen from the front");
                    unique.add(marker.toString());
                    var normal = ControlBoxDialPoses.normal(facing, channel, value).transform(new Vector3f(0, 0, 1));
                    helper.assertTrue(normal.distance(facing.getStepX(), 0, facing.getStepZ()) < 1e-6, "Dial normals stay perpendicular to the panel");
                }
                helper.assertTrue(unique.size() == 16, "Each dial has sixteen distinct marker positions");
            }
        }
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var state = ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.OPEN, true);
        level.setBlockAndUpdate(pos, state);
        var box = box(helper, pos);
        for (ControlChannel channel : ControlChannel.values()) box.setSignal(channel, channel.ordinal() * 5);
        var loaded = new ControlBoxBlockEntity(pos, state);
        loaded.loadWithComponents(box.getUpdateTag(level.registryAccess()), level.registryAccess());
        for (ControlChannel channel : ControlChannel.values())
            helper.assertTrue(ControlBoxDialPoses.angle(loaded.getSignal(channel)) == -135 + channel.ordinal() * 90,
                    "Synchronized packed values directly determine independent dial angles after reload");
        box.setSignal(ControlChannel.GREEN, 7);
        loaded.loadWithComponents(box.getUpdateTag(level.registryAccess()), level.registryAccess());
        helper.assertTrue(ControlBoxDialPoses.angle(loaded.getSignal(ControlChannel.GREEN)) == -9
                && loaded.getSignal(ControlChannel.RED) == 0 && loaded.getSignal(ControlChannel.BLUE) == 15,
                "Value synchronization changes only the selected dial without reopening the door");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void canonicalModelResources(GameTestHelper helper) throws IOException {
        String assets = "/assets/create_industrial_details/";
        String modelPath = "models/block/cable_tray/";
        var variants = resourceJson(assets + "blockstates/control_box.json").getAsJsonObject("variants");
        helper.assertTrue(variants.size() == 8, "Exactly four facing directions and two door models are defined");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int rotation = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                default -> 270;
            };
            for (boolean open : new boolean[]{false, true}) {
                var variant = variants.getAsJsonObject("facing=" + facing.getSerializedName() + ",open=" + open);
                String name = "control_box_" + (open ? "open" : "closed");
                helper.assertTrue(variant.get("model").getAsString().equals("create_industrial_details:block/cable_tray/" + name)
                        && (variant.has("y") ? variant.get("y").getAsInt() : 0) == rotation,
                        "Every state references the original model with its correct facing rotation");
                var textures = resourceJson(assets + modelPath + name + ".json").getAsJsonObject("textures");
                helper.assertTrue(textures.get("0").getAsString().equals("create_industrial_details:block/cable_tray/control_box")
                        && textures.get("particle").equals(textures.get("0")), "Original models resolve the canonical texture directly");
            }
        }
        helper.assertTrue(resourceJson(assets + "models/item/control_box.json").get("parent").getAsString()
                .equals("create_industrial_details:block/cable_tray/control_box_closed"), "Inventory uses the original closed model directly");
        for (String obsolete : new String[]{modelPath + "industrial_control_box_closed.json", modelPath + "industrial_control_box_open.json",
                "textures/block/cable_tray/industrial_control_box.png"})
            helper.assertTrue(ControlBoxGameTests.class.getResource(assets + obsolete) == null, "No redundant industrial-prefixed model or texture remains");
        helper.assertTrue(ControlBoxGameTests.class.getResource(assets + "textures/block/cable_tray/control_box.png") != null,
                "Original Control Box texture is present");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void canonicalRegistrationAndLegacyData(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        var current = ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "control_box");
        var previous = ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "industrial_control_box");
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(block).equals(current)
                && BuiltInRegistries.BLOCK.get(previous) == block && !BuiltInRegistries.BLOCK.keySet().contains(previous),
                "Control Box is canonical and the previous block ID is an alias only");
        helper.assertTrue(BuiltInRegistries.ITEM.get(previous) == block.asItem(), "Existing inventory items retain their meaning");
        var state = block.defaultBlockState().setValue(ControlBoxBlock.FACING, Direction.WEST).setValue(ControlBoxBlock.OPEN, true);
        level.setBlockAndUpdate(pos, state);
        var box = box(helper, pos);
        box.setSignal(ControlChannel.RED, 13);
        box.setSignal(ControlChannel.GREEN, 7);
        var tag = box.saveWithFullMetadata(level.registryAccess());
        tag.putString("id", previous.toString());
        var stateTag = NbtUtils.writeBlockState(state);
        stateTag.putString("Name", previous.toString());
        var restoredState = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
        var restored = (ControlBoxBlockEntity) BlockEntity.loadStatic(pos, restoredState, tag, level.registryAccess());
        helper.assertTrue(restoredState == state && restored.getSignal(ControlChannel.RED) == 13
                && restored.getSignal(ControlChannel.GREEN) == 7, "Legacy block and entity NBT retain state and all values");
        var drop = Block.getDrops(state, level, pos, box).getFirst();
        var itemTag = (net.minecraft.nbt.CompoundTag) drop.saveOptional(level.registryAccess());
        itemTag.putString("id", previous.toString());
        var components = itemTag.getCompound("components");
        String newComponent = CreateIndustrialDetails.MOD_ID + ":control_box_signals";
        var values = components.get(newComponent);
        components.remove(newComponent);
        components.put(CreateIndustrialDetails.MOD_ID + ":industrial_control_box_signals", values);
        var loaded = ItemStack.parseOptional(level.registryAccess(), itemTag);
        helper.assertTrue(ItemStack.isSameItemSameComponents(drop, loaded), "Legacy item component aliases preserve configured pickup data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void preciseDoorAndCabinetInteraction(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(ControlBoxBlock.FACING, facing).setValue(ControlBoxBlock.OPEN, true);
            level.setBlockAndUpdate(pos, state);
            var shape = ControlBoxGeometry.doorShape(facing);
            helper.assertTrue(shape == ControlBoxGeometry.doorShape(facing), "Door approximations are cached per orientation");
            helper.assertTrue(!Shapes.joinIsNotEmpty(state.getShape(level, pos), ORIGINAL_OPEN_SHAPES.get(facing), BooleanOp.NOT_SAME),
                    "Collision and selection exactly restore the original eight-strip enclosure and handle shape");
            var cabinet = hit(pos, facing, 8, 14);
            Vec3 front = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 eye = cabinet.getLocation().add(front.scale(2));
            player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
            helper.assertTrue(state.useWithoutItem(level, player, cabinet) == InteractionResult.PASS
                    && level.getBlockState(pos) == state, "Cabinet clicks leave the open door unchanged");
            for (ControlChannel channel : ControlChannel.values()) {
                var control = hit(pos, facing, 5.125 + 2 * channel.ordinal(), 8.375);
                Vec3 start = control.getLocation().add(front.scale(2));
                var selected = state.getShape(level, pos).clip(start, control.getLocation().subtract(front), pos);
                helper.assertTrue(selected != null && ControlBoxBlock.controlAt(state, pos, selected) == channel,
                        "Restored hitbox leaves every control directly accessible");
                helper.assertTrue(!ControlBoxGeometry.hitsDoor(state, pos, start, control.getLocation().subtract(front)),
                        "Control rays cannot be mistaken for door rays");
                state.useWithoutItem(level, player, control);
                helper.assertTrue(level.getBlockState(pos) == state, "Control clicks never close the door");
            }
            Vec3 empty = ControlBoxGeometry.fromModel(facing, new Vec3(21 / 16.0, .5, .8)).add(Vec3.atLowerCornerOf(pos));
            helper.assertTrue(!ControlBoxGeometry.hitsDoor(state, pos, empty.add(front.scale(2)), empty.subtract(front)),
                    "Empty space surrounding the door is not interactive");
            double angle = Math.PI / 8;
            double x = 18.125 - 18.0625, z = 10 - 12.9375;
            Vec3 local = new Vec3((18.0625 + Math.cos(angle) * x + Math.sin(angle) * z) / 16, .5,
                    (12.9375 - Math.sin(angle) * x + Math.cos(angle) * z) / 16);
            Vec3 surface = ControlBoxGeometry.fromModel(facing, local).add(Vec3.atLowerCornerOf(pos));
            Vec3 normal = ControlBoxGeometry.fromModel(facing, new Vec3(Math.cos(angle), 0, -Math.sin(angle)))
                    .subtract(ControlBoxGeometry.fromModel(facing, Vec3.ZERO));
            Vec3 start = surface.add(normal.scale(2));
            var exactHit = ControlBoxBlock.traceDoor(state, pos, start, surface.subtract(normal));
            helper.assertTrue(exactHit != null && exactHit.getLocation().distanceTo(surface) < 1e-6,
                    "Exact oriented door ray tracing works beyond the block boundary in all orientations");
            player.setPos(start.x, start.y - player.getEyeHeight(), start.z);
            state.useWithoutItem(level, player, exactHit);
            helper.assertTrue(!level.getBlockState(pos).getValue(ControlBoxBlock.OPEN), "Only the actual door ray closes it");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placementAndShapes(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(block.getStateDefinition().getPossibleStates().size() == 8, "Only four facing directions and two door states");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var placement = new BlockPlaceContext(new UseOnContext(level, player, InteractionHand.MAIN_HAND,
                    new ItemStack(block), new BlockHitResult(Vec3.atCenterOf(pos), facing, pos, false)));
            var state = block.getStateForPlacement(placement);
            helper.assertTrue(state.getValue(ControlBoxBlock.FACING) == facing
                    && !state.getValue(ControlBoxBlock.OPEN), "Box faces away from clicked mounting surface and starts closed");
            level.setBlockAndUpdate(pos, state);
            var box = box(helper, pos);
            helper.assertTrue(box.getAllBehaviours().stream().noneMatch(b -> b instanceof ScrollValueBehaviour),
                    "Controls use Value Settings without ScrollValueRenderer's permanent number boxes");
            for (Direction side : Direction.values())
                helper.assertTrue(box.canConnectCable(side) == (side.getAxis().isVertical() || side == facing.getOpposite()),
                        "Exactly top, bottom and back are valid for every facing");
            helper.assertTrue(box.getType() == ModBlockEntities.CONTROL_BOX.get()
                    && block.getTicker(level, state, box.getType()) == null, "Registered block entity has no ticker");
            for (boolean open : new boolean[]{false, true}) {
                var variant = state.setValue(ControlBoxBlock.OPEN, open);
                var shape = variant.getShape(level, pos);
                helper.assertTrue(shape == variant.getShape(level, pos)
                        && shape == variant.getCollisionShape(level, pos), "Both shapes use the same cached orientation");
                helper.assertTrue(!variant.isCollisionShapeFullBlock(level, pos) && !variant.canOcclude(), "Partial enclosure keeps correct non-full shape and occlusion");
                var saved = NbtUtils.writeBlockState(variant);
                helper.assertTrue(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), saved) == variant, "Facing and door state survive serialization");
            }
            helper.assertTrue(state.canSurvive(level, pos), "No mounting support dependency");
            var south = block.defaultBlockState();
            helper.assertTrue(south.rotate(Rotation.CLOCKWISE_90).getValue(ControlBoxBlock.FACING) == Direction.WEST,
                    "Vanilla rotation preserves south-facing model convention");
        }
        var south = block.defaultBlockState();
        helper.assertTrue(south.getShape(level, pos).bounds().maxZ == 5.75 / 16,
                "Closed enclosure includes supplied handle depth");
        var openBounds = south.setValue(ControlBoxBlock.OPEN, true).getShape(level, pos).bounds();
        helper.assertTrue(Math.abs(openBounds.maxX - 19.57922305667386 / 16) < .00001
                && Math.abs(openBounds.maxZ - 16.627214719359007 / 16) < .00001,
                "Open collision accounts for supplied out-of-block door bounds");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void controlsDoorAndWrenchPriority(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var closed = block.defaultBlockState().setValue(ControlBoxBlock.FACING, facing);
            level.setBlockAndUpdate(pos, closed);
            var firstHit = hit(pos, facing, 5.125, 8.375);
            helper.assertTrue(ControlBoxBlock.controlAt(closed, pos, firstHit) == null, "Closed door conceals controls");
            closed.useWithoutItem(level, player, firstHit);
            helper.assertTrue(level.getBlockState(pos).getValue(ControlBoxBlock.OPEN), "Closed door opens before controls can be changed");
            var box = box(helper, pos);
            for (ControlChannel channel : ControlChannel.values()) {
                int index = channel.ordinal();
                var controlHit = hit(pos, facing, 5.125 + index * 2, 8.375);
                var open = level.getBlockState(pos);
                helper.assertTrue(ControlBoxBlock.controlAt(open, pos, controlHit) == channel, "Dial labels use fixed red/yellow/green/blue mapping in every orientation");
                open.useWithoutItem(level, player, controlHit);
                helper.assertTrue(box.getSignal(channel) == 0, "Short control clicks no longer increment values");
                new TestValuePacket(pos, index, 99).apply(box);
                helper.assertTrue(box.getSignal(channel) == 15 && level.getBlockState(pos).getValue(ControlBoxBlock.OPEN),
                        "Native Value Settings packet clamps channel to 15 without closing door");
                new TestValuePacket(pos, index, -2).apply(box);
                helper.assertTrue(box.getSignal(channel) == 0, "Value Settings clamps channel to zero");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            var open = level.getBlockState(pos);
            helper.assertTrue(!open.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, firstHit).consumesAction()
                    && open.useWithoutItem(level, player, firstHit) == InteractionResult.PASS
                    && level.getBlockState(pos) == open, "Wrench interactions never toggle doors or controls");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            open.useWithoutItem(level, player, hit(pos, facing, 8, 14));
            helper.assertTrue(level.getBlockState(pos).getValue(ControlBoxBlock.OPEN), "Cabinet body no longer closes the door");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeValueSettings(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(ControlBoxBlock.FACING, facing).setValue(ControlBoxBlock.OPEN, true);
            level.setBlockAndUpdate(pos, state);
            var box = box(helper, pos);
            var types = new java.util.HashSet<>();
            var ids = new java.util.HashSet<Integer>();
            var clipboard = new java.util.HashSet<String>();
            for (ControlChannel channel : ControlChannel.values()) {
                var behaviour = box.getControl(channel);
                helper.assertTrue(types.add(behaviour.getType()) && ids.add(behaviour.netId()) && clipboard.add(behaviour.getClipboardKey()),
                        "Each control has an independent native behaviour type, packet ID and clipboard key");
                var target = hit(pos, facing, 5.125 + channel.ordinal() * 2, 8.375);
                helper.assertTrue(behaviour.isActive() && behaviour.testHit(target.getLocation()), "Native hit testing matches the model's dial");
                for (ControlChannel other : ControlChannel.values())
                    if (other != channel) helper.assertTrue(!box.getControl(other).testHit(target.getLocation()), "Dial regions do not overlap");
                var slot = behaviour.getSlotPositioning();
                var offset = slot.getLocalOffset(level, pos, state).add(Vec3.atLowerCornerOf(pos));
                helper.assertTrue(behaviour.testHit(offset) && slot.shouldRender(level, pos, state), "Native overlay anchor follows the rotated front");
                var board = behaviour.createBoard(player, target);
                helper.assertTrue(board.maxValue() == 15 && board.rows().size() == 1, "Native Value Settings board exposes one 0-15 range");
                new TestValuePacket(pos, behaviour.netId(), channel.ordinal() + 2).apply(box);
                helper.assertTrue(behaviour.getValueSettings().value() == channel.ordinal() + 2,
                        "Native packets route values to the selected channel");
                helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && behaviour.mayInteract(player),
                        "Native Value Settings remains available without goggles");
                player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
                helper.assertTrue(behaviour.bypassesInput(player.getMainHandItem()) && !behaviour.mayInteract(player), "Value Settings preserves wrench priority");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            level.setBlock(pos, state.setValue(ControlBoxBlock.OPEN, false), Block.UPDATE_CLIENTS);
            int old = box.getSignal(ControlChannel.RED);
            new TestValuePacket(pos, 0, 15).apply(box);
            helper.assertTrue(!box.getControl(ControlChannel.RED).isActive() && box.getSignal(ControlChannel.RED) == old,
                    "Closed door rejects late Value Settings confirmations");
            var saved = box.saveWithFullMetadata(level.registryAccess());
            helper.assertTrue(!saved.contains("ScrollValue") && saved.contains("Signals"), "Packed integer remains the only channel storage");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void targetedGoggleInspection(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, facing)
                    .setValue(ControlBoxBlock.OPEN, true);
            level.setBlockAndUpdate(pos, state);
            var box = box(helper, pos);
            for (ControlChannel channel : ControlChannel.values()) box.setSignal(channel, channel.ordinal() + 7);
            for (ControlChannel channel : ControlChannel.values()) {
                var target = hit(pos, facing, 5.125 + 2 * channel.ordinal(), 8.375);
                var tooltip = new ArrayList<Component>();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                helper.assertTrue(!box.addToGoggleTooltip(tooltip, player, target) && tooltip.isEmpty(),
                        "No passive value inspection without goggles");
                helper.assertTrue(box.getControl(channel).mayInteract(player), "Goggles never gate native value adjustment");
                player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
                helper.assertTrue(box.addToGoggleTooltip(tooltip, player, target) && tooltip.size() == 1,
                        "Goggles expose exactly the targeted channel");
                String expected = Component.translatable("create_industrial_details.control_box.value",
                        Component.translatable(channel.translationKey()), box.getSignal(channel)).getString();
                helper.assertTrue(tooltip.getFirst().getString().strip().equals(expected), "Compact tooltip matches the targeted channel and current value");
                tooltip.clear();
                helper.assertTrue(!box.addToGoggleTooltip(tooltip, player, hit(pos, facing, 8, 14)) && tooltip.isEmpty(),
                        "Cabinet/interior targeting produces no channel tooltip");
                level.setBlock(pos, state.setValue(ControlBoxBlock.OPEN, false), Block.UPDATE_CLIENTS);
                helper.assertTrue(!box.addToGoggleTooltip(tooltip, player, target) && tooltip.isEmpty(), "Closed door hides passive inspection");
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                helper.assertTrue(!box.addToGoggleTooltip(tooltip, player, target) && tooltip.isEmpty(), "Removing goggles immediately removes inspection eligibility");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void comparatorMaximumAndNotifications(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean open : new boolean[]{false, true}) {
                var state = block.defaultBlockState().setValue(ControlBoxBlock.FACING, facing).setValue(ControlBoxBlock.OPEN, open);
                level.setBlockAndUpdate(pos, state);
                var box = new CountingControlBox(pos, state);
                level.setBlockEntity(box);
                for (int[] values : new int[][]{{0, 0, 0, 0}, {2, 4, 6, 8}, {15, 0, 0, 0}, {7, 7, 7, 7}, {0, 3, 9, 15}}) {
                    for (ControlChannel channel : ControlChannel.values()) box.setSignal(channel, values[channel.ordinal()]);
                    int expected = java.util.Arrays.stream(values).max().orElseThrow();
                    helper.assertTrue(state.hasAnalogOutputSignal() && state.getAnalogOutputSignal(level, pos) == expected,
                            "Comparator reads maximum independently of facing, door state or cables");
                    for (Direction side : Direction.values())
                        helper.assertTrue(!state.isSignalSource() && state.getSignal(level, pos, side) == 0
                                && state.getDirectSignal(level, pos, side) == 0, "Analog support never emits ordinary redstone power");
                }
                for (ControlChannel channel : ControlChannel.values()) box.setSignal(channel, new int[]{3, 5, 8, 10}[channel.ordinal()]);
                box.comparatorUpdates = 0;
                box.setSignal(ControlChannel.YELLOW, 7);
                box.setSignal(ControlChannel.RED, 3);
                helper.assertTrue(box.comparatorUpdates == 0, "Changes below maximum and identical assignments issue no analog notifications");
                box.setSignal(ControlChannel.BLUE, 12);
                helper.assertTrue(box.comparatorUpdates == 1 && state.getAnalogOutputSignal(level, pos) == 12,
                        "Increasing maximum issues one analog notification");
                box.setSignal(ControlChannel.BLUE, 6);
                helper.assertTrue(box.comparatorUpdates == 2 && state.getAnalogOutputSignal(level, pos) == 8,
                        "Decreasing maximum exposes the next-highest channel");
                var item = new ItemStack(block);
                item.set(ModDataComponents.CONTROL_BOX_SIGNALS.get(), 0x7777);
                box.applyComponentsFromItemStack(item);
                helper.assertTrue(box.comparatorUpdates == 3 && state.getAnalogOutputSignal(level, pos) == 7,
                        "Configured item restoration refreshes analog output");
                var saved = box.saveWithFullMetadata(level.registryAccess());
                saved.putInt("Signals", 0x3333);
                box.loadWithComponents(saved, level.registryAccess());
                helper.assertTrue(box.comparatorUpdates == 4 && state.getAnalogOutputSignal(level, pos) == 3,
                        "Restored world data refreshes analog output when changed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placedComparatorLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        var state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos comparator = pos.relative(side);
            level.setBlockAndUpdate(comparator.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(comparator, Blocks.COMPARATOR.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, side.getOpposite()));
        }
        box(helper, pos).setSignal(ControlChannel.BLUE, 10);
        helper.runAtTickTime(8, () -> {
            assertComparators(helper, pos, 10);
            box(helper, pos).setSignal(ControlChannel.YELLOW, 7);
            for (Direction side : Direction.Plane.HORIZONTAL)
                helper.assertTrue(!level.getBlockTicks().hasScheduledTick(pos.relative(side), Blocks.COMPARATOR),
                        "Lower channel changes leave stable comparators idle, including the back port");
            box(helper, pos).setSignal(ControlChannel.BLUE, 12);
        });
        helper.runAtTickTime(12, () -> {
            assertComparators(helper, pos, 12);
            level.setBlock(pos, state.setValue(ControlBoxBlock.OPEN, true), Block.UPDATE_CLIENTS);
            helper.assertTrue(level.getBlockState(pos).getAnalogOutputSignal(level, pos) == 12, "Door changes leave analog reading intact");
            level.destroyBlock(pos, false);
        });
        helper.runAtTickTime(16, () -> {
            assertComparators(helper, pos, 0);
            ItemStack item = new ItemStack(block);
            item.set(ModDataComponents.CONTROL_BOX_SIGNALS.get(), 0x8642);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            var context = new BlockPlaceContext(new UseOnContext(level, player, InteractionHand.MAIN_HAND, item,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false)));
            helper.assertTrue(((BlockItem) item.getItem()).place(context).consumesAction(), "Configured box replaces normally beside existing comparators");
        });
        helper.runAtTickTime(20, () -> {
            assertComparators(helper, pos, 8);
            var restored = box(helper, pos).saveWithFullMetadata(level.registryAccess());
            restored.putInt("Signals", 0x7777);
            box(helper, pos).loadWithComponents(restored, level.registryAccess());
        });
        helper.runAtTickTime(24, () -> {
            assertComparators(helper, pos, 7);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        });
        helper.runAtTickTime(28, () -> {
            assertComparators(helper, pos, 0);
            helper.succeed();
        });
    }

    private static void assertComparators(GameTestHelper helper, BlockPos pos, int expected) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var comparator = (ComparatorBlockEntity) helper.getLevel().getBlockEntity(pos.relative(side));
            helper.assertTrue(comparator != null && comparator.getOutputSignal() == expected,
                    "Placed comparator on " + side + " reads " + expected);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void persistenceAndThreePorts(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var state = ModBlocks.CONTROL_BOX.get().defaultBlockState().setValue(ControlBoxBlock.FACING, Direction.EAST)
                .setValue(ControlBoxBlock.OPEN, true);
        level.setBlockAndUpdate(pos, state);
        var box = box(helper, pos);
        int value = 1;
        for (ControlChannel channel : ControlChannel.values()) box.setSignal(channel, value++);
        helper.assertTrue(box.getUpdateTag(level.registryAccess()).getInt("Signals") == 0x4321 && box.getUpdatePacket() != null,
                "Four values use one packed integer and standard block entity synchronization");
        helper.assertTrue(!box.setSignal(ControlChannel.RED, 1), "Unchanged values issue no update");
        for (Direction side : Direction.values()) {
            value = 1;
            for (ControlChannel channel : ControlChannel.values())
                helper.assertTrue(box.getSignal(channel, side) == ((side == Direction.WEST || side.getAxis().isVertical()) ? value++ : 0), "Only top, bottom and back expose the same configured source values");
        }
        var saved = box.saveWithFullMetadata(level.registryAccess());
        var loaded = (ControlBoxBlockEntity) BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
        value = 1;
        for (ControlChannel channel : ControlChannel.values())
            helper.assertTrue(loaded.getSignal(channel) == value++, "NBT reload preserves all channels independently");
        box.setRemoved();
        helper.assertTrue(box.getSignal(ControlChannel.RED, Direction.WEST) == 0 && !box.setSignal(ControlChannel.RED, 12),
                "Removed endpoints cannot supply or mutate signals");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void configuredDropsAndPickup(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = ModBlocks.CONTROL_BOX.get();
        var state = block.defaultBlockState().setValue(ControlBoxBlock.OPEN, true);
        level.setBlockAndUpdate(pos, state);
        var box = box(helper, pos);
        box.setSignal(ControlChannel.RED, 15);
        box.setSignal(ControlChannel.BLUE, 12);
        int packed = 0xC00F;
        var drops = Block.getDrops(state, level, pos, box);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getCount() == 1
                && drops.getFirst().getOrDefault(ModDataComponents.CONTROL_BOX_SIGNALS.get(), 0) == packed,
                "Loot preserves four-channel configuration on exactly one item");
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.setShiftKeyDown(true);
        survival.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        AllItems.WRENCH.get().useOn(context(survival, pos, Direction.UP));
        helper.assertTrue(level.getBlockState(pos).isAir() && survival.getInventory().countItem(block.asItem()) == 1,
                "Sneak wrench returns exactly one configured box");
        ItemStack pickedUp = survival.getInventory().items.stream().filter(stack -> stack.is(block.asItem())).findFirst().orElseThrow();
        helper.assertTrue(pickedUp.getOrDefault(ModDataComponents.CONTROL_BOX_SIGNALS.get(), 0) == packed,
                "Wrench pickup retains all signals");
        ItemStack placing = pickedUp.copy();
        survival.setShiftKeyDown(false);
        survival.setItemInHand(InteractionHand.MAIN_HAND, placing);
        var placement = new BlockPlaceContext(new UseOnContext(level, survival, InteractionHand.MAIN_HAND, placing,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false)));
        helper.assertTrue(((BlockItem) placing.getItem()).place(placement).consumesAction(), "Configured item can be placed normally");
        helper.assertTrue(!level.getBlockState(pos).getValue(ControlBoxBlock.OPEN)
                && box(helper, pos).getSignal(ControlChannel.RED) == 15 && box(helper, pos).getSignal(ControlChannel.BLUE) == 12,
                "Replaced box starts closed and restores its configuration");
        survival.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        AllItems.WRENCH.get().useOn(context(survival, pos, Direction.UP));
        helper.assertTrue(level.getBlockState(pos).getValue(ControlBoxBlock.FACING) == Direction.WEST
                && box(helper, pos).getSignal(ControlChannel.BLUE) == 12, "Normal wrench rotation preserves configured signals");
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setShiftKeyDown(true);
        creative.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        AllItems.WRENCH.get().useOn(context(creative, pos, Direction.UP));
        helper.assertTrue(level.getBlockState(pos).isAir() && creative.getInventory().countItem(block.asItem()) == 0
                && level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Creative wrench removal does not duplicate items");
        level.setBlockAndUpdate(pos, state);
        box(helper, pos).setSignal(ControlChannel.RED, 15);
        box(helper, pos).setSignal(ControlChannel.BLUE, 12);
        level.destroyBlock(pos, true);
        var brokenDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        helper.assertTrue(brokenDrops.size() == 1 && brokenDrops.getFirst().getItem().getCount() == 1
                && brokenDrops.getFirst().getItem().getOrDefault(ModDataComponents.CONTROL_BOX_SIGNALS.get(), 0) == packed,
                "Ordinary destruction drops exactly one configured box");
        helper.succeed();
    }

    private static ControlBoxBlockEntity box(GameTestHelper helper, BlockPos pos) {
        return (ControlBoxBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static VoxelShape originalOpenShape() {
        VoxelShape shape = Shapes.or(Block.box(2, 0, 0, 14, 16, 4), Block.box(2, 0, 4, 3, 16, 5),
                Block.box(13, 0, 4, 14, 16, 5), Block.box(3, 0, 4, 13, 1, 5), Block.box(3, 15, 4, 13, 16, 5),
                Block.box(3, 6.5, 4, 13, 13.25, 4.5));
        for (int strip = 0; strip < 8; strip++) {
            double z1 = 3.75 + 13.0 * strip / 8;
            double z2 = 3.75 + 13.0 * (strip + 1) / 8;
            double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (double x : new double[]{17.625, 18.125})
                for (double z : new double[]{z1, z2}) {
                    double dx = x - 18.0625, dz = z - 12.9375;
                    double rotatedX = 18.0625 + Math.cos(Math.PI / 8) * dx + Math.sin(Math.PI / 8) * dz;
                    double rotatedZ = 12.9375 - Math.sin(Math.PI / 8) * dx + Math.cos(Math.PI / 8) * dz;
                    minX = Math.min(minX, rotatedX); maxX = Math.max(maxX, rotatedX);
                    minZ = Math.min(minZ, rotatedZ); maxZ = Math.max(maxZ, rotatedZ);
                }
            shape = Shapes.or(shape, Block.box(minX, 0, minZ, maxX, 16, maxZ));
        }
        return Shapes.or(shape, Block.box(19.0052, 6, 14.9543, 19.5232, 10, 15.743)).optimize();
    }

    private static JsonObject resourceJson(String path) throws IOException {
        var stream = ControlBoxGameTests.class.getResourceAsStream(path);
        if (stream == null) throw new IOException("Missing resource: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static UseOnContext context(Player player, BlockPos pos, Direction face) {
        return new UseOnContext(player.level(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }

    private static BlockHitResult hit(BlockPos pos, Direction facing, double pixelX, double pixelY) {
        double x = pixelX / 16, z = 4.5 / 16;
        Vec3 point = switch (facing) {
            case SOUTH -> new Vec3(x, pixelY / 16, z);
            case NORTH -> new Vec3(1 - x, pixelY / 16, 1 - z);
            case EAST -> new Vec3(z, pixelY / 16, 1 - x);
            case WEST -> new Vec3(1 - z, pixelY / 16, x);
            default -> throw new IllegalArgumentException();
        };
        return new BlockHitResult(point.add(pos.getX(), pos.getY(), pos.getZ()), facing, pos, false);
    }
    private static class CountingControlBox extends ControlBoxBlockEntity {
        private int comparatorUpdates;

        private CountingControlBox(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        void notifyComparators() {
            comparatorUpdates++;
            super.notifyComparators();
        }
    }

    private static class TestValuePacket extends ValueSettingsPacket {
        private TestValuePacket(BlockPos pos, int id, int value) {
            super(pos, 0, value, null, null, Direction.UP, false, id);
        }

        private void apply(ControlBoxBlockEntity box) {
            super.applySettings(null, box);
        }
    }

}
