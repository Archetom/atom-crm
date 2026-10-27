package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.MarketingActivityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityVersionRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormFieldRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.MarketingActivityResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormResponse;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the tenant-scoped marketing activity lifecycle against the real persistence schema. */
@RequiresDatabase
class MarketingActivityIntegrationTest extends BaseIntegrationTest {

    private static final long ACTOR_ID = 888888L;
    private static final long ACTIVITY_WRITER_WITHOUT_FORM_READ = 888889L;
    private static final long TENANT_ID = 999999L;

    @Autowired
    private AttachmentTargetAccessRegistry targetAccessRegistry;

    @Test
    void marketingActivitiesSupportTypedTenantScopedLifecycle() throws Exception {
        mockMvc.perform(get("/api/v1/marketing-activities")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/marketing-activities/configuration-options"))
                .andExpect(status().isUnauthorized());
        bootstrap("Marketing activity owner");
        performGet("/api/v1/marketing-activities/configuration-options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityTypes.length()").value(6))
                .andExpect(jsonPath("$.activityTypes[0].code").value("ADVERTISING"))
                .andExpect(jsonPath("$.activityTypes[0].displayName").isString())
                .andExpect(jsonPath("$.relatedObjectTypes.length()").value(3))
                .andExpect(jsonPath("$.relatedObjectTypes[0].code").value("CUSTOMER"))
                .andExpect(jsonPath("$.relatedObjectTypes[0].displayName").value("客户"))
                .andExpect(jsonPath("$.relatedObjectTypes[0].supportsMarketingForm").value(true))
                .andExpect(jsonPath("$.relatedObjectTypes[1].code").value("CONTACT"))
                .andExpect(jsonPath("$.relatedObjectTypes[1].supportsMarketingForm").value(false))
                .andExpect(jsonPath("$.defaultRelatedObjectType").value("CUSTOMER"));
        long departmentId = createActiveDepartment();
        MarketingFormResponse activeForm = activateMarketingForm();

        seedActivityWriterWithoutFormRead(departmentId);
        mockMvc.perform(post("/api/v1/marketing-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(createRequest("Denied form association", "CUSTOMER")
                                .setMarketingFormId(activeForm.getId())
                                .setParticipantActorIds(List.of(ACTIVITY_WRITER_WITHOUT_FORM_READ))))
                        .header("X-Dev-User-Id", ACTIVITY_WRITER_WITHOUT_FORM_READ)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());

