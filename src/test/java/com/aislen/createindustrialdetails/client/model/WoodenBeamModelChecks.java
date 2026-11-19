package com.aislen.createindustrialdetails.client.model;

import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamConnectionEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamEndpoints;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamGeometry;
import com.aislen.createindustrialdetails.content.block.woodenbeam.WoodenBeamSnapPoint;
import com.aislen.createindustrialdetails.content.block.woodenbeam.structure.WoodenBeamLineGeometry;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.mesh.StrutSegmentMesh;
import com.cake.struts.internal.util.BakedQuadHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.cake.struts.content.shape.SurfaceClippingHelper.signedDistance;

public final class WoodenBeamModelChecks {
    private static final double TOLERANCE = 0.00015;

    public static void main(String[] args) throws Exception {
        var sprite = UnitTextureAtlasSprite.INSTANCE;
        var bakery = new FaceBakery();
        var quads = new ArrayList<BakedQuad>();
        try (var reader = model("wooden_beam")) {
            for (var element : BlockModel.fromStream(reader).getElements()) {
                for (var entry : element.faces.entrySet()) {
                    if (entry.getKey().getAxis() != Direction.Axis.Z) {
                        quads.add(bakery.bakeQuad(element.from, element.to, entry.getValue(), sprite,
                                entry.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade));
                    }
                }
            }
        }
        StrutSegmentMesh mesh = new StrutSegmentMesh(quads);
        checkBambooWallRegression(mesh);
        var reference = new StrutModelType(ResourceLocation.withDefaultNamespace("block/oak_log"),
                ResourceLocation.withDefaultNamespace("block/oak_log_top"), 8, 8);
        int checked = 0;
        for (Direction normal : Direction.values()) {
            Direction across = normal.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
            for (WoodenBeamSnapPoint from : WoodenBeamSnapPoint.values()) {
                for (WoodenBeamSnapPoint to : WoodenBeamSnapPoint.values()) {
                    var endpoints = new WoodenBeamConnectionEndpoints(normal, from, normal.getOpposite(), to);
                    checkSpan(mesh, reference, BlockPos.ZERO.relative(normal).relative(across, 8), endpoints);
                    checkSpan(mesh, reference, BlockPos.ZERO.relative(normal, 8).relative(across), endpoints);
                    checkSpan(mesh, reference, BlockPos.ZERO.relative(normal).relative(across), endpoints);
                    checked += 3;
                }
            }
        }

        var center = WoodenBeamSnapPoint.CENTER;
        for (Direction normal : Direction.Plane.HORIZONTAL) {
            checkSpan(mesh, reference, BlockPos.ZERO.relative(normal, 6),
                    new WoodenBeamConnectionEndpoints(normal, center, normal.getOpposite(), center));
            checked++;
        }
        checkSpan(mesh, reference, new BlockPos(1, 1, 0),
                new WoodenBeamConnectionEndpoints(Direction.EAST, center, Direction.DOWN, center));
        checkSpan(mesh, reference, new BlockPos(0, 27, 1),
                new WoodenBeamConnectionEndpoints(Direction.WEST, center, Direction.DOWN, center));
        System.out.println("Wooden Beam: " + (checked + 2) + " baked spans checked against selection and collision.");
    }

    private static void checkBambooWallRegression(StrutSegmentMesh mesh) {
        var bamboo = new StrutModelType(
                ResourceLocation.fromNamespaceAndPath("create_industrial_details", "block/wooden_beam/bamboo_wooden_beam"),
                ResourceLocation.withDefaultNamespace("block/bamboo_block_top"), 8, 8);
        // The saved screenshot beam, translated so the lower anchor is at the origin.
        var upperAnchor = new BlockPos(-1, 7, 0);
        var endpoints = new WoodenBeamConnectionEndpoints(Direction.UP, WoodenBeamSnapPoint.CENTER,
                Direction.EAST, WoodenBeamSnapPoint.CENTER);
        double peak = checkBambooCaps(mesh, bamboo, upperAnchor, endpoints, 7.5);
        var upperRow = new WoodenBeamConnectionEndpoints(Direction.UP, WoodenBeamSnapPoint.CENTER,
                Direction.EAST, WoodenBeamSnapPoint.TOP_CENTER);
        checkBambooCaps(mesh, bamboo, upperAnchor, upperRow, 7.75);
        checkSpan(mesh, bamboo, new BlockPos(-10, 1, 0), upperRow);
        checkSpan(mesh, bamboo, new BlockPos(-4, 4, 0), upperRow);
        checkSpan(mesh, bamboo, upperAnchor.multiply(-1), upperRow.reversed());
        System.out.printf(Locale.ROOT,
                "Bamboo wall regression: baked peak %.4f, wall top 8.0000.%n", peak);
    }

