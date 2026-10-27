package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.RegionReferenceResponse;
import io.github.archetom.common.result.Result;

import java.util.List;

/** Public contract for deployment-supplied region reference data. */
public interface RegionReferenceFacade {

    Result<List<RegionReferenceResponse>> listChildren(AuthenticatedCaller caller, String parentCode);
}
