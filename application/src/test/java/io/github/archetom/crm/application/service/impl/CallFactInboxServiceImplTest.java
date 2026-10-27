package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.port.out.call.CallFactInboxStore;
import io.github.archetom.crm.application.security.ConnectorContext;
import io.github.archetom.crm.application.security.ConnectorGuard;
import io.github.archetom.crm.application.service.CallFactInboxService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.CallFactInboxReceiptVO;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class CallFactInboxServiceImplTest {
    private static final TenantId TENANT = new TenantId(9L);
    private static final ConnectorId CONNECTOR = new ConnectorId(41L);

    @Test
    void locksTheResolvedConnectorInsideTheWriteTransactionBeforeAcceptingTheOpaqueFact() {
        Fixture fixture = fixture();
        byte[] delivery = bytes(1);
        byte[] digest = bytes(2);
        when(fixture.inbox.accept(eq(TENANT), eq(CONNECTOR), eq(delivery), eq(digest), any(Instant.class)))
                .thenReturn(CallFactInboxStore.Outcome.APPLIED);

        Result<CallFactInboxReceiptVO> result = fixture.service.accept(fixture.connector, delivery, digest);

        assertTrue(result.isSuccess());
        assertEquals("APPLIED", result.getData().outcome());
        var order = inOrder(fixture.guard, fixture.inbox);
        order.verify(fixture.guard).requireActive(fixture.connector);
        order.verify(fixture.guard).lockActive(fixture.context);
        order.verify(fixture.inbox).accept(eq(TENANT), eq(CONNECTOR), eq(delivery), eq(digest), any(Instant.class));
    }

    @Test
    void rejectsMalformedOpaqueMaterialBeforeTheRegistrationLockOrInboxWrite() {
        Fixture fixture = fixture();

        Result<?> result = fixture.service.accept(fixture.connector, new byte[31], bytes(2));

        assertError(result, ApplicationErrorCode.PARAMETER_INVALID);
        verify(fixture.guard, never()).lockActive(any());
        verify(fixture.inbox, never()).accept(any(), any(), any(), any(), any());
    }

    @Test
    void treatsASameDeliveryWithDifferentFactAsASafeConflict() {
        Fixture fixture = fixture();
        when(fixture.inbox.accept(any(), any(), any(), any(), any()))
                .thenReturn(CallFactInboxStore.Outcome.CONFLICT);

        Result<?> result = fixture.service.accept(fixture.connector, bytes(1), bytes(2));

        assertError(result, ApplicationErrorCode.PARAMETER_INVALID);
    }

    private Fixture fixture() {
        AuthenticatedConnector connector = new AuthenticatedConnector("opaque-connector-ref", new Object());
        ConnectorGuard guard = mock(ConnectorGuard.class);
        ConnectorContext context = new ConnectorContext(TENANT, CONNECTOR,
                new io.github.archetom.crm.domain.valueobject.ConnectorRef("A".repeat(43)), 1L);
        when(guard.requireActive(connector)).thenReturn(context);
        when(guard.lockActive(context)).thenReturn(context);
        CallFactInboxStore inbox = mock(CallFactInboxStore.class);
        CallFactInboxService service = new CallFactInboxServiceImpl(
                guard, inbox,
                new CommandServiceTemplate("test-app", new TestTransactionManager()));
        return new Fixture(service, connector, guard, context, inbox);
    }

    private static byte[] bytes(int value) {
        byte[] result = new byte[32];
        result[0] = (byte) value;
        return result;
    }

    private static void assertError(Result<?> result, ApplicationErrorCode expected) {
        assertFalse(result.isSuccess());
        assertEquals(expected.getCode(), result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
    }

    private record Fixture(CallFactInboxService service, AuthenticatedConnector connector,
                           ConnectorGuard guard, ConnectorContext context, CallFactInboxStore inbox) {
    }
}
