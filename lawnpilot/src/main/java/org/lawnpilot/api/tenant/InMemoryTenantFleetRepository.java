package org.lawnpilot.api.tenant;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTenantFleetRepository implements TenantFleetRepository {

    private final Map<String, TenantState> tenantStates = new ConcurrentHashMap<>();

    @Override
    public TenantState getOrCreateTenantState(String tenantId) {
        return tenantStates.computeIfAbsent(tenantId, key -> new TenantState());
    }

    @Override
    public Optional<TenantState> findTenantState(String tenantId) {
        return Optional.ofNullable(tenantStates.get(tenantId));
    }
}
