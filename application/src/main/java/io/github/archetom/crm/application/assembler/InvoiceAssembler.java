package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.InvoiceProfileVO;
import io.github.archetom.crm.application.vo.InvoiceVO;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.InvoiceProfile;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InvoiceAssembler {
    private static final String REDACTED = "[REDACTED]";

    private InvoiceAssembler() {
    }

    public static InvoiceVO toVO(
            Invoice invoice, Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, boolean contractVisible) {
        Map<FieldCode, EffectiveFieldAccess> effective = accesses == null ? Map.of() : accesses;
        return new InvoiceVO(invoice.getId().getValue(),
                number(invoice.getOwnerMemberId().getValue(), effective,
                        InvoiceFieldCatalog.OWNER_ACTOR),
                number(customerVisible ? invoice.getCustomerId().getValue() : null,
                        effective, InvoiceFieldCatalog.CUSTOMER),
                number(contractVisible ? invoice.getContractId().getValue() : null,
                        effective, InvoiceFieldCatalog.CONTRACT),
                text(invoice.getApplicationNumber(), effective,
                        InvoiceFieldCatalog.APPLICATION_NUMBER),
                decimal(invoice.getAmount(), effective, InvoiceFieldCatalog.AMOUNT),
                visible(effective, InvoiceFieldCatalog.REQUESTED_DATE)
                        ? invoice.getRequestedDate() : null,
                text(invoice.getInvoiceType(), effective, InvoiceFieldCatalog.INVOICE_TYPE),
                text(invoice.getTitleType().name(), effective, InvoiceFieldCatalog.TITLE_TYPE),
                text(invoice.getTitle(), effective, InvoiceFieldCatalog.TITLE),
                text(invoice.getTaxNumber(), effective, InvoiceFieldCatalog.TAX_NUMBER),
                text(invoice.getBankName(), effective, InvoiceFieldCatalog.BANK_NAME),
                text(invoice.getBankAccount(), effective, InvoiceFieldCatalog.BANK_ACCOUNT),
                text(invoice.getRegisteredAddress(), effective,
                        InvoiceFieldCatalog.REGISTERED_ADDRESS),
                text(invoice.getRegisteredPhone(), effective,
                        InvoiceFieldCatalog.REGISTERED_PHONE),
                text(invoice.getContactName(), effective, InvoiceFieldCatalog.CONTACT_NAME),
                text(invoice.getContactPhone(), effective, InvoiceFieldCatalog.CONTACT_PHONE),
                text(invoice.getShippingAddress(), effective,
                        InvoiceFieldCatalog.SHIPPING_ADDRESS),
                text(invoice.getRemark(), effective, InvoiceFieldCatalog.REMARK),
                text(invoice.getStatus().name(), effective, InvoiceFieldCatalog.STATUS),
                text(invoice.getInvoiceNumber(), effective, InvoiceFieldCatalog.INVOICE_NUMBER),
                visible(effective, InvoiceFieldCatalog.ISSUED_DATE)
                        ? invoice.getIssuedDate() : null,
                text(invoice.getLogisticsNumber(), effective,
                        InvoiceFieldCatalog.LOGISTICS_NUMBER),
                customFields(invoice, effective), invoice.getVersion(),
                visible(effective, InvoiceFieldCatalog.CREATED_TIME)
                        ? invoice.getCreatedTime() : null,
                visible(effective, InvoiceFieldCatalog.UPDATED_TIME)
                        ? invoice.getUpdatedTime() : null);
    }

    public static InvoiceProfileVO toVO(InvoiceProfile value) {
        return new InvoiceProfileVO(value.getId().getValue(), value.getCustomerId().getValue(),
                value.getTitleType().name(), value.getTitle(), value.getTaxNumber(),
                value.getBankName(), value.getBankAccount(), value.getRegisteredAddress(),
                value.getRegisteredPhone(), value.getRemark(), value.getVersion(),
                value.getCreatedTime(), value.getUpdatedTime());
    }

    private static Map<String, List<String>> customFields(
            Invoice invoice, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        invoice.getCustomFieldValues().forEach(value -> {
            EffectiveFieldAccess access = accesses.get(value.fieldCode());
            if (access != null && access.accessLevel() != FieldAccessLevel.HIDDEN) {
                result.put(value.fieldCode().getValue(), access.maskType() == FieldMaskType.NONE
                        ? value.values() : value.values().stream().map(ignored -> REDACTED).toList());
            }
        });
        return Map.copyOf(result);
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
        if (access.maskType() == FieldMaskType.FULL || value.codePointCount(0, value.length()) < 3) {
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
        return value != null && visible(accesses, code) ? value : null;
    }

    private static BigDecimal decimal(
            BigDecimal value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return value != null && visible(accesses, code) ? value : null;
    }

    private static boolean visible(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = access(accesses, code);
        return access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }

    private static EffectiveFieldAccess access(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return accesses.getOrDefault(new FieldCode(code), EffectiveFieldAccess.hidden());
    }
}