    private static double checkBambooCaps(
            StrutSegmentMesh mesh, StrutModelType bamboo, BlockPos upperAnchor,
            WoodenBeamConnectionEndpoints endpoints, double wallSnapY
    ) {
        var beam = new WoodenBeamGeometry(null, BlockPos.ZERO, upperAnchor, endpoints);
        Vec3 floorSnap = new Vec3(0.5, 0, 0.5);
        Vec3 wallSnap = new Vec3(-1, wallSnapY, 0.5);
        Vec3 axis = wallSnap.subtract(floorSnap).normalize();
        var capSprite = new TextureAtlasSprite(InventoryMenu.BLOCK_ATLAS,
                UnitTextureAtlasSprite.INSTANCE.contents(), 16, 16, 0, 0) {};
        double peak = Double.NEGATIVE_INFINITY;
        double width = WoodenBeamGeometry.HALF_WIDTH * 2;
        for (boolean first : new boolean[]{true, false}) {
            Vec3 origin = first ? Vec3.ZERO : Vec3.atLowerCornerOf(upperAnchor);
            var supportPlane = first ? beam.fromPlane() : beam.toPlane();
            List<BakedQuad> baked = new ArrayList<>();
            WoodenBeamModelBuilder.bakeHalf(beam, first, origin, bamboo, mesh, capSprite, baked);
            require(!baked.isEmpty(), "Both anchors supply bamboo quads");
            double endGrainArea = 0;
            boolean touchesSupport = false;
            for (BakedQuad quad : baked) {
                List<Vec3> vertices = new ArrayList<>(4);
                for (int i = 0; i < 4; i++) {
                    Vec3 vertex = BakedQuadHelper.getXYZ(quad.getVertices(), i).add(origin);
                    vertices.add(vertex);
                    peak = Math.max(peak, vertex.y);
                    checkExtensionVertex(vertex, floorSnap, wallSnap, axis, upperAnchor, endpoints);
                }
                if (quad.getSprite() == capSprite) {
                    touchesSupport |= vertices.stream()
                            .anyMatch(vertex -> Math.abs(signedDistance(vertex, supportPlane)) < TOLERANCE);
                    if (!first && vertices.stream().allMatch(vertex -> Math.abs(vertex.y - 8) < TOLERANCE)) {
                        checkCapTextureScale(quad, vertices, width, axis);
                        endGrainArea += area(vertices);
                    }
                }
            }
            require(touchesSupport && (first || endGrainArea > 0.01),
                    "Both joints touch their supports and the wall edge has a visible end-grain cap");
        }
        require(Math.abs(peak - 8) <= TOLERANCE, "Screenshot beam must reach the wall top without passing it");
        Vec3 capPoint = new Vec3(-0.98, 8, 0.5);
        Vec3 selected = new WoodenBeamSelectionShape(beam)
                .intersect(capPoint.add(0, 0.5, 0), capPoint.add(0, -0.5, 0));
        require(selected != null && selected.distanceTo(capPoint) < TOLERANCE,
                "Selection reaches the raised bamboo cap");
        require(covered(capPoint, WoodenBeamLineGeometry.voxelize(beam, bamboo.voxelShapeResolutionPixels())),
                "Collision reaches the raised bamboo cap");
        return peak;
    }

