package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NotificationQueryRequest;
import io.github.archetom.crm.api.dto.request.NotificationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.NotificationResponse;
import io.github.archetom.crm.api.dto.response.NotificationPreferenceResponse;

public interface NotificationFacade {
    Result<Pager<NotificationResponse>> query(AuthenticatedCaller caller, NotificationQueryRequest request);
    Result<NotificationResponse> markRead(AuthenticatedCaller caller, Long notificationId);
    Result<NotificationPreferenceResponse> preferences(AuthenticatedCaller caller);
    Result<NotificationPreferenceResponse> updatePreferences(
            AuthenticatedCaller caller, NotificationPreferenceUpdateRequest request);
}
