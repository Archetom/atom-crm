package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.CustomerAddressRequest;
import io.github.archetom.crm.api.dto.request.CustomerCollaboratorRequest;
import io.github.archetom.crm.application.assembler.CustomerAssembler;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerAddress;
import io.github.archetom.crm.domain.entity.CustomerCollaborator;
import io.github.archetom.crm.domain.entity.CustomerFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerCollaboratorAccess;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.policy.CustomerFieldValuePolicy;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class CustomerApplicationSupport {

    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    List<FieldDefinition> fields(TenantId tenantId) {
        return fieldRepository.findAll(tenantId, BusinessObjectType.CUSTOMER, true);
    }

    Map<FieldCode, FieldDefinition> fieldsByCode(List<FieldDefinition> fields) {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(TenantId tenantId, MemberId memberId) {
        return fieldAccessResolver.resolve(tenantId, memberId, BusinessObjectType.CUSTOMER);
    }

    CustomerVO toVO(
            Customer customer, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<CustomerId> visibleRelations) {
        return CustomerAssembler.toVO(customer, starred, accesses, visibleRelations);
    }

    CustomerVO toVO(
            Customer customer, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<CustomerId> visibleRelations,
            boolean collaboratorsVisible) {
        return CustomerAssembler.toVO(
                customer, starred, accesses, visibleRelations, collaboratorsVisible);
    }

    CustomerVO toVO(
            Customer customer, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<CustomerId> visibleRelations,
            LocalDateTime lastActivityTime,
            String lastActivityContent) {
        return CustomerAssembler.toVO(customer, starred, accesses, visibleRelations, false,
                lastActivityTime, lastActivityContent);
    }

    void requireWrite(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Customer field is not writable");
        }
    }

    List<CustomerAddress> addresses(List<CustomerAddressRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            if (request == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer address is invalid");
            }
            String key = request.getKey() == null || request.getKey().isBlank()
                    ? UUID.randomUUID().toString().replace("-", "") : request.getKey();
            return new CustomerAddress(key, request.getLabel(), request.getCountry(),
                    request.getRegion(), request.getCity(), request.getPostalCode(),
                    request.getAddressLine(), request.getLongitude(), request.getLatitude(),
                    Boolean.TRUE.equals(request.getPrimary()));
        }).toList();
    }

    List<CustomerCollaborator> collaborators(
            List<CustomerCollaboratorRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            if (request == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer collaborator is invalid");
            }
            return new CustomerCollaborator(new MemberId(request.getActorId()),
                    parse(CustomerCollaboratorAccess.class, request.getAccess(),
                            "Customer collaborator access"));
        }).toList();
    }

    List<CustomerFieldValue> customFields(
            Map<String, List<String>> requested,
            Collection<CustomerFieldValue> existing,
            boolean replaceAll,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, CustomerFieldValue> values = new LinkedHashMap<>();
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
                            "Customer field code is invalid");
                }
                FieldDefinition field = fields.get(code);
                if (field == null || field.isBuiltin() || !field.isActive()) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Customer field is unavailable");
                }
                requireWrite(accesses, code.getValue());
                List<String> input = entry.getValue() == null ? List.of() : entry.getValue();
                CustomerFieldValue value = CustomerFieldValuePolicy.normalize(field, input);
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
                        "A required customer field is empty");
            }
        }
        validateReferences(fields, values.values());
        return List.copyOf(values.values());
    }

    void requireActiveMembers(TenantId tenantId, Collection<MemberId> memberIds) {
        Set<MemberId> requested = memberIds == null ? Set.of() : memberIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            return;
        }
        Set<MemberId> active = membershipRepository.findByMembers(tenantId, requested).stream()
                .filter(OrganizationMembership::isActive)
                .map(OrganizationMembership::getMemberId)
                .collect(Collectors.toSet());
        if (!active.containsAll(requested)) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer member reference is invalid");
        }
    }

    OrganizationMembership requireActiveMember(TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer owner is not an active tenant member"));
    }

    void requireVersion(Customer customer, Long version) {
        if (version == null || !version.equals(customer.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Customer version is stale");
        }
    }

    <E extends Enum<E>> E parse(
            Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is invalid");
        }
    }

    NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private void validateReferences(
            Map<FieldCode, FieldDefinition> fields,
            Collection<CustomerFieldValue> values) {
        Set<MemberId> memberIds = new LinkedHashSet<>();
        Set<DepartmentId> departmentIds = new LinkedHashSet<>();
        TenantId tenantId = null;
        for (CustomerFieldValue value : values) {
            FieldDefinition field = fields.get(value.fieldCode());
            if (field == null || !field.isActive()) {
                continue;
            }
            tenantId = field.getTenantId();
            if (field.getFieldType() == CustomFieldType.USER) {
                value.values().stream().map(Long::valueOf).map(MemberId::new).forEach(memberIds::add);
            } else if (field.getFieldType() == CustomFieldType.DEPARTMENT) {
                value.values().stream().map(Long::valueOf).map(DepartmentId::new).forEach(departmentIds::add);
            } else if (field.getFieldType() == CustomFieldType.RELATION) {
                throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Customer relation fields require an owning business module");
            }
        }
        if (tenantId == null) {
            return;
        }
        requireActiveMembers(tenantId, memberIds);
        if (!departmentIds.isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, new ArrayList<>(departmentIds)).stream()
                    .filter(io.github.archetom.crm.domain.entity.Department::isActive)
                    .map(io.github.archetom.crm.domain.entity.Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(departmentIds)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer department field is invalid");
            }
        }
    }
}
