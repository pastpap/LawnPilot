package org.lawnpilot.model;

import org.junit.jupiter.api.Test;
import org.lawnpilot.model.geometry.MaskedLawnGeometry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawnTest {

    @Test
    void insideBoundaryCoordinatesAreAccepted() {
        Lawn lawn = new Lawn(5, 5);

        assertTrue(lawn.isInside(0, 0));
        assertTrue(lawn.isInside(5, 5));
        assertTrue(lawn.isInside(3, 1));
    }

    @Test
    void outsideBoundaryCoordinatesAreRejected() {
        Lawn lawn = new Lawn(5, 5);

        assertFalse(lawn.isInside(-1, 0));
        assertFalse(lawn.isInside(0, -1));
        assertFalse(lawn.isInside(6, 2));
        assertFalse(lawn.isInside(4, 6));
    }

    @Test
    void maskedGeometryAcceptsOnlyExplicitlyAllowedCells() {
        Lawn lawn = new Lawn(new MaskedLawnGeometry(List.of(
                new Position(0, 0),
                new Position(1, 0),
                new Position(1, 1))));

        assertTrue(lawn.isInside(0, 0));
        assertTrue(lawn.isInside(1, 1));
        assertFalse(lawn.isInside(0, 1));
        assertFalse(lawn.isInside(2, 2));
    }
}
