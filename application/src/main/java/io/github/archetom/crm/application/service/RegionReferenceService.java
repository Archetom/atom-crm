package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.RegionReferenceVO;
import io.github.archetom.common.result.Result;

import java.util.List;

/** Query boundary for independently provisioned region reference data. */
public interface RegionReferenceService {

    Result<List<RegionReferenceVO>> listChildren(AuthenticatedCaller caller, String parentCode);
}
