package org.lawnpilot.runtime.events;

public final class ReplayVerificationResult {

    private final boolean success;
    private final String message;

    public ReplayVerificationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}