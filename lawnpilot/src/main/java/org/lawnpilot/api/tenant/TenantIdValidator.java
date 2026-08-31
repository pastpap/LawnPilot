package org.lawnpilot.api.tenant;

import java.util.regex.Pattern;
import org.lawnpilot.exceptions.TenantValidationException;

final class TenantIdValidator {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private TenantIdValidator() {
    }

    static String requireValidTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new TenantValidationException("Tenant id must not be blank.");
        }

        String normalizedTenantId = tenantId.trim();
        if (!TENANT_ID_PATTERN.matcher(normalizedTenantId).matches()) {
            throw new TenantValidationException(
                    "Tenant id '" + tenantId + "' is invalid. Use 1-64 characters: letters, digits, '_' or '-'.");
        }

        return normalizedTenantId;
    }
}
