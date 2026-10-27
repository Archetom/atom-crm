package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContractFieldCatalog {
    public static final String NAME = "contract_name";
    public static final String NUMBER = "contract_number";
    public static final String CUSTOMER = "customer";
    public static final String OPPORTUNITY = "opportunity";
    public static final String CONTACT = "contact";
    public static final String ORDER_DATE = "order_date";
    public static final String START_DATE = "start_date";
    public static final String END_DATE = "end_date";
    public static final String CONTRACT_TYPE = "contract_type";
    public static final String PAYMENT_TYPE = "payment_type";
    public static final String REMARK = "remark";
    public static final String PRODUCTS = "products";
    public static final String SUBTOTAL = "subtotal";
    public static final String DISCOUNT_RATE = "discount_rate";
    public static final String TOTAL_AMOUNT = "total_amount";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(
            code(NAME), code(NUMBER), code(CUSTOMER), code(OPPORTUNITY), code(CONTACT),
            code(ORDER_DATE), code(START_DATE), code(END_DATE), code(CONTRACT_TYPE),
            code(PAYMENT_TYPE), code(REMARK), code(PRODUCTS), code(SUBTOTAL),
            code(DISCOUNT_RATE), code(TOTAL_AMOUNT), code(OWNER_ACTOR), code(STATUS),
            code(CREATED_TIME), code(UPDATED_TIME));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(NAME), SearchMatchMode.LITERAL_PREFIX,
            code(NUMBER), SearchMatchMode.NORMALIZED_PREFIX);

    private ContractFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static contract search candidates. Application query code must intersect these
     * with active, searchable, readable, and unmasked effective field access before querying.
     */
    public static Map<FieldCode, SearchMatchMode> searchCandidates() {
        return SEARCH_CANDIDATES;
    }

    public static List<FieldDefinition> definitions(TenantId tenantId) {
        return List.of(
                field(tenantId, NAME, "Contract name", CustomFieldType.TEXT, true, true, 10, List.of()),
                field(tenantId, NUMBER, "Contract number", CustomFieldType.TEXT, true, true, 20, List.of()),
                field(tenantId, CUSTOMER, "Customer", CustomFieldType.RELATION, true, true, 30, List.of()),
                field(tenantId, OPPORTUNITY, "Opportunity", CustomFieldType.RELATION, false, true, 40, List.of()),
                field(tenantId, CONTACT, "Contact", CustomFieldType.RELATION, false, true, 50, List.of()),
                field(tenantId, ORDER_DATE, "Order date", CustomFieldType.DATE, false, true, 60, List.of()),
                field(tenantId, START_DATE, "Start date", CustomFieldType.DATE, false, true, 70, List.of()),
                field(tenantId, END_DATE, "End date", CustomFieldType.DATE, false, true, 80, List.of()),
                field(tenantId, CONTRACT_TYPE, "Contract type", CustomFieldType.TEXT, false, true, 90, List.of()),
                field(tenantId, PAYMENT_TYPE, "Payment type", CustomFieldType.TEXT, false, true, 100, List.of()),
                field(tenantId, REMARK, "Remark", CustomFieldType.TEXTAREA, false, true, 110, List.of()),
                field(tenantId, PRODUCTS, "Products", CustomFieldType.RELATION, false, false, 120, List.of()),
                field(tenantId, SUBTOTAL, "Product subtotal", CustomFieldType.DECIMAL, false, true, 130, List.of()),
                field(tenantId, DISCOUNT_RATE, "Discount rate", CustomFieldType.DECIMAL, false, true, 140, List.of()),
                field(tenantId, TOTAL_AMOUNT, "Contract amount", CustomFieldType.DECIMAL, false, true, 150, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER, true, true, 160, List.of()),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT, false, true, 170,
                        List.of(option(ContractStatus.ACTIVE, "Active", 0),
                                option(ContractStatus.VOIDED, "Voided", 1))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME, false, true, 180, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME, false, true, 190, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, boolean searchable, int order, List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.CONTRACT,
                new FieldCode(code), name, null, type, required, false, searchable,
                order, "Contract", options);
    }

    private static FieldOption option(ContractStatus value, String label, int order) {
        return new FieldOption(value.name(), label, order);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
