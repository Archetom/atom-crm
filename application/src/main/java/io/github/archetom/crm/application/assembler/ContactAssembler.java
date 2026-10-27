package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.ContactAddressVO;
import io.github.archetom.crm.application.vo.ContactCollaboratorVO;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies effective field access while mapping contact values out of the application. */
public final class ContactAssembler {

    private static final String REDACTED = "[REDACTED]";

    private ContactAssembler() {
    }

    public static ContactVO toVO(
            Contact contact, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<ContactId> visibleRelations) {
        return toVO(contact, starred, accesses, visibleRelations, Set.of(), false);
    }

    public static ContactVO toVO(
            Contact contact, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<ContactId> visibleRelations,
            boolean collaboratorsVisible) {
        return toVO(contact, starred, accesses, visibleRelations, Set.of(), collaboratorsVisible);
    }

    public static ContactVO toVO(
            Contact contact, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<ContactId> visibleRelations,
            Set<CustomerId> visibleCustomerRelations,
            boolean collaboratorsVisible) {
        return toVO(contact, starred, accesses, visibleRelations, visibleCustomerRelations,
                collaboratorsVisible, null, null);
    }

    public static ContactVO toVO(
            Contact contact, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<ContactId> visibleRelations,
            Set<CustomerId> visibleCustomerRelations,
            boolean collaboratorsVisible,
            LocalDateTime lastActivityTime,
            String lastActivityContent) {
        Map<FieldCode, EffectiveFieldAccess> effective = accesses == null ? Map.of() : accesses;
        Set<ContactId> visibleContactIds = visibleRelations == null ? Set.of() : visibleRelations;
        Set<CustomerId> visibleCustomerIds = visibleCustomerRelations == null
                ? Set.of() : visibleCustomerRelations;
        boolean tagsVisible = visible(effective, ContactFieldCatalog.TAGS);
        boolean addressesVisible = visible(effective, ContactFieldCatalog.ADDRESSES);
        return new ContactVO(
                contact.getId().getValue(),
                number(contact.getOwnerMemberId() == null ? null
                        : contact.getOwnerMemberId().getValue(), effective,
                        ContactFieldCatalog.OWNER_ACTOR),
                number(contact.getCustomerId() == null
                                || !visibleCustomerIds.contains(contact.getCustomerId()) ? null
                        : contact.getCustomerId().getValue(), effective,
                        ContactFieldCatalog.CUSTOMER),
                number(contact.getParentContactId() == null
                                || !visibleContactIds.contains(contact.getParentContactId()) ? null
                        : contact.getParentContactId().getValue(), effective,
                        ContactFieldCatalog.PARENT_CONTACT),
                text(contact.getName(), effective, ContactFieldCatalog.NAME),
                text(contact.getPhoneNumber(), effective, ContactFieldCatalog.PHONE_NUMBER),
                text(contact.getTelephone(), effective, ContactFieldCatalog.TELEPHONE),
                text(contact.getJobTitle(), effective, ContactFieldCatalog.JOB_TITLE),
                text(contact.getEmail(), effective, ContactFieldCatalog.EMAIL),
                text(contact.getWebsite(), effective, ContactFieldCatalog.WEBSITE),
                text(contact.getDescription(), effective, ContactFieldCatalog.DESCRIPTION),
                unmaskedVisible(effective, ContactFieldCatalog.NEXT_CONTACT_TIME)
                        ? contact.getNextContactTime() : null,
                text(contact.getStatus().name(), effective, ContactFieldCatalog.STATUS),
                flag(contact.isLocked(), effective, ContactFieldCatalog.LOCKED),
                text(contact.getDealStatus().name(), effective, ContactFieldCatalog.DEAL_STATUS),
                unmaskedVisible(effective, ContactFieldCatalog.DEAL_STATUS)
                        ? contact.getDealTime() : null,
                tagsVisible ? maskedList(contact.getTags(), access(effective, ContactFieldCatalog.TAGS)) : List.of(),
                addressesVisible ? addresses(contact, access(effective, ContactFieldCatalog.ADDRESSES)) : List.of(),
                collaboratorsVisible ? collaborators(contact) : List.of(),
                customFields(contact, effective),
                starred,
                contact.getVersion(),
                unmaskedVisible(effective, ContactFieldCatalog.CREATED_TIME)
                        ? contact.getCreatedTime() : null,
                unmaskedVisible(effective, ContactFieldCatalog.UPDATED_TIME)
                        ? contact.getUpdatedTime() : null,
                lastActivityTime,
                lastActivityContent);
    }

    public static List<ContactCollaboratorVO> collaborators(Contact contact) {
        return contact.getCollaborators().stream().map(value ->
                new ContactCollaboratorVO(value.memberId().getValue(), value.access().name())).toList();
    }

    private static Map<String, List<String>> customFields(
            Contact contact, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        contact.getCustomFieldValues().forEach(value -> {
            EffectiveFieldAccess fieldAccess = accesses.get(value.fieldCode());
            if (fieldAccess != null && fieldAccess.accessLevel() != FieldAccessLevel.HIDDEN) {
                values.put(value.fieldCode().getValue(), maskedList(value.values(), fieldAccess));
            }
        });
        return Map.copyOf(values);
    }

    private static List<ContactAddressVO> addresses(
            Contact contact, EffectiveFieldAccess access) {
        if (access.maskType() == FieldMaskType.NONE) {
            return contact.getAddresses().stream()
                    .map(value -> new ContactAddressVO(value.key(), value.label(), value.country(),
                            value.region(), value.city(), value.postalCode(), value.addressLine(),
                            value.longitude(), value.latitude(), value.primary()))
                    .toList();
        }
        return contact.getAddresses().stream()
                .map(value -> new ContactAddressVO(value.key(), null, null, null, null,
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
