package org.lawnpilot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionTest {

    @Test
    void leftRotationCycleReturnsToNorth() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(1, 1, Direction.N);

        mower.execute("LLLL", lawn);

        assertEquals(Direction.N, mower.getDirection());
    }

    @Test
    void rightRotationCycleReturnsToNorth() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(1, 1, Direction.N);

        mower.execute("RRRR", lawn);

        assertEquals(Direction.N, mower.getDirection());
    }
}
