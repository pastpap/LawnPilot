package org.lawnpilot.api;

import org.lawnpilot.exceptions.ConflictException;
import org.lawnpilot.exceptions.GuardrailViolationException;
import org.lawnpilot.exceptions.InvalidInputException;
import org.lawnpilot.exceptions.NotFoundException;
import org.lawnpilot.exceptions.RoleAuthorizationException;
import org.lawnpilot.exceptions.RoleValidationException;
import org.lawnpilot.exceptions.TenantValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<String> handleInvalidInput(InvalidInputException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler({ TenantValidationException.class, RoleValidationException.class })
    public ResponseEntity<String> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(RoleAuthorizationException.class)
    public ResponseEntity<String> handleForbidden(RoleAuthorizationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    // Phase 7: Guardrail violation handling
    @ExceptionHandler(GuardrailViolationException.class)
    public ResponseEntity<String> handleGuardrailViolation(GuardrailViolationException ex) {
        // Return 422 (Unprocessable Entity) for guardrail violations
        // This indicates the request is well-formed but semantically invalid due to
        // safety constraints
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
    }
}
