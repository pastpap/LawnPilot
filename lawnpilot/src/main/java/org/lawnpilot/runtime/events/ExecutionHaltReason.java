package org.lawnpilot.runtime.events;

public enum ExecutionHaltReason {
    COMPLETED,
    MAX_STEPS_REACHED,
    TIMEOUT_REACHED
}