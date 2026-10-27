package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.RegionReferenceResponse;
import io.github.archetom.crm.api.facade.RegionReferenceFacade;
import io.github.archetom.crm.application.service.RegionReferenceService;
import io.github.archetom.crm.application.vo.RegionReferenceVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Facade adapter for independently provisioned region reference data. */
@Service
@RequiredArgsConstructor
public class RegionReferenceFacadeImpl implements RegionReferenceFacade {

    private final RegionReferenceService regionReferenceService;

    @Override
    public Result<List<RegionReferenceResponse>> listChildren(AuthenticatedCaller caller, String parentCode) {
        return ResultUtil.map(regionReferenceService.listChildren(caller, parentCode),
                RegionReferenceFacadeImpl::toResponses);
    }

    private static List<RegionReferenceResponse> toResponses(List<RegionReferenceVO> sources) {
        return sources.stream().map(source -> new RegionReferenceResponse()
                .setCode(source.getCode()).setParentCode(source.getParentCode())
                .setName(source.getName()).setLevel(source.getLevel())).toList();
    }
}
