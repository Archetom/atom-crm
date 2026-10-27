package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaAnnouncementAudienceSelectorRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementQueryRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.OaAnnouncementPublicationMetric;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.oa.OaAnnouncementPublicationStore;
import io.github.archetom.crm.application.properties.OaAnnouncementPublicationProperties;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.OaAnnouncementService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.OaAnnouncementAudienceSelectorVO;
import io.github.archetom.crm.application.vo.OaAnnouncementAudienceCandidateVO;
import io.github.archetom.crm.application.vo.OaAnnouncementInboxVO;
import io.github.archetom.crm.application.vo.OaAnnouncementReadReceiptVO;
import io.github.archetom.crm.application.vo.OaAnnouncementSummaryVO;
import io.github.archetom.crm.application.vo.OaAnnouncementVO;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.OaAnnouncementAudienceSelector;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.OaAnnouncementAudienceType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.OaAnnouncementDeliveryStatistics;
import io.github.archetom.crm.domain.model.OaAnnouncementInboxItem;
import io.github.archetom.crm.domain.model.OaAnnouncementInboxQuery;
import io.github.archetom.crm.domain.model.OaAnnouncementQuery;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.model.OaAnnouncementSummary;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class OaAnnouncementServiceImpl implements OaAnnouncementService {

    private static final OaAnnouncementDeliveryStatistics EMPTY_STATISTICS =
            new OaAnnouncementDeliveryStatistics(0, 0);

    private final OaAnnouncementRepository repository;
    private final DepartmentRepository departmentRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final Clock clock;
    private final OaAnnouncementPublicationCoordinator publicationCoordinator;
    private final OaAnnouncementPublicationStore publicationStore;
    private final OaAnnouncementPublicationProperties publicationProperties;
    private final SchedulingProperties schedulingProperties;
    private final AfterCommitExecutor afterCommitExecutor;
    private final BackgroundWorkObservability observability;
    private final AttachmentParentLifecycle attachmentParentLifecycle;

    @Override
    public Result<Pager<OaAnnouncementSummaryVO>> query(
            AuthenticatedCaller caller, OaAnnouncementQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementQuery query;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        requirePage(request, "OA announcement query");
                        query = new OaAnnouncementQuery(
                                request.getPage(), request.getSize(),
                                optionalStatus(request.getStatus()));
                    }

                    @Override
                    public Pager<OaAnnouncementSummaryVO> execute() {
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        PageResult<OaAnnouncementSummary> result =
                                repository.findPage(tenantId, query);
                        Map<OaAnnouncementId, OaAnnouncementDeliveryStatistics> statistics =
                                repository.findStatistics(tenantId, result.items().stream()
                                        .map(OaAnnouncementSummary::id).toList());
                        return page(result, value -> toSummaryVO(value,
                                statistics.getOrDefault(value.id(), EMPTY_STATISTICS)));
                    }
                });
    }

    @Override
    public Result<OaAnnouncementVO> get(
            AuthenticatedCaller caller, Long announcementIdValue) {
        return queryTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId announcementId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        announcementId = announcementId(announcementIdValue);
                    }

                    @Override
                    public OaAnnouncementVO execute() {
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        OaAnnouncement announcement = requireAnnouncement(
                                tenantId, announcementId, false);
                        return toVO(announcement, statistics(tenantId, announcementId));
                    }
                });
    }

    @Override
    public Result<Pager<OaAnnouncementAudienceCandidateVO>> audienceCandidates(
            AuthenticatedCaller caller, String type, String keyword, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_AUDIENCE_CANDIDATE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementAudienceType audienceType;
                    private String searchKeyword;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        audienceType = audienceCandidateType(type);
                        searchKeyword = blankToNull(keyword);
                        pageNumber = candidatePage(page);
                        pageSize = candidateSize(size);
                    }

                    @Override
                    public Pager<OaAnnouncementAudienceCandidateVO> execute() {
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        return switch (audienceType) {
                            case MEMBER -> memberAudienceCandidates(
                                    tenantId, searchKeyword, pageNumber, pageSize);
                            case DEPARTMENT_SUBTREE -> departmentAudienceCandidates(
                                    tenantId, searchKeyword, pageNumber, pageSize);
                            case TENANT -> throw invalid("OA announcement audience type is invalid");
                        };
                    }
                });
    }

    @Override
    public Result<OaAnnouncementVO> create(
            AuthenticatedCaller caller, OaAnnouncementCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId creator;
                    private List<OaAnnouncementAudienceSelector> audience;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        require(request, "OA announcement create request");
                        creator = memberId(caller.actorId(), "OA announcement creator");
                        audience = audience(request.getAudience());
                        now = now();
                    }

                    @Override
                    public OaAnnouncementVO execute() {
                        lockTenant(tenantId);
                        requireActiveMember(tenantId, creator,
                                "Caller is not an active tenant member");
                        validateAudienceTargets(tenantId, audience);
                        OaAnnouncement announcement = OaAnnouncement.create(
                                tenantId, creator, request.getTitle(), request.getBody(),
                                audience, now);
                        return toVO(repository.save(tenantId, announcement), EMPTY_STATISTICS);
                    }
                });
    }

    @Override
    public Result<OaAnnouncementVO> update(
            AuthenticatedCaller caller, Long announcementIdValue,
            OaAnnouncementUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId announcementId;
                    private List<OaAnnouncementAudienceSelector> audience;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        require(request, "OA announcement update request");
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        announcementId = announcementId(announcementIdValue);
                        requireVersionValue(request.getVersion());
                        audience = audience(request.getAudience());
                        now = now();
                    }

                    @Override
                    public OaAnnouncementVO execute() {
                        lockTenant(tenantId);
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        OaAnnouncement announcement = requireAnnouncement(
                                tenantId, announcementId, true);
                        requireVersion(announcement, request.getVersion());
                        validateAudienceTargets(tenantId, audience);
                        announcement.update(request.getTitle(), request.getBody(), audience, now);
                        return toVO(repository.save(tenantId, announcement),
                                statistics(tenantId, announcementId));
                    }
                });
    }

    @Override
    public Result<OaAnnouncementVO> publish(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_PUBLISH,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        require(request, "OA announcement version request");
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        id = announcementId(announcementId);
                        requireVersionValue(request.getVersion());
                    }

                    @Override
                    public OaAnnouncementVO execute() {
                        lockTenant(tenantId);
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        OaAnnouncement announcement = requireAnnouncement(tenantId, id, true);
                        requireVersion(announcement, request.getVersion());
                        LocalDateTime published = publicationStore.currentUtc();
                        if (announcement.getStatus() == OaAnnouncementStatus.SCHEDULED) {
                            recordCancelledAfterCommit(publicationStore.cancelGeneration(tenantId, id,
                                    announcement.getPublicationGeneration(), published,
                                    OaAnnouncementPublicationSafeReason.CANCELLED));
                        }
                        OaAnnouncementPublicationCoordinator.PublicationResult outcome =
                                publicationCoordinator.publish(announcement, published);
                        if (!outcome.published()) {
                            throw publicationRejected(outcome.rejectionReason());
                        }
                        return toVO(announcement, statistics(tenantId, id));
                    }
                });
    }

    @Override
    public Result<OaAnnouncementVO> schedule(
            AuthenticatedCaller caller, Long announcementIdValue,
            OaAnnouncementScheduleRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_SCHEDULE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId announcementId;
                    private LocalDateTime dueTime;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        require(request, "OA announcement schedule request");
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        announcementId = announcementId(announcementIdValue);
                        requireVersionValue(request.getVersion());
                        dueTime = scheduleUtc(request.getScheduledPublishTime());
                        if (!publicationProperties.acceptingSchedules(
                                schedulingProperties.isPulsesEnabled())) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "OA announcement publication scheduling is not available");
                        }
                    }

                    @Override
                    public OaAnnouncementVO execute() {
                        lockTenant(tenantId);
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        OaAnnouncement announcement = requireAnnouncement(
                                tenantId, announcementId, true);
                        requireVersion(announcement, request.getVersion());
                        validateAudienceTargets(tenantId, announcement.getAudienceSelectors());
                        LocalDateTime databaseNow = publicationStore.currentUtc();
                        requireSchedulingWindow(dueTime, databaseNow);
                        if (announcement.getStatus() == OaAnnouncementStatus.DRAFT
                                && repository.countByStatus(tenantId,
                                OaAnnouncementStatus.SCHEDULED) >= 1_000) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "OA announcement scheduled capacity is exhausted");
                        }
                        if (announcement.getStatus() == OaAnnouncementStatus.SCHEDULED) {
                            recordCancelledAfterCommit(publicationStore.cancelGeneration(tenantId, announcementId,
                                    announcement.getPublicationGeneration(), databaseNow,
                                    OaAnnouncementPublicationSafeReason.SUPERSEDED));
                        }
                        OaAnnouncementPublicationSafeReason attachmentFailure =
                                publicationCoordinator.validateAttachmentSet(announcement);
                        if (attachmentFailure != null) {
                            throw publicationRejected(attachmentFailure);
                        }
                        announcement.schedule(memberId, dueTime, databaseNow);
                        repository.save(tenantId, announcement);
                        publicationStore.create(tenantId, announcementId,
                                announcement.getPublicationGeneration(), dueTime,
                                dueTime.plus(publicationProperties.getMaxLateness()), databaseNow);
                        afterCommitExecutor.execute("OA announcement publication", () ->
                                observability.recordOaAnnouncementPublicationOutcome(
                                        OaAnnouncementPublicationMetric.SCHEDULED, 1));
                        return toVO(announcement, statistics(tenantId, announcementId));
                    }
                });
    }

    @Override
    public Result<OaAnnouncementVO> cancelSchedule(
            AuthenticatedCaller caller, Long announcementIdValue,
            OaAnnouncementVersionRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_CANCEL_SCHEDULE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId announcementId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        require(request, "OA announcement version request");
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        announcementId = announcementId(announcementIdValue);
                        requireVersionValue(request.getVersion());
                    }

                    @Override
                    public OaAnnouncementVO execute() {
                        lockTenant(tenantId);
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        OaAnnouncement announcement = requireAnnouncement(
                                tenantId, announcementId, true);
                        requireVersion(announcement, request.getVersion());
                        LocalDateTime databaseNow = publicationStore.currentUtc();
                        recordCancelledAfterCommit(publicationStore.cancelGeneration(tenantId, announcementId,
                                announcement.getPublicationGeneration(), databaseNow,
                                OaAnnouncementPublicationSafeReason.CANCELLED));
                        announcement.cancelSchedule(databaseNow);
                        repository.save(tenantId, announcement);
                        return toVO(announcement, statistics(tenantId, announcementId));
                    }
                });
    }

    @Override
    public Result<OaAnnouncementVO> withdraw(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request) {
        return transition(UseCaseOperation.OA_ANNOUNCEMENT_WITHDRAW,
                caller, announcementId, request,
                announcement -> {
                    announcement.withdraw(now());
                    attachmentParentLifecycle.withdrawAll(announcement.getTenantId(), attachmentTarget(announcement));
                    repository.save(announcement.getTenantId(), announcement);
                    publicationCoordinator.suppressOutstandingDeliveries(announcement.getTenantId(),
                            announcement.getId(), announcement.getWithdrawnTime());
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long announcementIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId announcementId;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                        memberId = memberId(caller.actorId(), "OA announcement manager");
                        announcementId = announcementId(announcementIdValue);
                        requireVersionValue(version);
                        now = now();
                    }

                    @Override
                    public Void execute() {
                        lockTenant(tenantId);
                        requireActiveMember(tenantId, memberId,
                                "Caller is not an active tenant member");
                        OaAnnouncement announcement = requireAnnouncement(
                                tenantId, announcementId, true);
                        requireVersion(announcement, version);
                        if (announcement.getStatus() == OaAnnouncementStatus.SCHEDULED) {
                            LocalDateTime databaseNow = publicationStore.currentUtc();
                            recordCancelledAfterCommit(publicationStore.cancelGeneration(tenantId, announcementId,
                                    announcement.getPublicationGeneration(), databaseNow,
                                    OaAnnouncementPublicationSafeReason.CANCELLED));
                            announcement.delete(databaseNow);
                        } else {
                            announcement.delete(now);
                        }
                        attachmentParentLifecycle.withdrawAll(tenantId, attachmentTarget(announcement));
                        repository.save(tenantId, announcement);
                        if (announcement.getStatus() == OaAnnouncementStatus.DELETED) {
                            publicationCoordinator.suppressOutstandingDeliveries(tenantId, announcementId,
                                    announcement.getUpdatedTime());
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<Pager<OaAnnouncementInboxVO>> mine(
            AuthenticatedCaller caller, QueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_INBOX_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OaAnnouncementInboxQuery query;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
                        requirePage(request, "OA announcement inbox query");
                        query = new OaAnnouncementInboxQuery(
                                request.getPage(), request.getSize(),
                                memberId(caller.actorId(), "OA announcement recipient"));
                    }

                    @Override
                    public Pager<OaAnnouncementInboxVO> execute() {
                        return page(repository.findInboxPage(tenantId, query),
                                OaAnnouncementServiceImpl.this::toInboxVO);
                    }
                });
    }

    @Override
    public Result<OaAnnouncementInboxVO> getMine(
            AuthenticatedCaller caller, Long announcementIdValue) {
        return queryTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_INBOX_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId announcementId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
                        memberId = memberId(caller.actorId(), "OA announcement recipient");
                        announcementId = announcementId(announcementIdValue);
                    }

                    @Override
                    public OaAnnouncementInboxVO execute() {
                        return repository.findInboxItem(
                                        tenantId, announcementId, memberId)
                                .map(OaAnnouncementServiceImpl.this::toInboxVO)
                                .orElseThrow(() -> notFound(
                                        "OA announcement was not found"));
                    }
                });
    }

    @Override
    public Result<Long> unreadCount(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_UNREAD_COUNT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
                        memberId = memberId(caller.actorId(), "OA announcement recipient");
                    }

                    @Override
                    public Long execute() {
                        return repository.countUnread(tenantId, memberId);
                    }
                });
    }

    @Override
    public Result<OaAnnouncementReadReceiptVO> markRead(
            AuthenticatedCaller caller, Long announcementIdValue) {
        return commandTemplate.execute(UseCaseOperation.OA_ANNOUNCEMENT_MARK_READ,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaAnnouncementId announcementId;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
                        memberId = memberId(caller.actorId(), "OA announcement recipient");
                        announcementId = announcementId(announcementIdValue);
                        now = now();
                    }

                    @Override
                    public OaAnnouncementReadReceiptVO execute() {
                        requireActiveMemberForShare(tenantId, memberId,
                                "Caller is not an active tenant member");
                        repository.findInboxItemForShare(
                                        tenantId, announcementId, memberId)
                                .orElseThrow(() -> notFound(
                                        "OA announcement was not found"));
                        LocalDateTime readTime = repository.saveReadReceiptIfAbsent(
                                tenantId, announcementId, memberId, now);
                        return new OaAnnouncementReadReceiptVO(
                                announcementId.getValue(), readTime);
                    }
                });
    }

    private Result<OaAnnouncementVO> transition(
            UseCaseOperation operation, AuthenticatedCaller caller,
            Long announcementIdValue, OaAnnouncementVersionRequest request,
            Consumer<OaAnnouncement> transition) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private OaAnnouncementId announcementId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
                require(request, "OA announcement version request");
                memberId = memberId(caller.actorId(), "OA announcement manager");
                announcementId = announcementId(announcementIdValue);
                requireVersionValue(request.getVersion());
            }

            @Override
            public OaAnnouncementVO execute() {
                lockTenant(tenantId);
                requireActiveMember(tenantId, memberId,
                        "Caller is not an active tenant member");
                OaAnnouncement announcement = requireAnnouncement(
                        tenantId, announcementId, true);
                requireVersion(announcement, request.getVersion());
                transition.accept(announcement);
                return toVO(announcement, statistics(tenantId, announcementId));
            }
        });
    }

    private void validateAudienceTargets(
            TenantId tenantId, Collection<OaAnnouncementAudienceSelector> audience) {
        List<DepartmentId> departmentIds = audience.stream()
                .filter(value -> value.type()
                        == OaAnnouncementAudienceType.DEPARTMENT_SUBTREE)
                .map(OaAnnouncementAudienceSelector::departmentId).distinct().toList();
        List<Department> departments = departmentRepository.findByIds(
                tenantId, departmentIds);
        Set<Long> activeDepartments = new HashSet<>(departments.stream()
                .filter(Department::isActive)
                .map(value -> value.getId().getValue()).toList());
        if (activeDepartments.size() != departmentIds.size()) {
            throw notFound("OA announcement audience department was not found");
        }

        List<MemberId> memberIds = audience.stream()
                .filter(value -> value.type() == OaAnnouncementAudienceType.MEMBER)
                .map(OaAnnouncementAudienceSelector::memberId).distinct().toList();
        List<OrganizationMembership> memberships =
                membershipRepository.findByMembers(tenantId, memberIds);
        Set<Long> activeMembers = new HashSet<>(memberships.stream()
                .filter(OrganizationMembership::isActive)
                .map(value -> value.getMemberId().getValue()).toList());
        if (activeMembers.size() != memberIds.size()) {
            throw notFound("OA announcement audience member was not found");
        }
    }

    private Pager<OaAnnouncementAudienceCandidateVO> memberAudienceCandidates(
            TenantId tenantId, String keyword, int page, int size) {
        PageResult<MemberProfile> profiles = memberProfileRepository.findActiveByTenant(
                tenantId, keyword, page, size);
        Pager<OaAnnouncementAudienceCandidateVO> result = new Pager<>();
        result.setPageNum(profiles.page());
        result.setPageSize(profiles.size());
        result.setTotalNum(profiles.total());
        result.setObjectList(profiles.items().stream().filter(MemberProfile::isActive)
                .map(value -> new OaAnnouncementAudienceCandidateVO(
                        value.getId().getValue(), value.getDisplayName())).toList());
        return result;
    }

    private Pager<OaAnnouncementAudienceCandidateVO> departmentAudienceCandidates(
            TenantId tenantId, String keyword, int page, int size) {
        List<Department> departments = departmentRepository.findAll(tenantId).stream()
                .filter(Department::isActive)
                .filter(value -> keyword == null || value.getName().toLowerCase(Locale.ROOT)
                        .contains(keyword.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(Department::getName)
                        .thenComparing(value -> value.getId().getValue()))
                .toList();
        int from = Math.min((page - 1) * size, departments.size());
        int to = Math.min(from + size, departments.size());
        Pager<OaAnnouncementAudienceCandidateVO> result = new Pager<>();
        result.setPageNum((long) page);
        result.setPageSize((long) size);
        result.setTotalNum((long) departments.size());
        result.setObjectList(departments.subList(from, to).stream().map(value ->
                new OaAnnouncementAudienceCandidateVO(
                        value.getId().getValue(), value.getName())).toList());
        return result;
    }

    private OaAnnouncement requireAnnouncement(
            TenantId tenantId, OaAnnouncementId announcementId, boolean lock) {
        return (lock ? repository.findByIdForUpdate(tenantId, announcementId)
                : repository.findById(tenantId, announcementId))
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> notFound("OA announcement was not found"));
    }

    private OaAnnouncementDeliveryStatistics statistics(
            TenantId tenantId, OaAnnouncementId announcementId) {
        return repository.findStatistics(tenantId, List.of(announcementId))
                .getOrDefault(announcementId, EMPTY_STATISTICS);
    }

    private OaAnnouncementVO toVO(
            OaAnnouncement value, OaAnnouncementDeliveryStatistics statistics) {
        return new OaAnnouncementVO(
                value.getId().getValue(), value.getCreatedByMemberId().getValue(),
                value.getTitle(), value.getBody(), audienceSelectors(value.getTenantId(),
                        value.getAudienceSelectors()),
                value.getStatus().name(), statistics.recipientTotal(),
                statistics.readTotal(), value.getScheduledPublishTime(),
                value.getScheduledByMemberId() == null ? null
                        : value.getScheduledByMemberId().getValue(),
                value.getLastPublicationSafeReason() == null ? null
                        : value.getLastPublicationSafeReason().name(),
                value.getPublishedTime(),
                value.getWithdrawnTime(), allowedActions(value.getStatus()), value.getVersion(),
                value.getCreatedTime(), value.getUpdatedTime());
    }

    private List<OaAnnouncementAudienceSelectorVO> audienceSelectors(
            TenantId tenantId, Collection<OaAnnouncementAudienceSelector> selectors) {
        Map<Long, String> departments = departmentRepository.findByIds(tenantId, selectors.stream()
                        .filter(value -> value.type() == OaAnnouncementAudienceType.DEPARTMENT_SUBTREE)
                        .map(OaAnnouncementAudienceSelector::departmentId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(
                        value -> value.getId().getValue(), Department::getName));
        return selectors.stream().map(selector -> {
            Long departmentId = selector.departmentId() == null ? null
                    : selector.departmentId().getValue();
            Long memberActorId = selector.memberId() == null ? null
                    : selector.memberId().getValue();
            String displayName = switch (selector.type()) {
                case TENANT -> null;
                case DEPARTMENT_SUBTREE -> departments.get(departmentId);
                case MEMBER -> memberProfileRepository.findById(selector.memberId())
                        .map(MemberProfile::getDisplayName).orElse(null);
            };
            return new OaAnnouncementAudienceSelectorVO(
                    selector.type().name(), departmentId, memberActorId, displayName);
        }).toList();
    }

    private List<String> allowedActions(OaAnnouncementStatus status) {
        return switch (status) {
            case DRAFT -> List.of("UPDATE", "PUBLISH", "SCHEDULE", "DELETE");
            case SCHEDULED -> List.of("SCHEDULE", "CANCEL_SCHEDULE");
            case PUBLISHED -> List.of("WITHDRAW");
            case WITHDRAWN -> List.of("DELETE");
            case DELETED -> List.of();
        };
    }

    private OaAnnouncementSummaryVO toSummaryVO(
            OaAnnouncementSummary value,
            OaAnnouncementDeliveryStatistics statistics) {
        return new OaAnnouncementSummaryVO(
                value.id().getValue(), value.createdByMemberId().getValue(),
                value.title(), value.body(), value.status().name(),
                statistics.recipientTotal(), statistics.readTotal(),
                value.scheduledPublishTime(), value.scheduledByMemberId() == null ? null
                        : value.scheduledByMemberId().getValue(),
                value.lastPublicationSafeReason() == null ? null
                        : value.lastPublicationSafeReason().name(),
                value.publishedTime(), value.withdrawnTime(), value.version(),
                value.createdTime(), value.updatedTime());
    }

    private OaAnnouncementInboxVO toInboxVO(OaAnnouncementInboxItem item) {
        return new OaAnnouncementInboxVO(
                item.id().getValue(), item.title(), item.body(),
                item.read(), item.readTime(), item.publishedTime());
    }

    private List<OaAnnouncementAudienceSelector> audience(
            Collection<OaAnnouncementAudienceSelectorRequest> values) {
        if (values == null) {
            throw invalid("OA announcement audience is required");
        }
        try {
            return values.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException();
                }
                OaAnnouncementAudienceType type = OaAnnouncementAudienceType.valueOf(
                        value.getType() == null ? ""
                                : value.getType().trim().toUpperCase(Locale.ROOT));
                return switch (type) {
                    case TENANT -> {
                        if (value.getDepartmentId() != null
                                || value.getMemberActorId() != null) {
                            throw new IllegalArgumentException();
                        }
                        yield OaAnnouncementAudienceSelector.tenant();
                    }
                    case DEPARTMENT_SUBTREE -> {
                        if (value.getDepartmentId() == null
                                || value.getMemberActorId() != null) {
                            throw new IllegalArgumentException();
                        }
                        yield OaAnnouncementAudienceSelector.department(
                                new DepartmentId(value.getDepartmentId()));
                    }
                    case MEMBER -> {
                        if (value.getMemberActorId() == null
                                || value.getDepartmentId() != null) {
                            throw new IllegalArgumentException();
                        }
                        yield OaAnnouncementAudienceSelector.member(
                                new MemberId(value.getMemberActorId()));
                    }
                };
            }).toList();
        } catch (RuntimeException exception) {
            throw invalid("OA announcement audience is invalid");
        }
    }

    private OaAnnouncementStatus optionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            OaAnnouncementStatus status = OaAnnouncementStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
            if (status == OaAnnouncementStatus.DELETED) {
                throw new IllegalArgumentException();
            }
            return status;
        } catch (IllegalArgumentException exception) {
            throw invalid("OA announcement status is invalid");
        }
    }

    private OaAnnouncementAudienceType audienceCandidateType(String value) {
        try {
            OaAnnouncementAudienceType type = OaAnnouncementAudienceType.valueOf(
                    value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
            if (type == OaAnnouncementAudienceType.TENANT) {
                throw new IllegalArgumentException();
            }
            return type;
        } catch (IllegalArgumentException exception) {
            throw invalid("OA announcement audience type is invalid");
        }
    }

    private int candidatePage(Integer value) {
        if (value == null || value < 1 || value > QueryRequest.MAX_PAGE) {
            throw invalid("OA announcement audience pagination is invalid");
        }
        return value;
    }

    private int candidateSize(Integer value) {
        if (value == null || value < 1 || value > QueryRequest.MAX_SIZE) {
            throw invalid("OA announcement audience pagination is invalid");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requirePage(QueryRequest request, String description) {
        require(request, description);
        if (request.getPage() == null || request.getPage() < 1
                || request.getPage() > QueryRequest.MAX_PAGE
                || request.getSize() == null || request.getSize() < 1
                || request.getSize() > QueryRequest.MAX_SIZE) {
            throw invalid(description + " pagination is invalid");
        }
    }

    private void requireVersion(OaAnnouncement announcement, Long version) {
        requireVersionValue(version);
        if (!version.equals(announcement.getVersion())) {
            throw new AggregateVersionConflictException();
        }
    }

    private void requireVersionValue(Long version) {
        if (version == null || version < 0) {
            throw invalid("OA announcement version is invalid");
        }
    }

    private OaAnnouncementId announcementId(Long value) {
        try {
            return new OaAnnouncementId(value);
        } catch (RuntimeException exception) {
            throw invalid("OA announcement ID is invalid");
        }
    }

    private MemberId memberId(Long value, String description) {
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw invalid(description + " is invalid");
        }
    }

    private OrganizationMembership requireActiveMember(
            TenantId tenantId, MemberId memberId, String message) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED, message));
    }

    private OrganizationMembership requireActiveMemberForShare(
            TenantId tenantId, MemberId memberId, String message) {
        return membershipRepository.findByMemberForShare(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED, message));
    }

    private void lockTenant(TenantId tenantId) {
        if (!tenantRepository.lockForTenantChanges(tenantId)) {
            throw notFound("OA announcement tenant was not found");
        }
    }

    private LocalDateTime now() {
        LocalDateTime value = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private LocalDateTime scheduleUtc(OffsetDateTime value) {
        if (value == null) {
            throw invalid("OA announcement scheduled publication time is required");
        }
        try {
            LocalDateTime result = LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
            if (result.getYear() < 1000 || result.getYear() > 9999) {
                throw new IllegalArgumentException();
            }
            return result.withNano(result.getNano() / 1_000 * 1_000);
        } catch (RuntimeException exception) {
            throw invalid("OA announcement scheduled publication time is invalid");
        }
    }

    private void requireSchedulingWindow(LocalDateTime dueTime, LocalDateTime databaseNow) {
        if (dueTime == null || databaseNow == null
                || dueTime.isBefore(databaseNow.plusMinutes(1))
                || dueTime.isAfter(databaseNow.plusDays(365))) {
            throw invalid("OA announcement scheduled publication time is outside the allowed window");
        }
    }

    private NonRetryableApplicationException publicationRejected(
            OaAnnouncementPublicationSafeReason reason) {
        String message = switch (reason) {
            case AUDIENCE_EMPTY -> "OA announcement audience resolves to no active recipients";
            case AUDIENCE_LIMIT -> "OA announcement recipient limit would be exceeded";
            case AUDIENCE_INVALID -> "OA announcement audience is no longer valid";
            case ATTACHMENT_UNAVAILABLE -> "OA announcement attachments are not ready";
            default -> "OA announcement publication is not allowed";
        };
        return error(ApplicationErrorCode.OPERATION_NOT_ALLOWED, message);
    }

    private static AttachmentTarget attachmentTarget(OaAnnouncement announcement) {
        return new AttachmentTarget(AttachmentTargetType.OA_ANNOUNCEMENT,
                announcement.getId().getValue());
    }

    private void recordCancelledAfterCommit(boolean cancelled) {
        if (!cancelled) return;
        afterCommitExecutor.execute("OA announcement cancellation metrics", () -> observability.recordOaAnnouncementPublicationOutcome(
                OaAnnouncementPublicationMetric.CANCELLED, 1));
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw invalid(description + " is required");
        }
    }

    private NonRetryableApplicationException invalid(String message) {
        return error(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private NonRetryableApplicationException notFound(String message) {
        return error(ApplicationErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private <S, T> Pager<T> page(PageResult<S> source,
                                 java.util.function.Function<S, T> mapper) {
        Pager<T> result = new Pager<>();
        result.setPageNum(source.page());
        result.setPageSize(source.size());
        result.setTotalNum(source.total());
        result.setObjectList(source.items().stream().map(mapper).toList());
        return result;
    }
}
