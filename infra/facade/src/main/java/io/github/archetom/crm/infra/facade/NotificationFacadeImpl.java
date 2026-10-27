package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NotificationQueryRequest;
import io.github.archetom.crm.api.dto.request.NotificationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.NotificationResponse;
import io.github.archetom.crm.api.dto.response.NotificationPreferenceResponse;
import io.github.archetom.crm.api.facade.NotificationFacade;
import io.github.archetom.crm.application.service.NotificationService;
import io.github.archetom.crm.application.vo.NotificationVO;
import io.github.archetom.crm.application.vo.NotificationPreferenceVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationFacadeImpl implements NotificationFacade {
    private final NotificationService service;
    @Override public Result<Pager<NotificationResponse>> query(AuthenticatedCaller caller, NotificationQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), page -> {
            Pager<NotificationResponse> result = PageUtil.copy(page); result.setMeta(null);
            result.setObjectList(page.getObjectList().stream().map(this::response).toList()); return result;
        });
    }
    @Override public Result<NotificationResponse> markRead(AuthenticatedCaller caller, Long notificationId) {
        return ResultUtil.map(service.markRead(caller, notificationId), this::response);
    }
    @Override public Result<NotificationPreferenceResponse> preferences(AuthenticatedCaller caller) {
        return ResultUtil.map(service.preferences(caller), this::preferenceResponse);
    }
    @Override public Result<NotificationPreferenceResponse> updatePreferences(
            AuthenticatedCaller caller, NotificationPreferenceUpdateRequest request) {
        return ResultUtil.map(service.updatePreferences(caller, request), this::preferenceResponse);
    }
    private NotificationResponse response(NotificationVO value) {
        return new NotificationResponse().setId(value.id()).setKind(value.kind()).setTaskId(value.taskId())
                .setTitle(value.title()).setPriority(value.priority()).setDueTime(value.dueTime())
                .setStatus(value.status()).setReadTime(value.readTime()).setCreatedTime(value.createdTime());
    }
    private NotificationPreferenceResponse preferenceResponse(NotificationPreferenceVO value) {
        return new NotificationPreferenceResponse().setEmailEnabled(value.emailEnabled())
                .setOaAnnouncementEmailEnabled(value.oaAnnouncementEmailEnabled())
                .setUpdatedTime(value.updatedTime());
    }
}
