package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.application.vo.ReceivableVO;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.ReceivablePlanProgress;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReceivableAssembler {
    private static final String REDACTED = "[REDACTED]";

    private ReceivableAssembler() {
    }

    public static ReceivableVO toVO(
            Receivable value, Map<FieldCode, EffectiveFieldAccess> access,
            boolean customerVisible, boolean contractVisible, boolean planVisible,
            String approvalStatus) {
        return new ReceivableVO(value.getId().getValue(), number(value.getOwnerMemberId().getValue(),
                access, ReceivableFieldCatalog.OWNER_ACTOR), number(customerVisible
                ? value.getCustomerId().getValue() : null, access, ReceivableFieldCatalog.CUSTOMER),
                number(contractVisible ? value.getContractId().getValue() : null, access,
                        ReceivableFieldCatalog.CONTRACT),
                number(planVisible && value.getPlanId() != null ? value.getPlanId().getValue() : null,
                        access, ReceivableFieldCatalog.PLAN),
                text(value.getReceiptNumber(), access, ReceivableFieldCatalog.NUMBER),
                date(value.getReceivedDate(), access, ReceivableFieldCatalog.RECEIVED_DATE),
                text(value.getPaymentMethod(), access, ReceivableFieldCatalog.PAYMENT_METHOD),
                decimal(value.getAmount(), access, ReceivableFieldCatalog.AMOUNT),
                text(value.getRemark(), access, ReceivableFieldCatalog.REMARK),
                text(value.getStatus().name(), access, ReceivableFieldCatalog.STATUS),
                approvalStatus,
                custom(value.getCustomFieldValues().stream().map(field ->
                        Map.entry(field.fieldCode(), field.values())).toList(), access), value.getVersion(),
                datetime(value.getCreatedTime(), access, ReceivableFieldCatalog.CREATED_TIME),
                datetime(value.getUpdatedTime(), access, ReceivableFieldCatalog.UPDATED_TIME));
    }

    public static ReceivablePlanVO toPlanVO(
            ReceivablePlan value, BigDecimal receivedAmount,
            Map<FieldCode, EffectiveFieldAccess> access,
            boolean customerVisible, boolean contractVisible) {
        BigDecimal received = receivedAmount == null ? BigDecimal.ZERO : receivedAmount;
        BigDecimal remaining = value.getPlannedAmount().subtract(received);
        ReceivablePlanProgress progress = progress(value.getDueDate(), received,
                value.getPlannedAmount());
        return new ReceivablePlanVO(value.getId().getValue(), number(value.getOwnerMemberId().getValue(),
                access, ReceivablePlanFieldCatalog.OWNER_ACTOR), number(customerVisible
                ? value.getCustomerId().getValue() : null, access, ReceivablePlanFieldCatalog.CUSTOMER),
                number(contractVisible ? value.getContractId().getValue() : null, access,
                        ReceivablePlanFieldCatalog.CONTRACT),
                integer(value.getInstallment(), access, ReceivablePlanFieldCatalog.NUMBER),
                date(value.getDueDate(), access, ReceivablePlanFieldCatalog.DUE_DATE),
                decimal(value.getPlannedAmount(), access, ReceivablePlanFieldCatalog.PLANNED_AMOUNT),
                text(value.getPaymentMethod(), access, ReceivablePlanFieldCatalog.PAYMENT_METHOD),
                integer(value.getReminderDays(), access, ReceivablePlanFieldCatalog.REMINDER_DAYS),
                text(value.getRemark(), access, ReceivablePlanFieldCatalog.REMARK),
                decimal(received, access, ReceivablePlanFieldCatalog.RECEIVED_AMOUNT),
                decimal(remaining, access, ReceivablePlanFieldCatalog.REMAINING_AMOUNT),
                text(progress.name(), access, ReceivablePlanFieldCatalog.PROGRESS),
                text(value.getStatus().name(), access, ReceivablePlanFieldCatalog.STATUS),
                custom(value.getCustomFieldValues().stream().map(field ->
                        Map.entry(field.fieldCode(), field.values())).toList(), access), value.getVersion(),
                datetime(value.getCreatedTime(), access, ReceivablePlanFieldCatalog.CREATED_TIME),
                datetime(value.getUpdatedTime(), access, ReceivablePlanFieldCatalog.UPDATED_TIME));
    }

    public static ReceivablePlanProgress progress(
            LocalDate dueDate, BigDecimal receivedAmount, BigDecimal plannedAmount) {
        if (receivedAmount.compareTo(plannedAmount) >= 0) return ReceivablePlanProgress.COMPLETED;
        if (receivedAmount.signum() > 0) return ReceivablePlanProgress.PARTIAL;
        return dueDate.isBefore(LocalDate.now()) ? ReceivablePlanProgress.OVERDUE
                : ReceivablePlanProgress.PENDING;
    }

    private static Map<String, List<String>> custom(
            List<Map.Entry<FieldCode, List<String>>> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<FieldCode, List<String>> field : fields) {
            EffectiveFieldAccess access = accesses.get(field.getKey());
            if (access != null && access.accessLevel() != FieldAccessLevel.HIDDEN) {
                result.put(field.getKey().getValue(), access.maskType() == FieldMaskType.NONE
                        ? field.getValue() : field.getValue().stream().map(ignored -> REDACTED).toList());
            }
        }
        return Map.copyOf(result);
    }

    private static String text(String value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        EffectiveFieldAccess field = field(access, code);
        if (value == null || field.accessLevel() == FieldAccessLevel.HIDDEN) return null;
        if (field.maskType() == FieldMaskType.NONE) return value;
        if (field.maskType() == FieldMaskType.FULL || value.codePointCount(0, value.length()) < 3) {
            return REDACTED;
        }
        int count = value.codePointCount(0, value.length());
        return value.substring(0, value.offsetByCodePoints(0, 1))
                + "*".repeat(Math.min(8, count - 2))
                + value.substring(value.offsetByCodePoints(0, count - 1));
    }

    private static Long number(Long value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return visible(access, code) ? value : null;
    }

    private static Integer integer(Integer value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return value != null && visible(access, code) ? value : null;
    }

    private static BigDecimal decimal(BigDecimal value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return value != null && visible(access, code) ? value : null;
    }

    private static LocalDate date(LocalDate value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return value != null && visible(access, code) ? value : null;
    }

    private static java.time.LocalDateTime datetime(
            java.time.LocalDateTime value, Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return value != null && visible(access, code) ? value : null;
    }

    private static boolean visible(Map<FieldCode, EffectiveFieldAccess> access, String code) {
        EffectiveFieldAccess field = field(access, code);
        return field.accessLevel() != FieldAccessLevel.HIDDEN && field.maskType() == FieldMaskType.NONE;
    }

    private static EffectiveFieldAccess field(Map<FieldCode, EffectiveFieldAccess> access, String code) {
        return (access == null ? Map.<FieldCode, EffectiveFieldAccess>of() : access)
                .getOrDefault(new FieldCode(code), EffectiveFieldAccess.hidden());
    }
}
