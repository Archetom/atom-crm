package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Set;

/** Built-in authoritative facts shared by B20 OA forms and request approvals. */
public final class OaRequestFieldCatalog {
    public static final String REQUEST_TITLE = "request_title";
    public static final String FORM_CODE = "form_code";
    public static final String APPLICANT_ACTOR = "applicant_actor";
    public static final String APPLICANT_DEPARTMENT = "applicant_department";
    public static final String SUBMITTED_TIME = "submitted_time";

    private static final Set<FieldCode> SERVER_DERIVED_CODES = Set.of(
            code(FORM_CODE), code(APPLICANT_ACTOR), code(APPLICANT_DEPARTMENT),
            code(SUBMITTED_TIME));

    private OaRequestFieldCatalog() {
    }

    public static Set<FieldCode> serverDerivedCodes() {
        return SERVER_DERIVED_CODES;
    }

    public static List<FieldDefinition> definitions(TenantId tenantId) {
        return List.of(
                field(tenantId, REQUEST_TITLE, "Request title", CustomFieldType.TEXT,
                        true, 10),
                field(tenantId, FORM_CODE, "Form code", CustomFieldType.TEXT,
                        true, 20),
                field(tenantId, APPLICANT_ACTOR, "Applicant", CustomFieldType.USER,
                        true, 30),
                field(tenantId, APPLICANT_DEPARTMENT, "Applicant department",
                        CustomFieldType.DEPARTMENT, false, 40),
                field(tenantId, SUBMITTED_TIME, "Submitted time",
                        CustomFieldType.DATETIME, true, 50));
    }

    private static FieldDefinition field(TenantId tenantId, String code, String name,
                                         CustomFieldType type, boolean required, int order) {
        return FieldDefinition.createBuiltin(tenantId, BusinessObjectType.OA_REQUEST,
                new FieldCode(code), name, null, type, required, false, true,
                order, "OA request", List.of());
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
