package org.lawnpilot.model.geometry;

import org.lawnpilot.model.Position;

public interface LawnGeometry {

    boolean isInside(int x, int y);

    default boolean isInside(Position position) {
        return isInside(position.getX(), position.getY());
    }

    int maxX();

    int maxY();
}