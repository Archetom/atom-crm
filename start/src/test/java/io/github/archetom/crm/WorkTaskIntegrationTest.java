package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractProductLineRequest;
import io.github.archetom.crm.api.dto.request.InvoiceCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantReplaceRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskAssigneeUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskBusinessRecordUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskFollowerUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskQueryRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskVersionRequest;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;
import io.github.archetom.crm.api.dto.request.WorklistItemAcknowledgementRequest;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.ContractResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.InvoiceResponse;
import io.github.archetom.crm.api.dto.response.LeadResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.api.dto.response.ProductResponse;
import io.github.archetom.crm.api.dto.response.ReceivablePlanResponse;
import io.github.archetom.crm.api.dto.response.ReceivableResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskCommentResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskResponse;
import io.github.archetom.crm.api.dto.response.WorklistItemResponse;
import io.github.archetom.crm.api.dto.response.WorklistPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B21 task core, collaboration, CRM relationship, and MySQL arbitration coverage. */
@RequiresDatabase
class WorkTaskIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER = 888888L;
    private static final long TENANT = 999999L;
    private static final long ASSIGNEE = 700001L;
    private static final long OUTSIDER = 700002L;
    private static final long MANAGER = 700003L;
    private static final long MANAGER_WITHOUT_WRITE = 700004L;
    private static final long WORKLIST_ONLY = 700005L;
    private static final long RELATION_READER = 700006L;
    private static final long OTHER_OWNER = 777777L;
    private static final long OTHER_TENANT = 777001L;
    private static final long CROSS_TENANT_MEMBER = 700099L;

    private static final List<RelationshipAuthority> RELATIONSHIP_AUTHORITIES = List.of(
            new RelationshipAuthority("CUSTOMER", "customers:read", "customers:write"),
            new RelationshipAuthority("CONTACT", "contacts:read", "contacts:write"),
            new RelationshipAuthority("LEAD", "leads:read", "leads:write"),
            new RelationshipAuthority(
                    "OPPORTUNITY", "opportunities:read", "opportunities:write"),
            new RelationshipAuthority("CONTRACT", "contracts:read", "contracts:write"),
            new RelationshipAuthority("INVOICE", "invoices:read", "invoices:write"),
            new RelationshipAuthority(
                    "RECEIVABLE", "receivables:read", "receivables:write"),
            new RelationshipAuthority("RECEIVABLE_PLAN",
                    "receivable-plans:read", "receivable-plans:write"));

    @Test
    void lifecycleKeepsFirstStartAndProjectionSemantics() throws Exception {
        bootstrap(OWNER, TENANT, "Task owner");
        seedMember(ASSIGNEE, "Task assignee");
        grant(ASSIGNEE, "Assignee role", "work-tasks:read", "work-tasks:write");

        WorkTaskResponse task = create("Initial task", ASSIGNEE,
                LocalDateTime.now(Clock.systemUTC()).minusHours(1));
        assertEquals("TODO", task.getStatus());
        assertEquals(0L, task.getWorklistProjectionVersion());
        assertTrue(task.isOverdue());

        performAs(ASSIGNEE, TENANT,
                put("/api/v1/work/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskUpdateRequest()
                                .setTitle("Assignee cannot edit")
                                .setPriority("NORMAL").setVersion(task.getVersion()))))
                .andExpect(status().isForbidden());

        WorkTaskResponse descriptionOnly = response(performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskUpdateRequest()
                                .setTitle(task.getTitle())
                                .setDescription("Plain text description")
                                .setPriority(task.getPriority())
                                .setDueTime(task.getDueTime())
                                .setVersion(task.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals(0L, descriptionOnly.getWorklistProjectionVersion());

        WorkTaskResponse started = response(performAs(ASSIGNEE, TENANT,
                post("/api/v1/work/tasks/{id}/start", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(descriptionOnly.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals("IN_PROGRESS", started.getStatus());
        LocalDateTime firstStarted = started.getFirstStartedTime();

        WorkTaskResponse todo = response(performAs(ASSIGNEE, TENANT,
                post("/api/v1/work/tasks/{id}/return-to-todo", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(started.getVersion()))))
                .andExpect(status().isOk()));
        WorkTaskResponse restarted = response(performAs(ASSIGNEE, TENANT,
                post("/api/v1/work/tasks/{id}/start", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(todo.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals(firstStarted, restarted.getFirstStartedTime());

        performAs(OWNER, TENANT,
                delete("/api/v1/work/tasks/{id}", task.getId())
                        .param("version", restarted.getVersion().toString()))
                .andExpect(status().isUnprocessableEntity());

        WorkTaskResponse done = response(performAs(ASSIGNEE, TENANT,
                post("/api/v1/work/tasks/{id}/complete", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(restarted.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals("DONE", done.getStatus());
        assertTrue(done.getCompletedTime() != null);
        performAs(ASSIGNEE, TENANT,
                post("/api/v1/work/tasks/{id}/start", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(done.getVersion()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void visibilityAndCapabilitiesFailClosedForEveryParticipantRole() throws Exception {
        bootstrap(OWNER, TENANT, "Visibility owner");
        seedMember(ASSIGNEE, "Visible assignee");
        seedMember(OUTSIDER, "Ordinary outsider");
        seedMember(MANAGER, "Task manager");
        seedMember(MANAGER_WITHOUT_WRITE, "Read manager");
        seedMember(WORKLIST_ONLY, "Worklist-only member");
        grant(ASSIGNEE, "Assignee visibility", "work-tasks:read", "work-tasks:write");
        grant(OUTSIDER, "Outsider visibility", "work-tasks:read", "work-tasks:write");
        grant(MANAGER, "Task manager role",
                "work-tasks:read", "work-tasks:write", "work-tasks:manage");
        grant(MANAGER_WITHOUT_WRITE, "Read manager role",
                "work-tasks:read", "work-tasks:manage");
        grant(WORKLIST_ONLY, "Worklist-only role", "worklist:read");
        WorkTaskResponse task = create("Visibility task", ASSIGNEE, null);

        performAs(ASSIGNEE, TENANT, get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isOk());
        queryAs(ASSIGNEE, new WorkTaskQueryRequest().setParticipantRole("ASSIGNEE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performAs(OUTSIDER, TENANT, get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isNotFound());
        queryAs(OUTSIDER, new WorkTaskQueryRequest())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));

        queryAs(MANAGER, new WorkTaskQueryRequest().setStatuses(List.of("todo"))
                .setPriorities(List.of("normal")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        WorkTaskResponse changed = response(performAs(MANAGER, TENANT,
                put("/api/v1/work/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskUpdateRequest()
                                .setTitle("Managed task").setPriority("URGENT")
                                .setVersion(task.getVersion()))))
                .andExpect(status().isOk()));

        performAs(MANAGER_WITHOUT_WRITE, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isOk());
        performAs(MANAGER_WITHOUT_WRITE, TENANT,
                post("/api/v1/work/tasks/{id}/cancel", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(changed.getVersion()))))
                .andExpect(status().isForbidden());
        performAs(WORKLIST_ONLY, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isForbidden());

        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?", TENANT, ASSIGNEE);
        performAs(ASSIGNEE, TENANT, get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isForbidden());

        bootstrap(OTHER_OWNER, OTHER_TENANT, "Other task tenant");
        performAs(OTHER_OWNER, OTHER_TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void worklistProjectsOnlyCurrentAssigneeAndAcknowledgesExactProjectionFingerprint()
            throws Exception {
        bootstrap(OWNER, TENANT, "Work task worklist owner");
        seedMember(ASSIGNEE, "Worklist assignee");
        seedMember(OUTSIDER, "Task-read-only assignee");
        seedMember(MANAGER, "Work task manager and follower");
        seedMember(WORKLIST_ONLY, "Worklist-only assignee");
        grant(ASSIGNEE, "Work task reminder participant", "worklist:read",
                "work-tasks:read", "work-tasks:write");
        grant(OUTSIDER, "Task read only", "work-tasks:read");
        grant(MANAGER, "Worklist task manager", "worklist:read", "work-tasks:read",
                "work-tasks:manage");
        grant(WORKLIST_ONLY, "Worklist only", "worklist:read");
        LocalDate day = LocalDate.of(2026, 7, 18);

        WorkTaskResponse visible = create("Visible Work task", ASSIGNEE, day.atTime(9, 0));
        create("No due Work task", ASSIGNEE, null);
        WorkTaskResponse finished = create("Finished Work task", ASSIGNEE, day.atTime(9, 5));
        response(performAs(ASSIGNEE, TENANT,
                post("/api/v1/work/tasks/{id}/complete", finished.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(finished.getVersion()))))
                .andExpect(status().isOk()));
        response(performAs(ASSIGNEE, TENANT, post("/api/v1/work/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskCreateRequest().setTitle("Creator-only Work task")
                        .setAssigneeActorId(OWNER).setDueTime(day.atTime(9, 10)))))
                .andExpect(status().isOk()));
        WorkTaskResponse worklistOnly = create(
                "Missing task read", WORKLIST_ONLY, day.atTime(9, 15));
        create("Task read without worklist", OUTSIDER, day.atTime(9, 25));

        WorklistPageResponse initial = worklist(ASSIGNEE, day);
        List<WorklistItemResponse> taskItems = initial.getItems().stream()
                .filter(value -> "WORK_TASK".equals(value.getKind())).toList();
        assertEquals(1, taskItems.size());
        WorklistItemResponse reminder = taskItems.getFirst();
        assertEquals(visible.getId(), reminder.getSourceId());
        assertEquals(visible.getId(), reminder.getSubjectId());
        assertEquals("WORK_TASK", reminder.getSubjectType());
        assertEquals(visible.getWorklistProjectionVersion(), reminder.getSourceVersion());

        assertTrue(worklist(WORKLIST_ONLY, day).getItems().stream().noneMatch(value ->
                value.getSourceId().equals(worklistOnly.getId())));
        assertTrue(worklist(MANAGER, day).getItems().stream()
                .noneMatch(value -> "WORK_TASK".equals(value.getKind())));
        performAs(OUTSIDER, TENANT, get("/api/v1/worklist").param("date", day.toString()))
                .andExpect(status().isForbidden());

        performAs(ASSIGNEE, TENANT, post("/api/v1/worklist/acknowledgements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorklistAcknowledgementRequest().setDate(day).setItems(List.of(
                        acknowledgement(reminder)))))).andExpect(status().isOk());
        assertTrue(worklist(ASSIGNEE, day).getItems().stream().noneMatch(value ->
                value.getSourceId().equals(visible.getId())));
        assertEquals("TODO", new JdbcTemplate(dataSource).queryForObject(
                "SELECT status FROM crm_work_task WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, visible.getId()));

        performAs(ASSIGNEE, TENANT,
                post("/api/v1/work/tasks/{id}/comments", visible.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCommentCreateRequest()
                                .setContent("Reminder acknowledgement note"))))
                .andExpect(status().isOk());
        WorkTaskResponse followed = response(performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}/followers", visible.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskFollowerUpdateRequest()
                                .setFollowerActorIds(List.of(MANAGER))
                                .setVersion(visible.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals(visible.getWorklistProjectionVersion(),
                followed.getWorklistProjectionVersion());
        assertTrue(worklist(ASSIGNEE, day).getItems().stream().noneMatch(value ->
                value.getSourceId().equals(visible.getId())));
        assertTrue(worklist(MANAGER, day).getItems().stream().noneMatch(value ->
                value.getSourceId().equals(visible.getId())));

        WorkTaskResponse renamed = response(performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}", visible.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskUpdateRequest()
                                .setTitle("Renamed Work task")
                                .setDescription(followed.getDescription())
                                .setPriority(followed.getPriority())
                                .setDueTime(followed.getDueTime())
                                .setVersion(followed.getVersion()))))
                .andExpect(status().isOk()));
        WorklistItemResponse changed = worklist(ASSIGNEE, day).getItems().stream()
                .filter(value -> value.getSourceId().equals(visible.getId()))
                .findFirst().orElseThrow();
        assertEquals(renamed.getWorklistProjectionVersion(), changed.getSourceVersion());
        assertTrue(changed.getSourceVersion() > reminder.getSourceVersion());

        performAs(ASSIGNEE, TENANT, post("/api/v1/worklist/acknowledgements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorklistAcknowledgementRequest().setDate(day)
                        .setKind("WORK_TASK"))))
                .andExpect(status().isOk());
        assertTrue(worklist(ASSIGNEE, day).getItems().stream()
                .noneMatch(value -> "WORK_TASK".equals(value.getKind())));
        assertEquals("TODO", new JdbcTemplate(dataSource).queryForObject(
                "SELECT status FROM crm_work_task WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, visible.getId()));

        response(performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}/assignee", visible.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskAssigneeUpdateRequest()
                                .setAssigneeActorId(OWNER).setVersion(renamed.getVersion()))))
                .andExpect(status().isOk()));
        assertTrue(worklist(ASSIGNEE, day).getItems().stream().noneMatch(value ->
                value.getSourceId().equals(visible.getId())));
    }

    @Test
    void reassignmentDeleteConcurrencyAndDatabaseConstraintsAreAuthoritative()
            throws Exception {
        bootstrap(OWNER, TENANT, "Concurrency owner");
        seedMember(ASSIGNEE, "Concurrent assignee");
        seedMember(OUTSIDER, "Next assignee");
        grant(ASSIGNEE, "Concurrent assignee role",
                "work-tasks:read", "work-tasks:write");
        grant(OUTSIDER, "Next assignee role",
                "work-tasks:read", "work-tasks:write");
        WorkTaskResponse task = create("Concurrent task", ASSIGNEE, null);

        WorkTaskResponse reassigned = response(performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}/assignee", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskAssigneeUpdateRequest()
                                .setAssigneeActorId(OUTSIDER)
                                .setVersion(task.getVersion()))))
                .andExpect(status().isOk()));
        performAs(ASSIGNEE, TENANT, get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isNotFound());
        performAs(OUTSIDER, TENANT, get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isOk());

        List<Integer> statuses = concurrently(
                () -> completeStatus(OUTSIDER, reassigned),
                () -> completeStatus(OUTSIDER, reassigned));
        assertEquals(Set.of(200, 409), Set.copyOf(statuses));
        assertEquals("DONE", new JdbcTemplate(dataSource).queryForObject(
                "SELECT status FROM crm_work_task WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, task.getId()));

        WorkTaskResponse structuralRace = create("Structural race", OWNER, null);
        List<Integer> structuralStatuses = concurrently(
                () -> updateStatus(structuralRace),
                () -> reassignStatus(structuralRace));
        assertEquals(Set.of(200, 409), Set.copyOf(structuralStatuses));
        var structuralRow = new JdbcTemplate(dataSource).queryForMap(
                "SELECT title, current_assignee_actor_id FROM crm_work_task "
                        + "WHERE tenant_id = ? AND id = ?",
                TENANT, structuralRace.getId());
        long structuralAssignee = ((Number) structuralRow.get(
                "current_assignee_actor_id")).longValue();
        boolean editWon = "Edited structural race".equals(structuralRow.get("title"))
                && structuralAssignee == OWNER;
        boolean reassignmentWon = structuralRace.getTitle().equals(structuralRow.get("title"))
                && structuralAssignee == ASSIGNEE;
        assertTrue(editWon || reassignmentWon);

        WorkTaskResponse cancelRace = create("Cancel race", OWNER, null);
        List<Integer> cancelStatuses = concurrently(
                () -> cancelStatus(cancelRace), () -> cancelStatus(cancelRace));
        assertEquals(Set.of(200, 409), Set.copyOf(cancelStatuses));
        assertEquals("CANCELLED", new JdbcTemplate(dataSource).queryForObject(
                "SELECT status FROM crm_work_task WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, cancelRace.getId()));

        WorkTaskResponse deletable = create("Delete me", OWNER, null);
        performAs(OWNER, TENANT,
                delete("/api/v1/work/tasks/{id}", deletable.getId())
                        .param("version", deletable.getVersion().toString()))
                .andExpect(status().isOk());
        performAs(OWNER, TENANT, get("/api/v1/work/tasks/{id}", deletable.getId()))
                .andExpect(status().isNotFound());

        bootstrap(OTHER_OWNER, OTHER_TENANT, "Foreign assignee tenant");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT @@FOREIGN_KEY_CHECKS", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND table_name = 'crm_work_task'",
                Integer.class));
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                + "VALUES (?, 'Cross-tenant member')", CROSS_TENANT_MEMBER);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                + "VALUES (?, ?, 'ACTIVE')", OTHER_TENANT, CROSS_TENANT_MEMBER);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_membership "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                Integer.class, TENANT, CROSS_TENANT_MEMBER));
        WorkTaskResponse crossTenantTarget = create("Cross-tenant target", OWNER, null);
        performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}/assignee", crossTenantTarget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskAssigneeUpdateRequest()
                                .setAssigneeActorId(CROSS_TENANT_MEMBER)
                                .setVersion(crossTenantTarget.getVersion()))))
                .andExpect(status().isBadRequest());
        assertDatabaseRejects(
                "DELETE FROM organization_membership "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                TENANT, OUTSIDER);
        assertDatabaseRejects(
                "INSERT INTO crm_work_task "
                        + "(tenant_id, created_by_actor_id, current_assignee_actor_id, title, "
                        + "status, completed_time) VALUES (?, ?, ?, 'Invalid state', "
                        + "'TODO', UTC_TIMESTAMP(6))",
                TENANT, OWNER, OWNER);
        assertDatabaseRejects(
                "INSERT INTO crm_work_task "
                        + "(tenant_id, created_by_actor_id, current_assignee_actor_id, title, "
                        + "status) VALUES (?, ?, ?, 'Invalid start state', 'IN_PROGRESS')",
                TENANT, OWNER, OWNER);
        assertDatabaseRejects(
                "INSERT INTO crm_work_task "
                        + "(tenant_id, created_by_actor_id, current_assignee_actor_id, "
                        + "business_object_type, business_record_id, title, status) "
                        + "VALUES (?, ?, ?, 'PRODUCT', 1, 'Invalid relation', 'TODO')",
                TENANT, OWNER, OWNER);
        assertDatabaseRejects(
                "INSERT INTO crm_work_task "
                        + "(tenant_id, created_by_actor_id, current_assignee_actor_id, "
                        + "business_object_type, business_record_id, title, status) "
                        + "VALUES (?, ?, ?, 'CUSTOMER', NULL, "
                        + "'Missing relationship ID', 'TODO')",
                TENANT, OWNER, OWNER);
        assertDatabaseRejects(
                "INSERT INTO crm_work_task "
                        + "(tenant_id, created_by_actor_id, current_assignee_actor_id, "
                        + "business_object_type, business_record_id, title, status) "
                        + "VALUES (?, ?, ?, NULL, 41, "
                        + "'Missing relationship type', 'TODO')",
                TENANT, OWNER, OWNER);
        assertDatabaseRejects(
                "INSERT INTO crm_work_task_follower "
                        + "(tenant_id, work_task_id, follower_actor_id) VALUES (?, ?, ?)",
                TENANT, crossTenantTarget.getId(), CROSS_TENANT_MEMBER);
        assertDatabaseRejects(
                "INSERT INTO crm_work_task_comment "
                        + "(tenant_id, work_task_id, author_actor_id, content) "
                        + "VALUES (?, ?, ?, '   ')",
                TENANT, crossTenantTarget.getId(), OWNER);

        Integer indexes = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'crm_work_task' "
                        + "AND index_name IN ('idx_crm_work_task_assignee_due', "
                        + "'idx_crm_work_task_creator_list', 'idx_crm_work_task_fixed_filter')",
                Integer.class);
        assertEquals(3, indexes);
        Integer compositeForeignKeyColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND table_name = 'crm_work_task' "
                        + "AND referenced_table_name = 'organization_membership' "
                        + "AND referenced_column_name IN ('tenant_id', 'actor_id')",
                Integer.class);
        assertEquals(4, compositeForeignKeyColumns);
        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND table_name IN ('crm_work_task_follower', "
                        + "'crm_work_task_comment')",
                Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() "
                        + "AND ((table_name = 'crm_work_task' "
                        + "AND index_name = 'idx_crm_work_task_business_record') "
                        + "OR (table_name = 'crm_work_task_follower' "
                        + "AND index_name = 'idx_crm_work_task_follower_member') "
                        + "OR (table_name = 'crm_work_task_comment' "
                        + "AND index_name = 'idx_crm_work_task_comment_page'))",
                Integer.class));
    }

    @Test
    void routeAndTransportRejectMissingAuthorityAndInjectedServerFacts()
            throws Exception {
        mockMvc.perform(post("/api/v1/work/tasks/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        bootstrap(OWNER, TENANT, "Transport owner");
        ResultActions injected = performAs(OWNER, TENANT, post("/api/v1/work/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Injected task",
                                  "assigneeActorId": 888888,
                                  "tenantId": 1,
                                  "createdByActorId": 1,
                                  "status": "DONE",
                                  "completedTime": "2026-07-19T00:00:00"
                                }
                                """))
                .andExpect(status().isOk());
        WorkTaskResponse protectedFacts = response(injected);
        assertEquals(OWNER, protectedFacts.getCreatedByActorId());
        assertEquals("TODO", protectedFacts.getStatus());
        assertNull(protectedFacts.getCompletedTime());

        performAs(OWNER, TENANT, post("/api/v1/work/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCreateRequest()
                                .setTitle("😀".repeat(201))
                                .setAssigneeActorId(OWNER))))
                .andExpect(status().isBadRequest());
        queryAs(OWNER, new WorkTaskQueryRequest()
                .setStatuses(List.of("DELETED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void followersCanReadAndCommentButCannotExecuteOrEraseCommentHistory()
            throws Exception {
        bootstrap(OWNER, TENANT, "Collaboration owner");
        seedMember(ASSIGNEE, "Collaboration assignee");
        seedMember(OUTSIDER, "Task follower");
        grant(ASSIGNEE, "Collaboration assignee role",
                "work-tasks:read", "work-tasks:write");
        grant(OUTSIDER, "Task follower role",
                "work-tasks:read", "work-tasks:write");

        WorkTaskResponse task = response(performAs(OWNER, TENANT,
                post("/api/v1/work/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCreateRequest()
                                .setTitle("Follower task")
                                .setAssigneeActorId(ASSIGNEE)
                                .setFollowerActorIds(List.of(OUTSIDER)))))
                .andExpect(status().isOk()));
        assertEquals(List.of(OUTSIDER), task.getFollowerActorIds());
        assertEquals(0L, task.getWorklistProjectionVersion());

        performAs(OUTSIDER, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isOk());
        queryAs(OUTSIDER, new WorkTaskQueryRequest()
                .setParticipantRole("FOLLOWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performAs(OUTSIDER, TENANT,
                post("/api/v1/work/tasks/{id}/start", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(task.getVersion()))))
                .andExpect(status().isForbidden());

        WorkTaskCommentResponse comment = fromJson(performAs(OUTSIDER, TENANT,
                post("/api/v1/work/tasks/{id}/comments", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCommentCreateRequest()
                                .setContent("  Plain follower comment  "))))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), WorkTaskCommentResponse.class);
        assertEquals("Plain follower comment", comment.getContent());
        performAs(ASSIGNEE, TENANT,
                delete("/api/v1/work/tasks/{taskId}/comments/{commentId}",
                        task.getId(), comment.getId())
                        .param("version", comment.getVersion().toString()))
                .andExpect(status().isUnprocessableEntity());
        performAs(OUTSIDER, TENANT,
                delete("/api/v1/work/tasks/{taskId}/comments/{commentId}",
                        task.getId(), comment.getId())
                        .param("version", comment.getVersion().toString()))
                .andExpect(status().isOk());
        performAs(OUTSIDER, TENANT,
                get("/api/v1/work/tasks/{id}/comments", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));

        performAs(OWNER, TENANT,
                delete("/api/v1/work/tasks/{id}", task.getId())
                        .param("version", task.getVersion().toString()))
                .andExpect(status().isUnprocessableEntity());
        WorkTaskResponse replaced = response(performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}/followers", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskFollowerUpdateRequest()
                                .setFollowerActorIds(List.of())
                                .setVersion(task.getVersion()))))
                .andExpect(status().isOk()));
        assertEquals(List.of(), replaced.getFollowerActorIds());
        assertEquals(0L, replaced.getWorklistProjectionVersion());
        performAs(OUTSIDER, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void crmRelationshipRequiresIndependentScopesAndIsHiddenAfterReadLoss()
            throws Exception {
        bootstrap(OWNER, TENANT, "Relationship owner");
        seedMember(ASSIGNEE, "Relationship assignee");
        seedMember(OUTSIDER, "CRM-only reader");
        seedMember(RELATION_READER, "Relationship follower");
        grant(ASSIGNEE, "Relationship assignee role",
                "work-tasks:read", "work-tasks:write");
        grant(OUTSIDER, "CRM-only work entry", "work-tasks:read");
        grant(RELATION_READER, "Relationship follower work role",
                "work-tasks:read", "work-tasks:write");
        grantScoped(OUTSIDER, "CRM-only customer role",
                "customers:read", "TENANT");
        TenantRoleResponse customerReaderRole = grantScoped(RELATION_READER,
                "Follower customer role", "customers:read", "TENANT");

        CustomerResponse customer = fromJson(performAs(OWNER, TENANT,
                post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerCreateRequest()
                                .setName("Task relationship customer")
                                .setOwnerActorId(OWNER))))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), CustomerResponse.class);

        WorkTaskResponse task = response(performAs(OWNER, TENANT,
                post("/api/v1/work/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCreateRequest()
                                .setTitle("Customer-related task")
                                .setAssigneeActorId(ASSIGNEE)
                                .setFollowerActorIds(List.of(RELATION_READER))
                                .setBusinessObjectType("CUSTOMER")
                                .setBusinessRecordId(customer.getId()))))
                .andExpect(status().isOk()));
        assertEquals("CUSTOMER", task.getBusinessObjectType());
        assertEquals(customer.getId(), task.getBusinessRecordId());

        performAs(OUTSIDER, TENANT,
                get("/api/v1/customers/{id}", customer.getId()))
                .andExpect(status().isOk());
        performAs(OUTSIDER, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isNotFound());
        performAs(ASSIGNEE, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessObjectType").doesNotExist())
                .andExpect(jsonPath("$.businessRecordId").doesNotExist());
        performAs(ASSIGNEE, TENANT,
                put("/api/v1/work/tasks/{id}/business-record", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskBusinessRecordUpdateRequest()
                                .setBusinessObjectType("CUSTOMER")
                                .setBusinessRecordId(customer.getId())
                                .setVersion(task.getVersion()))))
                .andExpect(status().isForbidden());

        queryAs(OWNER, new WorkTaskQueryRequest()
                .setBusinessObjectType("CUSTOMER")
                .setBusinessRecordId(customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performAs(RELATION_READER, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessRecordId").value(customer.getId()));

        new JdbcTemplate(dataSource).update(
                "DELETE FROM authorization_role_assignment "
                        + "WHERE tenant_id = ? AND actor_id = ? AND role_id = ?",
                TENANT, RELATION_READER, customerReaderRole.getId());
        performAs(RELATION_READER, TENANT,
                get("/api/v1/work/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessObjectType").doesNotExist())
                .andExpect(jsonPath("$.businessRecordId").doesNotExist());

        WorkTaskResponse cleared = response(performAs(RELATION_READER, TENANT,
                put("/api/v1/work/tasks/{id}/business-record", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskBusinessRecordUpdateRequest()
                                .setVersion(task.getVersion()))))
                .andExpect(status().isOk()));
        assertNull(cleared.getBusinessObjectType());
        assertEquals(0L, cleared.getWorklistProjectionVersion());

        WorkTaskResponse unlinked = response(performAs(RELATION_READER, TENANT,
                post("/api/v1/work/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCreateRequest()
                                .setTitle("Unlinked task")
                                .setAssigneeActorId(RELATION_READER))))
                .andExpect(status().isOk()));
        WorkTaskResponse unlinkedClear = response(performAs(RELATION_READER, TENANT,
                put("/api/v1/work/tasks/{id}/business-record", unlinked.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskBusinessRecordUpdateRequest()
                                .setVersion(unlinked.getVersion()))))
                .andExpect(status().isOk()));
        assertNull(unlinkedClear.getBusinessObjectType());

        WorkTaskResponse relationRace = response(performAs(OWNER, TENANT,
                post("/api/v1/work/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCreateRequest()
                                .setTitle("Relationship race")
                                .setAssigneeActorId(OWNER)
                                .setBusinessObjectType("CUSTOMER")
                                .setBusinessRecordId(customer.getId()))))
                .andExpect(status().isOk()));
        List<Integer> raceStatuses = concurrently(
                () -> updateStatus(relationRace),
                () -> businessRecordClearStatus(relationRace));
        assertEquals(Set.of(200, 409), Set.copyOf(raceStatuses));
    }

    @Test
    void allEightCrmRelationshipTypesUseRealOwningScopesAndHideAfterRevocation()
            throws Exception {
        bootstrap(OWNER, TENANT, "All relationship domains owner");
        seedMember(RELATION_READER, "All relationship domains member");
        List<BusinessRecordFixture> records = createAllBusinessRecordFixtures();
        TenantRoleResponse role = grantRelationshipRole(RELATION_READER);
        List<WorkTaskResponse> tasks = new ArrayList<>();

        for (BusinessRecordFixture record : records) {
            WorkTaskResponse task = response(performAs(RELATION_READER, TENANT,
                    post("/api/v1/work/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new WorkTaskCreateRequest()
                                    .setTitle(record.authority().type() + " scoped task")
                                    .setAssigneeActorId(RELATION_READER)
                                    .setBusinessObjectType(record.authority().type())
                                    .setBusinessRecordId(record.recordId()))))
                    .andExpect(status().isOk()));
            tasks.add(task);
            queryAs(RELATION_READER, new WorkTaskQueryRequest()
                    .setBusinessObjectType(record.authority().type())
                    .setBusinessRecordId(record.recordId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalNum").value(1))
                    .andExpect(jsonPath("$.objectList[0].businessObjectType")
                            .value(record.authority().type()))
                    .andExpect(jsonPath("$.objectList[0].businessRecordId")
                            .value(record.recordId()));
        }

        Set<String> restrictedTypes = new LinkedHashSet<>();
        for (int index = 0; index < records.size(); index++) {
            BusinessRecordFixture record = records.get(index);
            WorkTaskResponse task = tasks.get(index);
            restrictedTypes.add(record.authority().type());
            role = replaceRelationshipRoleGrants(role, restrictedTypes);

            performAs(RELATION_READER, TENANT,
                    get("/api/v1/work/tasks/{id}", task.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessObjectType").doesNotExist())
                    .andExpect(jsonPath("$.businessRecordId").doesNotExist());
            queryAs(RELATION_READER, new WorkTaskQueryRequest()
                    .setBusinessObjectType(record.authority().type())
                    .setBusinessRecordId(record.recordId()))
                    .andExpect(status().isNotFound());
            performAs(RELATION_READER, TENANT,
                    put("/api/v1/work/tasks/{id}/business-record", task.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new WorkTaskBusinessRecordUpdateRequest()
                                    .setVersion(task.getVersion()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessObjectType").doesNotExist())
                    .andExpect(jsonPath("$.businessRecordId").doesNotExist());
        }
    }

    private List<BusinessRecordFixture> createAllBusinessRecordFixtures()
            throws Exception {
        CustomerResponse customer = createBusinessRecord("/api/v1/customers",
                new CustomerCreateRequest().setName("Relationship customer")
                        .setOwnerActorId(OWNER), CustomerResponse.class);
        ContactResponse contact = createBusinessRecord("/api/v1/contacts",
                new ContactCreateRequest().setName("Relationship contact")
                        .setOwnerActorId(OWNER).setCustomerId(customer.getId()),
                ContactResponse.class);
        LeadResponse lead = createBusinessRecord("/api/v1/leads",
                new LeadCreateRequest().setName("Relationship lead")
                        .setOwnerActorId(OWNER), LeadResponse.class);
        OpportunityResponse opportunity = createBusinessRecord("/api/v1/opportunities",
                new OpportunityCreateRequest().setName("Relationship opportunity")
                        .setCustomerId(customer.getId()).setOwnerActorId(OWNER)
                        .setPipelineCode("direct").setStageCode("qualification")
                        .setExpectedCloseDate(LocalDate.of(2027, 12, 31))
                        .setEstimatedAmount(new BigDecimal("1000.00")),
                OpportunityResponse.class);
        ProductResponse product = createBusinessRecord("/api/v1/products",
                new ProductCreateRequest().setName("Relationship service")
                        .setCode("RELATIONSHIP-SERVICE").setOwnerActorId(OWNER)
                        .setUnit("item").setListPrice(new BigDecimal("100.00"))
                        .setAvailability("AVAILABLE"), ProductResponse.class);
        ContractResponse contract = createBusinessRecord("/api/v1/contracts",
                new ContractCreateRequest().setName("Relationship contract")
                        .setNumber("RELATIONSHIP-C-1").setCustomerId(customer.getId())
                        .setOpportunityId(opportunity.getId()).setContactId(contact.getId())
                        .setOwnerActorId(OWNER).setDiscountRate(BigDecimal.ZERO)
                        .setProducts(List.of(new ContractProductLineRequest()
                                .setProductId(product.getId())
                                .setQuantity(new BigDecimal("10.0000"))
                                .setUnitPrice(new BigDecimal("100.00")))),
                ContractResponse.class);
        InvoiceResponse invoice = createBusinessRecord("/api/v1/invoices",
                new InvoiceCreateRequest().setContractId(contract.getId())
                        .setOwnerActorId(OWNER).setAmount(new BigDecimal("100.00"))
                        .setInvoiceType("VAT").setTitleType("ORGANIZATION")
                        .setTitle("Relationship invoice").setTaxNumber("REL-TAX-1"),
                InvoiceResponse.class);
        ReceivablePlanResponse plan = createBusinessRecord("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(contract.getId())
                        .setOwnerActorId(OWNER).setDueDate(LocalDate.of(2027, 6, 30))
                        .setPlannedAmount(new BigDecimal("100.00"))
                        .setPaymentMethod("WIRE"), ReceivablePlanResponse.class);
        ReceivableResponse receivable = createBusinessRecord("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setOwnerActorId(OWNER).setReceivedDate(LocalDate.of(2027, 1, 31))
                        .setPaymentMethod("WIRE").setAmount(new BigDecimal("100.00")),
                ReceivableResponse.class);

        return List.of(
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(0), customer.getId()),
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(1), contact.getId()),
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(2), lead.getId()),
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(3), opportunity.getId()),
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(4), contract.getId()),
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(5), invoice.getId()),
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(6), receivable.getId()),
                new BusinessRecordFixture(RELATIONSHIP_AUTHORITIES.get(7), plan.getId()));
    }

    private <T> T createBusinessRecord(String path, Object request, Class<T> responseType)
            throws Exception {
        return fromJson(performAs(OWNER, TENANT, post(path)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                responseType);
    }

    private TenantRoleResponse grantRelationshipRole(long actorId) throws Exception {
        TenantRoleResponse role = fromJson(performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleCreateRequest()
                                .setName("All relationship domain scopes")
                                .setGrants(relationshipRoleGrants(Set.of())))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
        performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles/{roleId}/assignments", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(actorId)))))
                .andExpect(status().isOk());
        return role;
    }

    private TenantRoleResponse replaceRelationshipRoleGrants(
            TenantRoleResponse role, Set<String> restrictedTypes) throws Exception {
        return fromJson(performAs(OWNER, TENANT,
                put("/api/v1/authorization/roles/{roleId}/grants", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleGrantReplaceRequest()
                                .setVersion(role.getVersion())
                                .setGrants(relationshipRoleGrants(restrictedTypes)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private List<RoleGrantRequest> relationshipRoleGrants(Set<String> restrictedTypes) {
        List<RoleGrantRequest> grants = new ArrayList<>();
        grants.add(new RoleGrantRequest().setAuthorityCode("work-tasks:read"));
        grants.add(new RoleGrantRequest().setAuthorityCode("work-tasks:write"));
        for (RelationshipAuthority authority : RELATIONSHIP_AUTHORITIES) {
            String scope = restrictedTypes.contains(authority.type()) ? "SELF" : "TENANT";
            grants.add(new RoleGrantRequest().setAuthorityCode(authority.readAuthority())
                    .setDataScope(scope));
            grants.add(new RoleGrantRequest().setAuthorityCode(authority.writeAuthority())
                    .setDataScope(scope));
        }
        return List.copyOf(grants);
    }

    private WorkTaskResponse create(String title, long assignee, LocalDateTime dueTime)
            throws Exception {
        return response(performAs(OWNER, TENANT, post("/api/v1/work/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskCreateRequest().setTitle(title)
                        .setAssigneeActorId(assignee).setDueTime(dueTime))))
                .andExpect(status().isOk()));
    }

    private WorkTaskResponse response(ResultActions action) throws Exception {
        return fromJson(action.andReturn().getResponse().getContentAsString(),
                WorkTaskResponse.class);
    }

    private WorklistPageResponse worklist(long actorId, LocalDate day) throws Exception {
        return fromJson(performAs(actorId, TENANT, get("/api/v1/worklist")
                        .param("date", day.toString()).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                WorklistPageResponse.class);
    }

    private WorklistItemAcknowledgementRequest acknowledgement(WorklistItemResponse item) {
        return new WorklistItemAcknowledgementRequest().setKind(item.getKind())
                .setSourceId(item.getSourceId()).setSourceVersion(item.getSourceVersion())
                .setDueTime(item.getDueTime());
    }

    private ResultActions queryAs(long actorId, WorkTaskQueryRequest request)
            throws Exception {
        return performAs(actorId, TENANT, post("/api/v1/work/tasks/query")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request)));
    }

    private int completeStatus(long actorId, WorkTaskResponse task) throws Exception {
        return performAs(actorId, TENANT,
                post("/api/v1/work/tasks/{id}/complete", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(task.getVersion()))))
                .andReturn().getResponse().getStatus();
    }

    private int updateStatus(WorkTaskResponse task) throws Exception {
        return performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskUpdateRequest()
                                .setTitle("Edited structural race")
                                .setPriority(task.getPriority())
                                .setVersion(task.getVersion()))))
                .andReturn().getResponse().getStatus();
    }

    private int reassignStatus(WorkTaskResponse task) throws Exception {
        return performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}/assignee", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskAssigneeUpdateRequest()
                                .setAssigneeActorId(ASSIGNEE)
                                .setVersion(task.getVersion()))))
                .andReturn().getResponse().getStatus();
    }

    private int businessRecordClearStatus(WorkTaskResponse task) throws Exception {
        return performAs(OWNER, TENANT,
                put("/api/v1/work/tasks/{id}/business-record", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskBusinessRecordUpdateRequest()
                                .setVersion(task.getVersion()))))
                .andReturn().getResponse().getStatus();
    }

    private int cancelStatus(WorkTaskResponse task) throws Exception {
        return performAs(OWNER, TENANT,
                post("/api/v1/work/tasks/{id}/cancel", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskVersionRequest()
                                .setVersion(task.getVersion()))))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(first);
            Future<Integer> secondResult = executor.submit(second);
            return List.of(firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertDatabaseRejects(String sql, long... parameters) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (var foreignKeyChecks = connection.createStatement()) {
                foreignKeyChecks.execute("SET SESSION FOREIGN_KEY_CHECKS = 1");
                try (var result = foreignKeyChecks.executeQuery(
                        "SELECT @@SESSION.FOREIGN_KEY_CHECKS")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                    statement.setLong(index + 1, parameters[index]);
                }
                assertThrows(SQLException.class, statement::executeUpdate);
            }
        }
    }

    private void bootstrap(long actorId, long tenantId, String name) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(name))))
                .andExpect(status().isOk());
    }

    private void seedMember(long actorId, String displayName) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                + "VALUES (?, ?)", actorId, displayName);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                + "VALUES (?, ?, 'ACTIVE')", TENANT, actorId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT);
    }

    private TenantRoleResponse grant(long actorId, String roleName, String... authorities)
            throws Exception {
        TenantRoleResponse role = fromJson(performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleCreateRequest().setName(roleName)
                                .setGrants(java.util.Arrays.stream(authorities)
                                        .map(authority -> new RoleGrantRequest()
                                                .setAuthorityCode(authority))
                                        .toList()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
        performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles/{roleId}/assignments", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(actorId)))))
                .andExpect(status().isOk());
        return role;
    }

    private TenantRoleResponse grantScoped(
            long actorId, String roleName, String authority, String dataScope)
            throws Exception {
        TenantRoleResponse role = fromJson(performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleCreateRequest().setName(roleName)
                                .setGrants(List.of(new RoleGrantRequest()
                                        .setAuthorityCode(authority)
                                        .setDataScope(dataScope))))))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), TenantRoleResponse.class);
        performAs(OWNER, TENANT,
                post("/api/v1/authorization/roles/{roleId}/assignments", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(actorId)))))
                .andExpect(status().isOk());
        return role;
    }

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    private record RelationshipAuthority(
            String type, String readAuthority, String writeAuthority) {
    }

    private record BusinessRecordFixture(
            RelationshipAuthority authority, Long recordId) {
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
        executeSql("DELETE FROM worklist_acknowledgement");
        executeSql("DELETE FROM crm_work_task_comment");
        executeSql("DELETE FROM crm_work_task_follower");
        executeSql("DELETE FROM crm_work_task");
        executeSql("DELETE FROM crm_receivable_unique_field_value");
        executeSql("DELETE FROM crm_receivable_field_value");
        executeSql("DELETE FROM crm_receivable");
        executeSql("DELETE FROM crm_receivable_plan_unique_field_value");
        executeSql("DELETE FROM crm_receivable_plan_field_value");
        executeSql("DELETE FROM crm_receivable_plan");
        executeSql("DELETE FROM crm_invoice_unique_field_value");
        executeSql("DELETE FROM crm_invoice_field_value");
        executeSql("DELETE FROM crm_invoice_profile");
        executeSql("DELETE FROM crm_invoice");
        executeSql("DELETE FROM crm_contract_unique_field_value");
        executeSql("DELETE FROM crm_contract_field_value");
        executeSql("DELETE FROM crm_contract_product_line");
        executeSql("DELETE FROM crm_contract_collaborator");
        executeSql("DELETE FROM crm_contract");
        executeSql("DELETE FROM crm_opportunity_star");
        executeSql("DELETE FROM crm_opportunity_unique_field_value");
        executeSql("DELETE FROM crm_opportunity_field_value");
        executeSql("DELETE FROM crm_opportunity_product_line");
        executeSql("DELETE FROM crm_opportunity_collaborator");
        executeSql("DELETE FROM crm_opportunity_contact");
        executeSql("DELETE FROM crm_opportunity");
        executeSql("DELETE FROM crm_lead_star");
        executeSql("DELETE FROM crm_lead_unique_field_value");
        executeSql("DELETE FROM crm_lead_field_value");
        executeSql("DELETE FROM crm_lead_collaborator");
        executeSql("DELETE FROM crm_lead_tag");
        executeSql("DELETE FROM crm_lead_address");
        executeSql("UPDATE crm_lead SET parent_lead_id = NULL");
        executeSql("DELETE FROM crm_lead");
        executeSql("DELETE FROM crm_lead_guard");
        executeSql("DELETE FROM crm_contact_star");
        executeSql("DELETE FROM crm_contact_unique_field_value");
        executeSql("DELETE FROM crm_contact_field_value");
        executeSql("DELETE FROM crm_contact_collaborator");
        executeSql("DELETE FROM crm_contact_tag");
        executeSql("DELETE FROM crm_contact_address");
        executeSql("UPDATE crm_contact SET parent_contact_id = NULL");
        executeSql("DELETE FROM crm_contact");
        executeSql("DELETE FROM crm_contact_guard");
        executeSql("DELETE FROM crm_product_unique_field_value");
        executeSql("DELETE FROM crm_product_field_value");
        executeSql("DELETE FROM crm_product");
        executeSql("UPDATE crm_product_category SET parent_category_id = NULL");
        executeSql("DELETE FROM crm_product_category");
        executeSql("DELETE FROM crm_product_catalog_guard");
        executeSql("DELETE FROM crm_customer_star");
        executeSql("DELETE FROM crm_customer_unique_field_value");
        executeSql("DELETE FROM crm_customer_field_value");
        executeSql("DELETE FROM crm_customer_collaborator");
        executeSql("DELETE FROM crm_customer_tag");
        executeSql("DELETE FROM crm_customer_address");
        executeSql("UPDATE crm_customer SET parent_customer_id = NULL");
        executeSql("DELETE FROM crm_customer");
        executeSql("DELETE FROM crm_customer_guard");
        executeSql("DELETE FROM navigation_preference");
        executeSql("DELETE FROM role_field_access");
        executeSql("DELETE FROM role_field_policy");
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
