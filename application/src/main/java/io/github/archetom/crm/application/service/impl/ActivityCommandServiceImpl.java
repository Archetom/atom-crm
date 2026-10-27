package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.ActivityRelationRequest;
import io.github.archetom.crm.api.dto.request.ActivityUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ActivityCommandService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ActivityVO;
import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.entity.ActivityRelation;
import io.github.archetom.crm.domain.entity.ActivityOption;
import io.github.archetom.crm.domain.exception.ActivityNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ActivityOptionCatalog;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.ActivityOptionRepository;
import io.github.archetom.crm.domain.valueobject.ActivityId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityCommandServiceImpl implements ActivityCommandService {
    private static final int MAX_BATCH = 100;

    private final ActivityRepository repository;
    private final ActivityOptionRepository optionRepository;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final ActivityOutputSupport outputSupport;
    private final ActivityAuditService auditService;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<ActivityVO> create(AuthenticatedCaller caller, ActivityCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.ACTIVITY_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;

            @Override
            public void validate() {
                require(request, "Activity create request");
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.ACTIVITIES_WRITE);
            }

            @Override
            public ActivityVO execute() {
                Activity created = create(caller, tenantId, request, options(tenantId));
                auditService.record(caller, tenantId, created.getSubjectType(), created.getSubjectId(),
                        "Follow-up activity created");
                return outputSupport.toVO(caller, tenantId, created);
            }
        });
    }

    @Override
    public Result<List<ActivityVO>> createBatch(
            AuthenticatedCaller caller, List<ActivityCreateRequest> requests) {
        return commandTemplate.execute(UseCaseOperation.ACTIVITY_BATCH_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;

            @Override
            public void validate() {
                if (requests == null || requests.isEmpty() || requests.size() > MAX_BATCH
                        || requests.stream().anyMatch(java.util.Objects::isNull)) {
                    throw invalid("Activity batch must contain one to 100 entries");
                }
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.ACTIVITIES_WRITE);
            }

            @Override
            public List<ActivityVO> execute() {
                List<ActivityVO> result = new ArrayList<>(requests.size());
                List<ActivityOption> options = options(tenantId);
                for (ActivityCreateRequest request : requests) {
                    Activity created = create(caller, tenantId, request, options);
                    auditService.record(caller, tenantId, created.getSubjectType(), created.getSubjectId(),
                            "Follow-up activity created");
                    result.add(outputSupport.toVO(caller, tenantId, created));
                }
                return List.copyOf(result);
            }
        });
    }

    @Override
    public Result<ActivityVO> update(
            AuthenticatedCaller caller, Long activityIdValue, ActivityUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.ACTIVITY_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private ActivityId activityId;

            @Override
            public void validate() {
                require(request, "Activity update request");
                activityId = id(activityIdValue);
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.ACTIVITIES_WRITE);
            }

            @Override
            public ActivityVO execute() {
                Activity activity = repository.findById(tenantId, activityId)
                        .orElseThrow(ActivityNotFoundException::new);
                requireSubjectTenant(caller, tenantId, activity, true);
                requireAuthor(caller, activity);
                requireVersion(activity, request.getVersion());
                activity.replace(method(request.getCategory(), options(tenantId)),
                        request.getPurpose(), request.getContent(),
                        request.getOccurredTime(), request.getNextContactTime(),
                        relations(caller, tenantId, request.getRelations()));
                repository.save(tenantId, activity);
                auditService.record(caller, tenantId, activity.getSubjectType(), activity.getSubjectId(),
                        "Follow-up activity updated");
                return outputSupport.toVO(caller, tenantId, activity);
            }
        });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long activityIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.ACTIVITY_DELETE, new ServiceOperation<>() {
            private TenantId tenantId;
            private ActivityId activityId;

            @Override
            public void validate() {
                activityId = id(activityIdValue);
                if (version == null || version < 0) throw invalid("Activity version is invalid");
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.ACTIVITIES_DELETE);
            }

            @Override
            public Void execute() {
                Activity activity = repository.findById(tenantId, activityId)
                        .orElseThrow(ActivityNotFoundException::new);
                requireSubjectTenant(caller, tenantId, activity, true);
                requireAuthor(caller, activity);
                requireVersion(activity, version);
                activity.delete();
                repository.save(tenantId, activity);
                auditService.record(caller, tenantId, activity.getSubjectType(), activity.getSubjectId(),
                        "Follow-up activity deleted");
                return null;
            }
        });
    }

    private Activity create(AuthenticatedCaller caller, TenantId tenantId,
                            ActivityCreateRequest request, List<ActivityOption> options) {
        require(request, "Activity create request");
        BusinessObjectType subjectType = type(request.getSubjectType());
        TenantId subjectTenant = recordAccessGuard.requireVisible(caller, subjectType,
                request.getSubjectId(), true);
        requireSameTenant(tenantId, subjectTenant);
        Activity value = Activity.followUp(tenantId, subjectType, request.getSubjectId(),
                new MemberId(caller.actorId()), method(request.getCategory(), options), request.getPurpose(),
                request.getContent(), request.getOccurredTime(), request.getNextContactTime(),
                relations(caller, tenantId, request.getRelations()));
        return repository.save(tenantId, value);
    }

    private List<ActivityOption> options(TenantId tenantId) {
        List<ActivityOption> configured = optionRepository.lockAndFindAll(tenantId);
        return configured.isEmpty() ? ActivityOptionCatalog.defaults() : configured;
    }

    private String method(String value, Collection<ActivityOption> options) {
        final String normalized;
        try {
            normalized = ActivityOption.normalizeValue(value);
        } catch (io.github.archetom.crm.domain.exception.ActivityDomainException exception) {
            throw invalid("Activity method is invalid");
        }
        return options.stream().filter(option -> option.type()
                        == io.github.archetom.crm.domain.model.ActivityOptionType.METHOD)
                .map(ActivityOption::value).filter(normalized::equals).findFirst()
                .orElseThrow(() -> invalid("Activity method is unavailable"));
    }

    private List<ActivityRelation> relations(AuthenticatedCaller caller, TenantId tenantId,
                                             Collection<ActivityRelationRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        if (requests.size() > Activity.MAX_RELATIONS
                || requests.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("Activity relations are invalid");
        }
        List<ActivityRelation> result = new ArrayList<>(requests.size());
        for (ActivityRelationRequest request : requests) {
            try {
                result.add(new ActivityRelation(type(request.getObjectType()), request.getRecordId()));
            } catch (io.github.archetom.crm.domain.exception.ActivityDomainException exception) {
                throw invalid("Activity relation is invalid");
            }
        }
        for (ActivityRelation relation : result) {
            TenantId relatedTenant = recordAccessGuard.requireVisible(caller, relation.objectType(),
                    relation.recordId(), false);
            requireSameTenant(tenantId, relatedTenant);
        }
        return List.copyOf(result);
    }

    private void requireSubjectTenant(
            AuthenticatedCaller caller, TenantId expectedTenant, Activity activity, boolean write) {
        TenantId actual = recordAccessGuard.requireVisible(caller, activity.getSubjectType(),
                activity.getSubjectId(), write);
        requireSameTenant(expectedTenant, actual);
    }

    private void requireAuthor(AuthenticatedCaller caller, Activity activity) {
        if (!activity.isUserFollowUp() || !activity.getCreatorMemberId().getValue().equals(caller.actorId())) {
            throw invalid("Only the activity author may change this entry");
        }
    }

    private void requireVersion(Activity value, Long version) {
        if (!value.getVersion().equals(version)) {
            throw new io.github.archetom.crm.domain.exception.AggregateVersionConflictException();
        }
    }

    private ActivityId id(Long value) {
        try {
            return new ActivityId(value);
        } catch (RuntimeException exception) {
            throw invalid("Activity ID is invalid");
        }
    }

    private BusinessObjectType type(String value) {
        try {
            return BusinessObjectType.valueOf(value == null ? "" : value.trim().toUpperCase(
                    java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid("Activity object type is invalid");
        }
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (actual == null || !expected.sameValueAs(actual)) {
            throw invalid("Activity relation tenant is invalid");
        }
    }

    private void require(Object value, String name) {
        if (value == null) throw invalid(name + " is required");
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

}
