package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DashboardPreferenceUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.DashboardPreferenceService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.DashboardPreferenceVO;
import io.github.archetom.crm.domain.entity.DashboardPreference;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardModule;
import io.github.archetom.crm.domain.model.DashboardTimeRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.DashboardPreferenceRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.List;
import java.util.Set;

/** Personal dashboard defaults never replace source capability, data-scope, or field checks. */
@Service
@RequiredArgsConstructor
public class DashboardPreferenceServiceImpl implements DashboardPreferenceService {

    private final DashboardPreferenceRepository repository;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<DashboardPreferenceVO> get(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.DASHBOARD_PREFERENCE_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        memberId = new MemberId(caller.actorId());
                    }

                    @Override
                    public DashboardPreferenceVO execute() {
                        return repository.findByMember(tenantId, memberId)
                                .map(DashboardPreferenceServiceImpl.this::toVO)
                                .orElseGet(DashboardPreferenceServiceImpl.this::defaults);
                    }
                });
    }

    @Override
    public Result<DashboardPreferenceVO> update(
            AuthenticatedCaller caller, DashboardPreferenceUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.DASHBOARD_PREFERENCE_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private DashboardTimeRange timeRange;
                    private DashboardDataScope dataScope;
                    private Set<DashboardModule> hiddenModules;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        memberId = new MemberId(caller.actorId());
                        if (request == null) {
                            throw invalid("Dashboard preference request must not be empty");
                        }
                        timeRange = enumValue(DashboardTimeRange.class,
                                request.getTimeRange(), "Dashboard time range is invalid");
                        dataScope = enumValue(DashboardDataScope.class,
                                request.getDataScope(), "Dashboard data scope is invalid");
                        hiddenModules = dashboardModules(request.getHiddenModules());
                    }

                    @Override
                    public DashboardPreferenceVO execute() {
                        DashboardPreference preference = repository
                                .findByMember(tenantId, memberId)
                                .map(this::updateExisting)
                                .orElseGet(this::createPreference);
                        return toVO(repository.save(tenantId, preference));
                    }

                    private DashboardPreference createPreference() {
                        if (request.getVersion() != null) {
                            throw versionConflict();
                        }
                        return DashboardPreference.create(tenantId, memberId, timeRange,
                                request.getCustomStartDate(), request.getCustomEndDate(), dataScope,
                                hiddenModules);
                    }

                    private DashboardPreference updateExisting(DashboardPreference existing) {
                        if (request.getVersion() == null
                                || !request.getVersion().equals(existing.getVersion())) {
                            throw versionConflict();
                        }
                        existing.change(timeRange, request.getCustomStartDate(),
                                request.getCustomEndDate(), dataScope, hiddenModules);
                        return existing;
                    }
                });
    }

    private DashboardPreferenceVO defaults() {
        return new DashboardPreferenceVO(
                DashboardPreference.DEFAULT_TIME_RANGE.name(), null, null,
                DashboardPreference.DEFAULT_DATA_SCOPE.name(), List.of(), null);
    }

    private DashboardPreferenceVO toVO(DashboardPreference preference) {
        return new DashboardPreferenceVO(
                preference.getTimeRange().name(), preference.getCustomStartDate(),
                preference.getCustomEndDate(), preference.getDataScope().name(),
                preference.getHiddenModules().stream().map(Enum::name).sorted().toList(),
                preference.getVersion());
    }

    private <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String message) {
        try {
            return Enum.valueOf(type, value == null ? ""
                    : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(message);
        }
    }

    private Set<DashboardModule> dashboardModules(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        if (values.size() > DashboardModule.values().length) {
            throw invalid("Dashboard hidden modules exceed the catalog limit");
        }
        java.util.EnumSet<DashboardModule> result = java.util.EnumSet.noneOf(DashboardModule.class);
        for (String value : values) {
            result.add(enumValue(DashboardModule.class, value,
                    "Dashboard hidden module is invalid"));
        }
        return Set.copyOf(result);
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private NonRetryableApplicationException versionConflict() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.VERSION_CONFLICT,
                "Dashboard preference version is stale");
    }
}
