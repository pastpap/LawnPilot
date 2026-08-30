package org.lawnpilot.model;

import lombok.Getter;
import org.lawnpilot.model.geometry.LawnGeometry;
import org.lawnpilot.model.geometry.RectangularLawnGeometry;

@Getter
public class Lawn {
    private final int maxX;
    private final int maxY;
    private final LawnGeometry geometry;

    public Lawn(int maxX, int maxY) {
        this(new RectangularLawnGeometry(maxX, maxY));
    }

    public Lawn(LawnGeometry geometry) {
        if (geometry == null) {
            throw new IllegalArgumentException("Lawn geometry must not be null.");
        }
        this.geometry = geometry;
        this.maxX = geometry.maxX();
        this.maxY = geometry.maxY();
    }

    public boolean isInside(int x, int y) {
        return geometry.isInside(x, y);
    }

    public boolean isInside(Position position) {
        return geometry.isInside(position);
    }
}
