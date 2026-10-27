package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.WorkTaskSeriesCapabilityVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;

/** Resolves the server-side interaction capability for one visible Work task series. */
public interface WorkTaskSeriesCapabilityService {
    WorkTaskSeriesCapabilityVO resolve(AuthenticatedCaller caller, WorkTaskSeriesVO series);
}
