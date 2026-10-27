package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormFieldRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionTrendRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.application.security.MarketingFileDraftCredential;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B19a form configuration and anonymous B09 lead-creation coverage against MySQL. */
@RequiresDatabase
class MarketingFormIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;
    private static final long OTHER_ACTOR_ID = 777777L;
    private static final long OTHER_TENANT_ID = 777001L;

    @Test
    void configurationOptionsExposeOnlyServerAuthorizedPublicLeadFields() throws Exception {
        bootstrap("Marketing catalog owner");
        FieldDefinitionResponse campaign = fromJson(performPost(
                "/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Campaign").setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);

        MarketingFormConfigurationOptionsResponse createOptions = fromJson(performGet(
                "/api/v1/marketing-forms/configuration-options")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormConfigurationOptionsResponse.class);
        assertTrue(createOptions.fields().stream().anyMatch(field ->
                field.fieldCode().equals(campaign.getCode())
                        && field.displayName().equals("Campaign")
                        && field.fieldType().equals("TEXT")));
        assertTrue(createOptions.fields().stream().anyMatch(field ->
                field.fieldCode().equals("lead_name") && field.required()));

        MarketingFormResponse created = fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest().setName("Catalog form").setTitle("Catalog form")
                        .setFields(List.of(field("lead_name", "Name", true, 10),
                                field(campaign.getCode(), "Campaign", false, 20))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        MarketingFormConfigurationOptionsResponse editOptions = fromJson(performGetWithParams(
                "/api/v1/marketing-forms/configuration-options",
                Map.of("formId", created.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormConfigurationOptionsResponse.class);
        assertTrue(editOptions.fields().stream()
                .anyMatch(field -> field.fieldCode().equals(campaign.getCode())));
    }

    @Test
    void activePublicFormRendersConfiguredFieldsAndCreatesLeadThroughB09() throws Exception {
        mockMvc.perform(get("/api/v1/marketing-forms")).andExpect(status().isUnauthorized());
        bootstrap("Marketing owner");
        FieldDefinitionResponse campaign = fromJson(performPost(
                "/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Campaign").setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);

        MarketingFormResponse created = fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest()
                        .setName("Website contact form")
                        .setTitle("Talk to sales")
                        .setDescription("Leave your details")
                        .setSubmitButtonText("Send")
                        .setSuccessMessage("We will contact you")
                        .setFields(List.of(
                                field("lead_name", "Name", true, 10),
                                field("email", "Email", false, 20),
                                field(campaign.getCode(), "Campaign", true, 30))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        assertEquals("INACTIVE", created.getStatus());
        assertTrue(created.getPublicId().matches(
                "[A-Za-z0-9_-]{11}\\.[A-Za-z0-9_-]{24}"));

        mockMvc.perform(get("/api/v1/open/marketing-forms/{publicId}", created.getPublicId()))
                .andExpect(status().isNotFound());

        MarketingFormResponse active = fromJson(performPut(
                "/api/v1/marketing-forms/" + created.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(created.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        assertEquals("ACTIVE", active.getStatus());

        mockMvc.perform(get("/api/v1/open/marketing-forms/{publicId}", active.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Talk to sales"))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields[0].fieldType").value("TEXT"))
                .andExpect(jsonPath("$.ownerActorId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.successMessage").value("We will contact you"));

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}/submissions",
                        active.getPublicId()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionRequest()
                                .setIdempotencyKey("public-website-lead")
                                .setValues(Map.of(
                                "lead_name", List.of("Public website lead"),
                                "email", List.of("lead@example.com"),
                                campaign.getCode(), List.of("summer"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.idempotencyKey").value("public-website-lead"))
                .andExpect(jsonPath("$.submissionId").isNumber())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ownerActorId").doesNotExist());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long leadId = jdbc.queryForObject("SELECT id FROM crm_lead "
                        + "WHERE tenant_id = ? AND owner_actor_id = ? AND name = ?",
                Long.class, TENANT_ID, OWNER_ACTOR_ID, "Public website lead");
        assertEquals("summer", jdbc.queryForObject(
                "SELECT scalar_value FROM crm_lead_field_value "
                        + "WHERE tenant_id = ? AND lead_id = ? AND field_id = ?",
                String.class, TENANT_ID, leadId, campaign.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND marketing_form_id = ? "
                        + "AND form_version = ? AND lead_id = ?",
                Integer.class, TENANT_ID, active.getId(), active.getVersion(), leadId));
        assertTrue(jdbc.queryForObject(
                "SELECT ABS(TIMESTAMPDIFF(SECOND, submitted_time, UTC_TIMESTAMP(6))) "
                        + "FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND marketing_form_id = ? AND lead_id = ?",
                Integer.class, TENANT_ID, active.getId(), leadId) <= 30,
                "The ledger timestamp must use database UTC time");

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionRequest()
                                .setIdempotencyKey("rejected-lead")
                                .setValues(Map.of(
                                "lead_name", List.of("Rejected lead"),
                                "owner_actor", List.of("1"))))))
                .andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ?", Integer.class, TENANT_ID));

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionRequest()
                                .setIdempotencyKey("rate-limited-lead")
                                .setValues(Map.of(
                                "lead_name", List.of("Rate-limited lead"),
                                campaign.getCode(), List.of("autumn"))))))
                .andExpect(status().isTooManyRequests());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ?", Integer.class, TENANT_ID));

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/fields/objects/LEAD/{fieldId}", campaign.getId())
                        .param("version", campaign.getVersion().toString()))
                .andExpect(status().is(422));

        MarketingFormResponse inactive = fromJson(performPut(
                "/api/v1/marketing-forms/" + active.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(active.getVersion())
                        .setStatus("INACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        mockMvc.perform(get("/api/v1/open/marketing-forms/{publicId}", inactive.getPublicId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void activeCustomerFormCreatesAndAttributesCustomerRecords() throws Exception {
        bootstrap("Customer form owner");
        FieldDefinitionResponse campaign = fromJson(performPost(
                "/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName("Campaign").setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);

        MarketingFormConfigurationOptionsResponse options = fromJson(performGetWithParams(
                "/api/v1/marketing-forms/configuration-options",
                Map.of("targetObjectType", "CUSTOMER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormConfigurationOptionsResponse.class);
        assertTrue(options.fields().stream().anyMatch(field ->
                field.fieldCode().equals("customer_name") && field.required()));
        assertTrue(options.fields().stream().anyMatch(field ->
                field.fieldCode().equals(campaign.getCode()) && field.displayName().equals("Campaign")));

        MarketingFormResponse created = fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest().setTargetObjectType("CUSTOMER")
                        .setName("Customer campaign form").setTitle("Talk to sales")
                        .setFields(List.of(
                                field("customer_name", "Name", true, 10),
                                field("email", "Email", false, 20),
                                field(campaign.getCode(), "Campaign", true, 30))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        assertEquals("CUSTOMER", created.getTargetObjectType());

        MarketingFormResponse active = fromJson(performPut(
                "/api/v1/marketing-forms/" + created.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(created.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}/submissions",
                        active.getPublicId()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionRequest()
                                .setIdempotencyKey("public-customer")
                                .setValues(Map.of(
                                        "customer_name", List.of("Public customer"),
                                        "email", List.of("customer@example.com"),
                                        campaign.getCode(), List.of("summer"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long customerId = jdbc.queryForObject("SELECT id FROM crm_customer "
                        + "WHERE tenant_id = ? AND owner_actor_id = ? AND name = ?",
                Long.class, TENANT_ID, OWNER_ACTOR_ID, "Public customer");
        assertEquals("summer", jdbc.queryForObject(
                "SELECT scalar_value FROM crm_customer_field_value "
                        + "WHERE tenant_id = ? AND customer_id = ? AND field_id = ?",
                String.class, TENANT_ID, customerId, campaign.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND marketing_form_id = ? "
                        + "AND target_type = 'CUSTOMER' AND customer_id = ? AND lead_id IS NULL",
                Integer.class, TENANT_ID, active.getId(), customerId));

        performPost("/api/v1/marketing-forms/" + active.getId() + "/submissions/query",
                new MarketingFormSubmissionQueryRequest().setPage(1).setSize(20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].targetObjectType").value("CUSTOMER"))
                .andExpect(jsonPath("$.objectList[0].targetRecordId").value(customerId));
    }

    @Test
    void b09FailureIsReportedAsMarketingFormSubmission() throws Exception {
        bootstrap("Unique-field owner");
        FieldDefinitionResponse uniqueCampaign = fromJson(performPost(
                "/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Unique campaign")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        MarketingFormResponse created = fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest().setName("Unique campaign form")
                        .setTitle("Unique campaign form")
                        .setFields(List.of(
                                field("lead_name", "Name", true, 10),
                                field(uniqueCampaign.getCode(), "Campaign", true, 20))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        MarketingFormResponse active = fromJson(performPut(
                "/api/v1/marketing-forms/" + created.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(created.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);

        MarketingFormSubmissionRequest first = new MarketingFormSubmissionRequest()
                .setIdempotencyKey("unique-campaign-first").setValues(
                        Map.of("lead_name", List.of("First campaign lead"),
                                uniqueCampaign.getCode(), List.of("same-campaign")));
        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(first)))
                .andExpect(status().isOk());

        MarketingFormSubmissionRequest duplicate = new MarketingFormSubmissionRequest()
                .setIdempotencyKey("unique-campaign-duplicate").setValues(
                        Map.of("lead_name", List.of("Duplicate campaign lead"),
                                uniqueCampaign.getCode(), List.of("same-campaign")));
        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}/submissions",
                        active.getPublicId()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errCode").value(
                        ApplicationErrorCode.RESOURCE_ALREADY_EXISTS
                                .getCompleteCode("6907")));

        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_form_submission WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
    }

    @Test
    void publicSubmissionRetriesUseTheSameIdempotencyKeyWithoutCreatingAnotherLead()
            throws Exception {
        bootstrap("Idempotent public form owner");
        MarketingFormResponse active = activate(createForm(
                "Idempotent public form", "Idempotent public form",
                List.of(field("lead_name", "Name", true, 10))));
        MarketingFormSubmissionRequest request = new MarketingFormSubmissionRequest()
                .setIdempotencyKey("public-retry-1")
                .setValues(Map.of("lead_name", List.of("Idempotent public lead")));

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.idempotencyKey").value("public-retry-1"))
                .andExpect(jsonPath("$.submissionId").isNumber());
        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}/submissions",
                        active.getPublicId()).contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.idempotencyKey").value("public-retry-1"));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ?", Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, TENANT_ID, "public-retry-1"));
    }

    @Test
    void anonymousReadyFileCandidatesBindToTheNewLeadAndConsumeTheirDraftAtomically()
            throws Exception {
        bootstrap("Public file owner");
        FieldDefinitionResponse resume = fromJson(performPost("/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Resume").setFieldType("FILE")
                        .setRequired(true).setSearchable(false))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        MarketingFormResponse active = activate(fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest().setName("Public file form").setTitle("Apply")
                        .setFields(List.of(field("lead_name", "Name", true, 10),
                                field(resume.getCode(), "Resume", true, 20))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class));

        MarketingFileDraftCredential.Issued credential = MarketingFileDraftCredential.issue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO crm_marketing_file_draft
                    (tenant_id, marketing_form_id, form_version, credential_digest, deadline, state)
                VALUES (?, ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 MINUTE), 'ACTIVE')
                """, TENANT_ID, active.getId(), active.getVersion(), credential.digest());
        long draftId = jdbc.queryForObject("SELECT MAX(id) FROM crm_marketing_file_draft WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        jdbc.update("""
                INSERT INTO crm_attachment_file
                    (tenant_id, upload_origin, uploader_actor_id, public_marketing_draft_id,
                     display_name, declared_media_type, expected_bytes, byte_state, inspection_state,
                     publication_state, actual_bytes, media_type)
                VALUES (?, 'PUBLIC_MARKETING_FORM', NULL, ?, 'resume.pdf', 'application/pdf', 4,
                        'STORED', 'CLEAN', 'PRIVATE', 4, 'application/pdf')
                """, TENANT_ID, draftId);
        long fileId = jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?",
                Long.class, TENANT_ID);

        MarketingFileDraftCredential.Issued otherCredential = MarketingFileDraftCredential.issue();
        jdbc.update("""
                INSERT INTO crm_marketing_file_draft
                    (tenant_id, marketing_form_id, form_version, credential_digest, deadline, state)
                VALUES (?, ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 MINUTE), 'ACTIVE')
                """, TENANT_ID, active.getId(), active.getVersion(), otherCredential.digest());
        long otherDraftId = jdbc.queryForObject("SELECT MAX(id) FROM crm_marketing_file_draft WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        jdbc.update("""
                INSERT INTO crm_attachment_file
                    (tenant_id, upload_origin, uploader_actor_id, public_marketing_draft_id,
                     display_name, declared_media_type, expected_bytes, byte_state, inspection_state,
                     publication_state)
                VALUES (?, 'PUBLIC_MARKETING_FORM', NULL, ?, 'other.pdf', 'application/pdf', 1,
                        'UPLOADING', 'NOT_RUN', 'PRIVATE')
                """, TENANT_ID, draftId);
        long foreignOriginFileId = jdbc.queryForObject(
                "SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?", Long.class, TENANT_ID);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO crm_marketing_file_draft_candidate
                    (tenant_id, draft_id, object_type, field_id, file_id, ordinal, state)
                VALUES (?, ?, 'LEAD', ?, ?, 0, 'STAGED')
                """, TENANT_ID, otherDraftId, resume.getId(), foreignOriginFileId));

        jdbc.update("""
                INSERT INTO crm_attachment_content
                    (tenant_id, file_id, content_version, intent_version, binding_ref, planned_locator,
                     stored_identity, storage_generation, actual_bytes, sha256, upload_deadline, stored_time)
                VALUES (?, ?, 1, 0, X'01', X'01', X'01', X'01', 4, UNHEX(REPEAT('11', 32)),
                        DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 MINUTE), UTC_TIMESTAMP(6))
                """, TENANT_ID, fileId);
        jdbc.update("""
                INSERT INTO crm_marketing_file_draft_candidate
                    (tenant_id, draft_id, object_type, field_id, file_id, ordinal, state)
                VALUES (?, ?, 'LEAD', ?, ?, 0, 'STAGED')
                """, TENANT_ID, draftId, resume.getId(), fileId);

        MarketingFormSubmissionRequest request = new MarketingFormSubmissionRequest()
                .setIdempotencyKey("file-backed-submit")
                .setValues(Map.of("lead_name", List.of("File-backed public lead")))
                .setFileDraftId(draftId)
                .setFileFields(Map.of(resume.getCode(), List.of(fileId)));
        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .header(MarketingFileDraftCredential.HEADER_NAME, credential.secret())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));

        long leadId = jdbc.queryForObject("SELECT id FROM crm_lead WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "File-backed public lead");
        assertEquals("CONSUMED", jdbc.queryForObject("SELECT state FROM crm_marketing_file_draft WHERE id = ?",
                String.class, draftId));
        assertEquals("BOUND", jdbc.queryForObject("SELECT state FROM crm_marketing_file_draft_candidate "
                        + "WHERE tenant_id = ? AND draft_id = ? AND file_id = ?",
                String.class, TENANT_ID, draftId, fileId));
        assertEquals("PUBLIC_MARKETING_FORM", jdbc.queryForObject("SELECT upload_origin FROM crm_attachment_file "
                        + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, fileId));
        assertEquals("AVAILABLE", jdbc.queryForObject("SELECT publication_state FROM crm_attachment_file "
                        + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, fileId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_field_binding "
                        + "WHERE tenant_id = ? AND lead_id = ? AND field_id = ? AND file_id = ? AND state = 'ACTIVE'",
                Integer.class, TENANT_ID, leadId, resume.getId(), fileId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND marketing_form_id = ? AND lead_id = ?",
                Integer.class, TENANT_ID, active.getId(), leadId));

        request.setIdempotencyKey("file-backed-replay");
        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .header(MarketingFileDraftCredential.HEADER_NAME, credential.secret())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
    }

    @Test
    void submissionLedgerFailureRollsBackTheCreatedLeadInTheSameTransaction()
            throws Exception {
        bootstrap("Atomic attribution owner");
        MarketingFormResponse active = activate(createForm(
                "Atomic attribution form", "Atomic attribution form",
                List.of(field("lead_name", "Name", true, 10))));
        executeSql("RENAME TABLE crm_marketing_form_submission "
                + "TO crm_marketing_form_submission_unavailable");
        try {
            mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}",
                            active.getPublicId()).contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new MarketingFormSubmissionRequest()
                                    .setIdempotencyKey("rolled-back-lead")
                                    .setValues(
                                    Map.of("lead_name", List.of("Rolled back lead"))))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errCode").value(
                            ApplicationErrorCode.UNKNOWN.getCompleteCode("6907")));
        } finally {
            executeSql("RENAME TABLE crm_marketing_form_submission_unavailable "
                    + "TO crm_marketing_form_submission");
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name = ?",
                Integer.class, TENANT_ID, "Rolled back lead"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_form_submission WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
    }

    @Test
    void trendsKeepHistoricalSuccessWhileLinksUseCurrentLeadVisibilityAndLifecycle()
            throws Exception {
        bootstrap("Submission history owner");
        MarketingFormResponse active = activate(createForm(
                "Submission history form", "Submission history form",
                List.of(field("lead_name", "Name", true, 10))));
        submit(active, "First attributed lead");
        submit(active, "Second attributed lead");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Long> submissionIds = jdbc.queryForList(
                "SELECT id FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND marketing_form_id = ? ORDER BY id",
                Long.class, TENANT_ID, active.getId());
        List<Long> leadIds = jdbc.queryForList(
                "SELECT lead_id FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND marketing_form_id = ? ORDER BY id",
                Long.class, TENANT_ID, active.getId());
        assertEquals(2, submissionIds.size());
        jdbc.update("UPDATE crm_marketing_form_submission SET submitted_time = ? "
                        + "WHERE tenant_id = ? AND id = ?",
                "2026-07-18 08:30:00.000000", TENANT_ID, submissionIds.get(0));
        jdbc.update("UPDATE crm_marketing_form_submission SET submitted_time = ? "
                        + "WHERE tenant_id = ? AND id = ?",
                "2026-07-20 09:15:00.000000", TENANT_ID, submissionIds.get(1));

        performPost("/api/v1/marketing-forms/" + active.getId()
                        + "/submissions/trends",
                new MarketingFormSubmissionTrendRequest()
                        .setFrom(LocalDate.of(2026, 7, 17))
                        .setTo(LocalDate.of(2026, 7, 20))
                        .setGranularity("DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formId").value(active.getId()))
                .andExpect(jsonPath("$.points.length()").value(4))
                .andExpect(jsonPath("$.points[0].periodStart").value("2026-07-17"))
                .andExpect(jsonPath("$.points[0].acceptedCount").value(0))
                .andExpect(jsonPath("$.points[1].acceptedCount").value(1))
                .andExpect(jsonPath("$.points[2].acceptedCount").value(0))
                .andExpect(jsonPath("$.points[3].acceptedCount").value(1));
        performPost("/api/v1/marketing-forms/" + active.getId()
                        + "/submissions/trends",
                new MarketingFormSubmissionTrendRequest()
                        .setFrom(LocalDate.of(2026, 7, 13))
                        .setTo(LocalDate.of(2026, 7, 26))
                        .setGranularity("WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].periodStart").value("2026-07-13"))
                .andExpect(jsonPath("$.points[0].acceptedCount").value(1))
                .andExpect(jsonPath("$.points[1].periodStart").value("2026-07-20"))
                .andExpect(jsonPath("$.points[1].acceptedCount").value(1));
        performPost("/api/v1/marketing-forms/" + active.getId()
                        + "/submissions/trends",
                new MarketingFormSubmissionTrendRequest()
                        .setFrom(LocalDate.of(2026, 1, 1))
                        .setTo(LocalDate.of(2026, 5, 1))
                        .setGranularity("DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errCode").value(
                        ApplicationErrorCode.PARAMETER_INVALID.getCompleteCode("6908")));

        MarketingFormSubmissionQueryRequest firstPage =
                new MarketingFormSubmissionQueryRequest()
                        .setPage(1).setSize(1)
                        .setFrom(LocalDate.of(2026, 7, 18))
                        .setTo(LocalDate.of(2026, 7, 20));
        performPost("/api/v1/marketing-forms/" + active.getId()
                        + "/submissions/query", firstPage)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(2))
                .andExpect(jsonPath("$.objectList.length()").value(1))
                .andExpect(jsonPath("$.objectList[0].submissionId")
                        .value(submissionIds.get(1)))
                .andExpect(jsonPath("$.objectList[0].targetObjectType").value("LEAD"))
                .andExpect(jsonPath("$.objectList[0].targetRecordId").value(leadIds.get(1)))
                .andExpect(jsonPath("$.objectList[0].formVersion")
                        .value(active.getVersion()))
                .andExpect(jsonPath("$.objectList[0].name").doesNotExist());

        jdbc.update("UPDATE crm_lead SET status = 'DELETED', owner_actor_id = NULL "
                + "WHERE tenant_id = ? AND id = ?", TENANT_ID, leadIds.get(1));
        performPost("/api/v1/marketing-forms/" + active.getId()
                        + "/submissions/query",
                new MarketingFormSubmissionQueryRequest().setPage(1).setSize(100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].targetRecordId").value(leadIds.get(0)));
        performPost("/api/v1/marketing-forms/" + active.getId()
                        + "/submissions/trends",
                new MarketingFormSubmissionTrendRequest()
                        .setFrom(LocalDate.of(2026, 7, 17))
                        .setTo(LocalDate.of(2026, 7, 20))
                        .setGranularity("MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].acceptedCount").value(2));

        MarketingFormResponse inactive = fromJson(performPut(
                "/api/v1/marketing-forms/" + active.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(active.getVersion())
                        .setStatus("INACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        performDelete("/api/v1/marketing-forms/" + active.getId()
                        + "?version=" + inactive.getVersion())
                .andExpect(status().isOk());
        performPost("/api/v1/marketing-forms/" + active.getId()
                        + "/submissions/trends",
                new MarketingFormSubmissionTrendRequest()
                        .setFrom(LocalDate.of(2026, 7, 1))
                        .setTo(LocalDate.of(2026, 7, 31))
                        .setGranularity("MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].acceptedCount").value(2));

        bootstrap(OTHER_ACTOR_ID, OTHER_TENANT_ID, "Other history tenant");
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                post("/api/v1/marketing-forms/{formId}/submissions/trends", active.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionTrendRequest()
                                .setFrom(LocalDate.of(2026, 7, 1))
                                .setTo(LocalDate.of(2026, 7, 31))
                                .setGranularity("MONTH"))))
                .andExpect(status().isNotFound());
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                post("/api/v1/marketing-forms/{formId}/submissions/query", active.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionQueryRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void submissionLinksApplyEveryCurrentLeadScopeAndCollaborationInSql()
            throws Exception {
        bootstrap("Scoped submission owner");
        MarketingFormResponse active = activate(createForm(
                "Scoped submission form", "Scoped submission form",
                List.of(field("lead_name", "Name", true, 10))));
        submit(active, "Self-owned attribution lead");
        submit(active, "Department-owned attribution lead");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Long> leadIds = jdbc.queryForList(
                "SELECT lead_id FROM crm_marketing_form_submission "
                        + "WHERE tenant_id = ? AND marketing_form_id = ? ORDER BY id",
                Long.class, TENANT_ID, active.getId());
        long parentDepartment = insertDepartment(jdbc, 0L, "Submission parent");
        long childDepartment = insertDepartment(
                jdbc, parentDepartment, "Submission child");
        jdbc.update("UPDATE organization_membership SET department_id = ? "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                childDepartment, TENANT_ID, OWNER_ACTOR_ID);

        long noLeadReader = 700001L;
        long selfReader = 700002L;
        long departmentReader = 700003L;
        long parentReader = 700004L;
        long subtreeReader = 700005L;
        long tenantReader = 700006L;
        seedActiveMember(noLeadReader, "Form-only reader", null);
        seedActiveMember(selfReader, "Self reader", null);
        seedActiveMember(departmentReader, "Department reader", childDepartment);
        seedActiveMember(parentReader, "Parent reader", parentDepartment);
        seedActiveMember(subtreeReader, "Subtree reader", parentDepartment);
        seedActiveMember(tenantReader, "Tenant reader", null);

        assignRole(createRole("Form-only submission reader",
                grant("marketing-forms:read", null)).getId(), noLeadReader);
        assignRole(createRole("Self submission reader",
                grant("marketing-forms:read", null),
                grant("leads:read", "SELF")).getId(), selfReader);
        assignRole(createRole("Department submission reader",
                grant("marketing-forms:read", null),
                grant("leads:read", "DEPARTMENT")).getId(), departmentReader);
        assignRole(createRole("Parent department submission reader",
                grant("marketing-forms:read", null),
                grant("leads:read", "DEPARTMENT")).getId(), parentReader);
        assignRole(createRole("Subtree submission reader",
                grant("marketing-forms:read", null),
                grant("leads:read", "DEPARTMENT_SUBTREE")).getId(), subtreeReader);
        assignRole(createRole("Tenant submission reader",
                grant("marketing-forms:read", null),
                grant("leads:read", "TENANT")).getId(), tenantReader);

        performAs(noLeadReader, TENANT_ID,
                post("/api/v1/marketing-forms/{formId}/submissions/trends", active.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionTrendRequest()
                                .setFrom(LocalDate.now(Clock.systemUTC()))
                                .setTo(LocalDate.now(Clock.systemUTC()))
                                .setGranularity("DAY"))))
                .andExpect(status().isOk());
        performSubmissionQuery(noLeadReader, active.getId())
                .andExpect(status().isForbidden());

        jdbc.update("UPDATE crm_lead SET owner_actor_id = ? "
                        + "WHERE tenant_id = ? AND id = ?",
                selfReader, TENANT_ID, leadIds.get(0));
        assertSubmissionTotal(selfReader, active.getId(), 1);
        assertSubmissionTotal(departmentReader, active.getId(), 1);
        assertSubmissionTotal(parentReader, active.getId(), 0);
        assertSubmissionTotal(subtreeReader, active.getId(), 1);
        assertSubmissionTotal(tenantReader, active.getId(), 2);

        jdbc.update("INSERT INTO crm_lead_collaborator "
                        + "(tenant_id, lead_id, actor_id, access_level) VALUES (?, ?, ?, 'READ')",
                TENANT_ID, leadIds.get(0), parentReader);
        assertSubmissionTotal(parentReader, active.getId(), 1);

        jdbc.update("UPDATE organization_membership SET status = 'SUSPENDED' "
                + "WHERE tenant_id = ? AND actor_id = ?", TENANT_ID, tenantReader);
        performSubmissionQuery(tenantReader, active.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void activationRequiresEveryRequiredLeadField() throws Exception {
        bootstrap("Required-field owner");
        performPost("/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Required campaign")
                        .setFieldType("TEXT").setRequired(true))
                .andExpect(status().isOk());
        MarketingFormResponse form = fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest().setName("Incomplete")
                        .setTitle("Incomplete form")
                        .setFields(List.of(field("lead_name", "Name", true, 10))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);

        performPut("/api/v1/marketing-forms/" + form.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(form.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().is(422));
        assertEquals("INACTIVE", new JdbcTemplate(dataSource).queryForObject(
                "SELECT status FROM crm_marketing_form WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, form.getId()));
    }

    @Test
    void publicSubmissionRejectsUnknownIdentityAndLifecycleProperties() throws Exception {
        bootstrap("Strict public form owner");
        MarketingFormResponse active = activate(createForm(
                "Strict public form", "Strict public form",
                List.of(field("lead_name", "Name", true, 10))));

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": 1,
                                  "ownerActorId": 1,
                                  "status": "ACTIVE",
                                  "values": {"lead_name": ["Injected lead"]}
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name = ?",
                Integer.class, TENANT_ID, "Injected lead"));
    }

    @Test
    void publicSubmissionRejectsNullFileIdentifiersAsBadRequest() throws Exception {
        bootstrap("Null public FILE identifier owner");
        FieldDefinitionResponse resume = fromJson(performPost("/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Resume").setFieldType("FILE")
                        .setRequired(false).setSearchable(false))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        MarketingFormResponse active = activate(createForm(
                "Null public FILE identifier form", "Null public FILE identifier form",
                List.of(field("lead_name", "Name", true, 10),
                        field(resume.getCode(), "Resume", false, 20))));

        // The idempotency key is present and the FILE field is configured, so the null identifier
        // is the only remaining violation; without it the missing key alone produced the 400.
        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"null-file-identifier-1",
                                 "values":{"lead_name":["Invalid null FILE"]},
                                 "fileFields":{"%s":[null]}}
                                """.formatted(resume.getCode())))
                .andExpect(status().isBadRequest())
                // Pin the rejecting layer: a missing draft id also yields 400, so only the bound
                // field path proves that the null identifier itself was refused.
                .andExpect(jsonPath("$.errMsg").value(org.hamcrest.Matchers.containsString(
                        "fileFields[" + resume.getCode() + "][0]")));
    }

    @Test
    void tenantIsolationRejectsCrossTenantQueriesUpdatesAndDeletes() throws Exception {
        bootstrap("Tenant A owner");
        MarketingFormResponse form = createForm("Tenant A form", "Tenant A title",
                List.of(field("lead_name", "Name", true, 10)));
        bootstrap(OTHER_ACTOR_ID, OTHER_TENANT_ID, "Tenant B owner");

        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID, get("/api/v1/marketing-forms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                get("/api/v1/marketing-forms/{formId}", form.getId()))
                .andExpect(status().isNotFound());
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                put("/api/v1/marketing-forms/{formId}", form.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest(form.getVersion(),
                                "Cross-tenant update", "Cross-tenant update",
                                List.of(field("lead_name", "Name", true, 10))))))
                .andExpect(status().isNotFound());
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                delete("/api/v1/marketing-forms/{formId}", form.getId())
                        .param("version", form.getVersion().toString()))
                .andExpect(status().isNotFound());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("Tenant A form", jdbc.queryForObject(
                "SELECT name FROM crm_marketing_form WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, form.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_form WHERE tenant_id = ?",
                Integer.class, OTHER_TENANT_ID));
    }

    @Test
    void staleVersionsRejectUpdateStatusChangeAndDelete() throws Exception {
        bootstrap("Versioned form owner");
        MarketingFormResponse created = createForm("Versioned form", "Original title",
                List.of(field("lead_name", "Name", true, 10)));
        MarketingFormResponse updated = fromJson(performPut(
                "/api/v1/marketing-forms/" + created.getId(),
                updateRequest(created.getVersion(), "Updated form", "Updated title",
                        List.of(field("lead_name", "Name", true, 10))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);

        performPut("/api/v1/marketing-forms/" + created.getId(),
                updateRequest(created.getVersion(), "Stale form", "Stale title",
                        List.of(field("lead_name", "Name", true, 10))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errCode").value(
                        ApplicationErrorCode.VERSION_CONFLICT.getCompleteCode("6903")));
        performPut("/api/v1/marketing-forms/" + created.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(created.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errCode").value(
                        ApplicationErrorCode.VERSION_CONFLICT.getCompleteCode("6904")));
        performDelete("/api/v1/marketing-forms/" + created.getId()
                        + "?version=" + created.getVersion())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errCode").value(
                        ApplicationErrorCode.VERSION_CONFLICT.getCompleteCode("6905")));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(updated.getVersion(), jdbc.queryForObject(
                "SELECT version FROM crm_marketing_form WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, created.getId()));
        assertEquals("Updated form", jdbc.queryForObject(
                "SELECT name FROM crm_marketing_form WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, created.getId()));
        assertEquals("INACTIVE", jdbc.queryForObject(
                "SELECT status FROM crm_marketing_form WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, created.getId()));
    }

    @Test
    void submissionQueuedAfterUpdateUsesTheCommittedConfiguration() throws Exception {
        bootstrap("Concurrent update owner");
        MarketingFormResponse active = activate(createForm(
                "Concurrent update form", "Old title", List.of(
                        field("lead_name", "Name", true, 10),
                        field("email", "Email", false, 20))));
        MarketingFormUpdateRequest update = updateRequest(active.getVersion(),
                "Concurrent update form", "New title",
                List.of(field("lead_name", "Name", true, 10)));
        MarketingFormSubmissionRequest oldSubmission = new MarketingFormSubmissionRequest()
                .setIdempotencyKey("old-configuration-lead")
                .setValues(Map.of(
                        "lead_name", List.of("Old configuration lead"),
                        "email", List.of("old@example.com")));

        List<Integer> statuses = executeQueuedBehindFormLock(active.getId(),
                () -> performPut("/api/v1/marketing-forms/" + active.getId(), update)
                        .andReturn().getResponse().getStatus(),
                () -> mockMvc.perform(post(
                                "/api/v1/open/marketing-forms/{publicId}",
                                active.getPublicId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(oldSubmission)))
                        .andReturn().getResponse().getStatus());

        assertEquals(List.of(200, 400), statuses);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("New title", jdbc.queryForObject(
                "SELECT title FROM crm_marketing_form WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, active.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name = ?",
                Integer.class, TENANT_ID, "Old configuration lead"));
    }

    @Test
    void deactivationWaitsForInFlightSubmissionAndBlocksLaterSubmissions()
            throws Exception {
        bootstrap("Concurrent deactivation owner");
        MarketingFormResponse active = activate(createForm(
                "Concurrent deactivation form", "Concurrent deactivation form",
                List.of(field("lead_name", "Name", true, 10))));
        MarketingFormSubmissionRequest submission = new MarketingFormSubmissionRequest()
                .setIdempotencyKey("in-flight-form-lead")
                .setValues(Map.of("lead_name", List.of("In-flight form lead")));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            lockCustomizationObjectGuard(blocker);
            Future<Integer> submitFuture = executor.submit(() -> mockMvc.perform(post(
                            "/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(submission)))
                    .andReturn().getResponse().getStatus());
            assertBlocked(submitFuture,
                    "Submission must wait inside B09 while retaining the form lock");

            Future<Integer> deactivateFuture = executor.submit(() -> performPut(
                            "/api/v1/marketing-forms/" + active.getId() + "/status",
                            new MarketingFormStatusUpdateRequest()
                                    .setVersion(active.getVersion()).setStatus("INACTIVE"))
                    .andReturn().getResponse().getStatus());
            assertBlocked(deactivateFuture,
                    "Deactivation must wait for the in-flight submission form lock");

            blocker.commit();
            assertEquals(200, submitFuture.get(10, TimeUnit.SECONDS));
            assertEquals(200, deactivateFuture.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("INACTIVE", jdbc.queryForObject(
                "SELECT status FROM crm_marketing_form WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, active.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name = ?",
                Integer.class, TENANT_ID, "In-flight form lead"));

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionRequest()
                                .setIdempotencyKey("post-deactivation-lead")
                                .setValues(
                                Map.of("lead_name", List.of("Post-deactivation lead"))))))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name = ?",
                Integer.class, TENANT_ID, "Post-deactivation lead"));
    }

    private MarketingFormResponse createForm(
            String name, String title, List<MarketingFormFieldRequest> fields)
            throws Exception {
        return fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest().setName(name).setTitle(title)
                        .setFields(fields))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
    }

    private void submit(MarketingFormResponse form, String leadName) throws Exception {
        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", form.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionRequest()
                                .setIdempotencyKey("helper-" + java.util.UUID.randomUUID())
                                .setValues(Map.of("lead_name", List.of(leadName))))))
                .andExpect(status().isOk());
    }

    private TenantRoleResponse createRole(
            String name, RoleGrantRequest... grants) throws Exception {
        return fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName(name).setGrants(List.of(grants)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private RoleGrantRequest grant(String authority, String dataScope) {
        return new RoleGrantRequest().setAuthorityCode(authority).setDataScope(dataScope);
    }

    private void assignRole(long roleId, long actorId) throws Exception {
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(actorId)))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions performSubmissionQuery(
            long actorId, long formId) throws Exception {
        return performAs(actorId, TENANT_ID,
                post("/api/v1/marketing-forms/{formId}/submissions/query", formId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionQueryRequest()
                                .setPage(1).setSize(100))));
    }

    private void assertSubmissionTotal(long actorId, long formId, int expected)
            throws Exception {
        performSubmissionQuery(actorId, formId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(expected));
    }

    private long insertDepartment(JdbcTemplate jdbc, long parentId, String name) {
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, parentId, name);
        return jdbc.queryForObject("SELECT id FROM organization_department "
                        + "WHERE tenant_id = ? AND parent_department_id = ? AND name = ?",
                Long.class, TENANT_ID, parentId, name);
    }

    private void seedActiveMember(
            long actorId, String displayName, Long departmentId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                + "VALUES (?, ?)", actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, actorId, departmentId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT_ID);
    }

    private MarketingFormResponse activate(MarketingFormResponse form) throws Exception {
        return fromJson(performPut(
                "/api/v1/marketing-forms/" + form.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(form.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
    }

    private MarketingFormUpdateRequest updateRequest(
            Long version, String name, String title,
            List<MarketingFormFieldRequest> fields) {
        return new MarketingFormUpdateRequest().setVersion(version).setName(name)
                .setTitle(title).setFields(fields);
    }

    private List<Integer> executeQueuedBehindFormLock(
            Long formId, Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement(
                    "SELECT id FROM crm_marketing_form "
                            + "WHERE tenant_id = ? AND id = ? FOR UPDATE")) {
                lock.setLong(1, TENANT_ID);
                lock.setLong(2, formId);
                try (var rows = lock.executeQuery()) {
                    assertTrue(rows.next());
                }
            }
            Future<Integer> firstFuture = executor.submit(first);
            assertBlocked(firstFuture, "First form operation must wait for the row lock");
            Future<Integer> secondFuture = executor.submit(second);
            assertBlocked(secondFuture, "Second form operation must wait behind the first");

            blocker.commit();
            return List.of(firstFuture.get(10, TimeUnit.SECONDS),
                    secondFuture.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private void lockCustomizationObjectGuard(Connection connection) throws Exception {
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT tenant_id FROM customization_object_guard "
                        + "WHERE tenant_id = ? AND object_type = 'LEAD' FOR UPDATE")) {
            lock.setLong(1, TENANT_ID);
            try (var rows = lock.executeQuery()) {
                assertTrue(rows.next());
            }
        }
    }

    private void assertBlocked(Future<?> future, String message) {
        org.junit.jupiter.api.Assertions.assertThrows(TimeoutException.class,
                () -> future.get(300, TimeUnit.MILLISECONDS), message);
    }

    private MarketingFormFieldRequest field(
            String code, String label, boolean required, int order) {
        return new MarketingFormFieldRequest().setFieldCode(code).setLabel(label)
                .setRequired(required).setSortOrder(order);
    }

    private void bootstrap(String displayName) throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, displayName);
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions performAs(
            long actorId, long tenantId,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearData();
    }

    @Override
    protected void clearTestData() {
        clearData();
    }

    private void clearData() {
        executeSql("DELETE FROM crm_attachment_field_binding");
        executeSql("DELETE FROM crm_marketing_file_draft_candidate");
        executeSql("DELETE FROM crm_attachment_job");
        executeSql("DELETE FROM crm_attachment_content");
        executeSql("DELETE FROM crm_attachment_file");
        executeSql("DELETE FROM crm_marketing_file_draft_guard");
        executeSql("DELETE FROM crm_marketing_file_draft");
        executeSql("DELETE FROM crm_marketing_form_submission");
        executeSql("DELETE FROM crm_marketing_form_field");
        executeSql("DELETE FROM crm_marketing_form");
        executeSql("DELETE FROM crm_customer_unique_field_value");
        executeSql("DELETE FROM crm_customer_field_value");
        executeSql("DELETE FROM crm_customer_collaborator");
        executeSql("DELETE FROM crm_customer_tag");
        executeSql("DELETE FROM crm_customer_address");
        executeSql("UPDATE crm_customer SET parent_customer_id = NULL");
        executeSql("DELETE FROM crm_customer");
        executeSql("DELETE FROM crm_customer_guard");
        executeSql("DELETE FROM crm_lead_unique_field_value");
        executeSql("DELETE FROM crm_lead_field_value");
        executeSql("DELETE FROM crm_lead_collaborator");
        executeSql("DELETE FROM crm_lead_tag");
        executeSql("DELETE FROM crm_lead_address");
        executeSql("UPDATE crm_lead SET parent_lead_id = NULL");
        executeSql("DELETE FROM crm_lead");
        executeSql("DELETE FROM crm_lead_guard");
        executeSql("DELETE FROM saved_view");
        executeSql("DELETE FROM role_field_access");
        executeSql("DELETE FROM role_field_policy");
        executeSql("DELETE FROM field_layout_preference");
        executeSql("DELETE FROM custom_field_definition");
        executeSql("DELETE FROM customization_object_guard");
        executeSql("DELETE FROM navigation_preference");
        executeSql("DELETE FROM authorization_role_assignment");
        executeSql("DELETE FROM authorization_role_grant");
        executeSql("DELETE FROM authorization_role");
        executeSql("DELETE FROM organization_membership_request");
        executeSql("DELETE FROM organization_membership");
        executeSql("DELETE FROM organization_department");
        executeSql("DELETE FROM organization_member_profile");
        executeSql("DELETE FROM organization_tenant");
    }
}
