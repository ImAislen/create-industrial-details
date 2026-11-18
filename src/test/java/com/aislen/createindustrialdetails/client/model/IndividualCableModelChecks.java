package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.cable.individual.*;
import com.cake.struts.content.CableStrutModelManipulator;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class IndividualCableModelChecks {
    public static void run() {
        checkPortTextureCenters();
        for (var color : IndividualCableColor.values()) {
            String path = "/assets/create_industrial_details/models/" + color.modelPath() + ".json";
            try (var input = IndividualCableModelChecks.class.getResourceAsStream(path)) {
                if (input == null) throw new AssertionError("Missing cable model " + path);
                var model = BlockModel.fromStream(new InputStreamReader(input, StandardCharsets.UTF_8));
                var source = new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
                var bakery = new FaceBakery();
                for (var element : model.getElements()) for (var face : element.faces.entrySet())
                    source.add(bakery.bakeQuad(element.from, element.to, face.getValue(), UnitTextureAtlasSprite.INSTANCE,
                            face.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade));
                require(source.size() == 6, "Original cuboid supplies six faces");
                try (var elevatedInput = IndividualCableModelChecks.class.getResourceAsStream("/assets/create_industrial_details/models/" + color.modelPath() + "_elevated.json")) {
                    require(elevatedInput != null, "Supplied elevated asset exists");
                    var elevatedModel = BlockModel.fromStream(new InputStreamReader(elevatedInput, StandardCharsets.UTF_8));
                    var elevatedSource = new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
                    for (var element : elevatedModel.getElements()) for (var face : element.faces.entrySet())
                        elevatedSource.add(bakery.bakeQuad(element.from, element.to, face.getValue(), UnitTextureAtlasSprite.INSTANCE,
                                face.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade));
                    for (var route : CableRoute.values()) if (route != CableRoute.NONE) {
                        var actual = IndividualCableRenderer.rotateElevated(elevatedSource, route);
                        var bounds = CableConnectionGeometry.elevatedPart(color, route).bounds();
                        double[] low = {100,100,100}, high = {-100,-100,-100};
                        for (var q : actual) for (int v = 0; v < 4; v++) {
                            var point = BakedQuadHelper.getXYZ(q.getVertices(), v); double[] coordinates = {point.x, point.y, point.z};
                            for (int k = 0; k < 3; k++) { low[k] = Math.min(low[k], coordinates[k]); high[k] = Math.max(high[k], coordinates[k]); }
                        }
                        require(Math.abs(bounds.minX-low[0]) < 1e-6 && Math.abs(bounds.minY-low[1]) < 1e-6 && Math.abs(bounds.minZ-low[2]) < 1e-6
                                && Math.abs(bounds.maxX-high[0]) < 1e-6 && Math.abs(bounds.maxY-high[1]) < 1e-6 && Math.abs(bounds.maxZ-high[2]) < 1e-6,
                                "Rotated supplied elevated geometry agrees with selection and endpoint coordinates");
                        for (int q = 0; q < actual.size(); q++) require(uvKey(actual.get(q)).equals(uvKey(elevatedSource.get(q))), "Model rotation preserves original elevated UV mapping");
                    }
                }
                var profile = IndividualCableRenderer.profileFromQuads(color, source);
                for (Vec3 to : java.util.List.of(new Vec3(0, 0, 8), new Vec3(8, 1, 0), new Vec3(3, 6, 4), new Vec3(0, 8, 0))) {
                    var quads = IndividualCableRenderer.bakeProfile(color, profile, Vec3.ZERO, to);
                    require(!quads.isEmpty(), "Strut mesh produces geometry in horizontal, diagonal and vertical directions");
                    var endUVs = source.stream().filter(q -> q.getDirection().getAxis() == Direction.Axis.Z).map(IndividualCableModelChecks::uvKey).toList();
                    require(quads.stream().filter(q -> endUVs.contains(uvKey(q))).count() == 2,
                            "Only two original end-face UVs are used; no internal caps");
                    var joined = IndividualCableRenderer.bakeProfile(color, profile, Vec3.ZERO, to, false);
                    require(joined.size() == quads.size() - 2, "Placed and ghost spans omit duplicate anchor end caps");
                    double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
                    for (var quad : quads) {
                        require(quad.getSprite() == UnitTextureAtlasSprite.INSTANCE, "Original baked sprite is retained");
                        for (int i = 0; i < 4; i++) {
                            Vec3 vertex = BakedQuadHelper.getXYZ(quad.getVertices(), i);
                            require(Double.isFinite(vertex.lengthSqr()), "Every vertex remains finite");
                            minX = Math.min(minX, vertex.x); maxX = Math.max(maxX, vertex.x);
                            require(Float.isFinite(BakedQuadHelper.getU(quad.getVertices(), i))
                                    && Float.isFinite(BakedQuadHelper.getV(quad.getVertices(), i)), "UVs remain finite");
                        }
                    }
                    if (to.equals(new Vec3(0, 0, 8))) require(Math.abs(maxX - minX - 1.5 / 16) < 1e-5, "Span keeps the supplied 1.5-pixel cable width");
                }
                var points = CableStrutModelManipulator.sampleCurvePoints(Vec3.ZERO, new Vec3(0, 0, 8), IndividualCableRenderer.sag(color, 8), 8);
                require(points.getFirst().equals(Vec3.ZERO) && points.getLast().equals(new Vec3(0, 0, 8)), "Sag retains both exact endpoints");
                double sag = points.stream().mapToDouble(p -> p.y).min().orElseThrow();
                double expectedSag = .28 + color.ordinal() * .125;
                require(Math.abs(sag + expectedSag) < .01, "Native sag retains separate color depth lanes");
                var serverPoints = CableSpanGeometry.points(Vec3.ZERO, new Vec3(0, 0, 8), color);
                require(points.size() == serverPoints.size(), "Server and native Strut sampling counts match");
                for (int i = 0; i < points.size(); i++) require(points.get(i).distanceToSqr(serverPoints.get(i)) < 1e-16,
                        "Server targeting exactly follows the native Strut render curve");
                for (var route : CableRoute.values()) {
                    if (route == CableRoute.NONE) continue;
                    var rotation = BlockModelRotation.by(route.vertical() ? (route.reversedWall() ? 90 : 270) : 0, route.turns() * 90);
                    var routeQuads = new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
                    double[] low = {100, 100, 100}, high = {-100, -100, -100};
                    for (var element : model.getElements()) for (var face : element.faces.entrySet()) {
                        var q = bakery.bakeQuad(element.from, element.to, face.getValue(), UnitTextureAtlasSprite.INSTANCE,
                                face.getKey(), rotation, element.rotation, element.shade);
                        routeQuads.add(q);
                        for (int i = 0; i < 4; i++) {
                            var v = BakedQuadHelper.getXYZ(q.getVertices(), i);
                            double[] coordinates = {v.x, v.y, v.z};
                            for (int k = 0; k < 3; k++) { low[k] = Math.min(low[k], coordinates[k]); high[k] = Math.max(high[k], coordinates[k]); }
                        }
                    }
                    var box = IndividualCableGeometry.part(color, route).bounds();
                    var peer = net.minecraft.core.BlockPos.ZERO.relative(Direction.get(Direction.AxisDirection.POSITIVE, route.axis()), 5);
                    var span = new CableSpans.Span(color, net.minecraft.core.BlockPos.ZERO, peer, route, route, 5);
                    var spanPath = CableSpanGeometry.points(span);
                    var joined = IndividualCableRenderer.bakePoints(profile, spanPath, false, IndividualCableRenderer.endRoll(span.start()), IndividualCableRenderer.endRoll(span.end()));
                    double[] crossLow = {100, 100, 100}, crossHigh = {-100, -100, -100};
                    int endVertices = 0;
                    var attachment = CableSpanGeometry.from(span);
                    for (var q : joined.subList(0, 4)) for (int vertex = 0; vertex < 4; vertex++) {
                        var v = BakedQuadHelper.getXYZ(q.getVertices(), vertex);
                        if (Math.abs(route.axis().choose(v.x - attachment.x, v.y - attachment.y, v.z - attachment.z)) > 1e-6) continue;
                        endVertices++;
                        double[] coords = {v.x, v.y, v.z};
                        for (int k = 0; k < 3; k++) { crossLow[k] = Math.min(crossLow[k], coords[k]); crossHigh[k] = Math.max(crossHigh[k], coords[k]); }
                    }
                    require(endVertices == 8, "All four span sides share the exact anchor end plane");
                    double[] bLow = {box.minX, box.minY, box.minZ}, bHigh = {box.maxX, box.maxY, box.maxZ};
                    for (int k = 0; k < 3; k++) if (k != route.axis().ordinal())
                        require(Math.abs(crossLow[k] - bLow[k]) < 1e-6 && Math.abs(crossHigh[k] - bHigh[k]) < 1e-6,
                                "Span attachment cross-section matches the real source model in every wall/floor orientation");
                    var crop = new net.minecraft.world.phys.AABB(box.minX, box.minY, box.minZ,
                            box.minX + box.getXsize() / 2, box.minY + box.getYsize() / 2, box.minZ + box.getZsize() / 16);
                    var cropped = CableCuboidQuads.crop(routeQuads, box, crop);
                    for (int face = 0; face < routeQuads.size(); face++) for (int edge : new int[]{1, 3}) {
                        var original = routeQuads.get(face).getVertices(); var cut = cropped.get(face).getVertices();
                        double before = Math.hypot(BakedQuadHelper.getU(original, edge) - BakedQuadHelper.getU(original, 0), BakedQuadHelper.getV(original, edge) - BakedQuadHelper.getV(original, 0))
                                / BakedQuadHelper.getXYZ(original, edge).distanceTo(BakedQuadHelper.getXYZ(original, 0));
                        double after = Math.hypot(BakedQuadHelper.getU(cut, edge) - BakedQuadHelper.getU(cut, 0), BakedQuadHelper.getV(cut, edge) - BakedQuadHelper.getV(cut, 0))
                                / BakedQuadHelper.getXYZ(cut, edge).distanceTo(BakedQuadHelper.getXYZ(cut, 0));
                        require(Math.abs(before - after) < 1e-5, "Small connector faces retain source texels per model pixel on all cardinal rotations");
                    }

                    require(Math.abs(box.minX - low[0]) < 1e-6 && Math.abs(box.minY - low[1]) < 1e-6 && Math.abs(box.minZ - low[2]) < 1e-6
                            && Math.abs(box.maxX - high[0]) < 1e-6 && Math.abs(box.maxY - high[1]) < 1e-6 && Math.abs(box.maxZ - high[2]) < 1e-6,
                            "Selection bounds match actual lowered model rotations on all axes");
                }
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        }
        System.out.println("Individual cables: original-model Strut mesh width, UVs, end faces, native sag and multi-directional span checks passed.");
    }
    private static void checkPortTextureCenters() {
        String base = "/assets/create_industrial_details/";
        try (var input = IndividualCableModelChecks.class.getResourceAsStream(base + "models/block/cable_tray/control_box_closed.json");
             var texture = IndividualCableModelChecks.class.getResourceAsStream(base + "textures/block/cable_tray/control_box.png")) {
            var model = BlockModel.fromStream(new InputStreamReader(input, StandardCharsets.UTF_8));
            var image = javax.imageio.ImageIO.read(texture);
            var atlas = new net.minecraft.client.renderer.texture.TextureAtlasSprite(UnitTextureAtlasSprite.LOCATION,
                    UnitTextureAtlasSprite.INSTANCE.contents(), 1, 1, 0, 0) {
                @Override public float uvShrinkRatio() { return 0; }
            };
            var body = model.getElements().stream().filter(e -> e.from.equals(new org.joml.Vector3f(2, 0, 0)) && e.to.equals(new org.joml.Vector3f(14, 16, 4))).findFirst().orElseThrow();
            var bakery = new FaceBakery();
            for (var side : java.util.List.of(Direction.UP, Direction.DOWN, Direction.NORTH)) {
                var q = bakery.bakeQuad(body.from, body.to, body.faces.get(side), atlas,
                        side, BlockModelRotation.X0_Y0, body.rotation, body.shade);
                var vertices = q.getVertices(); var a = BakedQuadHelper.getXYZ(vertices, 0);
                var uAxis = BakedQuadHelper.getXYZ(vertices, 1).subtract(a); var vAxis = BakedQuadHelper.getXYZ(vertices, 3).subtract(a);
                for (var color : IndividualCableColor.values()) {
                    int code = com.aislen.createindustrialdetails.content.block.cable.ControlBoxPorts.encode(side, Direction.SOUTH);
                    var p = com.aislen.createindustrialdetails.content.block.cable.ControlBoxPorts.point(net.minecraft.core.BlockPos.ZERO, code, color).subtract(a);
                    double u = p.dot(uAxis) / uAxis.lengthSqr(), v = p.dot(vAxis) / vAxis.lengthSqr();
                    double textureU = BakedQuadHelper.getU(vertices, 0) + u * (BakedQuadHelper.getU(vertices, 1) - BakedQuadHelper.getU(vertices, 0))
                            + v * (BakedQuadHelper.getU(vertices, 3) - BakedQuadHelper.getU(vertices, 0));
                    double textureV = BakedQuadHelper.getV(vertices, 0) + u * (BakedQuadHelper.getV(vertices, 1) - BakedQuadHelper.getV(vertices, 0))
                            + v * (BakedQuadHelper.getV(vertices, 3) - BakedQuadHelper.getV(vertices, 0));
                    int rgb = image.getRGB((int) (textureU * image.getWidth()), (int) (textureV * image.getHeight()));
                    require((rgb >> 16 & 255) < 40 && (rgb >> 8 & 255) < 40 && (rgb & 255) < 40, "Port center maps to black opening: " + side + " " + color + " texel=" + textureU * image.getWidth() + "," + textureV * image.getHeight() + " rgb=" + Integer.toHexString(rgb));
                }
            }
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    private static java.util.List<String> uvKey(net.minecraft.client.renderer.block.model.BakedQuad quad) {
        var key = new java.util.ArrayList<String>();
        for (int i = 0; i < 4; i++) key.add(Float.floatToIntBits(BakedQuadHelper.getU(quad.getVertices(), i))
                + ":" + Float.floatToIntBits(BakedQuadHelper.getV(quad.getVertices(), i)));
        java.util.Collections.sort(key); return key;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
