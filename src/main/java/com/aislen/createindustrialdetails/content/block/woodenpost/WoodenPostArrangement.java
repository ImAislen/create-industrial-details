package com.aislen.createindustrialdetails.content.block.woodenpost;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

/** One of the 34 non-overlapping sets of 8x8 Wooden Post members. */
public enum WoodenPostArrangement implements StringRepresentable {
    NORTH_WEST(WoodenPostPosition.NORTH_WEST),
    NORTH(WoodenPostPosition.NORTH),
    NORTH_EAST(WoodenPostPosition.NORTH_EAST),
    WEST(WoodenPostPosition.WEST),
    CENTER(WoodenPostPosition.CENTER),
    EAST(WoodenPostPosition.EAST),
    SOUTH_WEST(WoodenPostPosition.SOUTH_WEST),
    SOUTH(WoodenPostPosition.SOUTH),
    SOUTH_EAST(WoodenPostPosition.SOUTH_EAST),

    NORTH_WEST_NORTH_EAST(
            WoodenPostPosition.NORTH_WEST, WoodenPostPosition.NORTH_EAST
    ),
    NORTH_WEST_EAST(WoodenPostPosition.NORTH_WEST, WoodenPostPosition.EAST),
    NORTH_WEST_SOUTH_WEST(
            WoodenPostPosition.NORTH_WEST, WoodenPostPosition.SOUTH_WEST
    ),
    NORTH_WEST_SOUTH(WoodenPostPosition.NORTH_WEST, WoodenPostPosition.SOUTH),
    NORTH_WEST_SOUTH_EAST(
            WoodenPostPosition.NORTH_WEST, WoodenPostPosition.SOUTH_EAST
    ),
    NORTH_SOUTH_WEST(WoodenPostPosition.NORTH, WoodenPostPosition.SOUTH_WEST),
    NORTH_SOUTH(WoodenPostPosition.NORTH, WoodenPostPosition.SOUTH),
    NORTH_SOUTH_EAST(WoodenPostPosition.NORTH, WoodenPostPosition.SOUTH_EAST),
    NORTH_EAST_WEST(WoodenPostPosition.NORTH_EAST, WoodenPostPosition.WEST),
    NORTH_EAST_SOUTH_WEST(
            WoodenPostPosition.NORTH_EAST, WoodenPostPosition.SOUTH_WEST
    ),
    NORTH_EAST_SOUTH(WoodenPostPosition.NORTH_EAST, WoodenPostPosition.SOUTH),
    NORTH_EAST_SOUTH_EAST(
            WoodenPostPosition.NORTH_EAST, WoodenPostPosition.SOUTH_EAST
    ),
    WEST_EAST(WoodenPostPosition.WEST, WoodenPostPosition.EAST),
    WEST_SOUTH_EAST(WoodenPostPosition.WEST, WoodenPostPosition.SOUTH_EAST),
    EAST_SOUTH_WEST(WoodenPostPosition.EAST, WoodenPostPosition.SOUTH_WEST),
    SOUTH_WEST_SOUTH_EAST(
            WoodenPostPosition.SOUTH_WEST, WoodenPostPosition.SOUTH_EAST
    ),

    NORTH_WEST_NORTH_EAST_SOUTH_WEST(
            WoodenPostPosition.NORTH_WEST,
            WoodenPostPosition.NORTH_EAST,
            WoodenPostPosition.SOUTH_WEST
    ),
    NORTH_WEST_NORTH_EAST_SOUTH(
            WoodenPostPosition.NORTH_WEST,
            WoodenPostPosition.NORTH_EAST,
            WoodenPostPosition.SOUTH
    ),
    NORTH_WEST_NORTH_EAST_SOUTH_EAST(
            WoodenPostPosition.NORTH_WEST,
            WoodenPostPosition.NORTH_EAST,
            WoodenPostPosition.SOUTH_EAST
    ),
    NORTH_WEST_EAST_SOUTH_WEST(
            WoodenPostPosition.NORTH_WEST,
            WoodenPostPosition.EAST,
            WoodenPostPosition.SOUTH_WEST
    ),
    NORTH_WEST_SOUTH_WEST_SOUTH_EAST(
            WoodenPostPosition.NORTH_WEST,
            WoodenPostPosition.SOUTH_WEST,
            WoodenPostPosition.SOUTH_EAST
    ),
    NORTH_SOUTH_WEST_SOUTH_EAST(
            WoodenPostPosition.NORTH,
            WoodenPostPosition.SOUTH_WEST,
            WoodenPostPosition.SOUTH_EAST
    ),
    NORTH_EAST_WEST_SOUTH_EAST(
            WoodenPostPosition.NORTH_EAST,
            WoodenPostPosition.WEST,
            WoodenPostPosition.SOUTH_EAST
    ),
    NORTH_EAST_SOUTH_WEST_SOUTH_EAST(
            WoodenPostPosition.NORTH_EAST,
            WoodenPostPosition.SOUTH_WEST,
            WoodenPostPosition.SOUTH_EAST
    ),

