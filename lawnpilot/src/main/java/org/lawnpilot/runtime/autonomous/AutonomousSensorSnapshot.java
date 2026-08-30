package org.lawnpilot.runtime.autonomous;

public final class AutonomousSensorSnapshot {

    private final boolean boundaryAhead;
    private final boolean mowerAhead;
    private final int distanceToBoundaryAhead;

    public AutonomousSensorSnapshot(boolean boundaryAhead, boolean mowerAhead, int distanceToBoundaryAhead) {
        this.boundaryAhead = boundaryAhead;
        this.mowerAhead = mowerAhead;
        this.distanceToBoundaryAhead = distanceToBoundaryAhead;
    }

    public boolean isBoundaryAhead() {
        return boundaryAhead;
    }

    public boolean isMowerAhead() {
        return mowerAhead;
    }

    public int getDistanceToBoundaryAhead() {
        return distanceToBoundaryAhead;
    }
}