package com.aislen.createindustrialdetails.content.block.cable.individual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

public final class IndividualCableTopology {
    private IndividualCableTopology() {}
    public static boolean connectedWithin(IndividualCableBlockEntity host, IndividualCableColor color, CableRoute a, CableRoute b) {
        return host.contains(color, a) && host.contains(color, b) && (a == b || IndividualCableComponents.validPair(color, a, b));
    }
    public record Link(BlockPos peer, boolean span) {}
    public static List<Link> links(ServerLevel level, BlockPos pos, IndividualCableColor color) {
        var local = CableSpans.endpoint(level, pos, color);
        if (local == null) return List.of();

        List<Link> links = new ArrayList<>();
        for (var route : local.routes(color)) for (Direction side : Direction.values()) {
            if (side.getAxis() != route.axis()) continue;
            var peer = CableSpans.endpoint(level, pos.relative(side), color);
            if (peer != null && peer.contains(color, route)) links.add(new Link(pos.relative(side), false));
        }
        for (var bend : IndividualCableBends.find(level, pos, local.getBlockState()))
            if (bend.color() == color) links.add(new Link(bend.peer(), false));
        for (var span : CableSpans.get(level).at(pos))
            if (span.color() == color && CableSpans.endpoint(level, span.peer(pos), color) != null) links.add(new Link(span.peer(pos), true));
        return List.copyOf(links);
    }
}
