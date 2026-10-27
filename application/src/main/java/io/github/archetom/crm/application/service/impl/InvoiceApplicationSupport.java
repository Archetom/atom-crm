package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.FieldReadability;
import io.github.archetom.crm.application.assembler.InvoiceAssembler;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.vo.InvoiceVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.InvoiceFieldValue;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.policy.InvoiceFieldValuePolicy;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class InvoiceApplicationSupport {
    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    List<FieldDefinition> fields(TenantId tenantId) {
        return fieldRepository.findAll(tenantId, BusinessObjectType.INVOICE, true);
    }

    Map<FieldCode, FieldDefinition> fieldsByCode(List<FieldDefinition> fields) {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(TenantId tenantId, MemberId memberId) {
        return fieldAccessResolver.resolve(tenantId, memberId, BusinessObjectType.INVOICE);
    }

    void requireWrite(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED, "Invoice field is not writable");
        }
    }

    void requireReadableUnmasked(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        if (!FieldReadability.readableUnmasked(accesses, code)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED, "Invoice field is not readable");
        }
    }

    List<InvoiceFieldValue> customFields(
            Map<String, List<String>> requested,
            Collection<InvoiceFieldValue> existing,
            boolean replaceAll,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, InvoiceFieldValue> values = new LinkedHashMap<>();
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
                            "Invoice field code is invalid");
                }
                FieldDefinition field = fields.get(code);
                if (field == null || field.isBuiltin() || !field.isActive()) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Invoice field is unavailable");
                }
                requireWrite(accesses, code.getValue());
                InvoiceFieldValue value = InvoiceFieldValuePolicy.normalize(
                        field, entry.getValue() == null ? List.of() : entry.getValue());
                if (value == null) {
                    values.remove(code);
                } else {
                    values.put(code, value);
                }
            }
        }
        for (FieldDefinition field : fields.values()) {
            if (!field.isBuiltin() && field.isActive() && field.isRequired()
                    && !values.containsKey(field.getCode())) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "A required invoice field is empty");
            }
        }
        validateReferences(fields, values.values());
        return List.copyOf(values.values());
    }

    OrganizationMembership requireActiveMember(TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Invoice owner is not an active tenant member"));
    }

    void requireVersion(Invoice invoice, Long version) {
        if (version == null || !version.equals(invoice.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT, "Invoice version is stale");
        }
    }

    InvoiceVO toVO(
            Invoice value, Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, boolean contractVisible) {
        return InvoiceAssembler.toVO(value, accesses, customerVisible, contractVisible);
    }

    <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is invalid");
        }
    }

    NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private void validateReferences(
            Map<FieldCode, FieldDefinition> fields,
            Collection<InvoiceFieldValue> values) {
        Set<MemberId> members = new LinkedHashSet<>();
        Set<DepartmentId> departments = new LinkedHashSet<>();
        TenantId tenantId = null;
        for (InvoiceFieldValue value : values) {
            FieldDefinition field = fields.get(value.fieldCode());
            if (field == null || !field.isActive()) {
                continue;
            }
            tenantId = field.getTenantId();
            if (field.getFieldType() == CustomFieldType.USER) {
                value.values().stream().map(Long::valueOf).map(MemberId::new)
                        .forEach(members::add);
            } else if (field.getFieldType() == CustomFieldType.DEPARTMENT) {
                value.values().stream().map(Long::valueOf).map(DepartmentId::new)
                        .forEach(departments::add);
            } else if (field.getFieldType() == CustomFieldType.RELATION) {
                throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Invoice relation fields require an owning business module");
            }
        }
        if (tenantId == null) {
            return;
        }
        if (!members.isEmpty()) {
            Set<MemberId> active = membershipRepository.findByMembers(tenantId, members)
                    .stream().filter(OrganizationMembership::isActive)
                    .map(OrganizationMembership::getMemberId).collect(Collectors.toSet());
            if (!active.containsAll(members)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Invoice member field is invalid");
            }
        }
        if (!departments.isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, new ArrayList<>(departments)).stream()
                    .filter(io.github.archetom.crm.domain.entity.Department::isActive)
                    .map(io.github.archetom.crm.domain.entity.Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(departments)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Invoice department field is invalid");
            }
        }
    }
}
