package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.OpportunityCollaboratorVO;
import io.github.archetom.crm.application.vo.OpportunityVO;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies effective field and relation access while mapping opportunity values. */
public final class OpportunityAssembler {

    private static final String REDACTED = "[REDACTED]";

    private OpportunityAssembler() {
    }

    public static OpportunityVO toVO(
            Opportunity opportunity, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, Set<ContactId> visibleContactIds,
            boolean collaboratorsVisible) {
        return toVO(opportunity, starred, accesses, customerVisible, visibleContactIds,
                collaboratorsVisible, null);
    }

    public static OpportunityVO toVO(
            Opportunity opportunity, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, Set<ContactId> visibleContactIds,
            boolean collaboratorsVisible,
            LocalDateTime lastActivityTime) {
        Map<FieldCode, EffectiveFieldAccess> effective = accesses == null
                ? Map.of() : accesses;
        Set<ContactId> contacts = visibleContactIds == null
                ? Set.of() : visibleContactIds;
        boolean contactsVisible = unmaskedVisible(
                effective, OpportunityFieldCatalog.CONTACTS);
        Long primaryId = opportunity.getPrimaryContactId() == null
                || !contacts.contains(opportunity.getPrimaryContactId())
                ? null : opportunity.getPrimaryContactId().getValue();
        return new OpportunityVO(
                opportunity.getId().getValue(),
                number(opportunity.getOwnerMemberId().getValue(), effective,
                        OpportunityFieldCatalog.OWNER_ACTOR),
                number(customerVisible ? opportunity.getCustomerId().getValue() : null,
                        effective, OpportunityFieldCatalog.CUSTOMER),
                number(primaryId, effective, OpportunityFieldCatalog.PRIMARY_CONTACT),
                text(opportunity.getName(), effective, OpportunityFieldCatalog.NAME),
                text(opportunity.getPipelineCode(), effective,
                        OpportunityFieldCatalog.PIPELINE_CODE),
                text(opportunity.getStageCode(), effective,
                        OpportunityFieldCatalog.STAGE_CODE),
                unmaskedVisible(effective, OpportunityFieldCatalog.EXPECTED_CLOSE_DATE)
                        ? opportunity.getExpectedCloseDate() : null,
                decimal(opportunity.getEstimatedAmount(), effective,
                        OpportunityFieldCatalog.ESTIMATED_AMOUNT),
                unmaskedVisible(effective, OpportunityFieldCatalog.NEXT_CONTACT_TIME)
                        ? opportunity.getNextContactTime() : null,
                text(opportunity.getDescription(), effective,
                        OpportunityFieldCatalog.DESCRIPTION),
                text(opportunity.getOutcome().name(), effective,
                        OpportunityFieldCatalog.OUTCOME),
                unmaskedVisible(effective, OpportunityFieldCatalog.OUTCOME_TIME)
                        ? opportunity.getOutcomeTime() : null,
                text(opportunity.getOutcomeRemark(), effective,
                        OpportunityFieldCatalog.OUTCOME_REMARK),
                text(opportunity.getStatus().name(), effective,
                        OpportunityFieldCatalog.STATUS),
                contactsVisible ? opportunity.getContactIds().stream()
                        .filter(contacts::contains).map(ContactId::getValue).toList() : List.of(),
                collaboratorsVisible ? opportunity.getCollaborators().stream()
                        .map(value -> new OpportunityCollaboratorVO(
                                value.memberId().getValue(), value.access().name()))
                        .toList() : List.of(),
                customFields(opportunity, effective), starred, opportunity.getVersion(),
                unmaskedVisible(effective, OpportunityFieldCatalog.CREATED_TIME)
                        ? opportunity.getCreatedTime() : null,
                unmaskedVisible(effective, OpportunityFieldCatalog.UPDATED_TIME)
                        ? opportunity.getUpdatedTime() : null,
                lastActivityTime);
    }

    private static Map<String, List<String>> customFields(
            Opportunity opportunity,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        opportunity.getCustomFieldValues().forEach(value -> {
            EffectiveFieldAccess access = accesses.get(value.fieldCode());
            if (access != null && access.accessLevel() != FieldAccessLevel.HIDDEN) {
                values.put(value.fieldCode().getValue(), access.maskType() == FieldMaskType.NONE
                        ? value.values()
                        : value.values().stream().map(ignored -> REDACTED).toList());
            }
        });
        return Map.copyOf(values);
    }

    private static String text(
            String value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = access(accesses, code);
        if (value == null || access.accessLevel() == FieldAccessLevel.HIDDEN) {
            return null;
        }
        if (access.maskType() == FieldMaskType.NONE) {
            return value;
        }
        if (access.maskType() == FieldMaskType.FULL
                || value.codePointCount(0, value.length()) < 3) {
            return REDACTED;
        }
        int count = value.codePointCount(0, value.length());
        int firstEnd = value.offsetByCodePoints(0, 1);
        int lastStart = value.offsetByCodePoints(0, count - 1);
        return value.substring(0, firstEnd) + "*".repeat(Math.min(8, count - 2))
                + value.substring(lastStart);
    }

    private static Long number(
            Long value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = access(accesses, code);
        return value != null && access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE ? value : null;
    }

    private static BigDecimal decimal(
            BigDecimal value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = access(accesses, code);
        return value != null && access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE ? value : null;
    }

    private static boolean unmaskedVisible(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = access(accesses, code);
        return access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }

    private static EffectiveFieldAccess access(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return accesses.getOrDefault(
                new FieldCode(code), EffectiveFieldAccess.hidden());
    }
}
