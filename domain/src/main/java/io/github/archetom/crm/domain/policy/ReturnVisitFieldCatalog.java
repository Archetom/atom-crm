package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReturnVisitFieldCatalog {
    public static final String NUMBER = "visit_number";
    public static final String CUSTOMER = "customer";
    public static final String CONTACT = "contact";
    public static final String CONTRACT = "contract";
    public static final String VISIT_TIME = "visit_time";
    public static final String VISIT_FORM = "visit_form";
    public static final String SATISFACTION = "satisfaction";
    public static final String REMARK = "remark";
    public static final String OWNER_ACTOR = "owner_actor";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "created_time";
    public static final String UPDATED_TIME = "updated_time";

    private static final Set<FieldCode> CODES = Set.of(code(NUMBER), code(CUSTOMER), code(CONTACT),
            code(CONTRACT), code(VISIT_TIME), code(VISIT_FORM), code(SATISFACTION), code(REMARK), code(OWNER_ACTOR), code(STATUS),
            code(CREATED_TIME), code(UPDATED_TIME));

    private ReturnVisitFieldCatalog() {
    }

    public static Set<FieldCode> codes() { return CODES; }

    /**
     * Static search candidates. Query adapters must intersect these with active, searchable,
     * readable, unmasked effective field access before constructing persistence predicates.
     */
    public static Map<FieldCode, SearchMatchMode> searchCandidates() {
        return Map.of(code(NUMBER), SearchMatchMode.LITERAL_PREFIX);
    }

    public static List<FieldDefinition> definitions(TenantId tenantId) {
        return List.of(
                field(tenantId, NUMBER, "Visit number", CustomFieldType.TEXT, true, 10, List.of()),
                field(tenantId, CUSTOMER, "Customer", CustomFieldType.RELATION, true, 20, List.of()),
                field(tenantId, CONTACT, "Contact", CustomFieldType.RELATION, false, 30, List.of()),
                field(tenantId, CONTRACT, "Contract", CustomFieldType.RELATION, false, 40, List.of()),
                field(tenantId, VISIT_TIME, "Visit time", CustomFieldType.DATETIME, true, 50, List.of()),
                field(tenantId, VISIT_FORM, "Visit form", CustomFieldType.SELECT, false, 60,
                        List.of(new FieldOption("IN_PERSON", "In-person", 0),
                                new FieldOption("PHONE", "Phone", 1), new FieldOption("SMS", "SMS", 2),
                                new FieldOption("EMAIL", "Email", 3), new FieldOption("WECHAT", "WeChat", 4))),
                field(tenantId, SATISFACTION, "Customer satisfaction", CustomFieldType.SELECT, false, 70,
                        List.of(new FieldOption("VERY_SATISFIED", "Very satisfied", 0),
                                new FieldOption("SATISFIED", "Satisfied", 1), new FieldOption("NEUTRAL", "Neutral", 2),
                                new FieldOption("DISSATISFIED", "Dissatisfied", 3),
                                new FieldOption("VERY_DISSATISFIED", "Very dissatisfied", 4))),
                field(tenantId, REMARK, "Remark", CustomFieldType.TEXTAREA, false, 80, List.of()),
                field(tenantId, OWNER_ACTOR, "Owner", CustomFieldType.USER, true, 90, List.of()),
                field(tenantId, STATUS, "Status", CustomFieldType.SELECT, false, 100,
                        List.of(new FieldOption(ReturnVisitStatus.ACTIVE.name(), "Active", 0))),
                field(tenantId, CREATED_TIME, "Created time", CustomFieldType.DATETIME, false, 110, List.of()),
                field(tenantId, UPDATED_TIME, "Updated time", CustomFieldType.DATETIME, false, 120, List.of()));
    }

    private static FieldDefinition field(TenantId tenantId, String code, String name,
                                         CustomFieldType type, boolean required, int order,
                                         List<FieldOption> options) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.RETURN_VISIT,
                new FieldCode(code), name, null, type, required, false, true, order,
                "Return visit", options);
    }
    private static FieldCode code(String value) { return new FieldCode(value); }
}
