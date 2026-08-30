package org.lawnpilot;

import lombok.Getter;

@Getter
public class Lawn {
    private int maxX;
    private int maxY;

    public Lawn(int maxX, int maxY) {
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public boolean isInside(int x, int y) {
        return x >= 0 && x <= maxX && y >= 0 && y <= maxY;
    }

    public boolean isInside(Position position) {
        return isInside(position.getX(), position.getY());
    }
}
