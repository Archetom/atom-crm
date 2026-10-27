package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ResolvedApprovalStep;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Shared submission path used by public approvals and stage approval gates. */
@Component
@RequiredArgsConstructor
class ApprovalSubmissionCoordinator {

    private static final String STAGE_IDEMPOTENCY_PREFIX = "stage:";
    private static final String OA_IDEMPOTENCY_PREFIX = "oa:";

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalApplicationSupport support;
    private final ApprovalRouteResolver routeResolver;

    record Resolution(Map<FieldCode, List<String>> facts,
                      List<ResolvedApprovalStep> route) {
    }

    String normalizePublicIdempotencyKey(String idempotencyKey) {
        String normalized = normalizeIdempotencyKey(idempotencyKey);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(STAGE_IDEMPOTENCY_PREFIX)
                || lower.startsWith(OA_IDEMPOTENCY_PREFIX)) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval idempotency key uses a reserved namespace");
        }
        return normalized;
    }

    String oaIdempotencyKey(OaRequestId requestId) {
        if (requestId == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA approval idempotency reference is invalid");
        }
        return OA_IDEMPOTENCY_PREFIX + requestId.getValue();
    }

    String stageIdempotencyKey(
            StageWorkflowRecordId recordId, String stageCode, int attempt) {
        if (recordId == null || stageCode == null || stageCode.isBlank() || attempt < 1) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Stage approval idempotency reference is invalid");
        }
        return STAGE_IDEMPOTENCY_PREFIX + recordId.getValue() + ":"
                + stageCode + ":" + attempt;
    }

    Resolution resolve(TenantId tenantId, ApprovalDefinition definition,
                       MemberId applicant, Map<FieldCode, List<String>> facts,
                       Map<String, List<Long>> selectedApprovers) {
        requireApplicant(tenantId, definition, applicant);
        var fields = support.fields(
                tenantId, definition.getObjectType());
        Map<FieldCode, List<String>> normalized = normalizeFacts(
                facts, fields, definition.getObjectType());
        Map<FieldCode, CustomFieldType> types = support.searchableTypes(fields);
        return new Resolution(normalized, routeResolver.resolve(
                tenantId, definition, normalized, types, selectedApprovers));
    }

    ApprovalInstance submit(TenantId tenantId, ApprovalDefinition definition,
                            Long subjectId, String idempotencyKey, String title,
                            MemberId applicant,
                            Supplier<Map<FieldCode, List<String>>> factsSupplier,
                            Map<String, List<Long>> selectedApprovers,
                            Collection<MemberId> watchers) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        ApprovalInstance existing = instanceRepository.findByIdempotencyKey(
                tenantId, normalizedIdempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getDefinitionId().equals(definition.getId())
                    || existing.getObjectType() != definition.getObjectType()
                    || !existing.getSubjectId().equals(subjectId)
                    || !existing.getApplicantMemberId().equals(applicant)) {
                throw support.error(ApplicationErrorCode.VERSION_CONFLICT,
                        "Approval idempotency key is already in use");
            }
            return existing;
        }
        Map<FieldCode, List<String>> facts = factsSupplier == null
                ? Map.of() : factsSupplier.get();
        Resolution resolution = resolve(tenantId, definition, applicant,
                facts, selectedApprovers);
        support.validateReferences(tenantId, watchers, List.of(), List.of());
        ApprovalInstance instance = ApprovalInstance.submit(tenantId, definition.getId(),
                definition.getVersion(), definition.getObjectType(), subjectId,
                normalizedIdempotencyKey, title, applicant, definition.getRepeatMode(),
                resolution.facts(), definition.getManagerMemberIds(), watchers,
                resolution.route());
        return instanceRepository.save(tenantId, instance);
    }

    private Map<FieldCode, List<String>> normalizeFacts(
            Map<FieldCode, List<String>> facts,
            Map<FieldCode, io.github.archetom.crm.domain.entity.FieldDefinition> fields,
            BusinessObjectType objectType) {
        if (facts == null) {
            return Map.of();
        }
        java.util.LinkedHashMap<FieldCode, List<String>> normalized =
                new java.util.LinkedHashMap<>();
        facts.forEach((code, values) -> {
            var field = fields.get(code);
            if (field == null || objectType != BusinessObjectType.OA_REQUEST
                    && !field.isSearchable()) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval routing field is unavailable");
            }
            normalized.put(code, objectType == BusinessObjectType.OA_REQUEST
                    ? support.normalizeSnapshotFact(field, values)
                    : support.normalizeFact(field, values));
        });
        return java.util.Collections.unmodifiableMap(normalized);
    }

    private String normalizeIdempotencyKey(String value) {
        try {
            return ApprovalInstance.normalizeIdempotencyKey(value);
        } catch (ApprovalDomainException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval idempotency key is invalid");
        }
    }

    private void requireApplicant(TenantId tenantId, ApprovalDefinition definition,
                                  MemberId applicant) {
        if (!definition.isActive()) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Approval definition is not active");
        }
        OrganizationMembership membership = support.activeMembership(tenantId, applicant);
        if (!definition.isApplicantAllowed(applicant, membership.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Caller is outside the approval initiator scope");
        }
    }
}
