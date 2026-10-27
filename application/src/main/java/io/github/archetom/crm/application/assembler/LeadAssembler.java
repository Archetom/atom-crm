package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.LeadAddressVO;
import io.github.archetom.crm.application.vo.LeadCollaboratorVO;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.ContactId;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies effective field access while mapping lead values out of the application. */
public final class LeadAssembler {

    private static final String REDACTED = "[REDACTED]";

    private LeadAssembler() {
    }

    public static LeadVO toVO(
            Lead lead, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<LeadId> visibleRelations) {
        return toVO(lead, starred, accesses, visibleRelations, Set.of(), false);
    }

    public static LeadVO toVO(
            Lead lead, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<LeadId> visibleRelations,
            boolean collaboratorsVisible) {
        return toVO(lead, starred, accesses, visibleRelations, Set.of(), collaboratorsVisible);
    }

    public static LeadVO toVO(
            Lead lead, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<LeadId> visibleRelations,
            Set<CustomerId> visibleCustomerRelations,
            boolean collaboratorsVisible) {
        return toVO(lead, starred, accesses, visibleRelations, visibleCustomerRelations,
                Set.of(), collaboratorsVisible);
    }

    public static LeadVO toVO(
            Lead lead, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<LeadId> visibleRelations,
            Set<CustomerId> visibleCustomerRelations,
            Set<ContactId> visibleConvertedContactRelations,
            boolean collaboratorsVisible) {
        return toVO(lead, starred, accesses, visibleRelations, visibleCustomerRelations,
                visibleConvertedContactRelations, collaboratorsVisible, null, null);
    }

    public static LeadVO toVO(
            Lead lead, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<LeadId> visibleRelations,
            Set<CustomerId> visibleCustomerRelations,
            Set<ContactId> visibleConvertedContactRelations,
            boolean collaboratorsVisible,
            LocalDateTime lastActivityTime,
            String lastActivityContent) {
        Map<FieldCode, EffectiveFieldAccess> effective = accesses == null ? Map.of() : accesses;
        Set<LeadId> visibleLeadIds = visibleRelations == null ? Set.of() : visibleRelations;
        Set<CustomerId> visibleCustomerIds = visibleCustomerRelations == null
                ? Set.of() : visibleCustomerRelations;
        Set<ContactId> visibleContactIds = visibleConvertedContactRelations == null
                ? Set.of() : visibleConvertedContactRelations;
        boolean tagsVisible = visible(effective, LeadFieldCatalog.TAGS);
        boolean addressesVisible = visible(effective, LeadFieldCatalog.ADDRESSES);
        return new LeadVO(
                lead.getId().getValue(),
                number(lead.getOwnerMemberId() == null ? null
                        : lead.getOwnerMemberId().getValue(), effective,
                        LeadFieldCatalog.OWNER_ACTOR),
                number(lead.getCustomerId() == null
                                || !visibleCustomerIds.contains(lead.getCustomerId()) ? null
                        : lead.getCustomerId().getValue(), effective,
                        LeadFieldCatalog.CUSTOMER),
                lead.getConvertedContactId() == null
                                || !visibleContactIds.contains(lead.getConvertedContactId())
                        ? null : lead.getConvertedContactId().getValue(),
                number(lead.getParentLeadId() == null
                                || !visibleLeadIds.contains(lead.getParentLeadId()) ? null
                        : lead.getParentLeadId().getValue(), effective,
                        LeadFieldCatalog.PARENT_LEAD),
                text(lead.getName(), effective, LeadFieldCatalog.NAME),
                text(lead.getPhoneNumber(), effective, LeadFieldCatalog.PHONE_NUMBER),
                text(lead.getTelephone(), effective, LeadFieldCatalog.TELEPHONE),
                text(lead.getJobTitle(), effective, LeadFieldCatalog.JOB_TITLE),
                text(lead.getEmail(), effective, LeadFieldCatalog.EMAIL),
                text(lead.getWebsite(), effective, LeadFieldCatalog.WEBSITE),
                text(lead.getDescription(), effective, LeadFieldCatalog.DESCRIPTION),
                unmaskedVisible(effective, LeadFieldCatalog.NEXT_CONTACT_TIME)
                        ? lead.getNextContactTime() : null,
                text(lead.getStatus().name(), effective, LeadFieldCatalog.STATUS),
                flag(lead.isLocked(), effective, LeadFieldCatalog.LOCKED),
                text(lead.getDealStatus().name(), effective, LeadFieldCatalog.DEAL_STATUS),
                unmaskedVisible(effective, LeadFieldCatalog.DEAL_STATUS)
                        ? lead.getDealTime() : null,
                tagsVisible ? maskedList(lead.getTags(), access(effective, LeadFieldCatalog.TAGS)) : List.of(),
                addressesVisible ? addresses(lead, access(effective, LeadFieldCatalog.ADDRESSES)) : List.of(),
                collaboratorsVisible ? collaborators(lead) : List.of(),
                customFields(lead, effective),
                starred,
                lead.getVersion(),
                unmaskedVisible(effective, LeadFieldCatalog.CREATED_TIME)
                        ? lead.getCreatedTime() : null,
                unmaskedVisible(effective, LeadFieldCatalog.UPDATED_TIME)
                        ? lead.getUpdatedTime() : null,
                lastActivityTime,
                lastActivityContent);
    }

