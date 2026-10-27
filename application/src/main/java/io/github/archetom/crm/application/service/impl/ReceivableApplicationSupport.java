package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.FieldReadability;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.ReceivableFieldValue;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.policy.ReceivableFieldValuePolicy;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ReceivableApplicationSupport {
    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    List<FieldDefinition> fields(TenantId tenantId, BusinessObjectType type) {
        return fieldRepository.findAll(tenantId, type, true);
    }

    Map<FieldCode, FieldDefinition> fieldsByCode(List<FieldDefinition> fields) {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(
            TenantId tenantId, MemberId memberId, BusinessObjectType type) {
        return fieldAccessResolver.resolve(tenantId, memberId, type);
    }

    void requireWrite(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED, "Receivable field is not writable");
        }
    }

    void requireReadableUnmasked(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        if (!FieldReadability.readableUnmasked(accesses, code)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED, "Receivable field is not readable");
        }
    }

    void requireAnyReadableUnmasked(Map<FieldCode, EffectiveFieldAccess> accesses, String... codes) {
        for (String code : codes) {
            if (readableUnmasked(accesses, code)) return;
        }
        throw error(ApplicationErrorCode.ACCESS_DENIED, "Receivable field is not readable");
    }

    List<ReceivableFieldValue> customFields(
            Map<String, List<String>> requested, Collection<ReceivableFieldValue> existing,
            boolean replaceAll, Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses, String subject) {
        Map<FieldCode, ReceivableFieldValue> values = new LinkedHashMap<>();
        if (!replaceAll && existing != null) {
            existing.forEach(value -> values.put(value.fieldCode(), value));
        }
        if (requested != null) {
            for (Map.Entry<String, List<String>> entry : requested.entrySet()) {
                FieldCode code;
                try {
                    code = new FieldCode(entry.getKey());
                } catch (RuntimeException exception) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            subject + " field code is invalid");
                }
                FieldDefinition field = fields.get(code);
                if (field == null || field.isBuiltin() || !field.isActive()) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            subject + " field is unavailable");
                }
                requireWrite(accesses, code.getValue());
                ReceivableFieldValue value = ReceivableFieldValuePolicy.normalize(field,
                        entry.getValue() == null ? List.of() : entry.getValue());
                if (value == null) values.remove(code); else values.put(code, value);
            }
        }
        for (FieldDefinition field : fields.values()) {
            if (!field.isBuiltin() && field.isActive() && field.isRequired()
                    && !values.containsKey(field.getCode())) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "A required " + subject + " field is empty");
            }
        }
        validateReferences(fields, values.values(), subject);
        return List.copyOf(values.values());
    }

    OrganizationMembership requireActiveMember(TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Receivable owner is not an active tenant member"));
    }

    void requireVersion(Long actual, Long expected, String subject) {
        if (expected == null || !expected.equals(actual)) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT, subject + " version is stale");
        }
    }

    NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private boolean readableUnmasked(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return FieldReadability.readableUnmasked(accesses, code);
    }

    private void validateReferences(
            Map<FieldCode, FieldDefinition> fields, Collection<ReceivableFieldValue> values,
            String subject) {
        Set<MemberId> members = new LinkedHashSet<>();
        Set<DepartmentId> departments = new LinkedHashSet<>();
        TenantId tenantId = null;
        for (ReceivableFieldValue value : values) {
            FieldDefinition field = fields.get(value.fieldCode());
            if (field == null || !field.isActive()) continue;
            tenantId = field.getTenantId();
            if (field.getFieldType() == CustomFieldType.USER) {
                value.values().stream().map(Long::valueOf).map(MemberId::new).forEach(members::add);
            } else if (field.getFieldType() == CustomFieldType.DEPARTMENT) {
                value.values().stream().map(Long::valueOf).map(DepartmentId::new).forEach(departments::add);
            } else if (field.getFieldType() == CustomFieldType.RELATION) {
                throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        subject + " relation fields require an owning business module");
            }
        }
        if (tenantId == null) return;
        if (!members.isEmpty()) {
            Set<MemberId> active = membershipRepository.findByMembers(tenantId, members).stream()
                    .filter(OrganizationMembership::isActive).map(OrganizationMembership::getMemberId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(members)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID, subject + " member field is invalid");
            }
        }
        if (!departments.isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                    tenantId, new ArrayList<>(departments)).stream().filter(Department::isActive)
                    .map(Department::getId).collect(Collectors.toSet());
            if (!active.containsAll(departments)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        subject + " department field is invalid");
            }
        }
    }
}
