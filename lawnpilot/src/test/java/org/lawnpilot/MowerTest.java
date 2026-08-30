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
    void movesForwardWest() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(2, 2, Direction.W);

        mower.execute("F", lawn);

        assertEquals(1, mower.getX());
        assertEquals(2, mower.getY());
        assertEquals(Direction.W, mower.getDirection());
    }

    @Test
    void movesForwardSouth() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(2, 2, Direction.S);

        mower.execute("F", lawn);

        assertEquals(2, mower.getX());
        assertEquals(1, mower.getY());
        assertEquals(Direction.S, mower.getDirection());
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

    @Test
    void blocksMoveWestAtLeftBoundary() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(0, 3, Direction.W);

        mower.execute("F", lawn);

        assertEquals(0, mower.getX());
        assertEquals(3, mower.getY());
        assertEquals(Direction.W, mower.getDirection());
    }

    @Test
    void blocksMoveNorthAtTopBoundary() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(4, 5, Direction.N);

        mower.execute("F", lawn);

        assertEquals(4, mower.getX());
        assertEquals(5, mower.getY());
        assertEquals(Direction.N, mower.getDirection());
    }

    @Test
    void blocksMoveEastAtRightBoundary() {
        Lawn lawn = new Lawn(5, 5);
        Mower mower = new Mower(5, 2, Direction.E);

        mower.execute("F", lawn);

        assertEquals(5, mower.getX());
        assertEquals(2, mower.getY());
        assertEquals(Direction.E, mower.getDirection());
    }
}
