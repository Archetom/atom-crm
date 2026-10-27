package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.RoleFieldAccess;
import io.github.archetom.crm.domain.entity.RoleFieldPolicy;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.RoleId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit max-permission merge across active roles, with a dynamic owner policy. */
public final class FieldAccessPolicy {

    private FieldAccessPolicy() {
    }

    public static Map<FieldCode, EffectiveFieldAccess> effective(
            Collection<FieldDefinition> fields, Collection<TenantRole> activeRoles,
            Collection<RoleFieldPolicy> policies) {
        Map<FieldCode, EffectiveFieldAccess> result = new LinkedHashMap<>();
        if (fields == null) {
            return Map.of();
        }
        for (FieldDefinition field : fields) {
            if (field != null && field.isActive()) {
                result.put(field.getCode(), EffectiveFieldAccess.hidden());
            }
        }
        if (activeRoles == null || activeRoles.isEmpty()) {
            return Map.copyOf(result);
        }
        if (activeRoles.stream().anyMatch(role -> role != null && role.isActive()
                && role.getSystemKey() == SystemRoleKey.TENANT_OWNER)) {
            result.replaceAll((ignored, access) -> EffectiveFieldAccess.owner());
            return Map.copyOf(result);
        }
        Set<RoleId> roleIds = activeRoles.stream().filter(java.util.Objects::nonNull)
                .filter(TenantRole::isActive)
                .map(TenantRole::getId).collect(Collectors.toSet());
        if (policies == null) {
            return Map.copyOf(result);
        }
        Map<FieldCode, FieldDefinition> fieldsByCode = fields.stream()
                .filter(field -> field != null && field.isActive())
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity()));
        for (RoleFieldPolicy policy : policies) {
            if (policy == null || !roleIds.contains(policy.getRoleId())) {
                continue;
            }
            for (RoleFieldAccess candidate : policy.getAccesses()) {
                if (fieldsByCode.containsKey(candidate.fieldCode())) {
                    result.merge(candidate.fieldCode(), toEffective(candidate), FieldAccessPolicy::morePermissive);
                }
            }
        }
        return Map.copyOf(result);
    }

    /**
     * Intersects an object's fixed keyword-search catalog with current field metadata and access.
     * Missing metadata or access fails closed and masked values are never usable as predicates.
     */
    public static Map<FieldCode, SearchMatchMode> searchableCandidates(
            Collection<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Map<FieldCode, SearchMatchMode> catalog) {
        if (fields == null || accesses == null || catalog == null || catalog.isEmpty()) {
            return Map.of();
        }
        Set<FieldCode> activeSearchable = fields.stream()
                .filter(java.util.Objects::nonNull)
                .filter(FieldDefinition::isActive)
                .filter(FieldDefinition::isSearchable)
                .map(FieldDefinition::getCode)
                .collect(Collectors.toSet());
        Map<FieldCode, SearchMatchMode> result = new LinkedHashMap<>();
        catalog.forEach((code, mode) -> {
            EffectiveFieldAccess access = accesses.get(code);
            if (code != null && mode != null && activeSearchable.contains(code)
                    && access != null && access.accessLevel() != FieldAccessLevel.HIDDEN
                    && access.maskType() == FieldMaskType.NONE) {
                result.put(code, mode);
            }
        });
        return Map.copyOf(result);
    }

    private static EffectiveFieldAccess toEffective(RoleFieldAccess access) {
        return new EffectiveFieldAccess(access.accessLevel(), access.maskType());
    }

    private static EffectiveFieldAccess morePermissive(EffectiveFieldAccess left,
                                                        EffectiveFieldAccess right) {
        FieldAccessLevel level = left.accessLevel().covers(right.accessLevel())
                ? left.accessLevel() : right.accessLevel();
        FieldMaskType mask = left.maskType().ordinal() <= right.maskType().ordinal()
                ? left.maskType() : right.maskType();
        if (level == FieldAccessLevel.WRITE) {
            mask = FieldMaskType.NONE;
        }
        return new EffectiveFieldAccess(level, mask);
    }
}