    NORTH_WEST_NORTH_EAST_SOUTH_WEST_SOUTH_EAST(
            WoodenPostPosition.NORTH_WEST,
            WoodenPostPosition.NORTH_EAST,
            WoodenPostPosition.SOUTH_WEST,
            WoodenPostPosition.SOUTH_EAST
    );

    public static final int VALID_ARRANGEMENT_COUNT = 34;
    private static final WoodenPostArrangement[] BY_MASK = new WoodenPostArrangement[1 << 9];

    static {
        if (values().length != VALID_ARRANGEMENT_COUNT) {
            throw new IllegalStateException("Expected exactly 34 Wooden Post arrangements");
        }
        for (WoodenPostArrangement arrangement : values()) {
            if (BY_MASK[arrangement.mask] != null) {
                throw new IllegalStateException("Duplicate Wooden Post arrangement mask: " + arrangement.mask);
            }
            BY_MASK[arrangement.mask] = arrangement;
        }
        int geometricCount = 0;
        WoodenPostPosition[] positions = WoodenPostPosition.values();
        for (int mask = 1; mask < BY_MASK.length; mask++) {
            boolean geometricallyValid = isGeometricallyValid(mask, positions);
            if (geometricallyValid) {
                geometricCount++;
            }
            if ((BY_MASK[mask] != null) != geometricallyValid) {
                throw new IllegalStateException("Missing or invalid Wooden Post arrangement mask: " + mask);
            }
        }
        if (geometricCount != VALID_ARRANGEMENT_COUNT) {
            throw new IllegalStateException("Expected 34 geometric Wooden Post arrangements, found " + geometricCount);
        }
    }

    private final String serializedName;
    private final List<WoodenPostPosition> members;
    private final int mask;
    private final VoxelShape shape;

    WoodenPostArrangement(WoodenPostPosition... members) {
        this.members = List.of(members);

        int mask = 0;
        VoxelShape shape = Shapes.empty();
        StringJoiner serializedName = new StringJoiner("_");
        for (int index = 0; index < members.length; index++) {
            WoodenPostPosition member = members[index];
            int bit = 1 << member.ordinal();
            if ((mask & bit) != 0) {
                throw new IllegalArgumentException("Duplicate Wooden Post member: " + member);
            }
            for (int otherIndex = 0; otherIndex < index; otherIndex++) {
                if (!member.isCompatibleWith(members[otherIndex])) {
                    throw new IllegalArgumentException(
                            "Overlapping Wooden Post members: " + member + " and " + members[otherIndex]
                    );
                }
            }
            mask |= bit;
            shape = Shapes.or(shape, member.shape());
            serializedName.add(member.getSerializedName());
        }
        this.serializedName = serializedName.toString();
        this.mask = mask;
        this.shape = shape;
    }

    private static boolean isGeometricallyValid(int mask, WoodenPostPosition[] positions) {
        for (int first = 0; first < positions.length; first++) {
            if ((mask & (1 << first)) == 0) {
                continue;
            }
            for (int second = first + 1; second < positions.length; second++) {
                if ((mask & (1 << second)) != 0
                        && !positions[first].isCompatibleWith(positions[second])) {
                    return false;
                }
            }
        }
        return true;
    }

    public static WoodenPostArrangement singleton(WoodenPostPosition position) {
        return BY_MASK[1 << position.ordinal()];
    }

    public boolean contains(WoodenPostPosition position) {
        return (mask & (1 << position.ordinal())) != 0;
    }

    public List<WoodenPostPosition> members() {
        return members;
    }

    public int memberCount() {
        return members.size();
    }

    public boolean canAdd(WoodenPostPosition position) {
        if (contains(position)) {
            return false;
        }
        int combinedMask = mask | (1 << position.ordinal());
        return BY_MASK[combinedMask] != null;
    }

    public WoodenPostArrangement add(WoodenPostPosition position) {
        WoodenPostArrangement combined = BY_MASK[mask | (1 << position.ordinal())];
        if (contains(position) || combined == null) {
            throw new IllegalArgumentException(position + " cannot be added to " + serializedName);
        }
        return combined;
    }

    public VoxelShape shape() {
        return shape;
    }

    @Nullable
    public WoodenPostPosition singlePosition() {
        return members.size() == 1 ? members.getFirst() : null;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
