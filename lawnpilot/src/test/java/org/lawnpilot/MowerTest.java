package org.lawnpilot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MowerTest {

    @Test
    void movesForwardNorth() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(2, 2, Direction.N);

        mower.execute("F", lawn);

        assertEquals(2, mower.getX());
        assertEquals(3, mower.getY());
        assertEquals(Direction.N, mower.getDirection());
    }

    @Test
    void movesForwardEast() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(2, 2, Direction.E);

        mower.execute("F", lawn);

        assertEquals(3, mower.getX());
        assertEquals(2, mower.getY());
        assertEquals(Direction.E, mower.getDirection());
    }

    @Test
    void blocksMoveOutsideBoundary() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(0, 0, Direction.S);

        mower.execute("F", lawn);

        assertEquals(0, mower.getX());
        assertEquals(0, mower.getY());
        assertEquals(Direction.S, mower.getDirection());
    }
}
