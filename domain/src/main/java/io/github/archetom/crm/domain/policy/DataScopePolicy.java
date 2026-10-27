package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;
import io.github.archetom.crm.domain.valueobject.DepartmentId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Explicit coverage and union rules for tenant-bounded data scopes. */
public final class DataScopePolicy {

    private DataScopePolicy() {
    }

    public static boolean covers(DataScope available, DataScope requested) {
        if (available == null || requested == null) {
            return false;
        }
        return switch (available) {
            case TENANT -> true;
            case DEPARTMENT_SUBTREE -> requested != DataScope.TENANT;
            case DEPARTMENT -> requested == DataScope.DEPARTMENT || requested == DataScope.SELF;
            case SELF -> requested == DataScope.SELF;
        };
    }

    public static Optional<DataScope> merge(Collection<DataScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Optional.empty();
        }
        if (scopes.contains(DataScope.TENANT)) {
            return Optional.of(DataScope.TENANT);
        }
        if (scopes.contains(DataScope.DEPARTMENT_SUBTREE)) {
            return Optional.of(DataScope.DEPARTMENT_SUBTREE);
        }
        if (scopes.contains(DataScope.DEPARTMENT)) {
            return Optional.of(DataScope.DEPARTMENT);
        }
        return scopes.contains(DataScope.SELF) ? Optional.of(DataScope.SELF) : Optional.empty();
    }

    public static DataScope restrictToMembership(DataScope scope, DepartmentId departmentId) {
        if (scope == null) {
            throw new IllegalArgumentException("data scope must not be null");
        }
        if (departmentId == null
                && (scope == DataScope.DEPARTMENT || scope == DataScope.DEPARTMENT_SUBTREE)) {
            return DataScope.SELF;
        }
        return scope;
    }

    public static List<RoleGrant> mergeGrants(Collection<RoleGrant> grants) {
        if (grants == null || grants.isEmpty()) {
            return List.of();
        }
        Map<AuthorityCode, List<RoleGrant>> byAuthority = new LinkedHashMap<>();
        for (RoleGrant grant : grants) {
            if (grant != null) {
                byAuthority.computeIfAbsent(grant.authorityCode(), ignored -> new ArrayList<>()).add(grant);
            }
        }
        return byAuthority.entrySet().stream()
                .map(entry -> mergeAuthority(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(grant -> grant.authorityCode().getValue()))
                .toList();
    }

    private static RoleGrant mergeAuthority(AuthorityCode authorityCode, List<RoleGrant> grants) {
        List<DataScope> scopes = grants.stream().map(RoleGrant::dataScope).filter(java.util.Objects::nonNull).toList();
        return new RoleGrant(authorityCode, merge(scopes).orElse(null));
    }
}
