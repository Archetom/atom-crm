package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ReceivablePlanProgress;
import io.github.archetom.crm.domain.model.ReceivablePlanStatus;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReceivablePlanFieldCatalog {
    public static final String NUMBER = "plan_number";
    public static final String CUSTOMER = "customer";
    public static final String CONTRACT = "contract";
    public static final String DUE_DATE = "due_date";
    public static final String PLANNED_AMOUNT = "planned_amount";
    public static final String PAYMENT_METHOD = "payment_method";
    public static final String REMINDER_DAYS = "reminder_days";
    public static final String REMARK = "remark";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String RECEIVED_AMOUNT = "received_amount";
    public static final String REMAINING_AMOUNT = "remaining_amount";
    public static final String PROGRESS = "progress";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(code(NUMBER), code(CUSTOMER),
            code(CONTRACT), code(DUE_DATE), code(PLANNED_AMOUNT), code(PAYMENT_METHOD),
            code(REMINDER_DAYS), code(REMARK), code(OWNER_ACTOR), code(RECEIVED_AMOUNT),
            code(REMAINING_AMOUNT), code(PROGRESS), code(STATUS), code(CREATED_TIME),
            code(UPDATED_TIME));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(NUMBER), SearchMatchMode.EXACT);

    private ReceivablePlanFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static receivable-plan search candidates. Application query code must intersect
     * these with active, searchable, readable, and unmasked effective field access before querying.
     */
    public static Map<FieldCode, SearchMatchMode> searchCandidates() {
        return SEARCH_CANDIDATES;
    }

    /** Parses a search term as the positive integer used for an exact plan-number comparison. */
    public static int parseExactPlanNumber(SearchKeyword keyword) {
        if (keyword == null) {
            throw new IllegalArgumentException("plan number search keyword is required");
        }
        try {
            int value = Integer.parseInt(keyword.value());
            if (value < 1) {
                throw new IllegalArgumentException("plan number search keyword must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("plan number search keyword must be an integer", exception);
        }
    }

    public static List<FieldDefinition> definitions(TenantId tenantId) {
        return List.of(
                field(tenantId, NUMBER, "Plan number", CustomFieldType.INTEGER, true, 10, List.of()),
                field(tenantId, CUSTOMER, "Customer", CustomFieldType.RELATION, true, 20, List.of()),
                field(tenantId, CONTRACT, "Contract", CustomFieldType.RELATION, true, 30, List.of()),
                field(tenantId, DUE_DATE, "Due date", CustomFieldType.DATE, true, 40, List.of()),
                field(tenantId, PLANNED_AMOUNT, "Planned amount", CustomFieldType.DECIMAL, true, 50, List.of()),
                field(tenantId, PAYMENT_METHOD, "Payment method", CustomFieldType.TEXT, true, 60, List.of()),
                field(tenantId, REMINDER_DAYS, "Reminder days", CustomFieldType.INTEGER, false, 70, List.of()),
                field(tenantId, REMARK, "Remark", CustomFieldType.TEXTAREA, false, 80, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER, true, 90, List.of()),
                field(tenantId, RECEIVED_AMOUNT, "Received amount", CustomFieldType.DECIMAL, false, 100, List.of()),
                field(tenantId, REMAINING_AMOUNT, "Remaining amount", CustomFieldType.DECIMAL, false, 110, List.of()),
                field(tenantId, PROGRESS, "Progress", CustomFieldType.SELECT, false, 120,
                        List.of(option(ReceivablePlanProgress.PENDING, "Pending", 0),
                                option(ReceivablePlanProgress.PARTIAL, "Partial", 1),
                                option(ReceivablePlanProgress.COMPLETED, "Completed", 2),
                                option(ReceivablePlanProgress.OVERDUE, "Overdue", 3))),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT, false, 130,
                        List.of(option(ReceivablePlanStatus.ACTIVE, "Active", 0))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME, false, 140, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME, false, 150, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, int order, List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.RECEIVABLE_PLAN,
                new FieldCode(code), name, null, type, required, false, true,
                order, "Receivable plan", options);
    }

    private static FieldOption option(Enum<?> value, String label, int order) {
        return new FieldOption(value.name(), label, order);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