    private static void checkCapTextureScale(BakedQuad quad, List<Vec3> vertices, double width, Vec3 axis) {
        int[] data = quad.getVertices();
        TextureAtlasSprite sprite = quad.getSprite();
        for (int i = 0; i < vertices.size(); i++) {
            int next = (i + 1) % vertices.size();
            double du = (BakedQuadHelper.getU(data, next) - BakedQuadHelper.getU(data, i))
                    / (sprite.getU1() - sprite.getU0());
            double dv = (BakedQuadHelper.getV(data, next) - BakedQuadHelper.getV(data, i))
                    / (sprite.getV1() - sprite.getV0());
            Vec3 edge = vertices.get(next).subtract(vertices.get(i));
            double projectedLength = edge.subtract(axis.scale(edge.dot(axis))).length();
            require(Math.abs(Math.hypot(du, dv) * width - projectedLength) < TOLERANCE,
                    "The end grain follows the original beam cross-section");
        }
    }

    private static void checkSpan(StrutSegmentMesh mesh, StrutModelType type, BlockPos to,
                                  WoodenBeamConnectionEndpoints endpoints) {
        WoodenBeamGeometry beam = new WoodenBeamGeometry(null, BlockPos.ZERO, to, endpoints);
        Vec3 fromSnap = faceSnap(BlockPos.ZERO, endpoints.localFace(), endpoints.localSnap());
        Vec3 toSnap = faceSnap(to, endpoints.peerFace(), endpoints.peerSnap());
        Vec3 axis = toSnap.subtract(fromSnap).normalize();
        var voxels = WoodenBeamLineGeometry.voxelize(beam, type.voxelShapeResolutionPixels());
        require(!voxels.isEmpty(), "Every tested span has collision");
        var selection = new WoodenBeamSelectionShape(beam);
        for (var plane : List.of(beam.fromPlane(), beam.toPlane())) {
            for (var entry : voxels.entrySet()) {
                var box = entry.getValue().bounds().move(entry.getKey());
                Vec3 n = plane.normal();
                var nearest = new Vec3(n.x < 0 ? box.maxX : box.minX, n.y < 0 ? box.maxY : box.minY,
                        n.z < 0 ? box.maxZ : box.minZ);
                require(signedDistance(nearest, plane) >= -TOLERANCE, "Collision cannot pass the support face");
            }
            var cap = beam.faces().stream().filter(face -> face.end() && face.normal().dot(plane.normal()) < -0.999)
                    .findFirst().orElseThrow(() -> new AssertionError("Missing support cap for " + endpoints));
            Vec3 center = Vec3.ZERO;
            for (Vec3 point : cap.vertices()) {
                center = center.add(point);
            }
            center = center.scale(1.0 / cap.vertices().size());
            Vec3 hit = selection.intersect(center.subtract(plane.normal()), center.add(plane.normal()));
            require(hit != null && hit.distanceTo(center) < TOLERANCE, "Selection hits the cut face");
        }

        double expectedArea = beam.faces().stream().mapToDouble(face -> area(face.vertices())).sum();
        double bakedArea = 0;
        for (boolean first : new boolean[]{true, false}) {
            Vec3 origin = first ? Vec3.ZERO : Vec3.atLowerCornerOf(to);
            List<BakedQuad> baked = new ArrayList<>();
            WoodenBeamModelBuilder.bakeHalf(beam, first, origin, type, mesh, UnitTextureAtlasSprite.INSTANCE, baked);
            require(!baked.isEmpty(), "Each endpoint renders its half");
            for (BakedQuad quad : baked) {
                List<Vec3> vertices = new ArrayList<>(4);
                for (int i = 0; i < 4; i++) {
                    Vec3 vertex = BakedQuadHelper.getXYZ(quad.getVertices(), i).add(origin);
                    vertices.add(vertex);
                    require(signedDistance(vertex, beam.fromPlane()) >= -TOLERANCE
                            && signedDistance(vertex, beam.toPlane()) >= -TOLERANCE, "Rendered vertex past support");
                    checkExtensionVertex(vertex, fromSnap, toSnap, axis, to, endpoints);
                    require(covered(vertex, voxels), "Rendered vertex " + vertex + " outside collision in " + to + " " + endpoints);
                    require(Float.isFinite(BakedQuadHelper.getU(quad.getVertices(), i))
                            && Float.isFinite(BakedQuadHelper.getV(quad.getVertices(), i)), "Finite texture coordinates");
                }
                double quadArea = area(vertices);
                for (var plane : List.of(beam.fromPlane(), beam.toPlane())) {
                    if (quadArea > TOLERANCE && vertices.stream()
                            .allMatch(vertex -> Math.abs(signedDistance(vertex, plane)) < TOLERANCE)) {
                        Vec3 normal = vertices.get(1).subtract(vertices.get(0))
                                .cross(vertices.get(2).subtract(vertices.get(0)));
                        require(normal.dot(plane.normal()) < 0,
                                "The cap faces the support, opposite the support's own face");
                    }
                }
                bakedArea += quadArea;
            }
        }
        require(Math.abs(expectedArea - bakedArea) < 0.001,
                "Baked sides and both caps cover the solid without a seam or overlap for " + to + " " + endpoints);
    }

