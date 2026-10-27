package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerDealStatus;
import io.github.archetom.crm.domain.model.CustomerStatus;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable built-in customer fields shared by field policies and saved views. */
public final class CustomerFieldCatalog {

    public static final String NAME = "customer_name";
    public static final String PHONE_NUMBER = "phone_number";
    public static final String TELEPHONE = "telephone";
    public static final String EMAIL = "email";
    public static final String WEBSITE = "website";
    public static final String DESCRIPTION = "description";
    public static final String TAGS = "tags";
    public static final String ADDRESSES = "addresses";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String PARENT_CUSTOMER = "parent_customer";
    public static final String LOCKED = "locked";
    public static final String DEAL_STATUS = "deal_status";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(
            code(NAME), code(PHONE_NUMBER), code(TELEPHONE), code(EMAIL), code(WEBSITE),
            code(DESCRIPTION), code(TAGS), code(ADDRESSES), code(OWNER_ACTOR), code(PARENT_CUSTOMER), code(LOCKED),
            code(DEAL_STATUS), code(STATUS), code(CREATED_TIME), code(UPDATED_TIME));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(NAME), SearchMatchMode.LITERAL_PREFIX,
            code(PHONE_NUMBER), SearchMatchMode.LITERAL_PREFIX,
            code(TELEPHONE), SearchMatchMode.LITERAL_PREFIX,
            code(EMAIL), SearchMatchMode.LITERAL_PREFIX);

    private CustomerFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static customer search candidates. Application query code must intersect these
     * with active, searchable, readable, and unmasked effective field access before querying.
     */
    public static Map<FieldCode, SearchMatchMode> searchCandidates() {
        return SEARCH_CANDIDATES;
    }

    public static boolean isBuiltin(FieldCode code) {
        return code != null && CODES.contains(code);
    }

    public static List<FieldDefinition> definitions(TenantId tenantId) {
        return List.of(
                field(tenantId, NAME, "Customer name", CustomFieldType.TEXT,
                        true, false, true, 10, List.of()),
                field(tenantId, PHONE_NUMBER, "Phone number", CustomFieldType.PHONE,
                        false, false, true, 20, List.of()),
                field(tenantId, TELEPHONE, "Telephone", CustomFieldType.TEXT,
                        false, false, true, 30, List.of()),
                field(tenantId, EMAIL, "Email", CustomFieldType.EMAIL,
                        false, false, true, 40, List.of()),
                field(tenantId, WEBSITE, "Website", CustomFieldType.URL,
                        false, false, true, 50, List.of()),
                field(tenantId, DESCRIPTION, "Description", CustomFieldType.TEXTAREA,
                        false, false, true, 60, List.of()),
                field(tenantId, TAGS, "Tags", CustomFieldType.TEXTAREA,
                        false, false, false, 65, List.of()),
                field(tenantId, ADDRESSES, "Addresses", CustomFieldType.ADDRESS,
                        false, false, false, 66, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER,
                        true, false, true, 70, List.of()),
                field(tenantId, PARENT_CUSTOMER, "Parent customer", CustomFieldType.RELATION,
                        false, false, true, 80, List.of()),
                field(tenantId, LOCKED, "Locked", CustomFieldType.BOOLEAN,
                        false, false, true, 90, List.of()),
                field(tenantId, DEAL_STATUS, "Deal status", CustomFieldType.SELECT,
                        false, false, true, 100, List.of(
                                new FieldOption(CustomerDealStatus.OPEN.name(), "Open", 0),
                                new FieldOption(CustomerDealStatus.WON.name(), "Won", 1))),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT,
                        false, false, true, 110, List.of(
                                new FieldOption(CustomerStatus.ACTIVE.name(), "Active", 0),
                                new FieldOption(CustomerStatus.IN_POOL.name(), "In pool", 1))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME,
                        false, false, true, 120, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME,
                        false, false, true, 130, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, boolean unique, boolean searchable, int order,
            List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.CUSTOMER,
                new FieldCode(code), name, null, type, required, unique, searchable,
                order, "Customer", options);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
