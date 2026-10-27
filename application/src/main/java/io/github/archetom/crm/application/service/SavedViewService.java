package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.request.SavedViewUpdateRequest;
import io.github.archetom.crm.application.vo.SavedViewVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface SavedViewService {
    Result<List<SavedViewVO>> list(
            AuthenticatedCaller caller, String objectType, boolean includeHidden);
    Result<SavedViewVO> create(AuthenticatedCaller caller, SavedViewCreateRequest request);
    Result<SavedViewVO> update(
            AuthenticatedCaller caller, Long viewId, SavedViewUpdateRequest request);
    Result<SavedViewVO> setDefault(
            AuthenticatedCaller caller, Long viewId, Long version);
    Result<Void> delete(AuthenticatedCaller caller, Long viewId, Long version);
}
