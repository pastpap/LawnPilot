package org.lawnpilot.model.geometry;

public final class RectangularLawnGeometry implements LawnGeometry {

    private final int maxX;
    private final int maxY;

    public RectangularLawnGeometry(int maxX, int maxY) {
        if (maxX < 0 || maxY < 0) {
            throw new IllegalArgumentException("Lawn bounds must be non-negative.");
        }
        this.maxX = maxX;
        this.maxY = maxY;
    }

    @Override
    public boolean isInside(int x, int y) {
        return x >= 0 && x <= maxX && y >= 0 && y <= maxY;
    }

    @Override
    public int maxX() {
        return maxX;
    }

    @Override
    public int maxY() {
        return maxY;
    }
}