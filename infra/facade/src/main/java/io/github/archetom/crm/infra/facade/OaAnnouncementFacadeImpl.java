package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaAnnouncementCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementQueryRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.response.OaAnnouncementAudienceSelectorResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementInboxResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementAudienceCandidateResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementReadReceiptResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementSummaryResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementUnreadCountResponse;
import io.github.archetom.crm.api.facade.OaAnnouncementFacade;
import io.github.archetom.crm.application.service.OaAnnouncementService;
import io.github.archetom.crm.application.vo.OaAnnouncementInboxVO;
import io.github.archetom.crm.application.vo.OaAnnouncementAudienceCandidateVO;
import io.github.archetom.crm.application.vo.OaAnnouncementSummaryVO;
import io.github.archetom.crm.application.vo.OaAnnouncementVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OaAnnouncementFacadeImpl implements OaAnnouncementFacade {

    private final OaAnnouncementService service;

    @Override
    public Result<Pager<OaAnnouncementSummaryResponse>> query(
            AuthenticatedCaller caller, OaAnnouncementQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), source -> {
            Pager<OaAnnouncementSummaryResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream()
                    .map(this::summary).toList());
            return target;
        });
    }

    @Override
    public Result<OaAnnouncementResponse> get(
            AuthenticatedCaller caller, Long announcementId) {
        return ResultUtil.map(service.get(caller, announcementId), this::response);
    }

    @Override
    public Result<Pager<OaAnnouncementAudienceCandidateResponse>> audienceCandidates(
            AuthenticatedCaller caller, String type, String keyword, Integer page, Integer size) {
        return ResultUtil.map(service.audienceCandidates(caller, type, keyword, page, size), source -> {
            Pager<OaAnnouncementAudienceCandidateResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream().map(this::audienceCandidate).toList());
            return target;
        });
    }

    @Override
    public Result<OaAnnouncementResponse> create(
            AuthenticatedCaller caller, OaAnnouncementCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::response);
    }

    @Override
    public Result<OaAnnouncementResponse> update(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementUpdateRequest request) {
        return ResultUtil.map(service.update(caller, announcementId, request), this::response);
    }

    @Override
    public Result<OaAnnouncementResponse> publish(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request) {
        return ResultUtil.map(service.publish(caller, announcementId, request), this::response);
    }

    @Override
    public Result<OaAnnouncementResponse> schedule(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementScheduleRequest request) {
        return ResultUtil.map(service.schedule(caller, announcementId, request), this::response);
    }

    @Override
    public Result<OaAnnouncementResponse> cancelSchedule(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request) {
        return ResultUtil.map(service.cancelSchedule(caller, announcementId, request), this::response);
    }

    @Override
    public Result<OaAnnouncementResponse> withdraw(
            AuthenticatedCaller caller, Long announcementId,
            OaAnnouncementVersionRequest request) {
        return ResultUtil.map(service.withdraw(caller, announcementId, request), this::response);
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long announcementId, Long version) {
        return service.delete(caller, announcementId, version);
    }

    @Override
    public Result<Pager<OaAnnouncementInboxResponse>> mine(
            AuthenticatedCaller caller, QueryRequest request) {
        return ResultUtil.map(service.mine(caller, request), source -> {
            Pager<OaAnnouncementInboxResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream()
                    .map(this::inbox).toList());
            return target;
        });
    }

    @Override
    public Result<OaAnnouncementInboxResponse> getMine(
            AuthenticatedCaller caller, Long announcementId) {
        return ResultUtil.map(service.getMine(caller, announcementId), this::inbox);
    }

    @Override
    public Result<OaAnnouncementUnreadCountResponse> unreadCount(
            AuthenticatedCaller caller) {
        return ResultUtil.map(service.unreadCount(caller), value ->
                new OaAnnouncementUnreadCountResponse().setUnreadCount(value));
    }

    @Override
    public Result<OaAnnouncementReadReceiptResponse> markRead(
            AuthenticatedCaller caller, Long announcementId) {
        return ResultUtil.map(service.markRead(caller, announcementId), value ->
                new OaAnnouncementReadReceiptResponse()
                        .setAnnouncementId(value.announcementId())
                        .setReadTime(value.readTime()));
    }

    private OaAnnouncementResponse response(OaAnnouncementVO value) {
        return new OaAnnouncementResponse()
                .setId(value.id())
                .setCreatedByActorId(value.createdByActorId())
                .setTitle(value.title())
                .setBody(value.body())
                .setAudience(value.audience().stream().map(selector ->
                        new OaAnnouncementAudienceSelectorResponse()
                                .setType(selector.type())
                                .setDepartmentId(selector.departmentId())
                                .setMemberActorId(selector.memberActorId())
                                .setDisplayName(selector.displayName())).toList())
                .setStatus(value.status())
                .setRecipientTotal(value.recipientTotal())
                .setReadTotal(value.readTotal())
                .setScheduledPublishTime(value.scheduledPublishTime())
                .setScheduledByActorId(value.scheduledByActorId())
                .setLastPublicationSafeReason(value.lastPublicationSafeReason())
                .setPublishedTime(value.publishedTime())
                .setWithdrawnTime(value.withdrawnTime())
                .setAllowedActions(value.allowedActions())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    private OaAnnouncementSummaryResponse summary(OaAnnouncementSummaryVO value) {
        return new OaAnnouncementSummaryResponse()
                .setId(value.id())
                .setCreatedByActorId(value.createdByActorId())
                .setTitle(value.title())
                .setBody(value.body())
                .setStatus(value.status())
                .setRecipientTotal(value.recipientTotal())
                .setReadTotal(value.readTotal())
                .setScheduledPublishTime(value.scheduledPublishTime())
                .setScheduledByActorId(value.scheduledByActorId())
                .setLastPublicationSafeReason(value.lastPublicationSafeReason())
                .setPublishedTime(value.publishedTime())
                .setWithdrawnTime(value.withdrawnTime())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    private OaAnnouncementInboxResponse inbox(OaAnnouncementInboxVO value) {
        return new OaAnnouncementInboxResponse()
                .setId(value.id())
                .setTitle(value.title())
                .setBody(value.body())
                .setRead(value.read())
                .setReadTime(value.readTime())
                .setPublishedTime(value.publishedTime());
    }

    private OaAnnouncementAudienceCandidateResponse audienceCandidate(
            OaAnnouncementAudienceCandidateVO value) {
        return new OaAnnouncementAudienceCandidateResponse(value.id(), value.displayName());
    }
}
