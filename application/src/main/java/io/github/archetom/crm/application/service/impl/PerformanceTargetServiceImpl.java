package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.PerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportItemRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.PerformanceTargetService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.PerformanceTargetImportVO;
import io.github.archetom.crm.application.vo.PerformanceTargetVO;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.PerformanceTarget;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.PerformanceTargetRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maintains targets as separate, versioned monthly aggregates. A target-management authority
 * never changes the data scope used to calculate dashboard business amounts.
 */
@Service
@RequiredArgsConstructor
public class PerformanceTargetServiceImpl implements PerformanceTargetService {

    private final PerformanceTargetRepository repository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationAccessGuard accessGuard;
    private final QueryServiceTemplate queryTemplate;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<List<PerformanceTargetVO>> query(
            AuthenticatedCaller caller, PerformanceTargetQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.DASHBOARD_PERFORMANCE_TARGET_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private PerformanceTargetMetric metric;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_READ);
                        require(request, "performance target query request");
                        memberId = memberId(request.getMemberId());
                        requireActiveMember(tenantId, memberId);
                        metric = metric(request.getMetric(), false);
                        requireYear(request.getTargetYear());
                    }

                    @Override
                    public List<PerformanceTargetVO> execute() {
                        return repository.findByMemberAndYear(tenantId, memberId, metric,
                                        request.getTargetYear())
                                .stream().map(PerformanceTargetServiceImpl::toVO).toList();
                    }
                });
    }

    @Override
    public Result<PerformanceTargetVO> create(
            AuthenticatedCaller caller, PerformanceTargetCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.DASHBOARD_PERFORMANCE_TARGET_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private PerformanceTargetMetric metric;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        require(request, "performance target create request");
                        memberId = memberId(request.getMemberId());
                        requireActiveMember(tenantId, memberId);
                        metric = metric(request.getMetric(), true);
                        requireYear(request.getTargetYear());
                        requireMonth(request.getTargetMonth());
                    }

                    @Override
                    public PerformanceTargetVO execute() {
                        if (repository.findByNaturalKey(tenantId, memberId, metric,
                                request.getTargetYear(), request.getTargetMonth()).isPresent()) {
                            throw alreadyExists();
                        }
                        return toVO(repository.save(tenantId, PerformanceTarget.create(tenantId, memberId,
                                metric, request.getTargetYear(), request.getTargetMonth(),
                                request.getAmount())));
                    }
                });
    }

    /**
     * Imports locally decoded CSV rows as one explicit server-owned command. Unlike ordinary
     * browser editing, the import has no client version baseline; it serializes every natural
     * key under the write authority and either applies the complete source or rolls it back.
     */
    @Override
    public Result<PerformanceTargetImportVO> importTargets(
            AuthenticatedCaller caller, PerformanceTargetImportRequest request) {
        return commandTemplate.execute(UseCaseOperation.DASHBOARD_PERFORMANCE_TARGET_IMPORT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private PerformanceTargetMetric metric;
                    private List<ImportTargetCommand> targets;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        require(request, "performance target import request");
                        metric = metric(request.getMetric(), true);
                        targets = importTargets(request.getTargets());
                    }

                    @Override
                    public PerformanceTargetImportVO execute() {
                        for (ImportTargetCommand command : targets) {
                            requireActiveMember(tenantId, command.memberId());
                            PerformanceTarget existing = repository.findByNaturalKeyForUpdate(tenantId,
                                            command.memberId(), metric, command.targetYear(), command.targetMonth())
                                    .orElse(null);
                            if (existing == null) {
                                repository.save(tenantId, PerformanceTarget.create(tenantId, command.memberId(),
                                        metric, command.targetYear(), command.targetMonth(), command.amount()));
                            } else if (existing.getAmount().compareTo(command.amount()) != 0) {
                                existing.changeAmount(command.amount());
                                repository.save(tenantId, existing);
                            }
                        }
                        return new PerformanceTargetImportVO(targets.size());
                    }
                });
    }

    @Override
    public Result<PerformanceTargetVO> update(
            AuthenticatedCaller caller, Long targetId, PerformanceTargetUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.DASHBOARD_PERFORMANCE_TARGET_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private PerformanceTargetId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        id = targetId(targetId);
                        require(request, "performance target update request");
                    }

                    @Override
                    public PerformanceTargetVO execute() {
                        PerformanceTarget target = requireTargetForUpdate(tenantId, id);
                        requireActiveMember(tenantId, target.getMemberId());
                        requireVersion(target, request.getVersion());
                        target.changeAmount(request.getAmount());
                        return toVO(repository.save(tenantId, target));
                    }
                });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version) {
        return commandTemplate.execute(UseCaseOperation.DASHBOARD_PERFORMANCE_TARGET_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private PerformanceTargetId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        id = targetId(targetId);
                    }

                    @Override
                    public Void execute() {
                        PerformanceTarget target = requireTargetForUpdate(tenantId, id);
                        requireActiveMember(tenantId, target.getMemberId());
                        requireVersion(target, version);
                        repository.delete(tenantId, target);
                        return null;
                    }
                });
    }

    private PerformanceTarget requireTargetForUpdate(TenantId tenantId, PerformanceTargetId id) {
        return repository.findByIdForUpdate(tenantId, id)
                .orElseThrow(() -> notFound("performance target is unavailable"));
    }

    private void requireActiveMember(TenantId tenantId, MemberId memberId) {
        OrganizationMembership membership = membershipRepository.findByMember(tenantId, memberId)
                .orElseThrow(() -> notFound("target member is unavailable"));
        if (!membership.isActive()) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.OPERATION_NOT_ALLOWED, "target member is inactive");
        }
    }

    private static PerformanceTargetMetric metric(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (!required) {
                return PerformanceTargetMetric.ACTIVE_CONTRACT;
            }
            throw invalid("performance target metric is required");
        }
        try {
            return PerformanceTargetMetric.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("performance target metric is invalid");
        }
    }

    private static MemberId memberId(Long value) {
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw invalid("performance target member ID is invalid");
        }
    }

    private static PerformanceTargetId targetId(Long value) {
        try {
            return new PerformanceTargetId(value);
        } catch (RuntimeException exception) {
            throw invalid("performance target ID is invalid");
        }
    }

    private static void requireYear(Integer value) {
        if (value == null || value < 2000 || value > 9999) {
            throw invalid("performance target year is invalid");
        }
    }

    private static void requireMonth(Integer value) {
        if (value == null || value < 1 || value > 12) {
            throw invalid("performance target month is invalid");
        }
    }

    private static List<ImportTargetCommand> importTargets(
            List<PerformanceTargetImportItemRequest> values) {
        if (values == null || values.isEmpty() || values.size() > 200) {
            throw invalid("performance target import size is invalid");
        }
        List<ImportTargetCommand> result = new ArrayList<>(values.size());
        Set<String> naturalKeys = new HashSet<>();
        for (PerformanceTargetImportItemRequest value : values) {
            require(value, "performance target import item");
            MemberId memberId = memberId(value.getMemberId());
            requireYear(value.getTargetYear());
            requireMonth(value.getTargetMonth());
            if (value.getAmount() == null) {
                throw invalid("performance target import amount is required");
            }
            String naturalKey = memberId.getValue() + ":" + value.getTargetYear() + ":"
                    + value.getTargetMonth();
            if (!naturalKeys.add(naturalKey)) {
                throw invalid("performance target import rows must be unique");
            }
            result.add(new ImportTargetCommand(memberId, value.getTargetYear(), value.getTargetMonth(),
                    value.getAmount()));
        }
        result.sort(Comparator.comparing((ImportTargetCommand value) -> value.memberId().getValue())
                .thenComparing(ImportTargetCommand::targetYear)
                .thenComparing(ImportTargetCommand::targetMonth));
        return List.copyOf(result);
    }

    private static void requireVersion(PerformanceTarget target, Long version) {
        if (version == null || !version.equals(target.getVersion())) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.VERSION_CONFLICT, "performance target version is stale");
        }
    }

    private static PerformanceTargetVO toVO(PerformanceTarget target) {
        return new PerformanceTargetVO(target.getId().getValue(), target.getMemberId().getValue(),
                target.getMetric().name(), target.getTargetYear(), target.getTargetMonth(),
                target.getAmount(), target.getVersion(), target.getCreatedTime(), target.getUpdatedTime());
    }

    private static void require(Object value, String description) {
        if (value == null) {
            throw invalid(description + " is required");
        }
    }

    private static NonRetryableApplicationException alreadyExists() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.RESOURCE_ALREADY_EXISTS, "performance target already exists");
    }

    private static NonRetryableApplicationException notFound(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private static NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private record ImportTargetCommand(MemberId memberId, int targetYear, int targetMonth,
                                       BigDecimal amount) { }
}
