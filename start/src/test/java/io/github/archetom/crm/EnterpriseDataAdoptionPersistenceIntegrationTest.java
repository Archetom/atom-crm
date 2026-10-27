package io.github.archetom.crm;

import io.github.archetom.crm.application.port.out.enterprisedata.CustomerRegisteredIdentityStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionProvenanceStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionRetentionWorkStore;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.exception.RegisteredIdentityAlreadyClaimedException;
import io.github.archetom.crm.domain.model.EnterpriseDataAdoptableFact;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MySQL-only proof for B23-XI/XP current identity arbitration and value-free retention. */
@RequiresDatabase
class EnterpriseDataAdoptionPersistenceIntegrationTest extends BaseIntegrationTest {
    private static final TenantId TENANT_A = new TenantId(923001L);
    private static final TenantId TENANT_B = new TenantId(923002L);
    private static final MemberId MEMBER_A = new MemberId(923011L);
    private static final MemberId MEMBER_B = new MemberId(923021L);
    private static final Instant OBSERVED = Instant.parse("2026-07-23T12:00:00Z");

    @Autowired private CustomerRegisteredIdentityStore identities;
    @Autowired private EnterpriseDataAdoptionProvenanceStore provenance;
    @Autowired private EnterpriseDataAdoptionRetentionWorkStore retentionWork;
    @Autowired private CustomerRepository customers;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void mysqlArbitratesCurrentIdentitiesAcrossConcurrentWritersAndReleasesOnlyOnSoftDelete()
            throws Exception {
        seedTenant(TENANT_A, MEMBER_A, "B23 Adoption A");
        seedTenant(TENANT_B, MEMBER_B, "B23 Adoption B");
        Customer first = createCustomer(TENANT_A, MEMBER_A, "Synthetic Customer One");
        Customer second = createCustomer(TENANT_A, MEMBER_A, "Synthetic Customer Two");
        Customer foreign = createCustomer(TENANT_B, MEMBER_B, "Synthetic Customer Foreign");
        RegisteredIdentifierKey adopted = key("0000SYNTHETICADOPT04");

        CustomerRegisteredIdentityStore.RegisteredIdentityAssociation firstAssociation = inTransaction(() ->
                identities.reserve(TENANT_A, first.getId(), List.of(adopted)).getFirst());
        CustomerRegisteredIdentityStore.RegisteredIdentityAssociation idempotent = inTransaction(() ->
                identities.reserve(TENANT_A, first.getId(), List.of(adopted)).getFirst());
        assertEquals(firstAssociation.associationId(), idempotent.associationId());
        assertThrows(RegisteredIdentityAlreadyClaimedException.class, () -> inTransaction(() ->
                identities.reserve(TENANT_A, second.getId(), List.of(adopted))));
        assertTrue(inTransaction(() -> identities.reserve(TENANT_B, foreign.getId(), List.of(adopted))).size() == 1);

        RegisteredIdentifierKey rolledBack = key("0000SYNTHETICROLLB03");
        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            identities.reserve(TENANT_A, first.getId(), List.of(rolledBack));
            throw new IllegalStateException("rollback-canary");
        }));
        assertTrue(inTransaction(() -> identities.reserve(TENANT_A, second.getId(), List.of(rolledBack))).size() == 1);

        RegisteredIdentifierKey concurrent = key("0000SYNTHETICCONC071");
        assertEquals(1L, concurrentReservations(first.getId(), second.getId(), concurrent));

        inTransaction(() -> {
            Customer deleting = customers.findById(TENANT_A, first.getId()).orElseThrow();
            deleting.delete();
            customers.save(TENANT_A, deleting, List.of());
            return null;
        });
        CustomerRegisteredIdentityStore.RegisteredIdentityAssociation releasedThenReserved = inTransaction(() ->
                identities.reserve(TENANT_A, second.getId(), List.of(adopted)).getFirst());
        assertTrue(releasedThenReserved.associationId() > firstAssociation.associationId());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer_registered_identity "
                        + "WHERE tenant_id = ? AND canonical_value = ? AND released_time IS NOT NULL",
                Integer.class, TENANT_A.getValue(), adopted.getCanonicalValue()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer_registered_identity "
                        + "WHERE tenant_id = ? AND canonical_value = ? AND released_time IS NULL",
                Integer.class, TENANT_A.getValue(), adopted.getCanonicalValue()));
    }

    @Test
    void provenanceIsValueFreeTenantScopedAndPhysicallyPurgedAtItsDeadline() {
        seedTenant(TENANT_A, MEMBER_A, "B23 Retention A");
        Customer customer = createCustomer(TENANT_A, MEMBER_A, "Synthetic Retention Customer");
        CustomerRegisteredIdentityStore.RegisteredIdentityAssociation identity = inTransaction(() ->
                identities.reserve(TENANT_A, customer.getId(), List.of(key("0000SYNTHETICRETN033"))).getFirst());
        Instant expires = OBSERVED.plusSeconds(120);
        Instant deleteAfter = OBSERVED.plusSeconds(3600);

        inTransaction(() -> {
            provenance.append(new EnterpriseDataAdoptionProvenanceStore.Adoption(UUID.randomUUID(), TENANT_A,
                    customer.getId(), MEMBER_A, identity.associationId(), EnterpriseDataSourceKind.SYNTHETIC_FIXTURE,
                    "synthetic-source-v1", "synthetic-evidence-v1", "policy-v1", "attribution-v1",
                    "mapping-v1", OBSERVED, expires, 0L, 1L, deleteAfter,
                    List.of(new EnterpriseDataAdoptionProvenanceStore.Selection(
                            EnterpriseDataAdoptableFact.REGISTERED_IDENTITY,
                            "identity:" + identity.associationId(), false))));
            return null;
        });
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_enterprise_data_adoption "
                        + "WHERE tenant_id = ?", Integer.class, TENANT_A.getValue()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_enterprise_data_adoption_retention_work "
                        + "WHERE tenant_id = ?", Integer.class, TENANT_A.getValue()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'crm_enterprise_data_adoption' "
                        + "AND column_name IN ('raw_response', 'registered_name', 'address_line', 'canonical_value')",
                Integer.class));

        EnterpriseDataAdoptionRetentionWorkStore.Lease lease = retentionWork.claimDue(
                deleteAfter.plusSeconds(1), Duration.ofMinutes(1), UUID.randomUUID()).orElseThrow();
        assertEquals(TENANT_A, lease.tenantId());
        assertEquals(0, provenance.purgeExpired(TENANT_B, deleteAfter.plusSeconds(1), 10));
        assertEquals(1, provenance.purgeExpired(TENANT_A, deleteAfter.plusSeconds(1), 10));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_enterprise_data_adoption "
                        + "WHERE tenant_id = ?", Integer.class, TENANT_A.getValue()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_enterprise_data_adoption_selection",
                Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_enterprise_data_adoption_retention_work "
                        + "WHERE tenant_id = ?", Integer.class, TENANT_A.getValue()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer_registered_identity "
                        + "WHERE tenant_id = ? AND id = ?", Integer.class,
                TENANT_A.getValue(), identity.associationId()));
    }

    private long concurrentReservations(CustomerId first, CustomerId second, RegisteredIdentifierKey identity)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> attempts = List.of(first, second).stream().map(customerId ->
                    (Callable<Boolean>) () -> {
                        ready.countDown();
                        if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new AssertionError("concurrent identity start timed out");
                        }
                        try {
                            inTransaction(() -> identities.reserve(TENANT_A, customerId, List.of(identity)));
                            return true;
                        } catch (RegisteredIdentityAlreadyClaimedException expected) {
                            return false;
                        }
                    }).toList();
            List<Future<Boolean>> results = attempts.stream().map(executor::submit).toList();
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            long successes = 0;
            for (Future<Boolean> result : results) if (result.get()) successes++;
            return successes;
        } finally {
            start.countDown();
        }
    }

    private Customer createCustomer(TenantId tenantId, MemberId owner, String name) {
        return inTransaction(() -> customers.save(tenantId,
                Customer.create(tenantId, owner, name, null, null, null, null, null, List.of(), List.of()),
                List.of()));
    }

    private void seedTenant(TenantId tenantId, MemberId memberId, String name) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                memberId.getValue(), name);
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                tenantId.getValue(), memberId.getValue());
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                tenantId.getValue(), memberId.getValue());
    }

    private RegisteredIdentifierKey key(String value) {
        return new RegisteredIdentifierKey(IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI, value);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    @Override protected void initTestData() { clearB23Data(); }
    @Override protected void clearTestData() { clearB23Data(); }

    private void clearB23Data() {
        executeSql("DELETE FROM crm_enterprise_data_adoption_selection");
        executeSql("DELETE FROM crm_enterprise_data_adoption_retention_work");
        executeSql("DELETE FROM crm_enterprise_data_adoption");
        executeSql("DELETE FROM crm_customer_registered_identity");
        executeSql("DELETE FROM crm_customer_address WHERE tenant_id IN (923001, 923002)");
        executeSql("DELETE FROM crm_customer_collaborator WHERE tenant_id IN (923001, 923002)");
        executeSql("DELETE FROM crm_customer_tag WHERE tenant_id IN (923001, 923002)");
        executeSql("DELETE FROM crm_customer_field_value WHERE tenant_id IN (923001, 923002)");
        executeSql("DELETE FROM crm_customer_unique_field_value WHERE tenant_id IN (923001, 923002)");
        executeSql("DELETE FROM crm_customer WHERE tenant_id IN (923001, 923002)");
        executeSql("DELETE FROM organization_membership WHERE tenant_id IN (923001, 923002)");
        executeSql("DELETE FROM organization_member_profile WHERE actor_id IN (923011, 923021)");
        executeSql("DELETE FROM organization_tenant WHERE tenant_id IN (923001, 923002)");
    }
}
