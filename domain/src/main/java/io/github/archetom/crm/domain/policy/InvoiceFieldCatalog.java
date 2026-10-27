package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InvoiceFieldCatalog {
    public static final String APPLICATION_NUMBER = "application_number";
    public static final String CUSTOMER = "customer";
    public static final String CONTRACT = "contract";
    public static final String AMOUNT = "amount";
    public static final String REQUESTED_DATE = "requested_date";
    public static final String INVOICE_TYPE = "invoice_type";
    public static final String TITLE_TYPE = "title_type";
    public static final String TITLE = "title";
    public static final String TAX_NUMBER = "tax_number";
    public static final String BANK_NAME = "bank_name";
    public static final String BANK_ACCOUNT = "bank_account";
    public static final String REGISTERED_ADDRESS = "registered_address";
    public static final String REGISTERED_PHONE = "registered_phone";
    public static final String CONTACT_NAME = "contact_name";
    public static final String CONTACT_PHONE = "contact_phone";
    public static final String SHIPPING_ADDRESS = "shipping_address";
    public static final String REMARK = "remark";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String STATUS = "status";
    public static final String INVOICE_NUMBER = "invoice_number";
    public static final String ISSUED_DATE = "issued_date";
    public static final String LOGISTICS_NUMBER = "logistics_number";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(
            code(APPLICATION_NUMBER), code(CUSTOMER), code(CONTRACT), code(AMOUNT),
            code(REQUESTED_DATE), code(INVOICE_TYPE), code(TITLE_TYPE), code(TITLE),
            code(TAX_NUMBER), code(BANK_NAME), code(BANK_ACCOUNT), code(REGISTERED_ADDRESS),
            code(REGISTERED_PHONE), code(CONTACT_NAME), code(CONTACT_PHONE),
            code(SHIPPING_ADDRESS), code(REMARK), code(OWNER_ACTOR), code(STATUS),
            code(INVOICE_NUMBER), code(ISSUED_DATE), code(LOGISTICS_NUMBER),
            code(CREATED_TIME), code(UPDATED_TIME));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(APPLICATION_NUMBER), SearchMatchMode.LITERAL_PREFIX,
            code(TITLE), SearchMatchMode.LITERAL_PREFIX,
            code(INVOICE_NUMBER), SearchMatchMode.LITERAL_PREFIX);

    private InvoiceFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static invoice search candidates. Application query code must intersect these
     * with active, searchable, readable, and unmasked effective field access before querying.
     */
    public static Map<FieldCode, SearchMatchMode> searchCandidates() {
        return SEARCH_CANDIDATES;
    }

    public static List<FieldDefinition> definitions(TenantId tenantId) {
        return List.of(
                field(tenantId, APPLICATION_NUMBER, "Application number", CustomFieldType.TEXT, true, true, 10, List.of()),
                field(tenantId, CUSTOMER, "Customer", CustomFieldType.RELATION, true, true, 20, List.of()),
                field(tenantId, CONTRACT, "Contract", CustomFieldType.RELATION, true, true, 30, List.of()),
                field(tenantId, AMOUNT, "Invoice amount", CustomFieldType.DECIMAL, true, true, 40, List.of()),
                field(tenantId, REQUESTED_DATE, "Requested date", CustomFieldType.DATE, false, true, 50, List.of()),
                field(tenantId, INVOICE_TYPE, "Invoice type", CustomFieldType.TEXT, true, true, 60, List.of()),
                field(tenantId, TITLE_TYPE, "Title type", CustomFieldType.SELECT, true, true, 70,
                        List.of(option(InvoiceTitleType.ORGANIZATION, "Organization", 0),
                                option(InvoiceTitleType.INDIVIDUAL, "Individual", 1))),
                field(tenantId, TITLE, "Title", CustomFieldType.TEXT, true, true, 80, List.of()),
                field(tenantId, TAX_NUMBER, "Tax number", CustomFieldType.TEXT, false, true, 90, List.of()),
                field(tenantId, BANK_NAME, "Bank name", CustomFieldType.TEXT, false, true, 100, List.of()),
                field(tenantId, BANK_ACCOUNT, "Bank account", CustomFieldType.TEXT, false, true, 110, List.of()),
                field(tenantId, REGISTERED_ADDRESS, "Registered address", CustomFieldType.ADDRESS, false, true, 120, List.of()),
                field(tenantId, REGISTERED_PHONE, "Registered phone", CustomFieldType.PHONE, false, true, 130, List.of()),
                field(tenantId, CONTACT_NAME, "Contact name", CustomFieldType.TEXT, false, true, 140, List.of()),
                field(tenantId, CONTACT_PHONE, "Contact phone", CustomFieldType.PHONE, false, true, 150, List.of()),
                field(tenantId, SHIPPING_ADDRESS, "Shipping address", CustomFieldType.ADDRESS, false, true, 160, List.of()),
                field(tenantId, REMARK, "Remark", CustomFieldType.TEXTAREA, false, true, 170, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER, true, true, 180, List.of()),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT, false, true, 190,
                        List.of(option(InvoiceStatus.PENDING, "Pending", 0),
                                option(InvoiceStatus.ISSUED, "Issued", 1))),
                field(tenantId, INVOICE_NUMBER, "Invoice number", CustomFieldType.TEXT, false, true, 200, List.of()),
                field(tenantId, ISSUED_DATE, "Issued date", CustomFieldType.DATE, false, true, 210, List.of()),
                field(tenantId, LOGISTICS_NUMBER, "Logistics number", CustomFieldType.TEXT, false, true, 220, List.of()),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME, false, true, 230, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME, false, true, 240, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, boolean searchable, int order, List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.INVOICE,
                new FieldCode(code), name, null, type, required, false, searchable,
                order, "Invoice", options);
    }

    private static FieldOption option(Enum<?> value, String label, int order) {
        return new FieldOption(value.name(), label, order);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
