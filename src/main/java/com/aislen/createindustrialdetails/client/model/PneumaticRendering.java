package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.pneumatic.*;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModItems;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import com.simibubi.create.AllPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.joml.Quaternionf;

import java.util.*;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class PneumaticRendering {
    private static final ModelResourceLocation TUBE = model("pneumatic_tube_resolved");
    private static final ModelResourceLocation CAPSULE = model("pneumatic_capsule_resolved");
    private static final Map<Long, Set<CompoundTag>> CHUNKS = new HashMap<>();
    private static final Map<CompoundTag, TubeView> TUBES = new HashMap<>();
    private static final Map<PneumaticSpline.Port, TubeView> PORTS = new HashMap<>();
    private static final Map<UUID, PneumaticNetwork.Flight> FLIGHTS = new HashMap<>();
    private static final Map<Long, Map<BlockPos, PneumaticNetwork.ReceiverDisplay>> RECEIVERS = new HashMap<>();
    private static ClientLevel level;
    private static List<BakedQuad> tubeTemplate = List.of();
    private static double[] tubeSlices = {};
    private static List<BakedQuad> capsule = List.of();
    private static Preview preview;
    private static final double TURN_COSINE = Math.cos(Math.PI / 8);
    private static final int MAX_MESH_SEGMENTS = 256;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static PneumaticPlacement.Problem previewProblem;
    private record Preview(PneumaticSpline.Port from, PneumaticSpline.Port to, BlockPos aim, Direction face,
                           PneumaticSpline curve, List<Section> mesh) {}
    private record Section(Vec3 origin, AABB bounds, List<BakedQuad> quads) {}

    private static final class TubeView {
        final PneumaticNetwork.Span span;
        int references;
        List<Section> mesh;
        TubeView(PneumaticNetwork.Span span) { this.span = span; }
    }

    private PneumaticRendering() {}

    private static ModelResourceLocation model(String file) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                CreateIndustrialDetails.MOD_ID, "block/pneumatic_tube/" + file));
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(TUBE);
        event.register(CAPSULE);
    }

    @SubscribeEvent
    public static void stationModels(ModelEvent.ModifyBakingResult event) {
        decorate(event.getModels(), ModBlocks.PNEUMATIC_LAUNCHER.get().defaultBlockState(),
                quads(event.getModels().get(CAPSULE)), new Vec3(0, 1.10, -.44));
    }

    private static void decorate(Map<ModelResourceLocation, BakedModel> models, BlockState station,
                                 List<BakedQuad> decoration, Vec3 offset) {
        var mounted = new ArrayList<BakedQuad>();
        for (var face : decoration) {
            var copy = BakedQuadHelper.clone(face);
            for (int i = 0; i < 4; i++)
                BakedQuadHelper.setXYZ(copy.getVertices(), i, BakedQuadHelper.getXYZ(face.getVertices(), i).add(offset));
            mounted.add(copy);
        }
        for (BlockState state : station.getBlock().getStateDefinition().getPossibleStates()) {
            if (!state.getValue(PneumaticStationBlock.LOADED) || state.getValue(PneumaticStationBlock.UPPER)) continue;
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state), (id, parent) -> new BakedModelWrapper<>(parent) {
                @Override
                public List<BakedQuad> getQuads(BlockState block, Direction side, RandomSource random, ModelData data, RenderType layer) {
                    var base = super.getQuads(block, side, random, data, layer);
                    if (side != null || layer != null && layer != RenderType.solid()) return base;
                    var all = new ArrayList<>(base);
                    all.addAll(mounted);
                    return all;
                }
            });
        }
    }

    @SubscribeEvent
    public static void bakingComplete(ModelEvent.BakingCompleted event) {
        tubeTemplate = quads(event.getModelManager().getModel(TUBE));
        capsule = quads(event.getModelManager().getModel(CAPSULE));
        var slices = new TreeSet<Double>();
        slices.add(0.0);
        slices.add(1.0);
        for (BakedQuad quad : tubeTemplate)
            for (int vertex = 0; vertex < 4; vertex++)
                slices.add(clampSlice(BakedQuadHelper.getXYZ(quad.getVertices(), vertex).y));
        tubeSlices = slices.stream().mapToDouble(Double::doubleValue).toArray();
        for (var view : TUBES.values()) view.mesh = null;
        preview = null;
    }

    private static double clampSlice(double height) {
        return Math.max(0, Math.min(1, height));
    }

    private static List<BakedQuad> quads(BakedModel model) {
        if (model == null) return List.of();
        var result = new ArrayList<>(model.getQuads(null, null, RandomSource.create(0)));
        for (Direction direction : Direction.values())
            result.addAll(model.getQuads(null, direction, RandomSource.create(0)));
        return result;
    }

    public static void receive(PneumaticEvents.View view) {
        ClientLevel current = Minecraft.getInstance().level;
        changeLevel(current);
        CompoundTag tag = view.info();
        if (current == null || tag == null || !current.dimension().location().toString().equals(tag.getString("Dimension"))) return;
        switch (tag.getString("Type")) {
            case "Selection" -> {
                var player = Minecraft.getInstance().player;
                if (player != null) PneumaticTubingSelection.receive(player, tag);
            }
            case "Chunk" -> {
                long chunk = tag.getLong("Chunk");
                var previous = CHUNKS.getOrDefault(chunk, Set.of());
                var next = new HashSet<CompoundTag>();
                for (Tag entry : tag.getList("Spans", Tag.TAG_COMPOUND)) {
                    var key = (CompoundTag) entry;
                    if (!next.add(key) || previous.contains(key)) continue;
                    TUBES.computeIfAbsent(key, id -> {
                        var added = new TubeView(PneumaticNetwork.Span.load(id));
                        PORTS.put(added.span.from(), added);
                        PORTS.put(added.span.to(), added);
                        return added;
                    }).references++;
                }
                for (var key : previous) if (!next.contains(key)) dereference(key);
                if (next.isEmpty()) CHUNKS.remove(chunk);
                else CHUNKS.put(chunk, next);
                RECEIVERS.remove(chunk);
                for (Tag entry : tag.getList("Receivers", Tag.TAG_COMPOUND))
                    receiver(PneumaticNetwork.ReceiverDisplay.load((CompoundTag) entry));
            }
            case "Flight" -> {
                var flight = tag.getCompound("Flight");
                FLIGHTS.computeIfAbsent(flight.getUUID("Id"), ignored -> PneumaticNetwork.Flight.load(flight));
            }
            case "Cancel" -> FLIGHTS.remove(tag.getUUID("Flight"));
            case "Receiver" -> receiver(PneumaticNetwork.ReceiverDisplay.load(tag.getCompound("Receiver")));
        }
    }

    private static void receiver(PneumaticNetwork.ReceiverDisplay display) {
        long chunk = new ChunkPos(display.pos()).toLong();
        if (display.style() != null) {
            RECEIVERS.computeIfAbsent(chunk, ignored -> new HashMap<>()).put(display.pos(), display);
        } else {
            var contents = RECEIVERS.get(chunk);
            if (contents != null && contents.remove(display.pos()) != null && contents.isEmpty()) RECEIVERS.remove(chunk);
        }
    }

    private static void changeLevel(ClientLevel current) {
        if (current == level) return;
        level = current;
        CHUNKS.clear();
        TUBES.clear();
        PORTS.clear();
        FLIGHTS.clear();
        RECEIVERS.clear();
        preview = null;
    }

    @SubscribeEvent
    public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        changeLevel(null);
    }

    private static void dereference(CompoundTag key) {
        TubeView view = TUBES.get(key);
        if (view == null || --view.references != 0) return;
        TUBES.remove(key);
        PORTS.remove(view.span.from(), view);
        PORTS.remove(view.span.to(), view);
    }

    @SubscribeEvent
    public static void unload(ChunkEvent.Unload event) {
        if (level == null || event.getLevel() != level) return;
        long chunk = event.getChunk().getPos().toLong();
        RECEIVERS.remove(chunk);
        var old = CHUNKS.remove(chunk);
        if (old != null) old.forEach(PneumaticRendering::dereference);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        changeLevel(minecraft.level);
        if (level == null || minecraft.player == null) return;
        if (!FLIGHTS.isEmpty()) FLIGHTS.values().removeIf(flight -> flight.distance(level.getGameTime()) >= flight.route().length());
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var from = PneumaticTubingSelection.port(minecraft.player, hand);
        if (from == null) {
            hand = net.minecraft.world.InteractionHand.OFF_HAND;
            from = PneumaticTubingSelection.port(minecraft.player, hand);
        }
        if (from == null || minecraft.player.isShiftKeyDown() || !(minecraft.hitResult instanceof BlockHitResult hit)
                || minecraft.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            preview = null;
            return;
        }
        var stack = minecraft.player.getItemInHand(hand);
        BlockPos aim = hit.getBlockPos();
        Direction face = hit.getDirection();
        var to = PneumaticStationBlock.port(level.getBlockState(aim), aim, face);
        if (preview == null || !from.equals(preview.from) || !Objects.equals(to, preview.to)
                || !aim.equals(preview.aim) || face != preview.face) {
            Vec3 fallback = Vec3.atCenterOf(aim).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
            var curve = PneumaticPlacement.candidate(from, to, fallback);
            preview = new Preview(from, to, aim, face, curve,
                    curve == null ? List.of() : buildMesh(curve, to == null || curve.tooLong() ? 12 : 96));
        }
        var player = minecraft.player;
        if (!PneumaticTubingItem.mayAim(level, player, aim, face, stack)) {
            previewProblem = PneumaticPlacement.Problem.INVALID_ENDPOINT;
            return;
        }
        var origin = from;
        boolean knownOrigin = level.getChunkSource().hasChunk(from.pos().getX() >> 4, from.pos().getZ() >> 4);
        previewProblem = PneumaticPlacement.validate(from, to, preview.curve,
                port -> level.mayInteract(player, port.pos())
                        && (port.equals(origin) && !knownOrigin || PneumaticNetwork.validPort(level, port)),
                PneumaticRendering::connectedTo, PneumaticTubingItem.available(player, ModItems.PNEUMATIC_TUBING.get()));
        if (previewProblem == null && !knownOrigin) previewProblem = PneumaticPlacement.Problem.WAITING_FOR_CHUNK;
    }

    private static PneumaticSpline.Port connectedTo(PneumaticSpline.Port port) {
        TubeView view = PORTS.get(port);
        if (view == null) return null;
        return view.span.from().equals(port) ? view.span.to() : view.span.from();
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        if (preview == null) return;
        var minecraft = Minecraft.getInstance();
        Component text = previewProblem == null
                ? Component.translatable("message.create_industrial_details.pneumatic.preview",
                        String.format(Locale.ROOT, "%.1f", preview.curve.length()), preview.curve.cost())
                : previewProblem.message(preview.curve == null ? 0 : preview.curve.cost());
        int width = event.getGuiGraphics().guiWidth();
        int y = event.getGuiGraphics().guiHeight() / 2 + 14;
        int color = previewProblem == null ? 0xA7EEB8
                : previewProblem == PneumaticPlacement.Problem.WAITING_FOR_CHUNK ? 0xFFD275 : 0xFF7777;
        event.getGuiGraphics().drawCenteredString(minecraft.font, text, width / 2, y, color);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || level == null
                || TUBES.isEmpty() && FLIGHTS.isEmpty() && RECEIVERS.isEmpty() && preview == null) return;
        var minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        var type = RenderType.cutoutMipped();
        var vertices = buffers.getBuffer(type);
        boolean drew = false;
        for (var view : TUBES.values()) {
            if (!event.getFrustum().isVisible(view.span.curve().bounds)) continue;
            if (view.mesh == null) view.mesh = buildMesh(view.span.curve(), 96);
            drew |= drawSections(event, camera, view.mesh, vertices, 1, 1, 1, 1, true);
        }
        double time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var flight : FLIGHTS.values()) {
            double distance = flight.distance(time);
            if (distance >= flight.route().length()) continue;
            var pose = flight.route().pose(distance);
            Vec3 pos = pose.pos();
            double age = time - flight.launch();
            if (age < 1) {
                BlockPos station = flight.route().legs().getFirst().from().pos();
                pos = Vec3.atBottomCenterOf(station).add(0, 1.475, -.44).lerp(pos, Math.max(0, age));
            }
            BlockPos block = BlockPos.containing(pos);
            if (!level.hasChunkAt(block) || !event.getFrustum().isVisible(new AABB(pos, pos).inflate(.6))) continue;
            poses.pushPose();
            poses.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
            Vec3 dir = pose.direction();
            poses.mulPose(turn(UP, dir, EAST));
            // The decorative corner rails of the supplied capsule extend to seven pixels.
            poses.scale(.75f, 1, .75f);
            poses.translate(-.5, -.375, -.5);
            int light = LevelRenderer.getLightColor(level, block);
            for (BakedQuad quad : capsule)
                vertices.putBulkData(poses.last(), quad, 1, 1, 1, 1, light, OverlayTexture.NO_OVERLAY, true);
            poses.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch(type);
        if (!RECEIVERS.isEmpty()) {
            var packageType = RenderType.solid();
            var packages = buffers.getBuffer(packageType);
            boolean shown = false;
            for (var contents : RECEIVERS.values()) for (var display : contents.values()) {
                BlockPos pos = display.pos();
                if (!event.getFrustum().isVisible(new AABB(pos).inflate(1)) || !level.hasChunkAt(pos)
                        || !level.getBlockState(pos).is(ModBlocks.PNEUMATIC_RECEIVER.get())) continue;
                var model = AllPartialModels.PACKAGES.get(display.style());
                if (model == null) continue;
                double settle = display.arrival() == 0 ? 0 : Math.max(0, 1 - Math.max(0, time - display.arrival()) / 4);
                poses.pushPose();
                poses.translate(pos.getX() - camera.x, pos.getY() + 1.12 + settle * .2 - camera.y, pos.getZ() - .48 - camera.z);
                CachedBuffers.partial(model, Blocks.AIR.defaultBlockState())
                        .light(LevelRenderer.getLightColor(level, pos.above().north())).renderInto(poses, packages);
                poses.popPose();
                shown = true;
            }
            if (shown) buffers.endBatch(packageType);
        }
        if (preview != null && !preview.mesh.isEmpty()) {
            var previewType = RenderType.translucent();
            boolean valid = previewProblem == null;
            boolean waiting = previewProblem == PneumaticPlacement.Problem.WAITING_FOR_CHUNK;
            if (drawSections(event, camera, preview.mesh, buffers.getBuffer(previewType),
                    valid ? .55f : 1, valid ? 1 : waiting ? .65f : .2f, valid ? .65f : .2f, .62f, false))
                buffers.endBatch(previewType);
        }
    }

    private static boolean drawSections(RenderLevelStageEvent event, Vec3 camera, List<Section> sections,
                                        com.mojang.blaze3d.vertex.VertexConsumer vertices,
                                        float red, float green, float blue, float alpha, boolean loadedOnly) {
        boolean drew = false;
        var poses = event.getPoseStack();
        for (Section section : sections) {
            if (!event.getFrustum().isVisible(section.bounds)) continue;
            BlockPos block = BlockPos.containing(section.bounds.getCenter());
            if (loadedOnly && !level.hasChunkAt(block)) continue;
            int light = loadedOnly ? LevelRenderer.getLightColor(level, block) : 0xf000f0;
            poses.pushPose();
            poses.translate(section.origin.x - camera.x, section.origin.y - camera.y, section.origin.z - camera.z);
            for (BakedQuad quad : section.quads)
                vertices.putBulkData(poses.last(), quad, red, green, blue, alpha, light, OverlayTexture.NO_OVERLAY, true);
            poses.popPose();
            drew = true;
        }
        return drew;
    }

    private record Frame(Vec3 point, Vec3 tangent, Vec3 right, Vec3 forward) {
        Vec3 project(Vec3 local) {
            return right.scale(local.x).add(tangent.scale(local.y)).add(forward.scale(local.z));
        }
    }

    private static Vec3 initialRight(Vec3 tangent) {
        return (Math.abs(tangent.y) > .9 ? SOUTH : UP).cross(tangent).normalize();
    }

    private static Quaternionf turn(Vec3 from, Vec3 to, Vec3 axis) {
        // At a reversal the shortest arc is ambiguous. Preserve the cross-section's right axis.
        if (from.dot(to) < -.999999)
            return new Quaternionf().rotationAxis((float) Math.PI, (float) axis.x, (float) axis.y, (float) axis.z);
        return new Quaternionf().rotationTo(from.toVector3f(), to.toVector3f());
    }

    private static List<Double> meshBreaks(PneumaticSpline spline, int maxTiles) {
        int count = Math.min(maxTiles, Math.max(1, (int) Math.ceil(spline.length() / 1.5)));
        int extraSegments = MAX_MESH_SEGMENTS - count;
        var breaks = new ArrayList<Double>(count + 1);
        breaks.add(0d);
        for (int i = 0; i < count; i++) {
            double end = spline.length() * (i + 1) / count;
            extraSegments = refine(spline, breaks.getLast(), end, 0, extraSegments, breaks);
        }
        return breaks;
    }

    private static int refine(PneumaticSpline spline, double start, double end, int depth,
                              int remaining, List<Double> breaks) {
        if (depth < 5 && remaining > 0) {
            double mid = (start + end) * .5;
            Vec3 a = spline.tangent(start);
            Vec3 b = spline.tangent(mid);
            Vec3 c = spline.tangent(end);
            if (a.dot(b) < TURN_COSINE || b.dot(c) < TURN_COSINE || a.dot(c) < TURN_COSINE) {
                remaining = refine(spline, start, mid, depth + 1, remaining - 1, breaks);
                return refine(spline, mid, end, depth + 1, remaining, breaks);
            }
        }
        breaks.add(end);
        return remaining;
    }

    private static List<Section> buildMesh(PneumaticSpline spline, int maxTiles) {
        if (tubeTemplate.isEmpty()) return List.of();
        var sections = new ArrayList<Section>();
        Vec3 tangent = spline.tangent(0);
        Vec3 right = initialRight(tangent);
        Frame latest = new Frame(spline.at(0), tangent, right, right.cross(tangent));
        List<Double> breaks = meshBreaks(spline, maxTiles);
        int count = breaks.size() - 1;
        for (int start = 0; start < count; start += 5) {
            Vec3 origin = latest.point;
            AABB bounds = new AABB(origin, origin);
            var quads = new ArrayList<BakedQuad>();
            for (int i = start; i < Math.min(start + 5, count); i++) {
                double begin = breaks.get(i);
                double end = breaks.get(i + 1);
                Frame[] frames = new Frame[tubeSlices.length];
                frames[0] = latest;
                for (int slice = 1; slice < tubeSlices.length; slice++) {
                    double distance = tubeSlices[slice] == 1 ? end : begin + (end - begin) * tubeSlices[slice];
                    Vec3 point = spline.at(distance);
                    tangent = spline.tangent(distance);
                    var rotated = turn(latest.tangent, tangent, latest.right).transform(latest.right.toVector3f());
                    Vec3 axis = new Vec3(rotated.x, rotated.y, rotated.z);
                    right = axis.subtract(tangent.scale(axis.dot(tangent))).normalize();
                    if (right.lengthSqr() < .5) right = initialRight(tangent);
                    latest = new Frame(point, tangent, right, right.cross(tangent));
                    frames[slice] = latest;
                    bounds = bounds.minmax(new AABB(point, point));
                }
                for (BakedQuad source : tubeTemplate) {
                    int[] vertices = source.getVertices().clone();
                    for (int v = 0; v < 4; v++) {
                        Vec3 vertex = BakedQuadHelper.getXYZ(vertices, v);
                        Frame frame = frames[Arrays.binarySearch(tubeSlices, clampSlice(vertex.y))];
                        Vec3 placed = frame.point.add(frame.right.scale(vertex.x - .5))
                                .add(frame.forward.scale(vertex.z - .5)).subtract(origin);
                        BakedQuadHelper.setXYZ(vertices, v, placed);
                    }
                    Vec3 normal = BakedQuadHelper.getXYZ(vertices, 2).subtract(BakedQuadHelper.getXYZ(vertices, 0))
                            .cross(BakedQuadHelper.getXYZ(vertices, 3).subtract(BakedQuadHelper.getXYZ(vertices, 1)));
                    if (normal.lengthSqr() < 1e-18) continue;
                    Vec3 outward = frames[frames.length / 2].project(Vec3.atLowerCornerOf(source.getDirection().getNormal()));
                    if (normal.dot(outward) < 0) {
                        reverseWinding(vertices);
                        normal = normal.scale(-1);
                    }
                    Direction side = Direction.getNearest(normal);
                    ClientHooks.fillNormal(vertices, side);
                    quads.add(new BakedQuad(vertices, source.getTintIndex(), side, source.getSprite(), source.isShade(), false));
                }
            }
            sections.add(new Section(origin, bounds.inflate(.4), List.copyOf(quads)));
        }
        return List.copyOf(sections);
    }

    private static void reverseWinding(int[] vertices) {
        int stride = vertices.length / 4;
        for (int i = 0; i < stride; i++) {
            int first = stride + i;
            int last = 3 * stride + i;
            int value = vertices[first];
            vertices[first] = vertices[last];
            vertices[last] = value;
        }
    }
}
