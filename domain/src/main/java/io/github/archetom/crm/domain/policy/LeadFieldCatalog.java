package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.LeadDealStatus;
import io.github.archetom.crm.domain.model.LeadStatus;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable built-in lead fields shared by field policies and saved views. */
public final class LeadFieldCatalog {

    public static final String NAME = "lead_name";
    public static final String PHONE_NUMBER = "phone_number";
    public static final String TELEPHONE = "telephone";
    public static final String JOB_TITLE = "job_title";
    public static final String EMAIL = "email";
    public static final String WEBSITE = "website";
    public static final String DESCRIPTION = "description";
    public static final String NEXT_CONTACT_TIME = "next_contact_time";
    public static final String TAGS = "tags";
    public static final String ADDRESSES = "addresses";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String CUSTOMER = "customer";
    public static final String PARENT_LEAD = "parent_lead";
    public static final String LOCKED = "locked";
    public static final String DEAL_STATUS = "deal_status";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(
            code(NAME), code(PHONE_NUMBER), code(TELEPHONE), code(JOB_TITLE), code(EMAIL), code(WEBSITE),
            code(DESCRIPTION), code(NEXT_CONTACT_TIME), code(TAGS), code(ADDRESSES), code(OWNER_ACTOR), code(CUSTOMER), code(PARENT_LEAD), code(LOCKED),
            code(DEAL_STATUS), code(STATUS), code(CREATED_TIME), code(UPDATED_TIME));
    private static final Map<FieldCode, SearchMatchMode> SEARCH_CANDIDATES = Map.of(
            code(NAME), SearchMatchMode.LITERAL_PREFIX,
            code(PHONE_NUMBER), SearchMatchMode.LITERAL_PREFIX,
            code(TELEPHONE), SearchMatchMode.LITERAL_PREFIX,
            code(EMAIL), SearchMatchMode.LITERAL_PREFIX);

    private LeadFieldCatalog() {
    }

    public static Set<FieldCode> codes() {
        return CODES;
    }

    /**
     * Returns static lead search candidates. Application query code must intersect these
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
                field(tenantId, NAME, "Lead name", CustomFieldType.TEXT,
                        true, false, true, 10, List.of()),
                field(tenantId, PHONE_NUMBER, "Phone number", CustomFieldType.PHONE,
                        false, false, true, 20, List.of()),
                field(tenantId, TELEPHONE, "Telephone", CustomFieldType.TEXT,
                        false, false, true, 30, List.of()),
                field(tenantId, JOB_TITLE, "Job title", CustomFieldType.TEXT,
                        false, false, true, 35, List.of()),
                field(tenantId, EMAIL, "Email", CustomFieldType.EMAIL,
                        false, false, true, 40, List.of()),
                field(tenantId, WEBSITE, "Website", CustomFieldType.URL,
                        false, false, true, 50, List.of()),
                field(tenantId, DESCRIPTION, "Description", CustomFieldType.TEXTAREA,
                        false, false, true, 60, List.of()),
                field(tenantId, NEXT_CONTACT_TIME, "Next contact time", CustomFieldType.DATETIME,
                        false, false, true, 62, List.of()),
                field(tenantId, TAGS, "Tags", CustomFieldType.TEXTAREA,
                        false, false, false, 65, List.of()),
                field(tenantId, ADDRESSES, "Addresses", CustomFieldType.ADDRESS,
                        false, false, false, 66, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER,
                        true, false, true, 70, List.of()),
                field(tenantId, CUSTOMER, "Customer", CustomFieldType.RELATION,
                        false, false, true, 75, List.of()),
                field(tenantId, PARENT_LEAD, "Parent lead", CustomFieldType.RELATION,
                        false, false, true, 80, List.of()),
                field(tenantId, LOCKED, "Locked", CustomFieldType.BOOLEAN,
                        false, false, true, 90, List.of()),
                field(tenantId, DEAL_STATUS, "Deal status", CustomFieldType.SELECT,
                        false, false, true, 100, List.of(
                                new FieldOption(LeadDealStatus.OPEN.name(), "Open", 0),
                                new FieldOption(LeadDealStatus.WON.name(), "Won", 1))),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT,
                        false, false, true, 110, List.of(
                                new FieldOption(LeadStatus.ACTIVE.name(), "Active", 0),
                                new FieldOption(LeadStatus.CONVERTED.name(), "Converted", 1))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME,
                        false, false, true, 120, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME,
                        false, false, true, 130, List.of()));
    }

    private static FieldDefinition field(
            TenantId tenantId, String code, String name, CustomFieldType type,
            boolean required, boolean unique, boolean searchable, int order,
            List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.LEAD,
                new FieldCode(code), name, null, type, required, unique, searchable,
                order, "Lead", options);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
