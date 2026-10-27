package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.WorklistPageVO;

import java.time.LocalDate;

public interface WorklistQueryService {
    Result<WorklistPageVO> query(AuthenticatedCaller caller, LocalDate date, String cursor, Integer size);
}
