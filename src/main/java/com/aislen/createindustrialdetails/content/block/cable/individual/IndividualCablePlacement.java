package com.aislen.createindustrialdetails.content.block.cable.individual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public final class IndividualCablePlacement {
    private IndividualCablePlacement() {}
    public static boolean valid(LevelReader level, BlockPos pos, BlockState state, IndividualCableColor color, CableRoute route) {
        var host = level.getBlockEntity(pos) instanceof IndividualCableBlockEntity cable ? cable : null;
        if (state.getValue(color.property) != CableRoute.NONE) return host != null && host.canAdd(color, route);
        return IndividualCableGeometry.permits(state.setValue(color.property, route)) && (host == null || host.clearOfOtherColors(color, route));
    }
    public static CableRoute choose(LevelReader level, BlockPos pos, BlockState state, IndividualCableColor color, CableRoute normal) {
        if (valid(level, pos, state, color, normal)) return normal;
        // A duplicate is a rejected placement, not a request for a different component.
        if (IndividualCableComponents.routes(level, pos, state, color).contains(normal)) return normal;
        CableRoute best = normal; int bestScore = 0, bestTurn = 5;
        for (var candidate : CableRoute.values()) {
            if (candidate == CableRoute.NONE || candidate.vertical() != normal.vertical() || !valid(level, pos, state, color, candidate)) continue;
            if (candidate.vertical() && !sameWallPlane(color, normal, candidate)) continue;
            int score = connections(level, pos, state, color, candidate);
            int turn = Math.abs(candidate.turns() - normal.turns()); turn = Math.min(turn, 4 - turn);
            if (score > bestScore || score > 0 && score == bestScore && turn < bestTurn) {
                best = candidate; bestScore = score; bestTurn = turn;
            }
        }
        return best;
    }
    private static boolean sameWallPlane(IndividualCableColor color, CableRoute a, CableRoute b) {
        var first = IndividualCableGeometry.part(color, a).bounds(); var second = IndividualCableGeometry.part(color, b).bounds();
        return first.getXsize() < first.getZsize() ? first.minX == second.minX && first.maxX == second.maxX
                : first.minZ == second.minZ && first.maxZ == second.maxZ;
    }
    private static int connections(LevelReader level, BlockPos pos, BlockState state, IndividualCableColor color, CableRoute candidate) {
        int score = 0;
        for (var existing : IndividualCableComponents.routes(level, pos, state, color))
            if (IndividualCableComponents.validPair(color, existing, candidate)) score++;
        for (var offset : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
            if (offset.equals(BlockPos.ZERO)) continue;
            var peer = pos.offset(offset); if (!level.hasChunkAt(peer)) continue;
            var other = level.getBlockState(peer); if (!(other.getBlock() instanceof IndividualCableBlock)) continue;
            for (var route : IndividualCableComponents.routes(level, peer, other, color)) {
                boolean straight = candidate.axis() == route.axis();
                if (!straight && candidate.vertical() == route.vertical()) continue;
                double limit = straight ? 1e-10 : .01;
                boolean joins = false;
                for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++)
                    if (IndividualCableGeometry.end(pos, color, candidate, a).distanceToSqr(IndividualCableGeometry.end(peer, color, route, b)) < limit) joins = true;
                if (joins) score++;
            }
        }
        return score;
    }
}