        performPost("/api/v1/marketing-activities", createRequest("Invalid counter", "CUSTOMER")
                .setViewCount(-1))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/marketing-activities", createRequest("Invalid type", "CUSTOMER")
                .setActivityType("unmanaged-type"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/marketing-activities", createRequest("Mismatched linked form", "CUSTOMER")
                .setMarketingFormId(activeForm.getId()))
                .andExpect(status().isBadRequest());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_marketing_form WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        performPost("/api/v1/marketing-activities", createRequest("Atomic rollback", "LEAD")
                .setEndTime(LocalDateTime.of(2026, 9, 1, 8, 0))
                .setMarketingForm(new MarketingFormCreateRequest().setName("Rollback public form")
                        .setTitle("Rollback public form")
                        .setFields(List.of(new MarketingFormFieldRequest()
                                .setFieldCode("lead_name").setLabel("Name")
                                .setRequired(true).setSortOrder(10)))))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_marketing_form WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_marketing_activity WHERE tenant_id = ?",
                Integer.class, TENANT_ID));

        MarketingActivityCreateRequest atomicRequest = createRequest("Atomic lead event", "LEAD")
                .setMarketingForm(new MarketingFormCreateRequest().setName("Atomic public form")
                        .setTitle("Atomic public form")
                        .setFields(List.of(new MarketingFormFieldRequest()
                                .setFieldCode("lead_name").setLabel("Name")
                                .setRequired(true).setSortOrder(10))));
        MarketingActivityResponse atomicallyCreated = fromJson(performPost(
                "/api/v1/marketing-activities", atomicRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketingFormId").isNumber())
                .andReturn().getResponse().getContentAsString(), MarketingActivityResponse.class);
        assertEquals("INACTIVE", fromJson(performGet("/api/v1/marketing-forms/"
                + atomicallyCreated.getMarketingFormId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), MarketingFormResponse.class).getStatus());

        performPost("/api/v1/marketing-activities", createRequest("Ambiguous form", "LEAD")
                .setMarketingFormId(atomicallyCreated.getMarketingFormId())
                .setMarketingForm(new MarketingFormCreateRequest().setName("Should not persist")
                        .setTitle("Should not persist")
                        .setFields(List.of(new MarketingFormFieldRequest()
                                .setFieldCode("lead_name").setLabel("Name")
                                .setRequired(true).setSortOrder(10)))))
                .andExpect(status().isBadRequest());

        performPost("/api/v1/marketing-activities", createRequest("Invalid customer form", "CUSTOMER")
                .setMarketingForm(new MarketingFormCreateRequest().setName("Should not persist")
                        .setTitle("Should not persist")
                        .setFields(List.of(new MarketingFormFieldRequest()
                                .setFieldCode("lead_name").setLabel("Name")
                                .setRequired(true).setSortOrder(10)))))
                .andExpect(status().isBadRequest());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM crm_marketing_form WHERE tenant_id = ?",
                Integer.class, TENANT_ID));

        MarketingActivityResponse created = fromJson(performPost("/api/v1/marketing-activities",
                createRequest("Autumn customer event", "CUSTOMER")
                        .setMarketingForm(new MarketingFormCreateRequest()
                                .setTargetObjectType("CUSTOMER")
                                .setName("Customer activity form")
                                .setTitle("Customer activity form")
                                .setFields(List.of(new MarketingFormFieldRequest()
                                        .setFieldCode("customer_name").setLabel("Name")
                                        .setRequired(true).setSortOrder(10))))
                        .setParticipantDepartmentIds(List.of(departmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdByActorId").value(ACTOR_ID))
                .andExpect(jsonPath("$.participantActorIds[0]").value(ACTOR_ID))
                .andExpect(jsonPath("$.participantDepartmentIds[0]").value(departmentId))
                .andExpect(jsonPath("$.relatedObjectType").value("CUSTOMER"))
                .andExpect(jsonPath("$.marketingFormId").isNumber())
                .andExpect(jsonPath("$.marketingFormPublicId").doesNotExist())
                .andExpect(jsonPath("$.budget").value(1288.5))
                .andExpect(jsonPath("$.viewCount").value(100))
                .andExpect(jsonPath("$.submissionCount").value(30))
                .andExpect(jsonPath("$.oneSubmissionPerUser").value(false))
                .andReturn().getResponse().getContentAsString(), MarketingActivityResponse.class);
        assertEquals("CUSTOMER", fromJson(performGet("/api/v1/marketing-forms/"
                + created.getMarketingFormId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), MarketingFormResponse.class)
                .getTargetObjectType());

        performGetWithParams("/api/v1/marketing-activities", Map.of(
                "keyword", "Autumn", "relatedObjectType", "CUSTOMER", "page", "1", "size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNum").value(1))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(created.getId()));

        MarketingActivityUpdateRequest update = new MarketingActivityUpdateRequest();
        update.setVersion(created.getVersion());
        update.setName("Autumn lead event");
        update.setRelatedObjectType("LEAD");
        update.setMarketingFormId(activeForm.getId());
        update.setParticipantActorIds(List.of(ACTOR_ID));
        update.setParticipantDepartmentIds(List.of(departmentId));
        update.setActivityType("CONFERENCE");
        update.setStartTime(LocalDateTime.of(2026, 10, 1, 9, 0));
        update.setEndTime(LocalDateTime.of(2026, 10, 1, 18, 0));
        update.setBudget(new BigDecimal("1500.00"));
        update.setAddress("Shanghai");
        update.setDescription("Qualified lead event");
        update.setViewCount(180);
        update.setSubmissionCount(50);
        update.setOneSubmissionPerUser(true);

        update.setMarketingForm(new MarketingFormCreateRequest().setName("Update public form")
                .setTitle("Update public form")
                .setFields(List.of(new MarketingFormFieldRequest()
                        .setFieldCode("lead_name").setLabel("Name")
                        .setRequired(true).setSortOrder(10))));
        performPut("/api/v1/marketing-activities/" + created.getId(), update)
                .andExpect(status().isBadRequest());
        update.setMarketingForm(null);

        MarketingActivityResponse updated = fromJson(performPut(
                "/api/v1/marketing-activities/" + created.getId(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Autumn lead event"))
                .andExpect(jsonPath("$.relatedObjectType").value("LEAD"))
                .andExpect(jsonPath("$.viewCount").value(180))
                .andExpect(jsonPath("$.submissionCount").value(50))
                .andExpect(jsonPath("$.marketingFormId").value(activeForm.getId()))
                .andExpect(jsonPath("$.marketingFormPublicId").value(activeForm.getPublicId()))
                .andExpect(jsonPath("$.participantDepartmentIds[0]").value(departmentId))
                .andExpect(jsonPath("$.oneSubmissionPerUser").value(true))
                .andReturn().getResponse().getContentAsString(), MarketingActivityResponse.class);

        performPut("/api/v1/marketing-activities/" + created.getId(), update)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errCode").value(
                        ApplicationErrorCode.VERSION_CONFLICT.getCompleteCode("6919")));

        jdbc.update("UPDATE crm_marketing_activity SET activity_type = ? WHERE tenant_id = ? AND id = ?",
                "Legacy roadshow", TENANT_ID, created.getId());
        update.setVersion(updated.getVersion());
        update.setActivityType("Legacy roadshow");
        MarketingActivityResponse legacyUpdated = fromJson(performPut(
                "/api/v1/marketing-activities/" + created.getId(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityType").value("Legacy roadshow"))
                .andReturn().getResponse().getContentAsString(), MarketingActivityResponse.class);
        update.setVersion(legacyUpdated.getVersion());
        update.setActivityType("unmanaged-type");
        performPut("/api/v1/marketing-activities/" + created.getId(), update)
                .andExpect(status().isBadRequest());

        performGet("/api/v1/marketing-activities/" + created.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(legacyUpdated.getVersion()))
                .andExpect(jsonPath("$.viewCount").value(180))
                .andExpect(jsonPath("$.participantActorIds.length()").value(1))
                .andExpect(jsonPath("$.participantDepartmentIds.length()").value(1))
                .andExpect(jsonPath("$.marketingFormPublicId").value(activeForm.getPublicId()));
        MarketingFormResponse inactiveForm = fromJson(performPut(
                "/api/v1/marketing-forms/" + activeForm.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(activeForm.getVersion())
                        .setStatus("INACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        assertEquals("INACTIVE", inactiveForm.getStatus());
        performGet("/api/v1/marketing-activities/" + created.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketingFormId").value(activeForm.getId()))
                .andExpect(jsonPath("$.marketingFormPublicId").doesNotExist());
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_activity_participant "
                        + "WHERE tenant_id = ? AND marketing_activity_id = ? AND actor_id = ?",
                Integer.class, TENANT_ID, created.getId(), ACTOR_ID));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '92' AND success = 1", Integer.class));
        long attachmentFileId = attachmentFile(jdbc);
        jdbc.update("INSERT INTO crm_attachment_relation "
                        + "(tenant_id, file_id, target_type, marketing_activity_id) VALUES (?, ?, ?, ?)",
                TENANT_ID, attachmentFileId, AttachmentTargetType.MARKETING_ACTIVITY.name(), created.getId());
        assertTrue(targetAccessRegistry.require(AttachmentTargetType.MARKETING_ACTIVITY).lockForWorker(
                new TenantId(TENANT_ID), new AttachmentTarget(AttachmentTargetType.MARKETING_ACTIVITY,
                        created.getId())).attachmentsPermitted());
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO crm_attachment_relation "
                        + "(tenant_id, file_id, target_type, marketing_activity_id) VALUES (?, ?, ?, ?)",
                TENANT_ID, attachmentFile(jdbc), AttachmentTargetType.PRODUCT.name(), created.getId()));

        performPost("/api/v1/marketing-activities/batch-delete",
                batchDeleteRequest(created.getId(), legacyUpdated.getVersion(), atomicallyCreated.getId(),
                        atomicallyCreated.getVersion() + 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errCode").value(
                        ApplicationErrorCode.VERSION_CONFLICT.getCompleteCode("6922")));
        performGet("/api/v1/marketing-activities/" + created.getId()).andExpect(status().isOk());
        assertTrue(targetAccessRegistry.require(AttachmentTargetType.MARKETING_ACTIVITY).lockForWorker(
                new TenantId(TENANT_ID), new AttachmentTarget(AttachmentTargetType.MARKETING_ACTIVITY,
                        created.getId())).attachmentsPermitted());

        performPost("/api/v1/marketing-activities/batch-delete",
                batchDeleteRequest(created.getId(), legacyUpdated.getVersion(), atomicallyCreated.getId(),
                        atomicallyCreated.getVersion()))
                .andExpect(status().isOk());
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT relation_state FROM crm_attachment_relation"
                        + " WHERE tenant_id = ? AND file_id = ?", String.class, TENANT_ID, attachmentFileId));
        assertFalse(targetAccessRegistry.require(AttachmentTargetType.MARKETING_ACTIVITY).lockForWorker(
                new TenantId(TENANT_ID), new AttachmentTarget(AttachmentTargetType.MARKETING_ACTIVITY,
                        created.getId())).attachmentsPermitted());
        performGet("/api/v1/marketing-activities/" + created.getId())
                .andExpect(status().isNotFound());
        performGet("/api/v1/marketing-activities/" + atomicallyCreated.getId())
                .andExpect(status().isNotFound());
        performGetWithParams("/api/v1/marketing-activities", Map.of("page", "1", "size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
    }

    private MarketingActivityBatchDeleteRequest batchDeleteRequest(
            long firstId, long firstVersion, long secondId, long secondVersion) {
        return new MarketingActivityBatchDeleteRequest().setActivities(List.of(
                new MarketingActivityVersionRequest().setActivityId(firstId).setVersion(firstVersion),
                new MarketingActivityVersionRequest().setActivityId(secondId).setVersion(secondVersion)));
    }

    private MarketingActivityCreateRequest createRequest(String name, String relatedObjectType) {
        return new MarketingActivityCreateRequest()
                .setName(name)
                .setRelatedObjectType(relatedObjectType)
                .setParticipantActorIds(List.of(ACTOR_ID))
                .setParticipantDepartmentIds(List.of())
                .setActivityType("SEMINAR")
                .setStartTime(LocalDateTime.of(2026, 9, 1, 9, 0))
                .setEndTime(LocalDateTime.of(2026, 9, 1, 17, 0))
                .setBudget(new BigDecimal("1288.50"))
                .setAddress("Beijing")
                .setDescription("Customer event")
                .setViewCount(100)
                .setSubmissionCount(30)
                .setOneSubmissionPerUser(false);
    }

    private long attachmentFile(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO crm_attachment_file (tenant_id, uploader_actor_id, display_name, declared_media_type,"
                        + " expected_bytes, byte_state, inspection_state, publication_state)"
                        + " VALUES (?, ?, 'marketing-activity.pdf', 'application/pdf', 4,"
                        + " 'UPLOADING', 'NOT_RUN', 'PRIVATE')", TENANT_ID, ACTOR_ID);
        return jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?", Long.class,
                TENANT_ID);
    }

    private MarketingFormResponse activateMarketingForm() throws Exception {
        MarketingFormResponse created = fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest().setName("Activity public form")
                        .setTitle("Activity public form")
                        .setFields(List.of(new MarketingFormFieldRequest()
                                .setFieldCode("lead_name").setLabel("Name")
                                .setRequired(true).setSortOrder(10))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        return fromJson(performPut("/api/v1/marketing-forms/" + created.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(created.getVersion()).setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
    }

    private void bootstrap(String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName)))
                        .header("X-Dev-User-Id", ACTOR_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());
    }

    private void seedActivityWriterWithoutFormRead(long departmentId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                ACTIVITY_WRITER_WITHOUT_FORM_READ, "Activity-only writer");
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, department_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, ACTIVITY_WRITER_WITHOUT_FORM_READ, departmentId);
        jdbc.update("DELETE FROM authorization_role_assignment WHERE tenant_id = ? AND actor_id = ?",
                TENANT_ID, ACTIVITY_WRITER_WITHOUT_FORM_READ);
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Activity-only writer");
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Activity-only writer");
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) VALUES (?, ?, ?, ?)",
                TENANT_ID, roleId, "marketing-activities:write", null);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, ACTIVITY_WRITER_WITHOUT_FORM_READ, roleId);
    }

    @Override
    protected void initTestData() {
        clearActivities();
    }

    @Override
    protected void clearTestData() {
        clearActivities();
    }

    private void clearActivities() {
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + TENANT_ID
                + " AND actor_id = " + ACTIVITY_WRITER_WITHOUT_FORM_READ);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + TENANT_ID
                + " AND role_id IN (SELECT id FROM authorization_role WHERE tenant_id = " + TENANT_ID
                + " AND name = 'Activity-only writer')");
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + TENANT_ID
                + " AND name = 'Activity-only writer'");
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + TENANT_ID
                + " AND actor_id = " + ACTIVITY_WRITER_WITHOUT_FORM_READ);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = "
                + ACTIVITY_WRITER_WITHOUT_FORM_READ);
        executeSql("DELETE FROM crm_marketing_activity_participant_department");
        executeSql("DELETE FROM crm_marketing_activity_participant");
        executeSql("DELETE FROM crm_marketing_activity");
        executeSql("DELETE FROM crm_marketing_form_field WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_marketing_form WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_department WHERE tenant_id = " + TENANT_ID);
    }

    private long createActiveDepartment() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_department (tenant_id, parent_department_id, name, status) "
                + "VALUES (?, 0, ?, 'ACTIVE')", TENANT_ID, "Marketing team");
        return jdbc.queryForObject("SELECT id FROM organization_department "
                + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT_ID, "Marketing team");
    }
}
