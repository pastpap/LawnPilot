package org.lawnpilot.exceptions;

public class TenantValidationException extends RuntimeException {

    public TenantValidationException(String message) {
        super(message);
    }
}
