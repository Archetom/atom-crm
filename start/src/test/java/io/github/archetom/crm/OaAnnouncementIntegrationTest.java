package io.github.archetom.crm;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.OaAnnouncementAudienceSelectorRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.response.OaAnnouncementReadReceiptResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationClaim;
import io.github.archetom.crm.application.automation.OaAnnouncementPublicationPump;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.oa.OaAnnouncementPublicationStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.OperationLogPolicy;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B20c publication snapshots, recipient visibility, receipts, tenancy, and concurrency. */
@RequiresDatabase
class OaAnnouncementIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER = 888888L;
    private static final long TENANT = 999999L;
    private static final long DEPARTMENT_MEMBER = 777777L;
    private static final long EXPLICIT_MEMBER = 666666L;
    private static final long OUTSIDER = 555555L;
    private static final long LATER_MEMBER = 444444L;
    private static final long OTHER_OWNER = 333333L;
    private static final long OTHER_TENANT = 222222L;

    @Autowired
    private OaAnnouncementPublicationPump publicationPump;

    @Autowired
    private OaAnnouncementPublicationStore publicationStore;

    @Autowired
    private CommandServiceTemplate commandTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @DynamicPropertySource
    static void configurePublication(DynamicPropertyRegistry registry) {
        registry.add("atom.oa.announcements.publication.enabled", () -> "true");
        // Effects are invoked explicitly below; an automatic pulse must not race the assertions.
        registry.add("atom.scheduling.pulse-delay", () -> "PT1H");
    }

    @Test
    void finalSchemaUsesStatusAsTheOnlySoftDeletionRepresentation() {
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'oa_announcement' "
                        + "AND column_name = 'deleted_time'",
                Integer.class));
    }

    @Test
    void scheduleCreatesOneDurableGenerationAndDuePumpPublishesCurrentRecipients()
            throws Exception {
        bootstrap(OWNER, TENANT, "Scheduled announcement owner");
        seedMember(DEPARTMENT_MEMBER, "Scheduled recipient", null);
        OaAnnouncementResponse draft = create("Scheduled update", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER)));

        OaAnnouncementResponse scheduled = response(performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/schedule", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementScheduleRequest()
                                .setVersion(draft.getVersion())
                                .setScheduledPublishTime(OffsetDateTime.now(ZoneOffset.UTC)
                                        .plusMinutes(2)))))
                .andExpect(status().isOk()));
        assertEquals("SCHEDULED", scheduled.getStatus());
        assertEquals(OWNER, scheduled.getScheduledByActorId());
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM oa_announcement_publication_occurrence "
                        + "WHERE tenant_id = ? AND announcement_id = ? AND status = 'READY'",
                Integer.class, TENANT, scheduled.getId()));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime dueNow = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
        jdbc.update("UPDATE oa_announcement SET scheduled_publish_time = ? "
                        + "WHERE tenant_id = ? AND id = ?", dueNow, TENANT, scheduled.getId());
        jdbc.update("UPDATE oa_announcement_publication_occurrence "
                        + "SET due_time = ?, available_at = ?, deadline_at = ? "
                        + "WHERE tenant_id = ? AND announcement_id = ?",
                dueNow, dueNow, dueNow.plusDays(1), TENANT, scheduled.getId());

        publicationPump.runOnce();

        OaAnnouncementResponse published = response(performAs(OWNER, TENANT,
                get("/api/v1/oa/announcements/{id}", scheduled.getId()))
                .andExpect(status().isOk()));
        assertEquals("PUBLISHED", published.getStatus());
        assertEquals(1L, published.getRecipientTotal());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_announcement_publication_occurrence "
                        + "WHERE tenant_id = ? AND announcement_id = ? AND status = 'PUBLISHED'",
                Integer.class, TENANT, scheduled.getId()));
    }

    @Test
    void leaseTakeoverFencesStaleReplicaAndSuccessfulRetryClearsReason() throws Exception {
        bootstrap(OWNER, TENANT, "Lease recovery owner");
        seedMember(DEPARTMENT_MEMBER, "Lease recovery recipient", null);
        OaAnnouncementResponse scheduled = schedule(create("Lease recovery", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime now = databaseNow(jdbc);
        forceDue(jdbc, scheduled.getId(), now, now.plusDays(1), 0);

        UUID firstReplica = UUID.randomUUID();
        OaAnnouncementPublicationClaim first = claim(firstReplica);
        assertTrue(first.occurrence().isPresent());
        assertTrue(claim(UUID.randomUUID()).occurrence().isEmpty());

        jdbc.update("UPDATE oa_announcement_publication_occurrence "
                        + "SET lease_until = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                        + "WHERE id = ?",
                first.occurrence().orElseThrow().id());
        UUID takeoverReplica = UUID.randomUUID();
        OaAnnouncementPublicationClaim takeover = claim(takeoverReplica);
        assertTrue(takeover.occurrence().isPresent());
        assertTrue(takeover.occurrence().orElseThrow().leaseFence()
                > first.occurrence().orElseThrow().leaseFence());

        assertThrows(IllegalStateException.class, () -> publicationStore.retry(
                new TenantId(TENANT), first.occurrence().orElseThrow().id(), firstReplica,
                first.occurrence().orElseThrow().leaseFence(), publicationStore.currentUtc(),
                Duration.ofSeconds(1)));
        publicationStore.retry(new TenantId(TENANT), takeover.occurrence().orElseThrow().id(),
                takeoverReplica, takeover.occurrence().orElseThrow().leaseFence(),
                publicationStore.currentUtc(), Duration.ofSeconds(1));
        jdbc.update("UPDATE oa_announcement_publication_occurrence "
                        + "SET available_at = UTC_TIMESTAMP(6) WHERE id = ?",
                takeover.occurrence().orElseThrow().id());

        publicationPump.runOnce();

        assertEquals("PUBLISHED", rootStatus(jdbc, scheduled.getId()));
        assertEquals("PUBLISHED", occurrenceStatus(jdbc, scheduled.getId(), 1));
        assertNull(occurrenceReason(jdbc, scheduled.getId(), 1));
    }

    @Test
    void terminalRecoverySeparatesRetryExhaustionMisfireAndRevokedAuthority() throws Exception {
        bootstrap(OWNER, TENANT, "Terminal recovery owner");
        seedMember(DEPARTMENT_MEMBER, "Terminal recovery recipient", null);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        double scheduledBefore = metricCount("scheduled");
        OaAnnouncementResponse exhausted = schedule(create("Retry exhausted", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        assertEquals(scheduledBefore + 1D, metricCount("scheduled"));
        LocalDateTime now = databaseNow(jdbc);
        forceDue(jdbc, exhausted.getId(), now, now.plusDays(1), 5);
        double exhaustedBefore = metricCount("dead");
        publicationPump.runOnce();
        assertEquals(exhaustedBefore + 1D, metricCount("dead"));
        assertEquals("DRAFT", rootStatus(jdbc, exhausted.getId()));
        assertEquals("RETRY_EXHAUSTED", rootReason(jdbc, exhausted.getId()));
        assertEquals("DEAD", occurrenceStatus(jdbc, exhausted.getId(), 1));
        assertEquals("RETRY_EXHAUSTED", occurrenceReason(jdbc, exhausted.getId(), 1));

        OaAnnouncementResponse misfired = schedule(create("Misfired", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        now = databaseNow(jdbc);
        forceDue(jdbc, misfired.getId(), now.minusMinutes(2), now.minusSeconds(1), 0);
        double misfireBefore = metricCount("misfire");
        publicationPump.runOnce();
        assertEquals(misfireBefore + 1D, metricCount("misfire"));
        assertEquals("DRAFT", rootStatus(jdbc, misfired.getId()));
        assertEquals("MISFIRE_EXPIRED", rootReason(jdbc, misfired.getId()));
        assertEquals("DEAD", occurrenceStatus(jdbc, misfired.getId(), 1));
        assertEquals("MISFIRE_EXPIRED", occurrenceReason(jdbc, misfired.getId(), 1));

        OaAnnouncementResponse revoked = schedule(create("Revoked", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        now = databaseNow(jdbc);
        forceDue(jdbc, revoked.getId(), now, now.plusDays(1), 0);
        jdbc.update("UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?", TENANT, OWNER);
        publicationPump.runOnce();
        assertEquals("DRAFT", rootStatus(jdbc, revoked.getId()));
        assertEquals("SCHEDULING_MEMBER_UNAUTHORIZED", rootReason(jdbc, revoked.getId()));
        assertEquals("CANCELLED", occurrenceStatus(jdbc, revoked.getId(), 1));
        assertEquals("SCHEDULING_MEMBER_UNAUTHORIZED", occurrenceReason(jdbc, revoked.getId(), 1));
    }

    @Test
    void manualOccurrenceSettlementRecordsCancelledOnlyAfterCommittedTransition() throws Exception {
        bootstrap(OWNER, TENANT, "Manual publication settlement owner");
        seedMember(DEPARTMENT_MEMBER, "Manual publication settlement recipient", null);
        double cancelledBefore = metricCount("cancelled");

        OaAnnouncementResponse first = schedule(create("Rescheduled", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        OaAnnouncementResponse rescheduled = schedule(first);
        assertEquals(cancelledBefore + 1D, metricCount("cancelled"));
        assertEquals("CANCELLED", occurrenceStatus(new JdbcTemplate(dataSource), first.getId(), 1));

        OaAnnouncementResponse published = publish(rescheduled);
        assertEquals("PUBLISHED", published.getStatus());
        assertEquals(cancelledBefore + 2D, metricCount("cancelled"));
        assertEquals("CANCELLED", occurrenceStatus(new JdbcTemplate(dataSource), first.getId(), 2));

        OaAnnouncementResponse cancellable = schedule(create("Cancelled", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        OaAnnouncementResponse restored = response(performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/cancel-schedule", cancellable.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementVersionRequest()
                                .setVersion(cancellable.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals("DRAFT", restored.getStatus());
        assertEquals(cancelledBefore + 3D, metricCount("cancelled"));
        assertEquals("CANCELLED", occurrenceStatus(new JdbcTemplate(dataSource), cancellable.getId(), 1));

        OaAnnouncementResponse deletable = schedule(create("Deleted", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        performAs(OWNER, TENANT, delete("/api/v1/oa/announcements/{id}", deletable.getId())
                .param("version", deletable.getVersion().toString()))
                .andExpect(status().isOk());
        assertEquals(cancelledBefore + 4D, metricCount("cancelled"));
        assertEquals("CANCELLED", occurrenceStatus(new JdbcTemplate(dataSource), deletable.getId(), 1));
    }

    @Test
    void concurrentRescheduleAndImmediatePublishSettlesEveryGeneration() throws Exception {
        bootstrap(OWNER, TENANT, "Schedule publish race owner");
        seedMember(DEPARTMENT_MEMBER, "Schedule publish race recipient", null);
        OaAnnouncementResponse first = schedule(create("Schedule publish race", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));

        Callable<Integer> reschedule = () -> performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/schedule", first.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementScheduleRequest()
                                .setVersion(first.getVersion())
                                .setScheduledPublishTime(OffsetDateTime.now(ZoneOffset.UTC)
                                        .plusMinutes(10)))))
                .andReturn().getResponse().getStatus();
        Callable<Integer> publishNow = () -> performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/publish", first.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementVersionRequest()
                                .setVersion(first.getVersion()))))
                .andReturn().getResponse().getStatus();
        assertEquals(List.of(200, 409), concurrently(reschedule, publishNow).stream().sorted().toList());

        OaAnnouncementResponse current = response(performAs(OWNER, TENANT,
                get("/api/v1/oa/announcements/{id}", first.getId()))
                .andExpect(status().isOk()));
        if ("SCHEDULED".equals(current.getStatus())) {
            current = publish(current);
        }
        assertEquals("PUBLISHED", current.getStatus());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_announcement_publication_occurrence "
                        + "WHERE tenant_id = ? AND announcement_id = ? "
                        + "AND status IN ('READY', 'RETRY_WAIT', 'LEASED')",
                Integer.class, TENANT, first.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_announcement_publication_occurrence "
                        + "WHERE tenant_id = ? AND announcement_id = ? AND safe_reason = 'RETRY'",
                Integer.class, TENANT, first.getId()));
    }

    @Test
    void snapshotsRecipientsAndKeepsReadAndUnreadQueriesCurrentlyVisible() throws Exception {
        bootstrap(OWNER, TENANT, "Announcement owner");
        long root = seedDepartment("Root", null);
        long child = seedDepartment("Child", root);
        long other = seedDepartment("Other", null);
        assignDepartment(OWNER, root);
        seedMember(DEPARTMENT_MEMBER, "Department recipient", child);
        seedMember(EXPLICIT_MEMBER, "Explicit recipient", null);
        seedMember(OUTSIDER, "Outsider", other);
        grantRead(DEPARTMENT_MEMBER, EXPLICIT_MEMBER, OUTSIDER);

        OaAnnouncementResponse draft = create("Quarterly update", "Plain <b>text</b>",
                List.of(departmentAudience(root), memberAudience(EXPLICIT_MEMBER),
                        memberAudience(DEPARTMENT_MEMBER)));
        assertEquals("DRAFT", draft.getStatus());
        assertEquals(0L, draft.getRecipientTotal());
        performAs(OWNER, TENANT, get("/api/v1/oa/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].title").value("Quarterly update"))
                .andExpect(jsonPath("$.objectList[0].audience").doesNotExist());
        draft = response(performAs(OWNER, TENANT,
                put("/api/v1/oa/announcements/{id}", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementUpdateRequest()
                                .setTitle("Quarterly update")
                                .setBody("Plain <b>text</b>")
                                .setAudience(List.of(departmentAudience(root),
                                        memberAudience(EXPLICIT_MEMBER),
                                        memberAudience(DEPARTMENT_MEMBER)))
                                .setVersion(draft.getVersion()))))
                .andExpect(status().isOk()));
        performAs(DEPARTMENT_MEMBER, TENANT, get("/api/v1/oa/announcements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));

        OaAnnouncementResponse published = response(performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/publish", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementVersionRequest()
                                .setVersion(draft.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals("PUBLISHED", published.getStatus());
        assertEquals(3L, published.getRecipientTotal());
        assertEquals(0L, published.getReadTotal());
        performAs(OWNER, TENANT,
                put("/api/v1/oa/announcements/{id}", published.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementUpdateRequest()
                                .setTitle("Cannot edit")
                                .setBody("Body")
                                .setAudience(List.of(tenantAudience()))
                                .setVersion(published.getVersion()))))
                .andExpect(status().isUnprocessableEntity());

        performAs(DEPARTMENT_MEMBER, TENANT, get("/api/v1/oa/announcements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].body")
                        .value("Plain <b>text</b>"))
                .andExpect(jsonPath("$.objectList[0].read").value(false));
        performAs(OUTSIDER, TENANT, get("/api/v1/oa/announcements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
        performAs(DEPARTMENT_MEMBER, TENANT,
                get("/api/v1/oa/announcements/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        OaAnnouncementReadReceiptResponse first = readReceipt(performAs(
                DEPARTMENT_MEMBER, TENANT,
                post("/api/v1/oa/announcements/mine/{id}/read", published.getId()))
                .andExpect(status().isOk()));
        OaAnnouncementReadReceiptResponse retry = readReceipt(performAs(
                DEPARTMENT_MEMBER, TENANT,
                post("/api/v1/oa/announcements/mine/{id}/read", published.getId()))
                .andExpect(status().isOk()));
        assertEquals(first.getReadTime(), retry.getReadTime());
        performAs(DEPARTMENT_MEMBER, TENANT,
                get("/api/v1/oa/announcements/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        performAs(OWNER, TENANT,
                get("/api/v1/oa/announcements/{id}", published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientTotal").value(3))
                .andExpect(jsonPath("$.readTotal").value(1))
                .andExpect(jsonPath("$.audience.length()").value(3));

        assignDepartment(DEPARTMENT_MEMBER, other);
        seedMember(LATER_MEMBER, "Later department member", child);
        grantRead(LATER_MEMBER);
        performAs(DEPARTMENT_MEMBER, TENANT, get("/api/v1/oa/announcements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performAs(LATER_MEMBER, TENANT, get("/api/v1/oa/announcements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));

        OaAnnouncementResponse withdrawn = response(performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/withdraw", published.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementVersionRequest()
                                .setVersion(published.getVersion()))))
                .andExpect(status().isOk()));
        performAs(EXPLICIT_MEMBER, TENANT, get("/api/v1/oa/announcements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
        performAs(EXPLICIT_MEMBER, TENANT,
                get("/api/v1/oa/announcements/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        performAs(EXPLICIT_MEMBER, TENANT,
                post("/api/v1/oa/announcements/mine/{id}/read", published.getId()))
                .andExpect(status().isNotFound());
        performAs(OWNER, TENANT,
                delete("/api/v1/oa/announcements/{id}", published.getId())
                        .param("version", withdrawn.getVersion().toString()))
                .andExpect(status().isOk());
        performAs(OWNER, TENANT,
                get("/api/v1/oa/announcements/{id}", published.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void audienceCandidatesAreSearchableActiveTargetsForAnnouncementManagers() throws Exception {
        bootstrap(OWNER, TENANT, "Audience candidate manager");
        long departmentId = seedDepartment("Audience candidate department", null);
        seedMember(DEPARTMENT_MEMBER, "Audience candidate member", departmentId);

        performAs(OWNER, TENANT, get("/api/v1/oa/announcements/audience-candidates")
                .param("type", "MEMBER").param("keyword", "candidate member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(DEPARTMENT_MEMBER))
                .andExpect(jsonPath("$.objectList[0].displayName")
                        .value("Audience candidate member"));
        performAs(OWNER, TENANT, get("/api/v1/oa/announcements/audience-candidates")
                .param("type", "DEPARTMENT_SUBTREE").param("keyword", "candidate department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(departmentId))
                .andExpect(jsonPath("$.objectList[0].displayName")
                        .value("Audience candidate department"));
        performAs(DEPARTMENT_MEMBER, TENANT,
                get("/api/v1/oa/announcements/audience-candidates").param("type", "MEMBER"))
                .andExpect(status().isForbidden());

        OaAnnouncementResponse draft = create("Audience candidate draft", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER)));
        assertEquals("Audience candidate member", draft.getAudience().getFirst().getDisplayName());
        assertTrue(draft.getAllowedActions().containsAll(List.of("UPDATE", "PUBLISH", "SCHEDULE", "DELETE")));
    }

    @Test
    void rejectsCrossTenantSelectorsCallerOwnedFieldsAndUnicodeOverflow() throws Exception {
        bootstrap(OWNER, TENANT, "Tenant A owner");
        long ownDepartment = seedDepartment("Tenant A", null);
        bootstrap(OTHER_OWNER, OTHER_TENANT, "Tenant B owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) "
                        + "VALUES (?, 0, 'Tenant B', 'ACTIVE')",
                OTHER_TENANT);
        long crossDepartment = jdbc.queryForObject(
                "SELECT id FROM organization_department WHERE tenant_id = ? AND name = 'Tenant B'",
                Long.class, OTHER_TENANT);

        performAs(OWNER, TENANT, post("/api/v1/oa/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OaAnnouncementCreateRequest()
                        .setTitle("Cross department").setBody("Body")
                        .setAudience(List.of(departmentAudience(crossDepartment))))))
                .andExpect(status().isNotFound());
        performAs(OWNER, TENANT, post("/api/v1/oa/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OaAnnouncementCreateRequest()
                        .setTitle("Cross member").setBody("Body")
                        .setAudience(List.of(memberAudience(OTHER_OWNER))))))
                .andExpect(status().isNotFound());
        performAs(OWNER, TENANT, post("/api/v1/oa/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Identity injection\",\"body\":\"Body\","
                        + "\"tenantId\":2,\"audience\":[{\"type\":\"TENANT\"}]}"))
                .andExpect(status().isBadRequest());
        performAs(OWNER, TENANT, post("/api/v1/oa/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OaAnnouncementCreateRequest()
                        .setTitle("Overflow").setBody("x".repeat(20_001))
                        .setAudience(List.of(departmentAudience(ownDepartment))))))
                .andExpect(status().isUnprocessableEntity());

        OaAnnouncementResponse own = create("Tenant A only", "Body",
                List.of(departmentAudience(ownDepartment)));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO oa_announcement_recipient "
                        + "(tenant_id, announcement_id, actor_id, delivered_time) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))",
                TENANT, own.getId(), OTHER_OWNER));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO oa_announcement_read_receipt "
                        + "(tenant_id, announcement_id, actor_id, read_time) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))",
                TENANT, own.getId(), OWNER));
    }

    @Test
    void concurrentPublishUsesRootVersionAndLeavesOneDeduplicatedSnapshot()
            throws Exception {
        bootstrap(OWNER, TENANT, "Concurrent announcement owner");
        seedMember(DEPARTMENT_MEMBER, "Concurrent recipient", null);
        OaAnnouncementResponse draft = create("Concurrent", "Body",
                List.of(tenantAudience(), memberAudience(DEPARTMENT_MEMBER)));

        Callable<Integer> publish = () -> performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/publish", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementVersionRequest()
                                .setVersion(draft.getVersion()))))
                .andReturn().getResponse().getStatus();
        List<Integer> statuses = concurrently(publish, publish).stream().sorted().toList();
        assertEquals(List.of(200, 409), statuses);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT status FROM oa_announcement WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, draft.getId()));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_announcement_recipient "
                        + "WHERE tenant_id = ? AND announcement_id = ?",
                Integer.class, TENANT, draft.getId()));
    }

    @Test
    void concurrentReadRetriesKeepTheFirstReceiptTime() throws Exception {
        bootstrap(OWNER, TENANT, "Concurrent read owner");
        seedMember(DEPARTMENT_MEMBER, "Concurrent reader", null);
        grantRead(DEPARTMENT_MEMBER);
        OaAnnouncementResponse published = publish(create(
                "Concurrent read", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));

        Callable<OaAnnouncementReadReceiptResponse> markRead = () -> readReceipt(
                performAs(DEPARTMENT_MEMBER, TENANT,
                        post("/api/v1/oa/announcements/mine/{id}/read",
                                published.getId()))
                        .andExpect(status().isOk()));
        List<OaAnnouncementReadReceiptResponse> receipts =
                concurrently(markRead, markRead);

        assertEquals(receipts.get(0).getReadTime(), receipts.get(1).getReadTime());
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM oa_announcement_read_receipt "
                        + "WHERE tenant_id = ? AND announcement_id = ? AND actor_id = ?",
                Integer.class, TENANT, published.getId(), DEPARTMENT_MEMBER));
    }

    @Test
    void readUsesSharedAnnouncementLockInsteadOfSerializingRecipients()
            throws Exception {
        bootstrap(OWNER, TENANT, "Shared lock owner");
        seedMember(DEPARTMENT_MEMBER, "Shared lock reader", null);
        grantRead(DEPARTMENT_MEMBER);
        OaAnnouncementResponse published = publish(create(
                "Shared lock", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<Void> lockHolder = executor.submit(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT id FROM oa_announcement "
                                    + "WHERE tenant_id = ? AND id = ? FOR SHARE")) {
                connection.setAutoCommit(false);
                statement.setLong(1, TENANT);
                statement.setLong(2, published.getId());
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                }
                locked.countDown();
                assertTrue(release.await(15, TimeUnit.SECONDS));
                connection.commit();
            } finally {
                locked.countDown();
            }
            return null;
        });
        try {
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<Integer> read = executor.submit(() -> performAs(
                    DEPARTMENT_MEMBER, TENANT,
                    post("/api/v1/oa/announcements/mine/{id}/read",
                            published.getId()))
                    .andReturn().getResponse().getStatus());
            assertEquals(200, read.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            lockHolder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void readWaitsForConcurrentMembershipSuspensionAndThenDeniesIt()
            throws Exception {
        bootstrap(OWNER, TENANT, "Suspension race owner");
        seedMember(DEPARTMENT_MEMBER, "Suspension race reader", null);
        grantRead(DEPARTMENT_MEMBER);
        OaAnnouncementResponse published = publish(create(
                "Suspension race", "Body",
                List.of(memberAudience(DEPARTMENT_MEMBER))));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<Void> suspension = executor.submit(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE organization_membership SET status = 'SUSPENDED' "
                                    + "WHERE tenant_id = ? AND actor_id = ?")) {
                connection.setAutoCommit(false);
                statement.setLong(1, TENANT);
                statement.setLong(2, DEPARTMENT_MEMBER);
                assertEquals(1, statement.executeUpdate());
                updated.countDown();
                assertTrue(release.await(15, TimeUnit.SECONDS));
                connection.commit();
            } finally {
                updated.countDown();
            }
            return null;
        });
        Future<Integer> read = null;
        try {
            assertTrue(updated.await(5, TimeUnit.SECONDS));
            read = executor.submit(() -> performAs(DEPARTMENT_MEMBER, TENANT,
                    post("/api/v1/oa/announcements/mine/{id}/read",
                            published.getId()))
                    .andReturn().getResponse().getStatus());
            Future<Integer> pendingRead = read;
            assertThrows(TimeoutException.class,
                    () -> pendingRead.get(300, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
        try {
            suspension.get(5, TimeUnit.SECONDS);
            assertEquals(403, read.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void inactiveRecipientsCannotUseSnapshotAndReadAuthorityDoesNotManage() throws Exception {
        bootstrap(OWNER, TENANT, "Permission owner");
        seedMember(DEPARTMENT_MEMBER, "Read-only member", null);
        grantRead(DEPARTMENT_MEMBER);
        OaAnnouncementResponse published = response(performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/publish",
                        create("Permission", "Body",
                                List.of(memberAudience(DEPARTMENT_MEMBER))).getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk()));

        performAs(DEPARTMENT_MEMBER, TENANT, get("/api/v1/oa/announcements"))
                .andExpect(status().isForbidden());
        performAs(DEPARTMENT_MEMBER, TENANT, post("/api/v1/oa/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OaAnnouncementCreateRequest()
                        .setTitle("Forbidden").setBody("Body")
                        .setAudience(List.of(tenantAudience())))))
                .andExpect(status().isForbidden());
        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                TENANT, DEPARTMENT_MEMBER);
        performAs(DEPARTMENT_MEMBER, TENANT,
                get("/api/v1/oa/announcements/mine/{id}", published.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void httpLayerSeparatesAnnouncementReadAndWriteRoutes() throws Exception {
        bootstrap(OWNER, TENANT, "HTTP authorization owner");
        seedMember(OUTSIDER, "Unrelated member", null);

        performAs(OUTSIDER, TENANT,
                get("/api/v1/oa/announcements").param("page", "0"))
                .andExpect(status().isForbidden());
        performAs(OUTSIDER, TENANT,
                get("/api/v1/oa/announcements/mine").param("page", "0"))
                .andExpect(status().isForbidden());
        performAs(OUTSIDER, TENANT,
                post("/api/v1/oa/announcements/mine/{id}/read", 0))
                .andExpect(status().isForbidden());

        grantRead(OUTSIDER);
        performAs(OUTSIDER, TENANT, get("/api/v1/oa/announcements/mine"))
                .andExpect(status().isOk());
        performAs(OUTSIDER, TENANT,
                get("/api/v1/oa/announcements/unread-count"))
                .andExpect(status().isOk());
        performAs(OUTSIDER, TENANT,
                post("/api/v1/oa/announcements/mine/{id}/read", 999999))
                .andExpect(status().isNotFound());
        performAs(OUTSIDER, TENANT, get("/api/v1/oa/announcements"))
                .andExpect(status().isForbidden());

        seedMember(EXPLICIT_MEMBER, "Write-only member", null);
        grantAuthority("oa-announcements:write", EXPLICIT_MEMBER);
        performAs(EXPLICIT_MEMBER, TENANT, get("/api/v1/oa/announcements"))
                .andExpect(status().isOk());
        performAs(EXPLICIT_MEMBER, TENANT,
                get("/api/v1/oa/announcements/mine").param("page", "0"))
                .andExpect(status().isForbidden());
        performAs(EXPLICIT_MEMBER, TENANT,
                post("/api/v1/oa/announcements/mine/{id}/read", 0))
                .andExpect(status().isForbidden());
    }

    private OaAnnouncementResponse create(
            String title, String body,
            List<OaAnnouncementAudienceSelectorRequest> audience) throws Exception {
        return response(performAs(OWNER, TENANT, post("/api/v1/oa/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OaAnnouncementCreateRequest()
                        .setTitle(title).setBody(body).setAudience(audience))))
                .andExpect(status().isOk()));
    }

    private OaAnnouncementResponse schedule(OaAnnouncementResponse draft) throws Exception {
        return response(performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/schedule", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementScheduleRequest()
                                .setVersion(draft.getVersion())
                                .setScheduledPublishTime(OffsetDateTime.now(ZoneOffset.UTC)
                                        .plusMinutes(2)))))
                .andExpect(status().isOk()));
    }

    private OaAnnouncementResponse publish(OaAnnouncementResponse draft)
            throws Exception {
        return response(performAs(OWNER, TENANT,
                post("/api/v1/oa/announcements/{id}/publish", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaAnnouncementVersionRequest()
                                .setVersion(draft.getVersion()))))
                .andExpect(status().isOk()));
    }

    private OaAnnouncementPublicationClaim claim(UUID leaseOwner) {
        Result<OaAnnouncementPublicationClaim> result = commandTemplate.execute(
                UseCaseOperation.OA_ANNOUNCEMENT_PUBLICATION_EFFECT,
                new ServiceOperation<OaAnnouncementPublicationClaim>() {
                    @Override
                    public OaAnnouncementPublicationClaim execute() {
                        LocalDateTime now = publicationStore.currentUtc();
                        return publicationStore.claimDue(leaseOwner, now, Duration.ofMinutes(2), 5);
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        return result.getData();
    }

    private LocalDateTime databaseNow(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
    }

    private void forceDue(JdbcTemplate jdbc, long announcementId, LocalDateTime dueTime,
                          LocalDateTime deadlineAt, int attemptCount) {
        jdbc.update("UPDATE oa_announcement SET scheduled_publish_time = ? "
                        + "WHERE tenant_id = ? AND id = ?",
                dueTime, TENANT, announcementId);
        jdbc.update("UPDATE oa_announcement_publication_occurrence "
                        + "SET due_time = ?, available_at = ?, deadline_at = ?, attempt_count = ? "
                        + "WHERE tenant_id = ? AND announcement_id = ?",
                dueTime, dueTime, deadlineAt, attemptCount, TENANT, announcementId);
    }

    private String rootStatus(JdbcTemplate jdbc, long announcementId) {
        return jdbc.queryForObject("SELECT status FROM oa_announcement "
                        + "WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, announcementId);
    }

    private String rootReason(JdbcTemplate jdbc, long announcementId) {
        return jdbc.queryForObject("SELECT last_publication_safe_reason FROM oa_announcement "
                        + "WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, announcementId);
    }

    private String occurrenceStatus(JdbcTemplate jdbc, long announcementId, long generation) {
        return jdbc.queryForObject("SELECT status FROM oa_announcement_publication_occurrence "
                        + "WHERE tenant_id = ? AND announcement_id = ? AND publication_generation = ?",
                String.class, TENANT, announcementId, generation);
    }

    private String occurrenceReason(JdbcTemplate jdbc, long announcementId, long generation) {
        return jdbc.queryForObject("SELECT safe_reason FROM oa_announcement_publication_occurrence "
                        + "WHERE tenant_id = ? AND announcement_id = ? AND publication_generation = ?",
                String.class, TENANT, announcementId, generation);
    }

    private double metricCount(String outcome) {
        Counter counter = meterRegistry.find("atom.oa.announcements.publication")
                .tag("outcome", outcome).counter();
        return counter == null ? 0D : counter.count();
    }

    private OaAnnouncementResponse response(ResultActions action) throws Exception {
        return fromJson(action.andReturn().getResponse().getContentAsString(),
                OaAnnouncementResponse.class);
    }

    private OaAnnouncementReadReceiptResponse readReceipt(ResultActions action)
            throws Exception {
        return fromJson(action.andReturn().getResponse().getContentAsString(),
                OaAnnouncementReadReceiptResponse.class);
    }

    private OaAnnouncementAudienceSelectorRequest tenantAudience() {
        return new OaAnnouncementAudienceSelectorRequest().setType("TENANT");
    }

    private OaAnnouncementAudienceSelectorRequest departmentAudience(long departmentId) {
        return new OaAnnouncementAudienceSelectorRequest()
                .setType("DEPARTMENT_SUBTREE").setDepartmentId(departmentId);
    }

    private OaAnnouncementAudienceSelectorRequest memberAudience(long actorId) {
        return new OaAnnouncementAudienceSelectorRequest()
                .setType("MEMBER").setMemberActorId(actorId);
    }

    private long seedDepartment(String name, Long parentId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                TENANT, parentId == null ? 0 : parentId, name);
        return jdbc.queryForObject(
                "SELECT id FROM organization_department WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT, name);
    }

    private void seedMember(long actorId, String displayName, Long departmentId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                + "VALUES (?, ?)", actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                TENANT, actorId, departmentId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT);
    }

    private void assignDepartment(long actorId, long departmentId) {
        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET department_id = ? "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                departmentId, TENANT, actorId);
    }

    private void grantRead(long... actorIds) throws Exception {
        grantAuthority("oa-announcements:read", actorIds);
    }

    private void grantAuthority(String authorityCode, long... actorIds) throws Exception {
        TenantRoleResponse role = fromJson(performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleCreateRequest()
                                .setName("Announcement " + authorityCode + " " + actorIds[0])
                                .setGrants(List.of(new RoleGrantRequest()
                                        .setAuthorityCode(authorityCode))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
        performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles/{roleId}/assignments", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest().setActorIds(
                                java.util.Arrays.stream(actorIds).boxed().toList()))))
                .andExpect(status().isOk());
    }

    private void bootstrap(long actorId, long tenantId, String name) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(name))))
                .andExpect(status().isOk());
    }

    private <T> List<T> concurrently(Callable<T> first, Callable<T> second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> firstResult = executor.submit(first);
            Future<T> secondResult = executor.submit(second);
            return List.of(firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request)
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
        executeSql("DELETE FROM oa_announcement_read_receipt");
        executeSql("DELETE FROM oa_announcement_recipient");
        executeSql("DELETE FROM oa_announcement_publication_occurrence");
        executeSql("DELETE FROM oa_announcement_audience_member");
        executeSql("DELETE FROM oa_announcement_audience_department");
        executeSql("DELETE FROM oa_announcement");
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
