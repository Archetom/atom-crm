package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReceivableFieldCatalog {
    public static final String NUMBER = "receipt_number";
    public static final String CUSTOMER = "customer";
    public static final String CONTRACT = "contract";
    public static final String PLAN = "receivable_plan";
    public static final String RECEIVED_DATE = "received_date";
    public static final String PAYMENT_METHOD = "payment_method";
    public static final String AMOUNT = "amount";
    public static final String REMARK = "remark";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(code(NUMBER), code(CUSTOMER),
            code(CONTRACT), code(PLAN), code(RECEIVED_DATE), code(PAYMENT_METHOD),
            code(AMOUNT), code(REMARK), code(OWNER_ACTOR), code(STATUS),
            code(CREATED_TIME), code(UPDATED_TIME));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(NUMBER), SearchMatchMode.LITERAL_PREFIX);

    private ReceivableFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static receivable search candidates. Application query code must intersect these
     * with active, searchable, readable, and unmasked effective field access before querying.
     */
    public static Map<FieldCode, SearchMatchMode> searchCandidates() {
        return SEARCH_CANDIDATES;
    }

    public static List<FieldDefinition> definitions(TenantId tenantId) {
        return List.of(
                field(tenantId, NUMBER, "Receipt number", CustomFieldType.TEXT, true, 10, List.of()),
                field(tenantId, CUSTOMER, "Customer", CustomFieldType.RELATION, true, 20, List.of()),
                field(tenantId, CONTRACT, "Contract", CustomFieldType.RELATION, true, 30, List.of()),
                field(tenantId, PLAN, "Receivable plan", CustomFieldType.RELATION, false, 40, List.of()),
                field(tenantId, RECEIVED_DATE, "Received date", CustomFieldType.DATE, true, 50, List.of()),
                field(tenantId, PAYMENT_METHOD, "Payment method", CustomFieldType.TEXT, true, 60, List.of()),
                field(tenantId, AMOUNT, "Received amount", CustomFieldType.DECIMAL, true, 70, List.of()),
                field(tenantId, REMARK, "Remark", CustomFieldType.TEXTAREA, false, 80, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER, true, 90, List.of()),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT, false, 100,
                        List.of(option(ReceivableStatus.ACTIVE, "Active", 0))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME, false, 110, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME, false, 120, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, int order, List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.RECEIVABLE,
                new FieldCode(code), name, null, type, required, false, true,
                order, "Receivable", options);
    }

    private static FieldOption option(Enum<?> value, String label, int order) {
        return new FieldOption(value.name(), label, order);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
