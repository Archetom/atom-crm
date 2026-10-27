package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.CustomerAddressVO;
import io.github.archetom.crm.application.vo.CustomerCollaboratorVO;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies effective field access while mapping customer values out of the application. */
public final class CustomerAssembler {

    private static final String REDACTED = "[REDACTED]";

    private CustomerAssembler() {
    }

    public static CustomerVO toVO(
            Customer customer, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<CustomerId> visibleRelations) {
        return toVO(customer, starred, accesses, visibleRelations, false);
    }

    public static CustomerVO toVO(
            Customer customer, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<CustomerId> visibleRelations,
            boolean collaboratorsVisible) {
        return toVO(customer, starred, accesses, visibleRelations, collaboratorsVisible, null, null);
    }

    public static CustomerVO toVO(
            Customer customer, boolean starred,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<CustomerId> visibleRelations,
            boolean collaboratorsVisible,
            LocalDateTime lastActivityTime,
            String lastActivityContent) {
        Map<FieldCode, EffectiveFieldAccess> effective = accesses == null ? Map.of() : accesses;
        Set<CustomerId> visibleCustomerIds = visibleRelations == null ? Set.of() : visibleRelations;
        boolean tagsVisible = visible(effective, CustomerFieldCatalog.TAGS);
        boolean addressesVisible = visible(effective, CustomerFieldCatalog.ADDRESSES);
        return new CustomerVO(
                customer.getId().getValue(),
                number(customer.getOwnerMemberId() == null ? null
                        : customer.getOwnerMemberId().getValue(), effective,
                        CustomerFieldCatalog.OWNER_ACTOR),
                number(customer.getParentCustomerId() == null
                                || !visibleCustomerIds.contains(customer.getParentCustomerId()) ? null
                        : customer.getParentCustomerId().getValue(), effective,
                        CustomerFieldCatalog.PARENT_CUSTOMER),
                text(customer.getName(), effective, CustomerFieldCatalog.NAME),
                text(customer.getPhoneNumber(), effective, CustomerFieldCatalog.PHONE_NUMBER),
                text(customer.getTelephone(), effective, CustomerFieldCatalog.TELEPHONE),
                text(customer.getEmail(), effective, CustomerFieldCatalog.EMAIL),
                text(customer.getWebsite(), effective, CustomerFieldCatalog.WEBSITE),
                text(customer.getDescription(), effective, CustomerFieldCatalog.DESCRIPTION),
                text(customer.getStatus().name(), effective, CustomerFieldCatalog.STATUS),
                flag(customer.isLocked(), effective, CustomerFieldCatalog.LOCKED),
                text(customer.getDealStatus().name(), effective, CustomerFieldCatalog.DEAL_STATUS),
                unmaskedVisible(effective, CustomerFieldCatalog.DEAL_STATUS)
                        ? customer.getDealTime() : null,
                tagsVisible ? maskedList(customer.getTags(), access(effective, CustomerFieldCatalog.TAGS)) : List.of(),
                addressesVisible ? addresses(customer, access(effective, CustomerFieldCatalog.ADDRESSES)) : List.of(),
                collaboratorsVisible ? collaborators(customer) : List.of(),
                customFields(customer, effective),
                starred,
                customer.getVersion(),
                unmaskedVisible(effective, CustomerFieldCatalog.CREATED_TIME)
                        ? customer.getCreatedTime() : null,
                unmaskedVisible(effective, CustomerFieldCatalog.UPDATED_TIME)
                        ? customer.getUpdatedTime() : null,
                lastActivityTime,
                lastActivityContent);
    }

    public static List<CustomerCollaboratorVO> collaborators(Customer customer) {
        return customer.getCollaborators().stream().map(value ->
                new CustomerCollaboratorVO(value.memberId().getValue(), value.access().name())).toList();
    }

    private static Map<String, List<String>> customFields(
            Customer customer, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        customer.getCustomFieldValues().forEach(value -> {
            EffectiveFieldAccess fieldAccess = accesses.get(value.fieldCode());
            if (fieldAccess != null && fieldAccess.accessLevel() != FieldAccessLevel.HIDDEN) {
                values.put(value.fieldCode().getValue(), maskedList(value.values(), fieldAccess));
            }
        });
        return Map.copyOf(values);
    }

    private static List<CustomerAddressVO> addresses(
            Customer customer, EffectiveFieldAccess access) {
        if (access.maskType() == FieldMaskType.NONE) {
            return customer.getAddresses().stream()
                    .map(value -> new CustomerAddressVO(value.key(), value.label(), value.country(),
                            value.region(), value.city(), value.postalCode(), value.addressLine(),
                            value.longitude(), value.latitude(), value.primary()))
                    .toList();
        }
        return customer.getAddresses().stream()
                .map(value -> new CustomerAddressVO(value.key(), null, null, null, null,
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
