package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.model.ProductStatus;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable product fields shared by field access and persistence queries. */
public final class ProductFieldCatalog {

    public static final String NAME = "product_name";
    public static final String CODE = "product_code";
    public static final String CATEGORY = "category";
    public static final String UNIT = "unit";
    public static final String LIST_PRICE = "list_price";
    public static final String DESCRIPTION = "description";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String AVAILABILITY = "availability";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(
            code(NAME), code(CODE), code(CATEGORY), code(UNIT), code(LIST_PRICE),
            code(DESCRIPTION), code(OWNER_ACTOR), code(AVAILABILITY), code(STATUS),
            code(CREATED_TIME), code(UPDATED_TIME));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(NAME), SearchMatchMode.LITERAL_PREFIX,
            code(CODE), SearchMatchMode.NORMALIZED_PREFIX);

    private ProductFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static product search candidates. Application query code must intersect these
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
                field(tenantId, NAME, "Product name", CustomFieldType.TEXT,
                        true, true, 10, List.of()),
                field(tenantId, CODE, "Product code", CustomFieldType.TEXT,
                        true, true, 20, List.of()),
                field(tenantId, CATEGORY, "Category", CustomFieldType.RELATION,
                        false, true, 30, List.of()),
                field(tenantId, UNIT, "Unit", CustomFieldType.TEXT,
                        false, true, 40, List.of()),
                field(tenantId, LIST_PRICE, "List price", CustomFieldType.DECIMAL,
                        true, true, 50, List.of()),
                field(tenantId, DESCRIPTION, "Description", CustomFieldType.TEXTAREA,
                        false, true, 60, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER,
                        true, true, 70, List.of()),
                field(tenantId, AVAILABILITY, "Availability", CustomFieldType.SELECT,
                        true, true, 80, List.of(
                                option(ProductAvailability.AVAILABLE, "Available", 0),
                                option(ProductAvailability.UNAVAILABLE, "Unavailable", 1))),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT,
                        false, true, 90, List.of(
                                new FieldOption(ProductStatus.ACTIVE.name(), "Active", 0))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME,
                        false, true, 100, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME,
                        false, true, 110, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, boolean searchable, int order, List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.PRODUCT,
                new FieldCode(code), name, null, type, required, false, searchable,
                order, "Product", options);
    }

    private static FieldOption option(
            ProductAvailability value, String label, int order) {
        return new FieldOption(value.name(), label, order);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
