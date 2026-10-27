package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.OpportunityCollaboratorRequest;
import io.github.archetom.crm.application.assembler.OpportunityAssembler;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.vo.OpportunityEditableBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OpportunityCollaborator;
import io.github.archetom.crm.domain.entity.OpportunityFieldValue;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.model.OpportunityCollaboratorAccess;
import io.github.archetom.crm.domain.policy.OpportunityFieldValuePolicy;
import io.github.archetom.crm.domain.policy.OpportunityTemporalValuePolicy;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
class OpportunityApplicationSupport {

    private static final List<String> EDITABLE_BASELINE_FIELDS = List.of(
            OpportunityFieldCatalog.NAME,
            OpportunityFieldCatalog.CUSTOMER,
            OpportunityFieldCatalog.PIPELINE_CODE,
            OpportunityFieldCatalog.STAGE_CODE,
            OpportunityFieldCatalog.EXPECTED_CLOSE_DATE,
            OpportunityFieldCatalog.ESTIMATED_AMOUNT,
            OpportunityFieldCatalog.NEXT_CONTACT_TIME,
            OpportunityFieldCatalog.DESCRIPTION);

    record TemporalValues(
            LocalDate expectedCloseDate, LocalDateTime nextContactTime) {
    }

    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    List<FieldDefinition> fields(TenantId tenantId) {
        return fieldRepository.findAll(
                tenantId, BusinessObjectType.OPPORTUNITY, true);
    }

    Map<FieldCode, FieldDefinition> fieldsByCode(List<FieldDefinition> fields) {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(
            TenantId tenantId, MemberId memberId) {
        return fieldAccessResolver.resolve(
                tenantId, memberId, BusinessObjectType.OPPORTUNITY);
    }

    OpportunityVO toVO(
            Opportunity opportunity, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, Set<ContactId> visibleContacts,
            boolean collaboratorsVisible) {
        return OpportunityAssembler.toVO(opportunity, starred, accesses,
                customerVisible, visibleContacts, collaboratorsVisible);
    }

    OpportunityVO toVO(
            Opportunity opportunity, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, Set<ContactId> visibleContacts,
            boolean collaboratorsVisible,
            LocalDateTime lastActivityTime) {
        return OpportunityAssembler.toVO(opportunity, starred, accesses,
                customerVisible, visibleContacts, collaboratorsVisible, lastActivityTime);
    }

    OpportunityEditableBaselineVO editableBaseline(
            Opportunity opportunity, Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (!canReturnEditableBaseline(accesses)) {
            return new OpportunityEditableBaselineVO(opportunity.getId().getValue(),
                    opportunity.getVersion(), false, null, null, null, null,
                    null, null, null, null);
        }
        return new OpportunityEditableBaselineVO(opportunity.getId().getValue(),
                opportunity.getVersion(), true, opportunity.getName(),
                opportunity.getCustomerId().getValue(), opportunity.getPipelineCode(),
                opportunity.getStageCode(), opportunity.getExpectedCloseDate(),
                opportunity.getEstimatedAmount(), opportunity.getNextContactTime(),
                opportunity.getDescription());
    }

    boolean canReturnContactsBaseline(Map<FieldCode, EffectiveFieldAccess> accesses) {
        return readableUnmasked(accesses, OpportunityFieldCatalog.CUSTOMER)
                && readableUnmasked(accesses, OpportunityFieldCatalog.CONTACTS)
                && readableUnmasked(accesses, OpportunityFieldCatalog.PRIMARY_CONTACT)
                && writable(accesses, OpportunityFieldCatalog.CONTACTS)
                && writable(accesses, OpportunityFieldCatalog.PRIMARY_CONTACT);
    }

    void requireWrite(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Opportunity field is not writable");
        }
    }

    void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Opportunity field is not readable");
        }
    }

    TemporalValues normalizeTemporalValues(
            LocalDate expectedCloseDate, LocalDateTime nextContactTime) {
        try {
            return new TemporalValues(
                    OpportunityTemporalValuePolicy.requireDate(
                            expectedCloseDate, "opportunity expected close date"),
                    OpportunityTemporalValuePolicy.normalizeDateTime(
                            nextContactTime, "opportunity next contact time"));
        } catch (OpportunityDomainException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity date is outside the supported range");
        }
    }

    List<OpportunityCollaborator> collaborators(
            List<OpportunityCollaboratorRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            if (request == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Opportunity collaborator is invalid");
            }
            return new OpportunityCollaborator(
                    new MemberId(request.getActorId()),
                    parse(OpportunityCollaboratorAccess.class, request.getAccess(),
                            "Opportunity collaborator access"));
        }).toList();
    }

    List<OpportunityFieldValue> customFields(
            Map<String, List<String>> requested,
            Collection<OpportunityFieldValue> existing,
            boolean replaceAll,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, OpportunityFieldValue> values = new LinkedHashMap<>();
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
                            "Opportunity field code is invalid");
                }
                FieldDefinition field = fields.get(code);
                if (field == null || field.isBuiltin() || !field.isActive()) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Opportunity field is unavailable");
                }
                requireWrite(accesses, code.getValue());
                OpportunityFieldValue value = OpportunityFieldValuePolicy.normalize(
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
                        "A required opportunity field is empty");
            }
        }
        validateReferences(fields, values.values());
        return List.copyOf(values.values());
    }

    void requireActiveMembers(
            TenantId tenantId, Collection<MemberId> memberIds) {
        Set<MemberId> requested = memberIds == null ? Set.of()
                : memberIds.stream().filter(java.util.Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            return;
        }
        Set<MemberId> active = membershipRepository.findByMembers(tenantId, requested)
                .stream().filter(OrganizationMembership::isActive)
                .map(OrganizationMembership::getMemberId).collect(Collectors.toSet());
        if (!active.containsAll(requested)) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity member reference is invalid");
        }
    }

    OrganizationMembership requireActiveMember(
            TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Opportunity owner is not an active tenant member"));
    }

    void requireVersion(Opportunity opportunity, Long version) {
        if (version == null || !version.equals(opportunity.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Opportunity version is stale");
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

    NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private boolean canReturnEditableBaseline(
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return EDITABLE_BASELINE_FIELDS.stream().allMatch(code -> {
            EffectiveFieldAccess access = accesses.get(new FieldCode(code));
            return access != null && access.accessLevel() == FieldAccessLevel.WRITE
                    && access.maskType() == FieldMaskType.NONE;
        });
    }

    private boolean readableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        return access != null && access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }

    private boolean writable(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        return access != null && access.accessLevel() == FieldAccessLevel.WRITE;
    }

    private void validateReferences(
            Map<FieldCode, FieldDefinition> fields,
            Collection<OpportunityFieldValue> values) {
        Set<MemberId> members = new LinkedHashSet<>();
        Set<DepartmentId> departments = new LinkedHashSet<>();
        TenantId tenantId = null;
        for (OpportunityFieldValue value : values) {
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
                        "Opportunity relation fields require an owning business module");
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
                        "Opportunity department field is invalid");
            }
        }
    }
}