    public static List<LeadCollaboratorVO> collaborators(Lead lead) {
        return lead.getCollaborators().stream().map(value ->
                new LeadCollaboratorVO(value.memberId().getValue(), value.access().name())).toList();
    }

    private static Map<String, List<String>> customFields(
            Lead lead, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        lead.getCustomFieldValues().forEach(value -> {
            EffectiveFieldAccess fieldAccess = accesses.get(value.fieldCode());
            if (fieldAccess != null && fieldAccess.accessLevel() != FieldAccessLevel.HIDDEN) {
                values.put(value.fieldCode().getValue(), maskedList(value.values(), fieldAccess));
            }
        });
        return Map.copyOf(values);
    }

    private static List<LeadAddressVO> addresses(
            Lead lead, EffectiveFieldAccess access) {
        if (access.maskType() == FieldMaskType.NONE) {
            return lead.getAddresses().stream()
                    .map(value -> new LeadAddressVO(value.key(), value.label(), value.country(),
                            value.region(), value.city(), value.postalCode(), value.addressLine(),
                            value.longitude(), value.latitude(), value.primary()))
                    .toList();
        }
        return lead.getAddresses().stream()
                .map(value -> new LeadAddressVO(value.key(), null, null, null, null,
                        null, REDACTED, null, null, value.primary()))
                .toList();
    }

    private static String text(
            String value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess fieldAccess = access(accesses, code);
        if (value == null || fieldAccess.accessLevel() == FieldAccessLevel.HIDDEN) {
            return null;
        }
        return mask(value, fieldAccess.maskType());
    }

    private static Long number(
            Long value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess fieldAccess = access(accesses, code);
        return value != null && fieldAccess.accessLevel() != FieldAccessLevel.HIDDEN
                && fieldAccess.maskType() == FieldMaskType.NONE ? value : null;
    }

    private static Boolean flag(
            boolean value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess fieldAccess = access(accesses, code);
        return fieldAccess.accessLevel() != FieldAccessLevel.HIDDEN
                && fieldAccess.maskType() == FieldMaskType.NONE ? value : null;
    }

    private static List<String> maskedList(
            List<String> values, EffectiveFieldAccess access) {
        if (access.maskType() == FieldMaskType.NONE) {
            return values;
        }
        return values.stream().map(value -> mask(value, access.maskType())).toList();
    }

    private static String mask(String value, FieldMaskType maskType) {
        if (maskType == FieldMaskType.NONE) {
            return value;
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (maskType == FieldMaskType.FULL || codePointCount < 3) {
            return REDACTED;
        }
        int firstEnd = value.offsetByCodePoints(0, 1);
        int lastStart = value.offsetByCodePoints(0, codePointCount - 1);
        return value.substring(0, firstEnd)
                + "*".repeat(Math.min(8, codePointCount - 2))
                + value.substring(lastStart);
    }

    private static boolean visible(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return access(accesses, code).accessLevel() != FieldAccessLevel.HIDDEN;
    }

    private static boolean unmaskedVisible(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess fieldAccess = access(accesses, code);
        return fieldAccess.accessLevel() != FieldAccessLevel.HIDDEN
                && fieldAccess.maskType() == FieldMaskType.NONE;
    }

    private static EffectiveFieldAccess access(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return accesses.getOrDefault(new FieldCode(code), EffectiveFieldAccess.hidden());
    }
}
