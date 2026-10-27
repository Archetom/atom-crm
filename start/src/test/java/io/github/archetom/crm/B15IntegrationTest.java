package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.ActivityOptionRequest;
import io.github.archetom.crm.api.dto.request.ActivityUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;
import io.github.archetom.crm.api.dto.request.WorklistItemAcknowledgementRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.response.ActivityResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitOptionResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitRelationStateResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldPolicyResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.api.dto.response.WorklistItemResponse;
import io.github.archetom.crm.api.dto.response.WorklistPageResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Production MySQL/HTTP coverage for the B15 activity, return-visit and worklist boundary. */
@RequiresDatabase
class B15IntegrationTest extends BaseIntegrationTest {
    private final Set<Long> createdTenants = new LinkedHashSet<>();

    @Test
    void returnVisitCrudOptionsAndSameCustomerForeignKeysAreEnforced() throws Exception {
        long actor = 815101L;
        long tenant = 815001L;
        bootstrap(actor, tenant, "Return visit owner");
        CustomerResponse first = customer(actor, tenant, "First customer");
        CustomerResponse second = customer(actor, tenant, "Second customer");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, customer_id, name, status) "
                        + "VALUES (?, ?, ?, 'First contact', 'ACTIVE')",
                tenant, actor, first.getId());
        Long contactId = jdbc.queryForObject(
                "SELECT id FROM crm_contact WHERE tenant_id = ? AND name = 'First contact'",
                Long.class, tenant);
        jdbc.update("INSERT INTO crm_contract "
                        + "(tenant_id, owner_actor_id, customer_id, name, contract_number, "
                        + "contract_number_key, status) VALUES (?, ?, ?, 'First contract', "
                        + "'RV-CONTRACT-1', 'rv-contract-1', 'ACTIVE')",
                tenant, actor, first.getId());
        Long contractId = jdbc.queryForObject(
                "SELECT id FROM crm_contract WHERE tenant_id = ? AND contract_number = 'RV-CONTRACT-1'",
                Long.class, tenant);
        LocalDateTime due = LocalDate.of(2026, 7, 18).atTime(10, 15, 30, 123_456_789);

        ReturnVisitResponse created = fromJson(performAs(actor, tenant,
                post("/api/v1/return-visits").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitCreateRequest().setCustomerId(first.getId())
                                .setContactId(contactId).setContractId(contractId)
                                .setVisitTime(due).setVisitForm("PHONE").setSatisfaction("SATISFIED")
                                .setRemark("Initial"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        assertEquals(123_456_000, created.getVisitTime().getNano());
        assertEquals(ReturnVisitRelationStateResponse.VISIBLE, created.getContactRelationState());
        assertEquals(ReturnVisitRelationStateResponse.VISIBLE, created.getContractRelationState());
        assertEquals("PHONE", created.getVisitForm());
        assertEquals("SATISFIED", created.getSatisfaction());
        ReturnVisitOptionResponse[] options = fromJson(performAs(actor, tenant,
                get("/api/v1/return-visits/options").param("keyword", created.getVisitNumber())
                        .param("limit", "10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitOptionResponse[].class);
        assertEquals(List.of(created.getId()),
                java.util.Arrays.stream(options).map(ReturnVisitOptionResponse::getId).toList());

        ReturnVisitResponse updated = fromJson(performAs(actor, tenant,
                put("/api/v1/return-visits/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitUpdateRequest().setVersion(created.getVersion())
                                .setContactId(contactId).setVisitTime(due.plusHours(1))
                                .setVisitForm("EMAIL").setSatisfaction("VERY_SATISFIED")
                                .setRemark("Updated"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        assertEquals("Updated", updated.getRemark());
        assertEquals("EMAIL", updated.getVisitForm());
        assertEquals("VERY_SATISFIED", updated.getSatisfaction());

        long relationBlindActor = 815102L;
        seedMember(jdbc, tenant, relationBlindActor, "Relation-blind return visit writer");
        TenantRoleResponse relationBlindRole = roleWithGrants(actor, tenant,
                "Relation-blind return visit writer",
                new RoleGrantRequest().setAuthorityCode("return-visits:read").setDataScope("TENANT"),
                new RoleGrantRequest().setAuthorityCode("return-visits:write").setDataScope("TENANT"),
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("TENANT"));
        assign(actor, tenant, relationBlindRole.getId(), relationBlindActor);
        replaceReturnVisitPolicy(actor, tenant, relationBlindRole.getId());

        ReturnVisitResponse concealed = fromJson(performAs(relationBlindActor, tenant,
                get("/api/v1/return-visits/{id}", created.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        assertNull(concealed.getContactId());
        assertEquals(ReturnVisitRelationStateResponse.HIDDEN, concealed.getContactRelationState());
        assertNull(concealed.getContractId());
        assertEquals(ReturnVisitRelationStateResponse.HIDDEN, concealed.getContractRelationState());

        ReturnVisitUpdateRequest retainHiddenRelations = new ReturnVisitUpdateRequest()
                .setVersion(updated.getVersion()).setVisitTime(due.plusHours(2)).setRemark("Retained");
        String retainHiddenRelationsJson = toJson(retainHiddenRelations);
        assertFalse(retainHiddenRelationsJson.contains("\"contactId\""));
        assertFalse(retainHiddenRelationsJson.contains("\"contractId\""));
        assertFalse(retainHiddenRelationsJson.contains("\"visitForm\""));
        assertFalse(retainHiddenRelationsJson.contains("\"satisfaction\""));
        ReturnVisitResponse relationBlindUpdate = fromJson(performAs(relationBlindActor, tenant,
                put("/api/v1/return-visits/{id}", created.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(retainHiddenRelationsJson))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        assertEquals(ReturnVisitRelationStateResponse.HIDDEN, relationBlindUpdate.getContactRelationState());
        assertEquals(ReturnVisitRelationStateResponse.HIDDEN, relationBlindUpdate.getContractRelationState());

        ReturnVisitResponse retained = fromJson(performAs(actor, tenant,
                get("/api/v1/return-visits/{id}", created.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        assertEquals(contactId, retained.getContactId());
        assertEquals(ReturnVisitRelationStateResponse.VISIBLE, retained.getContactRelationState());
        assertEquals(contractId, retained.getContractId());
        assertEquals(ReturnVisitRelationStateResponse.VISIBLE, retained.getContractRelationState());
        assertEquals("EMAIL", retained.getVisitForm());
        assertEquals("VERY_SATISFIED", retained.getSatisfaction());

        ReturnVisitUpdateRequest clearContact = new ReturnVisitUpdateRequest().setVersion(retained.getVersion())
                .setContactId(null).setVisitTime(due.plusHours(3)).setRemark("Contact cleared");
        String clearContactJson = toJson(clearContact);
        assertTrue(clearContactJson.contains("\"contactId\":null"));
        assertFalse(clearContactJson.contains("\"contractId\""));
        ReturnVisitResponse contactCleared = fromJson(performAs(actor, tenant,
                put("/api/v1/return-visits/{id}", created.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(clearContactJson))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        assertNull(contactCleared.getContactId());
        assertEquals(ReturnVisitRelationStateResponse.EMPTY, contactCleared.getContactRelationState());
        assertEquals(contractId, contactCleared.getContractId());
        assertEquals(ReturnVisitRelationStateResponse.VISIBLE, contactCleared.getContractRelationState());

        ReturnVisitResponse contractCleared = fromJson(performAs(actor, tenant,
                put("/api/v1/return-visits/{id}", created.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitUpdateRequest().setVersion(contactCleared.getVersion())
                                .setContractId(null).setVisitTime(due.plusHours(4))
                                .setRemark("Contract cleared"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        assertNull(contractCleared.getContractId());
        assertEquals(ReturnVisitRelationStateResponse.EMPTY, contractCleared.getContractRelationState());

        performAs(actor, tenant, put("/api/v1/return-visits/{id}", created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ReturnVisitUpdateRequest().setVersion(contactCleared.getVersion())
                        .setContactId(contactId).setVisitTime(due).setRemark("Stale"))))
                .andExpect(status().isConflict());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO crm_return_visit "
                        + "(tenant_id, owner_actor_id, customer_id, contact_id, visit_number, "
                        + "visit_time, status) VALUES (?, ?, ?, ?, 'RV-MISMATCH', ?, 'ACTIVE')",
                tenant, actor, second.getId(), contactId, due));

        performAs(actor, tenant, delete("/api/v1/return-visits/{id}", created.getId())
                .param("version", contractCleared.getVersion().toString())).andExpect(status().isOk());
        performAs(actor, tenant, get("/api/v1/return-visits/{id}", created.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void explicitSelfOwnerDoesNotRequireReturnVisitAssignmentAuthority() throws Exception {
        long owner = 815106L;
        long tenant = 815006L;
        long member = 815206L;
        bootstrap(owner, tenant, "Return visit self-assignment owner");
        CustomerResponse customer = customer(owner, tenant, "Self-assignment customer");
        seedMember(new JdbcTemplate(dataSource), tenant, member, "Return visit member");

        TenantRoleResponse role = roleWithGrants(owner, tenant, "Return visit self writer",
                new RoleGrantRequest().setAuthorityCode("return-visits:write").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("TENANT"));
        assign(owner, tenant, role.getId(), member);
        replaceReturnVisitPolicy(owner, tenant, role.getId());

        performAs(member, tenant, post("/api/v1/return-visits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ReturnVisitCreateRequest().setCustomerId(customer.getId())
                        .setOwnerActorId(member)
                        .setVisitTime(LocalDate.of(2026, 7, 18).atTime(10, 30)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerActorId").value(member));
    }

    @Test
    void worklistCursorMergesSourcesWithoutSkippingAndAcknowledgementsTrackVersion() throws Exception {
        long actor = 815102L;
        long tenant = 815002L;
        bootstrap(actor, tenant, "Worklist cursor owner");
        CustomerResponse customer = customer(actor, tenant, "Cursor customer");
        LocalDate day = LocalDate.of(2026, 7, 18);
        List<ActivityResponse> activities = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            activities.add(activity(actor, tenant, customer.getId(),
                    day.atTime(9, index * 2), "Activity " + index));
            performAs(actor, tenant, post("/api/v1/return-visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new ReturnVisitCreateRequest().setCustomerId(customer.getId())
                            .setVisitTime(day.atTime(9, index * 2 + 1))
                            .setRemark("Visit " + index)))).andExpect(status().isOk());
        }

        List<WorklistItemResponse> merged = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            WorklistPageResponse result = worklist(actor, tenant, day, cursor, 2);
            merged.addAll(result.getItems());
            cursor = result.getNextCursor();
            if (cursor == null) break;
        }
        assertEquals(List.of("FOLLOW_UP", "RETURN_VISIT", "FOLLOW_UP", "RETURN_VISIT",
                        "FOLLOW_UP", "RETURN_VISIT"),
                merged.stream().map(WorklistItemResponse::getKind).toList());
        assertEquals(6, new LinkedHashSet<>(merged.stream()
                .map(value -> value.getKind() + ':' + value.getSourceId()).toList()).size());

        WorklistItemResponse first = merged.getFirst();
        performAs(actor, tenant, post("/api/v1/worklist/acknowledgements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorklistAcknowledgementRequest().setDate(day).setItems(List.of(
                        acknowledgement(first)))))).andExpect(status().isOk());
        WorklistPageResponse afterExact = worklist(actor, tenant, day, null, 100);
        assertFalse(hasFingerprint(afterExact.getItems(), first));

        ActivityResponse original = activities.getFirst();
        ActivityResponse changed = fromJson(performAs(actor, tenant,
                put("/api/v1/activities/{id}", original.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ActivityUpdateRequest().setVersion(original.getVersion())
                                .setCategory("CALL").setPurpose("Updated purpose")
                                .setContent("Updated content").setOccurredTime(original.getOccurredTime())
                                .setNextContactTime(original.getNextContactTime()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ActivityResponse.class);
        WorklistPageResponse afterVersion = worklist(actor, tenant, day, null, 100);
        assertTrue(afterVersion.getItems().stream().anyMatch(value ->
                value.getKind().equals("FOLLOW_UP") && value.getSourceId().equals(changed.getId())
                        && value.getSourceVersion().equals(changed.getVersion())));

        ActivityResponse cleared = fromJson(performAs(actor, tenant,
                put("/api/v1/activities/{id}", changed.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ActivityUpdateRequest().setVersion(changed.getVersion())
                                .setCategory("CALL").setPurpose(changed.getPurpose())
                                .setContent(changed.getContent()).setOccurredTime(changed.getOccurredTime())
                                .setNextContactTime(null))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ActivityResponse.class);
        assertNull(cleared.getNextContactTime());

        performAs(actor, tenant, post("/api/v1/worklist/acknowledgements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorklistAcknowledgementRequest().setDate(day)
                        .setKind("RETURN_VISIT")))).andExpect(status().isOk());
        assertFalse(worklist(actor, tenant, day, null, 100).getItems().stream()
                .anyMatch(value -> value.getKind().equals("RETURN_VISIT")));
    }

    @Test
    void worklistReappliesDataScopeAndFieldPolicyForEveryRequest() throws Exception {
        long owner = 815103L;
        long tenant = 815003L;
        long fieldActor = 815203L;
        long scopedActor = 815204L;
        bootstrap(owner, tenant, "Worklist policy owner");
        CustomerResponse customer = customer(owner, tenant, "Policy customer");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDate day = LocalDate.of(2026, 7, 18);
        jdbc.update("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, customer_id, name, next_contact_time, status) "
                        + "VALUES (?, ?, ?, 'Policy contact', ?, 'ACTIVE')",
                tenant, owner, customer.getId(), day.atTime(11, 0));
        seedMember(jdbc, tenant, fieldActor, "Field member");
        seedMember(jdbc, tenant, scopedActor, "Scoped member");

        TenantRoleResponse fieldRole = role(owner, tenant, "Worklist field role", "TENANT");
        assign(owner, tenant, fieldRole.getId(), fieldActor);
        RoleFieldPolicyResponse hiddenDue = replaceContactPolicy(owner, tenant, fieldRole.getId(),
                null, List.of(field("contact_name")));
        assertTrue(worklist(fieldActor, tenant, day, null, 20).getItems().isEmpty());
        replaceContactPolicy(owner, tenant, fieldRole.getId(), hiddenDue.getVersion(),
                List.of(field("contact_name"), field("next_contact_time")));
        assertEquals(List.of("CONTACT_FOLLOW_UP"), worklist(fieldActor, tenant, day, null, 20)
                .getItems().stream().map(WorklistItemResponse::getKind).toList());

        TenantRoleResponse scopedRole = role(owner, tenant, "Worklist self role", "SELF");
        assign(owner, tenant, scopedRole.getId(), scopedActor);
        replaceContactPolicy(owner, tenant, scopedRole.getId(), null,
                List.of(field("contact_name"), field("next_contact_time")));
        assertTrue(worklist(scopedActor, tenant, day, null, 20).getItems().isEmpty());
    }

    @Test
    void legacyV2CursorDoesNotInsertWorkTasksIntoAnExistingContinuation() throws Exception {
        long actor = 815107L;
        long tenant = 815007L;
        bootstrap(actor, tenant, "Legacy worklist cursor owner");
        CustomerResponse customer = customer(actor, tenant, "Legacy cursor customer");
        LocalDate day = LocalDate.of(2026, 7, 18);
        WorkTaskResponse task = workTask(actor, tenant, actor,
                "Earlier Work task", day.atTime(8, 0));
        activity(actor, tenant, customer.getId(), day.atTime(9, 0), "First legacy activity");
        activity(actor, tenant, customer.getId(), day.atTime(10, 0), "Second legacy activity");
        String legacy = encodeCursor("v2|" + day + "|S|E|E|E|E|E|E|E");

        WorklistPageResponse first = worklist(actor, tenant, day, legacy, 1);
        assertEquals(List.of("FOLLOW_UP"), first.getItems().stream()
                .map(WorklistItemResponse::getKind).toList());
        String[] upgraded = decodeCursor(first.getNextCursor());
        assertEquals("v3", upgraded[0]);
        assertEquals(11, upgraded.length);
        assertEquals("E", upgraded[10]);

        WorklistPageResponse second = worklist(actor, tenant, day, first.getNextCursor(), 10);
        assertEquals(List.of("FOLLOW_UP"), second.getItems().stream()
                .map(WorklistItemResponse::getKind).toList());
        assertTrue(second.getNextCursor() == null);
        assertTrue(worklist(actor, tenant, day, null, 10).getItems().stream().anyMatch(value ->
                value.getKind().equals("WORK_TASK") && value.getSourceId().equals(task.getId())));
    }

    @Test
    void worklistIncludesEveryLocalSourceAndRejectsCrossTenantProbes() throws Exception {
        long actor = 815104L;
        long tenant = 815004L;
        long otherActor = 815105L;
        long otherTenant = 815005L;
        bootstrap(actor, tenant, "Complete source owner");
        CustomerResponse customer = customer(actor, tenant, "Source customer");
        LocalDate day = LocalDate.of(2026, 7, 18);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, customer_id, name, next_contact_time, status) "
                        + "VALUES (?, ?, ?, 'Due contact', ?, 'ACTIVE')",
                tenant, actor, customer.getId(), day.atTime(8, 0));
        jdbc.update("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, name, next_contact_time, status) "
                        + "VALUES (?, ?, 'Due lead', ?, 'ACTIVE')",
                tenant, actor, day.atTime(8, 10));
        OpportunityPipelineFixture.provisionActivePair(jdbc, tenant, "DEFAULT", "OPEN");
        jdbc.update("INSERT INTO crm_opportunity "
                        + "(tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code, "
                        + "next_contact_time, outcome, status) "
                        + "VALUES (?, ?, ?, 'Due opportunity', 'DEFAULT', 'OPEN', ?, 'OPEN', 'ACTIVE')",
                tenant, actor, customer.getId(), day.atTime(8, 20));
        jdbc.update("INSERT INTO crm_contract "
                        + "(tenant_id, owner_actor_id, customer_id, name, contract_number, "
                        + "contract_number_key, end_date, status) "
                        + "VALUES (?, ?, ?, 'Due contract', 'SRC-1', 'SRC-1', ?, 'ACTIVE')",
                tenant, actor, customer.getId(), day);
        Long contractId = jdbc.queryForObject(
                "SELECT id FROM crm_contract WHERE tenant_id = ? AND contract_number = 'SRC-1'",
                Long.class, tenant);
        jdbc.update("INSERT INTO crm_receivable_plan "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, installment, "
                        + "due_date, planned_amount, payment_method, reminder_days, status) "
                        + "VALUES (?, ?, ?, ?, 1, ?, 100, 'BANK', 2, 'ACTIVE')",
                tenant, actor, customer.getId(), contractId, day.plusDays(2));
        long approvalId = approval(jdbc, tenant, actor, customer.getId());
        WorkTaskResponse workTask = workTask(actor, tenant, actor,
                "Due Work task", day.atTime(8, 25));

        ActivityResponse activity = activity(actor, tenant, customer.getId(),
                day.atTime(8, 30), "Customer follow-up");
        ReturnVisitResponse visit = fromJson(performAs(actor, tenant,
                post("/api/v1/return-visits").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitCreateRequest().setCustomerId(customer.getId())
                                .setVisitTime(day.atTime(8, 40)).setRemark("Due visit"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);

        Set<String> kinds = new LinkedHashSet<>(worklist(actor, tenant, day, null, 100).getItems()
                .stream().map(WorklistItemResponse::getKind).toList());
        assertEquals(Set.of("FOLLOW_UP", "RETURN_VISIT", "CONTACT_FOLLOW_UP", "LEAD_FOLLOW_UP",
                "OPPORTUNITY_FOLLOW_UP", "RECEIVABLE_PLAN_REMINDER", "CONTRACT_EXPIRY",
                "APPROVAL_PENDING", "WORK_TASK"), kinds);
        assertNotNull(activity.getId());
        assertNotNull(workTask.getId());
        assertTrue(approvalId > 0);

        performAs(actor, tenant, get("/api/v1/activities/options"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].value").value("CALL"));
        performAs(actor, tenant, put("/api/v1/activities/options")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(
                        new ActivityOptionRequest().setType("METHOD").setValue("VISIT")
                                .setLabel("Visit").setDisplayOrder(10),
                        new ActivityOptionRequest().setType("TEMPLATE").setValue("RENEWAL")
                                .setLabel("Renewal").setDisplayOrder(10)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].value").value("VISIT"));
        performAs(actor, tenant, post("/api/v1/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ActivityCreateRequest().setSubjectType("CUSTOMER")
                        .setSubjectId(customer.getId()).setCategory("CALL").setContent("Rejected")
                        .setOccurredTime(day.atTime(12, 0)))))
                .andExpect(status().isBadRequest());
        performAs(actor, tenant, post("/api/v1/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ActivityCreateRequest().setSubjectType("CUSTOMER")
                        .setSubjectId(customer.getId()).setCategory("VISIT").setContent("Accepted")
                        .setOccurredTime(day.atTime(12, 1)))))
                .andExpect(status().isOk());

        bootstrap(otherActor, otherTenant, "Other tenant owner");
        performAs(otherActor, otherTenant, get("/api/v1/return-visits/{id}", visit.getId()))
                .andExpect(status().isNotFound());
    }

    private WorklistItemAcknowledgementRequest acknowledgement(WorklistItemResponse item) {
        return new WorklistItemAcknowledgementRequest().setKind(item.getKind())
                .setSourceId(item.getSourceId()).setSourceVersion(item.getSourceVersion())
                .setDueTime(item.getDueTime());
    }

    private boolean hasFingerprint(List<WorklistItemResponse> items, WorklistItemResponse expected) {
        return items.stream().anyMatch(value -> value.getKind().equals(expected.getKind())
                && value.getSourceId().equals(expected.getSourceId())
                && value.getSourceVersion().equals(expected.getSourceVersion())
                && value.getDueTime().equals(expected.getDueTime()));
    }

    private CustomerResponse customer(long actor, long tenant, String name) throws Exception {
        return fromJson(performAs(actor, tenant, post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerCreateRequest().setName(name))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
    }

    private ActivityResponse activity(
            long actor, long tenant, long customerId, LocalDateTime due, String purpose) throws Exception {
        return fromJson(performAs(actor, tenant, post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ActivityCreateRequest().setSubjectType("CUSTOMER")
                                .setSubjectId(customerId).setCategory("CALL").setPurpose(purpose)
                                .setContent(purpose).setOccurredTime(due).setNextContactTime(due))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ActivityResponse.class);
    }

    private WorklistPageResponse worklist(
            long actor, long tenant, LocalDate day, String cursor, int size) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/worklist")
                .param("date", day.toString()).param("size", Integer.toString(size));
        if (cursor != null) request.param("cursor", cursor);
        return fromJson(performAs(actor, tenant, request).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), WorklistPageResponse.class);
    }

    private WorkTaskResponse workTask(
            long actor, long tenant, long assignee, String title, LocalDateTime dueTime)
            throws Exception {
        return fromJson(performAs(actor, tenant, post("/api/v1/work/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskCreateRequest().setTitle(title)
                                .setAssigneeActorId(assignee).setDueTime(dueTime))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                WorkTaskResponse.class);
    }

    private String encodeCursor(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private String[] decodeCursor(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                .split("\\|", -1);
    }

    private TenantRoleResponse role(
            long owner, long tenant, String name, String contactScope) throws Exception {
        return fromJson(performAs(owner, tenant, post("/api/v1/authorization/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleCreateRequest().setName(name).setGrants(List.of(
                                new RoleGrantRequest().setAuthorityCode("worklist:read"),
                                new RoleGrantRequest().setAuthorityCode("contacts:read")
                                        .setDataScope(contactScope))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private TenantRoleResponse roleWithGrants(
            long owner, long tenant, String name, RoleGrantRequest... grants) throws Exception {
        return fromJson(performAs(owner, tenant, post("/api/v1/authorization/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleCreateRequest().setName(name)
                                .setGrants(List.of(grants)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private void assign(long owner, long tenant, long roleId, long actor) throws Exception {
        performAs(owner, tenant, post("/api/v1/authorization/roles/{id}/assignments", roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new RoleAssignmentCreateRequest().setActorIds(List.of(actor)))))
                .andExpect(status().isOk());
    }

    private RoleFieldPolicyResponse replaceContactPolicy(
            long owner, long tenant, long roleId, Long version,
            List<RoleFieldAccessRequest> fields) throws Exception {
        return fromJson(performAs(owner, tenant,
                put("/api/v1/fields/roles/{id}/objects/CONTACT/access", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setVersion(version)
                                .setAccesses(fields))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                RoleFieldPolicyResponse.class);
    }

    private void replaceReturnVisitPolicy(long owner, long tenant, long roleId) throws Exception {
        List<RoleFieldAccessRequest> accesses = List.of(
                writableField("visit_number"), writableField("customer"), writableField("contact"),
                writableField("contract"), writableField("visit_time"), writableField("visit_form"),
                writableField("satisfaction"), writableField("remark"),
                writableField("owner_actor"), writableField("status"), writableField("created_time"),
                writableField("updated_time"));
        performAs(owner, tenant, put("/api/v1/fields/roles/{id}/objects/RETURN_VISIT/access", roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(accesses))))
                .andExpect(status().isOk());
    }

    private RoleFieldAccessRequest field(String code) {
        return new RoleFieldAccessRequest().setFieldCode(code).setAccessLevel("READ")
                .setMaskType("NONE");
    }

    private RoleFieldAccessRequest writableField(String code) {
        return new RoleFieldAccessRequest().setFieldCode(code).setAccessLevel("WRITE")
                .setMaskType("NONE");
    }

    private void seedMember(JdbcTemplate jdbc, long tenant, long actor, String displayName) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actor, displayName);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                + "VALUES (?, ?, 'ACTIVE')", tenant, actor);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actor, tenant);
    }

    private long approval(JdbcTemplate jdbc, long tenant, long actor, long customerId) {
        jdbc.update("INSERT INTO approval_definition "
                        + "(tenant_id, object_type, name, normalized_name, status, repeat_mode, config_json) "
                        + "VALUES (?, 'CUSTOMER', 'Source approval', 'source approval', 'ACTIVE', 'ONCE', '{}')",
                tenant);
        Long definitionId = jdbc.queryForObject(
                "SELECT id FROM approval_definition WHERE tenant_id = ? AND name = 'Source approval'",
                Long.class, tenant);
        jdbc.update("INSERT INTO approval_instance "
                        + "(tenant_id, definition_id, definition_version, object_type, subject_id, "
                        + "idempotency_key, title, applicant_actor_id, repeat_mode, status, "
                        + "current_step_order, facts_json, submitted_time) "
                        + "VALUES (?, ?, 0, 'CUSTOMER', ?, 'source-instance', 'Pending approval', ?, "
                        + "'ONCE', 'PENDING', 0, '{}', CURRENT_TIMESTAMP(6))",
                tenant, definitionId, customerId, actor);
        Long instanceId = jdbc.queryForObject(
                "SELECT id FROM approval_instance WHERE tenant_id = ? AND idempotency_key = 'source-instance'",
                Long.class, tenant);
        jdbc.update("INSERT INTO approval_review_task "
                        + "(tenant_id, task_key, instance_id, step_code, step_name, step_order, "
                        + "approval_mode, actor_order, assigned_actor_id, status) "
                        + "VALUES (?, ?, ?, 'review', 'Review', 0, 'ANY', 0, ?, 'PENDING')",
                tenant, UUID.randomUUID().toString(), instanceId, actor);
        return instanceId;
    }

    private void bootstrap(long actor, long tenant, String displayName) throws Exception {
        performAs(actor, tenant, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
        createdTenants.add(tenant);
    }

    private ResultActions performAs(
            long actor, long tenant, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actor)
                .header("X-Dev-Tenant-Id", tenant));
    }

    @Override
    protected void clearTestData() {
        if (dataSource == null || createdTenants.isEmpty()) return;
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (Long tenant : createdTenants) {
            deleteTenantRows(jdbc, "worklist_acknowledgement", tenant);
            deleteTenantRows(jdbc, "crm_work_task_comment", tenant);
            deleteTenantRows(jdbc, "crm_work_task_follower", tenant);
            deleteTenantRows(jdbc, "crm_work_task", tenant);
            deleteTenantRows(jdbc, "crm_activity_relation", tenant);
            deleteTenantRows(jdbc, "crm_activity", tenant);
            deleteTenantRows(jdbc, "crm_return_visit_unique_field_value", tenant);
            deleteTenantRows(jdbc, "crm_return_visit_field_value", tenant);
            deleteTenantRows(jdbc, "crm_return_visit", tenant);
            deleteTenantRows(jdbc, "crm_activity_option", tenant);
            deleteTenantRows(jdbc, "approval_review_task", tenant);
            deleteTenantRows(jdbc, "approval_instance_manager", tenant);
            deleteTenantRows(jdbc, "approval_instance_watcher", tenant);
            deleteTenantRows(jdbc, "approval_instance", tenant);
            deleteTenantRows(jdbc, "approval_definition", tenant);
            deleteTenantRows(jdbc, "crm_receivable_plan", tenant);
            deleteTenantRows(jdbc, "crm_contract", tenant);
            deleteTenantRows(jdbc, "crm_opportunity", tenant);
            deleteTenantRows(jdbc, "crm_contact", tenant);
            deleteTenantRows(jdbc, "crm_lead", tenant);
            deleteTenantRows(jdbc, "crm_customer", tenant);
        }
        createdTenants.clear();
    }

    private void deleteTenantRows(JdbcTemplate jdbc, String table, long tenant) {
        jdbc.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenant);
    }
}
