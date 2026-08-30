package org.lawnpilot.model.geometry;

import org.lawnpilot.model.Position;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MaskedLawnGeometry implements LawnGeometry {

    private final Set<String> allowedCells;
    private final int maxX;
    private final int maxY;

    public MaskedLawnGeometry(List<Position> allowedCells) {
        if (allowedCells == null || allowedCells.isEmpty()) {
            throw new IllegalArgumentException("Masked lawn requires at least one allowed cell.");
        }

        Set<String> cells = new HashSet<>();
        int localMaxX = 0;
        int localMaxY = 0;
        for (Position position : allowedCells) {
            if (position == null) {
                throw new IllegalArgumentException("Allowed cell entries must not be null.");
            }
            if (position.getX() < 0 || position.getY() < 0) {
                throw new IllegalArgumentException("Allowed cell coordinates must be non-negative.");
            }
            cells.add(key(position.getX(), position.getY()));
            localMaxX = Math.max(localMaxX, position.getX());
            localMaxY = Math.max(localMaxY, position.getY());
        }

        this.allowedCells = cells;
        this.maxX = localMaxX;
        this.maxY = localMaxY;
    }

    @Override
    public boolean isInside(int x, int y) {
        return allowedCells.contains(key(x, y));
    }

    @Override
    public int maxX() {
        return maxX;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    private String key(int x, int y) {
        return x + ":" + y;
    }
}