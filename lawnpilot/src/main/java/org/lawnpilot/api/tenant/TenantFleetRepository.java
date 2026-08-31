package org.lawnpilot.api.tenant;

import java.util.Optional;

public interface TenantFleetRepository {

    TenantState getOrCreateTenantState(String tenantId);

    Optional<TenantState> findTenantState(String tenantId);
}
