package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.RegionReferenceCatalog;
import io.github.archetom.crm.application.vo.RegionReferenceVO;

import java.util.List;

/**
 * Deliberately empty reference-data adapter until a deployment supplies its
 * own curated region catalog. It never derives regions from organization data.
 *
 * <p>Registered by {@link RegionReferenceCatalogConfiguration} only when no other catalog is
 * present, so a deployment can install its own without colliding with this fallback.</p>
 */
public class EmptyRegionReferenceCatalog implements RegionReferenceCatalog {

    @Override
    public List<RegionReferenceVO> findChildren(String parentCode) {
        return List.of();
    }
}
