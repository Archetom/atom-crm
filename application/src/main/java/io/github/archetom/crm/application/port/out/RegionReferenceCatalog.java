package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.application.vo.RegionReferenceVO;

import java.util.List;

/** Outbound contract for a deployment-provided, read-only region data set. */
public interface RegionReferenceCatalog {

    List<RegionReferenceVO> findChildren(String parentCode);
}
