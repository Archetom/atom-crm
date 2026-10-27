package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.CrmImportDraftConfirmRequest;
import io.github.archetom.crm.api.dto.request.CrmImportDraftCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.response.AttachmentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadStatusResponse;
import io.github.archetom.crm.api.dto.response.CrmImportConfirmResponse;
import io.github.archetom.crm.api.dto.response.CrmImportDraftResponse;
import io.github.archetom.crm.api.dto.response.CrmImportHistoryPageResponse;
import io.github.archetom.crm.api.dto.response.CrmImportPreviewResponse;
import io.github.archetom.crm.api.dto.response.MarketingActivityResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskResponse;
import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentInspectionWorker;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentPublicationWorker;
import io.github.archetom.crm.application.service.attachment.worker.CrmImportDraftExpiryWorker;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.infra.external.attachment.SecureLocalAttachmentConformanceContract;
import io.github.archetom.crm.infra.external.attachment.SecureLocalAttachmentConformanceReportSource;
import io.github.archetom.crm.infra.external.attachment.SecureLocalAttachmentProperties;
import io.github.archetom.crm.infra.external.attachment.StreamingInspectionEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full Work lane: target-first HTTP intent, stream, inspection/publication, read/range and withdrawal. */
@RequiresDatabase
@Import(B24aWorkTaskAttachmentIntegrationTest.PositiveAttachmentProfile.class)
class B24aWorkTaskAttachmentIntegrationTest extends BaseIntegrationTest {
    private static final long ACTOR = 9_180_001L;
    private static final long TENANT = 9_180_002L;
    static final byte[] BINDING = new byte[] {11, 12, 13};
    static final Path ROOT = createRoot();
    private static final AtomicBoolean CONCURRENT_STORE_GATE = new AtomicBoolean();
    private static final CyclicBarrier CONCURRENT_STORE_BARRIER = new CyclicBarrier(2);

    @org.springframework.beans.factory.annotation.Autowired private AttachmentInspectionWorker inspectionWorker;
    @org.springframework.beans.factory.annotation.Autowired private AttachmentPublicationWorker publicationWorker;
    @org.springframework.beans.factory.annotation.Autowired private CrmImportDraftExpiryWorker importDraftExpiryWorker;

    @DynamicPropertySource
    static void attachmentProfile(DynamicPropertyRegistry registry) {
        registry.add("atom.files.enabled", () -> "true");
        registry.add("atom.files.workers.enabled", () -> "false");
        registry.add("atom.files.storage-profile", SecureLocalAttachmentConformanceContract::profile);
        registry.add("atom.files.secure-local-root", () -> ROOT.toString());
        registry.add("atom.files.binding-base64", () -> Base64.getUrlEncoder().withoutPadding().encodeToString(BINDING));
        registry.add("atom.files.inspection.detector-profile", () -> "BOUNDED_MAGIC_V1");
        registry.add("atom.files.inspection.scanner-profile", () -> "STREAMING_ENGINE_V1");
        registry.add("atom.files.inspection.detector-catalog-revision", () -> "magic-r1");
        registry.add("atom.files.inspection.allowlist-revision", () -> "public-r1");
        registry.add("atom.files.inspection.definition-max-age", () -> "PT30S");
        registry.add("atom.files.policy.max-file-bytes", () -> "1048576");
        registry.add("atom.files.policy.max-ready-bytes", () -> "2097152");
        registry.add("atom.files.policy.max-quarantine-bytes", () -> "2097152");
        registry.add("atom.files.policy.max-pending-intents", () -> "10");
        registry.add("atom.files.policy.max-active-uploads", () -> "20");
        registry.add("atom.files.policy.max-active-inspections", () -> "2");
        registry.add("atom.files.policy.max-active-downloads", () -> "2");
        registry.add("atom.files.policy.global-reserve-bytes", () -> "4194304");
        registry.add("atom.files.policy.deletion-control-reserve", () -> "1");
        registry.add("atom.files.policy.local-free-floor-bytes", () -> "4096");
        registry.add("atom.files.policy.intent-ttl", () -> "PT30S");
        registry.add("atom.files.policy.upload-open-timeout", () -> "PT1S");
        registry.add("atom.files.policy.upload-idle-timeout", () -> "PT1S");
        registry.add("atom.files.policy.upload-total-timeout", () -> "PT10S");
        registry.add("atom.files.policy.inspection-total-timeout", () -> "PT10S");
        registry.add("atom.files.policy.download-open-timeout", () -> "PT1S");
        registry.add("atom.files.policy.download-idle-timeout", () -> "PT1S");
        registry.add("atom.files.policy.download-total-timeout", () -> "PT10S");
        registry.add("atom.files.policy.job-lease", () -> "PT4S");
        registry.add("atom.files.policy.job-heartbeat", () -> "PT1S");
        registry.add("atom.files.policy.max-attempts", () -> "3");
        registry.add("atom.files.policy.quarantine-retention", () -> "PT2S");
        registry.add("atom.files.policy.tombstone-retention", () -> "PT8S");
        registry.add("atom.files.policy.claim-size", () -> "1");
    }

