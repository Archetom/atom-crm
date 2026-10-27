package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.ReturnVisitRelationStateResponse;
import io.github.archetom.crm.application.security.FieldReadability;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.entity.ReturnVisitFieldValue;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldValuePolicy;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.application.vo.ReturnVisitVO;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ReturnVisitApplicationSupport {
    private static final String REDACTED = "[REDACTED]";

    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final BusinessRecordAccessGuard businessRecordAccessGuard;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;
    private final ApprovalInstanceRepository approvalInstanceRepository;

    List<FieldDefinition> fields(TenantId tenantId) {
        return fieldRepository.findAll(tenantId, BusinessObjectType.RETURN_VISIT, true);
    }

    Map<FieldCode, FieldDefinition> fieldsByCode(Collection<FieldDefinition> fields) {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(TenantId tenantId, MemberId memberId) {
        return fieldAccessResolver.resolve(tenantId, memberId, BusinessObjectType.RETURN_VISIT);
    }

    List<ReturnVisitFieldValue> customFields(Map<String, List<String>> request,
                                              Collection<ReturnVisitFieldValue> existing,
                                              Map<FieldCode, FieldDefinition> fields,
                                              Map<FieldCode, EffectiveFieldAccess> access) {
        Map<String, List<String>> patches = request == null ? Map.of() : request;
        if (patches.size() > ReturnVisit.MAX_CUSTOM_FIELDS) throw invalid("Too many custom fields");
        LinkedHashMap<FieldCode, ReturnVisitFieldValue> values = new LinkedHashMap<>();
        if (existing != null) {
            existing.forEach(value -> values.put(value.fieldCode(), value));
        }
        for (Map.Entry<String, List<String>> entry : patches.entrySet()) {
            FieldCode code;
            try { code = new FieldCode(entry.getKey()); } catch (RuntimeException exception) {
                throw invalid("Return visit custom field is invalid");
            }
            FieldDefinition field = fields.get(code);
            if (field == null || !field.isActive() || field.isBuiltin()) {
                throw invalid("Return visit custom field is unavailable");
            }
            requireWrite(access, code);
            ReturnVisitFieldValue value = ReturnVisitFieldValuePolicy.normalize(field, entry.getValue());
            if (value == null) {
                values.remove(code);
            } else {
                values.put(code, value);
            }
        }
        for (FieldDefinition field : fields.values()) {
            if (!field.isBuiltin() && field.isActive() && field.isRequired()
                    && !values.containsKey(field.getCode())) {
                throw invalid("A required return visit field is missing");
            }
        }
        validateReferences(fields, values.values());
        return List.copyOf(values.values());
    }

    private void validateReferences(Map<FieldCode, FieldDefinition> fields,
                                    Collection<ReturnVisitFieldValue> values) {
        LinkedHashSet<MemberId> members = new LinkedHashSet<>();
        LinkedHashSet<DepartmentId> departments = new LinkedHashSet<>();
        TenantId tenantId = null;
        for (ReturnVisitFieldValue value : values) {
            FieldDefinition field = fields.get(value.fieldCode());
            if (field == null || !field.isActive()) {
                continue;
            }
            tenantId = field.getTenantId();
            if (field.getFieldType() == CustomFieldType.USER) {
                value.values().stream().map(Long::valueOf).map(MemberId::new).forEach(members::add);
            } else if (field.getFieldType() == CustomFieldType.DEPARTMENT) {
                value.values().stream().map(Long::valueOf).map(DepartmentId::new)
                        .forEach(departments::add);
            } else if (field.getFieldType() == CustomFieldType.RELATION) {
                throw new NonRetryableApplicationException(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Return visit relation fields require an owning business module");
            }
        }
        if (tenantId == null) {
            return;
        }
        if (!members.isEmpty()) {
            var active = membershipRepository.findByMembers(tenantId, members).stream()
                    .filter(OrganizationMembership::isActive)
                    .map(OrganizationMembership::getMemberId).collect(Collectors.toSet());
            if (!active.containsAll(members)) {
                throw invalid("Return visit member field is invalid");
            }
        }
        if (!departments.isEmpty()) {
            var active = departmentRepository.findByIds(tenantId, List.copyOf(departments)).stream()
                    .filter(io.github.archetom.crm.domain.entity.Department::isActive)
                    .map(io.github.archetom.crm.domain.entity.Department::getId).collect(Collectors.toSet());
            if (!active.containsAll(departments)) {
                throw invalid("Return visit department field is invalid");
            }
        }
    }

    void requireWrite(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        requireWrite(accesses, new FieldCode(code));
    }

    void requireWrite(Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.getOrDefault(code, EffectiveFieldAccess.hidden());
        if (access.accessLevel() != FieldAccessLevel.WRITE || access.maskType() != FieldMaskType.NONE) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                    "Return visit field is not writable");
        }
    }

    void requireReadableUnmasked(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        if (!FieldReadability.readableUnmasked(accesses, code)) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                    "Return visit field is not readable");
        }
    }

    ReturnVisitVO toVO(AuthenticatedCaller caller, TenantId tenantId, ReturnVisit value,
                       Map<FieldCode, EffectiveFieldAccess> access) {
        return toVOs(caller, tenantId, List.of(value), access).getFirst();
    }

    List<ReturnVisitVO> toVOs(AuthenticatedCaller caller, TenantId tenantId,
                               Collection<ReturnVisit> values,
                               Map<FieldCode, EffectiveFieldAccess> access) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Map<BusinessObjectType, java.util.Set<Long>> visible = visibleRelations(caller, tenantId, values);
        Map<Long, String> approvalStatuses = approvalStatus(tenantId, values);
        return values.stream().map(value -> toVO(value, access, visible,
                approvalStatuses.get(value.getId().getValue()))).toList();
    }

    private ReturnVisitVO toVO(ReturnVisit value, Map<FieldCode, EffectiveFieldAccess> access,
                               Map<BusinessObjectType, java.util.Set<Long>> visible,
                               String approvalStatus) {
        RelationProjection contact = relation(value.getContactId() == null ? null : value.getContactId().getValue(),
                BusinessObjectType.CONTACT,
                visible, access, ReturnVisitFieldCatalog.CONTACT);
        RelationProjection contract = relation(value.getContractId() == null ? null : value.getContractId().getValue(),
                BusinessObjectType.CONTRACT,
                visible, access, ReturnVisitFieldCatalog.CONTRACT);
        return new ReturnVisitVO(value.getId().getValue(), number(value.getOwnerMemberId().getValue(), access,
                ReturnVisitFieldCatalog.OWNER_ACTOR), number(visible.getOrDefault(BusinessObjectType.CUSTOMER,
                java.util.Set.of()).contains(value.getCustomerId().getValue())
                ? value.getCustomerId().getValue() : null, access, ReturnVisitFieldCatalog.CUSTOMER),
                contact.id(), contact.state(), contract.id(), contract.state(),
                text(value.getVisitNumber(), access, ReturnVisitFieldCatalog.NUMBER),
                datetime(value.getVisitTime(), access, ReturnVisitFieldCatalog.VISIT_TIME),
                text(value.getVisitForm() == null ? null : value.getVisitForm().name(), access,
                        ReturnVisitFieldCatalog.VISIT_FORM),
                text(value.getSatisfaction() == null ? null : value.getSatisfaction().name(), access,
                        ReturnVisitFieldCatalog.SATISFACTION),
                text(value.getRemark(), access, ReturnVisitFieldCatalog.REMARK),
                text(value.getStatus().name(), access, ReturnVisitFieldCatalog.STATUS), approvalStatus,
                custom(value, access),
                value.getVersion(), datetime(value.getCreatedTime(), access,
                        ReturnVisitFieldCatalog.CREATED_TIME), datetime(value.getUpdatedTime(), access,
                        ReturnVisitFieldCatalog.UPDATED_TIME));
    }

    private Map<Long, String> approvalStatus(TenantId tenantId, Collection<ReturnVisit> values) {
        return approvalInstanceRepository.findLatestSubjectStatuses(tenantId,
                        BusinessObjectType.RETURN_VISIT, values.stream()
                                .map(value -> value.getId().getValue()).toList())
                .stream().collect(Collectors.toMap(approval -> approval.subjectId(),
                        approval -> approval.status().name()));
    }

    private Map<BusinessObjectType, java.util.Set<Long>> visibleRelations(AuthenticatedCaller caller,
            TenantId tenantId, Collection<ReturnVisit> values) {
        Map<BusinessObjectType, java.util.Set<Long>> requested = new java.util.EnumMap<>(BusinessObjectType.class);
        values.forEach(value -> {
            requested.computeIfAbsent(BusinessObjectType.CUSTOMER, ignored -> new LinkedHashSet<>()).add(value.getCustomerId().getValue());
            if (value.getContactId() != null) requested.computeIfAbsent(BusinessObjectType.CONTACT, ignored -> new LinkedHashSet<>()).add(value.getContactId().getValue());
            if (value.getContractId() != null) requested.computeIfAbsent(BusinessObjectType.CONTRACT, ignored -> new LinkedHashSet<>()).add(value.getContractId().getValue());
        });
        Map<BusinessObjectType, java.util.Set<Long>> result = new java.util.EnumMap<>(BusinessObjectType.class);
        requested.forEach((type, ids) -> {
            try { result.put(type, businessRecordAccessGuard.visibleSubjectIds(caller, tenantId, type, ids)); }
            catch (NonRetryableApplicationException ignored) { result.put(type, java.util.Set.of()); }
        });
        return result;
    }

    private RelationProjection relation(Long relationId, BusinessObjectType type,
                                        Map<BusinessObjectType, java.util.Set<Long>> visible,
                                        Map<FieldCode, EffectiveFieldAccess> access, String fieldCode) {
        if (!visible(access, fieldCode)) {
            return new RelationProjection(null, ReturnVisitRelationStateResponse.HIDDEN);
        }
        if (relationId == null) {
            return new RelationProjection(null, ReturnVisitRelationStateResponse.EMPTY);
        }
        return visible.getOrDefault(type, java.util.Set.of()).contains(relationId)
                ? new RelationProjection(relationId, ReturnVisitRelationStateResponse.VISIBLE)
                : new RelationProjection(null, ReturnVisitRelationStateResponse.HIDDEN);
    }

    private Map<String, List<String>> custom(ReturnVisit value,
                                             Map<FieldCode, EffectiveFieldAccess> access) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (ReturnVisitFieldValue field : value.getCustomFieldValues()) {
            EffectiveFieldAccess valueAccess = access.getOrDefault(field.fieldCode(),
                    EffectiveFieldAccess.hidden());
            if (valueAccess.accessLevel() != FieldAccessLevel.HIDDEN) {
                result.put(field.fieldCode().getValue(), valueAccess.maskType() == FieldMaskType.NONE
                        ? field.values() : field.values().stream().map(ignored -> REDACTED).toList());
            }
        }
        return Map.copyOf(result);
    }

    private String text(String value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        EffectiveFieldAccess field = field(access, code);
        if (value == null || field.accessLevel() == FieldAccessLevel.HIDDEN) return null;
        return field.maskType() == FieldMaskType.NONE ? value : REDACTED;
    }

    private Long number(Long value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return visible(access, code) ? value : null;
    }
    private java.time.LocalDateTime datetime(java.time.LocalDateTime value,
                                             Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return visible(access, code) ? value : null;
    }
    private boolean visible(Map<FieldCode, EffectiveFieldAccess> access, String code) {
        EffectiveFieldAccess field = field(access, code);
        return field.accessLevel() != FieldAccessLevel.HIDDEN && field.maskType() == FieldMaskType.NONE;
    }
    private EffectiveFieldAccess field(Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return access.getOrDefault(new FieldCode(code), EffectiveFieldAccess.hidden());
    }
    NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private record RelationProjection(Long id, ReturnVisitRelationStateResponse state) {
    }
}
