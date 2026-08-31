package org.lawnpilot.api.tenant;

import java.util.Locale;
import org.lawnpilot.exceptions.RoleValidationException;

public enum TenantRole {
    VIEWER,
    OPERATOR,
    ADMIN;

    public static TenantRole fromHeader(String roleHeader) {
        if (roleHeader == null || roleHeader.trim().isEmpty()) {
            throw new RoleValidationException("Missing required X-Role header. Allowed values: ADMIN, OPERATOR, VIEWER.");
        }

        try {
            return TenantRole.valueOf(roleHeader.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RoleValidationException("Invalid X-Role header '" + roleHeader + "'. Allowed values: ADMIN, OPERATOR, VIEWER.");
        }
    }

    public boolean canMutateTenantData() {
        return this == ADMIN || this == OPERATOR;
    }
}
