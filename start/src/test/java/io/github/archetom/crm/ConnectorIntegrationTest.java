package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ConnectorCreateRequest;
import io.github.archetom.crm.api.dto.request.ConnectorQueryRequest;
import io.github.archetom.crm.api.dto.request.ConnectorStatusRequest;
import io.github.archetom.crm.api.dto.request.ConnectorVersionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.ConnectorCredentialCreatedResponse;
import io.github.archetom.crm.api.dto.response.ConnectorResponse;
import io.github.archetom.crm.application.port.out.call.CallFactInboxStore;
import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.repository.ConnectorRegistrationRepository;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B22a connector security, control-plane, exact identity, and registration-lock coverage. */
@RequiresDatabase
class ConnectorIntegrationTest extends BaseIntegrationTest {
    private static final long OWNER = 888888L;
    private static final long TENANT = 999999L;
    private static final long OTHER_OWNER = 777777L;
    private static final long OTHER_TENANT = 777001L;

    @Autowired
    private ConnectorRegistrationRepository connectorRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CallFactInboxStore callFactInbox;

    @Test
    void controlPlaneKeepsSecretsOneTimeTenantBoundAndLifecycleFailClosed() throws Exception {
        bootstrap(OWNER, TENANT, "Connector owner");
        bootstrap(OTHER_OWNER, OTHER_TENANT, "Other connector owner");

        ConnectorCredentialCreatedResponse createdResult = create(OWNER, TENANT);
        ConnectorResponse created = createdResult.getConnector();
        assertTrue(ConnectorRef.isCanonical(created.getConnectorRef()));
        assertEquals("ACTIVE", created.getStatus());
        assertTrue(createdResult.getCredential().startsWith(created.getConnectorRef() + "."));
        assertEquals(1, created.getCredentials().size());
        String firstCredential = createdResult.getCredential();
        String firstKeyId = created.getCredentials().getFirst().getKeyId();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String verifier = jdbc.queryForObject(
                "SELECT secret_verifier FROM crm_connector_credential "
                        + "WHERE tenant_id = ? AND connector_id = ? AND key_id = ?",
                String.class, TENANT, created.getId(), firstKeyId);
        assertEquals(64, verifier.length());
        assertFalse(firstCredential.contains(verifier));
        assertNotEquals(firstCredential, verifier);

        probe(firstCredential).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/connector-ingress/probe")
                        .header("Authorization", "Bearer " + firstCredential)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + OTHER_TENANT + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/connector-ingress/probe")
                        .header("X-Dev-User-Id", OWNER)
                        .header("X-Dev-Tenant-Id", TENANT))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/connectors/{id}", created.getId())
                        .header("Authorization", "Bearer " + firstCredential))
                .andExpect(status().isUnauthorized());

        ConnectorResponse detail = response(performAs(OWNER, TENANT,
                get("/api/v1/connectors/{id}", created.getId()))
                .andExpect(status().isOk()));
        assertEquals(created.getId(), detail.getId());
        performAs(OTHER_OWNER, OTHER_TENANT,
                get("/api/v1/connectors/{id}", created.getId()))
                .andExpect(status().isNotFound());
        performAs(OWNER, TENANT, post("/api/v1/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + OTHER_TENANT + "}"))
                .andExpect(status().isBadRequest());

        ConnectorCredentialCreatedResponse rotatedResult = credentialResponse(performAs(OWNER, TENANT,
                post("/api/v1/connectors/{id}/credentials", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ConnectorVersionRequest()
                                .setVersion(created.getVersion()))))
                .andExpect(status().isOk()));
        ConnectorResponse rotated = rotatedResult.getConnector();
        String secondCredential = rotatedResult.getCredential();
        assertEquals(2, rotated.getCredentials().stream()
                .filter(value -> "ACTIVE".equals(value.getStatus())).count());
        probe(firstCredential).andExpect(status().isOk());
        probe(secondCredential).andExpect(status().isOk());
        performAs(OWNER, TENANT,
                post("/api/v1/connectors/{id}/credentials", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ConnectorVersionRequest()
                                .setVersion(rotated.getVersion()))))
                .andExpect(status().isUnprocessableEntity());

        ConnectorResponse revoked = response(performAs(OWNER, TENANT,
                delete("/api/v1/connectors/{id}/credentials/{keyId}",
                        created.getId(), firstKeyId)
                        .param("version", rotated.getVersion().toString()))
                .andExpect(status().isOk()));
        probe(firstCredential).andExpect(status().isUnauthorized());
        probe(secondCredential).andExpect(status().isOk());

        ConnectorResponse disabled = updateStatus(
                created.getId(), "DISABLED", revoked.getVersion());
        probe(secondCredential).andExpect(status().isUnauthorized());
        ConnectorResponse enabled = updateStatus(
                created.getId(), "ACTIVE", disabled.getVersion());
        probe(secondCredential).andExpect(status().isOk());

        ConnectorResponse retired = updateStatus(
                created.getId(), "RETIRED", enabled.getVersion());
        assertEquals("RETIRED", retired.getStatus());
        assertTrue(retired.getCredentials().stream()
                .allMatch(value -> "REVOKED".equals(value.getStatus())));
        probe(secondCredential).andExpect(status().isUnauthorized());
        performAs(OWNER, TENANT, put("/api/v1/connectors/{id}/status", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ConnectorStatusRequest().setStatus("ACTIVE")
                                .setVersion(retired.getVersion()))))
                .andExpect(status().isUnprocessableEntity());

        performAs(OWNER, TENANT, post("/api/v1/connectors/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ConnectorQueryRequest().setStatus("RETIRED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].credential").doesNotExist());

        String attemptedRef = ConnectorCredential.create().connectorRef().getValue();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE crm_connector_registration SET tenant_id = ? WHERE id = ?",
                OTHER_TENANT, created.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE crm_connector_registration SET connector_ref = ? WHERE id = ?",
                attemptedRef, created.getId()));
        var identity = jdbc.queryForMap(
                "SELECT tenant_id, connector_ref FROM crm_connector_registration WHERE id = ?",
                created.getId());
        assertEquals(TENANT, ((Number) identity.get("tenant_id")).longValue());
        assertEquals(created.getConnectorRef(), identity.get("connector_ref"));
    }

    @Test
    void ingressRegistrationLockAndDisableFormOneDatabaseOrder() throws Exception {
        bootstrap(OWNER, TENANT, "Connector lock owner");
        ConnectorResponse created = createSummary(OWNER, TENANT);
        TenantId tenantId = new TenantId(TENANT);
        ConnectorId connectorId = new ConnectorId(created.getId());
        ConnectorRef connectorRef = new ConnectorRef(created.getConnectorRef());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ingress = executor.submit(() -> new TransactionTemplate(transactionManager).execute(
                    ignored -> {
                        assertTrue(connectorRepository.lockActive(
                                tenantId, connectorId, connectorRef));
                        locked.countDown();
                        await(release);
                        return true;
                    }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            var disable = executor.submit(() -> performAs(OWNER, TENANT,
                    put("/api/v1/connectors/{id}/status", created.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new ConnectorStatusRequest().setStatus("DISABLED")
                                    .setVersion(created.getVersion()))))
                    .andReturn().getResponse().getStatus());

            assertThrows(TimeoutException.class, () -> disable.get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(Boolean.TRUE.equals(ingress.get(5, TimeUnit.SECONDS)));
            assertEquals(200, disable.get(5, TimeUnit.SECONDS));

            Boolean active = new TransactionTemplate(transactionManager).execute(ignored ->
                    connectorRepository.lockActive(tenantId, connectorId, connectorRef));
            assertFalse(Boolean.TRUE.equals(active));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void disableAndRetirementCommitBeforeWaitingIngressCanLock() throws Exception {
        bootstrap(OWNER, TENANT, "Connector transition owner");
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (ConnectorStatus target : List.of(
                    ConnectorStatus.DISABLED, ConnectorStatus.RETIRED)) {
                ConnectorResponse created = createSummary(OWNER, TENANT);
                TenantId tenantId = new TenantId(TENANT);
                ConnectorId connectorId = new ConnectorId(created.getId());
                ConnectorRef connectorRef = new ConnectorRef(created.getConnectorRef());
                CountDownLatch transitionWritten = new CountDownLatch(1);
                CountDownLatch releaseTransition = new CountDownLatch(1);

                var transition = executor.submit(() ->
                        new TransactionTemplate(transactionManager).execute(ignored -> {
                            var registration = connectorRepository.findByIdForUpdate(
                                            tenantId, connectorId)
                                    .orElseThrow();
                            registration.changeStatus(target, LocalDateTime.now());
                            connectorRepository.save(tenantId, registration);
                            transitionWritten.countDown();
                            await(releaseTransition);
                            return true;
                        }));
                assertTrue(transitionWritten.await(5, TimeUnit.SECONDS));

                var ingress = executor.submit(() ->
                        new TransactionTemplate(transactionManager).execute(ignored ->
                                connectorRepository.lockActive(
                                        tenantId, connectorId, connectorRef)));
                assertThrows(TimeoutException.class,
                        () -> ingress.get(300, TimeUnit.MILLISECONDS));

                releaseTransition.countDown();
                assertTrue(Boolean.TRUE.equals(transition.get(5, TimeUnit.SECONDS)));
                assertFalse(Boolean.TRUE.equals(ingress.get(5, TimeUnit.SECONDS)));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void opaqueFactInboxUsesMySqlUniquenessForDuplicateConflictAndTenantIsolation() throws Exception {
        bootstrap(OWNER, TENANT, "Call fact owner");
        bootstrap(OTHER_OWNER, OTHER_TENANT, "Other call fact owner");
        ConnectorResponse first = createSummary(OWNER, TENANT);
        ConnectorResponse second = createSummary(OTHER_OWNER, OTHER_TENANT);
        byte[] delivery = bytes(1);
        byte[] fact = bytes(2);
        TenantId firstTenant = new TenantId(TENANT);
        ConnectorId firstConnector = new ConnectorId(first.getId());

        assertEquals(CallFactInboxStore.Outcome.APPLIED,
                accept(firstTenant, firstConnector, delivery, fact));
        assertEquals(CallFactInboxStore.Outcome.DUPLICATE,
                accept(firstTenant, firstConnector, delivery, fact));
        assertEquals(CallFactInboxStore.Outcome.CONFLICT,
                accept(firstTenant, firstConnector, delivery, bytes(3)));
        assertEquals(CallFactInboxStore.Outcome.APPLIED,
                accept(new TenantId(OTHER_TENANT), new ConnectorId(second.getId()), delivery, fact));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_call_fact_inbox "
                + "WHERE tenant_id = ? AND connector_id = ?", Integer.class, TENANT, first.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_call_fact_inbox "
                + "WHERE tenant_id = ? AND connector_id = ?", Integer.class, OTHER_TENANT, second.getId()));
        byte[] persisted = jdbc.queryForObject("SELECT fact_digest FROM crm_call_fact_inbox "
                + "WHERE tenant_id = ? AND connector_id = ?", byte[].class, TENANT, first.getId());
        assertTrue(Arrays.equals(fact, persisted));
    }

    @Test
    void simultaneousDuplicateDeliveriesSettleAsOneApplyAndOneDuplicate() throws Exception {
        bootstrap(OWNER, TENANT, "Concurrent call fact owner");
        ConnectorResponse connector = createSummary(OWNER, TENANT);
        TenantId tenantId = new TenantId(TENANT);
        ConnectorId connectorId = new ConnectorId(connector.getId());
        byte[] delivery = bytes(11);
        byte[] fact = bytes(12);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> concurrentAccept(tenantId, connectorId, delivery, fact, ready, start));
            var right = executor.submit(() -> concurrentAccept(tenantId, connectorId, delivery, fact, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<CallFactInboxStore.Outcome> outcomes = List.of(
                    left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS));
            assertTrue(outcomes.contains(CallFactInboxStore.Outcome.APPLIED));
            assertTrue(outcomes.contains(CallFactInboxStore.Outcome.DUPLICATE));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private ConnectorResponse updateStatus(long connectorId, String target, long version)
            throws Exception {
        return response(performAs(OWNER, TENANT,
                put("/api/v1/connectors/{id}/status", connectorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ConnectorStatusRequest().setStatus(target)
                                .setVersion(version))))
                .andExpect(status().isOk()));
    }

    private ResultActions probe(String credential) throws Exception {
        return mockMvc.perform(post("/api/v1/connector-ingress/probe")
                .header("Authorization", "Bearer " + credential));
    }

    private CallFactInboxStore.Outcome accept(
            TenantId tenantId, ConnectorId connectorId, byte[] delivery, byte[] fact) {
        return new TransactionTemplate(transactionManager).execute(ignored -> callFactInbox.accept(
                tenantId, connectorId, delivery, fact, Instant.parse("2026-07-23T12:00:00Z")));
    }

    private CallFactInboxStore.Outcome concurrentAccept(
            TenantId tenantId, ConnectorId connectorId, byte[] delivery, byte[] fact,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        return accept(tenantId, connectorId, delivery, fact);
    }

    private static byte[] bytes(int firstByte) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) firstByte;
        return bytes;
    }

    private ConnectorResponse response(ResultActions action) throws Exception {
        return fromJson(action.andReturn().getResponse().getContentAsString(),
                ConnectorResponse.class);
    }

    private ConnectorCredentialCreatedResponse credentialResponse(ResultActions action) throws Exception {
        return fromJson(action.andReturn().getResponse().getContentAsString(),
                ConnectorCredentialCreatedResponse.class);
    }

    private ConnectorCredentialCreatedResponse create(long actorId, long tenantId) throws Exception {
        return credentialResponse(performAs(actorId, tenantId,
                post("/api/v1/connectors").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ConnectorCreateRequest())))
                .andExpect(status().isOk()));
    }

    private ConnectorResponse createSummary(long actorId, long tenantId) throws Exception {
        return create(actorId, tenantId).getConnector();
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for registration lock release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Registration lock wait was interrupted", exception);
        }
    }

    @Override
    protected void initTestData() {
        clearConnectorData();
    }

    @Override
    protected void clearTestData() {
        clearConnectorData();
    }

    private void clearConnectorData() {
        executeSql("DELETE FROM crm_call_start_outbox");
        executeSql("DELETE FROM crm_call_fact_inbox");
        executeSql("DELETE FROM crm_call_request_binding");
        executeSql("DELETE FROM crm_call_observation_gap");
        executeSql("DELETE FROM crm_call_interaction");
        executeSql("DELETE FROM crm_connector_credential");
        executeSql("DELETE FROM crm_connector_registration");
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
