package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.model.MarketingFormSubmission;
import io.github.archetom.crm.domain.model.MarketingFormSubmissionDateRange;
import io.github.archetom.crm.domain.model.MarketingFormSubmissionTrendPoint;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped append and read port for successful marketing-form submissions. */
public interface MarketingFormSubmissionRepository {

    MarketingFormSubmission append(
            TenantId tenantId, MarketingFormId formId,
            long formVersion, MarketingFormTargetType targetType, long targetRecordId,
            String idempotencyKey, String submissionDigest);

    Optional<MarketingFormSubmission> findByIdempotencyKey(
            TenantId tenantId, MarketingFormId formId, String idempotencyKey);

    List<MarketingFormSubmissionTrendPoint> findTrend(
            TenantId tenantId, MarketingFormId formId,
            MarketingFormSubmissionDateRange range, ReportGranularity granularity);

    PageResult<MarketingFormSubmission> findVisibleLeadLinks(
            TenantId tenantId, MarketingFormId formId,
            MarketingFormSubmissionDateRange range, LeadVisibility visibility,
            int page, int size);

    PageResult<MarketingFormSubmission> findVisibleCustomerLinks(
            TenantId tenantId, MarketingFormId formId,
            MarketingFormSubmissionDateRange range, CustomerVisibility visibility,
            int page, int size);
}
