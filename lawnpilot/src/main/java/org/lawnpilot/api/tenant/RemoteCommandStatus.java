package org.lawnpilot.api.tenant;

/**
 * Lifecycle states for remote mower commands.
 * 
 * Phase 7: Command execution state machine.
 */
public enum RemoteCommandStatus {
    PENDING("Queued for execution"),
    EXECUTING("Command is executing"),
    COMPLETED("Command completed successfully"),
    FAILED("Command execution failed"),
    REJECTED("Command rejected by guardrails");

    private final String description;

    RemoteCommandStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
