package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OpportunityContactsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCollaboratorRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOutcomeUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPrimaryContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.api.dto.response.OpportunityPipelineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityPipelineStageResponse;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end opportunity boundary coverage against the production MySQL mappings. */
@RequiresDatabase
class OpportunityIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Test
    void opportunityListReturnsLatestDirectActivityTime() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity activity summary owner");
        CustomerResponse customer = createCustomer("Activity summary opportunity customer");
        OpportunityResponse opportunity = createOpportunity(
                "Activity summary opportunity", customer.getId(), null, Map.of());
        LocalDateTime latestTime = LocalDateTime.of(2026, 8, 11, 10, 45);
        performPost("/api/v1/activities", new ActivityCreateRequest().setSubjectType("OPPORTUNITY")
                        .setSubjectId(opportunity.getId()).setCategory("CALL")
                        .setContent("Latest opportunity follow-up")
                        .setOccurredTime(latestTime))
                .andExpect(status().isOk());

        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].lastActivityTime")
                        .value("2026-08-11T10:45:00"));
    }

    @Test
    void opportunityListSupportsSafeServerSideSorting() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity sort owner");
        CustomerResponse customer = createCustomer("Opportunity sort customer");
        OpportunityResponse zebra = createOpportunity("Zebra opportunity", customer.getId(), null, Map.of());
        OpportunityResponse alpha = createOpportunity("Aardvark opportunity", customer.getId(), null, Map.of());

        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setSortField("NAME").setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(zebra.getId()));
        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setSortField("DEAL_TIME").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchOwnerAndDeleteAreVersionedAndAtomic() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity batch owner");
        long replacementOwner = 888793L;
        seedActiveMember(replacementOwner, "Opportunity batch replacement owner");
        CustomerResponse customer = createCustomer("Opportunity batch customer");
        OpportunityResponse first = createOpportunity(
                "Opportunity batch first", customer.getId(), null, Map.of());
        OpportunityResponse second = createOpportunity(
                "Opportunity batch second", customer.getId(), null, Map.of());

        performPut("/api/v1/opportunities/batch/owner", new OpportunityBatchOwnerUpdateRequest()
                .setOwnerActorId(replacementOwner).setOpportunities(List.of(
                        new OpportunityBatchVersionRequest().setOpportunityId(second.getId())
                                .setVersion(second.getVersion()),
                        new OpportunityBatchVersionRequest().setOpportunityId(first.getId())
                                .setVersion(first.getVersion()))))
                .andExpect(status().isOk());
        OpportunityResponse transferredFirst = fromJson(performGet(
                "/api/v1/opportunities/" + first.getId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), OpportunityResponse.class);
        OpportunityResponse transferredSecond = fromJson(performGet(
                "/api/v1/opportunities/" + second.getId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), OpportunityResponse.class);
        assertEquals(replacementOwner, transferredFirst.getOwnerActorId());
        assertEquals(replacementOwner, transferredSecond.getOwnerActorId());

        performPost("/api/v1/opportunities/batch/delete", new OpportunityBatchDeleteRequest()
                .setOpportunities(List.of(
                        new OpportunityBatchVersionRequest().setOpportunityId(first.getId())
                                .setVersion(transferredFirst.getVersion() - 1),
                        new OpportunityBatchVersionRequest().setOpportunityId(second.getId())
                                .setVersion(transferredSecond.getVersion()))))
                .andExpect(status().isConflict());
        performGet("/api/v1/opportunities/" + first.getId()).andExpect(status().isOk());
        performGet("/api/v1/opportunities/" + second.getId()).andExpect(status().isOk());

        performPost("/api/v1/opportunities/batch/delete", new OpportunityBatchDeleteRequest()
                .setOpportunities(List.of(
                        new OpportunityBatchVersionRequest().setOpportunityId(first.getId())
                                .setVersion(transferredFirst.getVersion()),
                        new OpportunityBatchVersionRequest().setOpportunityId(second.getId())
                                .setVersion(transferredSecond.getVersion()))))
                .andExpect(status().isOk());
        performGet("/api/v1/opportunities/" + first.getId()).andExpect(status().isNotFound());
        performGet("/api/v1/opportunities/" + second.getId()).andExpect(status().isNotFound());
    }

    @Test
    void opportunityCustomNumericFieldSortUsesCanonicalNumericValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity numeric sort owner");
        CustomerResponse customer = createCustomer("Opportunity numeric sort customer");
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/OPPORTUNITY",
                new FieldCreateRequest().setDisplayName("Opportunity score")
                        .setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        OpportunityResponse two = createOpportunity("Two score", customer.getId(), null,
                Map.of(score.getCode(), List.of("2")));
        OpportunityResponse ten = createOpportunity("Ten score", customer.getId(), null,
                Map.of(score.getCode(), List.of("10")));

        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setCustomSortFieldCode(score.getCode()).setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(two.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(ten.getId()));
        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setSortField("NAME").setCustomSortFieldCode(score.getCode())
                .setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setCustomSortFieldCode(OpportunityFieldCatalog.NAME).setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void opportunityLifecycleKeepsRelationsVersionsAndVisibleSummaryConsistent()
            throws Exception {
        mockMvc.perform(post("/api/v1/opportunities/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(18, jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'OPPORTUNITY' "
                        + "AND is_builtin = 1",
                Integer.class, TENANT_ID));

        CustomerResponse customer = createCustomer("Acme account");
        ContactResponse contact = createContact("Decision maker", customer.getId());
        OpportunityResponse created = createOpportunity(
                "Annual renewal", customer.getId(), contact.getId(), Map.of());

        assertEquals(contact.getId(), created.getPrimaryContactId());
        assertEquals(List.of(contact.getId()), created.getContactIds());
        assertEquals(new BigDecimal("1250.50"), created.getEstimatedAmount());
        assertEquals("OPEN", created.getOutcome());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunity_contact "
                        + "WHERE tenant_id = ? AND opportunity_id = ? AND is_primary = 1",
                Integer.class, TENANT_ID, created.getId()));

        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setCustomerId(customer.getId()).setName("Annual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(created.getId()));
        performPost("/api/v1/opportunities/summary", new OpportunityQueryRequest()
                .setCustomerId(customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.estimatedAmount").value(1250.50));
        performPost("/api/v1/opportunities/" + created.getId() + "/contacts/query",
                Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("Decision maker"))
                .andExpect(jsonPath("$.objectList[0].primary").value(true));

        performPut("/api/v1/opportunities/" + created.getId() + "/star",
                new OpportunityStarUpdateRequest().setStarred(true))
                .andExpect(status().isOk());
        performPost("/api/v1/opportunities/query",
                new OpportunityQueryRequest().setStarred(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].starred").value(true));

        OpportunityResponse staged = fromJson(performPut(
                "/api/v1/opportunities/" + created.getId() + "/stage",
                new OpportunityStageUpdateRequest().setVersion(created.getVersion())
                        .setPipelineCode("direct").setStageCode("negotiation"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        OpportunityResponse won = fromJson(performPut(
                "/api/v1/opportunities/" + created.getId() + "/outcome",
                new OpportunityOutcomeUpdateRequest().setVersion(staged.getVersion())
                        .setOutcome("WON").setRemark("Signed"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        assertTrue(won.getOutcomeTime() != null);
        assertEquals(0, won.getOutcomeTime().getNano() % 1_000);
        assertEquals(won.getOutcomeTime(), jdbc.queryForObject(
                "SELECT outcome_time FROM crm_opportunity "
                        + "WHERE tenant_id = ? AND id = ?",
                LocalDateTime.class, TENANT_ID, created.getId()));
        performPut("/api/v1/opportunities/" + created.getId(),
                update(won, customer.getId()).setName("Blocked closed update"))
                .andExpect(status().isUnprocessableEntity());

        OpportunityResponse reopened = fromJson(performPut(
                "/api/v1/opportunities/" + created.getId() + "/outcome",
                new OpportunityOutcomeUpdateRequest().setVersion(won.getVersion())
                        .setOutcome("OPEN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        OpportunityResponse updated = fromJson(performPut(
                "/api/v1/opportunities/" + created.getId(),
                update(reopened, customer.getId()).setName("Renewal updated"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        performPut("/api/v1/opportunities/" + created.getId(),
                update(reopened, customer.getId()).setName("Stale update"))
                .andExpect(status().isConflict());

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/opportunities/{opportunityId}", created.getId())
                        .param("version", updated.getVersion().toString()))
                .andExpect(status().isOk());
        performGet("/api/v1/opportunities/{opportunityId}", created.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void opportunityContactsBaselineReturnsOnlyACompleteReplaceProjection() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity contacts baseline owner");
        CustomerResponse customer = createCustomer("Contacts baseline account");
        ContactResponse contact = createContact("Contacts baseline person", customer.getId());
        OpportunityResponse opportunity = createOpportunity(
                "Contacts baseline opportunity", customer.getId(), contact.getId(), Map.of());

        performGet("/api/v1/opportunities/{opportunityId}/contacts/baseline", opportunity.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(opportunity.getId()))
                .andExpect(jsonPath("$.version").value(opportunity.getVersion()))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.customerId").value(customer.getId()))
                .andExpect(jsonPath("$.contacts[0].id").value(contact.getId()))
                .andExpect(jsonPath("$.contacts[0].name").value("Contacts baseline person"))
                .andExpect(jsonPath("$.contacts[0].primary").value(true));
    }

    @Test
    void opportunityContactsMustBelongToTheSelectedCustomer() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity relation owner");
        CustomerResponse firstCustomer = createCustomer("First opportunity customer");
        CustomerResponse secondCustomer = createCustomer("Second opportunity customer");
        ContactResponse firstContact = createContact("First customer contact", firstCustomer.getId());
        ContactResponse secondContact = createContact("Second customer contact", secondCustomer.getId());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        performPost("/api/v1/opportunities", opportunityRequest(
                "Cross-customer create", firstCustomer.getId(), secondContact.getId(), Map.of()))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunity WHERE tenant_id = ?", Integer.class, TENANT_ID));

        OpportunityResponse created = createOpportunity(
                "Consistent opportunity", firstCustomer.getId(), firstContact.getId(), Map.of());
        assertEquals(firstCustomer.getId(), jdbc.queryForObject(
                "SELECT customer_id FROM crm_opportunity_contact "
                        + "WHERE tenant_id = ? AND opportunity_id = ? AND contact_id = ?",
                Long.class, TENANT_ID, created.getId(), firstContact.getId()));

        performPut("/api/v1/opportunities/" + created.getId() + "/contacts",
                new OpportunityContactsUpdateRequest().setVersion(created.getVersion())
                        .setContactIds(List.of(secondContact.getId()))
                        .setPrimaryContactId(secondContact.getId()))
                .andExpect(status().isBadRequest());
        performPut("/api/v1/opportunities/" + created.getId() + "/primary-contact",
                new OpportunityPrimaryContactUpdateRequest().setVersion(created.getVersion())
                        .setContactId(secondContact.getId()))
                .andExpect(status().isBadRequest());
        performPut("/api/v1/opportunities/" + created.getId(),
                update(created, secondCustomer.getId()))
                .andExpect(status().isBadRequest());

        assertEquals(firstCustomer.getId(), jdbc.queryForObject(
                "SELECT customer_id FROM crm_opportunity WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, created.getId()));
        assertEquals(firstContact.getId(), jdbc.queryForObject(
                "SELECT contact_id FROM crm_opportunity_contact "
                        + "WHERE tenant_id = ? AND opportunity_id = ? AND is_primary = 1",
                Long.class, TENANT_ID, created.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE crm_contact SET customer_id = ? WHERE tenant_id = ? AND id = ?",
                secondCustomer.getId(), TENANT_ID, firstContact.getId()));
    }

    @Test
    void tenantPipelineCatalogGovernsNewPairsButGrandfathersAnUnchangedPair()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pipeline owner");
        performPost("/api/v1/opportunity-pipelines/query", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].code").value("direct"))
                .andExpect(jsonPath("$.objectList[0].status").value("ACTIVE"));

        OpportunityPipelineResponse pipeline = fromJson(performPost(
                "/api/v1/opportunity-pipelines", new OpportunityPipelineCreateRequest()
                        .setCode("enterprise").setName("Enterprise").setDisplayOrder(30))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityPipelineResponse.class);
        assertEquals("INACTIVE", pipeline.status());
        OpportunityPipelineStageResponse stage = fromJson(performPost(
                "/api/v1/opportunity-pipelines/" + pipeline.id() + "/stages",
                new OpportunityPipelineStageCreateRequest().setCode("discovery")
                        .setName("Discovery").setDisplayOrder(10).setProbability(15))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityPipelineStageResponse.class);
        assertEquals("INACTIVE", stage.status());

        performPut("/api/v1/opportunity-pipelines/" + pipeline.id() + "/status",
                new OpportunityPipelineStatusUpdateRequest().setVersion(pipeline.version())
                        .setStatus("ACTIVE"))
                .andExpect(status().isUnprocessableEntity());
        OpportunityPipelineStageResponse activeStage = fromJson(performPut(
                "/api/v1/opportunity-pipelines/" + pipeline.id() + "/stages/" + stage.id()
                        + "/status", new OpportunityPipelineStageStatusUpdateRequest()
                        .setVersion(stage.version()).setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityPipelineStageResponse.class);
        OpportunityPipelineResponse activePipeline = fromJson(performPut(
                "/api/v1/opportunity-pipelines/" + pipeline.id() + "/status",
                new OpportunityPipelineStatusUpdateRequest().setVersion(pipeline.version())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityPipelineResponse.class);
        performPut("/api/v1/opportunity-pipelines/" + pipeline.id() + "/stages/" + stage.id()
                        + "/status", new OpportunityPipelineStageStatusUpdateRequest()
                        .setVersion(activeStage.version()).setStatus("INACTIVE"))
                .andExpect(status().isUnprocessableEntity());

        CustomerResponse customer = createCustomer("Pipeline account");
        OpportunityResponse created = fromJson(performPost("/api/v1/opportunities",
                opportunityRequest("Enterprise sale", customer.getId(), null, Map.of())
                        .setPipelineCode("enterprise").setStageCode("discovery"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        assertEquals("enterprise", created.getPipelineCode());
        assertEquals("discovery", created.getStageCode());

        OpportunityPipelineResponse inactivePipeline = fromJson(performPut(
                "/api/v1/opportunity-pipelines/" + pipeline.id() + "/status",
                new OpportunityPipelineStatusUpdateRequest().setVersion(activePipeline.version())
                        .setStatus("INACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityPipelineResponse.class);
        assertEquals("INACTIVE", inactivePipeline.status());
        OpportunityResponse updated = fromJson(performPut("/api/v1/opportunities/" + created.getId(),
                update(created, customer.getId()).setName("Enterprise historical sale"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        assertEquals("enterprise", updated.getPipelineCode());
        performPost("/api/v1/opportunities", opportunityRequest(
                "Blocked enterprise sale", customer.getId(), null, Map.of())
                .setPipelineCode("enterprise").setStageCode("discovery"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(1L, activeStage.version());
    }

    @Test
    void pipelineCatalogRoutesRequireDedicatedAuthorityAndHideOtherTenantRows() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pipeline authorization owner");
        long member = 888887L;
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                member, "Pipeline unprivileged member");
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                + "VALUES (?, ?, 'ACTIVE')", TENANT_ID, member);
        // Full generic opportunity authority: the catalog routes must still refuse it, otherwise
        // the rejections below would only prove that an actor without any authority is denied.
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Pipeline generic opportunity role");
        Long genericRoleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Pipeline generic opportunity role");
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, 'opportunities:read', 'TENANT')", TENANT_ID, genericRoleId);
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, 'opportunities:write', 'TENANT')", TENANT_ID, genericRoleId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, member, genericRoleId);

        performAs(member, TENANT_ID, post("/api/v1/opportunity-pipelines/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        performAs(member, TENANT_ID, post("/api/v1/opportunity-pipelines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OpportunityPipelineCreateRequest()
                                .setCode("forbidden").setName("Forbidden").setDisplayOrder(1))))
                .andExpect(status().isForbidden());

        long otherActor = 888886L;
        long otherTenant = 999998L;
        bootstrap(otherActor, otherTenant, "Other pipeline owner");
        Long otherPipelineId = jdbc.queryForObject("SELECT id FROM crm_opportunity_pipeline "
                        + "WHERE tenant_id = ? AND code = 'direct'", Long.class, otherTenant);
        performGet("/api/v1/opportunity-pipelines/{pipelineId}", otherPipelineId)
                .andExpect(status().isNotFound());
    }

    @Test
    void pipelineCatalogEnforcesCapacityAndUsesTheCatalogStageIndex() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pipeline capacity owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (int index = 1; index <= 99; index++) {
            jdbc.update("INSERT INTO crm_opportunity_pipeline "
                            + "(tenant_id, code, name, display_order, status) "
                            + "VALUES (?, ?, ?, ?, 'INACTIVE')",
                    TENANT_ID, "pipeline-" + index, "Pipeline " + index, index);
        }
        performPost("/api/v1/opportunity-pipelines", new OpportunityPipelineCreateRequest()
                        .setCode("overflow-pipeline").setName("Overflow pipeline").setDisplayOrder(101))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(100, jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity_pipeline "
                + "WHERE tenant_id = ? AND status <> 'DELETED'", Integer.class, TENANT_ID));

        for (int index = 1; index <= 98; index++) {
            jdbc.update("INSERT INTO crm_opportunity_pipeline_stage "
                            + "(tenant_id, pipeline_code, code, name, display_order, probability, status) "
                            + "VALUES (?, 'direct', ?, ?, ?, 0, 'INACTIVE')",
                    TENANT_ID, "stage-" + index, "Stage " + index, index);
        }
        Long directPipelineId = jdbc.queryForObject("SELECT id FROM crm_opportunity_pipeline "
                + "WHERE tenant_id = ? AND code = 'direct'", Long.class, TENANT_ID);
        performPost("/api/v1/opportunity-pipelines/" + directPipelineId + "/stages",
                new OpportunityPipelineStageCreateRequest().setCode("overflow-stage")
                        .setName("Overflow stage").setDisplayOrder(101).setProbability(0))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(100, jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity_pipeline_stage "
                + "WHERE tenant_id = ? AND pipeline_code = 'direct' AND status <> 'DELETED'",
                Integer.class, TENANT_ID));
        seedStagePlanNoise(jdbc);
        assertStageQueryPlan(jdbc, " AND status = 'ACTIVE' ",
                "idx_crm_opportunity_pipeline_stage_tenant_status_order", true);
        assertStageQueryPlan(jdbc, "", "idx_crm_opportunity_pipeline_stage_tenant_order", false);
    }

    @Test
    void concurrentPipelineDeactivationAndOpportunityCreationSerializeAtTheCatalogGuard()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pipeline concurrency owner");
        CustomerResponse customer = createCustomer("Pipeline concurrency account");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long pipelineId = jdbc.queryForObject("SELECT id FROM crm_opportunity_pipeline "
                + "WHERE tenant_id = ? AND code = 'direct'", Long.class, TENANT_ID);
        Long pipelineVersion = jdbc.queryForObject("SELECT version FROM crm_opportunity_pipeline "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT_ID, pipelineId);
        String deactivateJson = toJson(new OpportunityPipelineStatusUpdateRequest()
                .setVersion(pipelineVersion).setStatus("INACTIVE"));
        String createJson = toJson(opportunityRequest(
                "Concurrent pipeline assignment", customer.getId(), null, Map.of()));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> deactivate = executor.submit(() -> {
                start.await();
                return performAs(OWNER_ACTOR_ID, TENANT_ID,
                        put("/api/v1/opportunity-pipelines/{pipelineId}/status", pipelineId)
                                .contentType(MediaType.APPLICATION_JSON).content(deactivateJson))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> create = executor.submit(() -> {
                start.await();
                return performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/opportunities")
                                .contentType(MediaType.APPLICATION_JSON).content(createJson))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(200, deactivate.get(30, TimeUnit.SECONDS));
            int createStatus = create.get(30, TimeUnit.SECONDS);
            assertTrue(createStatus == 200 || createStatus == 422);
            assertEquals("INACTIVE", jdbc.queryForObject("SELECT status "
                    + "FROM crm_opportunity_pipeline WHERE tenant_id = ? AND id = ?",
                    String.class, TENANT_ID, pipelineId));
            assertEquals(createStatus == 200 ? 1 : 0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_opportunity WHERE tenant_id = ? "
                            + "AND name = 'Concurrent pipeline assignment'",
                    Integer.class, TENANT_ID));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void catalogGuardSerializesStageAndCatalogLifecycleRaces() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pipeline lifecycle race owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long directPipelineId = jdbc.queryForObject("SELECT id FROM crm_opportunity_pipeline "
                + "WHERE tenant_id = ? AND code = 'direct'", Long.class, TENANT_ID);
        List<Map<String, Object>> activeStages = jdbc.queryForList(
                "SELECT id, version FROM crm_opportunity_pipeline_stage "
                        + "WHERE tenant_id = ? AND pipeline_code = 'direct' AND status = 'ACTIVE' "
                        + "ORDER BY id",
                TENANT_ID);
        assertEquals(2, activeStages.size());
        long firstStageId = ((Number) activeStages.getFirst().get("id")).longValue();
        long firstStageVersion = ((Number) activeStages.getFirst().get("version")).longValue();
        long secondStageId = ((Number) activeStages.get(1).get("id")).longValue();
        long secondStageVersion = ((Number) activeStages.get(1).get("version")).longValue();

        List<Integer> stageDeactivations = race(
                () -> performAs(OWNER_ACTOR_ID, TENANT_ID,
                        put("/api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}/status",
                                        directPipelineId, firstStageId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new OpportunityPipelineStageStatusUpdateRequest()
                                        .setVersion(firstStageVersion).setStatus("INACTIVE"))))
                        .andReturn().getResponse().getStatus(),
                () -> performAs(OWNER_ACTOR_ID, TENANT_ID,
                        put("/api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}/status",
                                        directPipelineId, secondStageId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new OpportunityPipelineStageStatusUpdateRequest()
                                        .setVersion(secondStageVersion).setStatus("INACTIVE"))))
                        .andReturn().getResponse().getStatus());
        assertEquals(1, stageDeactivations.stream().filter(statusCode -> statusCode == 200).count());
        assertEquals(1, stageDeactivations.stream().filter(statusCode -> statusCode == 422).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity_pipeline_stage "
                        + "WHERE tenant_id = ? AND pipeline_code = 'direct' AND status = 'ACTIVE'",
                Integer.class, TENANT_ID));

        String duplicatePayload = toJson(new OpportunityPipelineCreateRequest()
                .setCode("concurrent-duplicate").setName("Concurrent duplicate").setDisplayOrder(40));
        List<Integer> duplicateCreates = race(
                () -> performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/opportunity-pipelines")
                                .contentType(MediaType.APPLICATION_JSON).content(duplicatePayload))
                        .andReturn().getResponse().getStatus(),
                () -> performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/opportunity-pipelines")
                                .contentType(MediaType.APPLICATION_JSON).content(duplicatePayload))
                        .andReturn().getResponse().getStatus());
        assertEquals(1, duplicateCreates.stream().filter(statusCode -> statusCode == 200).count());
        assertEquals(1, duplicateCreates.stream().filter(statusCode -> statusCode == 409).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity_pipeline "
                        + "WHERE tenant_id = ? AND code = 'concurrent-duplicate'",
                Integer.class, TENANT_ID));

        OpportunityPipelineResponse removable = fromJson(performPost("/api/v1/opportunity-pipelines",
                new OpportunityPipelineCreateRequest().setCode("concurrent-delete")
                        .setName("Concurrent delete").setDisplayOrder(50))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityPipelineResponse.class);
        String stagePayload = toJson(new OpportunityPipelineStageCreateRequest()
                .setCode("created-during-delete").setName("Created during delete")
                .setDisplayOrder(10).setProbability(0));
        List<Integer> deleteAndCreate = race(
                () -> performAs(OWNER_ACTOR_ID, TENANT_ID,
                        delete("/api/v1/opportunity-pipelines/{pipelineId}", removable.id())
                                .param("version", removable.version().toString()))
                        .andReturn().getResponse().getStatus(),
                () -> performAs(OWNER_ACTOR_ID, TENANT_ID,
                        post("/api/v1/opportunity-pipelines/{pipelineId}/stages", removable.id())
                                .contentType(MediaType.APPLICATION_JSON).content(stagePayload))
                        .andReturn().getResponse().getStatus());
        assertEquals(1, deleteAndCreate.stream().filter(statusCode -> statusCode == 200).count());
        assertEquals(1, deleteAndCreate.stream().filter(statusCode -> statusCode == 422).count());
        String removableStatus = jdbc.queryForObject("SELECT status FROM crm_opportunity_pipeline "
                        + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, removable.id());
        int retainedStages = jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity_pipeline_stage "
                        + "WHERE tenant_id = ? AND pipeline_code = 'concurrent-delete' "
                        + "AND status <> 'DELETED'", Integer.class, TENANT_ID);
        assertTrue(("DELETED".equals(removableStatus) && retainedStages == 0)
                || ("INACTIVE".equals(removableStatus) && retainedStages == 1));
    }

    @Test
    void fieldWriteDenialPrecedesAnyOpportunityCatalogLookup() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pipeline field-order owner");
        long restrictedActor = 888885L;
        seedActiveMember(restrictedActor, "Pipeline field-order writer");
        CustomerResponse customer = createCustomer("Pipeline field-order account");
        OpportunityResponse existing = fromJson(performPost("/api/v1/opportunities",
                opportunityRequest("Pipeline field-order existing", customer.getId(), null, Map.of())
                        .setCollaborators(List.of(new OpportunityCollaboratorRequest()
                                .setActorId(restrictedActor).setAccess("WRITE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long roleId = createRestrictedOpportunityRole(jdbc, restrictedActor);
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, 'customers:read', 'TENANT')", TENANT_ID, roleId);
        jdbc.update("INSERT INTO role_field_policy (tenant_id, role_id, object_type) "
                + "VALUES (?, ?, 'OPPORTUNITY')", TENANT_ID, roleId);
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, access_level, mask_type) "
                        + "SELECT ?, ?, 'OPPORTUNITY', id, 'WRITE', 'NONE' "
                        + "FROM custom_field_definition WHERE tenant_id = ? "
                        + "AND object_type = 'OPPORTUNITY' "
                        + "AND field_code NOT IN ('pipeline_code', 'stage_code')",
                TENANT_ID, roleId, TENANT_ID);

        OpportunityCreateRequest activePair = opportunityRequest(
                "Field order active", customer.getId(), null, Map.of());
        OpportunityCreateRequest unavailablePair = opportunityRequest(
                "Field order unavailable", customer.getId(), null, Map.of())
                .setPipelineCode("unavailable").setStageCode("unavailable");
        performAs(restrictedActor, TENANT_ID, post("/api/v1/opportunities")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(activePair)))
                .andExpect(status().isForbidden());
        performAs(restrictedActor, TENANT_ID, post("/api/v1/opportunities")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(unavailablePair)))
                .andExpect(status().isForbidden());

        OpportunityUpdateRequest update = update(existing, customer.getId());
        performAs(restrictedActor, TENANT_ID,
                put("/api/v1/opportunities/{opportunityId}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(update)))
                .andExpect(status().isForbidden());
        update.setPipelineCode("unavailable").setStageCode("unavailable");
        performAs(restrictedActor, TENANT_ID,
                put("/api/v1/opportunities/{opportunityId}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantContactsFailClosedAndCustomUniquenessIsDatabaseBacked()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity security owner");
        CustomerResponse customer = createCustomer("Primary account");
        FieldDefinitionResponse registration = fromJson(performPost(
                "/api/v1/fields/objects/OPPORTUNITY",
                new FieldCreateRequest().setDisplayName("Registration")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        createOpportunity("First", customer.getId(), null,
                Map.of(registration.getCode(), List.of("same-key")));
        performPost("/api/v1/opportunities", opportunityRequest(
                "Second", customer.getId(), null,
                Map.of(registration.getCode(), List.of("same-key"))))
                .andExpect(status().isConflict());

        long otherActor = 777777L;
        long otherTenant = 777999L;
        bootstrap(otherActor, otherTenant, "Other opportunity owner");
        CustomerResponse otherCustomer = fromJson(performAs(otherActor, otherTenant,
                post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerCreateRequest().setName("Other account"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ContactResponse otherContact = fromJson(performAs(otherActor, otherTenant,
                post("/api/v1/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactCreateRequest().setName("Other contact")
                                .setCustomerId(otherCustomer.getId()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        Long opportunityId = new JdbcTemplate(dataSource).queryForObject(
                "SELECT id FROM crm_opportunity WHERE tenant_id = ? AND name = 'First'",
                Long.class, TENANT_ID);
        Long version = new JdbcTemplate(dataSource).queryForObject(
                "SELECT version FROM crm_opportunity WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, opportunityId);
        performPut("/api/v1/opportunities/" + opportunityId + "/contacts",
                new OpportunityContactsUpdateRequest().setVersion(version)
                        .setContactIds(List.of(otherContact.getId()))
                        .setPrimaryContactId(otherContact.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignmentScopeAlsoControlsCollaboratorProjection() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity scope owner");
        long scopedActor = 666666L;
        seedActiveMember(scopedActor, "Scoped opportunity member");
        CustomerResponse customer = createCustomer("Scoped account");
        OpportunityCreateRequest request = opportunityRequest(
                "Scoped renewal", customer.getId(), null, Map.of())
                .setCollaborators(List.of(new OpportunityCollaboratorRequest()
                        .setActorId(scopedActor).setAccess("READ")));
        OpportunityResponse created = fromJson(performPost(
                "/api/v1/opportunities", request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Scoped opportunity reader");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Scoped opportunity reader");
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, 'opportunities:read', 'TENANT')",
                TENANT_ID, roleId);
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, 'opportunities:assign', 'SELF')",
                TENANT_ID, roleId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, scopedActor, roleId);

        performAs(scopedActor, TENANT_ID,
                post("/api/v1/opportunities/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].collaborators").isEmpty());
        performAs(scopedActor, TENANT_ID,
                put("/api/v1/opportunities/{opportunityId}/owner", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OpportunityOwnerUpdateRequest()
                                .setVersion(created.getVersion())
                                .setOwnerActorId(scopedActor))))
                .andExpect(status().isNotFound());
        performAs(scopedActor, TENANT_ID,
                delete("/api/v1/opportunities/{opportunityId}/collaborators/me",
                        created.getId())
                        .param("version", created.getVersion().toString()))
                .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunity_collaborator "
                        + "WHERE tenant_id = ? AND opportunity_id = ? AND actor_id = ?",
                Integer.class, TENANT_ID, created.getId(), scopedActor));
    }

    @Test
    void savedViewNormalizesOperatorAwareValuesAndRejectsInvalidTypes()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity filter owner");
        CustomerResponse customer = createCustomer("Opportunity filter account");
        FieldDefinitionResponse email = fromJson(performPost(
                "/api/v1/fields/objects/OPPORTUNITY",
                new FieldCreateRequest().setDisplayName("Buyer email")
                        .setFieldType("EMAIL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        LocalDateTime preciseNextContact = LocalDateTime.of(
                2027, 2, 3, 4, 5, 6, 123_456_789);
        OpportunityResponse typed = fromJson(performPost("/api/v1/opportunities",
                opportunityRequest("Typed filters", customer.getId(), null,
                        Map.of(email.getCode(), List.of("buyer@example.com")))
                        .setNextContactTime(preciseNextContact))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        assertEquals(preciseNextContact.withNano(123_456_000),
                typed.getNextContactTime());
        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setName("Typed filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(typed.getId()))
                .andExpect(jsonPath("$.objectList[0].nextContactTime")
                        .value("2027-02-03T04:05:06.123456"));

        SavedViewResponse pointRange = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("OPPORTUNITY")
                        .setName("Point amount range")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode("estimated_amount").setOperator("BETWEEN")
                                .setValues(List.of("1250.500", "1250.50")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        OpportunityQueryRequest rangeRequest = new OpportunityQueryRequest()
                .setSavedViewId(pointRange.getId());
        performPost("/api/v1/opportunities/query", rangeRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/opportunities/summary", rangeRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        OpportunityQueryRequest temporaryRange = new OpportunityQueryRequest()
                .setConditions(List.of(new FilterConditionRequest()
                        .setFieldCode("estimated_amount").setOperator("BETWEEN")
                        .setValues(List.of("1250.500", "1250.50"))));
        performPost("/api/v1/opportunities/query", temporaryRange)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/opportunities/summary", temporaryRange)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setSavedViewId(pointRange.getId()).setConditions(temporaryRange.getConditions()))
                .andExpect(status().isBadRequest());

        SavedViewResponse nextContact = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("OPPORTUNITY")
                        .setName("Precise next contact")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode("next_contact_time").setOperator("EQ")
                                .setValues(List.of(
                                        "2027-02-03T04:05:06.123456789")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setSavedViewId(nextContact.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));

        SavedViewResponse emailFragment = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("OPPORTUNITY")
                        .setName("Email fragment")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(email.getCode()).setOperator("CONTAINS")
                                .setValues(List.of("example.com")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        performPost("/api/v1/opportunities/query", new OpportunityQueryRequest()
                .setSavedViewId(emailFragment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));

        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("OPPORTUNITY")
                        .setName("Invalid amount")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode("estimated_amount").setOperator("GT")
                                .setValues(List.of("abc")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);

        OpportunityQueryRequest request = new OpportunityQueryRequest()
                .setSavedViewId(view.getId());
        performPost("/api/v1/opportunities/query", request)
                .andExpect(status().isBadRequest());
        performPost("/api/v1/opportunities/summary", request)
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedOpportunityDatesFailAsParameterErrorsBeforePersistence()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity date owner");
        CustomerResponse customer = createCustomer("Opportunity date account");

        performPost("/api/v1/opportunities", opportunityRequest(
                "Unsupported close date", customer.getId(), null, Map.of())
                .setExpectedCloseDate(LocalDate.of(10_000, 1, 1)))
                .andExpect(status().isBadRequest());
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_opportunity WHERE tenant_id = ?",
                Integer.class, TENANT_ID));

        LocalDateTime databaseMaximum = LocalDateTime.of(
                9999, 12, 31, 23, 59, 59, 999_999_999);
        OpportunityResponse created = fromJson(performPost("/api/v1/opportunities",
                opportunityRequest("Supported dates", customer.getId(), null, Map.of())
                        .setNextContactTime(databaseMaximum))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        LocalDateTime normalizedMaximum = databaseMaximum.withNano(999_999_000);
        assertEquals(normalizedMaximum, created.getNextContactTime());
        assertEquals(normalizedMaximum, new JdbcTemplate(dataSource).queryForObject(
                "SELECT next_contact_time FROM crm_opportunity "
                        + "WHERE tenant_id = ? AND id = ?",
                LocalDateTime.class, TENANT_ID, created.getId()));
        OpportunityResponse loaded = fromJson(performGet(
                        "/api/v1/opportunities/{opportunityId}", created.getId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        assertEquals(created.getNextContactTime(), loaded.getNextContactTime());
        performPut("/api/v1/opportunities/" + created.getId(),
                update(created, customer.getId()).setNextContactTime(
                        LocalDateTime.of(10_000, 1, 1, 0, 0)))
                .andExpect(status().isBadRequest());
        assertEquals(created.getVersion(), new JdbcTemplate(dataSource).queryForObject(
                "SELECT version FROM crm_opportunity WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, created.getId()));
    }

    @Test
    void restrictedFieldsCannotBeClearedOrUsedAsRelationshipOracle()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Opportunity field owner");
        long restrictedActor = 555555L;
        seedActiveMember(restrictedActor, "Restricted opportunity writer");
        CustomerResponse customer = createCustomer("Restricted account");
        ContactResponse contact = createContact("Hidden relation", customer.getId());
        OpportunityCollaboratorRequest collaborator = new OpportunityCollaboratorRequest()
                .setActorId(restrictedActor).setAccess("WRITE");
        OpportunityResponse outcomeTarget = fromJson(performPost("/api/v1/opportunities",
                opportunityRequest("Restricted outcome", customer.getId(), null, Map.of())
                        .setCollaborators(List.of(collaborator)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        OpportunityResponse contactTarget = fromJson(performPost("/api/v1/opportunities",
                opportunityRequest("Restricted contact", customer.getId(), contact.getId(),
                        Map.of()).setCollaborators(List.of(collaborator)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        OpportunityResponse closed = fromJson(performPut(
                "/api/v1/opportunities/" + outcomeTarget.getId() + "/outcome",
                new OpportunityOutcomeUpdateRequest().setVersion(outcomeTarget.getVersion())
                        .setOutcome("WON").setRemark("Confidential result"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long roleId = createRestrictedOpportunityRole(jdbc, restrictedActor);
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, 'OPPORTUNITY')",
                TENANT_ID, roleId);
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, access_level, mask_type) "
                        + "SELECT ?, ?, 'OPPORTUNITY', id, 'WRITE', 'NONE' "
                        + "FROM custom_field_definition WHERE tenant_id = ? "
                        + "AND object_type = 'OPPORTUNITY' "
                        + "AND field_code IN ('outcome', 'primary_contact')",
                TENANT_ID, roleId, TENANT_ID);

        performAs(restrictedActor, TENANT_ID,
                put("/api/v1/opportunities/{opportunityId}/outcome", closed.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OpportunityOutcomeUpdateRequest()
                                .setVersion(closed.getVersion()).setOutcome("LOST")
                                .setRemark(null))))
                .andExpect(status().isForbidden());
        performAs(restrictedActor, TENANT_ID,
                put("/api/v1/opportunities/{opportunityId}/primary-contact",
                        contactTarget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OpportunityPrimaryContactUpdateRequest()
                                .setVersion(contactTarget.getVersion())
                                .setContactId(contact.getId()))))
                .andExpect(status().isForbidden());

        assertEquals("Confidential result", jdbc.queryForObject(
                "SELECT outcome_remark FROM crm_opportunity "
                        + "WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, closed.getId()));
        assertEquals(contact.getId(), jdbc.queryForObject(
                "SELECT contact_id FROM crm_opportunity_contact "
                        + "WHERE tenant_id = ? AND opportunity_id = ? AND is_primary = 1",
                Long.class, TENANT_ID, contactTarget.getId()));
    }

    private void assertStageQueryPlan(
            JdbcTemplate jdbc, String statusPredicate, String expectedIndex, boolean statusFiltered) {
        List<Map<String, Object>> explain = jdbc.queryForList("EXPLAIN SELECT * "
                        + "FROM crm_opportunity_pipeline_stage "
                        + "WHERE tenant_id = ? AND pipeline_code = 'direct'" + statusPredicate
                        + " ORDER BY display_order, code, id",
                TENANT_ID);
        String plan = String.valueOf(explain.getFirst().get("EXPLAIN"));
        assertTrue(plan.contains("using " + expectedIndex),
                () -> "Unexpected stage query plan: " + plan);
        assertTrue(plan.contains("tenant_id = " + TENANT_ID)
                        && plan.contains("pipeline_code = 'direct'"),
                () -> "Stage query must use the tenant/pipeline prefix: " + plan);
        if (statusFiltered) {
            assertTrue(plan.contains("status = 'ACTIVE'"),
                    () -> "Status query must constrain status in the chosen index: " + plan);
        }
        assertTrue(!plan.contains("Sort:"),
                () -> "Stage query must preserve index ordering: " + plan);
    }

    private void seedStagePlanNoise(JdbcTemplate jdbc) {
        List<Object[]> tenants = new ArrayList<>();
        List<Object[]> pipelines = new ArrayList<>();
        List<Object[]> stages = new ArrayList<>();
        for (int tenantOffset = 1; tenantOffset <= 128; tenantOffset++) {
            long tenantId = 3_000_000L + tenantOffset;
            tenants.add(new Object[]{tenantId, OWNER_ACTOR_ID});
            pipelines.add(new Object[]{tenantId, "direct", "Noise pipeline", 0});
            for (int stageOffset = 1; stageOffset <= 100; stageOffset++) {
                stages.add(new Object[]{tenantId, "direct", "noise-" + stageOffset,
                        "Noise stage", stageOffset});
            }
        }
        jdbc.batchUpdate("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) "
                + "VALUES (?, ?)", tenants);
        jdbc.batchUpdate("INSERT INTO crm_opportunity_pipeline "
                        + "(tenant_id, code, name, display_order, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                pipelines);
        jdbc.batchUpdate("INSERT INTO crm_opportunity_pipeline_stage "
                        + "(tenant_id, pipeline_code, code, name, display_order, probability, status) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 'INACTIVE')",
                stages);
        jdbc.execute("ANALYZE TABLE crm_opportunity_pipeline_stage");
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> {
                start.await();
                return first.call();
            });
            Future<Integer> secondResult = executor.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS),
                    secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Long createRestrictedOpportunityRole(
            JdbcTemplate jdbc, long restrictedActor) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Restricted opportunity fields");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Restricted opportunity fields");
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) VALUES "
                        + "(?, ?, 'opportunities:read', 'SELF'), "
                        + "(?, ?, 'opportunities:write', 'SELF'), "
                        + "(?, ?, 'contacts:read', 'TENANT')",
                TENANT_ID, roleId, TENANT_ID, roleId, TENANT_ID, roleId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, restrictedActor, roleId);
        return roleId;
    }

    private OpportunityResponse createOpportunity(
            String name, Long customerId, Long contactId,
            Map<String, List<String>> customFields) throws Exception {
        return fromJson(performPost("/api/v1/opportunities",
                opportunityRequest(name, customerId, contactId, customFields))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
    }

    private OpportunityCreateRequest opportunityRequest(
            String name, Long customerId, Long contactId,
            Map<String, List<String>> customFields) {
        OpportunityCreateRequest request = new OpportunityCreateRequest()
                .setName(name).setCustomerId(customerId).setPipelineCode("direct")
                .setStageCode("qualification").setExpectedCloseDate(
                        LocalDate.of(2027, 1, 31))
                .setEstimatedAmount(new BigDecimal("1250.50"))
                .setCustomFields(customFields);
        if (contactId != null) {
            request.setContactIds(List.of(contactId)).setPrimaryContactId(contactId);
        }
        return request;
    }

    private OpportunityUpdateRequest update(
            OpportunityResponse source, Long customerId) {
        return new OpportunityUpdateRequest().setVersion(source.getVersion())
                .setName(source.getName()).setCustomerId(customerId)
                .setPipelineCode(source.getPipelineCode()).setStageCode(source.getStageCode())
                .setExpectedCloseDate(source.getExpectedCloseDate())
                .setEstimatedAmount(source.getEstimatedAmount())
                .setNextContactTime(source.getNextContactTime())
                .setDescription(source.getDescription())
                .setCustomFields(source.getCustomFields());
    }

    private CustomerResponse createCustomer(String name) throws Exception {
        return fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName(name))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
    }

    private ContactResponse createContact(String name, Long customerId) throws Exception {
        return fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName(name).setCustomerId(customerId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private void seedActiveMember(long actorId, String displayName) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile "
                        + "(actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, actorId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT_ID);
    }

    private ResultActions performAs(
            long actorId, long tenantId,
            MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearOpportunityData();
    }

    @Override
    protected void clearTestData() {
        clearOpportunityData();
    }

    private void clearOpportunityData() {
        executeSql("DELETE FROM crm_opportunity_star");
        executeSql("DELETE FROM crm_opportunity_unique_field_value");
        executeSql("DELETE FROM crm_opportunity_field_value");
        executeSql("DELETE FROM crm_opportunity_collaborator");
        executeSql("DELETE FROM crm_opportunity_contact");
        executeSql("DELETE FROM crm_opportunity");
        clearContacts();
        clearCustomers();
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

    private void clearCustomers() {
        executeSql("DELETE FROM crm_customer_star");
        executeSql("DELETE FROM crm_customer_unique_field_value");
        executeSql("DELETE FROM crm_customer_field_value");
        executeSql("DELETE FROM crm_customer_collaborator");
        executeSql("DELETE FROM crm_customer_tag");
        executeSql("DELETE FROM crm_customer_address");
        executeSql("UPDATE crm_customer SET parent_customer_id = NULL");
        executeSql("DELETE FROM crm_customer");
        executeSql("DELETE FROM crm_customer_guard");
    }

    private void clearContacts() {
        executeSql("DELETE FROM crm_contact_pool_claim_history");
        executeSql("DELETE FROM crm_contact_pool_membership");
        executeSql("DELETE FROM crm_contact_star");
        executeSql("DELETE FROM crm_contact_unique_field_value");
        executeSql("DELETE FROM crm_contact_field_value");
        executeSql("DELETE FROM crm_contact_collaborator");
        executeSql("DELETE FROM crm_contact_tag");
        executeSql("DELETE FROM crm_contact_address");
        executeSql("UPDATE crm_contact SET parent_contact_id = NULL");
        executeSql("DELETE FROM crm_contact");
        executeSql("DELETE FROM crm_contact_guard");
    }
}
