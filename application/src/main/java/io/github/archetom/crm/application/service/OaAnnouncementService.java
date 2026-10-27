package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaAnnouncementCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementQueryRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.vo.OaAnnouncementInboxVO;
import io.github.archetom.crm.application.vo.OaAnnouncementAudienceCandidateVO;
import io.github.archetom.crm.application.vo.OaAnnouncementReadReceiptVO;
import io.github.archetom.crm.application.vo.OaAnnouncementSummaryVO;
import io.github.archetom.crm.application.vo.OaAnnouncementVO;

public interface OaAnnouncementService {

    Result<Pager<OaAnnouncementSummaryVO>> query(
            AuthenticatedCaller caller, OaAnnouncementQueryRequest request);

    Result<OaAnnouncementVO> get(AuthenticatedCaller caller, Long announcementId);

    Result<Pager<OaAnnouncementAudienceCandidateVO>> audienceCandidates(
            AuthenticatedCaller caller, String type, String keyword, Integer page, Integer size);

    Result<OaAnnouncementVO> create(
            AuthenticatedCaller caller, OaAnnouncementCreateRequest request);

    Result<OaAnnouncementVO> update(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementUpdateRequest request);

    Result<OaAnnouncementVO> publish(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request);

    Result<OaAnnouncementVO> schedule(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementScheduleRequest request);

    Result<OaAnnouncementVO> cancelSchedule(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request);

    Result<OaAnnouncementVO> withdraw(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long announcementId, Long version);

    Result<Pager<OaAnnouncementInboxVO>> mine(
            AuthenticatedCaller caller, QueryRequest request);

    Result<OaAnnouncementInboxVO> getMine(
            AuthenticatedCaller caller, Long announcementId);

    Result<Long> unreadCount(AuthenticatedCaller caller);

    Result<OaAnnouncementReadReceiptVO> markRead(
            AuthenticatedCaller caller, Long announcementId);
}
