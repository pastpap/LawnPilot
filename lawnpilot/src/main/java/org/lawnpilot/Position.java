package org.lawnpilot;

import lombok.Getter;

@Getter
public final class Position {
    private final int x;
    private final int y;

    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Position translate(int deltaX, int deltaY) {
        return new Position(x + deltaX, y + deltaY);
    }
}