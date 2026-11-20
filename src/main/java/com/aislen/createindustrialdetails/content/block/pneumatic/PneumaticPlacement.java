package com.aislen.createindustrialdetails.content.block.pneumatic;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.aislen.createindustrialdetails.content.block.pneumatic.PneumaticSpline.Port;

public final class PneumaticPlacement {
    private PneumaticPlacement() {}

    public enum Problem {
        INVALID_ENDPOINT("invalid_endpoint"),
        SAME_ANCHOR("same_anchor"),
        DUPLICATE("duplicate"),
        OCCUPIED("occupied"),
        TOO_LONG("too_long"),
        INVALID_CURVE("invalid_curve"),
        MATERIALS("materials"),
        WAITING_FOR_CHUNK("waiting");

        private final String key;

        Problem(String name) {
            key = "message.create_industrial_details.pneumatic." + name;
        }

        public Component message(int cost) {
            return this == MATERIALS ? Component.translatable(key, cost) : Component.translatable(key);
        }
    }

    @Nullable
    public static PneumaticSpline candidate(Port origin, @Nullable Port target, Vec3 aim) {
        if (target == null) return PneumaticSpline.preview(origin.center(), aim);
        return origin.center().equals(target.center()) ? null : new PneumaticSpline(origin, target);
    }

    @Nullable
    public static Problem validate(Port origin, @Nullable Port target, @Nullable PneumaticSpline curve,
                                   Predicate<Port> validPort, Function<Port, Port> connectedTo, int available) {
        if (target == null || !validPort.test(origin) || !validPort.test(target)) return Problem.INVALID_ENDPOINT;
        if (origin.pos().equals(target.pos())) return Problem.SAME_ANCHOR;
        Port peer = connectedTo.apply(origin);
        if (target.equals(peer)) return Problem.DUPLICATE;
        if (peer != null || connectedTo.apply(target) != null) return Problem.OCCUPIED;
        if (curve == null) return Problem.INVALID_CURVE;
        if (curve.tooLong()) return Problem.TOO_LONG;
        if (!curve.valid()) return Problem.INVALID_CURVE;
        return available < curve.cost() ? Problem.MATERIALS : null;
    }
}
