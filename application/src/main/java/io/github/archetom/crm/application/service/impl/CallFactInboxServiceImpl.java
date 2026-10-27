package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.call.CallFactInboxStore;
import io.github.archetom.crm.application.security.ConnectorContext;
import io.github.archetom.crm.application.security.ConnectorGuard;
import io.github.archetom.crm.application.service.CallFactInboxService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.CallFactInboxReceiptVO;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** B22 core inbox boundary: connector resolution/lock precedes the first durable fact write. */
@Service
@RequiredArgsConstructor
public class CallFactInboxServiceImpl implements CallFactInboxService {
    private static final int DIGEST_BYTES = 32;
    private static final int DELIVERY_BYTES = 32;
    private final ConnectorGuard connectorGuard;
    private final CallFactInboxStore store;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<CallFactInboxReceiptVO> accept(
            AuthenticatedConnector connector, byte[] deliveryRef, byte[] factDigest) {
        return commandTemplate.execute(UseCaseOperation.CALL_FACT_INBOX_ACCEPT, new ServiceOperation<>() {
            private ConnectorContext context;
            private byte[] delivery;
            private byte[] digest;

            @Override public void validate() {
                context = connectorGuard.requireActive(connector);
                delivery = exact(deliveryRef, DELIVERY_BYTES);
                digest = exact(factDigest, DIGEST_BYTES);
            }

            @Override public CallFactInboxReceiptVO execute() {
                connectorGuard.lockActive(context);
                CallFactInboxStore.Outcome outcome = store.accept(context.tenantId(), context.connectorId(),
                        delivery, digest, Instant.now());
                if (outcome == CallFactInboxStore.Outcome.CONFLICT) throw conflict();
                return new CallFactInboxReceiptVO(outcome.name());
            }
        });
    }

    private static byte[] exact(byte[] value, int length) {
        if (value == null || value.length != length) throw invalid();
        return value.clone();
    }
    private static NonRetryableApplicationException invalid() {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, "Call fact is invalid");
    }
    private static NonRetryableApplicationException conflict() {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, "Call fact conflicts");
    }
}