    private static void checkExtensionVertex(
            Vec3 vertex, Vec3 fromSnap, Vec3 toSnap, Vec3 axis, BlockPos to, WoodenBeamConnectionEndpoints endpoints
    ) {
        if (vertex.subtract(fromSnap).dot(axis) < -TOLERANCE) {
            checkFootprint(vertex, BlockPos.ZERO, endpoints.localFace());
        }
        if (vertex.subtract(toSnap).dot(axis) > TOLERANCE) {
            checkFootprint(vertex, to, endpoints.peerFace());
        }
    }

    private static void checkFootprint(Vec3 vertex, BlockPos anchor, Direction supportFace) {
        var support = anchor.relative(supportFace.getOpposite());
        Vec3 normal = Vec3.atLowerCornerOf(supportFace.getNormal());
        Vec3 face = Vec3.atCenterOf(anchor).relative(supportFace, -0.5);
        double outward = vertex.subtract(face).dot(normal);
        require(outward >= -TOLERANCE && outward <= Math.sqrt(2) * WoodenBeamGeometry.HALF_WIDTH + TOLERANCE,
                "The outside portion of the extension is limited to the timber's cross-section");
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != supportFace.getAxis()) {
                double min = axis.choose(support.getX(), support.getY(), support.getZ());
                require(vertex.get(axis) >= min - TOLERANCE && vertex.get(axis) <= min + 1 + TOLERANCE,
                        "Extended vertex " + vertex + " must stay inside the footprint of " + support);
            }
        }
    }

    private static Vec3 faceSnap(BlockPos pos, Direction face, WoodenBeamSnapPoint snap) {
        var basis = WoodenBeamEndpoints.basis(face);
        return Vec3.atCenterOf(pos).relative(face, -0.5)
                .add(Vec3.atLowerCornerOf(basis.u()).scale(snap.u() - 0.5))
                .add(Vec3.atLowerCornerOf(basis.v()).scale(snap.v() - 0.5));
    }

    private static boolean covered(Vec3 vertex, Map<BlockPos, VoxelShape> voxels) {
        for (var entry : voxels.entrySet()) {
            Vec3 local = vertex.subtract(Vec3.atLowerCornerOf(entry.getKey()));
            for (var box : entry.getValue().toAabbs()) {
                if (box.inflate(TOLERANCE).contains(local)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double area(List<Vec3> vertices) {
        Vec3 a = vertices.getFirst();
        double area = 0;
        for (int i = 1; i < vertices.size() - 1; i++) {
            area += vertices.get(i).subtract(a).cross(vertices.get(i + 1).subtract(a)).length() * 0.5;
        }
        return area;
    }

    private static InputStreamReader model(String name) {
        String path = "/assets/create_industrial_details/models/block/wooden_beam/" + name + ".json";
        var input = WoodenBeamModelChecks.class.getResourceAsStream(path);
        if (input == null) {
            throw new AssertionError("Missing " + path);
        }
        return new InputStreamReader(input, StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
