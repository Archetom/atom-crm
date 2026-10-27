package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.LeadAddressRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchTagRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadCsvExportRequest;
import io.github.archetom.crm.api.dto.request.LeadDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.api.dto.request.LeadStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadVersionRequest;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldOptionRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.OpenLeadSubmissionRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.response.LeadResponse;
import io.github.archetom.crm.api.dto.response.LeadConversionResponse;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientCreatedResponse;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end lead boundary coverage against the production MySQL mappings. */
@RequiresDatabase
class LeadIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Test
    void leadListReturnsLatestDirectActivitySummary() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead activity summary owner");
        LeadResponse lead = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Activity summary lead"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LocalDateTime latestTime = LocalDateTime.of(2026, 8, 11, 10, 15);
        performPost("/api/v1/activities", new ActivityCreateRequest().setSubjectType("LEAD")
                        .setSubjectId(lead.getId()).setCategory("CALL")
                        .setContent("Earlier lead follow-up")
                        .setOccurredTime(latestTime.minusHours(1)))
                .andExpect(status().isOk());
        performPost("/api/v1/activities", new ActivityCreateRequest().setSubjectType("LEAD")
                        .setSubjectId(lead.getId()).setCategory("CALL")
                        .setContent("Latest lead follow-up")
                        .setOccurredTime(latestTime))
                .andExpect(status().isOk());

        performGet("/api/v1/leads")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].lastActivityTime")
                        .value("2026-08-11T10:15:00"))
                .andExpect(jsonPath("$.objectList[0].lastActivityContent")
                        .value("Latest lead follow-up"));
    }

    @Test
    void leadCsvExportIntersectsIndependentReadAndOwnerExportScopesInMySql() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead CSV owner");
        long tenantReadSelfExportActor = 777701L;
        long selfReadTenantExportActor = 777702L;
        long readOnlyActor = 777703L;
        long exportOnlyActor = 777704L;
        seedActiveMember(tenantReadSelfExportActor, "Lead CSV tenant read self export");
        seedActiveMember(selfReadTenantExportActor, "Lead CSV self read tenant export");
        seedActiveMember(readOnlyActor, "Lead CSV read only");
        seedActiveMember(exportOnlyActor, "Lead CSV export only");

        TenantRoleResponse tenantReadSelfExport = createRole("Lead CSV tenant read self export",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("TENANT"),
                new RoleGrantRequest().setAuthorityCode("leads:export").setDataScope("SELF"));
        TenantRoleResponse selfReadTenantExport = createRole("Lead CSV self read tenant export",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("leads:export").setDataScope("TENANT"));
        TenantRoleResponse readOnly = createRole("Lead CSV read only",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("TENANT"));
        TenantRoleResponse exportOnly = createRole("Lead CSV export only",
                new RoleGrantRequest().setAuthorityCode("leads:export").setDataScope("TENANT"));
        List<RoleFieldAccessRequest> namePolicy = List.of(
                new RoleFieldAccessRequest().setFieldCode(LeadFieldCatalog.NAME)
                        .setAccessLevel("READ").setMaskType("NONE"));
        setFieldPolicy(tenantReadSelfExport.getId(), namePolicy);
        setFieldPolicy(selfReadTenantExport.getId(), namePolicy);
        assignRole(tenantReadSelfExport.getId(), tenantReadSelfExportActor);
        assignRole(selfReadTenantExport.getId(), selfReadTenantExportActor);
        assignRole(readOnly.getId(), readOnlyActor);
        assignRole(exportOnly.getId(), exportOnlyActor);

        LeadResponse selfExported = createLead(OWNER_ACTOR_ID, TENANT_ID,
                "Lead CSV self export included", tenantReadSelfExportActor,
                createTextField("Lead CSV scope marker").getCode(), "included", false);
        createLead(OWNER_ACTOR_ID, TENANT_ID, "Lead CSV self export hidden", OWNER_ACTOR_ID,
                createTextField("Lead CSV hidden marker").getCode(), "hidden", false);
        LeadResponse tenantExported = createLead(OWNER_ACTOR_ID, TENANT_ID,
                "Lead CSV tenant export included", selfReadTenantExportActor,
                createTextField("Lead CSV tenant marker").getCode(), "included", false);

        String selfScope = csvLeadContent(tenantReadSelfExportActor);
        assertTrue(selfScope.contains(selfExported.getId().toString()));
        assertFalse(selfScope.contains(tenantExported.getId().toString()));
        String tenantScope = csvLeadContent(selfReadTenantExportActor);
        assertTrue(tenantScope.contains(tenantExported.getId().toString()));
        assertFalse(tenantScope.contains(selfExported.getId().toString()));
        performAs(readOnlyActor, TENANT_ID, leadCsvExportRequest()).andExpect(status().isForbidden());
        performAs(exportOnlyActor, TENANT_ID, leadCsvExportRequest()).andExpect(status().isForbidden());
    }

    @Test
    void leadCollaboratorReadReturnsAnAuthoritativeVersionedBaseline() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead collaborator owner");
        long collaboratorActor = 777702L;
        seedActiveMember(collaboratorActor, "Lead collaborator");
        LeadResponse lead = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Collaborator baseline lead"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadResponse updated = replaceCollaborators(lead, collaboratorActor, "READ");

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                get("/api/v1/leads/{leadId}/collaborators", lead.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(updated.getVersion()))
                .andExpect(jsonPath("$.callerState").value("OWNER"))
                .andExpect(jsonPath("$.collaborators[0].actorId").value(collaboratorActor))
                .andExpect(jsonPath("$.collaborators[0].access").value("READ"));
    }

    @Test
    void leadListSupportsSafeServerSideSorting() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead sort owner");
        LeadResponse zebra = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Zebra lead"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadResponse alpha = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Aardvark lead"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        performGetWithParams("/api/v1/leads", Map.of(
                        "sortField", "NAME", "sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(zebra.getId()));
        performGetWithParams("/api/v1/leads", Map.of(
                        "sortField", "DEAL_TIME", "sortDirection", "ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leadCustomNumericFieldSortUsesCanonicalNumericValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead numeric sort owner");
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Lead score").setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        LeadResponse two = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Two score")
                        .setCustomFields(Map.of(score.getCode(), List.of("2"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadResponse ten = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Ten score")
                        .setCustomFields(Map.of(score.getCode(), List.of("10"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        mockMvc.perform(withTestActor(get("/api/v1/leads")
                        .param("customSortFieldCode", score.getCode())
                        .param("sortDirection", "ASC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(two.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(ten.getId()));
        mockMvc.perform(withTestActor(get("/api/v1/leads")
                        .param("sortField", "NAME")
                        .param("customSortFieldCode", score.getCode())
                        .param("sortDirection", "ASC")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withTestActor(get("/api/v1/leads")
                        .param("customSortFieldCode", LeadFieldCatalog.NAME)
                        .param("sortDirection", "ASC")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void namePrefixSearchTreatsLikeMetacharactersLiterally() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead search owner");
        performPost("/api/v1/leads", new LeadCreateRequest().setName("ACME%Literal"))
                .andExpect(status().isOk());
        performPost("/api/v1/leads", new LeadCreateRequest().setName("ACME_Literal"))
                .andExpect(status().isOk());
        performPost("/api/v1/leads", new LeadCreateRequest().setName("ACME Plain"))
                .andExpect(status().isOk());

        performGetWithParams("/api/v1/leads", Map.of("name", "ACME%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("ACME%Literal"));
        performGetWithParams("/api/v1/leads", Map.of("name", "ACME_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("ACME_Literal"));
        performGetWithParams("/api/v1/leads", Map.of("name", "Literal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
    }

    @Test
    void unchangedMultiValueJsonDoesNotDriftItsAuditTimestamp() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead JSON owner");
        FieldDefinitionResponse regions = fromJson(performPost("/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Regions").setFieldType("MULTI_SELECT")
                        .setOptions(List.of(
                                new FieldOptionRequest().setValue("east").setLabel("East"),
                                new FieldOptionRequest().setValue("west").setLabel("West"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        LeadResponse lead = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Multi-region lead")
                        .setCustomFields(Map.of(regions.getCode(), List.of("east", "west"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime sentinel = LocalDateTime.of(2000, 1, 1, 0, 0);
        jdbc.update("UPDATE crm_lead_field_value SET updated_time = ? "
                        + "WHERE tenant_id = ? AND lead_id = ? AND field_id = ?",
                sentinel, TENANT_ID, lead.getId(), regions.getId());

        performPut("/api/v1/leads/" + lead.getId(),
                new LeadUpdateRequest().setVersion(lead.getVersion()).setName("Renamed lead"))
                .andExpect(status().isOk());

        assertEquals(sentinel, jdbc.queryForObject(
                "SELECT updated_time FROM crm_lead_field_value "
                        + "WHERE tenant_id = ? AND lead_id = ? AND field_id = ?",
                LocalDateTime.class, TENANT_ID, lead.getId(), regions.getId()));
    }

    @Test
    void nearbySearchCrossesTheAntimeridian() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead antimeridian owner");
        LeadResponse east = createGeocodedLead("Dateline east", "179.999");
        LeadResponse west = createGeocodedLead("Dateline west", "-179.999");

        performGetWithParams("/api/v1/leads/nearby", Map.of(
                        "longitude", "179.999", "latitude", "0",
                        "radiusMeters", "1000", "page", "1", "size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(2))
                .andExpect(jsonPath("$.objectList[0].lead.id").value(east.getId()))
                .andExpect(jsonPath("$.objectList[1].lead.id").value(west.getId()));
    }

    @Test
    void leadLifecycleUsesCustomFieldsSavedViewsNearbyAndOptimisticLocking() throws Exception {
        mockMvc.perform(get("/api/v1/leads")).andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead Owner");
        performGetWithParams("/api/v1/leads/nearby", Map.of(
                        "longitude", "121.4737", "latitude", "31.2304", "radiusMeters", "0"))
                .andExpect(status().isBadRequest());
        assertEquals(18, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'LEAD' AND is_builtin = 1",
                Integer.class, TENANT_ID));

        FieldDefinitionResponse segment = createTextField("Segment");
        LeadResponse lead = createLead(
                OWNER_ACTOR_ID, TENANT_ID, "Acme Shanghai", OWNER_ACTOR_ID,
                segment.getCode(), "enterprise", true);
        assertEquals(List.of("Enterprise"), lead.getTags());
        assertEquals(List.of("enterprise"), lead.getCustomFields().get(segment.getCode()));
        assertEquals("hq", lead.getAddresses().getFirst().getKey());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO crm_lead_address "
                        + "(tenant_id, lead_id, address_key, address_line, longitude, latitude, is_primary) "
                        + "VALUES (?, ?, 'longitude-only', 'Invalid', 121.0000000, NULL, 0)",
                TENANT_ID, lead.getId()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO crm_lead_address "
                        + "(tenant_id, lead_id, address_key, address_line, longitude, latitude, is_primary) "
                        + "VALUES (?, ?, 'latitude-only', 'Invalid', NULL, 31.0000000, 0)",
                TENANT_ID, lead.getId()));

        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("LEAD").setName("Enterprise")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(segment.getCode()).setOperator("EQ")
                                .setValues(List.of("enterprise")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        mockMvc.perform(withTestActor(get("/api/v1/leads")
                        .param("savedViewId", view.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(lead.getId()));
        performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/leads/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadQueryRequest().setConditions(List.of(
                                new FilterConditionRequest().setFieldCode(segment.getCode())
                                        .setOperator("EQ").setValues(List.of("enterprise")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(lead.getId()));

        performPut("/api/v1/leads/" + lead.getId() + "/star",
                new LeadStarUpdateRequest().setStarred(true))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(get("/api/v1/leads").param("starred", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].starred").value(true));
        performPost("/api/v1/leads/duplicate-check",
                new LeadDuplicateCheckRequest().setName("Acme Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        performGetWithParams("/api/v1/leads/nearby", Map.of(
                        "longitude", "121.4737", "latitude", "31.2304",
                        "radiusMeters", "1000", "page", "1", "size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].lead.id").value(lead.getId()));

        LeadResponse updated = fromJson(performPut("/api/v1/leads/" + lead.getId(),
                new LeadUpdateRequest().setVersion(lead.getVersion())
                        .setName("Acme Updated"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertTrue(updated.getVersion() > lead.getVersion());
        assertEquals("+8613800138000", updated.getPhoneNumber());
        LeadResponse cleared = fromJson(performPut("/api/v1/leads/" + lead.getId(),
                new LeadUpdateRequest().setVersion(updated.getVersion())
                        .setPhoneNumber(""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertNull(cleared.getPhoneNumber());
        performPut("/api/v1/leads/" + lead.getId(),
                new LeadUpdateRequest().setVersion(lead.getVersion())
                        .setName("Stale update"))
                .andExpect(status().isConflict());

        long otherActorId = 666666L;
        long otherTenantId = 666001L;
        bootstrap(otherActorId, otherTenantId, "Other Lead Owner");
        performAs(otherActorId, otherTenantId, get("/api/v1/leads/{leadId}", lead.getId()))
                .andExpect(status().isNotFound());

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/leads/{leadId}", lead.getId())
                        .param("version", cleared.getVersion().toString()))
                .andExpect(status().isOk());
        performGet("/api/v1/leads/{leadId}", lead.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void conversionAtomicallyCreatesCustomerAndContactThenFinalizesTheLead() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead conversion owner");
        LeadResponse lead = fromJson(performPost("/api/v1/leads", new LeadCreateRequest()
                .setName("Conversion account").setPhoneNumber("+8613800138000")
                .setTelephone("021-12345678").setJobTitle("Director")
                .setEmail("conversion@example.com").setNextContactTime(
                        LocalDateTime.of(2026, 8, 2, 9, 30))
                .setTags(List.of("Qualified")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        LeadConversionResponse conversion = fromJson(performPost(
                "/api/v1/leads/" + lead.getId() + "/convert",
                new LeadConvertRequest().setVersion(lead.getVersion())
                        .setCustomerName("Converted customer").setContactName("Decision maker"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadConversionResponse.class);
        assertEquals(lead.getId(), conversion.getLeadId());
        assertTrue(conversion.getCustomerId() > 0);
        assertTrue(conversion.getContactId() > 0);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("CONVERTED", jdbc.queryForObject(
                "SELECT status FROM crm_lead WHERE tenant_id = ? AND id = ?", String.class,
                TENANT_ID, lead.getId()));
        assertEquals(conversion.getCustomerId(), jdbc.queryForObject(
                "SELECT customer_id FROM crm_lead WHERE tenant_id = ? AND id = ?", Long.class,
                TENANT_ID, lead.getId()));
        assertEquals(conversion.getContactId(), jdbc.queryForObject(
                "SELECT converted_contact_id FROM crm_lead WHERE tenant_id = ? AND id = ?", Long.class,
                TENANT_ID, lead.getId()));
        assertEquals(conversion.getCustomerId(), jdbc.queryForObject(
                "SELECT customer_id FROM crm_contact WHERE tenant_id = ? AND id = ?", Long.class,
                TENANT_ID, conversion.getContactId()));
        assertEquals("Decision maker", jdbc.queryForObject(
                "SELECT name FROM crm_contact WHERE tenant_id = ? AND id = ?", String.class,
                TENANT_ID, conversion.getContactId()));
        assertEquals("Qualified", jdbc.queryForObject(
                "SELECT tag_name FROM crm_customer_tag WHERE tenant_id = ? AND customer_id = ?", String.class,
                TENANT_ID, conversion.getCustomerId()));
        LeadResponse reloaded = fromJson(performGet("/api/v1/leads/{leadId}", lead.getId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals("CONVERTED", reloaded.getStatus());
        assertEquals(conversion.getContactId(), reloaded.getConvertedContactId());
        performGet("/api/v1/leads").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(lead.getId()));
        performGetWithParams("/api/v1/leads", Map.of("converted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(lead.getId()))
                .andExpect(jsonPath("$.objectList[0].status").value("CONVERTED"));
        performGetWithParams("/api/v1/leads", Map.of("converted", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));

        performPut("/api/v1/leads/" + lead.getId(),
                new LeadUpdateRequest().setVersion(conversion.getLeadVersion())
                        .setName("Mutation after conversion"))
                .andExpect(status().is(422));
        performPost("/api/v1/leads/" + lead.getId() + "/convert",
                new LeadConvertRequest().setVersion(conversion.getLeadVersion()))
                .andExpect(status().is(422));
    }

    @Test
    void conversionRejectsMaskedLeadValuesInsteadOfCopyingTheirRawValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Conversion field-policy owner");
        long converterActorId = 777779L;
        seedActiveMember(converterActorId, "Restricted converter");
        TenantRoleResponse converter = createRole("Restricted lead converter",
                new RoleGrantRequest().setAuthorityCode("leads:convert").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("leads:write").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:write").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("contacts:write").setDataScope("SELF"));
        setFieldPolicy(converter.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("customer")
                        .setAccessLevel("WRITE").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("status")
                        .setAccessLevel("WRITE").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("deal_status")
                        .setAccessLevel("WRITE").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("owner_actor")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("phone_number")
                        .setAccessLevel("READ").setMaskType("PARTIAL")));
        assignRole(converter.getId(), converterActorId);

        LeadResponse lead = fromJson(performPost("/api/v1/leads", new LeadCreateRequest()
                .setName("Restricted source").setPhoneNumber("+8613800138000")
                .setOwnerActorId(converterActorId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        performAs(converterActorId, TENANT_ID,
                post("/api/v1/leads/{leadId}/convert", lead.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadConvertRequest().setVersion(lead.getVersion())
                                .setCustomerName("Allowed target customer")
                                .setContactName("Allowed target contact"))))
                .andExpect(status().isForbidden());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM crm_lead WHERE tenant_id = ? AND id = ?", String.class,
                TENANT_ID, lead.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer WHERE tenant_id = ?", Integer.class, TENANT_ID));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact WHERE tenant_id = ?", Integer.class, TENANT_ID));
    }

    @Test
    void conversionRollsBackWhenLeadOwnerIsHidden() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Conversion owner-policy owner");
        long converterActorId = 777781L;
        seedActiveMember(converterActorId, "Owner-hidden converter");
        TenantRoleResponse converter = createRole("Owner-hidden lead converter",
                new RoleGrantRequest().setAuthorityCode("leads:convert").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("leads:write").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:write").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("contacts:write").setDataScope("SELF"));
        setFieldPolicy(converter.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("customer")
                        .setAccessLevel("WRITE").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("status")
                        .setAccessLevel("WRITE").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("deal_status")
                        .setAccessLevel("WRITE").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("owner_actor")
                        .setAccessLevel("HIDDEN").setMaskType("NONE")));
        assignRole(converter.getId(), converterActorId);

        LeadResponse lead = fromJson(performPost("/api/v1/leads", new LeadCreateRequest()
                .setName("Owner-hidden source").setOwnerActorId(OWNER_ACTOR_ID)
                .setCollaborators(List.of(new LeadCollaboratorRequest()
                        .setActorId(converterActorId).setAccess("WRITE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        performAs(converterActorId, TENANT_ID,
                post("/api/v1/leads/{leadId}/convert", lead.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadConvertRequest().setVersion(lead.getVersion())
                                .setCustomerName("Owner-hidden customer")
                                .setContactName("Owner-hidden contact"))))
                .andExpect(status().isForbidden());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM crm_lead WHERE tenant_id = ? AND id = ?", String.class,
                TENANT_ID, lead.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer WHERE tenant_id = ?", Integer.class, TENANT_ID));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact WHERE tenant_id = ?", Integer.class, TENANT_ID));
    }

    @Test
    void convertedContactAssociationFollowsIndependentContactVisibility() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Converted contact visibility owner");
        LeadResponse lead = fromJson(performPost("/api/v1/leads", new LeadCreateRequest()
                .setName("Contact visibility source"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadConversionResponse conversion = fromJson(performPost(
                "/api/v1/leads/" + lead.getId() + "/convert",
                new LeadConvertRequest().setVersion(lead.getVersion())
                        .setCustomerName("Visibility customer").setContactName("Visibility contact"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadConversionResponse.class);

        long readerActorId = 777780L;
        seedActiveMember(readerActorId, "Lead-only reader");
        TenantRoleResponse leadReader = createRole("Lead-only conversion reader",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("TENANT"));
        setFieldPolicy(leadReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(leadReader.getId(), readerActorId);

        LeadResponse withoutContactRead = fromJson(performAs(readerActorId, TENANT_ID,
                get("/api/v1/leads/{leadId}", lead.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertNull(withoutContactRead.getConvertedContactId());

        TenantRoleResponse contactReader = createRole("Converted contact reader",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("TENANT"));
        assignRole(contactReader.getId(), readerActorId);
        LeadResponse withContactRead = fromJson(performAs(readerActorId, TENANT_ID,
                get("/api/v1/leads/{leadId}", lead.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals(conversion.getContactId(), withContactRead.getConvertedContactId());
    }

    @Test
    void publicIntakeRequiresTheConfiguredApiKeyAndNeverAcceptsTenantOrOwnerFromTheBody()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead intake owner");
        LeadIntakeClientCreatedResponse created = fromJson(performPost("/api/v1/lead-intake-clients",
                new LeadIntakeClientCreateRequest())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadIntakeClientCreatedResponse.class);
        LeadIntakeClientResponse client = created.getClient();
        assertTrue(client.getActive());
        assertTrue(client.getKeyId().matches("[A-Za-z0-9_-]{8,64}"));
        String[] apiKeyParts = created.getApiKey().split("\\.");
        assertEquals(3, apiKeyParts.length);
        assertTrue(apiKeyParts[0].matches("[A-Za-z0-9_-]{11}"));
        assertEquals(client.getKeyId(), apiKeyParts[1]);
        String secret = apiKeyParts[2];
        assertTrue(secret.length() >= 32);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertFalse(secret.equals(jdbc.queryForObject(
                "SELECT api_key_hash FROM crm_lead_intake_client WHERE id = ?", String.class,
                client.getId())));

        mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", created.getApiKey())
                        .content(toJson(new OpenLeadSubmissionRequest().setName("Public account")
                                .setPhoneNumber("+8613800138000")
                                .setDescription("Submitted through the public intake"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ownerActorId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? "
                        + "AND owner_actor_id = ? AND name = 'Public account'",
                Integer.class, TENANT_ID, OWNER_ACTOR_ID));
        String caseSensitiveKeyId = "AbCdEfGhIjKlMnOpQrStUvWx";
        jdbc.update("UPDATE crm_lead_intake_client SET key_id = ? WHERE id = ?",
                caseSensitiveKeyId, client.getId());
        String differentlyCasedKeyId = caseSensitiveKeyId.toLowerCase(java.util.Locale.ROOT);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead_intake_client WHERE tenant_id = ? AND key_id = ?",
                Integer.class, TENANT_ID, differentlyCasedKeyId));
        mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", apiKeyParts[0] + "."
                                + differentlyCasedKeyId + "." + secret)
                        .content(toJson(new OpenLeadSubmissionRequest().setName("Case rejected account"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OpenLeadSubmissionRequest().setName("Rejected account"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", apiKeyParts[0] + "." + caseSensitiveKeyId
                                + ".wrong-key-material-that-is-long-enough-000")
                        .content(toJson(new OpenLeadSubmissionRequest().setName("Rejected account"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void intakeClientListReturnsOnlyCredentialFreeSummaries() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead intake list owner");
        LeadIntakeClientCreatedResponse created = fromJson(performPost("/api/v1/lead-intake-clients",
                new LeadIntakeClientCreateRequest()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), LeadIntakeClientCreatedResponse.class);

        mockMvc.perform(withTestActor(get("/api/v1/lead-intake-clients")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.getClient().getId()))
                .andExpect(jsonPath("$[0].keyId").value(created.getClient().getKeyId()))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$[0].ownerActorId").value(OWNER_ACTOR_ID));
    }

    @Test
    void publicIntakeAppliesConfiguredRateLimitAndClientDeactivationImmediately() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead intake limiter owner");
        LeadIntakeClientCreatedResponse created = fromJson(performPost("/api/v1/lead-intake-clients",
                new LeadIntakeClientCreateRequest()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), LeadIntakeClientCreatedResponse.class);
        LeadIntakeClientResponse client = created.getClient();

        for (int index = 0; index < 2; index++) {
            mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                            .header("X-API-Key", created.getApiKey())
                            .content(toJson(new OpenLeadSubmissionRequest()
                                    .setName("Rate-limited lead " + index))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", created.getApiKey())
                        .content(toJson(new OpenLeadSubmissionRequest().setName("Over limit"))))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(withTestActor(delete("/api/v1/lead-intake-clients/{clientId}", client.getId())
                        .param("version", client.getVersion().toString())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", created.getApiKey())
                        .content(toJson(new OpenLeadSubmissionRequest().setName("Deactivated"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicIntakeRejectsAClientAfterItsOwnerMembershipIsSuspended() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead intake suspended owner");
        LeadIntakeClientCreatedResponse created = fromJson(performPost("/api/v1/lead-intake-clients",
                new LeadIntakeClientCreateRequest()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), LeadIntakeClientCreatedResponse.class);
        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                TENANT_ID, OWNER_ACTOR_ID);

        mockMvc.perform(post("/api/v1/open/leads").contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", created.getApiKey())
                        .content(toJson(new OpenLeadSubmissionRequest().setName("Suspended"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerChangeEnforcesAuthorityVisibilityAndOptimisticLocking() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead assignment owner");
        long firstOwner = 777790L;
        long secondOwner = 777791L;
        seedActiveMember(firstOwner, "First lead owner");
        seedActiveMember(secondOwner, "Second lead owner");
        TenantRoleResponse selfReader = createRole("Lead owner visibility",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("SELF"));
        setFieldPolicy(selfReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(selfReader.getId(), firstOwner);
        assignRole(selfReader.getId(), secondOwner);
        LeadResponse lead = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Assigned lead").setOwnerActorId(firstOwner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        performAs(firstOwner, TENANT_ID, put("/api/v1/leads/{leadId}/owner", lead.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadOwnerUpdateRequest().setVersion(lead.getVersion())
                                .setOwnerActorId(secondOwner))))
                .andExpect(status().isForbidden());

        LeadResponse assigned = fromJson(performPut("/api/v1/leads/" + lead.getId() + "/owner",
                new LeadOwnerUpdateRequest().setVersion(lead.getVersion())
                        .setOwnerActorId(secondOwner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        performAs(firstOwner, TENANT_ID, get("/api/v1/leads/{leadId}", lead.getId()))
                .andExpect(status().isNotFound());
        performAs(secondOwner, TENANT_ID, get("/api/v1/leads/{leadId}", lead.getId()))
                .andExpect(status().isOk());
        performPut("/api/v1/leads/" + lead.getId() + "/owner",
                new LeadOwnerUpdateRequest().setVersion(lead.getVersion())
                        .setOwnerActorId(firstOwner))
                .andExpect(status().isConflict());
        assertEquals(secondOwner, assigned.getOwnerActorId());
    }

    @Test
    void batchOwnerAndDeleteAreVersionedAndAtomic() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead batch owner");
        long replacementOwner = 777792L;
        seedActiveMember(replacementOwner, "Lead batch replacement owner");
        LeadResponse first = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Lead batch first"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadResponse second = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Lead batch second"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        performPut("/api/v1/leads/batch/owner", new LeadBatchOwnerUpdateRequest()
                .setOwnerActorId(replacementOwner).setLeads(List.of(
                        new LeadBatchVersionRequest().setLeadId(second.getId())
                                .setVersion(second.getVersion()),
                        new LeadBatchVersionRequest().setLeadId(first.getId())
                                .setVersion(first.getVersion()))))
                .andExpect(status().isOk());
        LeadResponse transferredFirst = fromJson(performGet("/api/v1/leads/" + first.getId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadResponse transferredSecond = fromJson(performGet("/api/v1/leads/" + second.getId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals(replacementOwner, transferredFirst.getOwnerActorId());
        assertEquals(replacementOwner, transferredSecond.getOwnerActorId());

        performPost("/api/v1/leads/batch/delete", new LeadBatchDeleteRequest().setLeads(List.of(
                new LeadBatchVersionRequest().setLeadId(first.getId())
                        .setVersion(transferredFirst.getVersion() - 1),
                new LeadBatchVersionRequest().setLeadId(second.getId())
                        .setVersion(transferredSecond.getVersion()))))
                .andExpect(status().isConflict());
        performGet("/api/v1/leads/" + first.getId()).andExpect(status().isOk());
        performGet("/api/v1/leads/" + second.getId()).andExpect(status().isOk());

        performPost("/api/v1/leads/batch/delete", new LeadBatchDeleteRequest().setLeads(List.of(
                new LeadBatchVersionRequest().setLeadId(first.getId())
                        .setVersion(transferredFirst.getVersion()),
                new LeadBatchVersionRequest().setLeadId(second.getId())
                        .setVersion(transferredSecond.getVersion()))))
                .andExpect(status().isOk());
        performGet("/api/v1/leads/" + first.getId()).andExpect(status().isNotFound());
        performGet("/api/v1/leads/" + second.getId()).andExpect(status().isNotFound());
    }

    @Test
    void conversionRollsBackCustomerWhenContactPersistenceFails() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead conversion rollback owner");
        FieldDefinitionResponse uniqueContactField = fromJson(performPost(
                "/api/v1/fields/objects/CONTACT",
                new FieldCreateRequest().setDisplayName("Conversion unique contact")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        performPost("/api/v1/contacts", new ContactCreateRequest()
                .setName("Existing contact")
                .setCustomFields(Map.of(uniqueContactField.getCode(), List.of("duplicate"))))
                .andExpect(status().isOk());
        LeadResponse lead = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Rollback source"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        int customersBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer WHERE tenant_id = ?", Integer.class, TENANT_ID);

        performPost("/api/v1/leads/" + lead.getId() + "/convert",
                new LeadConvertRequest().setVersion(lead.getVersion())
                        .setCustomerName("Rolled back customer")
                        .setContactName("Duplicate contact")
                        .setContactCustomFields(Map.of(
                                uniqueContactField.getCode(), List.of("duplicate"))))
                .andExpect(status().isConflict());

        assertEquals(customersBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer WHERE tenant_id = ?", Integer.class, TENANT_ID));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM crm_lead WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, lead.getId()));
    }

    @Test
    void crossTenantLeadWriteFailsClosed() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead tenant owner");
        LeadResponse lead = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Tenant-private lead"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        long otherActor = 777792L;
        long otherTenant = 777793L;
        bootstrap(otherActor, otherTenant, "Other tenant owner");

        performAs(otherActor, otherTenant, put("/api/v1/leads/{leadId}", lead.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadUpdateRequest().setVersion(lead.getVersion())
                                .setName("Cross-tenant overwrite"))))
                .andExpect(status().isNotFound());
        assertEquals("Tenant-private lead", new JdbcTemplate(dataSource).queryForObject(
                "SELECT name FROM crm_lead WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, lead.getId()));
    }

    @Test
    void hierarchyReadsEveryVisibleNodeAllowedByTheWriteLimit() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead hierarchy read owner");
        LeadResponse root = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Large hierarchy root"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        List<Object[]> descendants = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            descendants.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, root.getId(),
                    "Large hierarchy child " + index});
        }
        new JdbcTemplate(dataSource).batchUpdate("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, parent_lead_id, name, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                descendants);

        performGet("/api/v1/leads/{leadId}/hierarchy", root.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.length()").value(601));
    }

    @Test
    void notEqualExcludesEmptyBuiltInAndCustomValuesConsistently() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead not-equal owner");
        FieldDefinitionResponse segment = createTextField("Not-equal segment");
        performPost("/api/v1/leads", new LeadCreateRequest().setName("Equal lead")
                .setJobTitle("Director")
                .setCustomFields(Map.of(segment.getCode(), List.of("enterprise"))))
                .andExpect(status().isOk());
        LeadResponse different = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Different lead").setJobTitle("Manager")
                        .setCustomFields(Map.of(segment.getCode(), List.of("growth"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        performPost("/api/v1/leads", new LeadCreateRequest().setName("Empty lead"))
                .andExpect(status().isOk());

        assertSavedViewMatches("Built-in not equal", new FilterConditionRequest()
                .setFieldCode("job_title").setOperator("NOT_EQ")
                .setValues(List.of("Director")), different.getId());
        assertSavedViewMatches("Custom not equal", new FilterConditionRequest()
                .setFieldCode(segment.getCode()).setOperator("NOT_EQ")
                .setValues(List.of("enterprise")), different.getId());
    }

    @Test
    void activeLeadIntakeClientsAreBoundedPerTenant() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead intake client limit owner");

        LeadIntakeClientResponse first = null;
        for (int index = 0; index < LeadIntakeClient.MAX_ACTIVE_CLIENTS_PER_TENANT; index++) {
            LeadIntakeClientCreatedResponse created = fromJson(
                    performPost("/api/v1/lead-intake-clients", new LeadIntakeClientCreateRequest())
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                    LeadIntakeClientCreatedResponse.class);
            if (first == null) {
                first = created.getClient();
            }
        }

        performPost("/api/v1/lead-intake-clients", new LeadIntakeClientCreateRequest())
                .andExpect(status().isUnprocessableContent());

        // The bound counts ACTIVE clients: deactivating one frees exactly one slot.
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/lead-intake-clients/{clientId}", first.getId())
                        .param("version", String.valueOf(first.getVersion())))
                .andExpect(status().isOk());
        performPost("/api/v1/lead-intake-clients", new LeadIntakeClientCreateRequest())
                .andExpect(status().isOk());
        performPost("/api/v1/lead-intake-clients", new LeadIntakeClientCreateRequest())
                .andExpect(status().isUnprocessableContent());

        // The bound is per tenant: a saturated tenant cannot exhaust another one.
        long otherActor = 777_791L;
        long otherTenant = 999_997L;
        bootstrap(otherActor, otherTenant, "Other intake client owner");
        performAs(otherActor, otherTenant, post("/api/v1/lead-intake-clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadIntakeClientCreateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void intakeClientCreationRequiresItsOwnerToSubmitPublicLeads() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead intake client capability owner");
        long intakeManagerActorId = 777782L;
        seedActiveMember(intakeManagerActorId, "Intake manager");
        TenantRoleResponse manager = createRole("Lead intake manager",
                new RoleGrantRequest().setAuthorityCode("leads:intake-manage"));
        assignRole(manager.getId(), intakeManagerActorId);

        performAs(intakeManagerActorId, TENANT_ID,
                post("/api/v1/lead-intake-clients").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadIntakeClientCreateRequest())))
                .andExpect(status().isForbidden());

        TenantRoleResponse submitter = createRole("Lead intake submitter",
                new RoleGrantRequest().setAuthorityCode("leads:write").setDataScope("SELF"));
        assignRole(submitter.getId(), intakeManagerActorId);
        performAs(intakeManagerActorId, TENANT_ID,
                post("/api/v1/lead-intake-clients").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadIntakeClientCreateRequest())))
                .andExpect(status().isForbidden());

        setFieldPolicy(submitter.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("WRITE").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("owner_actor")
                        .setAccessLevel("WRITE").setMaskType("NONE")));
        performAs(intakeManagerActorId, TENANT_ID,
                post("/api/v1/lead-intake-clients").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadIntakeClientCreateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void leadCoreFieldsCustomerSavedViewsAndExplicitUnlinkRoundTrip() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead core-field owner");
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Associated customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        LeadResponse parent = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Associated parent"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LocalDateTime nextContactTime = LocalDateTime.of(2026, 8, 1, 9, 30);
        LeadResponse associated = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Associated lead")
                        .setPhoneNumber("+8613800138000").setTelephone("021-12345678")
                        .setJobTitle("Sales director").setEmail("sales@example.com")
                        .setWebsite("https://example.com").setDescription("Important lead")
                        .setNextContactTime(nextContactTime).setCustomerId(customer.getId())
                        .setParentLeadId(parent.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals("Sales director", associated.getJobTitle());
        assertEquals(nextContactTime, associated.getNextContactTime());
        assertEquals(customer.getId(), associated.getCustomerId());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("Sales director", jdbc.queryForObject(
                "SELECT job_title FROM crm_lead WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, associated.getId()));
        assertEquals(nextContactTime, jdbc.queryForObject(
                "SELECT next_contact_time FROM crm_lead WHERE tenant_id = ? AND id = ?",
                LocalDateTime.class, TENANT_ID, associated.getId()));

        performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/leads/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadQueryRequest().setName("Associated lead"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(associated.getId()))
                .andExpect(jsonPath("$.objectList[0].nextContactTime")
                        .value("2026-08-01T09:30:00"));

        assertSavedViewMatches("Job title", new FilterConditionRequest()
                .setFieldCode("job_title").setOperator("EQ").setValues(List.of("Sales director")),
                associated.getId());
        assertSavedViewMatches("Next lead", new FilterConditionRequest()
                .setFieldCode("next_contact_time").setOperator("GTE")
                .setValues(List.of("2026-08-01T09:30:00")), associated.getId());
        assertSavedViewMatches("Customer equals", new FilterConditionRequest()
                .setFieldCode("customer").setOperator("EQ")
                .setValues(List.of(customer.getId().toString())), associated.getId());
        assertSavedViewMatches("Customer in", new FilterConditionRequest()
                .setFieldCode("customer").setOperator("IN")
                .setValues(List.of(customer.getId().toString())), associated.getId());

        LeadUpdateRequest rename = new LeadUpdateRequest().setVersion(associated.getVersion())
                .setName("Associated lead renamed");
        String renameJson = toJson(rename);
        assertFalse(renameJson.contains("\"customerId\""));
        assertFalse(renameJson.contains("\"nextContactTime\""));
        LeadResponse renamed = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/leads/{leadId}", associated.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(renameJson))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals(customer.getId(), renamed.getCustomerId());
        assertEquals(nextContactTime, renamed.getNextContactTime());

        LeadResponse markersIgnored = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/leads/{leadId}", associated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + renamed.getVersion()
                                + ",\"customerIdSpecified\":true"
                                + ",\"nextContactTimeSpecified\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals(customer.getId(), markersIgnored.getCustomerId());
        assertEquals(nextContactTime, markersIgnored.getNextContactTime());

        LeadUpdateRequest clearRootFields = new LeadUpdateRequest()
                .setVersion(markersIgnored.getVersion()).setPhoneNumber("").setTelephone("")
                .setJobTitle("").setEmail("").setWebsite("").setDescription("")
                .setNextContactTime(null).setCustomerId(null);
        String clearRootFieldsJson = toJson(clearRootFields);
        assertTrue(clearRootFieldsJson.contains("\"nextContactTime\":null"));
        assertTrue(clearRootFieldsJson.contains("\"customerId\":null"));
        LeadResponse rootFieldsCleared = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/leads/{leadId}", associated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearRootFieldsJson))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertNull(rootFieldsCleared.getPhoneNumber());
        assertNull(rootFieldsCleared.getTelephone());
        assertNull(rootFieldsCleared.getJobTitle());
        assertNull(rootFieldsCleared.getEmail());
        assertNull(rootFieldsCleared.getWebsite());
        assertNull(rootFieldsCleared.getDescription());
        assertNull(rootFieldsCleared.getNextContactTime());
        assertNull(rootFieldsCleared.getCustomerId());

        LeadResponse parentCleared = fromJson(performPut(
                "/api/v1/leads/" + associated.getId() + "/parent",
                new LeadParentUpdateRequest().setVersion(rootFieldsCleared.getVersion())
                        .setParentLeadId(null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertNull(parentCleared.getParentLeadId());

        LeadResponse reloaded = fromJson(performGet("/api/v1/leads/{leadId}",
                associated.getId()).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), LeadResponse.class);
        assertNull(reloaded.getPhoneNumber());
        assertNull(reloaded.getTelephone());
        assertNull(reloaded.getJobTitle());
        assertNull(reloaded.getEmail());
        assertNull(reloaded.getWebsite());
        assertNull(reloaded.getDescription());
        assertNull(reloaded.getNextContactTime());
        assertNull(reloaded.getCustomerId());
        assertNull(reloaded.getParentLeadId());
        assertNull(jdbc.queryForObject("SELECT phone_number FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT telephone FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT job_title FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT email FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT website FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT description FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT next_contact_time FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", LocalDateTime.class,
                TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT customer_id FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT parent_lead_id FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT_ID, associated.getId()));

        LeadResponse won = fromJson(performPut("/api/v1/leads/" + associated.getId()
                        + "/state", new LeadStateUpdateRequest().setVersion(reloaded.getVersion())
                        .setLocked(false).setDealStatus("WON"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertTrue(won.getDealTime() != null);
        LeadResponse reopened = fromJson(performPut("/api/v1/leads/" + associated.getId()
                        + "/state", new LeadStateUpdateRequest().setVersion(won.getVersion())
                        .setLocked(false).setDealStatus("OPEN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertNull(reopened.getDealTime());
        LeadResponse reloadedOpen = fromJson(performGet("/api/v1/leads/{leadId}",
                associated.getId()).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), LeadResponse.class);
        assertNull(reloadedOpen.getDealTime());
        assertNull(jdbc.queryForObject("SELECT deal_time FROM crm_lead "
                + "WHERE tenant_id = ? AND id = ?", LocalDateTime.class,
                TENANT_ID, associated.getId()));
    }

    @Test
    void savedViewRelationsRequireVisibleTargetsAndRejectRelationshipOracles() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead relation-view owner");
        long scopedActorId = 777779L;
        seedActiveMember(scopedActorId, "Relation-scoped reader");
        TenantRoleResponse reader = createRole("Relation-scoped lead reader",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("saved-views:write"));
        setFieldPolicy(reader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("customer")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("parent_lead")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(reader.getId(), scopedActorId);

        CustomerResponse visibleCustomer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Visible relation customer")
                        .setOwnerActorId(scopedActorId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        CustomerResponse hiddenCustomer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Hidden relation customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        LeadResponse visibleParent = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Visible relation parent")
                        .setOwnerActorId(scopedActorId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadResponse hiddenParent = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Hidden relation parent"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        LeadResponse visibleChild = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Visible relation child")
                        .setOwnerActorId(scopedActorId).setCustomerId(visibleCustomer.getId())
                        .setParentLeadId(visibleParent.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Hidden relation child")
                        .setOwnerActorId(scopedActorId).setCustomerId(hiddenCustomer.getId())
                        .setParentLeadId(hiddenParent.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        SavedViewResponse visibleCustomerView = createSavedViewAs(scopedActorId,
                "Visible customer relation", new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("EQ").setValues(List.of(visibleCustomer.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("savedViewId", visibleCustomerView.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(visibleChild.getId()));

        SavedViewResponse visibleParentView = createSavedViewAs(scopedActorId,
                "Visible parent relation", new FilterConditionRequest().setFieldCode("parent_lead")
                        .setOperator("EQ").setValues(List.of(visibleParent.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("savedViewId", visibleParentView.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(visibleChild.getId()));

        SavedViewResponse hiddenCustomerView = createSavedViewAs(scopedActorId,
                "Hidden customer relation", new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("EQ").setValues(List.of(hiddenCustomer.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("savedViewId", hiddenCustomerView.getId().toString()))
                .andExpect(status().isForbidden());

        SavedViewResponse hiddenParentView = createSavedViewAs(scopedActorId,
                "Hidden parent relation", new FilterConditionRequest().setFieldCode("parent_lead")
                        .setOperator("IN").setValues(List.of(hiddenParent.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("savedViewId", hiddenParentView.getId().toString()))
                .andExpect(status().isForbidden());

        SavedViewResponse nonEmptyCustomerView = createSavedViewAs(scopedActorId,
                "Customer relation oracle", new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("IS_NOT_EMPTY").setValues(List.of()));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("savedViewId", nonEmptyCustomerView.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalizedPersistenceRepresentationsRespectDatabaseContracts() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead Normalization Owner");
        FieldDefinitionResponse uniqueText = fromJson(performPost(
                "/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Normalized unique text")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        String compatibilityLigatures = "ﬃ".repeat(500);
        LeadResponse lead = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Normalization account")
                        .setTags(List.of("e", "é", "VIP", "Ａ", "ﬃ"))
                        .setCustomFields(Map.of(uniqueText.getCode(),
                                List.of(compatibilityLigatures))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(List.of("e", "é", "VIP", "Ａ", "ﬃ"), lead.getTags());
        assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead_tag "
                        + "WHERE tenant_id = ? AND lead_id = ?",
                Integer.class, TENANT_ID, lead.getId()));
        assertEquals(1_500, jdbc.queryForObject(
                "SELECT CHAR_LENGTH(normalized_value) "
                        + "FROM crm_lead_unique_field_value "
                        + "WHERE tenant_id = ? AND lead_id = ? AND field_id = ?",
                Integer.class, TENANT_ID, lead.getId(), uniqueText.getId()));
        LocalDateTime retainedTagCreatedTime = jdbc.queryForObject(
                "SELECT created_time FROM crm_lead_tag WHERE tenant_id = ? "
                        + "AND lead_id = ? AND tag_key = 'vip'",
                LocalDateTime.class, TENANT_ID, lead.getId());

        LeadResponse aliases = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Tag aliases")
                        .setTags(List.of("vip", "a", "ffi")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        String[] catalog = fromJson(performGet("/api/v1/leads/tags")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                String[].class);
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("VIP") || tag.equals("vip")).count());
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("Ａ") || tag.equals("a")).count());
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("ﬃ") || tag.equals("ffi")).count());

        LeadResponse batchChanged = fromJson(performPost("/api/v1/leads/batch/tags",
                new LeadBatchTagRequest()
                        .setLeads(List.of(new LeadVersionRequest()
                                .setLeadId(lead.getId()).setVersion(lead.getVersion())))
                        .setRemovedTags(List.of("ffi")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse[].class)[0];
        assertTrue(!batchChanged.getTags().contains("ﬃ"));
        // Re-read: normalized removal on one record must not strip the alias tag off another.
        assertTrue(fromJson(performGet("/api/v1/leads/" + aliases.getId())
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class).getTags().contains("ffi"));
        assertEquals(retainedTagCreatedTime, jdbc.queryForObject(
                "SELECT created_time FROM crm_lead_tag WHERE tenant_id = ? "
                        + "AND lead_id = ? AND tag_key = 'vip'",
                LocalDateTime.class, TENANT_ID, lead.getId()));

        String expandedWebsite = "https://example.invalid/" + "你".repeat(476);
        performPut("/api/v1/leads/" + lead.getId(),
                new LeadUpdateRequest().setVersion(batchChanged.getVersion())
                        .setWebsite(expandedWebsite))
                .andExpect(status().is(422));
    }

    @Test
    void selfScopeAndFieldPoliciesPreventRecordAndDuplicateOracles() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead Scope Owner");
        FieldDefinitionResponse segment = createTextField("Scope segment");
        long targetActorId = 777777L;
        seedActiveMember(targetActorId, "Scoped Member");
        performAs(targetActorId, TENANT_ID, get("/api/v1/leads"))
                .andExpect(status().isForbidden());

        TenantRoleResponse reader = createRole("Self Lead Readers",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("SELF"));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/LEAD/access", reader.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                                new RoleFieldAccessRequest().setFieldCode("lead_name")
                                        .setAccessLevel("READ").setMaskType("NONE"),
                                new RoleFieldAccessRequest().setFieldCode(segment.getCode())
                                        .setAccessLevel("READ").setMaskType("PARTIAL"))))))
                .andExpect(status().isOk());
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", reader.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(targetActorId)))))
                .andExpect(status().isOk());

        LeadResponse owned = createLead(
                OWNER_ACTOR_ID, TENANT_ID, "Scoped Account", targetActorId,
                segment.getCode(), "enterprise", false);
        LeadResponse privateLead = createLead(
                OWNER_ACTOR_ID, TENANT_ID, "Private Account", OWNER_ACTOR_ID,
                segment.getCode(), "private", false);

        performAs(targetActorId, TENANT_ID, get("/api/v1/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(owned.getId()))
                .andExpect(jsonPath("$.objectList[0].name").value("Scoped Account"))
                .andExpect(jsonPath("$.objectList[0].customFields."
                        + segment.getCode() + "[0]").value("e********e"));
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/leads/{leadId}", privateLead.getId()))
                .andExpect(status().isNotFound());
        performAs(targetActorId, TENANT_ID, post("/api/v1/leads/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadDuplicateCheckRequest()
                                .setName("Private Account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));
        performAs(targetActorId, TENANT_ID, post("/api/v1/leads/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadDuplicateCheckRequest()
                                .setName("Scoped Account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        long maskedActorId = 777778L;
        seedActiveMember(maskedActorId, "Masked Reader");
        TenantRoleResponse maskedReader = createRole("Masked Lead Readers",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("SELF"));
        setFieldPolicy(maskedReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("READ").setMaskType("PARTIAL"),
                new RoleFieldAccessRequest().setFieldCode("addresses")
                        .setAccessLevel("READ").setMaskType("PARTIAL"),
                new RoleFieldAccessRequest().setFieldCode("deal_status")
                        .setAccessLevel("READ").setMaskType("FULL")));
        assignRole(maskedReader.getId(), maskedActorId);
        LeadResponse maskedLead = createLead(
                OWNER_ACTOR_ID, TENANT_ID, "Masked Account", maskedActorId,
                segment.getCode(), "masked", true);
        LeadResponse wonLead = fromJson(performPut(
                "/api/v1/leads/" + maskedLead.getId() + "/state",
                new LeadStateUpdateRequest().setVersion(maskedLead.getVersion())
                        .setLocked(false).setDealStatus("WON"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);

        LeadResponse maskedResponse = fromJson(performAs(maskedActorId, TENANT_ID,
                get("/api/v1/leads/{leadId}", wonLead.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals("M********t", maskedResponse.getName());
        assertEquals("[REDACTED]", maskedResponse.getDealStatus());
        org.junit.jupiter.api.Assertions.assertNull(maskedResponse.getDealTime());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("name", "Masked Account"))
                .andExpect(status().isForbidden());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performAs(maskedActorId, TENANT_ID, get("/api/v1/leads")
                        .param("ownerActorId", Long.toString(maskedActorId)))
                .andExpect(status().isForbidden());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/leads/nearby")
                        .param("longitude", "121.4737").param("latitude", "31.2304")
                        .param("radiusMeters", "1000"))
                .andExpect(status().isForbidden());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> hiddenDescendants = new ArrayList<>();
        for (int index = 0; index < 1_998; index++) {
            hiddenDescendants.add(new Object[]{
                    TENANT_ID, OWNER_ACTOR_ID, owned.getId(), "Hidden descendant " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, parent_lead_id, name, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                hiddenDescendants);
        LeadResponse visibleAfterHidden = fromJson(performPost(
                "/api/v1/leads", new LeadCreateRequest()
                        .setName("Visible after hidden descendants")
                        .setOwnerActorId(targetActorId)
                        .setParentLeadId(owned.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/leads/{leadId}/hierarchy", owned.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.length()").value(2))
                .andExpect(jsonPath("$.leads[1].id").value(visibleAfterHidden.getId()));

        jdbc.batchUpdate("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, parent_lead_id, name, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                List.of(
                        new Object[]{TENANT_ID, OWNER_ACTOR_ID, owned.getId(),
                                "Hidden descendant 1998"},
                        new Object[]{TENANT_ID, OWNER_ACTOR_ID, owned.getId(),
                                "Hidden descendant 1999"}));
        performPost("/api/v1/leads", new LeadCreateRequest()
                .setName("Visible after hidden traversal budget")
                .setOwnerActorId(targetActorId)
                .setParentLeadId(owned.getId()))
                .andExpect(status().is(422));

        LeadResponse deepRoot = createLead(
                OWNER_ACTOR_ID, TENANT_ID, "Deep hierarchy root", targetActorId,
                segment.getCode(), "deep", false);
        long firstDeepId = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM crm_lead", Long.class);
        List<Object[]> deepDescendants = new ArrayList<>();
        for (int index = 0; index < 1_100; index++) {
            long id = firstDeepId + index;
            long parentId = index == 0 ? deepRoot.getId() : id - 1;
            deepDescendants.add(new Object[]{
                    id, TENANT_ID, OWNER_ACTOR_ID, parentId, "Deep descendant " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_lead "
                        + "(id, tenant_id, owner_actor_id, parent_lead_id, name, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                deepDescendants);
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/leads/{leadId}/hierarchy", deepRoot.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.length()").value(1))
                .andExpect(jsonPath("$.leads[0].id").value(deepRoot.getId()));
    }

    @Test
    void departmentScopesAndCollaboratorWriteAccessFailClosed() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead Department Owner");
        FieldDefinitionResponse segment = createTextField("Department segment");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = insertDepartment(jdbc, 0L, "Sales");
        long salesEast = insertDepartment(jdbc, sales, "Sales East");
        long service = insertDepartment(jdbc, 0L, "Service");
        long salesActor = 710001L;
        long subtreeActor = 710002L;
        long eastOwner = 710003L;
        long serviceOwner = 710004L;
        long collaboratorActor = 710005L;
        seedActiveMember(salesActor, "Sales Reader", sales);
        seedActiveMember(subtreeActor, "Sales Tree Reader", sales);
        seedActiveMember(eastOwner, "East Owner", salesEast);
        seedActiveMember(serviceOwner, "Service Owner", service);
        seedActiveMember(collaboratorActor, "Collaborator", service);

        TenantRoleResponse departmentReader = createRole("Department Lead Readers",
                new RoleGrantRequest().setAuthorityCode("leads:read")
                        .setDataScope("DEPARTMENT"));
        setFieldPolicy(departmentReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(departmentReader.getId(), salesActor);

        TenantRoleResponse subtreeReader = createRole("Subtree Lead Readers",
                new RoleGrantRequest().setAuthorityCode("leads:read")
                        .setDataScope("DEPARTMENT_SUBTREE"));
        setFieldPolicy(subtreeReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(subtreeReader.getId(), subtreeActor);

        createLead(OWNER_ACTOR_ID, TENANT_ID, "Sales Account", salesActor,
                segment.getCode(), "sales", false);
        LeadResponse eastLead = createLead(OWNER_ACTOR_ID, TENANT_ID,
                "East Account", eastOwner,
                segment.getCode(), "east", false);
        createLead(OWNER_ACTOR_ID, TENANT_ID, "Service Account", serviceOwner,
                segment.getCode(), "service", false);

        performAs(salesActor, TENANT_ID, get("/api/v1/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("Sales Account"));
        performAs(subtreeActor, TENANT_ID, get("/api/v1/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(2));
        performAs(subtreeActor, TENANT_ID,
                delete("/api/v1/leads/{leadId}/collaborators/me", eastLead.getId())
                        .param("version", eastLead.getVersion().toString()))
                .andExpect(status().isNotFound());
        assertEquals(eastLead.getVersion(), jdbc.queryForObject(
                "SELECT version FROM crm_lead WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, eastLead.getId()));

        TenantRoleResponse collaboratorRole = createRole("Lead Collaborators",
                new RoleGrantRequest().setAuthorityCode("leads:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("leads:write").setDataScope("SELF"));
        setFieldPolicy(collaboratorRole.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("lead_name")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("tags")
                        .setAccessLevel("WRITE").setMaskType("NONE")));
        assignRole(collaboratorRole.getId(), collaboratorActor);
        LeadResponse shared = createLead(OWNER_ACTOR_ID, TENANT_ID,
                "Shared Account", OWNER_ACTOR_ID, segment.getCode(), "shared", false);
        LeadResponse readOnly = replaceCollaborators(shared, collaboratorActor, "READ");
        assertEquals(collaboratorActor, readOnly.getCollaborators().getFirst().getActorId());
        assertEquals("READ", readOnly.getCollaborators().getFirst().getAccess());
        performAs(collaboratorActor, TENANT_ID,
                put("/api/v1/leads/{leadId}/tags", shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadTagsUpdateRequest()
                                .setVersion(readOnly.getVersion()).setTags(List.of("Blocked")))))
                .andExpect(status().isNotFound());
        LeadResponse writable = replaceCollaborators(readOnly, collaboratorActor, "WRITE");
        performAs(collaboratorActor, TENANT_ID,
                put("/api/v1/leads/{leadId}/tags", shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadTagsUpdateRequest()
                                .setVersion(writable.getVersion()).setTags(List.of("Shared")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("Shared"));
    }

    @Test
    void databaseArbitratesConcurrentUniqueValuesAndOpposingHierarchyMoves() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead Concurrency Owner");
        FieldDefinitionResponse registration = fromJson(performPost(
                "/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName("Registration")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);

        List<Integer> createStatuses = runConcurrently(
                () -> leadCreateStatus("Concurrent A", registration.getCode(), "same-value"),
                () -> leadCreateStatus("Concurrent B", registration.getCode(), "same-value"));
        assertEquals(List.of(200, 409), createStatuses.stream().sorted().toList());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND status = 'ACTIVE'",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead_unique_field_value WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/objects/LEAD/{fieldId}", registration.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FieldUpdateRequest()
                                .setVersion(registration.getVersion())
                                .setDisplayName(registration.getDisplayName())
                                .setUnique(false))))
                .andExpect(status().is(422));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_lead_unique_field_value WHERE tenant_id = ?",
                Integer.class, TENANT_ID));

        FieldDefinitionResponse segment = createTextField("Hierarchy segment");
        LeadResponse first = createLead(OWNER_ACTOR_ID, TENANT_ID, "Parent A",
                OWNER_ACTOR_ID, segment.getCode(), "a", false);
        LeadResponse second = createLead(OWNER_ACTOR_ID, TENANT_ID, "Parent B",
                OWNER_ACTOR_ID, segment.getCode(), "b", false);
        LeadResponse child = fromJson(performPost("/api/v1/leads",
                new LeadCreateRequest().setName("Hierarchy child")
                        .setParentLeadId(first.getId())
                        .setCustomFields(Map.of(segment.getCode(), List.of("child"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals(first.getId(), child.getParentLeadId());
        LeadResponse movedChild = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/leads/{leadId}/parent", child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadParentUpdateRequest()
                                .setVersion(child.getVersion())
                                .setParentLeadId(second.getId()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
        assertEquals(second.getId(), movedChild.getParentLeadId());
        List<Integer> hierarchyStatuses = runConcurrently(
                () -> parentUpdateStatus(first, second.getId()),
                () -> parentUpdateStatus(second, first.getId()));
        assertEquals(List.of(200, 422), hierarchyStatuses.stream().sorted().toList());
        int twoNodeCycles = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM crm_lead first_lead
                JOIN crm_lead second_lead
                  ON second_lead.tenant_id = first_lead.tenant_id
                 AND second_lead.id = first_lead.parent_lead_id
                WHERE first_lead.tenant_id = ?
                  AND second_lead.parent_lead_id = first_lead.id
                """, Integer.class, TENANT_ID);
        assertEquals(0, twoNodeCycles);
    }

    @Test
    void tagCatalogIncludesOlderVisibleLeadsBeyondTheFirstThousand() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead Tag Owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, name, status, updated_time) "
                        + "VALUES (?, ?, 'Legacy tagged account', 'ACTIVE', '2020-01-01 00:00:00')",
                TENANT_ID, OWNER_ACTOR_ID);
        Long legacyId = jdbc.queryForObject(
                "SELECT id FROM crm_lead WHERE tenant_id = ? AND name = 'Legacy tagged account'",
                Long.class, TENANT_ID);
        jdbc.update("INSERT INTO crm_lead_tag "
                        + "(tenant_id, lead_id, tag_name, tag_key) "
                        + "VALUES (?, ?, 'Legacy-only', 'legacy-only')",
                TENANT_ID, legacyId);
        List<Object[]> newerLeads = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            newerLeads.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, "Recent account " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, name, status, updated_time) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6))",
                newerLeads);

        String[] tags = fromJson(performGet("/api/v1/leads/tags")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                String[].class);
        assertTrue(List.of(tags).contains("Legacy-only"));
    }

    private FieldDefinitionResponse createTextField(String name) throws Exception {
        return fromJson(performPost("/api/v1/fields/objects/LEAD",
                new FieldCreateRequest().setDisplayName(name).setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
    }

    private void assertSavedViewMatches(
            String name, FilterConditionRequest condition, Long expectedLeadId) throws Exception {
        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("LEAD").setName(name)
                        .setConditions(List.of(condition)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        mockMvc.perform(withTestActor(get("/api/v1/leads")
                        .param("savedViewId", view.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(expectedLeadId));
    }

    private SavedViewResponse createSavedViewAs(
            long actorId, String name, FilterConditionRequest condition) throws Exception {
        return fromJson(performAs(actorId, TENANT_ID, post("/api/v1/saved-views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SavedViewCreateRequest().setObjectType("LEAD").setName(name)
                                .setConditions(List.of(condition)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
    }

    private LeadResponse createLead(
            long actorId, long tenantId, String name, long ownerActorId,
            String customFieldCode, String customFieldValue, boolean withAddress) throws Exception {
        LeadCreateRequest request = new LeadCreateRequest()
                .setName(name)
                .setPhoneNumber("+8613800138000")
                .setOwnerActorId(ownerActorId)
                .setTags(List.of("Enterprise"))
                .setCustomFields(Map.of(customFieldCode, List.of(customFieldValue)));
        if (withAddress) {
            request.setAddresses(List.of(new LeadAddressRequest()
                    .setKey("hq").setLabel("Headquarters")
                    .setAddressLine("Example road 1")
                    .setLongitude(new BigDecimal("121.4737"))
                    .setLatitude(new BigDecimal("31.2304"))
                    .setPrimary(true)));
        }
        return fromJson(performAs(actorId, tenantId, post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
    }

    private LeadResponse createGeocodedLead(String name, String longitude) throws Exception {
        return fromJson(performPost("/api/v1/leads", new LeadCreateRequest().setName(name)
                        .setAddresses(List.of(new LeadAddressRequest()
                                .setKey("primary").setAddressLine("Dateline")
                                .setLongitude(new BigDecimal(longitude))
                                .setLatitude(BigDecimal.ZERO).setPrimary(true))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
    }

    private TenantRoleResponse createRole(String name, RoleGrantRequest... grants) throws Exception {
        return fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName(name).setGrants(List.of(grants)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    /**
     * Exporting a custom field is the only path that reads the custom-value projection; every other
     * export test asks for built-in columns only, leaving that query parsed but never executed.
     */
    @Test
    void leadCsvExportProjectsCustomFieldValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Lead CSV custom projection owner");
        FieldDefinitionResponse segment = createTextField("Lead CSV custom projection segment");
        LeadResponse lead = createLead(OWNER_ACTOR_ID, TENANT_ID,
                "Lead CSV custom projection", OWNER_ACTOR_ID,
                segment.getCode(), "inbound-tier", false);

        String content = performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/leads/export/csv").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadCsvExportRequest().setFieldCodes(
                                List.of(LeadFieldCatalog.NAME, segment.getCode())))))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(content.contains("inbound-tier"),
                () -> "the custom value must reach the export, was: " + content);
        assertTrue(content.contains(lead.getId().toString()));
    }

    private MockHttpServletRequestBuilder leadCsvExportRequest() {
        return post("/api/v1/leads/export/csv")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LeadCsvExportRequest()
                        .setFieldCodes(List.of(LeadFieldCatalog.NAME))));
    }

    private String csvLeadContent(long actorId) throws Exception {
        return performAs(actorId, TENANT_ID, leadCsvExportRequest())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"leads.csv\""))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void setFieldPolicy(Long roleId, List<RoleFieldAccessRequest> accesses) throws Exception {
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/LEAD/access", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(accesses))))
                .andExpect(status().isOk());
    }

    private void assignRole(Long roleId, long actorId) throws Exception {
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(actorId)))))
                .andExpect(status().isOk());
    }

    private LeadResponse replaceCollaborators(
            LeadResponse lead, long actorId, String access) throws Exception {
        return fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/leads/{leadId}/collaborators", lead.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadCollaboratorsUpdateRequest()
                                .setVersion(lead.getVersion())
                                .setCollaborators(List.of(new LeadCollaboratorRequest()
                                        .setActorId(actorId).setAccess(access))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                LeadResponse.class);
    }

    private int leadCreateStatus(String name, String fieldCode, String value) throws Exception {
        LeadCreateRequest request = new LeadCreateRequest().setName(name)
                .setCustomFields(Map.of(fieldCode, List.of(value)));
        return performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/leads")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andReturn().getResponse().getStatus();
    }

    private int parentUpdateStatus(LeadResponse lead, Long parentId) throws Exception {
        return performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/leads/{leadId}/parent", lead.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LeadParentUpdateRequest()
                                .setVersion(lead.getVersion())
                                .setParentLeadId(parentId))))
                .andReturn().getResponse().getStatus();
    }

    @SafeVarargs
    private final List<Integer> runConcurrently(java.util.concurrent.Callable<Integer>... tasks)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<Integer>> futures = java.util.Arrays.stream(tasks).map(task ->
                    executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to start lead writes");
                        }
                        return task.call();
                    })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(10, TimeUnit.SECONDS));
            }
            return List.copyOf(statuses);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private void seedActiveMember(long actorId, String displayName) {
        seedActiveMember(actorId, displayName, null);
    }

    private void seedActiveMember(long actorId, String displayName, Long departmentId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, actorId, departmentId);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT_ID);
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

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearLeadData();
    }

    @Override
    protected void clearTestData() {
        clearLeadData();
    }

    private void clearLeadData() {
        executeSql("DELETE FROM crm_lead_intake_client");
        executeSql("DELETE FROM crm_lead_star");
        executeSql("DELETE FROM crm_lead_unique_field_value");
        executeSql("DELETE FROM crm_lead_field_value");
        executeSql("DELETE FROM crm_lead_collaborator");
        executeSql("DELETE FROM crm_lead_tag");
        executeSql("DELETE FROM crm_lead_address");
        executeSql("UPDATE crm_lead SET parent_lead_id = NULL");
        executeSql("DELETE FROM crm_lead");
        executeSql("DELETE FROM crm_lead_guard");
        clearContacts();
        clearCustomers();
        executeSql("DELETE FROM embedded_page");
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
