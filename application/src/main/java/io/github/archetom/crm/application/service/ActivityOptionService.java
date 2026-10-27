package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityOptionRequest;
import io.github.archetom.crm.application.vo.ActivityOptionVO;

import java.util.List;

public interface ActivityOptionService {
    Result<List<ActivityOptionVO>> query(AuthenticatedCaller caller);
    Result<List<ActivityOptionVO>> replace(AuthenticatedCaller caller, List<ActivityOptionRequest> requests);
}
