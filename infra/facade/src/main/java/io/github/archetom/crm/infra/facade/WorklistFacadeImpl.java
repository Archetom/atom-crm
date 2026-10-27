package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.WorklistItemResponse;
import io.github.archetom.crm.api.dto.response.WorklistPageResponse;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;
import io.github.archetom.crm.api.facade.WorklistFacade;
import io.github.archetom.crm.application.service.WorklistQueryService;
import io.github.archetom.crm.application.service.WorklistAcknowledgementService;
import io.github.archetom.crm.application.vo.WorklistItemVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class WorklistFacadeImpl implements WorklistFacade {
    private final WorklistQueryService queryService;
    private final WorklistAcknowledgementService acknowledgementService;

    @Override
    public Result<WorklistPageResponse> query(AuthenticatedCaller caller, LocalDate date, String cursor, Integer size) {
        return ResultUtil.map(queryService.query(caller, date, cursor, size), values -> new WorklistPageResponse()
                .setItems(values.items().stream().map(this::response).toList())
                .setNextCursor(values.nextCursor()));
    }

    @Override
    public Result<Void> acknowledge(
            AuthenticatedCaller caller, WorklistAcknowledgementRequest request) {
        return acknowledgementService.acknowledge(caller, request);
    }

    private WorklistItemResponse response(WorklistItemVO value) {
        return new WorklistItemResponse().setKind(value.kind()).setSourceId(value.sourceId())
                .setSourceVersion(value.sourceVersion()).setSubjectType(value.subjectType())
                .setSubjectId(value.subjectId()).setDueTime(value.dueTime()).setSummary(value.summary());
    }
}
