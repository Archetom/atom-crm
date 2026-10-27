package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetBatchItemRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetBatchUpsertRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetImportItemRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.DepartmentPerformanceTargetService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.DepartmentPerformanceTargetVO;
import io.github.archetom.crm.application.vo.PerformanceTargetImportVO;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.DepartmentPerformanceTarget;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.PerformanceTargetPeriod;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.DepartmentPerformanceTargetRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.DepartmentPerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maintains versioned annual, quarterly and monthly department target settings.
 * Business facts remain in contract/report services and are never calculated from client input.
 */
@Service
@RequiredArgsConstructor
public class DepartmentPerformanceTargetServiceImpl implements DepartmentPerformanceTargetService {

    private final DepartmentPerformanceTargetRepository repository;
    private final DepartmentRepository departmentRepository;
    private final OrganizationAccessGuard accessGuard;
    private final QueryServiceTemplate queryTemplate;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<List<DepartmentPerformanceTargetVO>> query(
            AuthenticatedCaller caller, DepartmentPerformanceTargetQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.DEPARTMENT_PERFORMANCE_TARGET_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private DepartmentId departmentId;
                    private PerformanceTargetMetric metric;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_READ);
                        require(request, "department performance target query request");
                        departmentId = departmentId(request.getDepartmentId());
                        requireActiveDepartment(tenantId, departmentId);
                        metric = metric(request.getMetric(), false);
                        requireYear(request.getTargetYear());
                    }

                    @Override
                    public List<DepartmentPerformanceTargetVO> execute() {
                        return repository.findByDepartmentAndYear(tenantId, departmentId, metric,
                                        request.getTargetYear())
                                .stream().map(DepartmentPerformanceTargetServiceImpl::toVO).toList();
                    }
                });
    }

    @Override
    public Result<DepartmentPerformanceTargetVO> create(
            AuthenticatedCaller caller, DepartmentPerformanceTargetCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_PERFORMANCE_TARGET_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private DepartmentId departmentId;
                    private PerformanceTargetMetric metric;
                    private PerformanceTargetPeriod period;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        require(request, "department performance target create request");
                        departmentId = departmentId(request.getDepartmentId());
                        requireActiveDepartment(tenantId, departmentId);
                        metric = metric(request.getMetric(), true);
                        requireYear(request.getTargetYear());
                        period = period(request.getPeriod());
                        requirePeriodNumber(period, request.getPeriodNumber());
                    }

                    @Override
                    public DepartmentPerformanceTargetVO execute() {
                        if (repository.findByNaturalKey(tenantId, departmentId, metric,
                                request.getTargetYear(), period, request.getPeriodNumber()).isPresent()) {
                            throw alreadyExists();
                        }
                        return toVO(repository.save(tenantId, DepartmentPerformanceTarget.create(
                                tenantId, departmentId, metric, request.getTargetYear(), period,
                                request.getPeriodNumber(), request.getAmount())));
                    }
                });
    }

    @Override
    public Result<DepartmentPerformanceTargetVO> update(
            AuthenticatedCaller caller, Long targetId, DepartmentPerformanceTargetUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_PERFORMANCE_TARGET_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private DepartmentPerformanceTargetId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        id = targetId(targetId);
                        require(request, "department performance target update request");
                    }

                    @Override
                    public DepartmentPerformanceTargetVO execute() {
                        DepartmentPerformanceTarget target = requireTargetForUpdate(tenantId, id);
                        requireActiveDepartment(tenantId, target.getDepartmentId());
                        requireVersion(target, request.getVersion());
                        target.changeAmount(request.getAmount());
                        return toVO(repository.save(tenantId, target));
                    }
                });
    }

    @Override
    public Result<List<DepartmentPerformanceTargetVO>> batchUpsert(
            AuthenticatedCaller caller, DepartmentPerformanceTargetBatchUpsertRequest request) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_PERFORMANCE_TARGET_BATCH_UPSERT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private DepartmentId departmentId;
                    private PerformanceTargetMetric metric;
                    private List<BatchTargetCommand> targets;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        require(request, "department performance target batch request");
                        departmentId = departmentId(request.getDepartmentId());
                        requireActiveDepartment(tenantId, departmentId);
                        metric = metric(request.getMetric(), true);
                        requireYear(request.getTargetYear());
                        targets = batchTargets(request.getTargets());
                    }

                    @Override
                    public List<DepartmentPerformanceTargetVO> execute() {
                        requireActiveDepartment(tenantId, departmentId);
                        List<DepartmentPerformanceTargetVO> result = new ArrayList<>(targets.size());
                        for (BatchTargetCommand command : targets) {
                            DepartmentPerformanceTarget existing = repository.findByNaturalKeyForUpdate(
                                            tenantId, departmentId, metric, request.getTargetYear(),
                                            command.period(), command.periodNumber())
                                    .orElse(null);
                            if (command.version() == null) {
                                if (existing != null) {
                                    throw alreadyExists();
                                }
                                result.add(toVO(repository.save(tenantId,
                                        DepartmentPerformanceTarget.create(tenantId, departmentId, metric,
                                                request.getTargetYear(), command.period(),
                                                command.periodNumber(), command.amount()))));
                                continue;
                            }
                            if (existing == null) {
                                throw versionConflict();
                            }
                            requireVersion(existing, command.version());
                            if (existing.getAmount().compareTo(command.amount()) != 0) {
                                existing.changeAmount(command.amount());
                                existing = repository.save(tenantId, existing);
                            }
                            result.add(toVO(existing));
                        }
                        return result;
                    }
                });
    }

    /**
     * Imports locally decoded CSV rows as one explicit server-owned command. The file source is
     * never retained by this service; all rows are tenant-checked and natural-key locked before
     * their full transaction can commit.
     */
    @Override
    public Result<PerformanceTargetImportVO> importTargets(
            AuthenticatedCaller caller, DepartmentPerformanceTargetImportRequest request) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_PERFORMANCE_TARGET_IMPORT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private PerformanceTargetMetric metric;
                    private List<ImportTargetCommand> targets;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        require(request, "department performance target import request");
                        metric = metric(request.getMetric(), true);
                        targets = importTargets(request.getTargets());
                    }

                    @Override
                    public PerformanceTargetImportVO execute() {
                        for (ImportTargetCommand command : targets) {
                            requireActiveDepartment(tenantId, command.departmentId());
                            DepartmentPerformanceTarget existing = repository.findByNaturalKeyForUpdate(tenantId,
                                            command.departmentId(), metric, command.targetYear(), command.period(),
                                            command.periodNumber())
                                    .orElse(null);
                            if (existing == null) {
                                repository.save(tenantId, DepartmentPerformanceTarget.create(tenantId,
                                        command.departmentId(), metric, command.targetYear(), command.period(),
                                        command.periodNumber(), command.amount()));
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
    public Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_PERFORMANCE_TARGET_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private DepartmentPerformanceTargetId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.DASHBOARD_TARGETS_WRITE);
                        id = targetId(targetId);
                    }

                    @Override
                    public Void execute() {
                        DepartmentPerformanceTarget target = requireTargetForUpdate(tenantId, id);
                        requireActiveDepartment(tenantId, target.getDepartmentId());
                        requireVersion(target, version);
                        repository.delete(tenantId, target);
                        return null;
                    }
                });
    }

    private DepartmentPerformanceTarget requireTargetForUpdate(
            TenantId tenantId, DepartmentPerformanceTargetId id) {
        return repository.findByIdForUpdate(tenantId, id)
                .orElseThrow(() -> notFound("department performance target is unavailable"));
    }

    private void requireActiveDepartment(TenantId tenantId, DepartmentId departmentId) {
        Department department = departmentRepository.findById(tenantId, departmentId)
                .orElseThrow(() -> notFound("target department is unavailable"));
        if (!department.isActive()) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.OPERATION_NOT_ALLOWED, "target department is inactive");
        }
    }

    private static PerformanceTargetMetric metric(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (!required) {
                return PerformanceTargetMetric.ACTIVE_CONTRACT;
            }
            throw invalid("department performance target metric is required");
        }
        try {
            return PerformanceTargetMetric.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("department performance target metric is invalid");
        }
    }

    private static PerformanceTargetPeriod period(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("department performance target period is required");
        }
        try {
            return PerformanceTargetPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("department performance target period is invalid");
        }
    }

    private static DepartmentId departmentId(Long value) {
        try {
            return new DepartmentId(value);
        } catch (RuntimeException exception) {
            throw invalid("department performance target department ID is invalid");
        }
    }

    private static DepartmentPerformanceTargetId targetId(Long value) {
        try {
            return new DepartmentPerformanceTargetId(value);
        } catch (RuntimeException exception) {
            throw invalid("department performance target ID is invalid");
        }
    }

    private static void requireYear(Integer value) {
        if (value == null || value < 2000 || value > 9999) {
            throw invalid("department performance target year is invalid");
        }
    }

    private static void requirePeriodNumber(PerformanceTargetPeriod period, Integer value) {
        boolean valid = value != null && switch (period) {
            case YEAR -> value == 1;
            case QUARTER -> value >= 1 && value <= 4;
            case MONTH -> value >= 1 && value <= 12;
        };
        if (!valid) {
            throw invalid("department performance target period number is invalid");
        }
    }

    private static List<BatchTargetCommand> batchTargets(
            List<DepartmentPerformanceTargetBatchItemRequest> values) {
        if (values == null || values.isEmpty() || values.size() > 17) {
            throw invalid("department performance target batch size is invalid");
        }
        List<BatchTargetCommand> result = new ArrayList<>(values.size());
        Set<String> periodKeys = new HashSet<>();
        for (DepartmentPerformanceTargetBatchItemRequest value : values) {
            require(value, "department performance target batch item");
            PerformanceTargetPeriod period = period(value.getPeriod());
            requirePeriodNumber(period, value.getPeriodNumber());
            String periodKey = period.name() + ':' + value.getPeriodNumber();
            if (!periodKeys.add(periodKey)) {
                throw invalid("department performance target batch periods must be unique");
            }
            if (value.getVersion() != null && value.getVersion() < 0) {
                throw invalid("department performance target batch version is invalid");
            }
            if (value.getAmount() == null) {
                throw invalid("department performance target batch amount is required");
            }
            result.add(new BatchTargetCommand(period, value.getPeriodNumber(), value.getAmount(),
                    value.getVersion()));
        }
        return List.copyOf(result);
    }

    private static List<ImportTargetCommand> importTargets(
            List<DepartmentPerformanceTargetImportItemRequest> values) {
        if (values == null || values.isEmpty() || values.size() > 200) {
            throw invalid("department performance target import size is invalid");
        }
        List<ImportTargetCommand> result = new ArrayList<>(values.size());
        Set<String> naturalKeys = new HashSet<>();
        for (DepartmentPerformanceTargetImportItemRequest value : values) {
            require(value, "department performance target import item");
            DepartmentId departmentId = departmentId(value.getDepartmentId());
            requireYear(value.getTargetYear());
            PerformanceTargetPeriod period = period(value.getPeriod());
            requirePeriodNumber(period, value.getPeriodNumber());
            if (value.getAmount() == null) {
                throw invalid("department performance target import amount is required");
            }
            String naturalKey = departmentId.getValue() + ":" + value.getTargetYear() + ":"
                    + period.name() + ":" + value.getPeriodNumber();
            if (!naturalKeys.add(naturalKey)) {
                throw invalid("department performance target import rows must be unique");
            }
            result.add(new ImportTargetCommand(departmentId, value.getTargetYear(), period,
                    value.getPeriodNumber(), value.getAmount()));
        }
        result.sort(Comparator.comparing((ImportTargetCommand value) -> value.departmentId().getValue())
                .thenComparing(ImportTargetCommand::targetYear)
                .thenComparing(value -> value.period().ordinal())
                .thenComparing(ImportTargetCommand::periodNumber));
        return List.copyOf(result);
    }

    private static void requireVersion(DepartmentPerformanceTarget target, Long version) {
        if (version == null || !version.equals(target.getVersion())) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.VERSION_CONFLICT,
                    "department performance target version is stale");
        }
    }

    private static DepartmentPerformanceTargetVO toVO(DepartmentPerformanceTarget target) {
        return new DepartmentPerformanceTargetVO(target.getId().getValue(),
                target.getDepartmentId().getValue(), target.getMetric().name(), target.getTargetYear(),
                target.getPeriod().name(), target.getPeriodNumber(), target.getAmount(), target.getVersion(),
                target.getCreatedTime(), target.getUpdatedTime());
    }

    private static void require(Object value, String description) {
        if (value == null) {
            throw invalid(description + " is required");
        }
    }

    private static NonRetryableApplicationException alreadyExists() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                "department performance target already exists");
    }

    private static NonRetryableApplicationException versionConflict() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.VERSION_CONFLICT, "department performance target version is stale");
    }

    private static NonRetryableApplicationException notFound(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private static NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private record BatchTargetCommand(
            PerformanceTargetPeriod period, int periodNumber, java.math.BigDecimal amount, Long version) {
    }

    private record ImportTargetCommand(DepartmentId departmentId, int targetYear,
                                       PerformanceTargetPeriod period, int periodNumber,
                                       java.math.BigDecimal amount) { }
}
