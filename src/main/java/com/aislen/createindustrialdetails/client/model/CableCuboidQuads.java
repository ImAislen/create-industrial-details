package com.aislen.createindustrialdetails.client.model;

import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class CableCuboidQuads {
    private CableCuboidQuads() {}
    public static List<BakedQuad> crop(List<BakedQuad> source, AABB original, AABB box) {
        var result = new ArrayList<BakedQuad>();
        for (var face : source) {
            var data = face.getVertices(); var quad = BakedQuadHelper.clone(face); var output = quad.getVertices();
            var p0 = BakedQuadHelper.getXYZ(data, 0); var u = BakedQuadHelper.getXYZ(data, 1).subtract(p0); var v = BakedQuadHelper.getXYZ(data, 3).subtract(p0);
            var center = box.getCenter().subtract(p0);
            double shiftU = Math.floor(center.dot(u) / u.lengthSqr());
            double shiftV = Math.floor(center.dot(v) / v.lengthSqr());
            for (int i = 0; i < 4; i++) {
                var p = BakedQuadHelper.getXYZ(data, i);
                var point = new Vec3(map(p.x, original.minX, original.maxX, box.minX, box.maxX),
                        map(p.y, original.minY, original.maxY, box.minY, box.maxY), map(p.z, original.minZ, original.maxZ, box.minZ, box.maxZ));
                var offset = point.subtract(p0); double a = offset.dot(u) / u.lengthSqr() - shiftU, b = offset.dot(v) / v.lengthSqr() - shiftV;
                BakedQuadHelper.setXYZ(output, i, point);
                BakedQuadHelper.setU(output, i, (float) (BakedQuadHelper.getU(data, 0) + a * (BakedQuadHelper.getU(data, 1) - BakedQuadHelper.getU(data, 0)) + b * (BakedQuadHelper.getU(data, 3) - BakedQuadHelper.getU(data, 0))));
                BakedQuadHelper.setV(output, i, (float) (BakedQuadHelper.getV(data, 0) + a * (BakedQuadHelper.getV(data, 1) - BakedQuadHelper.getV(data, 0)) + b * (BakedQuadHelper.getV(data, 3) - BakedQuadHelper.getV(data, 0))));
            }
            result.add(quad);
        }
        return result;
    }
    private static double map(double value, double min, double max, double targetMin, double targetMax) {
        return Math.abs(value - min) < Math.abs(value - max) ? targetMin : targetMax;
    }
}
