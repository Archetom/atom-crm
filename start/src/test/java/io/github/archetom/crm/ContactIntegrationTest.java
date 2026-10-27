package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ContactAddressRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchTagRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.ContactCollaboratorRequest;
import io.github.archetom.crm.api.dto.request.ContactCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ContactDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.ContactParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactVersionRequest;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.application.service.ContactCsvExportService;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end contact boundary coverage against the production MySQL mappings. */
@RequiresDatabase
class ContactIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Test
    void contactListReturnsLatestDirectActivitySummary() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact activity summary owner");
        ContactResponse contact = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Activity summary contact"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        LocalDateTime latestTime = LocalDateTime.of(2026, 8, 11, 10, 30);
        performPost("/api/v1/activities", new ActivityCreateRequest().setSubjectType("CONTACT")
                        .setSubjectId(contact.getId()).setCategory("CALL")
                        .setContent("Earlier contact follow-up")
                        .setOccurredTime(latestTime.minusHours(1)))
                .andExpect(status().isOk());
        performPost("/api/v1/activities", new ActivityCreateRequest().setSubjectType("CONTACT")
                        .setSubjectId(contact.getId()).setCategory("CALL")
                        .setContent("Latest contact follow-up")
                        .setOccurredTime(latestTime))
                .andExpect(status().isOk());

        performGet("/api/v1/contacts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].lastActivityTime")
                        .value("2026-08-11T10:30:00"))
                .andExpect(jsonPath("$.objectList[0].lastActivityContent")
                        .value("Latest contact follow-up"));
    }

    @Test
    void contactCollaboratorReadReturnsAnAuthoritativeVersionedBaseline() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact collaborator baseline owner");
        seedActiveMember(777701L, "Contact collaborator baseline member");
        ContactResponse created = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contact collaborator baseline"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactResponse updated = replaceCollaborators(created, 777701L, "READ");

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                get("/api/v1/contacts/{contactId}/collaborators", updated.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(updated.getVersion()))
                .andExpect(jsonPath("$.callerState").value("OWNER"))
                .andExpect(jsonPath("$.collaborators.length()").value(1))
                .andExpect(jsonPath("$.collaborators[0].actorId").value(777701L))
                .andExpect(jsonPath("$.collaborators[0].access").value("READ"));
    }

    @Test
    void contactListSupportsSafeServerSideSorting() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact sort owner");
        ContactResponse zebra = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Zebra contact"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactResponse alpha = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Aardvark contact"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        performGetWithParams("/api/v1/contacts", Map.of(
                        "sortField", "NAME", "sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(zebra.getId()));
        performGetWithParams("/api/v1/contacts", Map.of(
                        "sortField", "DEAL_TIME", "sortDirection", "ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contactCustomNumericFieldSortUsesCanonicalNumericValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact numeric sort owner");
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/CONTACT",
                new FieldCreateRequest().setDisplayName("Contact score").setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        ContactResponse two = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Two score")
                        .setCustomFields(Map.of(score.getCode(), List.of("2"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactResponse ten = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Ten score")
                        .setCustomFields(Map.of(score.getCode(), List.of("10"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        mockMvc.perform(withTestActor(get("/api/v1/contacts")
                        .param("customSortFieldCode", score.getCode())
                        .param("sortDirection", "ASC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(two.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(ten.getId()));
        mockMvc.perform(withTestActor(get("/api/v1/contacts")
                        .param("sortField", "NAME")
                        .param("customSortFieldCode", score.getCode())
                        .param("sortDirection", "ASC")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withTestActor(get("/api/v1/contacts")
                        .param("customSortFieldCode", ContactFieldCatalog.NAME)
                        .param("sortDirection", "ASC")))
                .andExpect(status().isBadRequest());
    }

    @MockitoSpyBean
    private ContactRepository contactRepository;

    @Autowired
    private ContactCsvExportService contactCsvExportService;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void contactCsvExportIntersectsIndependentReadAndOwnerExportScopesInMySql() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV owner");
        long tenantReadSelfExportActor = 777701L;
        long selfReadTenantExportActor = 777702L;
        long readOnlyActor = 777703L;
        long exportOnlyActor = 777704L;
        seedActiveMember(tenantReadSelfExportActor, "Contact CSV tenant read self export");
        seedActiveMember(selfReadTenantExportActor, "Contact CSV self read tenant export");
        seedActiveMember(readOnlyActor, "Contact CSV read only");
        seedActiveMember(exportOnlyActor, "Contact CSV export only");

        TenantRoleResponse tenantReadSelfExport = createRole("Contact CSV tenant read self export",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("TENANT"),
                new RoleGrantRequest().setAuthorityCode("contacts:export").setDataScope("SELF"));
        TenantRoleResponse selfReadTenantExport = createRole("Contact CSV self read tenant export",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("contacts:export").setDataScope("TENANT"));
        TenantRoleResponse readOnly = createRole("Contact CSV read only",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("TENANT"));
        TenantRoleResponse exportOnly = createRole("Contact CSV export only",
                new RoleGrantRequest().setAuthorityCode("contacts:export").setDataScope("TENANT"));
        setFieldPolicy(tenantReadSelfExport.getId(), csvContactNamePolicy());
        setFieldPolicy(selfReadTenantExport.getId(), csvContactNamePolicy());
        assignRole(tenantReadSelfExport.getId(), tenantReadSelfExportActor);
        assignRole(selfReadTenantExport.getId(), selfReadTenantExportActor);
        assignRole(readOnly.getId(), readOnlyActor);
        assignRole(exportOnly.getId(), exportOnlyActor);

        String code = createTextField("Contact CSV scope marker").getCode();
        ContactResponse selfExported = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Self export included", tenantReadSelfExportActor, code, "a", false);
        ContactResponse selfCollaboratorOnly = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Self export collaborator only", OWNER_ACTOR_ID, code, "b", false);
        ContactResponse pooled = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Self export pool", tenantReadSelfExportActor, code, "c", false);
        ContactResponse deleted = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Self export deleted", tenantReadSelfExportActor, code, "d", false);
        ContactResponse tenantExportOwned = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Tenant export owned", selfReadTenantExportActor, code, "e", false);
        ContactResponse tenantExportCollaborator = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Tenant export collaborator", OWNER_ACTOR_ID, code, "f", false);
        ContactResponse tenantExportHidden = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Tenant export hidden", OWNER_ACTOR_ID, code, "g", false);
        replaceCollaborators(selfCollaboratorOnly, tenantReadSelfExportActor, "READ");
        replaceCollaborators(tenantExportCollaborator, selfReadTenantExportActor, "READ");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE crm_contact SET status = 'IN_POOL' WHERE tenant_id = ? AND id = ?",
                TENANT_ID, pooled.getId());
        jdbc.update("UPDATE crm_contact SET status = 'DELETED' WHERE tenant_id = ? AND id = ?",
                TENANT_ID, deleted.getId());

        assertCsvContactIds(tenantReadSelfExportActor, TENANT_ID, List.of(selfExported.getId()));
        assertCsvContactIds(selfReadTenantExportActor, TENANT_ID,
                List.of(tenantExportOwned.getId(), tenantExportCollaborator.getId()));
        performAs(readOnlyActor, TENANT_ID, contactCsvExportRequest()).andExpect(status().isForbidden());
        performAs(exportOnlyActor, TENANT_ID, contactCsvExportRequest()).andExpect(status().isForbidden());

        long otherActor = 777705L;
        long otherTenant = 777799L;
        bootstrap(otherActor, otherTenant, "Other tenant contact CSV owner");
        String crossTenant = performAs(otherActor, otherTenant, contactCsvExportRequest())
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("\uFEFFcontact_id,contact_name\r\n", crossTenant);
    }

    @Test
    void contactCsvExportIntersectsDepartmentAndSubtreeScopesAndRejectsUnavailableScopes()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV department scope owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = insertDepartment(jdbc, 0L, "Contact CSV Sales");
        long salesEast = insertDepartment(jdbc, sales, "Contact CSV Sales East");
        long service = insertDepartment(jdbc, 0L, "Contact CSV Service");
        long subtreeReadDepartmentExportActor = 777711L;
        long departmentReadSubtreeExportActor = 777712L;
        long salesOwner = 777713L;
        long eastOwner = 777714L;
        long serviceOwner = 777715L;
        long inactiveSalesOwner = 777716L;
        long noDepartmentActor = 777717L;
        seedActiveMember(subtreeReadDepartmentExportActor, "Contact CSV subtree read department export", sales);
        seedActiveMember(departmentReadSubtreeExportActor, "Contact CSV department read subtree export", sales);
        seedActiveMember(salesOwner, "Contact CSV sales owner", sales);
        seedActiveMember(eastOwner, "Contact CSV sales east owner", salesEast);
        seedActiveMember(serviceOwner, "Contact CSV service owner", service);
        seedActiveMember(inactiveSalesOwner, "Contact CSV inactive sales owner", sales);
        seedActiveMember(noDepartmentActor, "Contact CSV no department");

        TenantRoleResponse subtreeReadDepartmentExport = createRole(
                "Contact CSV subtree read department export",
                new RoleGrantRequest().setAuthorityCode("contacts:read")
                        .setDataScope("DEPARTMENT_SUBTREE"),
                new RoleGrantRequest().setAuthorityCode("contacts:export")
                        .setDataScope("DEPARTMENT"));
        TenantRoleResponse departmentReadSubtreeExport = createRole(
                "Contact CSV department read subtree export",
                new RoleGrantRequest().setAuthorityCode("contacts:read")
                        .setDataScope("DEPARTMENT"),
                new RoleGrantRequest().setAuthorityCode("contacts:export")
                        .setDataScope("DEPARTMENT_SUBTREE"));
        TenantRoleResponse unavailableDepartmentScope = createRole("Contact CSV unavailable department",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("DEPARTMENT"),
                new RoleGrantRequest().setAuthorityCode("contacts:export").setDataScope("DEPARTMENT"));
        setFieldPolicy(subtreeReadDepartmentExport.getId(), csvContactNamePolicy());
        setFieldPolicy(departmentReadSubtreeExport.getId(), csvContactNamePolicy());
        setFieldPolicy(unavailableDepartmentScope.getId(), csvContactNamePolicy());
        assignRole(subtreeReadDepartmentExport.getId(), subtreeReadDepartmentExportActor);
        assignRole(departmentReadSubtreeExport.getId(), departmentReadSubtreeExportActor);
        assignRole(unavailableDepartmentScope.getId(), noDepartmentActor);

        String code = createTextField("Contact CSV department marker").getCode();
        ContactResponse salesContact = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Contact CSV sales included", salesOwner, code, "sales", false);
        createContact(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV east excluded", eastOwner,
                code, "east", false);
        createContact(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV service excluded", serviceOwner,
                code, "service", false);
        ContactResponse inactiveOwnerContact = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Contact CSV inactive owner excluded", inactiveSalesOwner, code, "inactive", false);
        jdbc.update("UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                TENANT_ID, inactiveSalesOwner);

        assertCsvContactIds(subtreeReadDepartmentExportActor, TENANT_ID,
                List.of(salesContact.getId()));
        assertCsvContactIds(departmentReadSubtreeExportActor, TENANT_ID,
                List.of(salesContact.getId()));
        assertEquals("\uFEFFcontact_id,contact_name\r\n", contactCsvContent(noDepartmentActor, TENANT_ID));
        assertFalse(contactCsvContent(subtreeReadDepartmentExportActor, TENANT_ID)
                .contains(inactiveOwnerContact.getId().toString()));
    }

    @Test
    void contactCsvExportRevalidatesCustomerAndParentTargetsInMySql() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV relation owner");
        long exporterActor = 777721L;
        long noCustomerReadActor = 777722L;
        seedActiveMember(exporterActor, "Contact CSV relation exporter");
        seedActiveMember(noCustomerReadActor, "Contact CSV no customer read");
        TenantRoleResponse exporterRole = createRole("Contact CSV relation exporter",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("contacts:export").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("saved-views:write"));
        TenantRoleResponse noCustomerReadRole = createRole("Contact CSV no customer read",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("contacts:export").setDataScope("SELF"));
        List<RoleFieldAccessRequest> relationPolicy = List.of(
                new RoleFieldAccessRequest().setFieldCode(ContactFieldCatalog.NAME)
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode(ContactFieldCatalog.CUSTOMER)
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode(ContactFieldCatalog.PARENT_CONTACT)
                        .setAccessLevel("READ").setMaskType("NONE"));
        setFieldPolicy(exporterRole.getId(), relationPolicy);
        setFieldPolicy(noCustomerReadRole.getId(), relationPolicy);
        assignRole(exporterRole.getId(), exporterActor);
        assignRole(noCustomerReadRole.getId(), noCustomerReadActor);

        CustomerResponse visibleCustomer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Contact CSV visible customer")
                        .setOwnerActorId(exporterActor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        CustomerResponse hiddenCustomer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Contact CSV hidden customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ContactResponse sharedParent = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contact CSV shared parent"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        sharedParent = replaceCollaborators(sharedParent, exporterActor, "READ");
        ContactResponse child = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contact CSV relation child")
                        .setOwnerActorId(exporterActor).setCustomerId(visibleCustomer.getId())
                        .setParentContactId(sharedParent.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contact CSV no customer child")
                        .setOwnerActorId(noCustomerReadActor).setCustomerId(visibleCustomer.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        String directCustomerCsv = performAs(exporterActor, TENANT_ID,
                contactCsvExportRequest().content(toJson(new ContactCsvExportRequest()
                        .setFieldCodes(List.of(ContactFieldCatalog.NAME))
                        .setCustomerId(visibleCustomer.getId()))))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(directCustomerCsv.contains(child.getId() + ",Contact CSV relation child"));
        SavedViewResponse customerView = createSavedViewAs(exporterActor,
                "Contact CSV visible customer view", new FilterConditionRequest()
                        .setFieldCode(ContactFieldCatalog.CUSTOMER).setOperator("EQ")
                        .setValues(List.of(visibleCustomer.getId().toString())));
        assertCsvContactIds(exporterActor, TENANT_ID, List.of(child.getId()), customerView.getId());
        performAs(exporterActor, TENANT_ID,
                contactCsvExportRequest().content(toJson(new ContactCsvExportRequest()
                        .setFieldCodes(List.of(ContactFieldCatalog.NAME))
                        .setCustomerId(hiddenCustomer.getId())))).andExpect(status().isNotFound());
        performAs(noCustomerReadActor, TENANT_ID,
                contactCsvExportRequest().content(toJson(new ContactCsvExportRequest()
                        .setFieldCodes(List.of(ContactFieldCatalog.NAME))
                        .setCustomerId(visibleCustomer.getId())))).andExpect(status().isForbidden());
        new JdbcTemplate(dataSource).update("UPDATE crm_customer SET owner_actor_id = ? "
                        + "WHERE tenant_id = ? AND id = ?",
                OWNER_ACTOR_ID, TENANT_ID, visibleCustomer.getId());
        performAs(exporterActor, TENANT_ID,
                contactCsvExportRequest().content(toJson(new ContactCsvExportRequest()
                        .setFieldCodes(List.of(ContactFieldCatalog.NAME))
                        .setSavedViewId(customerView.getId())))).andExpect(status().isForbidden());

        SavedViewResponse parentView = createSavedViewAs(exporterActor,
                "Contact CSV shared parent view", new FilterConditionRequest()
                        .setFieldCode(ContactFieldCatalog.PARENT_CONTACT).setOperator("EQ")
                        .setValues(List.of(sharedParent.getId().toString())));
        assertCsvContactIds(exporterActor, TENANT_ID, List.of(child.getId()), parentView.getId());
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/contacts/{contactId}/collaborators", sharedParent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactCollaboratorsUpdateRequest()
                                .setVersion(sharedParent.getVersion()).setCollaborators(List.of()))))
                .andExpect(status().isOk());
        performAs(exporterActor, TENANT_ID,
                contactCsvExportRequest().content(toJson(new ContactCsvExportRequest()
                        .setFieldCodes(List.of(ContactFieldCatalog.NAME))
                        .setSavedViewId(parentView.getId())))).andExpect(status().isForbidden());
    }

    @Test
    void contactCsvExportAppliesAllFiltersBeforeTheFiveThousandAndOneRowProbe() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV filter owner");
        FieldDefinitionResponse segment = createTextField("Contact CSV filter segment");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> nonMatches = new ArrayList<>();
        for (int index = 0; index < 5_001; index++) {
            nonMatches.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, "Contact CSV noise " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_contact (tenant_id, owner_actor_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')", nonMatches);
        ContactResponse matching = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Contact CSV filter match", OWNER_ACTOR_ID, segment.getCode(), "export-match", false);
        performPut("/api/v1/contacts/" + matching.getId() + "/star",
                new ContactStarUpdateRequest().setStarred(true)).andExpect(status().isOk());
        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CONTACT").setName("Contact CSV filtered")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(segment.getCode()).setOperator("EQ")
                                .setValues(List.of("export-match")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);

        ContactCsvExportRequest request = new ContactCsvExportRequest()
                .setFieldCodes(List.of(ContactFieldCatalog.NAME))
                .setKeyword("Contact CSV filter")
                .setName("Contact CSV filter match")
                .setOwnerActorId(OWNER_ACTOR_ID)
                .setStarred(true)
                .setSavedViewId(view.getId());
        CapturedSql capture = null;
        startContactExportQueryCapture();
        String content;
        try {
            content = performAs(OWNER_ACTOR_ID, TENANT_ID,
                    contactCsvExportRequest().content(toJson(request)))
                    .andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
        } finally {
            capture = CONTACT_EXPORT_QUERY_CAPTURE.stop();
        }
        assertEquals("\uFEFFcontact_id,contact_name\r\n" + matching.getId()
                + ",Contact CSV filter match\r\n", content);

        assertNotNull(capture);
        assertTrue(capture.sql().contains("crm_contact_star"), capture::sql);
        assertTrue(capture.sql().contains("crm_contact_field_value"), capture::sql);
        assertTrue(capture.sql().contains("owner_actor_id"), capture::sql);
        assertTrue(capture.sql().contains("ORDER BY id ASC"), capture::sql);
        assertTrue(capture.sql().contains("LIMIT 5001"), capture::sql);
        List<Map<String, Object>> plan = jdbc.queryForList("EXPLAIN " + capture.sql(),
                capture.parameters().toArray());
        String explain = plan.stream().flatMap(row -> row.values().stream()).map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(explain.contains("index lookup on crm_contact using PRIMARY"), () -> explain);
        assertFalse(explain.contains("Table scan on crm_contact"), () -> explain);
    }

    @Test
    void contactCsvExportCapturesProductionScopeAndRelationshipSqlBranches() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV query capture owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = insertDepartment(jdbc, 0L, "Contact CSV query capture sales");
        long exporterActor = 777731L;
        seedActiveMember(exporterActor, "Contact CSV query capture exporter", sales);
        FieldDefinitionResponse segment = createTextField("Contact CSV query capture segment");
        TenantRoleResponse exporterRole = createRole("Contact CSV query capture exporter",
                new RoleGrantRequest().setAuthorityCode("contacts:read")
                        .setDataScope("DEPARTMENT_SUBTREE"),
                new RoleGrantRequest().setAuthorityCode("contacts:export")
                        .setDataScope("DEPARTMENT"),
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("TENANT"),
                new RoleGrantRequest().setAuthorityCode("saved-views:write"));
        setFieldPolicy(exporterRole.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode(ContactFieldCatalog.NAME)
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode(ContactFieldCatalog.OWNER_ACTOR)
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode(ContactFieldCatalog.CUSTOMER)
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode(segment.getCode())
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(exporterRole.getId(), exporterActor);
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Contact CSV query capture customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ContactResponse contact = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contact CSV query capture match")
                        .setOwnerActorId(exporterActor).setCustomerId(customer.getId())
                        .setCustomFields(Map.of(segment.getCode(), List.of("capture-match"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        jdbc.update("INSERT INTO crm_contact_star (tenant_id, actor_id, contact_id) VALUES (?, ?, ?)",
                TENANT_ID, exporterActor, contact.getId());
        SavedViewResponse view = createSavedViewAs(exporterActor, "Contact CSV query capture view",
                new FilterConditionRequest().setFieldCode(segment.getCode()).setOperator("EQ")
                        .setValues(List.of("capture-match")));

        CapturedSql capture = null;
        startContactExportQueryCapture();
        try {
            performAs(exporterActor, TENANT_ID, contactCsvExportRequest().content(toJson(
                    new ContactCsvExportRequest().setFieldCodes(List.of(ContactFieldCatalog.NAME))
                            .setOwnerActorId(exporterActor).setCustomerId(customer.getId())
                            .setStarred(true).setSavedViewId(view.getId()))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"contacts.csv\""));
        } finally {
            capture = CONTACT_EXPORT_QUERY_CAPTURE.stop();
        }

        assertNotNull(capture);
        assertTrue(capture.sql().contains("organization_membership"), capture::sql);
        assertTrue(capture.sql().contains("crm_contact_collaborator"), capture::sql);
        assertTrue(capture.sql().contains("crm_contact_star"), capture::sql);
        assertTrue(capture.sql().contains("crm_contact_field_value"), capture::sql);
        assertTrue(capture.sql().contains("customer_id"), capture::sql);
    }

    @Test
    void contactCsvExportRejectsFiveThousandAndOneMatchingRowsBeforeStartingTheBody()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV row limit owner");
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 5_001; index++) {
            rows.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, "Contact CSV limit " + index});
        }
        new JdbcTemplate(dataSource).batchUpdate(
                "INSERT INTO crm_contact (tenant_id, owner_actor_id, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                rows);

        performAs(OWNER_ACTOR_ID, TENANT_ID, contactCsvExportRequest())
                .andExpect(status().is(422)).andExpect(jsonPath("$.errCode").value(endsWith("002")))
                .andExpect(jsonPath("$.errMsg").value("Contact export exceeds the row limit"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void contactCsvExportKeepsOneMySqlRepeatableReadSnapshotAcrossProjectionBatches() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV snapshot owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> contacts = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            contacts.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, "Contact snapshot before " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_contact (tenant_id, owner_actor_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')", contacts);
        long secondBatchId = jdbc.queryForObject("SELECT id FROM crm_contact "
                        + "WHERE tenant_id = ? ORDER BY id DESC LIMIT 1", Long.class, TENANT_ID);
        CountDownLatch firstProjectionCompleted = new CountDownLatch(1);
        CountDownLatch mutationCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<io.github.archetom.crm.domain.valueobject.ContactId> batch = invocation.getArgument(1);
            Object rows = invocation.callRealMethod();
            if (batch.size() == 200 && firstProjectionCompleted.getCount() > 0) {
                firstProjectionCompleted.countDown();
                await(mutationCommitted, "Contact CSV mutation was not committed");
            }
            return rows;
        }).when(contactRepository).findExportRows(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> export = executor.submit(() -> {
                var result = contactCsvExportService.export(new AuthenticatedCaller(
                                OWNER_ACTOR_ID, TENANT_ID, Set.of(AuthorityCatalog.CONTACTS_READ,
                                AuthorityCatalog.CONTACTS_EXPORT)), new ContactCsvExportRequest()
                                .setFieldCodes(List.of(ContactFieldCatalog.NAME)));
                assertTrue(result.isSuccess());
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    result.getData().writeTo(output);
                    return output.toString(StandardCharsets.UTF_8);
                } finally {
                    result.getData().close();
                }
            });
            await(firstProjectionCompleted, "Contact CSV export did not finish its first projection batch");
            jdbc.update("UPDATE crm_contact SET name = ? WHERE tenant_id = ? AND id = ?",
                    "Contact snapshot after", TENANT_ID, secondBatchId);
            mutationCommitted.countDown();
            String output = export.get(10, TimeUnit.SECONDS);
            assertTrue(output.contains(secondBatchId + ",Contact snapshot before 200\r\n"), output);
            assertFalse(output.contains(secondBatchId + ",Contact snapshot after\r\n"), output);
        } finally {
            mutationCommitted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void contactCsvExportRejectsUnknownRequestPropertiesWithASafeJsonError() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV strict request owner");
        String body = performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/contacts/export/csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldCodes\":[\"contact_name\"],\"tenant\":999999,"
                                + "\"page\":1,\"filename\":\"contacts.csv\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errCode").value(endsWith("101")))
                .andExpect(jsonPath("$.errMsg").value("Request body is malformed"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("tenant"));
        assertFalse(body.contains("filename"));
    }

    @Test
    void contactLifecycleUsesCustomFieldsSavedViewsNearbyAndOptimisticLocking() throws Exception {
        mockMvc.perform(get("/api/v1/contacts")).andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact Owner");
        performGetWithParams("/api/v1/contacts/nearby", Map.of(
                        "longitude", "121.4737", "latitude", "31.2304", "radiusMeters", "0"))
                .andExpect(status().isBadRequest());
        assertEquals(18, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'CONTACT' AND is_builtin = 1",
                Integer.class, TENANT_ID));

        FieldDefinitionResponse segment = createTextField("Segment");
        ContactResponse contact = createContact(
                OWNER_ACTOR_ID, TENANT_ID, "Acme Shanghai", OWNER_ACTOR_ID,
                segment.getCode(), "enterprise", true);
        assertEquals(List.of("Enterprise"), contact.getTags());
        assertEquals(List.of("enterprise"), contact.getCustomFields().get(segment.getCode()));
        assertEquals("hq", contact.getAddresses().getFirst().getKey());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO crm_contact_address "
                        + "(tenant_id, contact_id, address_key, address_line, longitude, latitude, is_primary) "
                        + "VALUES (?, ?, 'longitude-only', 'Invalid', 121.0000000, NULL, 0)",
                TENANT_ID, contact.getId()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO crm_contact_address "
                        + "(tenant_id, contact_id, address_key, address_line, longitude, latitude, is_primary) "
                        + "VALUES (?, ?, 'latitude-only', 'Invalid', NULL, 31.0000000, 0)",
                TENANT_ID, contact.getId()));

        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CONTACT").setName("Enterprise")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(segment.getCode()).setOperator("EQ")
                                .setValues(List.of("enterprise")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        mockMvc.perform(withTestActor(get("/api/v1/contacts")
                        .param("savedViewId", view.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(contact.getId()));
        performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/contacts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactQueryRequest().setConditions(List.of(
                                new FilterConditionRequest().setFieldCode(segment.getCode())
                                        .setOperator("EQ").setValues(List.of("enterprise")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(contact.getId()));
        performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/contacts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactQueryRequest().setSavedViewId(view.getId())
                                .setConditions(List.of(new FilterConditionRequest()
                                        .setFieldCode(segment.getCode()).setOperator("EQ")
                                        .setValues(List.of("enterprise")))))))
                .andExpect(status().isBadRequest());

        performPut("/api/v1/contacts/" + contact.getId() + "/star",
                new ContactStarUpdateRequest().setStarred(true))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(get("/api/v1/contacts").param("starred", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].starred").value(true));
        performPost("/api/v1/contacts/duplicate-check",
                new ContactDuplicateCheckRequest().setName("Acme Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        performGetWithParams("/api/v1/contacts/nearby", Map.of(
                        "longitude", "121.4737", "latitude", "31.2304",
                        "radiusMeters", "1000", "page", "1", "size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].contact.id").value(contact.getId()));

        ContactResponse updated = fromJson(performPut("/api/v1/contacts/" + contact.getId(),
                new ContactUpdateRequest().setVersion(contact.getVersion())
                        .setName("Acme Updated"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertTrue(updated.getVersion() > contact.getVersion());
        assertEquals("+8613800138000", updated.getPhoneNumber());
        ContactResponse cleared = fromJson(performPut("/api/v1/contacts/" + contact.getId(),
                new ContactUpdateRequest().setVersion(updated.getVersion())
                        .setPhoneNumber(""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertNull(cleared.getPhoneNumber());
        performPut("/api/v1/contacts/" + contact.getId(),
                new ContactUpdateRequest().setVersion(contact.getVersion())
                        .setName("Stale update"))
                .andExpect(status().isConflict());

        long otherActorId = 666666L;
        long otherTenantId = 666001L;
        bootstrap(otherActorId, otherTenantId, "Other Contact Owner");
        performAs(otherActorId, otherTenantId, get("/api/v1/contacts/{contactId}", contact.getId()))
                .andExpect(status().isNotFound());
        performAs(otherActorId, otherTenantId,
                put("/api/v1/contacts/{contactId}", contact.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContactUpdateRequest()
                                .setVersion(cleared.getVersion())
                                .setName("Cross-tenant update"))))
                .andExpect(status().isNotFound());

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/contacts/{contactId}", contact.getId())
                        .param("version", cleared.getVersion().toString()))
                .andExpect(status().isOk());
        performGet("/api/v1/contacts/{contactId}", contact.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void batchOwnerAndDeleteAreVersionedAndAtomic() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact batch owner");
        long replacementOwner = 777793L;
        seedActiveMember(replacementOwner, "Contact batch replacement owner");
        ContactResponse first = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contact batch first"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactResponse second = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contact batch second"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        performPut("/api/v1/contacts/batch/owner", new ContactBatchOwnerUpdateRequest()
                .setOwnerActorId(replacementOwner).setContacts(List.of(
                        new ContactBatchVersionRequest().setContactId(second.getId())
                                .setVersion(second.getVersion()),
                        new ContactBatchVersionRequest().setContactId(first.getId())
                                .setVersion(first.getVersion()))))
                .andExpect(status().isOk());
        ContactResponse transferredFirst = fromJson(performGet("/api/v1/contacts/" + first.getId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactResponse transferredSecond = fromJson(performGet("/api/v1/contacts/" + second.getId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertEquals(replacementOwner, transferredFirst.getOwnerActorId());
        assertEquals(replacementOwner, transferredSecond.getOwnerActorId());

        performPost("/api/v1/contacts/batch/delete", new ContactBatchDeleteRequest().setContacts(List.of(
                new ContactBatchVersionRequest().setContactId(first.getId())
                        .setVersion(transferredFirst.getVersion() - 1),
                new ContactBatchVersionRequest().setContactId(second.getId())
                        .setVersion(transferredSecond.getVersion()))))
                .andExpect(status().isConflict());
        performGet("/api/v1/contacts/" + first.getId()).andExpect(status().isOk());
        performGet("/api/v1/contacts/" + second.getId()).andExpect(status().isOk());

        performPost("/api/v1/contacts/batch/delete", new ContactBatchDeleteRequest().setContacts(List.of(
                new ContactBatchVersionRequest().setContactId(first.getId())
                        .setVersion(transferredFirst.getVersion()),
                new ContactBatchVersionRequest().setContactId(second.getId())
                        .setVersion(transferredSecond.getVersion()))))
                .andExpect(status().isOk());
        performGet("/api/v1/contacts/" + first.getId()).andExpect(status().isNotFound());
        performGet("/api/v1/contacts/" + second.getId()).andExpect(status().isNotFound());
    }

    @Test
    void contactCoreFieldsCustomerSavedViewsAndExplicitUnlinkRoundTrip() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact core-field owner");
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Associated customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ContactResponse parent = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Associated parent"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        LocalDateTime nextContactTime = LocalDateTime.of(2026, 8, 1, 9, 30);
        ContactResponse associated = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Associated contact")
                        .setPhoneNumber("+8613800138000").setTelephone("021-12345678")
                        .setJobTitle("Sales director").setEmail("sales@example.com")
                        .setWebsite("https://example.com").setDescription("Important contact")
                        .setNextContactTime(nextContactTime).setCustomerId(customer.getId())
                        .setParentContactId(parent.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertEquals("Sales director", associated.getJobTitle());
        assertEquals(nextContactTime, associated.getNextContactTime());
        assertEquals(customer.getId(), associated.getCustomerId());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("Sales director", jdbc.queryForObject(
                "SELECT job_title FROM crm_contact WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, associated.getId()));
        assertEquals(nextContactTime, jdbc.queryForObject(
                "SELECT next_contact_time FROM crm_contact WHERE tenant_id = ? AND id = ?",
                LocalDateTime.class, TENANT_ID, associated.getId()));

        performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/contacts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactQueryRequest().setName("Associated contact"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(associated.getId()))
                .andExpect(jsonPath("$.objectList[0].nextContactTime")
                        .value("2026-08-01T09:30:00"));

        assertSavedViewMatches("Job title", new FilterConditionRequest()
                .setFieldCode("job_title").setOperator("EQ").setValues(List.of("Sales director")),
                associated.getId());
        assertSavedViewMatches("Next contact", new FilterConditionRequest()
                .setFieldCode("next_contact_time").setOperator("GTE")
                .setValues(List.of("2026-08-01T09:30:00")), associated.getId());
        assertSavedViewMatches("Customer equals", new FilterConditionRequest()
                .setFieldCode("customer").setOperator("EQ")
                .setValues(List.of(customer.getId().toString())), associated.getId());
        assertSavedViewMatches("Customer in", new FilterConditionRequest()
                .setFieldCode("customer").setOperator("IN")
                .setValues(List.of(customer.getId().toString())), associated.getId());

        ContactUpdateRequest rename = new ContactUpdateRequest().setVersion(associated.getVersion())
                .setName("Associated contact renamed");
        String renameJson = toJson(rename);
        assertFalse(renameJson.contains("\"customerId\""));
        assertFalse(renameJson.contains("\"nextContactTime\""));
        ContactResponse renamed = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/contacts/{contactId}", associated.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(renameJson))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertEquals(customer.getId(), renamed.getCustomerId());
        assertEquals(nextContactTime, renamed.getNextContactTime());

        ContactResponse markersIgnored = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/contacts/{contactId}", associated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + renamed.getVersion()
                                + ",\"customerIdSpecified\":true"
                                + ",\"nextContactTimeSpecified\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertEquals(customer.getId(), markersIgnored.getCustomerId());
        assertEquals(nextContactTime, markersIgnored.getNextContactTime());

        ContactUpdateRequest clearRootFields = new ContactUpdateRequest()
                .setVersion(markersIgnored.getVersion()).setPhoneNumber("").setTelephone("")
                .setJobTitle("").setEmail("").setWebsite("").setDescription("")
                .setNextContactTime(null).setCustomerId(null);
        String clearRootFieldsJson = toJson(clearRootFields);
        assertTrue(clearRootFieldsJson.contains("\"nextContactTime\":null"));
        assertTrue(clearRootFieldsJson.contains("\"customerId\":null"));
        ContactResponse rootFieldsCleared = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/contacts/{contactId}", associated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearRootFieldsJson))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertNull(rootFieldsCleared.getPhoneNumber());
        assertNull(rootFieldsCleared.getTelephone());
        assertNull(rootFieldsCleared.getJobTitle());
        assertNull(rootFieldsCleared.getEmail());
        assertNull(rootFieldsCleared.getWebsite());
        assertNull(rootFieldsCleared.getDescription());
        assertNull(rootFieldsCleared.getNextContactTime());
        assertNull(rootFieldsCleared.getCustomerId());

        ContactResponse parentCleared = fromJson(performPut(
                "/api/v1/contacts/" + associated.getId() + "/parent",
                new ContactParentUpdateRequest().setVersion(rootFieldsCleared.getVersion())
                        .setParentContactId(null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertNull(parentCleared.getParentContactId());

        ContactResponse reloaded = fromJson(performGet("/api/v1/contacts/{contactId}",
                associated.getId()).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), ContactResponse.class);
        assertNull(reloaded.getPhoneNumber());
        assertNull(reloaded.getTelephone());
        assertNull(reloaded.getJobTitle());
        assertNull(reloaded.getEmail());
        assertNull(reloaded.getWebsite());
        assertNull(reloaded.getDescription());
        assertNull(reloaded.getNextContactTime());
        assertNull(reloaded.getCustomerId());
        assertNull(reloaded.getParentContactId());
        assertNull(jdbc.queryForObject("SELECT phone_number FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT telephone FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT job_title FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT email FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT website FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT description FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", String.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT next_contact_time FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", LocalDateTime.class,
                TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT customer_id FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT_ID, associated.getId()));
        assertNull(jdbc.queryForObject("SELECT parent_contact_id FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT_ID, associated.getId()));

        ContactResponse won = fromJson(performPut("/api/v1/contacts/" + associated.getId()
                        + "/state", new ContactStateUpdateRequest().setVersion(reloaded.getVersion())
                        .setLocked(false).setDealStatus("WON"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertTrue(won.getDealTime() != null);
        ContactResponse reopened = fromJson(performPut("/api/v1/contacts/" + associated.getId()
                        + "/state", new ContactStateUpdateRequest().setVersion(won.getVersion())
                        .setLocked(false).setDealStatus("OPEN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertNull(reopened.getDealTime());
        ContactResponse reloadedOpen = fromJson(performGet("/api/v1/contacts/{contactId}",
                associated.getId()).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), ContactResponse.class);
        assertNull(reloadedOpen.getDealTime());
        assertNull(jdbc.queryForObject("SELECT deal_time FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", LocalDateTime.class,
                TENANT_ID, associated.getId()));
    }

    @Test
    void savedViewRelationsRequireVisibleTargetsAndRejectRelationshipOracles() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact relation-view owner");
        long scopedActorId = 777779L;
        seedActiveMember(scopedActorId, "Relation-scoped reader");
        TenantRoleResponse reader = createRole("Relation-scoped contact reader",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("saved-views:write"));
        setFieldPolicy(reader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("contact_name")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("customer")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("parent_contact")
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
        ContactResponse visibleParent = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Visible relation parent")
                        .setOwnerActorId(scopedActorId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactResponse hiddenParent = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Hidden relation parent"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactResponse visibleChild = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Visible relation child")
                        .setOwnerActorId(scopedActorId).setCustomerId(visibleCustomer.getId())
                        .setParentContactId(visibleParent.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Hidden relation child")
                        .setOwnerActorId(scopedActorId).setCustomerId(hiddenCustomer.getId())
                        .setParentContactId(hiddenParent.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        SavedViewResponse visibleCustomerView = createSavedViewAs(scopedActorId,
                "Visible customer relation", new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("EQ").setValues(List.of(visibleCustomer.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("savedViewId", visibleCustomerView.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(visibleChild.getId()));

        SavedViewResponse visibleParentView = createSavedViewAs(scopedActorId,
                "Visible parent relation", new FilterConditionRequest().setFieldCode("parent_contact")
                        .setOperator("EQ").setValues(List.of(visibleParent.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("savedViewId", visibleParentView.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(visibleChild.getId()));

        SavedViewResponse hiddenCustomerView = createSavedViewAs(scopedActorId,
                "Hidden customer relation", new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("EQ").setValues(List.of(hiddenCustomer.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("savedViewId", hiddenCustomerView.getId().toString()))
                .andExpect(status().isForbidden());

        SavedViewResponse hiddenParentView = createSavedViewAs(scopedActorId,
                "Hidden parent relation", new FilterConditionRequest().setFieldCode("parent_contact")
                        .setOperator("IN").setValues(List.of(hiddenParent.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("savedViewId", hiddenParentView.getId().toString()))
                .andExpect(status().isForbidden());

        SavedViewResponse nonEmptyCustomerView = createSavedViewAs(scopedActorId,
                "Customer relation oracle", new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("IS_NOT_EMPTY").setValues(List.of()));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("savedViewId", nonEmptyCustomerView.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalizedPersistenceRepresentationsRespectDatabaseContracts() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact Normalization Owner");
        FieldDefinitionResponse uniqueText = fromJson(performPost(
                "/api/v1/fields/objects/CONTACT",
                new FieldCreateRequest().setDisplayName("Normalized unique text")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        String compatibilityLigatures = "ﬃ".repeat(500);
        ContactResponse contact = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Normalization account")
                        .setTags(List.of("e", "é", "VIP", "Ａ", "ﬃ"))
                        .setCustomFields(Map.of(uniqueText.getCode(),
                                List.of(compatibilityLigatures))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(List.of("e", "é", "VIP", "Ａ", "ﬃ"), contact.getTags());
        assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_tag "
                        + "WHERE tenant_id = ? AND contact_id = ?",
                Integer.class, TENANT_ID, contact.getId()));
        assertEquals(1_500, jdbc.queryForObject(
                "SELECT CHAR_LENGTH(normalized_value) "
                        + "FROM crm_contact_unique_field_value "
                        + "WHERE tenant_id = ? AND contact_id = ? AND field_id = ?",
                Integer.class, TENANT_ID, contact.getId(), uniqueText.getId()));

        ContactResponse aliases = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Tag aliases")
                        .setTags(List.of("vip", "a", "ffi")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        String[] catalog = fromJson(performGet("/api/v1/contacts/tags")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                String[].class);
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("VIP") || tag.equals("vip")).count());
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("Ａ") || tag.equals("a")).count());
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("ﬃ") || tag.equals("ffi")).count());

        ContactResponse batchChanged = fromJson(performPost("/api/v1/contacts/batch/tags",
                new ContactBatchTagRequest()
                        .setContacts(List.of(new ContactVersionRequest()
                                .setContactId(contact.getId()).setVersion(contact.getVersion())))
                        .setRemovedTags(List.of("ffi")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse[].class)[0];
        assertTrue(!batchChanged.getTags().contains("ﬃ"));
        // Re-read: normalized removal on one record must not strip the alias tag off another.
        assertTrue(fromJson(performGet("/api/v1/contacts/" + aliases.getId())
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class).getTags().contains("ffi"));

        String expandedWebsite = "https://example.invalid/" + "你".repeat(476);
        performPut("/api/v1/contacts/" + contact.getId(),
                new ContactUpdateRequest().setVersion(batchChanged.getVersion())
                        .setWebsite(expandedWebsite))
                .andExpect(status().is(422));
    }

    @Test
    void selfScopeAndFieldPoliciesPreventRecordAndDuplicateOracles() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact Scope Owner");
        FieldDefinitionResponse segment = createTextField("Scope segment");
        long targetActorId = 777777L;
        seedActiveMember(targetActorId, "Scoped Member");
        performAs(targetActorId, TENANT_ID, get("/api/v1/contacts"))
                .andExpect(status().isForbidden());

        TenantRoleResponse reader = createRole("Self Contact Readers",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/CONTACT/access", reader.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                                new RoleFieldAccessRequest().setFieldCode("contact_name")
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

        ContactResponse owned = createContact(
                OWNER_ACTOR_ID, TENANT_ID, "Scoped Account", targetActorId,
                segment.getCode(), "enterprise", false);
        ContactResponse privateContact = createContact(
                OWNER_ACTOR_ID, TENANT_ID, "Private Account", OWNER_ACTOR_ID,
                segment.getCode(), "private", false);

        performAs(targetActorId, TENANT_ID, get("/api/v1/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(owned.getId()))
                .andExpect(jsonPath("$.objectList[0].name").value("Scoped Account"))
                .andExpect(jsonPath("$.objectList[0].customFields."
                        + segment.getCode() + "[0]").value("e********e"));
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/contacts/{contactId}", privateContact.getId()))
                .andExpect(status().isNotFound());
        performAs(targetActorId, TENANT_ID, post("/api/v1/contacts/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactDuplicateCheckRequest()
                                .setName("Private Account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));
        performAs(targetActorId, TENANT_ID, post("/api/v1/contacts/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactDuplicateCheckRequest()
                                .setName("Scoped Account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        long maskedActorId = 777778L;
        seedActiveMember(maskedActorId, "Masked Reader");
        TenantRoleResponse maskedReader = createRole("Masked Contact Readers",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"));
        setFieldPolicy(maskedReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("contact_name")
                        .setAccessLevel("READ").setMaskType("PARTIAL"),
                new RoleFieldAccessRequest().setFieldCode("addresses")
                        .setAccessLevel("READ").setMaskType("PARTIAL"),
                new RoleFieldAccessRequest().setFieldCode("deal_status")
                        .setAccessLevel("READ").setMaskType("FULL")));
        assignRole(maskedReader.getId(), maskedActorId);
        ContactResponse maskedContact = createContact(
                OWNER_ACTOR_ID, TENANT_ID, "Masked Account", maskedActorId,
                segment.getCode(), "masked", true);
        ContactResponse wonContact = fromJson(performPut(
                "/api/v1/contacts/" + maskedContact.getId() + "/state",
                new ContactStateUpdateRequest().setVersion(maskedContact.getVersion())
                        .setLocked(false).setDealStatus("WON"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);

        ContactResponse maskedResponse = fromJson(performAs(maskedActorId, TENANT_ID,
                get("/api/v1/contacts/{contactId}", wonContact.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertEquals("M********t", maskedResponse.getName());
        assertEquals("[REDACTED]", maskedResponse.getDealStatus());
        org.junit.jupiter.api.Assertions.assertNull(maskedResponse.getDealTime());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("name", "Masked Account"))
                .andExpect(status().isForbidden());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performAs(maskedActorId, TENANT_ID, get("/api/v1/contacts")
                        .param("ownerActorId", Long.toString(maskedActorId)))
                .andExpect(status().isForbidden());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/contacts/nearby")
                        .param("longitude", "121.4737").param("latitude", "31.2304")
                        .param("radiusMeters", "1000"))
                .andExpect(status().isForbidden());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> hiddenDescendants = new ArrayList<>();
        for (int index = 0; index < 1_998; index++) {
            hiddenDescendants.add(new Object[]{
                    TENANT_ID, OWNER_ACTOR_ID, owned.getId(), "Hidden descendant " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, parent_contact_id, name, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                hiddenDescendants);
        ContactResponse visibleAfterHidden = fromJson(performPost(
                "/api/v1/contacts", new ContactCreateRequest()
                        .setName("Visible after hidden descendants")
                        .setOwnerActorId(targetActorId)
                        .setParentContactId(owned.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/contacts/{contactId}/hierarchy", owned.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(2))
                .andExpect(jsonPath("$.contacts[1].id").value(visibleAfterHidden.getId()));

        jdbc.batchUpdate("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, parent_contact_id, name, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                List.of(
                        new Object[]{TENANT_ID, OWNER_ACTOR_ID, owned.getId(),
                                "Hidden descendant 1998"},
                        new Object[]{TENANT_ID, OWNER_ACTOR_ID, owned.getId(),
                                "Hidden descendant 1999"}));
        performPost("/api/v1/contacts", new ContactCreateRequest()
                .setName("Visible after hidden traversal budget")
                .setOwnerActorId(targetActorId)
                .setParentContactId(owned.getId()))
                .andExpect(status().is(422));

        ContactResponse deepRoot = createContact(
                OWNER_ACTOR_ID, TENANT_ID, "Deep hierarchy root", targetActorId,
                segment.getCode(), "deep", false);
        long firstDeepId = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM crm_contact", Long.class);
        List<Object[]> deepDescendants = new ArrayList<>();
        for (int index = 0; index < 1_100; index++) {
            long id = firstDeepId + index;
            long parentId = index == 0 ? deepRoot.getId() : id - 1;
            deepDescendants.add(new Object[]{
                    id, TENANT_ID, OWNER_ACTOR_ID, parentId, "Deep descendant " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_contact "
                        + "(id, tenant_id, owner_actor_id, parent_contact_id, name, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                deepDescendants);
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/contacts/{contactId}/hierarchy", deepRoot.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.contacts[0].id").value(deepRoot.getId()));
    }

    @Test
    void departmentScopesAndCollaboratorWriteAccessFailClosed() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact Department Owner");
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

        TenantRoleResponse departmentReader = createRole("Department Contact Readers",
                new RoleGrantRequest().setAuthorityCode("contacts:read")
                        .setDataScope("DEPARTMENT"));
        setFieldPolicy(departmentReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("contact_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(departmentReader.getId(), salesActor);

        TenantRoleResponse subtreeReader = createRole("Subtree Contact Readers",
                new RoleGrantRequest().setAuthorityCode("contacts:read")
                        .setDataScope("DEPARTMENT_SUBTREE"));
        setFieldPolicy(subtreeReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("contact_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(subtreeReader.getId(), subtreeActor);

        createContact(OWNER_ACTOR_ID, TENANT_ID, "Sales Account", salesActor,
                segment.getCode(), "sales", false);
        ContactResponse eastContact = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "East Account", eastOwner,
                segment.getCode(), "east", false);
        createContact(OWNER_ACTOR_ID, TENANT_ID, "Service Account", serviceOwner,
                segment.getCode(), "service", false);

        performAs(salesActor, TENANT_ID, get("/api/v1/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("Sales Account"));
        performAs(subtreeActor, TENANT_ID, get("/api/v1/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(2));
        performAs(subtreeActor, TENANT_ID,
                delete("/api/v1/contacts/{contactId}/collaborators/me", eastContact.getId())
                        .param("version", eastContact.getVersion().toString()))
                .andExpect(status().isNotFound());
        assertEquals(eastContact.getVersion(), jdbc.queryForObject(
                "SELECT version FROM crm_contact WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, eastContact.getId()));

        TenantRoleResponse collaboratorRole = createRole("Contact Collaborators",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("contacts:write").setDataScope("SELF"));
        setFieldPolicy(collaboratorRole.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("contact_name")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("tags")
                        .setAccessLevel("WRITE").setMaskType("NONE")));
        assignRole(collaboratorRole.getId(), collaboratorActor);
        ContactResponse shared = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Shared Account", OWNER_ACTOR_ID, segment.getCode(), "shared", false);
        ContactResponse readOnly = replaceCollaborators(shared, collaboratorActor, "READ");
        assertEquals(collaboratorActor, readOnly.getCollaborators().getFirst().getActorId());
        assertEquals("READ", readOnly.getCollaborators().getFirst().getAccess());
        performAs(collaboratorActor, TENANT_ID,
                put("/api/v1/contacts/{contactId}/tags", shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactTagsUpdateRequest()
                                .setVersion(readOnly.getVersion()).setTags(List.of("Blocked")))))
                .andExpect(status().isNotFound());
        ContactResponse writable = replaceCollaborators(readOnly, collaboratorActor, "WRITE");
        performAs(collaboratorActor, TENANT_ID,
                put("/api/v1/contacts/{contactId}/tags", shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactTagsUpdateRequest()
                                .setVersion(writable.getVersion()).setTags(List.of("Shared")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("Shared"));
    }

    @Test
    void databaseArbitratesConcurrentUniqueValuesAndOpposingHierarchyMoves() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact Concurrency Owner");
        FieldDefinitionResponse registration = fromJson(performPost(
                "/api/v1/fields/objects/CONTACT",
                new FieldCreateRequest().setDisplayName("Registration")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);

        List<Integer> createStatuses = runConcurrently(
                () -> contactCreateStatus("Concurrent A", registration.getCode(), "same-value"),
                () -> contactCreateStatus("Concurrent B", registration.getCode(), "same-value"));
        assertEquals(List.of(200, 409), createStatuses.stream().sorted().toList());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact WHERE tenant_id = ? AND status = 'ACTIVE'",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_unique_field_value WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/objects/CONTACT/{fieldId}", registration.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FieldUpdateRequest()
                                .setVersion(registration.getVersion())
                                .setDisplayName(registration.getDisplayName())
                                .setUnique(false))))
                .andExpect(status().is(422));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_unique_field_value WHERE tenant_id = ?",
                Integer.class, TENANT_ID));

        FieldDefinitionResponse segment = createTextField("Hierarchy segment");
        ContactResponse first = createContact(OWNER_ACTOR_ID, TENANT_ID, "Parent A",
                OWNER_ACTOR_ID, segment.getCode(), "a", false);
        ContactResponse second = createContact(OWNER_ACTOR_ID, TENANT_ID, "Parent B",
                OWNER_ACTOR_ID, segment.getCode(), "b", false);
        ContactResponse child = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Hierarchy child")
                        .setParentContactId(first.getId())
                        .setCustomFields(Map.of(segment.getCode(), List.of("child"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertEquals(first.getId(), child.getParentContactId());
        ContactResponse movedChild = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/contacts/{contactId}/parent", child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactParentUpdateRequest()
                                .setVersion(child.getVersion())
                                .setParentContactId(second.getId()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        assertEquals(second.getId(), movedChild.getParentContactId());
        List<Integer> hierarchyStatuses = runConcurrently(
                () -> parentUpdateStatus(first, second.getId()),
                () -> parentUpdateStatus(second, first.getId()));
        assertEquals(List.of(200, 422), hierarchyStatuses.stream().sorted().toList());
        int twoNodeCycles = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM crm_contact first_contact
                JOIN crm_contact second_contact
                  ON second_contact.tenant_id = first_contact.tenant_id
                 AND second_contact.id = first_contact.parent_contact_id
                WHERE first_contact.tenant_id = ?
                  AND second_contact.parent_contact_id = first_contact.id
                """, Integer.class, TENANT_ID);
        assertEquals(0, twoNodeCycles);
    }

    @Test
    void tagCatalogIncludesOlderVisibleContactsBeyondTheFirstThousand() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact Tag Owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, name, status, updated_time) "
                        + "VALUES (?, ?, 'Legacy tagged account', 'ACTIVE', '2020-01-01 00:00:00')",
                TENANT_ID, OWNER_ACTOR_ID);
        Long legacyId = jdbc.queryForObject(
                "SELECT id FROM crm_contact WHERE tenant_id = ? AND name = 'Legacy tagged account'",
                Long.class, TENANT_ID);
        jdbc.update("INSERT INTO crm_contact_tag "
                        + "(tenant_id, contact_id, tag_name, tag_key) "
                        + "VALUES (?, ?, 'Legacy-only', 'legacy-only')",
                TENANT_ID, legacyId);
        List<Object[]> newerContacts = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            newerContacts.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, "Recent account " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, name, status, updated_time) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6))",
                newerContacts);

        String[] tags = fromJson(performGet("/api/v1/contacts/tags")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                String[].class);
        assertTrue(List.of(tags).contains("Legacy-only"));
    }

    private FieldDefinitionResponse createTextField(String name) throws Exception {
        return fromJson(performPost("/api/v1/fields/objects/CONTACT",
                new FieldCreateRequest().setDisplayName(name).setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
    }

    private void assertSavedViewMatches(
            String name, FilterConditionRequest condition, Long expectedContactId) throws Exception {
        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CONTACT").setName(name)
                        .setConditions(List.of(condition)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        mockMvc.perform(withTestActor(get("/api/v1/contacts")
                        .param("savedViewId", view.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(expectedContactId));
    }

    private SavedViewResponse createSavedViewAs(
            long actorId, String name, FilterConditionRequest condition) throws Exception {
        return fromJson(performAs(actorId, TENANT_ID, post("/api/v1/saved-views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SavedViewCreateRequest().setObjectType("CONTACT").setName(name)
                                .setConditions(List.of(condition)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
    }

    private ContactResponse createContact(
            long actorId, long tenantId, String name, long ownerActorId,
            String customFieldCode, String customFieldValue, boolean withAddress) throws Exception {
        ContactCreateRequest request = new ContactCreateRequest()
                .setName(name)
                .setPhoneNumber("+8613800138000")
                .setOwnerActorId(ownerActorId)
                .setTags(List.of("Enterprise"))
                .setCustomFields(Map.of(customFieldCode, List.of(customFieldValue)));
        if (withAddress) {
            request.setAddresses(List.of(new ContactAddressRequest()
                    .setKey("hq").setLabel("Headquarters")
                    .setAddressLine("Example road 1")
                    .setLongitude(new BigDecimal("121.4737"))
                    .setLatitude(new BigDecimal("31.2304"))
                    .setPrimary(true)));
        }
        return fromJson(performAs(actorId, tenantId, post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
    }

    private TenantRoleResponse createRole(String name, RoleGrantRequest... grants) throws Exception {
        return fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName(name).setGrants(List.of(grants)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    /**
     * Exporting a custom field is the only path that reads the custom-value projection.
     *
     * <p>Every other export test asks for built-in columns only, so the projection query was never
     * executed by the suite — it parsed and bound, which says nothing about whether it joins the
     * right definition or returns the value against the right row.</p>
     */
    @Test
    void contactCsvExportProjectsCustomFieldValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Contact CSV custom projection owner");
        FieldDefinitionResponse segment = createTextField("Contact CSV custom projection segment");
        ContactResponse contact = createContact(OWNER_ACTOR_ID, TENANT_ID,
                "Contact CSV custom projection", OWNER_ACTOR_ID,
                segment.getCode(), "enterprise-tier", false);

        String content = performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/contacts/export/csv").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactCsvExportRequest().setFieldCodes(
                                List.of(ContactFieldCatalog.NAME, segment.getCode())))))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(content.contains("enterprise-tier"),
                () -> "the custom value must reach the export, was: " + content);
        assertTrue(content.contains(contact.getId().toString()));
    }

    private List<RoleFieldAccessRequest> csvContactNamePolicy() {
        return List.of(new RoleFieldAccessRequest().setFieldCode(ContactFieldCatalog.NAME)
                .setAccessLevel("READ").setMaskType("NONE"));
    }

    private MockHttpServletRequestBuilder contactCsvExportRequest() {
        return post("/api/v1/contacts/export/csv")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ContactCsvExportRequest()
                        .setFieldCodes(List.of(ContactFieldCatalog.NAME))));
    }

    private void assertCsvContactIds(long actorId, long tenantId, List<Long> expectedIds)
            throws Exception {
        assertCsvContactIds(actorId, tenantId, expectedIds, null);
    }

    private void assertCsvContactIds(
            long actorId, long tenantId, List<Long> expectedIds, Long savedViewId) throws Exception {
        MockHttpServletRequestBuilder request = contactCsvExportRequest();
        if (savedViewId != null) {
            request.content(toJson(new ContactCsvExportRequest()
                    .setFieldCodes(List.of(ContactFieldCatalog.NAME)).setSavedViewId(savedViewId)));
        }
        String content = performAs(actorId, tenantId, request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Long> actualIds = content.lines().skip(1).filter(line -> !line.isBlank())
                .map(line -> Long.parseLong(line.substring(0, line.indexOf(',')))).toList();
        assertEquals(expectedIds.stream().sorted().toList(), actualIds);
    }

    private String contactCsvContent(long actorId, long tenantId) throws Exception {
        return performAs(actorId, tenantId, contactCsvExportRequest()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void startContactExportQueryCapture() {
        synchronized (CONTACT_EXPORT_QUERY_CAPTURE) {
            if (!sqlSessionFactory.getConfiguration().getInterceptors()
                    .contains(CONTACT_EXPORT_QUERY_CAPTURE)) {
                sqlSessionFactory.getConfiguration().addInterceptor(CONTACT_EXPORT_QUERY_CAPTURE);
            }
        }
        CONTACT_EXPORT_QUERY_CAPTURE.start();
    }

    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    private static final class ContactExportQueryCapture implements Interceptor {
        private final ThreadLocal<Boolean> active = new ThreadLocal<>();
        private final ThreadLocal<CapturedSql> capture = new ThreadLocal<>();

        private void start() {
            capture.remove();
            active.set(true);
        }

        private CapturedSql stop() {
            active.remove();
            CapturedSql captured = capture.get();
            capture.remove();
            return captured;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            if (Boolean.TRUE.equals(active.get())) {
                MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
                Object parameterObject = invocation.getArgs()[1];
                BoundSql boundSql = statement.getBoundSql(parameterObject);
                if (statement.getId().equals(ContactMapper.class.getName() + ".selectList")
                        && boundSql.getSql().contains("ORDER BY id ASC")
                        && boundSql.getSql().contains("LIMIT 5001")) {
                    capture.set(new CapturedSql(boundSql.getSql(), parameters(
                            statement, boundSql, parameterObject)));
                }
            }
            return invocation.proceed();
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }

        private List<Object> parameters(
                MappedStatement statement, BoundSql boundSql, Object parameterObject) {
            MetaObject metaObject = parameterObject == null ? null
                    : statement.getConfiguration().newMetaObject(parameterObject);
            return boundSql.getParameterMappings().stream().map(mapping -> parameter(
                    statement, boundSql, metaObject, parameterObject, mapping)).toList();
        }

        private Object parameter(
                MappedStatement statement, BoundSql boundSql, MetaObject metaObject,
                Object parameterObject, ParameterMapping mapping) {
            String property = mapping.getProperty();
            if (boundSql.hasAdditionalParameter(property)) {
                return boundSql.getAdditionalParameter(property);
            }
            if (parameterObject != null && statement.getConfiguration().getTypeHandlerRegistry()
                    .hasTypeHandler(parameterObject.getClass())) {
                return parameterObject;
            }
            return metaObject != null && metaObject.hasGetter(property)
                    ? metaObject.getValue(property) : null;
        }
    }

    private record CapturedSql(String sql, List<Object> parameters) {
    }

    private static final ContactExportQueryCapture CONTACT_EXPORT_QUERY_CAPTURE =
            new ContactExportQueryCapture();

    private static void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(failureMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failureMessage, exception);
        }
    }

    private void setFieldPolicy(Long roleId, List<RoleFieldAccessRequest> accesses) throws Exception {
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/CONTACT/access", roleId)
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

    private ContactResponse replaceCollaborators(
            ContactResponse contact, long actorId, String access) throws Exception {
        return fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/contacts/{contactId}/collaborators", contact.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactCollaboratorsUpdateRequest()
                                .setVersion(contact.getVersion())
                                .setCollaborators(List.of(new ContactCollaboratorRequest()
                                        .setActorId(actorId).setAccess(access))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
    }

    private int contactCreateStatus(String name, String fieldCode, String value) throws Exception {
        ContactCreateRequest request = new ContactCreateRequest().setName(name)
                .setCustomFields(Map.of(fieldCode, List.of(value)));
        return performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andReturn().getResponse().getStatus();
    }

    private int parentUpdateStatus(ContactResponse contact, Long parentId) throws Exception {
        return performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/contacts/{contactId}/parent", contact.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContactParentUpdateRequest()
                                .setVersion(contact.getVersion())
                                .setParentContactId(parentId))))
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
                            throw new AssertionError("Timed out waiting to start contact writes");
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
        clearContactData();
    }

    @Override
    protected void clearTestData() {
        clearContactData();
    }

    private void clearContactData() {
        executeSql("DELETE FROM crm_contact_star");
        executeSql("DELETE FROM crm_contact_unique_field_value");
        executeSql("DELETE FROM crm_contact_field_value");
        executeSql("DELETE FROM crm_contact_collaborator");
        executeSql("DELETE FROM crm_contact_tag");
        executeSql("DELETE FROM crm_contact_address");
        executeSql("UPDATE crm_contact SET parent_contact_id = NULL");
        executeSql("DELETE FROM crm_contact");
        executeSql("DELETE FROM crm_contact_guard");
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
}
