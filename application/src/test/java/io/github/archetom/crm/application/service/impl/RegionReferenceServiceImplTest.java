package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.RegionReferenceCatalog;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.RegionReferenceVO;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionReferenceServiceImplTest {

    @Test
    void queriesTheIndependentReferenceCatalogWithoutOrganizationMembership() {
        RegionReferenceCatalog catalog = parentCode -> List.of(
                new RegionReferenceVO("CN-01", parentCode, "Example Region", "PROVINCE"));
        RegionReferenceServiceImpl service = new RegionReferenceServiceImpl(
                catalog, new QueryServiceTemplate(
                        "test-app", new TestTransactionManager()), new CallerGuard());

        Result<List<RegionReferenceVO>> result = service.listChildren(
                new AuthenticatedCaller(7L, 99L, Set.of("organization:request")), "CN");

        assertTrue(result.isSuccess());
        assertEquals("CN", result.getData().getFirst().getParentCode());
    }
}
