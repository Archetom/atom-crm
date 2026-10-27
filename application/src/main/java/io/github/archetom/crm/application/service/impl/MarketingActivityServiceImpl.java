package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingActivityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityVersionRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.MarketingFormPublicId;
import io.github.archetom.crm.application.service.MarketingActivityService;
import io.github.archetom.crm.application.service.MarketingFormCreationCoordinator;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.MarketingActivityConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.MarketingActivityRelatedObjectTypeOptionVO;
import io.github.archetom.crm.application.vo.MarketingActivityTypeOptionVO;
import io.github.archetom.crm.application.vo.MarketingActivityVO;
import io.github.archetom.crm.domain.entity.MarketingActivity;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.MarketingActivityNotFoundException;
import io.github.archetom.crm.domain.model.MarketingActivityQuery;
import io.github.archetom.crm.domain.model.MarketingActivityRelatedObjectType;
import io.github.archetom.crm.domain.model.MarketingActivityType;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.MarketingActivityRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.MarketingActivityId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MarketingActivityServiceImpl implements MarketingActivityService {
    private final MarketingActivityRepository repository;
    private final DepartmentRepository departmentRepository;
    private final MarketingFormRepository marketingFormRepository;
    private final MarketingFormCreationCoordinator marketingFormCreationCoordinator;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<MarketingActivityConfigurationOptionsVO> configurationOptions(
            AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.MARKETING_ACTIVITY_CONFIGURATION_OPTIONS,
                new ServiceOperation<>() {
                    @Override
                    public void validate() {
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_ACTIVITIES_READ);
                    }

                    @Override
                    public MarketingActivityConfigurationOptionsVO execute() {
                        return new MarketingActivityConfigurationOptionsVO(
                                Arrays.stream(MarketingActivityType.values())
                                        .map(type -> new MarketingActivityTypeOptionVO(
                                                type.code(), type.displayName()))
                                        .toList(),
                                Arrays.stream(MarketingActivityRelatedObjectType.values())
                                        .map(type -> new MarketingActivityRelatedObjectTypeOptionVO(
                                                type.code(), type.displayName(),
                                                type.supportsMarketingForm()))
                                        .toList(),
                                MarketingActivityRelatedObjectType.CUSTOMER.code());
                    }
                });
    }

    @Override
    public Result<Pager<MarketingActivityVO>> query(
            AuthenticatedCaller caller, MarketingActivityQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.MARKETING_ACTIVITY_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private MarketingActivityQuery query;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.MARKETING_ACTIVITIES_READ);
                require(request, "marketing activity query");
                query = new MarketingActivityQuery(request.getKeyword(),
                        relatedObjectType(request.getRelatedObjectType(), false),
                        request.getPage(), request.getSize());
            }

            @Override
            public Pager<MarketingActivityVO> execute() {
                PageResult<MarketingActivity> page = repository.findPage(tenantId, query);
                Pager<MarketingActivityVO> result = new Pager<>();
                result.setPageNum((int) page.page());
                result.setPageSize((int) page.size());
                result.setTotalNum(page.total());
                result.setObjectList(page.items().stream()
                        .map(activity -> toVO(tenantId, activity)).toList());
                return result;
            }
        });
    }

    @Override
    public Result<MarketingActivityVO> get(AuthenticatedCaller caller, Long activityId) {
        return queryTemplate.execute(UseCaseOperation.MARKETING_ACTIVITY_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private MarketingActivityId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.MARKETING_ACTIVITIES_READ);
                id = id(activityId);
            }

            @Override
            public MarketingActivityVO execute() {
                return toVO(tenantId, requireActivity(tenantId, id));
            }
        });
    }

    @Override
    public Result<MarketingActivityVO> create(
            AuthenticatedCaller caller, MarketingActivityCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_ACTIVITY_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private List<MemberId> participants;
            private List<DepartmentId> participantDepartments;
            private MarketingFormId marketingFormId;
            private MarketingActivityRelatedObjectType relatedObjectType;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.MARKETING_ACTIVITIES_WRITE);
                require(request, "marketing activity create request");
                if (request.getMarketingFormId() != null && request.getMarketingForm() != null) {
                    throw invalid("marketing activity can only link or create one marketing form");
                }
                relatedObjectType = relatedObjectType(request.getRelatedObjectType(), true);
                if (request.getMarketingForm() != null) {
                    MarketingFormTargetType targetType = targetType(relatedObjectType);
                    if (targetType != marketingFormTargetType(
                            request.getMarketingForm().getTargetObjectType())) {
                        throw invalid("marketing activity form target must match the related object");
                    }
                }
                marketingFormId = marketingFormId(
                        caller, tenantId, request.getMarketingFormId(), relatedObjectType);
                participants = participants(tenantId, request.getParticipantActorIds());
                participantDepartments = departments(tenantId, request.getParticipantDepartmentIds());
                requireParticipants(participants, participantDepartments);
            }

            @Override
            public MarketingActivityVO execute() {
                if (request.getMarketingForm() != null) {
                    marketingFormId = new MarketingFormId(marketingFormCreationCoordinator
                            .createInCurrentTransaction(caller, request.getMarketingForm()).id());
                }
                MarketingActivity activity = MarketingActivity.create(tenantId, new MemberId(caller.actorId()),
                        request.getName(), relatedObjectType, marketingFormId,
                        participants, participantDepartments,
                        activityType(request.getActivityType(), null),
                        request.getStartTime(), request.getEndTime(), request.getBudget(), request.getAddress(),
                        request.getDescription(), request.getViewCount(), request.getSubmissionCount(),
                        request.isOneSubmissionPerUser());
                return toVO(tenantId, repository.save(tenantId, activity));
            }
        });
    }

    @Override
    public Result<MarketingActivityVO> update(
            AuthenticatedCaller caller, Long activityId, MarketingActivityUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_ACTIVITY_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MarketingActivityId id;
            private List<MemberId> participants;
            private List<DepartmentId> participantDepartments;
            private MarketingFormId marketingFormId;
            private MarketingActivityRelatedObjectType relatedObjectType;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.MARKETING_ACTIVITIES_WRITE);
                id = id(activityId);
                require(request, "marketing activity update request");
                if (request.getMarketingForm() != null) {
                    throw invalid("marketing activity form can only be created during activity creation");
                }
                relatedObjectType = relatedObjectType(request.getRelatedObjectType(), true);
                marketingFormId = marketingFormId(
                        caller, tenantId, request.getMarketingFormId(), relatedObjectType);
                participants = participants(tenantId, request.getParticipantActorIds());
                participantDepartments = departments(tenantId, request.getParticipantDepartmentIds());
                requireParticipants(participants, participantDepartments);
            }

            @Override
            public MarketingActivityVO execute() {
                MarketingActivity activity = requireActivityForUpdate(tenantId, id);
                requireVersion(activity, request.getVersion());
                activity.update(request.getName(), relatedObjectType, marketingFormId,
                        participants, participantDepartments,
                        activityType(request.getActivityType(), activity.getActivityType()),
                        request.getStartTime(), request.getEndTime(), request.getBudget(), request.getAddress(),
                        request.getDescription(), request.getViewCount(), request.getSubmissionCount(),
                        request.isOneSubmissionPerUser());
                return toVO(tenantId, repository.save(tenantId, activity));
            }
        });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long activityId, Long version) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_ACTIVITY_DELETE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MarketingActivityId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.MARKETING_ACTIVITIES_DELETE);
                id = id(activityId);
            }

            @Override
            public Void execute() {
                MarketingActivity activity = requireActivityForUpdate(tenantId, id);
                requireVersion(activity, version);
                activity.delete();
                attachmentParentLifecycle.withdrawAll(tenantId,
                        new AttachmentTarget(AttachmentTargetType.MARKETING_ACTIVITY, id.getValue()));
                repository.save(tenantId, activity);
                return null;
            }
        });
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, MarketingActivityBatchDeleteRequest request) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_ACTIVITY_BATCH_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private List<MarketingActivityVersionRequest> targets;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_ACTIVITIES_DELETE);
                        require(request, "marketing activity batch delete request");
                        targets = request.getActivities();
                        if (targets == null || targets.isEmpty() || targets.size() > 100) {
                            throw invalid("marketing activity batch targets are invalid");
                        }
                        Set<Long> ids = new LinkedHashSet<>();
                        for (MarketingActivityVersionRequest target : targets) {
                            if (target == null || target.getActivityId() == null || target.getVersion() == null
                                    || target.getVersion() < 0 || !ids.add(target.getActivityId())) {
                                throw invalid("marketing activity batch targets are invalid");
                            }
                            id(target.getActivityId());
                        }
                        targets = targets.stream()
                                .sorted(Comparator.comparing(MarketingActivityVersionRequest::getActivityId))
                                .toList();
                    }

                    @Override
                    public Void execute() {
                        List<MarketingActivity> activities = targets.stream()
                                .map(target -> requireActivityForUpdate(
                                        tenantId, id(target.getActivityId())))
                                .toList();
                        for (int index = 0; index < activities.size(); index++) {
                            requireVersion(activities.get(index), targets.get(index).getVersion());
                        }
                        for (MarketingActivity activity : activities) {
                            activity.delete();
                            attachmentParentLifecycle.withdrawAll(tenantId,
                                    new AttachmentTarget(AttachmentTargetType.MARKETING_ACTIVITY,
                                            activity.getId().getValue()));
                            repository.save(tenantId, activity);
                        }
                        return null;
                    }
                });
    }

    private MarketingActivityVO toVO(TenantId tenantId, MarketingActivity activity) {
        MarketingForm linkedForm = activity.getMarketingFormId() == null ? null
                : marketingFormRepository.findById(tenantId, activity.getMarketingFormId())
                        .filter(form -> !form.isDeleted()).orElse(null);
        return new MarketingActivityVO(activity.getId().getValue(), activity.getName(),
                activity.getRelatedObjectType().name(), activity.getCreatedByMemberId().getValue(),
                activity.getMarketingFormId() == null ? null : activity.getMarketingFormId().getValue(),
                linkedForm != null && linkedForm.isActive()
                        ? MarketingFormPublicId.value(tenantId, linkedForm.getPublicKey()) : null,
                activity.getParticipantMemberIds().stream().map(MemberId::getValue).toList(),
                activity.getParticipantDepartmentIds().stream().map(DepartmentId::getValue).toList(),
                activity.getActivityType(), activity.getStartTime(), activity.getEndTime(), activity.getBudget(),
                activity.getAddress(), activity.getDescription(), activity.getViewCount(),
                activity.getSubmissionCount(), activity.isOneSubmissionPerUser(),
                activity.getStatus().name(), activity.getVersion(), activity.getCreatedTime(),
                activity.getUpdatedTime());
    }

    private MarketingFormId marketingFormId(AuthenticatedCaller caller, TenantId tenantId, Long value,
                                            MarketingActivityRelatedObjectType relatedObjectType) {
        if (value == null) return null;
        accessGuard.requireActiveMembership(caller, AuthorityCatalog.MARKETING_FORMS_READ);
        MarketingFormId formId;
        try {
            formId = new MarketingFormId(value);
        } catch (RuntimeException exception) {
            throw invalid("marketing activity marketing form ID is invalid");
        }
        MarketingForm form = marketingFormRepository.findById(tenantId, formId)
                .filter(candidate -> !candidate.isDeleted())
                .orElse(null);
        if (form == null || form.getTargetType() != targetType(relatedObjectType)) {
            throw invalid("marketing activity marketing form is unavailable");
        }
        return formId;
    }

    private MarketingFormTargetType targetType(
            MarketingActivityRelatedObjectType relatedObjectType) {
        return switch (relatedObjectType) {
            case CUSTOMER -> MarketingFormTargetType.CUSTOMER;
            case LEAD -> MarketingFormTargetType.LEAD;
            default -> throw invalid("marketing activity related object does not support public forms");
        };
    }

    private String activityType(String requested, String current) {
        String normalized = requested == null ? null : requested.trim();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        return MarketingActivityType.fromCode(normalized).map(MarketingActivityType::code)
                .orElseGet(() -> {
                    if (Objects.equals(normalized, current)) {
                        return current;
                    }
                    throw invalid("marketing activity type is unavailable");
                });
    }

    private MarketingFormTargetType marketingFormTargetType(String value) {
        if (value == null || value.isBlank()) {
            return MarketingFormTargetType.LEAD;
        }
        try {
            return MarketingFormTargetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("marketing activity form target is invalid");
        }
    }

    private MarketingActivity requireActivity(TenantId tenantId, MarketingActivityId id) {
        return repository.findById(tenantId, id).filter(MarketingActivity::isActive)
                .orElseThrow(MarketingActivityNotFoundException::new);
    }

    private MarketingActivity requireActivityForUpdate(TenantId tenantId, MarketingActivityId id) {
        return repository.findByIdForUpdate(tenantId, id).filter(MarketingActivity::isActive)
                .orElseThrow(MarketingActivityNotFoundException::new);
    }

    private List<MemberId> participants(TenantId tenantId, List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MarketingActivity.MAX_PARTICIPANTS) {
            throw invalid("marketing activity participants are invalid");
        }
        List<MemberId> requested;
        try {
            requested = values.stream().map(MemberId::new).toList();
        } catch (RuntimeException exception) {
            throw invalid("marketing activity participants are invalid");
        }
        Set<MemberId> unique = new LinkedHashSet<>(requested);
        if (unique.size() > MarketingActivity.MAX_PARTICIPANTS) {
            throw invalid("marketing activity participants are invalid");
        }
        Set<MemberId> active = membershipRepository.findByMembers(tenantId, unique).stream()
                .filter(OrganizationMembership::isActive).map(OrganizationMembership::getMemberId)
                .collect(java.util.stream.Collectors.toSet());
        if (!active.containsAll(unique)) {
            throw invalid("marketing activity participants are not active tenant members");
        }
        return List.copyOf(unique);
    }

    private List<DepartmentId> departments(TenantId tenantId, List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MarketingActivity.MAX_PARTICIPANTS) {
            throw invalid("marketing activity participant departments are invalid");
        }
        List<DepartmentId> requested;
        try {
            requested = values.stream().map(DepartmentId::new).toList();
        } catch (RuntimeException exception) {
            throw invalid("marketing activity participant departments are invalid");
        }
        Set<DepartmentId> unique = new LinkedHashSet<>(requested);
        if (unique.size() > MarketingActivity.MAX_PARTICIPANTS) {
            throw invalid("marketing activity participant departments are invalid");
        }
        Set<DepartmentId> active = departmentRepository.findByIds(tenantId, new java.util.ArrayList<>(unique))
                .stream().filter(Department::isActive).map(Department::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!active.containsAll(unique)) {
            throw invalid("marketing activity participant departments are not active tenant departments");
        }
        return List.copyOf(unique);
    }

    private static void requireParticipants(
            List<MemberId> participants, List<DepartmentId> participantDepartments) {
        int members = participants == null ? 0 : participants.size();
        int departments = participantDepartments == null ? 0 : participantDepartments.size();
        if (members + departments == 0 || members + departments > MarketingActivity.MAX_PARTICIPANTS) {
            throw invalid("marketing activity participants are invalid");
        }
    }

    private static MarketingActivityRelatedObjectType relatedObjectType(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (!required) return null;
            throw invalid("marketing activity related object is required");
        }
        try {
            return MarketingActivityRelatedObjectType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("marketing activity related object is invalid");
        }
    }

    private static MarketingActivityId id(Long value) {
        try {
            return new MarketingActivityId(value);
        } catch (RuntimeException exception) {
            throw invalid("marketing activity ID is invalid");
        }
    }

    private static void requireVersion(MarketingActivity activity, Long version) {
        if (version == null || !version.equals(activity.getVersion())) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.VERSION_CONFLICT, "marketing activity version is stale");
        }
    }

    private static void require(Object value, String description) {
        if (value == null) throw invalid(description + " is required");
    }

    private static NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }
}
