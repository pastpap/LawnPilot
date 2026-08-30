package org.lawnpilot.runtime.events;

import org.lawnpilot.model.Direction;
import org.lawnpilot.model.Mower;

import java.util.Objects;

public final class MowerStateSnapshot {

    private final int x;
    private final int y;
    private final Direction direction;

    public MowerStateSnapshot(int x, int y, Direction direction) {
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    public static MowerStateSnapshot fromMower(Mower mower) {
        return new MowerStateSnapshot(mower.getX(), mower.getY(), mower.getDirection());
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MowerStateSnapshot that)) {
            return false;
        }
        return x == that.x && y == that.y && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, direction);
    }
}