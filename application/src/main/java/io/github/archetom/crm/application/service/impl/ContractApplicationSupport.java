package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.ContractCollaboratorRequest;
import io.github.archetom.crm.application.security.FieldReadability;
import io.github.archetom.crm.application.assembler.ContractAssembler;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.vo.ContractVO;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.ContractCollaborator;
import io.github.archetom.crm.domain.entity.ContractFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractCollaboratorAccess;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.policy.ContractFieldValuePolicy;
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

import java.time.LocalDate;
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
class ContractApplicationSupport {
    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    List<FieldDefinition> fields(TenantId tenantId) {
        return fieldRepository.findAll(tenantId, BusinessObjectType.CONTRACT, true);
    }

    Map<FieldCode, FieldDefinition> fieldsByCode(List<FieldDefinition> fields) {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(TenantId tenantId, MemberId memberId) {
        return fieldAccessResolver.resolve(tenantId, memberId, BusinessObjectType.CONTRACT);
    }

    void requireWrite(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED, "Contract field is not writable");
        }
    }

    void requireReadableUnmasked(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        if (!FieldReadability.readableUnmasked(accesses, code)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED, "Contract field is not readable");
        }
    }

    List<ContractCollaborator> collaborators(List<ContractCollaboratorRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            if (request == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract collaborator is invalid");
            }
            LocalDate effectiveUntil = request.getEffectiveUntil();
            if (effectiveUntil != null && effectiveUntil.isBefore(LocalDate.now())) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract collaborator effective-until is in the past");
            }
            return new ContractCollaborator(new MemberId(request.getActorId()),
                    parse(ContractCollaboratorAccess.class, request.getAccess(),
                            "Contract collaborator access"), effectiveUntil);
        }).toList();
    }

    List<ContractFieldValue> customFields(
            Map<String, List<String>> requested,
            Collection<ContractFieldValue> existing,
            boolean replaceAll,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, ContractFieldValue> values = new LinkedHashMap<>();
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
                            "Contract field code is invalid");
                }
                FieldDefinition field = fields.get(code);
                if (field == null || field.isBuiltin() || !field.isActive()) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Contract field is unavailable");
                }
                requireWrite(accesses, code.getValue());
                ContractFieldValue value = ContractFieldValuePolicy.normalize(
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
                        "A required contract field is empty");
            }
        }
        validateReferences(fields, values.values());
        return List.copyOf(values.values());
    }

    void requireActiveMembers(TenantId tenantId, Collection<MemberId> ids) {
        Set<MemberId> requested = ids == null ? Set.of() : ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            return;
        }
        Set<MemberId> active = membershipRepository.findByMembers(tenantId, requested)
                .stream().filter(OrganizationMembership::isActive)
                .map(OrganizationMembership::getMemberId).collect(Collectors.toSet());
        if (!active.containsAll(requested)) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract member reference is invalid");
        }
    }

    OrganizationMembership requireActiveMember(TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract owner is not an active tenant member"));
    }

    void requireVersion(Contract contract, Long version) {
        if (version == null || !version.equals(contract.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT, "Contract version is stale");
        }
    }

    ContractVO toVO(
            Contract value, Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, boolean opportunityVisible, boolean contactVisible,
            boolean productsVisible, boolean collaboratorsVisible) {
        return ContractAssembler.toVO(value, accesses, customerVisible,
                opportunityVisible, contactVisible, productsVisible, collaboratorsVisible);
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
            Collection<ContractFieldValue> values) {
        Set<MemberId> members = new LinkedHashSet<>();
        Set<DepartmentId> departments = new LinkedHashSet<>();
        TenantId tenantId = null;
        for (ContractFieldValue value : values) {
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
                        "Contract relation fields require an owning business module");
            }
        }
        if (tenantId == null) {
            return;
        }
        requireActiveMembers(tenantId, members);
        if (!departments.isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, new ArrayList<>(departments)).stream()
                    .filter(io.github.archetom.crm.domain.entity.Department::isActive)
                    .map(io.github.archetom.crm.domain.entity.Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(departments)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract department field is invalid");
            }
        }
    }
}
