package com.aislen.createindustrialdetails.content.block.woodenbeam;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record WoodenBeamConnectionEndpoints(
        Direction localFace,
        WoodenBeamSnapPoint localSnap,
        Direction peerFace,
        WoodenBeamSnapPoint peerSnap
) {
    public WoodenBeamConnectionEndpoints reversed() {
        return new WoodenBeamConnectionEndpoints(peerFace, peerSnap, localFace, localSnap);
    }

    public WoodenBeamConnectionEndpoints rotate(Rotation rotation) {
        return transform(rotation, Mirror.NONE);
    }

    public WoodenBeamConnectionEndpoints mirror(Mirror mirror) {
        return transform(Rotation.NONE, mirror);
    }

    public WoodenBeamConnectionEndpoints transform(Rotation rotation, Mirror mirror) {
        return new WoodenBeamConnectionEndpoints(
                WoodenBeamEndpoints.transformFace(localFace, rotation, mirror),
                WoodenBeamEndpoints.transformSnap(localFace, localSnap, rotation, mirror),
                WoodenBeamEndpoints.transformFace(peerFace, rotation, mirror),
                WoodenBeamEndpoints.transformSnap(peerFace, peerSnap, rotation, mirror)
        );
    }
}
