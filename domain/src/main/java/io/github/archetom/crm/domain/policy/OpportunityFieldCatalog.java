package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable opportunity fields shared by field policies, persistence, and saved views. */
public final class OpportunityFieldCatalog {

    public static final String NAME = "opportunity_name";
    public static final String CUSTOMER = "customer";
    public static final String PRIMARY_CONTACT = "primary_contact";
    public static final String CONTACTS = "contacts";
    public static final String PIPELINE_CODE = "pipeline_code";
    public static final String STAGE_CODE = "stage_code";
    public static final String EXPECTED_CLOSE_DATE = "expected_close_date";
    public static final String ESTIMATED_AMOUNT = "estimated_amount";
    public static final String NEXT_CONTACT_TIME = "next_contact_time";
    public static final String DESCRIPTION = "description";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String OUTCOME = "outcome";
    public static final String OUTCOME_TIME = "outcome_time";
    public static final String OUTCOME_REMARK = "outcome_remark";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";
    public static final String PRODUCTS = "products";

    private static final Set<FieldCode> CODES = Set.of(
            code(NAME), code(CUSTOMER), code(PRIMARY_CONTACT), code(CONTACTS),
            code(PIPELINE_CODE), code(STAGE_CODE), code(EXPECTED_CLOSE_DATE),
            code(ESTIMATED_AMOUNT), code(NEXT_CONTACT_TIME), code(DESCRIPTION),
            code(OWNER_ACTOR), code(OUTCOME), code(OUTCOME_TIME),
            code(OUTCOME_REMARK), code(STATUS), code(CREATED_TIME), code(UPDATED_TIME),
            code(PRODUCTS));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(NAME), SearchMatchMode.LITERAL_PREFIX);

    private OpportunityFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static opportunity search candidates. Application query code must intersect these
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
                field(tenantId, NAME, "Opportunity name", CustomFieldType.TEXT,
                        true, true, 10, List.of()),
                field(tenantId, CUSTOMER, "Customer", CustomFieldType.RELATION,
                        true, true, 20, List.of()),
                field(tenantId, PRIMARY_CONTACT, "Primary contact", CustomFieldType.RELATION,
                        false, true, 30, List.of()),
                field(tenantId, CONTACTS, "Contacts", CustomFieldType.RELATION,
                        false, false, 40, List.of()),
                field(tenantId, PIPELINE_CODE, "Pipeline", CustomFieldType.TEXT,
                        true, true, 50, List.of()),
                field(tenantId, STAGE_CODE, "Sales stage", CustomFieldType.TEXT,
                        true, true, 60, List.of()),
                field(tenantId, EXPECTED_CLOSE_DATE, "Expected close date", CustomFieldType.DATE,
                        false, true, 70, List.of()),
                field(tenantId, ESTIMATED_AMOUNT, "Estimated amount", CustomFieldType.DECIMAL,
                        false, true, 80, List.of()),
                field(tenantId, NEXT_CONTACT_TIME, "Next contact time", CustomFieldType.DATETIME,
                        false, true, 90, List.of()),
                field(tenantId, DESCRIPTION, "Description", CustomFieldType.TEXTAREA,
                        false, true, 100, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER,
                        true, true, 110, List.of()),
                field(tenantId, OUTCOME, "Outcome", CustomFieldType.SELECT,
                        false, true, 120, List.of(
                                option(OpportunityOutcome.OPEN, "Open", 0),
                                option(OpportunityOutcome.WON, "Won", 1),
                                option(OpportunityOutcome.LOST, "Lost", 2),
                                option(OpportunityOutcome.INVALID, "Invalid", 3))),
                field(tenantId, OUTCOME_TIME, "Outcome time", CustomFieldType.DATETIME,
                        false, true, 130, List.of()),
                field(tenantId, OUTCOME_REMARK, "Outcome remark", CustomFieldType.TEXTAREA,
                        false, true, 140, List.of()),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT,
                        false, true, 150, List.of(
                                new FieldOption(OpportunityStatus.ACTIVE.name(), "Active", 0))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME,
                        false, true, 160, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME,
                        false, true, 170, List.of()),
                field(tenantId, PRODUCTS, "Products", CustomFieldType.RELATION,
                        false, false, 175, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, boolean searchable, int order, List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.OPPORTUNITY,
                new FieldCode(code), name, null, type, required, false, searchable,
                order, "Opportunity", options);
    }

    private static FieldOption option(
            OpportunityOutcome value, String label, int order) {
        return new FieldOption(value.name(), label, order);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
