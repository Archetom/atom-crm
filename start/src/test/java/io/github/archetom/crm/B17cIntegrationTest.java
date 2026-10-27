package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionCreateRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionRunRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReportMeasureRequest;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ReportCatalogResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionRunResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B17c ownership, route authorization, Flyway storage, and production MySQL aggregation. */
@RequiresDatabase
class B17cIntegrationTest extends BaseIntegrationTest {

    private static final long TENANT = 1_717_900L;
    private static final long OWNER = 1_729_001L;
    private static final long READER = 1_729_002L;

    @Test
    void personalDefinitionCrudCatalogAndExecutionStayPermissionBounded() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedReader(jdbc);
        long caseFieldId = seedCaseSensitiveSelect(jdbc);
        seedOpportunities(jdbc, caseFieldId);

        ReportCatalogResponse defaultCatalog = fromJson(mockMvc.perform(
                        as(OWNER, get("/api/v1/reports/catalog")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReportCatalogResponse.class);
        assertEquals("OPPORTUNITY", defaultCatalog.getObjectType());
        assertTrue(defaultCatalog.getSupportedObjectTypes().contains("OPPORTUNITY"));

        String catalogJson = mockMvc.perform(as(OWNER, get("/api/v1/reports/catalog")
                        .param("objectType", "OPPORTUNITY")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ReportCatalogResponse catalog = fromJson(catalogJson, ReportCatalogResponse.class);
        assertTrue(catalog.getTimeFields().stream()
                .anyMatch(field -> "outcome_time".equals(field.getFieldCode())));
        assertTrue(catalog.getDimensionFields().stream()
                .anyMatch(field -> "outcome".equals(field.getFieldCode())));
        assertTrue(catalog.getSumFields().stream()
                .anyMatch(field -> "estimated_amount".equals(field.getFieldCode())));
        assertTrue(catalog.getDimensionFields().stream()
                .anyMatch(field -> "case_key".equals(field.getFieldCode())));
        assertTrue(catalog.getFilterFields().stream()
                .anyMatch(field -> "case_key".equals(field.getFieldCode())));
        assertTrue(catalog.getFilterFields().stream()
                .noneMatch(field -> "customer".equals(field.getFieldCode())));

        ReportDefinitionCreateRequest relationFilter = definition("Relation filter rejected")
                .setFilters(List.of(new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("EQ").setValues(List.of("1"))));
        mockMvc.perform(as(OWNER, post("/api/v1/reports/definitions")
                .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(relationFilter))))
                .andExpect(status().isUnprocessableEntity());

        ReportDefinitionCreateRequest create = new ReportDefinitionCreateRequest()
                .setName("  Outcome totals  ").setDescription("Personal report")
                .setObjectType("OPPORTUNITY").setTimeFieldCode("outcome_time")
                .setGranularity("DAY").setDimensionFieldCode("outcome")
                .setMeasures(List.of(new ReportMeasureRequest().setType("COUNT"),
                        new ReportMeasureRequest().setType("SUM")
                                .setFieldCode("estimated_amount")));
        String createdJson = mockMvc.perform(as(OWNER, post("/api/v1/reports/definitions")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(create))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ReportDefinitionResponse created = fromJson(createdJson, ReportDefinitionResponse.class);
        assertEquals("Outcome totals", created.getName());
        assertEquals(0L, created.getVersion());

        mockMvc.perform(as(READER, post("/api/v1/reports/definitions")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(create))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(READER, get("/api/v1/reports/definitions/{id}", created.getId())))
                .andExpect(status().isNotFound());
        String readerPage = mockMvc.perform(as(READER,
                        post("/api/v1/reports/definitions/query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ReportDefinitionQueryRequest()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(readerPage.contains("\"objectList\":[]"));

        String runJson = mockMvc.perform(as(OWNER,
                        post("/api/v1/reports/definitions/{id}/run", created.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ReportDefinitionRunRequest()
                                        .setFrom(LocalDate.of(2026, 1, 1))
                                        .setTo(LocalDate.of(2026, 1, 2))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ReportDefinitionRunResponse run = fromJson(runJson, ReportDefinitionRunResponse.class);
        assertEquals(3, run.getRows().size());
        assertEquals(0, new BigDecimal("10.10").compareTo((BigDecimal) run.getRows().getFirst()
                .getValues().get("SUM:estimated_amount")));
        assertEquals("Won", run.getRows().getFirst().getDimension().getLabel());

        ReportDefinitionResponse grouped = create(OWNER,
                definition("Case-sensitive groups").setDimensionFieldCode("case_key"));
        ReportDefinitionRunResponse groupedRun = run(OWNER, grouped.getId(),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2));
        assertEquals(List.of("A", "a"), groupedRun.getRows().stream()
                .map(row -> row.getDimension().getKey()).toList());
        assertTrue(groupedRun.getRows().stream().allMatch(row ->
                BigDecimal.ONE.compareTo(row.getValues().get("COUNT")) == 0));

        ReportDefinitionResponse exact = create(OWNER,
                definition("Case-sensitive filter").setDimensionFieldCode("case_key")
                        .setFilters(List.of(new FilterConditionRequest()
                                .setFieldCode("case_key").setOperator("EQ")
                                .setValues(List.of("A")))));
        ReportDefinitionRunResponse exactRun = run(OWNER, exact.getId(),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2));
        assertEquals(1, exactRun.getRows().size());
        assertEquals("A", exactRun.getRows().getFirst().getDimension().getKey());
        assertEquals(0, BigDecimal.ONE.compareTo(
                exactRun.getRows().getFirst().getValues().get("COUNT")));

        FieldDefinitionResponse dependencyField = createField(
                "B17c dependency", "BOOLEAN");
        FieldDefinitionResponse measureDependencyField = createField(
                "B17c measure dependency", "DECIMAL");
        ReportDefinitionResponse dependencyReport = create(OWNER,
                definition("Dependency protected")
                        .setFilters(List.of(new FilterConditionRequest()
                                .setFieldCode(dependencyField.getCode())
                                .setOperator("EQ").setValues(List.of("true"))))
                        .setMeasures(List.of(new ReportMeasureRequest().setType("COUNT"),
                                new ReportMeasureRequest().setType("SUM")
                                        .setFieldCode(measureDependencyField.getCode()))));
        FieldUpdateRequest disableSearch = new FieldUpdateRequest()
                .setVersion(dependencyField.getVersion())
                .setDisplayName(dependencyField.getDisplayName())
                .setSearchable(false);
        mockMvc.perform(as(OWNER, put("/api/v1/fields/objects/OPPORTUNITY/{fieldId}",
                                dependencyField.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(disableSearch))))
                .andExpect(status().isUnprocessableEntity());
        FieldUpdateRequest disableMeasureSearch = new FieldUpdateRequest()
                .setVersion(measureDependencyField.getVersion())
                .setDisplayName(measureDependencyField.getDisplayName())
                .setSearchable(false);
        mockMvc.perform(as(OWNER, put("/api/v1/fields/objects/OPPORTUNITY/{fieldId}",
                                measureDependencyField.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(disableMeasureSearch))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(as(OWNER, delete("/api/v1/reports/definitions/{id}",
                        dependencyReport.getId()).param("version",
                        dependencyReport.getVersion().toString())))
                .andExpect(status().isOk());
        String disabledFieldJson = mockMvc.perform(as(OWNER,
                        put("/api/v1/fields/objects/OPPORTUNITY/{fieldId}",
                                dependencyField.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(disableSearch))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(fromJson(disabledFieldJson,
                FieldDefinitionResponse.class).getSearchable());

        long rowKeyFieldId = seedSimpleField(jdbc, "row_key", "USER");
        seedBoundedResultRows(jdbc, rowKeyFieldId);
        ReportDefinitionResponse bounded = create(OWNER,
                definition("Bounded result").setDimensionFieldCode("row_key"));
        mockMvc.perform(as(OWNER,
                        post("/api/v1/reports/definitions/{id}/run", bounded.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ReportDefinitionRunRequest()
                                        .setFrom(LocalDate.of(2026, 3, 1))
                                        .setTo(LocalDate.of(2026, 3, 1))))))
                .andExpect(status().isUnprocessableEntity());

        String expandingName = "\uFDFA".repeat(100);
        ReportDefinitionCreateRequest expanding = definition(expandingName);
        ReportDefinitionResponse expanded = create(OWNER, expanding);
        String persistedNormalized = jdbc.queryForObject(
                "SELECT normalized_name FROM report_definition WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, expanded.getId());
        String persistedHash = jdbc.queryForObject(
                "SELECT normalized_name_hash FROM report_definition WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, expanded.getId());
        assertTrue(persistedNormalized.length() > 400);
        assertEquals(64, persistedHash.length());
        mockMvc.perform(as(OWNER, post("/api/v1/reports/definitions")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(expanding))))
                .andExpect(status().isConflict());

        ReportDefinitionUpdateRequest update = new ReportDefinitionUpdateRequest()
                .setName("Outcome totals v2").setDescription(null)
                .setObjectType("OPPORTUNITY").setTimeFieldCode("outcome_time")
                .setGranularity("MONTH").setDimensionFieldCode(null)
                .setMeasures(create.getMeasures()).setVersion(created.getVersion());
        String updatedJson = mockMvc.perform(as(OWNER,
                        put("/api/v1/reports/definitions/{id}", created.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(toJson(update))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ReportDefinitionResponse updated = fromJson(updatedJson, ReportDefinitionResponse.class);
        assertEquals("MONTH", updated.getGranularity());
        assertEquals(1L, updated.getVersion());
        assertNull(updated.getDescription());
        assertNull(updated.getDimensionFieldCode());
        String reloadedJson = mockMvc.perform(as(OWNER,
                        get("/api/v1/reports/definitions/{id}", created.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ReportDefinitionResponse reloaded = fromJson(reloadedJson, ReportDefinitionResponse.class);
        assertNull(reloaded.getDescription());
        assertNull(reloaded.getDimensionFieldCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM report_definition "
                        + "WHERE tenant_id = ? AND id = ? AND description IS NULL "
                        + "AND dimension_field_code IS NULL",
                Integer.class, TENANT, created.getId()));

        mockMvc.perform(as(OWNER, delete("/api/v1/reports/definitions/{id}", created.getId())
                        .param("version", updated.getVersion().toString())))
                .andExpect(status().isOk());
        mockMvc.perform(as(OWNER, post("/api/v1/reports/definitions/{id}/run", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReportDefinitionRunRequest()
                                .setFrom(LocalDate.of(2026, 1, 1))
                                .setTo(LocalDate.of(2026, 1, 2))))))
                .andExpect(status().isNotFound());
        assertEquals("DELETED", jdbc.queryForObject(
                "SELECT status FROM report_definition WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, created.getId()));
    }

    private void bootstrap() throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName("B17c owner")))
                        .header("X-Dev-User-Id", OWNER)
                        .header("X-Dev-Tenant-Id", TENANT))
                .andExpect(status().isOk());
    }

    private void seedReader(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO organization_member_profile "
                + "(actor_id, display_name) VALUES (?, 'B17c reader')", READER);
        jdbc.update("INSERT INTO organization_membership "
                + "(tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')", TENANT, READER);
        jdbc.update("INSERT INTO authorization_role "
                + "(tenant_id, name, status) VALUES (?, 'B17c read only', 'ACTIVE')", TENANT);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                + "WHERE tenant_id = ? AND name = 'B17c read only'", Long.class, TENANT);
        jdbc.update("INSERT INTO authorization_role_grant "
                + "(tenant_id, role_id, authority_code, data_scope) VALUES (?, ?, 'reports:read', NULL)",
                TENANT, roleId);
        jdbc.update("INSERT INTO authorization_role_grant "
                + "(tenant_id, role_id, authority_code, data_scope) "
                + "VALUES (?, ?, 'opportunities:read', 'TENANT')", TENANT, roleId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)", TENANT, READER, roleId);
        jdbc.update("INSERT INTO role_field_policy "
                + "(tenant_id, role_id, object_type) VALUES (?, ?, 'OPPORTUNITY')", TENANT, roleId);
        jdbc.update("INSERT INTO role_field_access "
                + "(tenant_id, role_id, object_type, field_id, access_level, mask_type) "
                + "SELECT ?, ?, 'OPPORTUNITY', id, 'READ', 'NONE' "
                + "FROM custom_field_definition WHERE tenant_id = ? AND object_type = 'OPPORTUNITY'",
                TENANT, roleId, TENANT);
    }

    private long seedCaseSensitiveSelect(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO custom_field_definition "
                + "(tenant_id, object_type, field_code, display_name, field_type, is_builtin, "
                + "is_required, is_unique, is_searchable, status, display_order, options_json) "
                + "VALUES (?, 'OPPORTUNITY', 'case_key', 'Case key', 'SELECT', 0, 0, 0, 1, "
                + "'ACTIVE', 180, JSON_ARRAY(JSON_OBJECT('value', 'A', 'label', 'Upper', "
                + "'order', 0), JSON_OBJECT('value', 'a', 'label', 'Lower', 'order', 1)))",
                TENANT);
        return jdbc.queryForObject("SELECT id FROM custom_field_definition "
                + "WHERE tenant_id = ? AND object_type = 'OPPORTUNITY' "
                + "AND field_code = 'case_key'", Long.class, TENANT);
    }

    private long seedSimpleField(JdbcTemplate jdbc, String code, String type) {
        jdbc.update("INSERT INTO custom_field_definition "
                        + "(tenant_id, object_type, field_code, display_name, field_type, is_builtin, "
                        + "is_required, is_unique, is_searchable, status, display_order, options_json) "
                        + "VALUES (?, 'OPPORTUNITY', ?, ?, ?, 0, 0, 0, 1, 'ACTIVE', 190, JSON_ARRAY())",
                TENANT, code, code, type);
        return jdbc.queryForObject("SELECT id FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'OPPORTUNITY' AND field_code = ?",
                Long.class, TENANT, code);
    }

    private void seedBoundedResultRows(JdbcTemplate jdbc, long fieldId) {
        long customerId = jdbc.queryForObject("SELECT id FROM crm_customer "
                + "WHERE tenant_id = ? AND name = 'B17c customer'", Long.class, TENANT);
        OpportunityPipelineFixture.provisionActivePair(jdbc, TENANT, "direct", "qualification");
        jdbc.update("""
                INSERT INTO crm_opportunity
                    (tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code,
                     estimated_amount, outcome, outcome_time, status)
                WITH RECURSIVE sequence_number(value) AS (
                    SELECT 1
                    UNION ALL
                    SELECT value + 1 FROM sequence_number WHERE value < 501
                )
                SELECT ?, ?, ?, CONCAT('bounded-', value), 'direct', 'qualification',
                       1.00, 'WON', '2026-03-01 10:00:00', 'ACTIVE'
                FROM sequence_number
                """, TENANT, OWNER, customerId);
        jdbc.update("""
                INSERT INTO crm_opportunity_field_value
                    (tenant_id, opportunity_id, field_id, values_json, scalar_value)
                SELECT tenant_id, id, ?, JSON_ARRAY(CAST(id AS CHAR)), CAST(id AS CHAR)
                FROM crm_opportunity
                WHERE tenant_id = ? AND name LIKE 'bounded-%'
                """, fieldId, TENANT);
    }

    private FieldDefinitionResponse createField(String name, String type) throws Exception {
        String json = mockMvc.perform(as(OWNER,
                        post("/api/v1/fields/objects/OPPORTUNITY")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new FieldCreateRequest()
                                        .setDisplayName(name).setFieldType(type)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return fromJson(json, FieldDefinitionResponse.class);
    }

    private void seedOpportunities(JdbcTemplate jdbc, long caseFieldId) {
        jdbc.update("INSERT INTO crm_customer "
                + "(tenant_id, owner_actor_id, name, status, deal_status) "
                + "VALUES (?, ?, 'B17c customer', 'ACTIVE', 'OPEN')", TENANT, OWNER);
        long customerId = jdbc.queryForObject("SELECT id FROM crm_customer "
                + "WHERE tenant_id = ? AND name = 'B17c customer'", Long.class, TENANT);
        opportunity(jdbc, customerId, "won", "WON", "2026-01-01 10:00:00", "10.10", "ACTIVE");
        opportunity(jdbc, customerId, "lost", "LOST", "2026-01-02 10:00:00", "20.20", "ACTIVE");
        opportunity(jdbc, customerId, "invalid", "INVALID", "2026-01-02 11:00:00", "30.30", "ACTIVE");
        opportunity(jdbc, customerId, "deleted", "WON", "2026-01-01 12:00:00", "99.99", "DELETED");
        caseValue(jdbc, caseFieldId, "lost", "A");
        caseValue(jdbc, caseFieldId, "invalid", "a");
    }

    private void caseValue(JdbcTemplate jdbc, long fieldId, String name, String value) {
        long opportunityId = jdbc.queryForObject("SELECT id FROM crm_opportunity "
                + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT, name);
        jdbc.update("INSERT INTO crm_opportunity_field_value "
                        + "(tenant_id, opportunity_id, field_id, values_json, scalar_value) "
                        + "VALUES (?, ?, ?, JSON_ARRAY(?), ?)",
                TENANT, opportunityId, fieldId, value, value);
    }

    private ReportDefinitionCreateRequest definition(String name) {
        return new ReportDefinitionCreateRequest().setName(name)
                .setObjectType("OPPORTUNITY").setTimeFieldCode("outcome_time")
                .setGranularity("DAY")
                .setMeasures(List.of(new ReportMeasureRequest().setType("COUNT")));
    }

    private ReportDefinitionResponse create(
            long actorId, ReportDefinitionCreateRequest request) throws Exception {
        String json = mockMvc.perform(as(actorId, post("/api/v1/reports/definitions")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return fromJson(json, ReportDefinitionResponse.class);
    }

    private ReportDefinitionRunResponse run(
            long actorId, long definitionId, LocalDate from, LocalDate to) throws Exception {
        String json = mockMvc.perform(as(actorId,
                        post("/api/v1/reports/definitions/{id}/run", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ReportDefinitionRunRequest()
                                        .setFrom(from).setTo(to)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return fromJson(json, ReportDefinitionRunResponse.class);
    }

    private void opportunity(
            JdbcTemplate jdbc, long customerId, String name, String outcome,
            String outcomeTime, String amount, String status) {
        OpportunityPipelineFixture.provisionActivePair(jdbc, TENANT, "direct", "qualification");
        jdbc.update("INSERT INTO crm_opportunity "
                + "(tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code, "
                + "estimated_amount, outcome, outcome_time, status) "
                + "VALUES (?, ?, ?, ?, 'direct', 'qualification', ?, ?, ?, ?)",
                TENANT, OWNER, customerId, name, new BigDecimal(amount), outcome, outcomeTime, status);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(
            long actorId,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", TENANT);
    }
}
