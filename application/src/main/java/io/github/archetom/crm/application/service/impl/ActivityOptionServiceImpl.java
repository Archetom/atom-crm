package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityOptionRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ActivityOptionService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ActivityOptionVO;
import io.github.archetom.crm.domain.entity.ActivityOption;
import io.github.archetom.crm.domain.model.ActivityOptionType;
import io.github.archetom.crm.domain.policy.ActivityOptionCatalog;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ActivityOptionRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ActivityOptionServiceImpl implements ActivityOptionService {
    private static final int MAX_OPTIONS = 100;

    private final ActivityOptionRepository repository;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<List<ActivityOptionVO>> query(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.ACTIVITY_OPTION_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.ACTIVITIES_READ);
            }
            @Override public List<ActivityOptionVO> execute() { return vos(options(tenantId)); }
        });
    }

    @Override
    public Result<List<ActivityOptionVO>> replace(
            AuthenticatedCaller caller, List<ActivityOptionRequest> requests) {
        return commandTemplate.execute(UseCaseOperation.ACTIVITY_OPTION_REPLACE, new ServiceOperation<>() {
            private TenantId tenantId;
            private List<ActivityOption> options;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.ACTIVITIES_WRITE);
                if (requests == null || requests.isEmpty() || requests.size() > MAX_OPTIONS
                        || requests.stream().anyMatch(java.util.Objects::isNull)) {
                    throw invalid("Activity options must contain one to 100 entries");
                }
                options = normalize(requests);
                if (options.stream().noneMatch(value -> value.type() == ActivityOptionType.METHOD)) {
                    throw invalid("At least one activity method is required");
                }
            }
            @Override public List<ActivityOptionVO> execute() {
                repository.replaceAll(tenantId, options);
                return vos(options);
            }
        });
    }

    private List<ActivityOption> options(TenantId tenantId) {
        List<ActivityOption> values = repository.findAll(tenantId);
        return values.isEmpty() ? ActivityOptionCatalog.defaults() : values;
    }

    private List<ActivityOption> normalize(Collection<ActivityOptionRequest> requests) {
        LinkedHashMap<String, ActivityOption> values = new LinkedHashMap<>();
        for (ActivityOptionRequest request : requests) {
            ActivityOptionType type;
            try {
                type = ActivityOptionType.valueOf(request.getType() == null ? ""
                        : request.getType().trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw invalid("Activity option type is invalid");
            }
            if (request.getDisplayOrder() == null) throw invalid("Activity option order is required");
            ActivityOption option = new ActivityOption(type, request.getValue(), request.getLabel(),
                    request.getDisplayOrder());
            if (values.put(type.name() + ':' + option.value(), option) != null) {
                throw invalid("Activity options must be unique");
            }
        }
        return List.copyOf(values.values());
    }

    private List<ActivityOptionVO> vos(Collection<ActivityOption> values) {
        return values.stream().sorted(java.util.Comparator.comparing(ActivityOption::type)
                        .thenComparingInt(ActivityOption::displayOrder).thenComparing(ActivityOption::value))
                .map(value -> new ActivityOptionVO(value.type().name(), value.value(), value.label(),
                        value.displayOrder())).toList();
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }
}
