package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.CustomerBusinessOverviewResponse;
import io.github.archetom.crm.api.dto.response.CustomerBusinessOverviewResponse.CardResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real-MySQL/HTTP coverage for B25a's fixed customer-first summary contract. */
@RequiresDatabase
class B25aIntegrationTest extends BaseIntegrationTest {

    private static final long TENANT = 1_725_100L;
    private static final long OWNER = 1_725_101L;
    private static final long SELF_READER = 1_725_102L;
    private static final long DEPARTMENT_READER = 1_725_103L;
    private static final long SUBTREE_READER = 1_725_104L;
    private static final long TENANT_READER = 1_725_105L;
    private static final long DEPARTMENT_PEER = 1_725_106L;
    private static final long SUBTREE_PEER = 1_725_107L;
    private static final long OTHER_OWNER = 1_725_108L;
    private static final long OTHER_TENANT = 1_725_109L;
    private static final long OUTSIDE_PEER = 1_725_110L;
    private static final long COLLABORATOR_READER = 1_725_111L;
    private static final long CONTACT_ONLY_READER = 1_725_112L;
    private static final long CUSTOMER_ONLY_READER = 1_725_113L;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 21);
    private static final List<SourceField> REQUIRED_SOURCE_FIELDS = List.of(
            new SourceField("CONTACT", "CONTACT", "customer"),
            new SourceField("OPEN_OPPORTUNITY", "OPPORTUNITY", "estimated_amount"),
            new SourceField("ACTIVE_CONTRACT", "CONTRACT", "total_amount"),
            new SourceField("PENDING_INVOICE", "INVOICE", "amount"),
            new SourceField("ACTIVE_RECEIVABLE", "RECEIVABLE", "amount"),
            new SourceField("ACTIVE_RECEIVABLE_PLAN", "RECEIVABLE_PLAN", "planned_amount"),
            new SourceField("RETURN_VISIT", "RETURN_VISIT", "status"));

    @MockitoSpyBean
    private CustomerRepository customerRepository;

    @Override
    protected void initTestData() {
        clearB25aData();
    }

    @Override
    protected void clearTestData() {
        clearB25aData();
    }

    @Test
    void fixedCardsUseDirectCustomerRelationsLifecyclePredicatesAndCustomerIndexes()
            throws Exception {
        mockMvc.perform(get("/api/v1/customers/1/business-overview"))
                .andExpect(status().isUnauthorized());
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long customerId = customer(jdbc);
        long contactId = contact(jdbc, customerId, "ACTIVE");
        contact(jdbc, customerId, "IN_POOL");
        opportunity(jdbc, customerId, "OPEN", "ACTIVE", "10.10");
        opportunity(jdbc, customerId, "WON", "ACTIVE", "90.90");
        long contractId = contract(jdbc, customerId, "ACTIVE", "20.20", "active");
        contract(jdbc, customerId, "VOIDED", "80.80", "voided");
        invoice(jdbc, customerId, contractId, "PENDING", "30.30", "pending");
        invoice(jdbc, customerId, contractId, "ISSUED", "70.70", "issued");
        plan(jdbc, customerId, contractId, "ACTIVE", "40.40", 1);
        plan(jdbc, customerId, contractId, "DELETED", "60.60", 2);
        receivable(jdbc, customerId, contractId, "ACTIVE", "50.50", "active");
        receivable(jdbc, customerId, contractId, "DELETED", "70.70", "deleted");
        visit(jdbc, customerId, contactId, contractId, "ACTIVE", "active");
        visit(jdbc, customerId, contactId, contractId, "DELETED", "deleted");

        CustomerBusinessOverviewResponse overview = fromJson(mockMvc.perform(as(OWNER,
                        get("/api/v1/customers/{customerId}/business-overview", customerId)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerBusinessOverviewResponse.class);
        assertEquals(customerId, overview.getCustomerId());
        Map<String, CardResponse> cards = overview.getCards().stream().collect(Collectors.toMap(
                CardResponse::getSource, Function.identity()));
        assertEquals(List.of("CONTACT", "OPEN_OPPORTUNITY", "ACTIVE_CONTRACT",
                        "PENDING_INVOICE", "ACTIVE_RECEIVABLE", "ACTIVE_RECEIVABLE_PLAN",
                        "RETURN_VISIT"), overview.getCards().stream().map(CardResponse::getSource).toList());
        assertCard(cards.get("CONTACT"), 1L, null);
        assertCard(cards.get("OPEN_OPPORTUNITY"), 1L, "10.10");
        assertCard(cards.get("ACTIVE_CONTRACT"), 1L, "20.20");
        assertCard(cards.get("PENDING_INVOICE"), 1L, "30.30");
        assertCard(cards.get("ACTIVE_RECEIVABLE"), 1L, "50.50");
        assertCard(cards.get("ACTIVE_RECEIVABLE_PLAN"), 1L, "40.40");
        assertCard(cards.get("RETURN_VISIT"), 1L, null);

        var contacts = fromJson(mockMvc.perform(as(OWNER, get("/api/v1/contacts")
                        .param("customerId", Long.toString(customerId))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new tools.jackson.core.type.TypeReference<io.github.archetom.common.result.Pager<ContactResponse>>() { });
        assertEquals(1L, contacts.getTotalNum());
        assertEquals(contactId, contacts.getObjectList().getFirst().getId());

        assertCustomerIndex(jdbc, "crm_contact", "idx_crm_contact_tenant_customer",
                customerId, "ACTIVE", "");
        assertCustomerIndex(jdbc, "crm_opportunity", "idx_crm_opportunity_tenant_customer",
                customerId, "ACTIVE", " AND outcome = 'OPEN'");
        assertCustomerIndex(jdbc, "crm_contract", "idx_crm_contract_tenant_customer",
                customerId, "ACTIVE", "");
        assertCustomerIndex(jdbc, "crm_invoice", "idx_crm_invoice_tenant_customer",
                customerId, "PENDING", "");
        assertCustomerIndex(jdbc, "crm_receivable", "idx_crm_receivable_tenant_customer",
                customerId, "ACTIVE", "");
        assertCustomerIndex(jdbc, "crm_receivable_plan", "idx_crm_receivable_plan_tenant_customer",
                customerId, "ACTIVE", "");
        assertCustomerIndex(jdbc, "crm_return_visit", "idx_crm_return_visit_tenant_customer",
                customerId, "ACTIVE", "");
    }

    @Test
    void sourceScopesAndFieldPoliciesProduceOpaquePartialCardsWithoutNullFields()
            throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = department(jdbc, 0L, "B25a Sales");
        long salesChild = department(jdbc, sales, "B25a Sales Child");
        long other = department(jdbc, 0L, "B25a Other");
        member(jdbc, SELF_READER, other, "B25a self reader");
        member(jdbc, DEPARTMENT_READER, sales, "B25a department reader");
        member(jdbc, SUBTREE_READER, sales, "B25a subtree reader");
        member(jdbc, TENANT_READER, other, "B25a tenant reader");
        member(jdbc, DEPARTMENT_PEER, sales, "B25a department peer");
        member(jdbc, SUBTREE_PEER, salesChild, "B25a subtree peer");
        long customerId = customer(jdbc, "B25a scope customer", "ACTIVE", OWNER);
        contact(jdbc, customerId, SELF_READER, "ACTIVE", "self");
        contact(jdbc, customerId, DEPARTMENT_PEER, "ACTIVE", "department");
        contact(jdbc, customerId, SUBTREE_PEER, "ACTIVE", "subtree");

        overviewRole(jdbc, SELF_READER, "SELF");
        overviewRole(jdbc, DEPARTMENT_READER, "DEPARTMENT");
        overviewRole(jdbc, SUBTREE_READER, "DEPARTMENT_SUBTREE");
        long tenantRole = overviewRole(jdbc, TENANT_READER, "TENANT");

        assertContactScope(overview(SELF_READER, customerId), 1L);
        assertContactScope(overview(DEPARTMENT_READER, customerId), 1L);
        assertContactScope(overview(SUBTREE_READER, customerId), 2L);
        assertContactScope(overview(TENANT_READER, customerId), 3L);

        maskField(jdbc, tenantRole, "CONTACT", "customer");
        String maskedJson = overviewJson(TENANT_READER, TENANT, customerId);
        CustomerBusinessOverviewResponse masked = fromJson(maskedJson,
                CustomerBusinessOverviewResponse.class);
        assertUnavailable(card(masked, "CONTACT"));
        assertFalse(maskedJson.contains("\"count\":null"));
        assertFalse(maskedJson.contains("\"amount\":null"));

        hideField(jdbc, tenantRole, "CONTACT", "customer");
        assertUnavailable(card(overview(TENANT_READER, customerId), "CONTACT"));
    }

    @Test
    void everySourceIndependentlyAppliesAllScopesAndItsRequiredField() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = department(jdbc, 0L, "B25a all-source sales");
        long salesChild = department(jdbc, sales, "B25a all-source sales child");
        long other = department(jdbc, 0L, "B25a all-source other");
        member(jdbc, SELF_READER, sales, "B25a all-source self reader");
        member(jdbc, DEPARTMENT_READER, sales, "B25a all-source department reader");
        member(jdbc, SUBTREE_READER, sales, "B25a all-source subtree reader");
        member(jdbc, TENANT_READER, other, "B25a all-source tenant reader");
        member(jdbc, DEPARTMENT_PEER, sales, "B25a all-source department peer");
        member(jdbc, SUBTREE_PEER, salesChild, "B25a all-source subtree peer");
        member(jdbc, OUTSIDE_PEER, other, "B25a all-source outside peer");
        long customerId = customer(jdbc, "B25a all-source customer", "ACTIVE", OWNER);
        sourceRoots(jdbc, customerId, SELF_READER, "scope-self");
        sourceRoots(jdbc, customerId, DEPARTMENT_PEER, "scope-department");
        sourceRoots(jdbc, customerId, SUBTREE_PEER, "scope-subtree");
        sourceRoots(jdbc, customerId, OUTSIDE_PEER, "scope-tenant");

        completeOverviewRole(jdbc, SELF_READER, "SELF");
        completeOverviewRole(jdbc, DEPARTMENT_READER, "DEPARTMENT");
        completeOverviewRole(jdbc, SUBTREE_READER, "DEPARTMENT_SUBTREE");
        long tenantRole = completeOverviewRole(jdbc, TENANT_READER, "TENANT");

        assertEverySourceCount(overview(SELF_READER, customerId), 1L);
        assertEverySourceCount(overview(DEPARTMENT_READER, customerId), 2L);
        assertEverySourceCount(overview(SUBTREE_READER, customerId), 3L);
        assertEverySourceCount(overview(TENANT_READER, customerId), 4L);

        for (SourceField field : REQUIRED_SOURCE_FIELDS) {
            maskField(jdbc, tenantRole, field.objectType(), field.fieldCode());
            assertOnlySourceUnavailable(overview(TENANT_READER, customerId), field.source(), 4L);
            unmaskField(jdbc, tenantRole, field.objectType(), field.fieldCode());
        }
    }

    @Test
    void collaborationOnlyExpandsTheThreeSourceDomainsThatSupportIt() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long department = department(jdbc, 0L, "B25a collaborator department");
        member(jdbc, OUTSIDE_PEER, department, "B25a collaborator owner");
        member(jdbc, COLLABORATOR_READER, department, "B25a collaborator reader");
        long customerId = customer(jdbc, "B25a collaborator customer", "ACTIVE", OWNER);
        SourceRoots source = sourceRoots(jdbc, customerId, OUTSIDE_PEER, "collaborator");
        jdbc.update("INSERT INTO crm_contact_collaborator "
                        + "(tenant_id, contact_id, actor_id, access_level) VALUES (?, ?, ?, 'READ')",
                TENANT, source.contactId(), COLLABORATOR_READER);
        jdbc.update("INSERT INTO crm_opportunity_collaborator "
                        + "(tenant_id, opportunity_id, actor_id, access_level) VALUES (?, ?, ?, 'READ')",
                TENANT, source.opportunityId(), COLLABORATOR_READER);
        jdbc.update("INSERT INTO crm_contract_collaborator "
                        + "(tenant_id, contract_id, actor_id, access_level) VALUES (?, ?, ?, 'READ')",
                TENANT, source.contractId(), COLLABORATOR_READER);
        completeOverviewRole(jdbc, COLLABORATOR_READER, "SELF");

        CustomerBusinessOverviewResponse overview = overview(COLLABORATOR_READER, customerId);
        assertCard(card(overview, "CONTACT"), 1L, null);
        assertCard(card(overview, "OPEN_OPPORTUNITY"), 1L, "10.00");
        assertCard(card(overview, "ACTIVE_CONTRACT"), 1L, "10.00");
        assertCard(card(overview, "PENDING_INVOICE"), 0L, "0.00");
        assertCard(card(overview, "ACTIVE_RECEIVABLE"), 0L, "0.00");
        assertCard(card(overview, "ACTIVE_RECEIVABLE_PLAN"), 0L, "0.00");
        assertCard(card(overview, "RETURN_VISIT"), 0L, null);
    }

    @Test
    void contactCustomerDrillDownRequiresBothIndependentCustomerAndContactScopes()
            throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long department = department(jdbc, 0L, "B25a contact intersection department");
        member(jdbc, CONTACT_ONLY_READER, department, "B25a contact-only reader");
        member(jdbc, CUSTOMER_ONLY_READER, department, "B25a customer-only reader");
        long customerId = customer(jdbc, "B25a contact intersection customer", "ACTIVE", OWNER);
        contact(jdbc, customerId, OWNER, "ACTIVE", "intersection");
        contactDrillDownRole(jdbc, CONTACT_ONLY_READER, "SELF", "TENANT");
        contactDrillDownRole(jdbc, CUSTOMER_ONLY_READER, "TENANT", "SELF");

        mockMvc.perform(as(CONTACT_ONLY_READER,
                        get("/api/v1/contacts").param("customerId", Long.toString(customerId))))
                .andExpect(status().isNotFound());
        var noContactScope = fromJson(mockMvc.perform(as(CUSTOMER_ONLY_READER,
                        get("/api/v1/contacts").param("customerId", Long.toString(customerId))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new tools.jackson.core.type.TypeReference<io.github.archetom.common.result.Pager<ContactResponse>>() { });
        assertEquals(0L, noContactScope.getTotalNum());
    }

    @Test
    void emptyAndUnavailableCustomersHaveDistinctSafeEndpointSemantics() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long emptyCustomer = customer(jdbc, "B25a empty customer", "ACTIVE", OWNER);
        CustomerBusinessOverviewResponse empty = overview(OWNER, emptyCustomer);
        for (CardResponse card : empty.getCards()) {
            assertEquals("AVAILABLE", card.getStatus());
            assertEquals(0L, card.getCount());
        }

        long pooledCustomer = customer(jdbc, "B25a pooled customer", "IN_POOL", OWNER);
        long deletedCustomer = customer(jdbc, "B25a deleted customer", "DELETED", OWNER);
        mockMvc.perform(as(OWNER, get("/api/v1/customers/{customerId}/business-overview", pooledCustomer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(OWNER, get("/api/v1/customers/{customerId}/business-overview", deletedCustomer)))
                .andExpect(status().isNotFound());

        bootstrap(OTHER_OWNER, OTHER_TENANT, "B25a other tenant owner");
        long otherCustomer = customer(jdbc, OTHER_TENANT, OTHER_OWNER,
                "B25a cross-tenant customer", "ACTIVE");
        mockMvc.perform(as(OWNER, TENANT,
                        get("/api/v1/customers/{customerId}/business-overview", otherCustomer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void overviewRetainsOneRepeatableReadSnapshotAcrossAConcurrentSourceCommit() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long customerId = customer(jdbc, "B25a snapshot customer", "ACTIVE", OWNER);
        SnapshotBarrier barrier = new SnapshotBarrier();
        doAnswer(invocation -> {
            Object customer = invocation.callRealMethod();
            CustomerId requestedId = invocation.getArgument(1);
            if (requestedId.getValue().equals(customerId)) {
                barrier.rootRead();
            }
            return customer;
        }).when(customerRepository).findVisibleById(any(), any(), any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> inFlightOverview = executor.submit(
                    () -> overviewJson(OWNER, TENANT, customerId));
            barrier.awaitRootRead();
            contact(jdbc, customerId, OWNER, "ACTIVE", "committed-after-snapshot");
            barrier.releaseSources();

            CustomerBusinessOverviewResponse snapshot = fromJson(
                    inFlightOverview.get(10, TimeUnit.SECONDS), CustomerBusinessOverviewResponse.class);
            assertCard(card(snapshot, "CONTACT"), 0L, null);
            assertCard(card(overview(OWNER, customerId), "CONTACT"), 1L, null);
        } finally {
            barrier.releaseSources();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void assertCard(CardResponse card, long count, String amount) {
        assertEquals("AVAILABLE", card.getStatus());
        assertEquals(count, card.getCount());
        if (amount == null) {
            assertNull(card.getAmount());
        } else {
            assertEquals(new BigDecimal(amount), card.getAmount());
        }
    }

    private void assertContactScope(CustomerBusinessOverviewResponse overview, long count) {
        assertCard(card(overview, "CONTACT"), count, null);
        for (String source : List.of("OPEN_OPPORTUNITY", "ACTIVE_CONTRACT", "PENDING_INVOICE",
                "ACTIVE_RECEIVABLE", "ACTIVE_RECEIVABLE_PLAN", "RETURN_VISIT")) {
            assertUnavailable(card(overview, source));
        }
    }

    private void assertEverySourceCount(CustomerBusinessOverviewResponse overview, long count) {
        for (CardResponse card : overview.getCards()) {
            assertEquals("AVAILABLE", card.getStatus());
            assertEquals(count, card.getCount(), card.getSource());
        }
    }

    private void assertOnlySourceUnavailable(
            CustomerBusinessOverviewResponse overview, String unavailableSource, long availableCount) {
        for (CardResponse card : overview.getCards()) {
            if (unavailableSource.equals(card.getSource())) {
                assertUnavailable(card);
            } else {
                assertEquals("AVAILABLE", card.getStatus(), card.getSource());
                assertEquals(availableCount, card.getCount(), card.getSource());
            }
        }
    }

    private void assertUnavailable(CardResponse card) {
        assertEquals("UNAVAILABLE", card.getStatus());
        assertNull(card.getCount());
        assertNull(card.getAmount());
    }

    private CardResponse card(CustomerBusinessOverviewResponse overview, String source) {
        return overview.getCards().stream().filter(card -> source.equals(card.getSource()))
                .findFirst().orElseThrow(() -> new AssertionError("card is missing: " + source));
    }

    private void assertCustomerIndex(
            JdbcTemplate jdbc, String table, String expectedIndex, long customerId,
            String status, String extraPredicate) {
        List<Map<String, Object>> plan = jdbc.queryForList(
                "EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(*) FROM " + table
                        + " WHERE tenant_id = ? AND customer_id = ? AND status = ?" + extraPredicate,
                TENANT, customerId, status);
        assertNotNull(plan.getFirst().get("key"));
        assertTrue(String.valueOf(plan.getFirst().get("possible_keys")).contains(expectedIndex),
                () -> "customer index is not a candidate for " + table + ": " + plan);
        assertNotEquals("ALL", String.valueOf(plan.getFirst().get("type")),
                () -> "unexpected full scan for " + table + ": " + plan);
    }

    private void bootstrap() throws Exception {
        bootstrap(OWNER, TENANT, "B25a owner");
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName)))
                        .header("X-Dev-User-Id", actorId)
                        .header("X-Dev-Tenant-Id", tenantId))
                .andExpect(status().isOk());
    }

    private long customer(JdbcTemplate jdbc) {
        return customer(jdbc, "B25a customer", "ACTIVE", OWNER);
    }

    private long customer(JdbcTemplate jdbc, String name, String status, long ownerActorId) {
        return customer(jdbc, TENANT, ownerActorId, name, status);
    }

    private long customer(
            JdbcTemplate jdbc, long tenantId, long ownerActorId, String name, String status) {
        jdbc.update("INSERT INTO crm_customer (tenant_id, owner_actor_id, name, status, deal_status) "
                        + "VALUES (?, ?, ?, ?, 'OPEN')", tenantId, ownerActorId, name, status);
        return jdbc.queryForObject("SELECT id FROM crm_customer WHERE tenant_id = ? "
                        + "AND name = ?", Long.class, tenantId, name);
    }

    private long contact(JdbcTemplate jdbc, long customerId, String status) {
        return contact(jdbc, customerId, OWNER, status, status);
    }

    private long contact(
            JdbcTemplate jdbc, long customerId, long ownerActorId, String status, String suffix) {
        jdbc.update("INSERT INTO crm_contact (tenant_id, owner_actor_id, customer_id, name, status) "
                        + "VALUES (?, ?, ?, ?, ?)", TENANT, ownerActorId, customerId,
                "B25a contact " + suffix, status);
        return jdbc.queryForObject("SELECT id FROM crm_contact WHERE tenant_id = ? "
                        + "AND name = ?", Long.class, TENANT, "B25a contact " + suffix);
    }

    private long opportunity(
            JdbcTemplate jdbc, long customerId, String outcome, String status, String amount) {
        OpportunityPipelineFixture.provisionActivePair(jdbc, TENANT, "direct", "qualification");
        jdbc.update("INSERT INTO crm_opportunity (tenant_id, owner_actor_id, customer_id, name, "
                        + "pipeline_code, stage_code, estimated_amount, outcome, outcome_time, status) "
                        + "VALUES (?, ?, ?, ?, 'direct', 'qualification', ?, ?, ?, ?)",
                TENANT, OWNER, customerId, "B25a opportunity " + outcome,
                new BigDecimal(amount), outcome,
                "OPEN".equals(outcome) ? null : LocalDateTime.of(DATE, java.time.LocalTime.NOON), status);
        return jdbc.queryForObject("SELECT id FROM crm_opportunity WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT, "B25a opportunity " + outcome);
    }

    private SourceRoots sourceRoots(
            JdbcTemplate jdbc, long customerId, long ownerActorId, String suffix) {
        long contactId = contact(jdbc, customerId, ownerActorId, "ACTIVE", suffix);
        long opportunityId = opportunity(jdbc, customerId, ownerActorId, suffix);
        long contractId = contract(jdbc, customerId, ownerActorId, "ACTIVE", "10.00", suffix);
        invoice(jdbc, customerId, contractId, ownerActorId, "PENDING", "10.00", suffix);
        plan(jdbc, customerId, contractId, ownerActorId, "ACTIVE", "10.00", 1);
        receivable(jdbc, customerId, contractId, ownerActorId, "ACTIVE", "10.00", suffix);
        visit(jdbc, customerId, contactId, contractId, ownerActorId, "ACTIVE", suffix);
        return new SourceRoots(contactId, opportunityId, contractId);
    }

    private long opportunity(JdbcTemplate jdbc, long customerId, long ownerActorId, String suffix) {
        OpportunityPipelineFixture.provisionActivePair(jdbc, TENANT, "direct", "qualification");
        jdbc.update("INSERT INTO crm_opportunity (tenant_id, owner_actor_id, customer_id, name, "
                        + "pipeline_code, stage_code, estimated_amount, outcome, status) "
                        + "VALUES (?, ?, ?, ?, 'direct', 'qualification', 10.00, 'OPEN', 'ACTIVE')",
                TENANT, ownerActorId, customerId, "B25a opportunity " + suffix);
        return jdbc.queryForObject("SELECT id FROM crm_opportunity WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT, "B25a opportunity " + suffix);
    }

    private long contract(
            JdbcTemplate jdbc, long customerId, String status, String amount, String suffix) {
        return contract(jdbc, customerId, OWNER, status, amount, suffix);
    }

    private long contract(
            JdbcTemplate jdbc, long customerId, long ownerActorId,
            String status, String amount, String suffix) {
        jdbc.update("INSERT INTO crm_contract (tenant_id, owner_actor_id, customer_id, name, "
                        + "contract_number, contract_number_key, order_date, subtotal, discount_rate, "
                        + "total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                TENANT, ownerActorId, customerId, "B25a contract " + suffix, "B25A-" + suffix,
                "b25a-" + suffix, DATE, new BigDecimal(amount), new BigDecimal(amount), status);
        return jdbc.queryForObject("SELECT id FROM crm_contract WHERE tenant_id = ? "
                        + "AND contract_number = ?", Long.class, TENANT, "B25A-" + suffix);
    }

    private void invoice(
            JdbcTemplate jdbc, long customerId, long contractId, String status, String amount, String suffix) {
        invoice(jdbc, customerId, contractId, OWNER, status, amount, suffix);
    }

    private void invoice(
            JdbcTemplate jdbc, long customerId, long contractId, long ownerActorId,
            String status, String amount, String suffix) {
        boolean issued = "ISSUED".equals(status);
        jdbc.update("INSERT INTO crm_invoice (tenant_id, owner_actor_id, customer_id, contract_id, "
                        + "application_number, amount, requested_date, invoice_type, title_type, title, "
                        + "tax_number, status, invoice_number, issued_date) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'VAT', 'ORGANIZATION', 'B25a title', "
                        + "'TAX-B25A', ?, ?, ?)",
                TENANT, ownerActorId, customerId, contractId, "B25A-I-" + suffix,
                new BigDecimal(amount), DATE,
                status, issued ? "B25A-ISSUED" : null, issued ? DATE : null);
    }

    private void plan(
            JdbcTemplate jdbc, long customerId, long contractId, String status, String amount, int installment) {
        plan(jdbc, customerId, contractId, OWNER, status, amount, installment);
    }

    private void plan(
            JdbcTemplate jdbc, long customerId, long contractId, long ownerActorId,
            String status, String amount, int installment) {
        jdbc.update("INSERT INTO crm_receivable_plan (tenant_id, owner_actor_id, customer_id, contract_id, "
                        + "installment, due_date, planned_amount, payment_method, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'BANK', ?)",
                TENANT, ownerActorId, customerId, contractId, installment,
                DATE.plusDays(30L * installment),
                new BigDecimal(amount), status);
    }

    private void receivable(
            JdbcTemplate jdbc, long customerId, long contractId, String status, String amount, String suffix) {
        receivable(jdbc, customerId, contractId, OWNER, status, amount, suffix);
    }

    private void receivable(
            JdbcTemplate jdbc, long customerId, long contractId, long ownerActorId,
            String status, String amount, String suffix) {
        jdbc.update("INSERT INTO crm_receivable (tenant_id, owner_actor_id, customer_id, contract_id, "
                        + "receipt_number, received_date, payment_method, amount, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'BANK', ?, ?)",
                TENANT, ownerActorId, customerId, contractId, "B25A-R-" + suffix, DATE,
                new BigDecimal(amount), status);
    }

    private void visit(
            JdbcTemplate jdbc, long customerId, long contactId, long contractId,
            String status, String suffix) {
        visit(jdbc, customerId, contactId, contractId, OWNER, status, suffix);
    }

    private void visit(
            JdbcTemplate jdbc, long customerId, long contactId, long contractId, long ownerActorId,
            String status, String suffix) {
        jdbc.update("INSERT INTO crm_return_visit (tenant_id, owner_actor_id, customer_id, contact_id, "
                        + "contract_id, visit_number, visit_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                TENANT, ownerActorId, customerId, contactId, contractId, "B25A-V-" + suffix,
                LocalDateTime.of(DATE, java.time.LocalTime.NOON), status);
    }

    private CustomerBusinessOverviewResponse overview(long actorId, long customerId) throws Exception {
        return fromJson(overviewJson(actorId, TENANT, customerId),
                CustomerBusinessOverviewResponse.class);
    }

    private String overviewJson(long actorId, long tenantId, long customerId) throws Exception {
        return mockMvc.perform(as(actorId, tenantId,
                        get("/api/v1/customers/{customerId}/business-overview", customerId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long department(JdbcTemplate jdbc, long parentId, String name) {
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT, parentId, name);
        return jdbc.queryForObject("SELECT id FROM organization_department "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT, name);
    }

    private void member(JdbcTemplate jdbc, long actorId, long departmentId, String displayName) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT, actorId, departmentId);
    }

    private long overviewRole(JdbcTemplate jdbc, long actorId, String contactScope) {
        String name = "B25a overview " + actorId;
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT, name);
        grant(jdbc, roleId, "customers:read", "TENANT");
        grant(jdbc, roleId, "contacts:read", contactScope);
        allFields(jdbc, roleId, "CUSTOMER");
        allFields(jdbc, roleId, "CONTACT");
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, actorId, roleId);
        return roleId;
    }

    private long completeOverviewRole(JdbcTemplate jdbc, long actorId, String sourceScope) {
        String name = "B25a complete overview " + actorId;
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT, name);
        grant(jdbc, roleId, "customers:read", "TENANT");
        grant(jdbc, roleId, "contacts:read", sourceScope);
        grant(jdbc, roleId, "opportunities:read", sourceScope);
        grant(jdbc, roleId, "contracts:read", sourceScope);
        grant(jdbc, roleId, "invoices:read", sourceScope);
        grant(jdbc, roleId, "receivables:read", sourceScope);
        grant(jdbc, roleId, "receivable-plans:read", sourceScope);
        grant(jdbc, roleId, "return-visits:read", sourceScope);
        for (String objectType : List.of("CUSTOMER", "CONTACT", "OPPORTUNITY", "CONTRACT",
                "INVOICE", "RECEIVABLE", "RECEIVABLE_PLAN", "RETURN_VISIT")) {
            allFields(jdbc, roleId, objectType);
        }
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, actorId, roleId);
        return roleId;
    }

    private void contactDrillDownRole(
            JdbcTemplate jdbc, long actorId, String customerScope, String contactScope) {
        String name = "B25a contact drill-down " + actorId;
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT, name);
        grant(jdbc, roleId, "customers:read", customerScope);
        grant(jdbc, roleId, "contacts:read", contactScope);
        allFields(jdbc, roleId, "CUSTOMER");
        allFields(jdbc, roleId, "CONTACT");
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, actorId, roleId);
    }

    private void grant(JdbcTemplate jdbc, long roleId, String authority, String dataScope) {
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) VALUES (?, ?, ?, ?)",
                TENANT, roleId, authority, dataScope);
    }

    private void allFields(JdbcTemplate jdbc, long roleId, String objectType) {
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, ?)",
                TENANT, roleId, objectType);
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, access_level, mask_type) "
                        + "SELECT ?, ?, ?, id, 'READ', 'NONE' "
                        + "FROM custom_field_definition WHERE tenant_id = ? "
                        + "AND object_type = ? AND is_builtin = 1",
                TENANT, roleId, objectType, TENANT, objectType);
    }

    private void maskField(JdbcTemplate jdbc, long roleId, String objectType, String fieldCode) {
        jdbc.update("UPDATE role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "SET access_rule.mask_type = 'FULL' "
                        + "WHERE access_rule.role_id = ? AND access_rule.object_type = ? "
                        + "AND field_definition.field_code = ?",
                roleId, objectType, fieldCode);
    }

    private void unmaskField(JdbcTemplate jdbc, long roleId, String objectType, String fieldCode) {
        jdbc.update("UPDATE role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "SET access_rule.mask_type = 'NONE' "
                        + "WHERE access_rule.role_id = ? AND access_rule.object_type = ? "
                        + "AND field_definition.field_code = ?",
                roleId, objectType, fieldCode);
    }

    private void hideField(JdbcTemplate jdbc, long roleId, String objectType, String fieldCode) {
        jdbc.update("DELETE access_rule FROM role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "WHERE access_rule.role_id = ? AND access_rule.object_type = ? "
                        + "AND field_definition.field_code = ?",
                roleId, objectType, fieldCode);
    }

    private MockHttpServletRequestBuilder as(long actorId, MockHttpServletRequestBuilder request) {
        return as(actorId, TENANT, request);
    }

    private MockHttpServletRequestBuilder as(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) {
        return request.header("X-Dev-User-Id", actorId).header("X-Dev-Tenant-Id", tenantId);
    }

    private void clearB25aData() {
        clearTenantData(TENANT);
        clearTenantData(OTHER_TENANT);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id BETWEEN "
                + OWNER + " AND " + CUSTOMER_ONLY_READER);
    }

    private void clearTenantData(long tenantId) {
        String tenant = Long.toString(tenantId);
        executeSql("DELETE FROM crm_return_visit WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_receivable WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_receivable_plan WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_invoice WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_contract_collaborator WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_contract WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_opportunity_collaborator WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_opportunity WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_contact_collaborator WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_contact WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM crm_customer WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM role_field_access WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM role_field_policy WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM field_layout_preference WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM custom_field_definition WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM customization_object_guard WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM navigation_preference WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM organization_membership_request WHERE target_tenant_id = " + tenant);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM organization_department WHERE tenant_id = " + tenant);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + tenant);
    }

    private static final class SnapshotBarrier {
        private final CountDownLatch rootRead = new CountDownLatch(1);
        private final CountDownLatch sourceRelease = new CountDownLatch(1);

        private void rootRead() throws InterruptedException {
            rootRead.countDown();
            if (!sourceRelease.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("overview did not receive the source release");
            }
        }

        private void awaitRootRead() throws InterruptedException {
            if (!rootRead.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("overview did not establish its customer snapshot");
            }
        }

        private void releaseSources() {
            sourceRelease.countDown();
        }
    }

    private record SourceField(String source, String objectType, String fieldCode) { }

    private record SourceRoots(long contactId, long opportunityId, long contractId) { }
}
