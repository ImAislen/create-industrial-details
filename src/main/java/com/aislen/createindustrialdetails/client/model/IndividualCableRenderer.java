package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.cake.struts.content.CableStrutInfo;
import com.cake.struts.content.CableStrutModelManipulator;
import com.cake.struts.content.cap.CapAccumulator;
import com.cake.struts.content.mesh.StrutMeshQuad;
import com.cake.struts.content.mesh.StrutSegmentMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.*;

public final class IndividualCableRenderer implements BlockEntityRenderer<IndividualCableBlockEntity> {
    public static final CableStrutInfo SAG = new CableStrutInfo(.035f);
    private static final Map<IndividualCableColor, Profile> PROFILES = new EnumMap<>(IndividualCableColor.class);
    private record Component(IndividualCableColor color, CableRoute route) {}
    private static final Map<Component, List<BakedQuad>> COMPONENTS = new HashMap<>();
    private static synchronized List<BakedQuad> component(IndividualCableColor color, CableRoute route) {
        return COMPONENTS.computeIfAbsent(new Component(color, route), key -> {
            var state = com.aislen.createindustrialdetails.registry.ModBlocks.INDIVIDUAL_CONTROL_CABLE.get().defaultBlockState().setValue(color.property, route);
            var model = net.minecraft.client.Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            return List.copyOf(model.getQuads(state, null, RandomSource.create(0), ModelData.EMPTY, null));
        });
    }
    private static final Map<IndividualCableBlockEntity, Cache> CACHE = new WeakHashMap<>();
    record Profile(StrutSegmentMesh sides, StrutMeshQuad start, StrutMeshQuad end) {}
    private record LitQuad(BakedQuad quad, BlockPos lightPosition) {}
    private record Draw(CableSpans.Span span, List<LitQuad> quads) {}
    private record Cache(int revision, List<Draw> draws, List<BakedQuad> bends) {}

