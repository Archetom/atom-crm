package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.request.SavedViewUpdateRequest;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface SavedViewFacade {
    Result<List<SavedViewResponse>> list(
            AuthenticatedCaller caller, String objectType, boolean includeHidden);
    Result<SavedViewResponse> create(AuthenticatedCaller caller, SavedViewCreateRequest request);
    Result<SavedViewResponse> update(
            AuthenticatedCaller caller, Long viewId, SavedViewUpdateRequest request);
    Result<SavedViewResponse> setDefault(
            AuthenticatedCaller caller, Long viewId, Long version);
    Result<Void> delete(AuthenticatedCaller caller, Long viewId, Long version);
}
