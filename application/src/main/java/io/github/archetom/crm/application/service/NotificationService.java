package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NotificationQueryRequest;
import io.github.archetom.crm.api.dto.request.NotificationPreferenceUpdateRequest;
import io.github.archetom.crm.application.vo.NotificationVO;
import io.github.archetom.crm.application.vo.NotificationPreferenceVO;

public interface NotificationService {
    Result<Pager<NotificationVO>> query(AuthenticatedCaller caller, NotificationQueryRequest request);
    Result<NotificationVO> markRead(AuthenticatedCaller caller, Long notificationId);
    Result<NotificationPreferenceVO> preferences(AuthenticatedCaller caller);
    Result<NotificationPreferenceVO> updatePreferences(
            AuthenticatedCaller caller, NotificationPreferenceUpdateRequest request);
}
