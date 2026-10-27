package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementAudienceSelectorRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementResponse;
import io.github.archetom.crm.application.service.impl.OaAnnouncementPublicationCoordinator;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentInspectionWorker;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentPublicationWorker;
import io.github.archetom.crm.infra.external.attachment.SecureLocalAttachmentConformanceContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B29c MySQL acceptance: V83 parent proof, recipient streaming, freeze ceiling and lock contention. */
@RequiresDatabase
@Import(B24aWorkTaskAttachmentIntegrationTest.PositiveAttachmentProfile.class)
class B29cOaAnnouncementAttachmentIntegrationTest extends BaseIntegrationTest {
    private static final long TENANT = 9_290_831L;
    private static final long OWNER = TENANT;
    private static final long RECIPIENT = TENANT + 1;
    private static final long FOREIGN_TENANT = TENANT + 10;
    private static final long FOREIGN_READER = FOREIGN_TENANT + 1;

    @Autowired private AttachmentInspectionWorker inspectionWorker;
    @Autowired private AttachmentPublicationWorker publicationWorker;
    @MockitoSpyBean private OaAnnouncementPublicationCoordinator publicationCoordinator;

    @DynamicPropertySource
    static void attachmentProfile(DynamicPropertyRegistry registry) {
        registry.add("atom.files.enabled", () -> "true");
        registry.add("atom.files.workers.enabled", () -> "false");
        registry.add("atom.oa.announcements.publication.enabled", () -> "true");
        registry.add("atom.files.storage-profile", SecureLocalAttachmentConformanceContract::profile);
        registry.add("atom.files.secure-local-root", () -> B24aWorkTaskAttachmentIntegrationTest.ROOT.toString());
        registry.add("atom.files.binding-base64", () -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(B24aWorkTaskAttachmentIntegrationTest.BINDING));
        registry.add("atom.files.inspection.detector-profile", () -> "BOUNDED_MAGIC_V1");
        registry.add("atom.files.inspection.scanner-profile", () -> "STREAMING_ENGINE_V1");
        registry.add("atom.files.inspection.detector-catalog-revision", () -> "magic-r1");
        registry.add("atom.files.inspection.allowlist-revision", () -> "public-r1");
        registry.add("atom.files.inspection.definition-max-age", () -> "PT30S");
        registry.add("atom.files.policy.max-file-bytes", () -> "1048576");
        registry.add("atom.files.policy.max-ready-bytes", () -> "2097152");
        registry.add("atom.files.policy.max-quarantine-bytes", () -> "2097152");
        registry.add("atom.files.policy.max-pending-intents", () -> "128");
        registry.add("atom.files.policy.max-active-uploads", () -> "2");
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
    void v83RecipientDownloadAndFreezeBoundariesAreTenantIsolated() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedReader(jdbc, TENANT, RECIPIENT, "B29c recipient");
        bootstrap(FOREIGN_READER, FOREIGN_TENANT, "B29c foreign owner");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '83' AND success = 1", Integer.class));

        OaAnnouncementResponse announcement = createAnnouncement("B29c recipient download");
        String root = root(announcement.getId());
        byte[] bytes = "%PDF-1.7\\nB29c recipient".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        AttachmentUploadIntentResponse intent = fromJson(as(OWNER, TENANT, post(root + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new AttachmentUploadIntentCreateRequest()
                        .setDisplayName("recipient.pdf").setDeclaredMediaType("application/pdf")
                        .setExpectedBytes((long) bytes.length))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
        as(OWNER, TENANT, put(root + "/{fileId}/content", intent.getFileId())
                .param("version", intent.getVersion().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes)).andExpect(status().isAccepted());
        inspectionWorker.runOnce();
        publicationWorker.runOnce();
        OaAnnouncementResponse published = fromJson(as(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/publish", announcement.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementVersionRequest().setVersion(announcement.getVersion()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OaAnnouncementResponse.class);
        assertEquals("PUBLISHED", published.getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_announcement_recipient"
                + " WHERE tenant_id = ? AND announcement_id = ? AND actor_id = ?", Integer.class,
                TENANT, announcement.getId(), RECIPIENT));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_relation"
                + " WHERE tenant_id = ? AND file_id = ? AND oa_announcement_id = ?", Integer.class,
                TENANT, intent.getFileId(), announcement.getId()));
        assertEquals(bytes.length, completeAsync(as(RECIPIENT, TENANT,
                get(root + "/{fileId}/content", intent.getFileId())).andExpect(status().isOk()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray().length);
        as(FOREIGN_READER, FOREIGN_TENANT, get(root + "/{fileId}/content", intent.getFileId()))
                .andExpect(status().isNotFound());

        OaAnnouncementResponse overLimit = createAnnouncement("B29c 101-file ceiling");
        for (int index = 0; index < 101; index++) {
            long fileId = availableFile(jdbc, TENANT, OWNER, "limit-" + index + ".pdf");
            jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, oa_announcement_id)"
                    + " VALUES (?, ?, 'OA_ANNOUNCEMENT', ?)", TENANT, fileId, overLimit.getId());
        }
        as(OWNER, TENANT, post("/api/v1/oa/announcements/{id}/schedule", overLimit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaAnnouncementScheduleRequest()
                        .setVersion(overLimit.getVersion()).setScheduledPublishTime(
                                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)))))
                .andExpect(status().isUnprocessableEntity());
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM oa_announcement"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, overLimit.getId()));