    @Test
    void workTaskAttachmentHttpLifecycleAndHistoryDeleteBlock() throws Exception {
        asOwner(post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Attachment task owner"))))
                .andExpect(status().isOk());
        WorkTaskResponse task = fromJson(asOwner(post("/api/v1/work/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskCreateRequest().setTitle("Attachment history task").setAssigneeActorId(ACTOR))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), WorkTaskResponse.class);
        String root = "/api/v1/attachment-targets/work-task/" + task.getId() + "/attachments";
        byte[] bytes = "%PDF-1.7\\nB24a".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        AttachmentUploadIntentResponse intent = fromJson(asOwner(post(root + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new AttachmentUploadIntentCreateRequest()
                        .setDisplayName("work.pdf").setDeclaredMediaType("application/pdf")
                        .setExpectedBytes((long) bytes.length))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version", intent.getVersion().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes))
                .andExpect(status().isAccepted());
        byte[] alternate = bytes.clone();
        alternate[alternate.length - 1] ^= 1;
        // Stored state is only idempotent for the original intent version and exact content
        // evidence; it must not turn the endpoint into a generic uploader success oracle.
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version",
                Long.toString(intent.getVersion() + 1)).contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes))
                .andExpect(status().isConflict());
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version", intent.getVersion().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(alternate))
                .andExpect(status().isConflict());
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version", intent.getVersion().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes))
                .andExpect(status().isAccepted());
        AttachmentUploadStatusResponse pending = fromJson(asOwner(get(root + "/{fileId}/upload-status", intent.getFileId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), AttachmentUploadStatusResponse.class);
        assertEquals("ACCEPTED_FOR_INSPECTION", pending.getStatus());

        inspectionWorker.runOnce();
        publicationWorker.runOnce();
        AttachmentResponse metadata = fromJson(asOwner(get(root + "/{fileId}", intent.getFileId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), AttachmentResponse.class);
        asOwner(get(root).param("page", "1").param("size", "20")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("work.pdf")));
        assertEquals("application/pdf", metadata.getMediaType());

        assertArrayEquals(bytes, completeAsync(asOwner(get(root + "/{fileId}/content", intent.getFileId()))
                .andExpect(status().isOk())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray());
        completeAsync(asOwner(get(root + "/{fileId}/content", intent.getFileId())
                .header("Range", "bytes=2-5")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/" + bytes.length)))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes(java.util.Arrays.copyOfRange(bytes, 2, 6)));

        asOwner(delete(root + "/{fileId}", intent.getFileId()).param("version", metadata.getVersion().toString()))
                .andExpect(status().isNoContent());
        asOwner(delete("/api/v1/work/tasks/{id}", task.getId()).param("version", task.getVersion().toString()))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("TODO", new JdbcTemplate(dataSource).queryForObject(
                "SELECT status FROM crm_work_task WHERE tenant_id = ? AND id = ?", String.class, TENANT, task.getId()));
    }

    @Test
    void leadCsvImportUsesThePrivateAttachmentThenCreatesEveryValidatedRowAtomically() throws Exception {
        int bootstrapStatus = asOwner(post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("CSV import owner"))))
                .andReturn().getResponse().getStatus();
        assertTrue(bootstrapStatus == 200 || bootstrapStatus == 409);
        CrmImportDraftResponse draft = fromJson(asOwner(post("/api/v1/crm-import-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftCreateRequest().setObjectType("LEAD"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                CrmImportDraftResponse.class);
        assertEquals("import-draft", draft.getAttachmentTargetType());
        assertEquals("LEAD", draft.getObjectType());
        asOwner(get("/api/v1/crm-import-drafts/templates/LEAD")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("phoneNumber")));

        String root = "/api/v1/attachment-targets/import-draft/" + draft.getDraftId() + "/attachments";
        byte[] source = String.join("\n",
                "name,phoneNumber,telephone,jobTitle,email,website,description,customerId,tags",
                "Imported lead A,,,,,,,,priority",
                "Imported lead B,,,,,,,,priority;summer", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AttachmentUploadIntentResponse intent = fromJson(asOwner(post(root + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new AttachmentUploadIntentCreateRequest()
                        .setDisplayName("lead-import.csv").setDeclaredMediaType("text/csv")
                        .setExpectedBytes((long) source.length))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version", intent.getVersion().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(source)).andExpect(status().isAccepted());
        inspectionWorker.runOnce();
        publicationWorker.runOnce();
        assertEquals("AVAILABLE", fromJson(asOwner(get(root + "/{fileId}/upload-status", intent.getFileId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadStatusResponse.class).getStatus());

        CrmImportPreviewResponse preview = fromJson(asOwner(get("/api/v1/crm-import-drafts/{draftId}/preview",
                draft.getDraftId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CrmImportPreviewResponse.class);
        assertEquals("READY", preview.getStatus());
        assertEquals(2, preview.getValidRows());
        assertTrue(preview.getIssues().isEmpty());

        CrmImportConfirmResponse confirmed = fromJson(asOwner(post("/api/v1/crm-import-drafts/{draftId}/confirm",
                draft.getDraftId()).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftConfirmRequest().setVersion(draft.getVersion()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), CrmImportConfirmResponse.class);
        assertEquals("COMPLETED", confirmed.getStatus());
        assertEquals(2, confirmed.getImportedRows());
        assertEquals(2, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name LIKE 'Imported lead %'", Integer.class, TENANT));
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_attachment_relation WHERE tenant_id = ? AND import_draft_id = ?"
                        + " AND relation_state = 'ACTIVE'", Integer.class, TENANT, draft.getDraftId()));

        CrmImportHistoryPageResponse history = fromJson(asOwner(get(
                "/api/v1/crm-import-drafts/history/LEAD").param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CrmImportHistoryPageResponse.class);
        assertEquals(1L, history.totalNum());
        assertEquals(1, history.objectList().size());
        assertEquals(draft.getDraftId(), history.objectList().getFirst().draftId());
        assertEquals(2, history.objectList().getFirst().importedRows());

        asOwner(post("/api/v1/crm-import-drafts/{draftId}/confirm", draft.getDraftId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftConfirmRequest().setVersion(confirmed.getVersion() - 1))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("COMPLETED")));
        assertEquals(2, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name LIKE 'Imported lead %'", Integer.class, TENANT));
    }

    @Test
    void invalidContactCsvPreviewCannotPartiallyCreateRowsAndCanBeWithdrawn() throws Exception {
        int bootstrapStatus = asOwner(post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Invalid CSV import owner"))))
                .andReturn().getResponse().getStatus();
        assertTrue(bootstrapStatus == 200 || bootstrapStatus == 409);
        CrmImportDraftResponse draft = fromJson(asOwner(post("/api/v1/crm-import-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftCreateRequest().setObjectType("CONTACT"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                CrmImportDraftResponse.class);
        String root = "/api/v1/attachment-targets/import-draft/" + draft.getDraftId() + "/attachments";
        byte[] source = String.join("\n",
                "name,phoneNumber,telephone,jobTitle,email,website,description,customerId,tags",
                "Imported contact A,,,,,,,,priority",
                ",,,,,,,,", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AttachmentUploadIntentResponse intent = fromJson(asOwner(post(root + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new AttachmentUploadIntentCreateRequest()
                        .setDisplayName("contact-import.csv").setDeclaredMediaType("text/csv")
                        .setExpectedBytes((long) source.length))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version", intent.getVersion().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(source)).andExpect(status().isAccepted());
        inspectionWorker.runOnce();
        publicationWorker.runOnce();

        CrmImportPreviewResponse preview = fromJson(asOwner(get("/api/v1/crm-import-drafts/{draftId}/preview",
                draft.getDraftId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CrmImportPreviewResponse.class);
        assertEquals("INVALID", preview.getStatus());
        assertFalse(preview.getIssues().isEmpty());
        asOwner(post("/api/v1/crm-import-drafts/{draftId}/confirm", draft.getDraftId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftConfirmRequest().setVersion(draft.getVersion()))))
                .andExpect(status().isBadRequest());
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_contact WHERE tenant_id = ? AND name LIKE 'Imported contact %'", Integer.class, TENANT));
        asOwner(delete("/api/v1/crm-import-drafts/{draftId}", draft.getDraftId())
                .param("version", draft.getVersion().toString())).andExpect(status().isNoContent());
    }

    @Test
    void expiredImportDraftWithdrawsItsPrivateSourceAndSchedulesControlledReclamation() throws Exception {
        int bootstrapStatus = asOwner(post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Expired CSV import owner"))))
                .andReturn().getResponse().getStatus();
        assertTrue(bootstrapStatus == 200 || bootstrapStatus == 409);
        CrmImportDraftResponse draft = fromJson(asOwner(post("/api/v1/crm-import-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftCreateRequest().setObjectType("PRODUCT"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                CrmImportDraftResponse.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime retainedUntil = jdbc.queryForObject("SELECT deadline FROM crm_import_draft"
                        + " WHERE tenant_id = ? AND id = ?", LocalDateTime.class, TENANT, draft.getDraftId());
        assertTrue(retainedUntil.isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(6)));

        String root = "/api/v1/attachment-targets/import-draft/" + draft.getDraftId() + "/attachments";
        byte[] source = String.join("\n",
                "name,code,categoryId,unit,listPrice,description,availability",
                "Expired product,EXPIRED-CSV,,piece,1.00,Discard after retention,AVAILABLE", "")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AttachmentUploadIntentResponse intent = fromJson(asOwner(post(root + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new AttachmentUploadIntentCreateRequest()
                        .setDisplayName("expired-product.csv").setDeclaredMediaType("text/csv")
                        .setExpectedBytes((long) source.length))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version", intent.getVersion().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(source)).andExpect(status().isAccepted());
        inspectionWorker.runOnce();
        publicationWorker.runOnce();

        jdbc.update("UPDATE crm_import_draft SET deadline = ? WHERE tenant_id = ? AND id = ?",
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1), TENANT, draft.getDraftId());
        importDraftExpiryWorker.runOnce();

        assertEquals("EXPIRED", jdbc.queryForObject("SELECT state FROM crm_import_draft WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, draft.getDraftId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_relation"
                        + " WHERE tenant_id = ? AND import_draft_id = ? AND relation_state = 'ACTIVE'",
                Integer.class, TENANT, draft.getDraftId()));
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT publication_state FROM crm_attachment_file"
                        + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, intent.getFileId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_job"
                        + " WHERE tenant_id = ? AND file_id = ? AND kind = 'DELETION'", Integer.class, TENANT,
                intent.getFileId()));
    }

    @Test
    void productCsvImportSkipsExistingAndRepeatedCodesWithoutOverwritingProducts() throws Exception {
        int bootstrapStatus = asOwner(post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Product CSV import owner"))))
                .andReturn().getResponse().getStatus();
        assertTrue(bootstrapStatus == 200 || bootstrapStatus == 409);
        asOwner(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ProductCreateRequest().setName("Existing import product")
                        .setCode("CSV-EXISTING").setListPrice(new BigDecimal("1.00")))))
                .andExpect(status().isOk());
        CrmImportDraftResponse draft = fromJson(asOwner(post("/api/v1/crm-import-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftCreateRequest().setObjectType("PRODUCT"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                CrmImportDraftResponse.class);
        asOwner(get("/api/v1/crm-import-drafts/templates/PRODUCT")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("listPrice")));

        String root = "/api/v1/attachment-targets/import-draft/" + draft.getDraftId() + "/attachments";
        byte[] source = String.join("\n",
                "name,code,categoryId,unit,listPrice,description,availability",
                "Existing import product,CSV-EXISTING,,piece,1.00,Must not overwrite,AVAILABLE",
                "Imported product A,CSV-NEW,,piece,12.50,Imported,AVAILABLE",
                "Repeated product A,CSV-NEW,,piece,13.00,Must not overwrite,UNAVAILABLE", "")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AttachmentUploadIntentResponse intent = fromJson(asOwner(post(root + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new AttachmentUploadIntentCreateRequest()
                        .setDisplayName("product-import.csv").setDeclaredMediaType("text/csv")
                        .setExpectedBytes((long) source.length))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
        asOwner(put(root + "/{fileId}/content", intent.getFileId()).param("version", intent.getVersion().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(source)).andExpect(status().isAccepted());
        inspectionWorker.runOnce();
        publicationWorker.runOnce();
        CrmImportPreviewResponse preview = fromJson(asOwner(get("/api/v1/crm-import-drafts/{draftId}/preview",
                draft.getDraftId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CrmImportPreviewResponse.class);
        assertEquals("READY", preview.getStatus());
        assertEquals(3, preview.getValidRows());

        CrmImportConfirmResponse confirmed = fromJson(asOwner(post("/api/v1/crm-import-drafts/{draftId}/confirm",
                draft.getDraftId()).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftConfirmRequest().setVersion(draft.getVersion()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), CrmImportConfirmResponse.class);
        assertEquals("COMPLETED", confirmed.getStatus());
        assertEquals(1, confirmed.getImportedRows());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_product WHERE tenant_id = ?"
                + " AND code_key = 'csv-new'", Integer.class, TENANT));
        assertEquals("Existing import product", jdbc.queryForObject("SELECT name FROM crm_product WHERE tenant_id = ?"
                + " AND code_key = 'csv-existing'", String.class, TENANT));
        asOwner(post("/api/v1/crm-import-drafts/{draftId}/confirm", draft.getDraftId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CrmImportDraftConfirmRequest().setVersion(confirmed.getVersion() - 1))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("COMPLETED")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_product WHERE tenant_id = ?"
                + " AND code_key = 'csv-new'", Integer.class, TENANT));
    }

    @Test
    void marketingActivityImagesUseClosedGroupsAndEnforceNineIntentsPerGroup() throws Exception {
        int bootstrapStatus = asOwner(post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Marketing image owner"))))
                .andReturn().getResponse().getStatus();
        assertTrue(bootstrapStatus == 200 || bootstrapStatus == 409);
        MarketingActivityResponse activity = fromJson(asOwner(post("/api/v1/marketing-activities")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new MarketingActivityCreateRequest()
                        .setName("Image-limited activity").setRelatedObjectType("CUSTOMER")
                        .setParticipantActorIds(List.of(ACTOR))
                        .setStartTime(LocalDateTime.of(2026, 10, 1, 9, 0))
                        .setEndTime(LocalDateTime.of(2026, 10, 1, 18, 0)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingActivityResponse.class);
        String activityRoot = "/api/v1/attachment-targets/marketing-activity/" + activity.getId()
                + "/attachments";

        WorkTaskResponse task = fromJson(asOwner(post("/api/v1/work/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskCreateRequest().setTitle("Ungrouped target").setAssigneeActorId(ACTOR))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), WorkTaskResponse.class);
        String taskRoot = "/api/v1/attachment-targets/work-task/" + task.getId() + "/attachments";
        asOwner(post(taskRoot + "/upload-intents").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(imageIntent("other-target.png").setMarketingActivityImageType("COVER"))))
                .andExpect(status().isBadRequest());

        for (int index = 1; index <= 9; index++) {
            asOwner(post(activityRoot + "/upload-intents").contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(imageIntent("cover-" + index + ".png")
                            .setMarketingActivityImageType("COVER"))))
                    .andExpect(status().isCreated());
        }
        asOwner(post(activityRoot + "/upload-intents").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(imageIntent("cover-overflow.png").setMarketingActivityImageType("COVER"))))
                .andExpect(status().isUnprocessableEntity());
        asOwner(post(activityRoot + "/upload-intents").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(imageIntent("detail-1.jpg").setMarketingActivityImageType("DETAIL"))))
                .andExpect(status().isCreated());
        asOwner(get(activityRoot).param("marketingActivityImageType", "DETAIL"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("[]")));
        assertEquals(9, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM crm_attachment_relation"
                        + " WHERE tenant_id = ? AND marketing_activity_id = ?"
                        + " AND marketing_activity_image_type = 'COVER' AND relation_state = 'ACTIVE'",
                Integer.class, TENANT, activity.getId()));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM crm_attachment_relation"
                        + " WHERE tenant_id = ? AND marketing_activity_id = ?"
                        + " AND marketing_activity_image_type = 'DETAIL' AND relation_state = 'ACTIVE'",
                Integer.class, TENANT, activity.getId()));
    }

    @Test
    void simultaneousIdenticalPutConvergesAfterBothAdmissions() throws Exception {
        int bootstrapStatus = asOwner(post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Concurrent attachment owner"))))
                .andReturn().getResponse().getStatus();
        assertTrue(bootstrapStatus == 200 || bootstrapStatus == 409);
        WorkTaskResponse task = fromJson(asOwner(post("/api/v1/work/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskCreateRequest().setTitle("Concurrent attachment task").setAssigneeActorId(ACTOR))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), WorkTaskResponse.class);
        String root = "/api/v1/attachment-targets/work-task/" + task.getId() + "/attachments";
        byte[] bytes = "%PDF-1.7\nconcurrent".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        AttachmentUploadIntentResponse intent = fromJson(asOwner(post(root + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new AttachmentUploadIntentCreateRequest()
                        .setDisplayName("concurrent.pdf").setDeclaredMediaType("application/pdf")
                        .setExpectedBytes((long) bytes.length))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
        CONCURRENT_STORE_GATE.set(true);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> asOwner(put(root + "/{fileId}/content", intent.getFileId())
                    .param("version", intent.getVersion().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(bytes)).andReturn().getResponse().getStatus());
            var second = executor.submit(() -> asOwner(put(root + "/{fileId}/content", intent.getFileId())
                    .param("version", intent.getVersion().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(bytes)).andReturn().getResponse().getStatus());
            assertEquals(202, first.get(10, TimeUnit.SECONDS));
            assertEquals(202, second.get(10, TimeUnit.SECONDS));
        } finally {
            CONCURRENT_STORE_GATE.set(false);
        }
    }

    private org.springframework.test.web.servlet.ResultActions asOwner(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", ACTOR).header("X-Dev-Tenant-Id", TENANT));
    }

    @Override protected void clearTestData() {
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_tenant_usage WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_global_usage WHERE singleton_id = 1");
        executeSql("DELETE FROM crm_import_draft WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead_star WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead_unique_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead_collaborator WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead_tag WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead_address WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_lead_guard WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact_star WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact_unique_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact_collaborator WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact_tag WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact_address WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_contact_guard WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_image_draft_file WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_image_draft WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_unique_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_catalog_guard WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_marketing_activity_participant WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_marketing_activity_participant_department WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_marketing_activity WHERE tenant_id = " + TENANT);
    }

    private static Path createRoot() {
        try { return Files.createTempDirectory("b24a-work-http-"); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static AttachmentUploadIntentCreateRequest imageIntent(String name) {
        return new AttachmentUploadIntentCreateRequest().setDisplayName(name)
                .setDeclaredMediaType(name.endsWith(".png") ? "image/png" : "image/jpeg")
                .setExpectedBytes(8L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PositiveAttachmentProfile {
        @Bean StreamingInspectionEngine streamedInspectionEngine() {
            return new StreamingInspectionEngine() {
                @Override public Evidence evidence() {
                    return new Evidence("work-test-engine-r1", "work-definitions-r1",
                            AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                            Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));
                }
                @Override public AttachmentContentScanner.ScanResult scan(java.io.InputStream input,
                                                                            AttachmentContentEvidence evidence)
                        throws IOException {
                    while (input.read(new byte[1024]) >= 0) { }
                    return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED,
                            AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                            "work-definitions-r1", null);
                }
            };
        }
        @Bean @Primary AttachmentStorage concurrencyGatedAttachmentStorage() throws IOException {
            AttachmentStorage delegate = new io.github.archetom.crm.infra.external.attachment.SecureLocalAttachmentStorage(ROOT);
            return new AttachmentStorage() {
                @Override public Allocation allocate(StorageBindingRef binding, long expectedBytes, Instant deadline) {
                    return delegate.allocate(binding, expectedBytes, deadline);
                }
                @Override public StoreReceipt store(StoreCommand command,
                                                    io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource source)
                        throws Exception {
                    if (CONCURRENT_STORE_GATE.get()) {
                        try { CONCURRENT_STORE_BARRIER.await(5, TimeUnit.SECONDS); }
                        catch (Exception exception) { throw new IOException("concurrent upload barrier failed", exception); }
                    }
                    return delegate.store(command, source);
                }
                @Override public ProbeResult probe(ProbeCommand command) throws Exception { return delegate.probe(command); }
                @Override public io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle open(OpenCommand command)
                        throws Exception { return delegate.open(command); }
                @Override public void requestDelete(DeleteCommand command) throws Exception { delegate.requestDelete(command); }
                @Override public boolean confirmAbsent(DeleteCommand command) throws Exception { return delegate.confirmAbsent(command); }
                @Override public AbortState abortIncomplete(StorageBindingRef binding,
                                                            io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator locator,
                                                            byte[] storageGeneration, long attempt, Instant deadline) throws Exception {
                    return delegate.abortIncomplete(binding, locator, storageGeneration, attempt, deadline);
                }
            };
        }
        @Bean SecureLocalAttachmentConformanceReportSource b24aReport(SecureLocalAttachmentProperties properties,
                                                                        StreamingInspectionEngine engine) throws IOException {
            String rootDigest = SecureLocalAttachmentConformanceContract.storageRootDigest(properties.getSecureLocalRoot());
            return () -> Optional.of(new SecureLocalAttachmentConformanceReportSource.Report(
                    SecureLocalAttachmentConformanceContract.contractVersion(),
                    SecureLocalAttachmentConformanceContract.currentAdapterBuildDigest(), engine.evidence().engineBuildDigest(),
                    SecureLocalAttachmentConformanceContract.bindingDigest(new StorageBindingRef(BINDING)),
                    rootDigest,
                    SecureLocalAttachmentConformanceContract.policyDigest(properties), "BOUNDED_MAGIC_V1", "magic-r1",
                    "STREAMING_ENGINE_V1", "public-r1", "work-definitions-r1",
                    AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                    engine.evidence().definitionsValidUntil(), Instant.now().plusSeconds(60),
                    SecureLocalAttachmentConformanceContract.requiredScenarios()));
        }
    }
}
