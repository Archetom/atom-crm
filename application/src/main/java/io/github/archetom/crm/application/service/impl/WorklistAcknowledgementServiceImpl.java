package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;
import io.github.archetom.crm.api.dto.request.WorklistItemAcknowledgementRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.WorklistAcknowledgementService;
import io.github.archetom.crm.application.service.WorklistQueryService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.model.WorklistItemFingerprint;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.WorklistAcknowledgementRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorklistAcknowledgementServiceImpl implements WorklistAcknowledgementService {
    private static final int MAX_CATEGORY_ITEMS = 1_000;
    private static final int MAX_SCAN_PAGES = 100;

    private final OrganizationAccessGuard accessGuard;
    private final WorklistQueryService queryService;
    private final WorklistAcknowledgementRepository repository;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<Void> acknowledge(AuthenticatedCaller caller, WorklistAcknowledgementRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORKLIST_ACKNOWLEDGE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private LocalDate day;
            private String category;
            private Set<WorklistItemFingerprint> requested;
            private List<WorklistItemFingerprint> verified;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORKLIST_READ);
                memberId = new MemberId(caller.actorId());
                if (request == null || request.getDate() == null) {
                    throw invalid("Worklist acknowledgement is invalid");
                }
                day = request.getDate();
                category = normalizeKind(request.getKind());
                List<WorklistItemAcknowledgementRequest> items = request.getItems() == null
                        ? List.of() : request.getItems();
                if (items.stream().anyMatch(java.util.Objects::isNull)) {
                    throw invalid("Worklist acknowledgement item is invalid");
                }
                requested = new LinkedHashSet<>();
                for (WorklistItemAcknowledgementRequest item : items) {
                    WorklistItemFingerprint value;
                    try {
                        value = new WorklistItemFingerprint(item.getKind(), item.getSourceId(),
                                item.getSourceVersion(), item.getDueTime());
                    } catch (RuntimeException exception) {
                        throw invalid("Worklist acknowledgement item is invalid");
                    }
                    if (!day.equals(value.dueTime().toLocalDate())
                            || category != null && !category.equals(value.kind())
                            || !requested.add(value)) {
                        throw invalid("Worklist acknowledgement item is invalid");
                    }
                }
                if (category == null && requested.isEmpty()) {
                    throw invalid("Worklist acknowledgement requires items or a category");
                }
            }

            @Override
            public void prepare() {
                Set<WorklistItemFingerprint> pending = requested;
                if (category == null) {
                    Set<WorklistItemFingerprint> acknowledged = repository.findAcknowledged(
                            tenantId, memberId, requested);
                    pending = new LinkedHashSet<>(requested);
                    pending.removeAll(acknowledged);
                    if (pending.isEmpty()) {
                        verified = List.of();
                        return;
                    }
                }
                verified = verifyCurrent(caller, day, category, pending);
            }

            @Override
            public Void execute() {
                repository.saveAll(tenantId, memberId, verified);
                return null;
            }
        });
    }

    private List<WorklistItemFingerprint> verifyCurrent(
            AuthenticatedCaller caller, LocalDate day, String category,
            Set<WorklistItemFingerprint> requested) {
        LinkedHashSet<WorklistItemFingerprint> found = new LinkedHashSet<>();
        String cursor = null;
        for (int page = 0; page < MAX_SCAN_PAGES; page++) {
            Result<io.github.archetom.crm.application.vo.WorklistPageVO> result =
                    queryService.query(caller, day, cursor, 100);
            if (!result.isSuccess() || result.getData() == null) {
                throw invalid("Current worklist could not be verified");
            }
            for (var item : result.getData().items()) {
                WorklistItemFingerprint fingerprint = new WorklistItemFingerprint(item.kind(),
                        item.sourceId(), item.sourceVersion(), item.dueTime());
                if (category != null && category.equals(fingerprint.kind())) {
                    found.add(fingerprint);
                    if (found.size() > MAX_CATEGORY_ITEMS) {
                        throw invalid("Worklist category is too large to acknowledge at once");
                    }
                } else if (requested.contains(fingerprint)) {
                    found.add(fingerprint);
                }
            }
            if (category == null && found.containsAll(requested)) break;
            cursor = result.getData().nextCursor();
            if (cursor == null) break;
        }
        if (category != null && cursor != null) {
            throw invalid("Worklist category exceeds the bounded verification scan");
        }
        if (category == null && !found.containsAll(requested)) {
            throw invalid("Worklist acknowledgement item is no longer visible");
        }
        return List.copyOf(found);
    }

    private String normalizeKind(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,39}")) {
            throw invalid("Worklist category is invalid");
        }
        return normalized;
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }
}