        OaAnnouncementResponse concurrent = createAnnouncement("B29c concurrent freeze");
        long concurrentFile = availableFile(jdbc, TENANT, OWNER, "frozen.pdf");
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, oa_announcement_id)"
                + " VALUES (?, ?, 'OA_ANNOUNCEMENT', ?)", TENANT, concurrentFile, concurrent.getId());
        assertProductionRescheduleAndCancellationSerialize(concurrent, concurrentFile);
    }

    /**
     * Holds a real reschedule after it has acquired tenant -> announcement -> old occurrence
     * -> relation/file locks. A concurrent HTTP cancellation must wait, then lose its stale
     * version after the replacement generation commits.
     */
    private void assertProductionRescheduleAndCancellationSerialize(
            OaAnnouncementResponse announcement, long fileId) throws Exception {
        OaAnnouncementResponse initiallyScheduled = schedule(announcement, announcement.getVersion(), 1);
        CountDownLatch attachmentFreezeReached = new CountDownLatch(1);
        CountDownLatch releaseReschedule = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object outcome = invocation.callRealMethod();
            attachmentFreezeReached.countDown();
            if (!releaseReschedule.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out while holding B29c production freeze");
            }
            return outcome;
        }).when(publicationCoordinator).validateAttachmentSet(any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch cancellationSubmitted = new CountDownLatch(1);
        try {
            Future<Integer> reschedule = executor.submit(() -> as(OWNER, TENANT,
                    post("/api/v1/oa/announcements/{id}/schedule", announcement.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaAnnouncementScheduleRequest()
                                    .setVersion(initiallyScheduled.getVersion()).setScheduledPublishTime(
                                            OffsetDateTime.now(ZoneOffset.UTC).plusDays(2)))))
                    .andReturn().getResponse().getStatus());
            assertTrue(attachmentFreezeReached.await(5, TimeUnit.SECONDS));
            Future<Integer> cancellation = executor.submit(() -> {
                cancellationSubmitted.countDown();
                return as(OWNER, TENANT, post("/api/v1/oa/announcements/{id}/cancel-schedule", announcement.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaAnnouncementVersionRequest()
                                .setVersion(initiallyScheduled.getVersion()))))
                        .andReturn().getResponse().getStatus();
            });
            assertTrue(cancellationSubmitted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> cancellation.get(200, TimeUnit.MILLISECONDS));
            releaseReschedule.countDown();
            assertEquals(200, reschedule.get(5, TimeUnit.SECONDS));
            assertEquals(409, cancellation.get(5, TimeUnit.SECONDS));
        } finally {
            releaseReschedule.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            reset(publicationCoordinator);
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("SCHEDULED", jdbc.queryForObject("SELECT status FROM oa_announcement"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, announcement.getId()));
        assertEquals(2L, jdbc.queryForObject("SELECT publication_generation FROM oa_announcement"
                + " WHERE tenant_id = ? AND id = ?", Long.class, TENANT, announcement.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_announcement_publication_occurrence"
                + " WHERE tenant_id = ? AND announcement_id = ? AND publication_generation = 1"
                + " AND status = 'CANCELLED' AND safe_reason = 'SUPERSEDED'", Integer.class,
                TENANT, announcement.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_announcement_publication_occurrence"
                + " WHERE tenant_id = ? AND announcement_id = ? AND publication_generation = 2"
                + " AND status = 'READY'", Integer.class, TENANT, announcement.getId()));
        assertEquals("AVAILABLE", jdbc.queryForObject("SELECT publication_state FROM crm_attachment_file"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, fileId));
    }

    private OaAnnouncementResponse schedule(OaAnnouncementResponse announcement, long version, int days) throws Exception {
        var response = as(OWNER, TENANT, post("/api/v1/oa/announcements/{id}/schedule", announcement.getId())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaAnnouncementScheduleRequest()
                        .setVersion(version).setScheduledPublishTime(
                                OffsetDateTime.now(ZoneOffset.UTC).plusDays(days)))))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        return fromJson(response.getContentAsString(), OaAnnouncementResponse.class);
    }

    private OaAnnouncementResponse createAnnouncement(String title) throws Exception {
        return fromJson(as(OWNER, TENANT, post("/api/v1/oa/announcements")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaAnnouncementCreateRequest()
                        .setTitle(title).setBody("B29c body")
                        .setAudience(List.of(new OaAnnouncementAudienceSelectorRequest().setType("TENANT"))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OaAnnouncementResponse.class);
    }

    private long availableFile(JdbcTemplate jdbc, long tenantId, long actorId, String name) {
        jdbc.update("INSERT INTO crm_attachment_file (tenant_id, uploader_actor_id, display_name, declared_media_type,"
                        + " expected_bytes, byte_state, inspection_state, publication_state, actual_bytes, media_type,"
                        + " available_time) VALUES (?, ?, ?, 'application/pdf', 4, 'STORED', 'CLEAN', 'AVAILABLE',"
                        + " 4, 'application/pdf', UTC_TIMESTAMP(6))",
                tenantId, actorId, name);
        return jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?", Long.class, tenantId);
    }

    private void bootstrap() throws Exception {
        bootstrap(OWNER, TENANT, "B29c owner");
    }

    private void bootstrap(long actorId, long tenantId, String name) throws Exception {
        as(actorId, tenantId, post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(name))))
                .andExpect(status().isOk());
    }

    private void seedReader(JdbcTemplate jdbc, long tenantId, long actorId, String name) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)", actorId, name);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                tenantId, actorId);
        seedRole(jdbc, tenantId, actorId, name + " role");
    }

    private void seedRole(JdbcTemplate jdbc, long tenantId, long actorId, String name) {
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')", tenantId, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, tenantId, name);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                tenantId, actorId, roleId);
        jdbc.update("INSERT INTO authorization_role_grant (tenant_id, role_id, authority_code)"
                + " VALUES (?, ?, 'files:read'), (?, ?, 'oa-announcements:read')",
                tenantId, roleId, tenantId, roleId);
    }

    private String root(long announcementId) {
        return "/api/v1/attachment-targets/oa-announcement/" + announcementId + "/attachments";
    }

    private org.springframework.test.web.servlet.ResultActions as(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId).header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void clearTestData() {
        for (long tenantId : List.of(TENANT, FOREIGN_TENANT)) {
            executeSql("DELETE FROM crm_notification_delivery WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM oa_announcement_read_receipt WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM oa_announcement_recipient WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM oa_announcement_publication_occurrence WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM oa_announcement_audience_member WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM oa_announcement_audience_department WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM crm_attachment_tenant_usage WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM oa_announcement WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM authorization_role WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM organization_membership_request WHERE target_tenant_id = " + tenantId);
            executeSql("DELETE FROM organization_membership WHERE tenant_id = " + tenantId);
            executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + tenantId);
        }
        executeSql("DELETE FROM organization_member_profile WHERE actor_id IN (" + OWNER + ", "
                + RECIPIENT + ", " + FOREIGN_READER + ")");
    }
}
