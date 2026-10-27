package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.List;
import java.util.Map;

/**
 * Reuses the B09 lead-creation operation inside a composite command transaction.
 *
 * <p>The caller owns the active transaction and the public error-code scene. This avoids
 * opening a nested {@code REQUIRES_NEW} transaction while a coordinating aggregate lock is
 * held.</p>
 */
public interface LeadCreationCoordinator {

    /** Ordinary in-transaction creation has no pre-validated non-scalar field facts. */
    default LeadVO createInCurrentTransaction(
            AuthenticatedCaller caller, LeadCreateRequest request) {
        return createInCurrentTransaction(caller, request, Map.of());
    }

    /**
     * Creates a Lead while treating these already locked, READY FILE facts as satisfying the
     * corresponding required custom FILE definitions. Their binding remains the coordinator's
     * caller responsibility in the same surrounding transaction.
     */
    LeadVO createInCurrentTransaction(
            AuthenticatedCaller caller, LeadCreateRequest request,
            Map<FieldCode, List<FileFieldValue>> preparedFileFields);
}