    public IndividualCableRenderer(BlockEntityRendererProvider.Context context) {}
    public static void reload() { PROFILES.clear(); COMPONENTS.clear(); ELEVATED.clear(); CACHE.clear(); PORT_CACHE.clear(); TRAY_CACHE.clear(); IndividualCablePreview.reload(); }
    private static synchronized Profile profile(IndividualCableColor color) {
        return PROFILES.computeIfAbsent(color, key -> {
            var model = net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath("create_industrial_details", key.modelPath())));
            return profileFromQuads(key, model.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null));
        });
    }
    static Profile profileFromQuads(IndividualCableColor key, List<BakedQuad> all) {
            StrutMeshQuad start = null, end = null;
            for (var quad : all) {
                var centered = StrutMeshQuad.from(quad).translate((float) (.5 - (key.minX + .75) / 16), .46875f, 0);
                if (quad.getDirection() == Direction.NORTH) start = centered;
                else if (quad.getDirection() == Direction.SOUTH) end = centered;
            }
            // Translate source quads through Strut's public mesh API without altering their UVs.
            List<BakedQuad> centeredSides = new ArrayList<>();
            CapAccumulator caps = new CapAccumulator(ResourceLocation.fromNamespaceAndPath("create_industrial_details", "block/cable_tray/cable_tray"));
            for (var quad : all) if (quad.getDirection().getAxis() != Direction.Axis.Z)
                StrutMeshQuad.from(quad).translate((float) (.5 - (key.minX + .75) / 16), .46875f, 0)
                        .transformAndEmit(new Matrix4f(), new Matrix3f(), new Vector3f(), new Vector3f(), caps, centeredSides);
            return new Profile(new StrutSegmentMesh(centeredSides), start, end);
    }
    public static CableStrutInfo sag(IndividualCableColor color, double length) {
        return CableSpanGeometry.sag(color, length);
    }
    public static List<BakedQuad> ghost(IndividualCableColor color, Vec3 from, Vec3 to) {
        return bakeProfile(color, profile(color), from, to, false);
    }
    private static Matrix4f frame(Vec3 start, Vec3 delta) {
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return new Matrix4f().translation((float) start.x, (float) start.y, (float) start.z)
                .rotateY((float) Math.atan2(delta.x, delta.z)).rotateX((float) -Math.atan2(delta.y, horizontal))
                .translate(-.5f, -.5f, 0);
    }
    private static Draw bake(BlockPos position, CableSpans.Span span) {
        Vec3 origin = Vec3.atLowerCornerOf(position);
        var quads = bakePoints(profile(span.color()), CableSpanGeometry.points(span).stream().map(p -> p.subtract(origin)).toList(), false, endRoll(span.start()), endRoll(span.end()));
        List<LitQuad> lit = quads.stream().map(q -> {
            var vertices = q.getVertices(); double x = 0, y = 0, z = 0;
            for (int i = 0; i < 4; i++) { x += Float.intBitsToFloat(vertices[i * 8]); y += Float.intBitsToFloat(vertices[i * 8 + 1]); z += Float.intBitsToFloat(vertices[i * 8 + 2]); }
            return new LitQuad(q, BlockPos.containing(origin.x + x / 4, origin.y + y / 4, origin.z + z / 4));
        }).toList();
        return new Draw(span, lit);
    }
    static List<BakedQuad> bakeProfile(IndividualCableColor color, Profile profile, Vec3 from, Vec3 to) {
        return bakeProfile(color, profile, from, to, true);
    }
    static List<BakedQuad> bakeProfile(IndividualCableColor color, Profile profile, Vec3 from, Vec3 to, boolean endCaps) {
        var points = CableStrutModelManipulator.sampleCurvePoints(from, to, sag(color, from.distanceTo(to)), from.distanceTo(to));
        return bakePoints(profile, points, endCaps);
    }
    public static List<BakedQuad> ghost(IndividualCableColor color, List<Vec3> points) { return bakePoints(profile(color), points, false); }
    public static List<BakedQuad> ghost(CableSpans.Span span, Vec3 origin) {
        return bakePoints(profile(span.color()), CableSpanGeometry.points(span).stream().map(p -> p.subtract(origin)).toList(), false, endRoll(span.start()), endRoll(span.end()));
    }
    static float endRoll(CableEndpoint endpoint) {
        return (float) endpoint.roll();
    }
    static List<BakedQuad> bakePoints(Profile profile, List<Vec3> points, boolean endCaps) {
        return bakePoints(profile, points, endCaps, 0, 0);
    }
    static List<BakedQuad> bakePoints(Profile profile, List<Vec3> points, boolean endCaps, float startRoll, float endRoll) {
        var quads = new ArrayList<BakedQuad>();
        var caps = new CapAccumulator(ResourceLocation.fromNamespaceAndPath("create_industrial_details", "block/cable_tray/cable_tray"));
        double textureDistance = 0;
        for (int i = 1; i < points.size(); i++) {
            Vec3 delta = points.get(i).subtract(points.get(i - 1)); float length = (float) delta.length();
            if (length < 1e-6) continue;
            var pose = frame(points.get(i - 1), delta);
            float roll = startRoll + (endRoll - startRoll) * (i - 1) / Math.max(1, points.size() - 2);
            if (roll != 0) pose.translate(.5f, .5f, 0).rotateZ(roll).translate(-.5f, -.5f, 0);
            var normal = new Matrix3f(pose);
            for (var quad : profile.sides.forLength(length, (float) (textureDistance - Math.floor(textureDistance))))
                quad.transformAndEmit(pose, normal, new Vector3f(), new Vector3f(), caps, quads);
            if (endCaps && i == 1 && profile.start != null) profile.start.transformAndEmit(pose, normal, new Vector3f(), new Vector3f(), caps, quads);
            if (endCaps && i == points.size() - 1 && profile.end != null) {
                var endPose = frame(points.get(i).subtract(delta.normalize()), delta);
                profile.end.transformAndEmit(endPose, new Matrix3f(endPose), new Vector3f(), new Vector3f(), caps, quads);
            }
            textureDistance += length;
        }
        return List.copyOf(quads);
    }
    private static final Map<Component, List<BakedQuad>> ELEVATED = new HashMap<>();
    static synchronized List<BakedQuad> elevatedComponent(IndividualCableColor color, CableRoute route) {
        return ELEVATED.computeIfAbsent(new Component(color, route), key -> {
            var model = net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath("create_industrial_details", color.modelPath() + "_elevated")));
            return rotateElevated(model.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null), route);
        });
    }
    static List<BakedQuad> rotateElevated(List<BakedQuad> source, CableRoute route) {
        var rotation = BlockModelRotation.by(route.vertical() ? (route.reversedWall() ? 90 : 270) : 0, route.turns() * 90).getRotation().getMatrix();
        var pose = new Matrix4f().translation(.5f, .5f, .5f).mul(rotation).translate(-.5f, -.5f, -.5f);
        var result = new ArrayList<BakedQuad>();
        var caps = new CapAccumulator(ResourceLocation.fromNamespaceAndPath("create_industrial_details", "block/cable_tray/cable_tray"));
        for (var quad : source) StrutMeshQuad.from(quad).transformAndEmit(pose, new Matrix3f(pose), new Vector3f(), new Vector3f(), caps, result);
        return List.copyOf(result);
    }
    static List<BakedQuad> connectionComponent(IndividualCableBlockEntity host, IndividualCableColor color, CableRoute route) {
        var pos = host.getBlockPos(); var level = host.getLevel();
        boolean raised = CableConnectionGeometry.elevated(level, pos, color, route);
        var source = raised ? elevatedComponent(color, route) : component(color, route);
        var original = CableConnectionGeometry.part(level, pos, color, route).bounds();
        var shape = net.minecraft.world.phys.shapes.Shapes.create(original);
        if (host.secondary(color) == route) shape = net.minecraft.world.phys.shapes.Shapes.join(shape,
                CableConnectionGeometry.part(level, pos, color, host.getBlockState().getValue(color.property)), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        var result = new ArrayList<BakedQuad>();
        if (host.secondary(color) != route) result.addAll(source);
        else for (var box : shape.toAabbs()) result.addAll(CableCuboidQuads.crop(source, original, box));
        var origin = Vec3.atLowerCornerOf(pos);
        for (var box : CableConnectionGeometry.steps(level, pos, color, route))
            result.addAll(CableCuboidQuads.crop(source, original, box.move(origin.scale(-1))));
        return List.copyOf(result);
    }
    @Override public void render(IndividualCableBlockEntity host, float partial, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (host.getLevel() == null) return;
        Cache cached = CACHE.get(host);
        if (cached == null || cached.revision != host.revision) {
            var bends = new ArrayList<BakedQuad>();
            var origin = Vec3.atLowerCornerOf(host.getBlockPos());
            for (var bend : IndividualCableBends.find(host.getLevel(), host.getBlockPos(), host.getBlockState())) {
                if (host.getBlockPos().compareTo(bend.peer()) > 0) continue;
                var route = bend.route(); var color = bend.color();
                var own = CableConnectionGeometry.part(host.getLevel(), host.getBlockPos(), color, route);
                var raised = CableConnectionGeometry.elevated(host.getLevel(), host.getBlockPos(), color, route);
                var template = raised ? elevatedComponent(color, route) : component(color, route);
                for (var peerRoute : IndividualCableComponents.routes(host.getLevel(), bend.peer(), host.getLevel().getBlockState(bend.peer()), color)) {
                    if (route.axis() == peerRoute.axis()) continue;
                    boolean matches = false;
                    for (int end = 0; end < 2; end++) if (IndividualCableGeometry.end(bend.peer(), color, peerRoute, end).distanceToSqr(bend.points().getLast()) < 1e-10) matches = true;
                    if (!matches) continue;
                    var other = CableConnectionGeometry.part(host.getLevel(), bend.peer(), color, peerRoute);
                    var boxes = IndividualCableBends.infill(own.move(host.getBlockPos().getX(), host.getBlockPos().getY(), host.getBlockPos().getZ()),
                            other.move(bend.peer().getX(), bend.peer().getY(), bend.peer().getZ()), route.axis(), peerRoute.axis());
                    for (var box : boxes) bends.addAll(CableCuboidQuads.crop(template, own.bounds(), box.move(origin.scale(-1))));
                }
            }
            for (var color : IndividualCableColor.values()) {
                var extra = host.secondary(color); if (extra == CableRoute.NONE) continue;
                bends.addAll(connectionComponent(host, color, extra));
            }
            cached = new Cache(host.revision, host.spans().stream().map(span -> bake(host.getBlockPos(), span)).toList(), List.copyOf(bends)); CACHE.put(host, cached);
        }
        if (cached.draws.isEmpty() && cached.bends.isEmpty()) return;
        var vertices = buffers.getBuffer(RenderType.cutoutMipped());
        for (var quad : cached.bends) vertices.putBulkData(poses.last(), quad, 1f, 1f, 1f, 1f, light, overlay, true);
        for (var draw : cached.draws) {
            if (draw.span.start().isTray()) continue;
            var from = draw.span.from();
            if (!host.getBlockPos().equals(from) && host.getLevel().hasChunkAt(from)
                    && host.getLevel().getBlockEntity(from) instanceof net.minecraft.world.level.block.entity.BlockEntity) continue;
            for (var quad : draw.quads) {
                int sampled = host.getLevel().hasChunkAt(quad.lightPosition) ? LevelRenderer.getLightColor(host.getLevel(), quad.lightPosition) : light;
                vertices.putBulkData(poses.last(), quad.quad, 1f, 1f, 1f, 1f, sampled, overlay, true);
            }
        }
    }
    private record PortCache(int revision, List<Draw> draws) {}
    private static final Map<com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlockEntity, PortCache> PORT_CACHE = new WeakHashMap<>();
    public static void renderPorts(com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlockEntity box, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (box.getLevel() == null) return;
        var cached = PORT_CACHE.get(box);
        if (cached == null || cached.revision != box.cableRevision) {
            cached = new PortCache(box.cableRevision, box.portSpans().stream().map(s -> bake(box.getBlockPos(), s)).toList()); PORT_CACHE.put(box, cached);
        }
        var vertices = buffers.getBuffer(RenderType.cutoutMipped());
        for (var draw : cached.draws) {
            if (draw.span.start().isTray()) continue;
            if (!box.getBlockPos().equals(draw.span.from()) && box.getLevel().hasChunkAt(draw.span.from()) && box.getLevel().getBlockEntity(draw.span.from()) != null) continue;
            for (var quad : draw.quads) {
                int sampled = box.getLevel().hasChunkAt(quad.lightPosition) ? LevelRenderer.getLightColor(box.getLevel(), quad.lightPosition) : light;
                vertices.putBulkData(poses.last(), quad.quad, 1f, 1f, 1f, 1f, sampled, overlay, true);
            }
        }
    }
    @Override public AABB getRenderBoundingBox(IndividualCableBlockEntity host) { return new AABB(host.getBlockPos()).inflate(31); }
    private static final Map<net.minecraft.world.level.Level, Map<CableSpans.Span, Draw>> TRAY_CACHE = new WeakHashMap<>();
    public static void pruneTrayCache(net.minecraft.world.level.Level level) {
        var cached = TRAY_CACHE.get(level);
        if (cached != null) cached.keySet().removeIf(span -> !CableSpanIndex.client(level).containsRecord(span));
    }
    public static void renderTraySpan(net.minecraft.client.multiplayer.ClientLevel level, CableSpans.Span span, PoseStack poses, MultiBufferSource buffers, Vec3 camera) {
        var draw = TRAY_CACHE.computeIfAbsent(level, key -> new HashMap<>()).computeIfAbsent(span, s -> bake(s.from(), s));
        var origin = Vec3.atLowerCornerOf(span.from()).subtract(camera);
        poses.pushPose(); poses.translate(origin.x, origin.y, origin.z);
        var vertices = buffers.getBuffer(RenderType.cutoutMipped());
        for (var quad : draw.quads) {
            int sampled = level.hasChunkAt(quad.lightPosition) ? LevelRenderer.getLightColor(level, quad.lightPosition) : LightTexture.FULL_BRIGHT;
            vertices.putBulkData(poses.last(), quad.quad, 1f, 1f, 1f, 1f, sampled, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, true);
        }
        poses.popPose();
    }
}
