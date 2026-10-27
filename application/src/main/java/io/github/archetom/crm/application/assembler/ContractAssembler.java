package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.ContractCollaboratorVO;
import io.github.archetom.crm.application.vo.ContractProductLineVO;
import io.github.archetom.crm.application.vo.ContractVO;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContractAssembler {
    private static final String REDACTED = "[REDACTED]";

    private ContractAssembler() {
    }

    public static ContractVO toVO(
            Contract contract, Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, boolean opportunityVisible, boolean contactVisible,
            boolean productsVisible, boolean collaboratorsVisible) {
        Map<FieldCode, EffectiveFieldAccess> effective = accesses == null ? Map.of() : accesses;
        return new ContractVO(
                contract.getId().getValue(),
                number(contract.getOwnerMemberId().getValue(), effective,
                        ContractFieldCatalog.OWNER_ACTOR),
                number(customerVisible ? contract.getCustomerId().getValue() : null,
                        effective, ContractFieldCatalog.CUSTOMER),
                number(opportunityVisible && contract.getOpportunityId() != null
                                ? contract.getOpportunityId().getValue() : null,
                        effective, ContractFieldCatalog.OPPORTUNITY),
                number(contactVisible && contract.getContactId() != null
                                ? contract.getContactId().getValue() : null,
                        effective, ContractFieldCatalog.CONTACT),
                text(contract.getName(), effective, ContractFieldCatalog.NAME),
                text(contract.getNumber(), effective, ContractFieldCatalog.NUMBER),
                visible(effective, ContractFieldCatalog.ORDER_DATE) ? contract.getOrderDate() : null,
                visible(effective, ContractFieldCatalog.START_DATE) ? contract.getStartDate() : null,
                visible(effective, ContractFieldCatalog.END_DATE) ? contract.getEndDate() : null,
                text(contract.getContractType(), effective, ContractFieldCatalog.CONTRACT_TYPE),
                text(contract.getPaymentType(), effective, ContractFieldCatalog.PAYMENT_TYPE),
                text(contract.getRemark(), effective, ContractFieldCatalog.REMARK),
                decimal(contract.getSubtotal(), effective, ContractFieldCatalog.SUBTOTAL),
                decimal(contract.getDiscountRate(), effective, ContractFieldCatalog.DISCOUNT_RATE),
                decimal(contract.getTotalAmount(), effective, ContractFieldCatalog.TOTAL_AMOUNT),
                text(contract.getStatus().name(), effective, ContractFieldCatalog.STATUS),
                productsVisible && visible(effective, ContractFieldCatalog.PRODUCTS)
                        ? contract.getProductLines().stream().map(value ->
                        new ContractProductLineVO(value.productId().getValue(),
                                value.productName(), value.productCode(), value.categoryName(),
                                value.unit(), value.listPrice(), value.unitPrice(), value.quantity(),
                                value.discountRate(), value.lineAmount(), value.displayOrder())).toList()
                        : List.of(),
                collaboratorsVisible ? contract.getCollaborators().stream()
                        .map(value -> new ContractCollaboratorVO(
                                value.memberId().getValue(), value.access().name(),
                                value.effectiveUntil())).toList()
                        : List.of(),
                customFields(contract, effective), contract.getVersion(),
                visible(effective, ContractFieldCatalog.CREATED_TIME)
                        ? contract.getCreatedTime() : null,
                visible(effective, ContractFieldCatalog.UPDATED_TIME)
                        ? contract.getUpdatedTime() : null);
    }

    private static Map<String, List<String>> customFields(
            Contract contract, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        contract.getCustomFieldValues().forEach(value -> {
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
