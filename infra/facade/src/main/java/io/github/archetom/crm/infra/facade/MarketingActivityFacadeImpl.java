package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingActivityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityUpdateRequest;
import io.github.archetom.crm.api.dto.response.MarketingActivityConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.MarketingActivityRelatedObjectTypeOptionResponse;
import io.github.archetom.crm.api.dto.response.MarketingActivityResponse;
import io.github.archetom.crm.api.dto.response.MarketingActivityTypeOptionResponse;
import io.github.archetom.crm.api.facade.MarketingActivityFacade;
import io.github.archetom.crm.application.service.MarketingActivityService;
import io.github.archetom.crm.application.vo.MarketingActivityVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class MarketingActivityFacadeImpl implements MarketingActivityFacade {
    private final MarketingActivityService service;

    @Override
    public Result<MarketingActivityConfigurationOptionsResponse> configurationOptions(
            AuthenticatedCaller caller) {
        return ResultUtil.map(service.configurationOptions(caller), options ->
                new MarketingActivityConfigurationOptionsResponse(
                        options.activityTypes().stream()
                                .map(type -> new MarketingActivityTypeOptionResponse(
                                        type.code(), type.displayName()))
                                .toList(),
                        options.relatedObjectTypes().stream()
                                .map(type -> new MarketingActivityRelatedObjectTypeOptionResponse(
                                        type.code(), type.displayName(), type.supportsMarketingForm()))
                                .toList(),
                        options.defaultRelatedObjectType()));
    }

    @Override
    public Result<Pager<MarketingActivityResponse>> query(
            AuthenticatedCaller caller, MarketingActivityQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), page -> {
            Pager<MarketingActivityResponse> response = PageUtil.copy(page);
            response.setMeta(null);
            response.setObjectList(page.getObjectList().stream().map(this::response).toList());
            return response;
        });
    }

    @Override
    public Result<MarketingActivityResponse> get(AuthenticatedCaller caller, Long activityId) {
        return ResultUtil.map(service.get(caller, activityId), this::response);
    }

    @Override
    public Result<MarketingActivityResponse> create(
            AuthenticatedCaller caller, MarketingActivityCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::response);
    }

    @Override
    public Result<MarketingActivityResponse> update(
            AuthenticatedCaller caller, Long activityId, MarketingActivityUpdateRequest request) {
        return ResultUtil.map(service.update(caller, activityId, request), this::response);
    }

    @Override
    public Result<Void> deleteBatch(AuthenticatedCaller caller, MarketingActivityBatchDeleteRequest request) {
        return service.deleteBatch(caller, request);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long activityId, Long version) {
        return service.delete(caller, activityId, version);
    }

    private MarketingActivityResponse response(MarketingActivityVO value) {
        return new MarketingActivityResponse()
                .setId(value.id()).setName(value.name()).setRelatedObjectType(value.relatedObjectType())
                .setCreatedByActorId(value.createdByActorId())
                .setMarketingFormId(value.marketingFormId())
                .setMarketingFormPublicId(value.marketingFormPublicId())
                .setParticipantActorIds(value.participantActorIds())
                .setParticipantDepartmentIds(value.participantDepartmentIds())
                .setActivityType(value.activityType())
                .setStartTime(value.startTime()).setEndTime(value.endTime()).setBudget(value.budget())
                .setAddress(value.address()).setDescription(value.description())
                .setViewCount(value.viewCount()).setSubmissionCount(value.submissionCount())
                .setOneSubmissionPerUser(value.oneSubmissionPerUser()).setStatus(value.status())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
