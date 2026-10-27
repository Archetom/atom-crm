package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.RegionReferenceCatalog;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.service.RegionReferenceService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.RegionReferenceVO;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Keeps reference-data lookup separate from tenant-owned organization aggregates. */
@Service
@RequiredArgsConstructor
public class RegionReferenceServiceImpl implements RegionReferenceService {

    private static final String ORGANIZATION_REQUEST = "organization:request";

    private final RegionReferenceCatalog regionReferenceCatalog;
    private final QueryServiceTemplate queryTemplate;
    private final CallerGuard callerGuard;

    @Override
    public Result<List<RegionReferenceVO>> listChildren(AuthenticatedCaller caller, String parentCode) {
        return queryTemplate.execute(UseCaseOperation.REGION_REFERENCE_LIST, new ServiceOperation<>() {
            private String normalizedParentCode;

            @Override
            public void validate() {
                callerGuard.requireTenant(caller, ORGANIZATION_REQUEST);
                normalizedParentCode = normalizeParentCode(parentCode);
            }

            @Override
            public List<RegionReferenceVO> execute() {
                return regionReferenceCatalog.findChildren(normalizedParentCode);
            }
        });
    }

    private String normalizeParentCode(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) return null;
        String normalized = parentCode.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                    "Region parent code must contain only letters, digits, underscores, or hyphens");
        }
        return normalized;
    }
}
