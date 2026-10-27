package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.RoleFieldPolicy;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.RoleFieldPolicyRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EffectiveFieldAccessResolverImpl implements EffectiveFieldAccessResolver {

    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final FieldDefinitionRepository fieldRepository;
    private final RoleFieldPolicyRepository policyRepository;

    @Override
    public Map<FieldCode, EffectiveFieldAccess> resolve(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType) {
        List<FieldDefinition> fields = fieldRepository.findAll(tenantId, objectType, false);
        return resolve(tenantId, memberId, objectType, fields);
    }

    @Override
    public Map<FieldCode, EffectiveFieldAccess> resolve(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
            Collection<FieldDefinition> fields) {
        requireScope(tenantId, objectType, fields);
        if (fields.isEmpty()) {
            return Map.of();
        }
        TenantAuthorizationEvaluator.Evaluation evaluation =
                authorizationEvaluator.evaluate(tenantId, memberId);
        if (!evaluation.activeMember()) {
            return FieldAccessPolicy.effective(fields, List.of(), List.of());
        }
        List<RoleId> roleIds = evaluation.roles().stream().map(role -> role.getId()).toList();
        List<RoleFieldPolicy> policies = policyRepository.findByRolesAndFields(
                tenantId, roleIds, objectType,
                fields.stream().map(FieldDefinition::getCode).toList());
        return FieldAccessPolicy.effective(fields, evaluation.roles(), policies);
    }

    private void requireScope(
            TenantId tenantId, BusinessObjectType objectType,
            Collection<FieldDefinition> fields) {
        if (tenantId == null || objectType == null || fields == null) {
            throw new IllegalArgumentException("field access scope is required");
        }
        for (FieldDefinition field : fields) {
            if (field == null || field.getTenantId() == null
                    || !tenantId.sameValueAs(field.getTenantId())
                    || field.getObjectType() != objectType) {
                throw new IllegalArgumentException(
                        "field access definitions do not match the requested scope");
            }
        }
    }
}
