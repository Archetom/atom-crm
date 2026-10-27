package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.ApprovalConditionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.policy.ApprovalFactPolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
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
class ApprovalApplicationSupport {

    private final FieldDefinitionRepository fieldRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantRoleRepository roleRepository;

    Map<FieldCode, FieldDefinition> fields(
            TenantId tenantId, BusinessObjectType objectType) {
        return fieldRepository.findAll(tenantId, objectType, false).stream()
                .filter(FieldDefinition::isActive)
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, CustomFieldType> searchableTypes(
            Map<FieldCode, FieldDefinition> fields) {
        return fields.values().stream().filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        FieldDefinition::getFieldType));
    }

    Map<FieldCode, List<String>> normalizeFacts(
            Map<String, List<String>> requested,
            Map<FieldCode, FieldDefinition> fields) {
        if (requested == null) {
            return Map.of();
        }
        LinkedHashMap<FieldCode, List<String>> result = new LinkedHashMap<>();
        requested.forEach((rawCode, values) -> {
            FieldCode code;
            try {
                code = new FieldCode(rawCode);
            } catch (RuntimeException exception) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval field code is invalid");
            }
            FieldDefinition field = fields.get(code);
            if (field == null || !field.isSearchable()) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval routing field is unavailable");
            }
            if (result.putIfAbsent(code, normalizeFact(field, values)) != null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval routing field is duplicated");
            }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    List<ApprovalStepDefinition> steps(
            List<ApprovalStepRequest> requests,
            Map<FieldCode, FieldDefinition> fields) {
        if (requests == null) {
            return List.of();
        }
        Map<FieldCode, CustomFieldType> types = searchableTypes(fields);
        return requests.stream().map(request -> {
            if (request == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval step is invalid");
            }
            List<FilterCondition> conditions = request.getConditions() == null ? List.of()
                    : request.getConditions().stream().map(value -> condition(value, fields)).toList();
            SavedViewPolicy.validate(conditions, types);
            return new ApprovalStepDefinition(request.getCode(), request.getName(), request.getOrder(),
                    parse(ApprovalStepType.class, request.getType(), "Approval step type"),
                    parse(ApprovalStepMode.class, request.getMode(), "Approval step mode"),
                    parse(ApprovalFallback.class, request.getFallback(), "Approval fallback"),
                    memberIds(request.getMemberActorIds()).stream().toList(),
                    roleIds(request.getRoleIds()).stream().toList(), conditions);
        }).toList();
    }

    void validateReferences(
            TenantId tenantId,
            Collection<MemberId> members,
            Collection<DepartmentId> departments,
            Collection<RoleId> roles) {
        Set<MemberId> requestedMembers = members == null ? Set.of()
                : members.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!requestedMembers.isEmpty()) {
            Set<MemberId> active = membershipRepository.findByMembers(tenantId, requestedMembers)
                    .stream().filter(OrganizationMembership::isActive)
                    .map(OrganizationMembership::getMemberId).collect(Collectors.toSet());
            if (!active.containsAll(requestedMembers)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval member reference is invalid");
            }
        }
        Set<DepartmentId> requestedDepartments = departments == null ? Set.of()
                : departments.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!requestedDepartments.isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, new ArrayList<>(requestedDepartments)).stream()
                    .filter(Department::isActive).map(Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(requestedDepartments)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval department reference is invalid");
            }
        }
        Set<RoleId> requestedRoles = roles == null ? Set.of()
                : roles.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!requestedRoles.isEmpty()) {
            Set<RoleId> active = roleRepository.findByIds(tenantId, requestedRoles).stream()
                    .filter(TenantRole::isActive).map(TenantRole::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(requestedRoles)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval role reference is invalid");
            }
        }
    }

    void validateDefinitionReferences(TenantId tenantId, ApprovalDefinition definition) {
        LinkedHashSet<MemberId> members = new LinkedHashSet<>();
        members.addAll(definition.getInitiatorMemberIds());
        members.addAll(definition.getManagerMemberIds());
        LinkedHashSet<RoleId> roles = new LinkedHashSet<>();
        definition.getSteps().forEach(step -> {
            members.addAll(step.memberIds());
            roles.addAll(step.roleIds());
        });
        validateReferences(tenantId, members,
                definition.getInitiatorDepartmentIds(), roles);
    }

    OrganizationMembership activeMembership(TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED,
                        "Caller is not an active tenant member"));
    }

    void requireVersion(ApprovalDefinition definition, Long version) {
        if (version == null || !version.equals(definition.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Approval definition version is stale");
        }
    }

    void requireVersion(ApprovalInstance instance, Long version) {
        if (version == null || !version.equals(instance.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Approval instance version is stale");
        }
    }

    Set<MemberId> memberIds(Collection<Long> values) {
        if (values == null) {
            return Set.of();
        }
        requirePositiveIds(values, "Approval member reference is invalid");
        return values.stream().map(MemberId::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<DepartmentId> departmentIds(Collection<Long> values) {
        if (values == null) {
            return Set.of();
        }
        requirePositiveIds(values, "Approval department reference is invalid");
        return values.stream().map(DepartmentId::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<RoleId> roleIds(Collection<Long> values) {
        if (values == null) {
            return Set.of();
        }
        requirePositiveIds(values, "Approval role reference is invalid");
        return values.stream().map(RoleId::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    <E extends Enum<E>> E parseOptional(Class<E> type, String value, String description) {
        return value == null || value.isBlank() ? null : parse(type, value, description);
    }

    NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private FilterCondition condition(
            ApprovalConditionRequest request,
            Map<FieldCode, FieldDefinition> fields) {
        if (request == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval condition is invalid");
        }
        FieldCode code;
        try {
            code = new FieldCode(request.getFieldCode());
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval condition field is invalid");
        }
        FieldDefinition field = fields.get(code);
        if (field == null || !field.isSearchable()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval condition field is unavailable");
        }
        FilterOperator operator = parse(FilterOperator.class, request.getOperator(),
                "Approval condition operator");
        List<String> values = normalizeConditionValues(field, operator, request.getValues());
        return new FilterCondition(code, operator, values);
    }

    private List<String> normalizeConditionValues(
            FieldDefinition field, FilterOperator operator, List<String> values) {
        List<String> source = values == null ? List.of() : values;
        if (source.stream().anyMatch(java.util.Objects::isNull)) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval condition value is invalid");
        }
        if (operator == FilterOperator.IS_EMPTY || operator == FilterOperator.IS_NOT_EMPTY) {
            return List.copyOf(source);
        }
        if (operator == FilterOperator.BETWEEN || operator == FilterOperator.IN) {
            return source.stream().flatMap(value ->
                    normalizeFact(field, List.of(value)).stream()).toList();
        }
        return normalizeFact(field, source);
    }

    List<String> normalizeFact(FieldDefinition field, List<String> values) {
        try {
            return ApprovalFactPolicy.normalize(field, values);
        } catch (ApprovalDomainException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval field value is invalid");
        }
    }

    List<String> normalizeSnapshotFact(FieldDefinition field, List<String> values) {
        try {
            return ApprovalFactPolicy.normalizeStored(field, values);
        } catch (ApprovalDomainException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval field value is invalid");
        }
    }

    private void requirePositiveIds(Collection<Long> values, String message) {
        if (values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, message);
        }
    }
}
