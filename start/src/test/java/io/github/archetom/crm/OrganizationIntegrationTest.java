package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.DepartmentCreateRequest;
import io.github.archetom.crm.api.dto.request.MemberProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.MembershipRequestCreateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.DepartmentResponse;
import io.github.archetom.crm.api.dto.response.MembershipRequestResponse;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Pager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end coverage for local organization initialization and tenant-visible departments. */
@RequiresDatabase
class OrganizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrganizationTenantRepository organizationTenantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void bootstrapCreatesAProfileAndAnActiveMembershipForTheVerifiedActor() throws Exception {
        ResultActions result = performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("First Member").setEmail("first@example.com"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(999999))
                .andExpect(jsonPath("$.actorId").value(888888))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM authorization_role WHERE tenant_id = ? AND system_key IS NOT NULL",
                Integer.class, 999999L));
        Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM authorization_role_assignment WHERE tenant_id = ? AND actor_id = ?",
                Integer.class, 999999L, 888888L));

        DepartmentResponse sales = fromJson(performPost("/api/v1/organization/departments",
                new DepartmentCreateRequest().setName("Sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sales"))
                .andReturn().getResponse().getContentAsString(), DepartmentResponse.class);
        DepartmentResponse fieldSales = fromJson(performPost("/api/v1/organization/departments",
                new DepartmentCreateRequest().setName("Field Sales").setParentDepartmentId(sales.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), DepartmentResponse.class);

        performGet("/api/v1/organization/departments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sales"));
        performGetWithParams("/api/v1/organization/departments/by-ids",
                java.util.Map.of("ids", sales.getId() + "," + fieldSales.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sales.getId()))
                .andExpect(jsonPath("$[1].id").value(fieldSales.getId()));
        performGet("/api/v1/organization/departments/{departmentId}/descendants", sales.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fieldSales.getId()));
        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET department_id = ? WHERE tenant_id = ? AND actor_id = ?",
                fieldSales.getId(), 999999L, 888888L);
        performGetWithParams("/api/v1/organization/departments/" + sales.getId() + "/members",
                java.util.Map.of("includeDescendants", "false", "page", "1", "size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
        performGetWithParams("/api/v1/organization/departments/" + sales.getId() + "/members",
                java.util.Map.of("includeDescendants", "true", "page", "1", "size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].actorId").value(888888))
                .andExpect(jsonPath("$.objectList[0].departmentName").value("Field Sales"));
        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET department_id = NULL WHERE tenant_id = ? AND actor_id = ?",
                999999L, 888888L);
        performGetWithParams("/api/v1/organization/members",
                java.util.Map.of("keyword", "first", "page", "1", "size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].actorId").value(888888));

        performDelete("/api/v1/organization/departments/{departmentId}", fieldSales.getId())
                .andExpect(status().isOk());
        performGet("/api/v1/organization/departments/{departmentId}/members", fieldSales.getId())
                .andExpect(status().isNotFound());

        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("Second Member"))
                .andExpect(status().isConflict());
    }

    @Test
    void targetAccessCheckReturnsDataWithoutSwitchingTheAuthenticatedTenant() throws Exception {
        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("First Member"));

        mockMvc.perform(withTestActor(get("/api/v1/organization/tenants/{tenantId}/access", 123456L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(123456))
                .andExpect(jsonPath("$.granted").value(false));

        performGet("/api/v1/organization/tenants/{tenantId}/access", 999999L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(999999))
                .andExpect(jsonPath("$.granted").value(true));
    }

    @Test
    void regionReferenceDataIsIndependentFromOrganizationMembership() throws Exception {
        performGet("/api/v1/reference/regions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void membershipRequestsArePagedAndTheDatabaseRejectsConcurrentPendingDuplicates() throws Exception {
        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("First Member"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_membership_request "
                        + "(applicant_actor_id, target_tenant_id, status, version) VALUES (?, ?, 'PENDING', 0)",
                777777L, 999999L);

        performGetWithParams("/api/v1/organization/membership-requests", java.util.Map.of("page", "1", "size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNum").value(1))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].applicantActorId").value(777777));

        Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO organization_membership_request "
                                + "(applicant_actor_id, target_tenant_id, status, version) VALUES (?, ?, 'PENDING', 0)",
                        777777L, 999999L));
    }

    @Test
    void applicantsCanPageAndWithdrawOnlyTheirOwnMembershipRequests() throws Exception {
        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("Target Owner"))
                .andExpect(status().isOk());

        MembershipRequestResponse own = submitMembershipRequest(777777L, 777001L, "Own Applicant", null);
        submitMembershipRequest(777778L, 777002L, "Other Applicant", null);

        performAs(777777L, 777001L, get("/api/v1/organization/membership-requests/mine")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(own.getId()))
                .andExpect(jsonPath("$.objectList[0].applicantActorId").value(777777))
                .andExpect(jsonPath("$.objectList[0].targetTenantId").value(999999));

        performAs(777777L, 777001L,
                post("/api/v1/organization/membership-requests/{requestId}/withdraw", own.getId()))
                .andExpect(status().isOk());

        performAs(777777L, 777001L, get("/api/v1/organization/membership-requests/mine")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].status").value("WITHDRAWN"));
    }

    @Test
    void membershipRequestStateMachineUsesPublicEndpointsAndRejectsCrossTenantReview() throws Exception {
        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("Target Owner"))
                .andExpect(status().isOk());
        DepartmentResponse sales = fromJson(performPost("/api/v1/organization/departments",
                new DepartmentCreateRequest().setName("Sales"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), DepartmentResponse.class);

        MembershipRequestResponse approved = submitMembershipRequest(777777L, 777001L, "Approve Applicant", sales.getId());

        performAs(666666L, 666001L, post("/api/v1/organization/bootstrap")
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Other Tenant Owner"))))
                .andExpect(status().isOk());
        performAs(666666L, 666001L, post("/api/v1/organization/membership-requests/{requestId}/approve", approved.getId()))
                .andExpect(status().isNotFound());

        performAs(888888L, 999999L,
                post("/api/v1/organization/membership-requests/{requestId}/approve", approved.getId()))
                .andExpect(status().isOk());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM authorization_role_assignment role_assignment "
                        + "JOIN authorization_role role ON role.tenant_id = role_assignment.tenant_id "
                        + "AND role.id = role_assignment.role_id "
                        + "WHERE role_assignment.tenant_id = ? AND role_assignment.actor_id = ? "
                        + "AND role.system_key = 'TENANT_MEMBER'",
                Integer.class, 999999L, 777777L));
        performAs(777777L, 777001L, get("/api/v1/organization/tenants/{tenantId}/access", 999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(true));
        performGetWithParams("/api/v1/organization/departments/" + sales.getId() + "/members",
                java.util.Map.of("keyword", "approve", "page", "1", "size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNum").value(1))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].actorId").value(777777));

        MembershipRequestResponse rejected = submitMembershipRequest(777778L, 777002L, "Reject Applicant", null);
        performAs(888888L, 999999L,
                post("/api/v1/organization/membership-requests/{requestId}/reject", rejected.getId()))
                .andExpect(status().isOk());

        MembershipRequestResponse withdrawn = submitMembershipRequest(777779L, 777003L, "Withdraw Applicant", null);
        performAs(777779L, 777003L, post("/api/v1/organization/membership-requests/{requestId}/withdraw", withdrawn.getId()))
                .andExpect(status().isOk());

        Pager<MembershipRequestResponse> requests = fromJson(performGetWithParams(
                "/api/v1/organization/membership-requests", java.util.Map.of("page", "1", "size", "20"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() {});
        Assertions.assertEquals("APPROVED", requestStatus(requests, approved.getId()));
        Assertions.assertEquals("REJECTED", requestStatus(requests, rejected.getId()));
        Assertions.assertEquals("WITHDRAWN", requestStatus(requests, withdrawn.getId()));
    }

    @Test
    void tenantStructureLockSerializesConcurrentDepartmentMutations() throws Exception {
        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("First Member"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                Assertions.assertTrue(organizationTenantRepository.lockForStructureChanges(new TenantId(999999L)));
                firstLockAcquired.countDown();
                await(releaseFirstTransaction);
            }));
            Assertions.assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS));

            Future<Boolean> second = executor.submit(() -> transaction.execute(status -> {
                secondTransactionStarted.countDown();
                return organizationTenantRepository.lockForStructureChanges(new TenantId(999999L));
            }));
            Assertions.assertTrue(secondTransactionStarted.await(5, TimeUnit.SECONDS));
            Assertions.assertThrows(TimeoutException.class,
                    () -> second.get(250, TimeUnit.MILLISECONDS),
                    "Second structure mutation must wait for the tenant lock");

            releaseFirstTransaction.countDown();
            first.get(5, TimeUnit.SECONDS);
            Assertions.assertTrue(second.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the test transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the test transaction", exception);
        }
    }

    private MembershipRequestResponse submitMembershipRequest(
            long actorId, long tenantId, String displayName, Long targetDepartmentId) throws Exception {
        performAs(actorId, tenantId, put("/api/v1/organization/profile")
                .content(toJson(new MemberProfileUpdateRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
        MembershipRequestCreateRequest request = new MembershipRequestCreateRequest()
                .setTargetTenantId(999999L)
                .setTargetDepartmentId(targetDepartmentId);
        return fromJson(performAs(actorId, tenantId, post("/api/v1/organization/membership-requests")
                .content(toJson(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MembershipRequestResponse.class);
    }

    private ResultActions performAs(long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", Long.toString(actorId))
                .header("X-Dev-Tenant-Id", Long.toString(tenantId)));
    }

    private String requestStatus(Pager<MembershipRequestResponse> requests, Long requestId) {
        return requests.getObjectList().stream()
                .filter(request -> requestId.equals(request.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Membership request was not returned: " + requestId))
                .getStatus();
    }

    @Override
    protected void initTestData() {
        executeSql("DELETE FROM organization_membership_request");
        executeSql("DELETE FROM navigation_preference");
        executeSql("DELETE FROM authorization_role_assignment");
        executeSql("DELETE FROM authorization_role_grant");
        executeSql("DELETE FROM authorization_role");
        executeSql("DELETE FROM organization_membership");
        executeSql("DELETE FROM organization_department");
        executeSql("DELETE FROM organization_member_profile");
        executeSql("DELETE FROM organization_tenant");
    }

    @Override
    protected void clearTestData() {
        initTestData();
    }
}
