package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityQueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ActivityQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ActivityVO;
import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.exception.ActivityNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.valueobject.ActivityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityQueryServiceImpl implements ActivityQueryService {
    private final ActivityRepository repository;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final ActivityOutputSupport outputSupport;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ActivityVO>> query(AuthenticatedCaller caller, ActivityQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.ACTIVITY_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;

            @Override
            public void validate() {
                if (request == null || request.getSubjectType() == null || request.getSubjectId() == null
                        || request.getSubjectId() <= 0 || request.getPage() == null
                        || request.getSize() == null) throw invalid("Activity query is invalid");
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.ACTIVITIES_READ);
            }

            @Override
            public Pager<ActivityVO> execute() {
                BusinessObjectType type = type(request.getSubjectType());
                TenantId subjectTenant = recordAccessGuard.requireVisible(caller, type,
                        request.getSubjectId(), false);
                if (!tenantId.sameValueAs(subjectTenant)) throw invalid("Activity subject tenant is invalid");
                var page = repository.findBySubject(tenantId, type, request.getSubjectId(),
                        request.getPage(), request.getSize());
                Pager<ActivityVO> result = new Pager<>();
                result.setPageNum(page.page());
                result.setPageSize(page.size());
                result.setTotalNum(page.total());
                result.setObjectList(outputSupport.toVOs(caller, tenantId, page.items()));
                return result;
            }
        });
    }

    @Override
    public Result<ActivityVO> get(AuthenticatedCaller caller, Long activityIdValue) {
        return queryTemplate.execute(UseCaseOperation.ACTIVITY_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private ActivityId activityId;

            @Override
            public void validate() {
                try {
                    activityId = new ActivityId(activityIdValue);
                } catch (RuntimeException exception) {
                    throw invalid("Activity ID is invalid");
                }
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.ACTIVITIES_READ);
            }

            @Override
            public ActivityVO execute() {
                Activity value = repository.findById(tenantId, activityId)
                        .orElseThrow(ActivityNotFoundException::new);
                TenantId subjectTenant = recordAccessGuard.requireVisible(caller, value.getSubjectType(),
                        value.getSubjectId(), false);
                if (!tenantId.sameValueAs(subjectTenant)) throw invalid("Activity subject tenant is invalid");
                return outputSupport.toVO(caller, tenantId, value);
            }
        });
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private BusinessObjectType type(String value) {
        try {
            return BusinessObjectType.valueOf(value == null ? "" : value.trim().toUpperCase(
                    java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid("Activity object type is invalid");
        }
    }
}
