package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NotificationQueryRequest;
import io.github.archetom.crm.api.dto.request.NotificationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.NotificationMetric;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.NotificationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.NotificationVO;
import io.github.archetom.crm.application.vo.NotificationPreferenceVO;
import io.github.archetom.crm.application.port.out.notification.NotificationPreference;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.model.NotificationQuery;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository repository;
    private final NotificationPreferenceStore preferenceStore;
    private final OrganizationAccessGuard accessGuard;
    private final QueryServiceTemplate queryTemplate;
    private final CommandServiceTemplate commandTemplate;
    private final BackgroundWorkObservability observability;
    private final AfterCommitExecutor afterCommitExecutor;

    @Override
    public Result<Pager<NotificationVO>> query(AuthenticatedCaller caller, NotificationQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.NOTIFICATION_QUERY, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId;
            @Override public void validate() {
                validatePage(request); tenantId = requireInboxAccess(caller); memberId = memberId(caller.actorId());
            }
            @Override public Pager<NotificationVO> execute() {
                var page = repository.findInboxPage(tenantId, new NotificationQuery(request.getPage(), request.getSize(),
                        memberId, Boolean.TRUE.equals(request.getUnreadOnly())));
                Pager<NotificationVO> result = new Pager<>(); result.setPageNum(page.page()); result.setPageSize(page.size());
                result.setTotalNum(page.total()); result.setObjectList(page.items().stream().map(NotificationServiceImpl.this::vo).toList());
                return result;
            }
        });
    }

    @Override
    public Result<NotificationVO> markRead(AuthenticatedCaller caller, Long notificationIdValue) {
        return commandTemplate.execute(UseCaseOperation.NOTIFICATION_MARK_READ, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private NotificationId notificationId;
            @Override public void validate() {
                tenantId = requireInboxAccess(caller); memberId = memberId(caller.actorId()); notificationId = notificationId(notificationIdValue);
            }
            @Override public NotificationVO execute() {
                return repository.markReadIfVisible(tenantId, notificationId, memberId)
                        .map(receipt -> {
                            if (receipt.firstRead()) {
                                afterCommitExecutor.execute("notification delivery enqueue", () ->
                                        observability.recordNotificationOutcome(NotificationMetric.READ, 1));
                            }
                            return vo(receipt.notification());
                        })
                        .orElseThrow(() -> notFound("Notification was not found"));
            }
        });
    }

    @Override
    public Result<NotificationPreferenceVO> preferences(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.NOTIFICATION_PREFERENCE_GET, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId;
            @Override public void validate() {
                tenantId = requirePreferenceAccess(caller); memberId = memberId(caller.actorId());
            }
            @Override public NotificationPreferenceVO execute() {
                return preference(preferenceStore.findOrDefault(tenantId, memberId));
            }
        });
    }

    @Override
    public Result<NotificationPreferenceVO> updatePreferences(
            AuthenticatedCaller caller, NotificationPreferenceUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.NOTIFICATION_PREFERENCE_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId;
            @Override public void validate() {
                if (request == null || request.getEmailEnabled() == null) {
                    throw invalid("Notification preference is invalid");
                }
                tenantId = requirePreferenceAccess(caller); memberId = memberId(caller.actorId());
            }
            @Override public NotificationPreferenceVO execute() {
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                now = now.withNano(now.getNano() / 1_000 * 1_000);
                return preference(preferenceStore.update(tenantId, memberId, request.getEmailEnabled(),
                        request.getOaAnnouncementEmailEnabled(), now));
            }
        });
    }

    private TenantId requireInboxAccess(AuthenticatedCaller caller) {
        TenantId tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.NOTIFICATIONS_READ);
        if (!caller.hasAuthority(AuthorityCatalog.WORK_TASKS_READ)) {
            throw denied("Work task access is required for notifications");
        }
        return tenantId;
    }

    private TenantId requirePreferenceAccess(AuthenticatedCaller caller) {
        return accessGuard.requireActiveMembership(caller, AuthorityCatalog.NOTIFICATIONS_READ);
    }

    private void validatePage(NotificationQueryRequest request) {
        if (request == null || request.getPage() == null || request.getSize() == null
                || request.getPage() < 1 || request.getPage() > QueryRequest.MAX_PAGE
                || request.getSize() < 1 || request.getSize() > QueryRequest.MAX_SIZE) {
            throw invalid("Notification query is invalid");
        }
    }
    private MemberId memberId(Long value) {
        try { return new MemberId(value); } catch (RuntimeException exception) { throw invalid("Notification member is invalid"); }
    }
    private NotificationId notificationId(Long value) {
        try { return new NotificationId(value); } catch (RuntimeException exception) { throw invalid("Notification ID is invalid"); }
    }
    private NotificationVO vo(Notification value) {
        return new NotificationVO(value.getId().getValue(), value.getKind().name(), value.getTaskId().getValue(),
                value.getTitle(), value.getPriority().name(), value.getDueTime(), value.getStatus().name(),
                value.getReadTime(), value.getCreatedTime());
    }
    private NotificationPreferenceVO preference(NotificationPreference value) {
        return new NotificationPreferenceVO(value.emailEnabled(), value.oaAnnouncementEmailEnabled(),
                value.updatedTime());
    }
    private NonRetryableApplicationException invalid(String message) { return error(ApplicationErrorCode.PARAMETER_INVALID, message); }
    private NonRetryableApplicationException denied(String message) { return error(ApplicationErrorCode.ACCESS_DENIED, message); }
    private NonRetryableApplicationException notFound(String message) { return error(ApplicationErrorCode.RESOURCE_NOT_FOUND, message); }
    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
