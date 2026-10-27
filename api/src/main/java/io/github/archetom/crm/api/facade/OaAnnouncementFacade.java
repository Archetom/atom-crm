package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaAnnouncementCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementQueryRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.response.OaAnnouncementInboxResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementAudienceCandidateResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementReadReceiptResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementSummaryResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementUnreadCountResponse;

public interface OaAnnouncementFacade {

    Result<Pager<OaAnnouncementSummaryResponse>> query(
            AuthenticatedCaller caller, OaAnnouncementQueryRequest request);

    Result<OaAnnouncementResponse> get(AuthenticatedCaller caller, Long announcementId);

    Result<Pager<OaAnnouncementAudienceCandidateResponse>> audienceCandidates(
            AuthenticatedCaller caller, String type, String keyword, Integer page, Integer size);

    Result<OaAnnouncementResponse> create(
            AuthenticatedCaller caller, OaAnnouncementCreateRequest request);

    Result<OaAnnouncementResponse> update(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementUpdateRequest request);

    Result<OaAnnouncementResponse> publish(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request);

    Result<OaAnnouncementResponse> schedule(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementScheduleRequest request);

    Result<OaAnnouncementResponse> cancelSchedule(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request);

    Result<OaAnnouncementResponse> withdraw(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request);

    Result<Void> delete(
            AuthenticatedCaller caller, Long announcementId, Long version);

    Result<Pager<OaAnnouncementInboxResponse>> mine(
            AuthenticatedCaller caller, QueryRequest request);

    Result<OaAnnouncementInboxResponse> getMine(
            AuthenticatedCaller caller, Long announcementId);

    Result<OaAnnouncementUnreadCountResponse> unreadCount(AuthenticatedCaller caller);

    Result<OaAnnouncementReadReceiptResponse> markRead(
            AuthenticatedCaller caller, Long announcementId);
}
